package com.solerunner.myhealth.data.repository

import com.solerunner.myhealth.data.db.MyHealthDatabase
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.repository.DeviceRepository
import com.solerunner.myhealth.domain.repository.MedicineRepository
import com.solerunner.myhealth.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class MedicineRepositoryImpl(private val database: MyHealthDatabase) : MedicineRepository {

    override fun observeAll(): Flow<List<Medicine>> = database.observe { database.read { it.medicineDao.all() } }

    override suspend fun all(): List<Medicine> = database.read { it.medicineDao.all() }

    override suspend fun byId(id: Long): Medicine? = database.read { it.medicineDao.byId(id) }

    override suspend fun save(medicine: Medicine): Long = database.write { db ->
        if (medicine.id == 0L) db.medicineDao.insert(medicine)
        else {
            db.medicineDao.update(medicine)
            medicine.id
        }
    }

    override suspend fun delete(id: Long) = database.write { it.medicineDao.delete(id) }
}

class TreatmentRepositoryImpl(private val database: MyHealthDatabase) : TreatmentRepository {

    override fun observeAll(): Flow<List<Treatment>> = database.observe { database.read { it.treatmentDao.all() } }

    override suspend fun all(): List<Treatment> = database.read { it.treatmentDao.all() }

    override suspend fun save(treatment: Treatment): Long = database.write { db ->
        if (treatment.id == 0L) db.treatmentDao.insert(treatment)
        else {
            db.treatmentDao.update(treatment)
            treatment.id
        }
    }

    override suspend fun delete(id: Long) = database.write { it.treatmentDao.delete(id) }
}

class DeviceRepositoryImpl(private val database: MyHealthDatabase) : DeviceRepository {

    override fun observeRecent(days: Int): Flow<List<DailyMetric>> =
        database.observe { database.read { it.dailyMetricDao.recent(days) } }

    override suspend fun since(date: LocalDate): List<DailyMetric> = database.read { it.dailyMetricDao.since(date) }

    override suspend fun upsertAll(metrics: List<DailyMetric>) = database.write { db ->
        metrics.forEach { db.dailyMetricDao.upsert(it) }
    }

    override suspend fun lastSync(): Instant? = database.read { it.dailyMetricDao.lastSync() }

    override suspend fun clear() = database.write { it.dailyMetricDao.deleteAll() }
}
