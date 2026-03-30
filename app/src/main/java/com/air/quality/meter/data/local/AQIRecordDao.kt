package com.air.quality.meter.data.local

import androidx.room.*
import com.air.quality.meter.data.model.AQIRecord
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for AQI records.
 * Provides offline read/write and sync-queue operations.
 */
@Dao
interface AQIRecordDao {

    /** Insert or replace a record (used for both API syncs and manual entries) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AQIRecord)

    /** Insert a batch of records from Firestore sync */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AQIRecord>)

    /** Get the most recent AQI record for a user (for offline dashboard display) */
    @Query("SELECT * FROM aqi_records WHERE uid = :uid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(uid: String): AQIRecord?

    /** Get all records for a user between two timestamps (history + graphs) */
    @Query("SELECT * FROM aqi_records WHERE uid = :uid AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    fun getRecordsInRange(uid: String, from: Long, to: Long): Flow<List<AQIRecord>>

    /** Get all records as a Flow for real-time UI updates */
    @Query("SELECT * FROM aqi_records WHERE uid = :uid ORDER BY timestamp DESC")
    fun getAllRecords(uid: String): Flow<List<AQIRecord>>

    /** Get unsynced manual entries that need to be pushed to Firestore */
    @Query("SELECT * FROM aqi_records WHERE uid = :uid AND synced = 0")
    suspend fun getUnsyncedRecords(uid: String): List<AQIRecord>

    /** Mark a record as synced after successful Firestore upload */
    @Query("UPDATE aqi_records SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Delete records older than a cutoff timestamp to avoid unbounded local storage */
    @Query("DELETE FROM aqi_records WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /** Delete all records for a user (on account deletion) */
    @Query("DELETE FROM aqi_records WHERE uid = :uid")
    suspend fun deleteAllForUser(uid: String)
}
