package com.air.quality.meter.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.repository.AQIRepository
import com.google.firebase.auth.FirebaseAuth

/**
 * WorkManager worker that runs when internet connectivity is restored.
 * It pushes all unsynced offline AQI entries to Firestore.
 *
 * Scheduled in the Application class as a NetworkType.CONNECTED constraint job.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Result.failure()   // no authenticated user — nothing to sync

        val dao  = AppDatabase.getInstance(applicationContext).aqiRecordDao()
        val repo = AQIRepository(dao)

        return try {
            repo.syncUnsyncedToFirestore(uid)
            repo.pruneOldRecords()
            Result.success()
        } catch (e: Exception) {
            // Retry on failure (WorkManager will re-queue with backoff)
            Result.retry()
        }
    }
}
