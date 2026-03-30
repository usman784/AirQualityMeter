package com.air.quality.meter.data.repository

import com.air.quality.meter.data.local.AQIRecordDao
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.remote.RetrofitClient
import com.air.quality.meter.util.AQIClassifier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Single source of truth for AQI data.
 *
 * Strategy:
 *  1. Serve cached Room data immediately (offline-first)
 *  2. Fetch fresh data from API / Firestore in background
 *  3. Store offline manual entries locally; sync to Firestore via SyncWorker
 */
class AQIRepository(
    private val dao: AQIRecordDao,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ─── Live API Fetch ───────────────────────────────────────────────────────

    /**
     * Fetch current AQI from OpenWeatherMap, store in Room, and return the record.
     */
    suspend fun fetchLiveAQI(
        uid: String,
        lat: Double,
        lon: Double,
        apiKey: String,
        locationName: String = ""
    ): Result<AQIRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val weatherResp = RetrofitClient.weatherApi.getCurrentWeather(lat, lon, apiKey = apiKey)
            val pollutionResp = RetrofitClient.weatherApi.getAirPollution(lat, lon, apiKey = apiKey)

            val pm25 = pollutionResp.list.firstOrNull()?.components?.pm25 ?: 0f
            val pm10 = pollutionResp.list.firstOrNull()?.components?.pm10 ?: 0f
            val owmIdx = pollutionResp.list.firstOrNull()?.main?.aqi ?: 1

            // Prefer PM2.5-based AQI; fall back to OWM index conversion
            val aqi = if (pm25 > 0f) AQIClassifier.pm25ToAqi(pm25)
                      else AQIClassifier.owmIndexToAqi(owmIdx)
            val category = AQIClassifier.classify(aqi)

            // Throttle: Only save a new record if the last one was > 10 mins ago
            val lastRecord = dao.getLatestRecord(uid)
            val shouldSave = lastRecord == null || (System.currentTimeMillis() - lastRecord.timestamp > 10 * 60 * 1000)

            val record = AQIRecord(
                id           = UUID.randomUUID().toString(),
                uid          = uid,
                location     = locationName.ifBlank { weatherResp.cityName },
                latitude     = lat,
                longitude    = lon,
                aqi          = aqi,
                aqiCategory  = category.name,
                temperature  = weatherResp.main.temp,
                humidity     = weatherResp.main.humidity,
                windSpeed    = weatherResp.wind.speed,
                pm25         = pm25,
                pm10         = pm10,
                source       = "api",
                synced       = false,   // only false in local Room
                timestamp    = System.currentTimeMillis()
            )

            if (shouldSave) {
                dao.insertRecord(record)
            }
            record
        }
    }

    // ─── Manual Entry (Offline) ───────────────────────────────────────────────

    /**
     * Save a manual AQI entry locally.  synced=false so SyncWorker will push it later.
     */
    suspend fun saveManualEntry(record: AQIRecord): Unit = withContext(Dispatchers.IO) {
        dao.insertRecord(record.copy(source = "manual", synced = false))
    }

    // ─── Room Queries ─────────────────────────────────────────────────────────

    suspend fun getLatestRecord(uid: String): AQIRecord? =
        withContext(Dispatchers.IO) { dao.getLatestRecord(uid) }

    fun getAllRecords(uid: String): Flow<List<AQIRecord>> = dao.getAllRecords(uid)

    fun getRecordsInRange(uid: String, from: Long, to: Long): Flow<List<AQIRecord>> =
        dao.getRecordsInRange(uid, from, to)

    // ─── Firestore Sync ───────────────────────────────────────────────────────

    /**
     * Push all unsynced local records to Firestore.
     * Called by SyncWorker when connectivity is restored.
     */
    suspend fun syncUnsyncedToFirestore(uid: String): Unit = withContext(Dispatchers.IO) {
        val unsynced = dao.getUnsyncedRecords(uid)
        unsynced.forEach { record ->
            runCatching {
                db.collection("aqi_records")
                  .document(record.id)
                  .set(record.copy(synced = true)) // Mark as synced for Firestore cloud storage
                  .await()
                dao.markSynced(record.id)
            }
        }
    }

    /**
     * Pull the latest N records from Firestore and cache locally.
     */
    suspend fun syncFromFirestore(uid: String, limit: Long = 90): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("aqi_records")
                .whereEqualTo("uid", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            val records = snapshot.toObjects(AQIRecord::class.java)
            dao.insertAll(records)
        }
    }

    /** Prune records older than 90 days from Room to free space */
    suspend fun pruneOldRecords(): Unit = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        dao.deleteOlderThan(cutoff)
    }

    /** Fetch all AQI records (for admin dataset management UC09) */
    suspend fun getAllAqiRecords(limit: Long = 200): Result<List<AQIRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = db.collection("aqi_records")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snapshot.toObjects(AQIRecord::class.java)
        }
    }
}
