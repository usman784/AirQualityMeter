package com.air.quality.meter.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Health recommendation document → Firestore /recommendations/{id}
 * Admins can create / update / delete these via the Admin panel.
 */
data class HealthRecommendation(
    @DocumentId val id: String = "",
    val aqiCategory: String = "",    // Good | Moderate | Unhealthy | Very Unhealthy | Hazardous | Sensitive
    val title: String = "",
    val description: String = "",
    val iconEmoji: String = "😷",
    val updatedAt: Long = System.currentTimeMillis()
)
