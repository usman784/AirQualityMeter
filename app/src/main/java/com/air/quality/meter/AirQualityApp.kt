package com.air.quality.meter

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.air.quality.meter.work.SyncWorker
import com.air.quality.meter.work.AqiAlertWorker
import java.util.concurrent.TimeUnit

/**
 * Application class — initialises global singletons at startup.
 *
 * Responsibilities:
 *  1. Schedule the periodic SyncWorker that pushes offline entries to Firestore
 *     whenever the device regains connectivity.
 */
class AirQualityApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleSyncWorker()
        scheduleAqiAlertWorker()
    }

    /**
     * Enqueue a periodic sync job that runs every 15 minutes
     * ONLY when the device has a network connection.
     * Uses KEEP policy so an existing job isn't replaced unnecessarily.
     */
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "aqi_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

    }
    /**
     * Enqueue a periodic job to check AQI every 1 hour
     * and trigger alerts if threshold exceeded.
     */
   private fun scheduleAqiAlertWorker() {
        val request = PeriodicWorkRequestBuilder<AqiAlertWorker>(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "aqi_alerts",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
