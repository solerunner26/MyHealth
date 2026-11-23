package app.myhealth.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import app.myhealth.domain.model.VitalType
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Read-only Health Connect access. Nothing is ever written to Health Connect and nothing leaves the device. */
class HealthConnectSource(private val context: Context, private val zone: ZoneId) : HealthSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun status(): HcStatus = when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)) {
        HealthConnectClient.SDK_AVAILABLE -> HcStatus.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.UpdateRequired
        else -> if (android.os.Build.VERSION.SDK_INT >= 28) HcStatus.NotInstalled else HcStatus.Unsupported
    }

    override suspend fun granted(): Set<HcData> {
        val g = client.permissionController.getGrantedPermissions()
        return HcData.entries.filter { PERMISSIONS[it]?.let { p -> p in g } == true }.toSet()
    }

    override suspend fun dailyAggregates(from: LocalDate, toExclusive: LocalDate): List<DayAggregate> {
        val granted = granted()
        val metrics = buildSet {
            if (HcData.Steps in granted) add(StepsRecord.COUNT_TOTAL)
            if (HcData.HeartRate in granted) add(HeartRateRecord.BPM_AVG)
            if (HcData.RestingHeartRate in granted) add(RestingHeartRateRecord.BPM_AVG)
            if (HcData.Sleep in granted) add(SleepSessionRecord.SLEEP_DURATION_TOTAL)
            if (HcData.Weight in granted) add(WeightRecord.WEIGHT_AVG)
        }
        if (metrics.isEmpty()) return emptyList()
        val groups = client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(from.atStartOfDay(), toExclusive.atStartOfDay()), Period.ofDays(1)))
        return groups.map { g ->
            val r = g.result
            DayAggregate(g.startTime.toLocalDate(), r[StepsRecord.COUNT_TOTAL], r[HeartRateRecord.BPM_AVG]?.toDouble(), r[RestingHeartRateRecord.BPM_AVG]?.toDouble(),
                r[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(), r[WeightRecord.WEIGHT_AVG]?.inKilograms)
        }
    }

    override suspend fun changesToken(): String = client.getChangesToken(ChangesTokenRequest(RECORD_TYPES))

    override suspend fun changes(token: String): HcChanges {
        var t = token; val days = mutableSetOf<LocalDate>(); var full = false
        while (true) {
            val resp = client.getChanges(t)
            if (resp.changesTokenExpired) return HcChanges(emptySet(), true, t)
            resp.changes.forEach { c ->
                when (c) {
                    is UpsertionChange -> timeOf(c.record)?.let { days += it.atZone(zone).toLocalDate() }
                    is DeletionChange -> full = true // deletions carry no time: re-aggregate the window
                }
            }
            t = resp.nextChangesToken
            if (!resp.hasMore) break
        }
        return HcChanges(days, full, t)
    }

    override suspend fun revokeAll() = client.permissionController.revokeAllPermissions()

    override suspend fun readings(from: Instant, to: Instant): List<HcReading> {
        val g = granted(); val range = TimeRangeFilter.between(from, to)
        suspend fun <T : Record> read(type: KClass<T>): List<T> = runCatching {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(type, range, pageSize = 500)).records
        }.getOrDefault(emptyList())
        val out = mutableListOf<HcReading>()
        if (HcData.BloodPressure in g) read(BloodPressureRecord::class).forEach {
            out += HcReading(it.metadata.id, VitalType.BloodPressure, it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury, "mmHg", it.time)
        }
        if (HcData.OxygenSaturation in g) read(OxygenSaturationRecord::class).forEach { out += HcReading(it.metadata.id, VitalType.SpO2, it.percentage.value, null, "%", it.time) }
        if (HcData.BloodGlucose in g) read(BloodGlucoseRecord::class).forEach { out += HcReading(it.metadata.id, VitalType.BloodGlucose, it.level.inMilligramsPerDeciliter, null, "mg/dL", it.time) }
        if (HcData.BodyTemperature in g) read(BodyTemperatureRecord::class).forEach { out += HcReading(it.metadata.id, VitalType.Temperature, it.temperature.inCelsius, null, "°C", it.time) }
        if (HcData.RespiratoryRate in g) read(RespiratoryRateRecord::class).forEach { out += HcReading(it.metadata.id, VitalType.RespiratoryRate, it.rate, null, "/min", it.time) }
        return out
    }

    private fun timeOf(r: Record): Instant? = when (r) {
        is StepsRecord -> r.startTime; is HeartRateRecord -> r.startTime; is SleepSessionRecord -> r.startTime
        is RestingHeartRateRecord -> r.time; is WeightRecord -> r.time; is BloodPressureRecord -> r.time; is OxygenSaturationRecord -> r.time
        is BloodGlucoseRecord -> r.time; is BodyTemperatureRecord -> r.time; is RespiratoryRateRecord -> r.time; else -> null
    }

    companion object {
        val RECORD_TYPES: Set<KClass<out Record>> = setOf(StepsRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, SleepSessionRecord::class, WeightRecord::class,
            BloodPressureRecord::class, OxygenSaturationRecord::class, BloodGlucoseRecord::class, BodyTemperatureRecord::class, RespiratoryRateRecord::class)
        val PERMISSIONS: Map<HcData, String> = mapOf(
            HcData.Steps to HealthPermission.getReadPermission(StepsRecord::class),
            HcData.HeartRate to HealthPermission.getReadPermission(HeartRateRecord::class),
            HcData.RestingHeartRate to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HcData.Sleep to HealthPermission.getReadPermission(SleepSessionRecord::class),
            HcData.Weight to HealthPermission.getReadPermission(WeightRecord::class),
            HcData.BloodPressure to HealthPermission.getReadPermission(BloodPressureRecord::class),
            HcData.OxygenSaturation to HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HcData.BloodGlucose to HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HcData.BodyTemperature to HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HcData.RespiratoryRate to HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        )
        /** Lets the periodic sync read while the app is closed (Android 15+ / recent Health Connect). */
        const val BACKGROUND_READ = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
