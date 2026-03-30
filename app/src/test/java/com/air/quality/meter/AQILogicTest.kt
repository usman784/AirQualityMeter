package com.air.quality.meter

import com.air.quality.meter.util.AQIClassifier
import com.air.quality.meter.util.AqiMlPredictor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for core AQI logic.
 * Essential for Phase 9 Testing & QA.
 */
class AQILogicTest {

    @Test
    fun test_aqi_classification() {
        // Test Good
        assertEquals(AQIClassifier.AQICategory.GOOD, AQIClassifier.classify(25f))
        
        // Test Hazardous
        assertEquals(AQIClassifier.AQICategory.HAZARDOUS, AQIClassifier.classify(450f))
    }

    @Test
    fun test_pm25_to_aqi_formula() {
        // 12.0 ug/m3 should be exactly 50 AQI (boundary of Good)
        assertEquals(50, AQIClassifier.pm25ToAqi(12.0f).toInt())
        
        // 35.4 ug/m3 should be exactly 100 AQI (boundary of Moderate)
        assertEquals(100, AQIClassifier.pm25ToAqi(35.4f).toInt())
    }

    @Test
    fun test_ml_predictor_bounds() {
        // Ensure predictor never returns values outside 0-500
        val extremeAqi = AqiMlPredictor.predict(100f, 100f, 0f, 1000f)
        assertEquals(500f, extremeAqi)
        
        val negativeAqi = AqiMlPredictor.predict(-50f, 0f, 100f, 0f)
        assertEquals(0f, negativeAqi)
    }
}
