package com.air.quality.meter.ui.fragments.citizen

data class CitizenModel(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "citizen",
    val isActive: Boolean = true,
    val age: String = "",
    val gender: String = "",
    val cellNumber: String = "",
    val countryCode: String = "+92",
    val fullPhone: String = "",
    val createdAt: Long = 0L
)
