package com.solerunner.myhealth.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.repository.DeviceRepository
import com.solerunner.myhealth.domain.repository.MeasurementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Result of one incremental read, so the UI can say what happened without a log. */
data class HealthConnectResult(
    val days: Int = 0,
    val measurements: Int = 0,
    val skippedForPermission: Boolean = false,
    val unavailable: Boolean = false,
)

/**
 * Reads wearable data through Health Connect and folds it into the local record.
 *
 * Everything is aggregated per local day before it is written, so the database stores one row per day
 * instead of a stream of samples. Blood pressure and glucose are also kept as individual measurements
 * because those are clinically meaningful readings, not just daily averages.
 */
class HealthConnectSync(
    private val context: Context,
    private val device: DeviceRepository,
    private val measurements: MeasurementRepository,
) {

    val readPermissions: Set<String> = listOf<KClass<out Record>>(
        StepsRecord::class,
        SleepSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        OxygenSaturationRecord::class,
        WeightRecord::class,
        BloodPressureRecord::class,
        BloodGlucoseRecord::class,
        ActiveCaloriesBurnedRecord::class,
        ExerciseSessionRecord::class,
    ).map { HealthPermission.getReadPermission(it) }.toSet()

    fun isAvailable(): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun grantedPermissions(): Set<String> = runCatching {
        client().permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(readPermissions)

    suspend fun sync(days: Int = 90): HealthConnectResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext HealthConnectResult(unavailable = true)
        val granted = grantedPermissions()
        if (!granted.containsAll(readPermissions)) {
            return@withContext HealthConnectResult(skippedForPermission = true)
        }
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = LocalDate.now(zone).minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        val client = client()
        val byDay = linkedMapOf<LocalDate, MutableDay>()
        val newMeasurements = mutableListOf<Measurement>()

        read(client, StepsRecord::class, start, end).forEach { record ->
            day(byDay, record.startTime, zone).steps += record.count.toInt()
        }
        read(client, SleepSessionRecord::class, start, end).forEach { record ->
            val minutes = (record.endTime.epochSecond - record.startTime.epochSecond) / 60
            if (minutes in 0..1440) day(byDay, record.startTime, zone).sleepMinutes += minutes.toInt()
        }
        read(client, HeartRateRecord::class, start, end).forEach { record ->
            val day = day(byDay, record.startTime, zone)
            record.samples.forEach { sample ->
                day.heartSamples += sample.beatsPerMinute
                day.maxHeartRate = maxOf(day.maxHeartRate ?: 0, sample.beatsPerMinute)
            }
        }
        read(client, RestingHeartRateRecord::class, start, end).forEach { record ->
            day(byDay, record.time, zone).restingHeartRates += record.beatsPerMinute
        }
        read(client, HeartRateVariabilityRmssdRecord::class, start, end).forEach { record ->
            day(byDay, record.time, zone).hrv += record.heartRateVariabilityMillis
        }
        read(client, OxygenSaturationRecord::class, start, end).forEach { record ->
            val day = day(byDay, record.time, zone)
            val value = record.percentage.value
            day.spo2 = minOf(day.spo2 ?: value, value)
        }
        read(client, WeightRecord::class, start, end).forEach { record ->
            day(byDay, record.time, zone).weightKg = record.weight.inKilograms
            newMeasurements += Measurement(
                kind = MeasurementKind.WEIGHT,
                primaryValue = record.weight.inKilograms,
                unit = "kg",
                recordedAt = record.time,
                recordedDay = record.time.atZone(zone).toLocalDate(),
                source = DataSource.DEVICE_SYNC,
            )
        }
        read(client, ActiveCaloriesBurnedRecord::class, start, end).forEach { record ->
            day(byDay, record.startTime, zone).activeCalories += record.energy.inKilocalories
        }
        read(client, ExerciseSessionRecord::class, start, end).forEach { record ->
            val minutes = (record.endTime.epochSecond - record.startTime.epochSecond) / 60
            day(byDay, record.startTime, zone).exerciseMinutes += minutes.toInt()
        }
        read(client, BloodPressureRecord::class, start, end).forEach { record ->
            newMeasurements += Measurement(
                kind = MeasurementKind.BLOOD_PRESSURE,
                primaryValue = record.systolic.inMillimetersOfMercury,
                secondaryValue = record.diastolic.inMillimetersOfMercury,
                unit = "mmHg",
                recordedAt = record.time,
                recordedDay = record.time.atZone(zone).toLocalDate(),
                source = DataSource.DEVICE_SYNC,
            )
        }
        read(client, BloodGlucoseRecord::class, start, end).forEach { record ->
            newMeasurements += Measurement(
                kind = MeasurementKind.BLOOD_GLUCOSE,
                primaryValue = record.level.inMilligramsPerDeciliter,
                unit = "mg/dL",
                recordedAt = record.time,
                recordedDay = record.time.atZone(zone).toLocalDate(),
                source = DataSource.DEVICE_SYNC,
            )
        }

        val metrics = byDay.map { (date, value) ->
            DailyMetric(
                day = date,
                sleepMinutes = value.sleepMinutes.takeIf { it > 0 },
                steps = value.steps.takeIf { it > 0 },
                restingHeartRate = value.restingHeartRates.takeIf { it.isNotEmpty() }?.average(),
                averageHeartRate = value.heartSamples.takeIf { it.isNotEmpty() }?.average(),
                maxHeartRate = value.maxHeartRate?.toDouble(),
                hrvRmssd = value.hrv.takeIf { it.isNotEmpty() }?.average(),
                spo2Average = value.spo2,
                weightKg = value.weightKg,
                activeCalories = value.activeCalories.takeIf { it > 0 },
                exerciseMinutes = value.exerciseMinutes.takeIf { it > 0 },
                provider = "Health Connect",
                syncedAt = Instant.now(),
            )
        }
        if (metrics.isNotEmpty()) device.upsertAll(metrics)

        val existing = measurements.since(LocalDate.now(zone).minusDays(days.toLong()))
            .filter { it.source == DataSource.DEVICE_SYNC }
            .map { Triple(it.kind, it.recordedAt, it.primaryValue) }
            .toSet()
        var written = 0
        newMeasurements
            .filter { item -> Triple(item.kind, item.recordedAt, item.primaryValue) !in existing }
            .distinctBy { "${it.kind}-${it.recordedAt}-${it.primaryValue}" }
            .forEach { item ->
                measurements.add(item)
                written += 1
            }
        HealthConnectResult(days = metrics.size, measurements = written)
    }

    private suspend fun <T : Record> read(
        client: HealthConnectClient,
        type: KClass<T>,
        start: Instant,
        end: Instant,
    ): List<T> = runCatching {
        client.readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1000,
            ),
        ).records
    }.getOrDefault(emptyList())

    private fun day(days: MutableMap<LocalDate, MutableDay>, instant: Instant, zone: ZoneId): MutableDay =
        days.getOrPut(instant.atZone(zone).toLocalDate()) { MutableDay() }

    private class MutableDay {
        var steps = 0
        var sleepMinutes = 0
        val heartSamples = mutableListOf<Long>()
        val restingHeartRates = mutableListOf<Long>()
        val hrv = mutableListOf<Double>()
        var maxHeartRate: Long? = null
        var spo2: Double? = null
        var weightKg: Double? = null
        var activeCalories = 0.0
        var exerciseMinutes = 0
    }
}
