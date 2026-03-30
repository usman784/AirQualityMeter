package com.air.quality.meter.data.remote.dto

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────────────────────────────────────
// OpenWeatherMap Air Pollution API — /air_pollution
// Endpoint: http://api.openweathermap.org/data/2.5/air_pollution?lat=&lon=&appid=
// ──────────────────────────────────────────────────────────────────────────────

data class AirPollutionResponse(
    @SerializedName("list") val list: List<AirPollutionEntry> = emptyList()
)

data class AirPollutionEntry(
    @SerializedName("main")       val main: AqiMain,
    @SerializedName("components") val components: AqiComponents,
    @SerializedName("dt")         val dt: Long = 0L
)

data class AqiMain(
    /** OpenWeatherMap AQI index: 1=Good … 5=Very Poor */
    @SerializedName("aqi") val aqi: Int = 1
)

data class AqiComponents(
    @SerializedName("pm2_5") val pm25: Float = 0f,
    @SerializedName("pm10")  val pm10: Float  = 0f,
    @SerializedName("co")    val co: Float    = 0f,
    @SerializedName("no2")   val no2: Float   = 0f,
    @SerializedName("o3")    val o3: Float    = 0f,
    @SerializedName("so2")   val so2: Float   = 0f
)

// ──────────────────────────────────────────────────────────────────────────────
// OpenWeatherMap Current Weather API — /weather
// Endpoint: https://api.openweathermap.org/data/2.5/weather?lat=&lon=&appid=&units=metric
// ──────────────────────────────────────────────────────────────────────────────

data class WeatherResponse(
    @SerializedName("name")    val cityName: String = "",
    @SerializedName("main")    val main: WeatherMain,
    @SerializedName("wind")    val wind: Wind,
    @SerializedName("weather") val weather: List<WeatherCondition> = emptyList(),
    @SerializedName("dt")      val dt: Long = 0L
)

data class WeatherMain(
    @SerializedName("temp")     val temp: Float    = 0f,
    @SerializedName("humidity") val humidity: Float = 0f,
    @SerializedName("pressure") val pressure: Float = 0f
)

data class Wind(
    @SerializedName("speed") val speed: Float = 0f
)

data class WeatherCondition(
    @SerializedName("description") val description: String = "",
    @SerializedName("icon")        val icon: String         = ""
)
