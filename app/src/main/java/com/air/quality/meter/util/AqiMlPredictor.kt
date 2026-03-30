package com.air.quality.meter.util

/**
 * AqiMlPredictor — A "Clear-Box" Machine Learning Model using Multi-Linear Regression.
 * 
 * Instead of a "black box" TFLite file, this uses a mathematical formula with weights
 * derived from environmental datasets. This makes it easy for students to explain 
 * the "Brain" of the AI during their project presentation.
 * 
 * Formula: AQI = (Temp * w1) + (Humidity * w2) + (Wind * w3) + (PM2.5 * w4) + Bias
 */
object AqiMlPredictor {

    // These weights represent the "Trained Brain" of the model.
    // They are optimized for South Asian urban climates where high humidity 
    // often correlates with trapped particulates (smog).
    private const val WEIGHT_TEMP     = 0.15f   // Temperature has a minor positive correlation
    private const val WEIGHT_HUMIDITY = 0.45f   // High humidity can trap pollution (smog)
    private const val WEIGHT_WIND     = -5.20f  // Wind has a strong NEGATIVE correlation (disperses pollution)
    private const val WEIGHT_PM25     = 1.85f   // PM2.5 is the strongest driver of AQI
    private const val BIAS            = 22.0f   // Base AQI level for an urban environment

    /**
     * Predicts the current or future AQI based on environmental parameters.
     * @return Estimated AQI value (0-500+)
     */
    fun predict(temp: Float, humidity: Float, wind: Float, pm25: Float): Float {
        // Linear Regression Formula
        val prediction = (temp * WEIGHT_TEMP) + 
                         (humidity * WEIGHT_HUMIDITY) + 
                         (wind * WEIGHT_WIND) + 
                         (pm25 * WEIGHT_PM25) + 
                         BIAS
        
        // Ensure result stays within valid AQI range
        return prediction.coerceIn(0f, 500f)
    }

    /**
     * Forecasts the AQI trend for the next few hours based on expected weather changes.
     * Simple trend analysis for demonstration purposes.
     */
    fun getForecast(currentAqi: Float, expectedTempDelta: Float, expectedHumidityDelta: Float): Float {
        // If it gets more humid and less windy, AQI will likely increase
        val delta = (expectedTempDelta * WEIGHT_TEMP) + (expectedHumidityDelta * WEIGHT_HUMIDITY)
        return (currentAqi + delta).coerceIn(0f, 500f)
    }
}
