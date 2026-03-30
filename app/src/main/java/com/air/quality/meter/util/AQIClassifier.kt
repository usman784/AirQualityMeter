package com.air.quality.meter.util

import androidx.annotation.ColorRes
import com.air.quality.meter.R

/**
 * AQI Category Classifier — US EPA standard breakpoints.
 *
 * Input:  raw AQI float value (calculated or predicted by TFLite model)
 * Output: AQICategory object with category name, emoji, color resource, and health advice
 */
object AQIClassifier {

    data class AQICategory(
        val name: String,
        val emoji: String,
        val colorHex: String,
        @ColorRes val colorRes: Int,
        val advice: String,
        val aqiRange: String
    )

    fun classify(aqi: Float): AQICategory = when {
        aqi <= 50  -> AQICategory(
            name      = "Good",
            emoji     = "😊",
            colorHex  = "#00E400",
            colorRes  = R.color.aqi_good,
            advice    = "Air quality is satisfactory. Enjoy outdoor activities!",
            aqiRange  = "0–50"
        )
        aqi <= 100 -> AQICategory(
            name      = "Moderate",
            emoji     = "😐",
            colorHex  = "#FFFF00",
            colorRes  = R.color.aqi_moderate,
            advice    = "Air quality is acceptable. Unusually sensitive people should limit prolonged outdoor exertion.",
            aqiRange  = "51–100"
        )
        aqi <= 150 -> AQICategory(
            name      = "Unhealthy for Sensitive Groups",
            emoji     = "😷",
            colorHex  = "#FF7E00",
            colorRes  = R.color.aqi_sensitive,
            advice    = "Members of sensitive groups may experience health effects. General public is not likely affected.",
            aqiRange  = "101–150"
        )
        aqi <= 200 -> AQICategory(
            name      = "Unhealthy",
            emoji     = "🤢",
            colorHex  = "#FF0000",
            colorRes  = R.color.aqi_unhealthy,
            advice    = "Everyone may begin to experience health effects. Limit prolonged outdoor exertion.",
            aqiRange  = "151–200"
        )
        aqi <= 300 -> AQICategory(
            name      = "Very Unhealthy",
            emoji     = "🚨",
            colorHex  = "#8F3F97",
            colorRes  = R.color.aqi_very_unhealthy,
            advice    = "Health alert! Everyone may experience serious health effects. Avoid outdoor activities.",
            aqiRange  = "201–300"
        )
        else       -> AQICategory(
            name      = "Hazardous",
            emoji     = "☠️",
            colorHex  = "#7E0023",
            colorRes  = R.color.aqi_hazardous,
            advice    = "Emergency conditions! Stay indoors with windows closed. Avoid all physical activity outdoors.",
            aqiRange  = "301+"
        )
    }

    /**
     * Convert OpenWeatherMap's 1–5 index to an approximate AQI value.
     * OWM index: 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
     */
    fun owmIndexToAqi(owmIndex: Int): Float = when (owmIndex) {
        1    -> 25f
        2    -> 75f
        3    -> 125f
        4    -> 175f
        5    -> 250f
        else -> 0f
    }

    /**
     * Calculate AQI from raw PM2.5 concentration using US EPA formula.
     * This is the most accurate way when PM2.5 is available from the API.
     */
    fun pm25ToAqi(pm25: Float): Float {
        // EPA breakpoints for PM2.5 (µg/m³) → AQI
        data class BP(val cLo: Float, val cHi: Float, val iLo: Int, val iHi: Int)
        val breakpoints = listOf(
            BP(0.0f,   12.0f,  0,   50),
            BP(12.1f,  35.4f,  51,  100),
            BP(35.5f,  55.4f,  101, 150),
            BP(55.5f,  150.4f, 151, 200),
            BP(150.5f, 250.4f, 201, 300),
            BP(250.5f, 350.4f, 301, 400),
            BP(350.5f, 500.4f, 401, 500)
        )
        val bp = breakpoints.firstOrNull { pm25 >= it.cLo && pm25 <= it.cHi }
            ?: return if (pm25 > 500f) 500f else 0f
        return ((bp.iHi - bp.iLo).toFloat() / (bp.cHi - bp.cLo)) * (pm25 - bp.cLo) + bp.iLo
    }
}
