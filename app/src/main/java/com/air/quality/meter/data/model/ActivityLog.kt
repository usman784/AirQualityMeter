package com.air.quality.meter.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Admin activity log → Firestore /activity_logs/{id}
 * Auto-written by admin actions and system events.
 */
data class ActivityLog(
    @DocumentId val id: String = "",
    val uid: String = "",
    val action: String = "",     // e.g. "USER_LOGIN", "MANUAL_ENTRY", "ALERT_SENT", "THRESHOLD_UPDATED"
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
