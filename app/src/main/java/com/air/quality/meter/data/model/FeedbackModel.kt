package com.air.quality.meter.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Citizen feedback document → Firestore /feedback/{id}
 */
data class FeedbackModel(
    @DocumentId val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val message: String = "",
    val category: String = "General",   // General | Bug | Suggestion
    val status: String = "pending",     // pending | reviewed | resolved
    val timestamp: Long = System.currentTimeMillis()
)
