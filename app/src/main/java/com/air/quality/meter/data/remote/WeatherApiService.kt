package com.air.quality.meter.data.remote

import com.air.quality.meter.data.remote.dto.AirPollutionResponse
import com.air.quality.meter.data.remote.dto.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for OpenWeatherMap APIs.
 *
 * Base URL → https://api.openweathermap.org/data/2.5/
 *
 * Your API key is passed as a query parameter to each call.
 * Store it in local.properties: OWM_API_KEY=your_key_here
 * Expose via BuildConfig (see build.gradle.kts).
 */
interface WeatherApiService {

    /**
     * Fetch current weather (temperature, humidity, wind speed) for a location.
     * units=metric → temperature in °C, wind in m/s
     */
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat")   lat: Double,
        @Query("lon")   lon: Double,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    /**
     * Fetch air pollution data (PM2.5, PM10, AQI index) for a location.
     */
    @GET("air_pollution")
    suspend fun getAirPollution(
        @Query("lat")   lat: Double,
        @Query("lon")   lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse
}
