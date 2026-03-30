package com.air.quality.meter.data.model

import com.google.firebase.firestore.DocumentId

/**
 * AQI alert threshold document → Firestore /thresholds/{id}
 * Admins configure these breakpoints; the AQI alert worker uses them to trigger FCM.
 */
data class AlertThreshold(
    @DocumentId val id: String = "",
    val category: String = "",       // Good | Moderate | Unhealthy | Very Unhealthy | Hazardous
    val minAqi: Int = 0,
    val maxAqi: Int = 50,
    val alertMessage: String = "",
    val color: String = "#00E400"    // hex color for UI indicator
)
