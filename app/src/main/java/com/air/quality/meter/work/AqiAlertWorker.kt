package com.air.quality.meter.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.air.quality.meter.R
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.util.AQIClassifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * UC04 — AQI Alerts & Push Notifications.
 * Periodically checks the current AQI for the user's last known location.
 * Triggers a notification if the AQI exceeds the admin-set threshold.
 */
class AqiAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "aqi_alerts_channel"
    }

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val repo = AQIRepository(AppDatabase.getInstance(applicationContext).aqiRecordDao())
        val latest = repo.getLatestRecord(uid) ?: return Result.success()

        // Fetch thresholds from Firestore
        val db = FirebaseFirestore.getInstance()
        val thresholds = try {
            db.collection("settings").document("aqi_thresholds").get().await()
        } catch (e: Exception) {
            null
        }

        val limit = thresholds?.getLong("unhealthy") ?: 150L // Default to 150 if not set

        if (latest.aqi >= limit) {
            showNotification(latest.aqi)
        }

        return Result.success()
    }

    private fun showNotification(aqi: Float) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Air Quality Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when the air quality is poor"
            }
            nm.createNotificationChannel(channel)
        }

        val cat = AQIClassifier.classify(aqi)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this exists or use a generic one
            .setContentTitle("Poor Air Quality Alert! ⚠️")
            .setContentText("The current AQI is ${aqi.toInt()} (${cat.name}). ${cat.advice}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(101, notification)
    }
}
