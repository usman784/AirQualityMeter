package com.air.quality.meter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId

/**
 * AQI record — used both as a Room @Entity (local cache) and mapped to Firestore /aqi_records/{id}
 *
 * source: "api" → fetched automatically from OpenWeatherMap / AQICN
 *         "manual" → entered by citizen offline (pending Firestore sync)
 */
@Entity(tableName = "aqi_records")
data class AQIRecord(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @DocumentId val firestoreId: String = "",
    val uid: String = "",                   // owner citizen UID
    val location: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val aqi: Float = 0f,                    // raw AQI value
    val aqiCategory: String = "",           // Good | Moderate | Unhealthy | Very Unhealthy | Hazardous
    val temperature: Float = 0f,            // °C
    val humidity: Float = 0f,               // %
    val windSpeed: Float = 0f,              // m/s
    val pm25: Float = 0f,                   // µg/m³ (may be 0 if not available)
    val pm10: Float = 0f,
    val source: String = "api",             // "api" | "manual"
    val synced: Boolean = true,             // false → offline entry not yet pushed to Firestore
    val timestamp: Long = System.currentTimeMillis()
)
