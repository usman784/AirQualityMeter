package com.air.quality.meter.data.model

/**
 * Represents a citizen / admin user document in Firestore → /users/{uid}
 */
data class UserModel(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "citizen",        // "citizen" | "admin"
    val age: String = "",
    val gender: String = "",
    val cellNumber: String = "",
    val countryCode: String = "+92",
    val fullPhone: String = "",
    val profileImageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
