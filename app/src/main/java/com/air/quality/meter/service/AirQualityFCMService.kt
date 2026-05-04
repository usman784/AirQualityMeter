package com.air.quality.meter.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.air.quality.meter.R
import com.air.quality.meter.ui.activity.MainActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions

/**
 * Firebase Cloud Messaging service.
 *
 * Handles:
 *  1. Incoming AQI alert push notifications (sent by a Cloud Function or admin trigger)
 *  2. FCM token refresh — updates /users/{uid} with the latest token
 */
class AirQualityFCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "aqi_alerts"
        const val CHANNEL_NAME = "AQI Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save updated FCM token to Firestore user document
        saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Air Quality Alert"
        val body  = message.notification?.body
            ?: message.data["body"]
            ?: "Current AQI exceeds safe threshold."
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // Create notification channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "AQI threshold breach alerts" }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveFcmToken(token: String) {
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val uid = authUser.uid
        val email = authUser.email?.trim().orEmpty()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val adminCollections = listOf("admin", "admins")

        val adminDocChecks = adminCollections.map { collection ->
            db.collection(collection).document(uid).get()
        }

        Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(adminDocChecks)
            .addOnSuccessListener { docs ->
                if (docs.any { it.exists() }) return@addOnSuccessListener

                val adminUidQueries = adminCollections.map { collection ->
                    db.collection(collection)
                        .whereEqualTo("uid", uid)
                        .limit(1)
                        .get()
                }

                Tasks.whenAllSuccess<QuerySnapshot>(adminUidQueries)
                    .addOnSuccessListener { uidSnapshots ->
                        if (uidSnapshots.any { !it.isEmpty }) return@addOnSuccessListener

                        if (email.isBlank()) {
                            writeCitizenToken(db, uid, token)
                            return@addOnSuccessListener
                        }

                        val adminEmailQueries = adminCollections.map { collection ->
                            db.collection(collection)
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get()
                        }

                        Tasks.whenAllSuccess<QuerySnapshot>(adminEmailQueries)
                            .addOnSuccessListener { emailSnapshots ->
                                if (emailSnapshots.any { !it.isEmpty }) return@addOnSuccessListener
                                writeCitizenToken(db, uid, token)
                            }
                    }
            }
    }

    private fun writeCitizenToken(
        db: com.google.firebase.firestore.FirebaseFirestore,
        uid: String,
        token: String
    ) {
        db.collection("users")
            .document(uid)
            .set(mapOf("uid" to uid, "fcmToken" to token), SetOptions.merge())
    }
}
