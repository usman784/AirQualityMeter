package com.air.quality.meter.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.air.quality.meter.BuildConfig
import com.air.quality.meter.R
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.util.AQIClassifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
        private const val PREFS_NAME = "aqi_alert_worker_prefs"
        private const val UNSAFE_STATE_KEY_PREFIX = "is_unsafe_"
    }

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val repo = AQIRepository(AppDatabase.getInstance(applicationContext).aqiRecordDao())
        val previous = repo.getLatestRecord(uid) ?: return Result.success()
        val latest = fetchFreshAqiIfPossible(repo, uid, previous)

        // Fetch thresholds from Firestore
        val db = FirebaseFirestore.getInstance()
        val thresholdsDoc = try {
            db.collection("settings").document("aqi_thresholds").get().await()
        } catch (e: Exception) {
            null
        }

        // Alert trigger:
        // 1) If admin provides explicit "alertThreshold", use it.
        // 2) Otherwise derive start of Unhealthy band as (sensitive + 1), default 151.
        val alertThreshold = resolveAlertThreshold(thresholdsDoc)

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unsafeKey = "$UNSAFE_STATE_KEY_PREFIX$uid"
        val wasUnsafe = prefs.getBoolean(unsafeKey, false)
        val isUnsafe = latest.aqi >= alertThreshold

        // Notify only on threshold crossing into unsafe zone.
        if (!wasUnsafe && isUnsafe) {
            showNotification(latest.aqi)
            logAlertTriggered(uid, latest.aqi, alertThreshold)
        }
        prefs.edit().putBoolean(unsafeKey, isUnsafe).apply()

        return Result.success()
    }

    private suspend fun fetchFreshAqiIfPossible(
        repo: AQIRepository,
        uid: String,
        fallback: AQIRecord
    ): AQIRecord {
        val apiKey = BuildConfig.OWM_API_KEY
        if (apiKey.isBlank()) return fallback

        return repo.fetchLiveAQI(
            uid = uid,
            lat = fallback.latitude,
            lon = fallback.longitude,
            apiKey = apiKey,
            locationName = fallback.location
        ).getOrElse { fallback }
    }

    private fun resolveAlertThreshold(doc: DocumentSnapshot?): Float {
        val explicit = doc?.getLong("alertThreshold")
        if (explicit != null && explicit > 0L) return explicit.toFloat()

        val sensitiveUpperBound = doc?.getLong("sensitive") ?: 150L
        return (sensitiveUpperBound + 1L).toFloat()
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

    private suspend fun logAlertTriggered(uid: String, aqi: Float, threshold: Float) {
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("activity_logs")
                .document()
                .set(
                    mapOf(
                        "uid" to uid,
                        "action" to "ALERT_TRIGGERED",
                        "details" to "AQI ${aqi.toInt()} crossed threshold ${threshold.toInt()}",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                .await()
        }
    }
}
