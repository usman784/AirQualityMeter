package com.air.quality.meter.util

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLitePredictor — on-device AQI inference using LiteRT / TensorFlow Lite.
 *
 * Expected assets:
 *  1) aqi_model.tflite
 *  2) aqi_model_norm.json (optional normalization metadata)
 *
 * If model loading fails, prediction gracefully falls back to [AqiMlPredictor].
 */
class TFLitePredictor(context: Context) : Closeable {

    enum class InferenceSource {
        TFLITE,
        FALLBACK
    }

    private val modelFileName = "aqi_model.tflite"
    private val normFileName = "aqi_model_norm.json"

    private var interpreter: Interpreter? = null
    private var normalizer: Normalizer = Normalizer.default()
    private var lastInferenceSource: InferenceSource = InferenceSource.FALLBACK

    init {
        normalizer = loadNormalizer(context.assets) ?: Normalizer.default()
        interpreter = runCatching {
            val mappedModel = loadModelFile(context.assets, modelFileName)
            Interpreter(mappedModel)
        }.getOrNull()
        lastInferenceSource = if (interpreter != null) InferenceSource.TFLITE else InferenceSource.FALLBACK
    }

    /**
     * Inputs: [temp, humidity, wind, pm25]
     * Output: predicted AQI (0..500)
     */
    fun predict(temp: Float, humidity: Float, wind: Float, pm25: Float): Float {
        val tflite = interpreter
        if (tflite == null) {
            lastInferenceSource = InferenceSource.FALLBACK
            return AqiMlPredictor.predict(temp, humidity, wind, pm25)
        }

        return runCatching {
            val input = arrayOf(
                floatArrayOf(
                    normalizer.normalize(temp, 0),
                    normalizer.normalize(humidity, 1),
                    normalizer.normalize(wind, 2),
                    normalizer.normalize(pm25, 3)
                )
            )
            val output = Array(1) { FloatArray(1) }
            tflite.run(input, output)
            lastInferenceSource = InferenceSource.TFLITE
            output[0][0].coerceIn(0f, 500f)
        }.getOrElse {
            // Fail-safe path so app never blocks prediction.
            lastInferenceSource = InferenceSource.FALLBACK
            AqiMlPredictor.predict(temp, humidity, wind, pm25)
        }
    }

    fun getLastInferenceSourceLabel(): String {
        return when (lastInferenceSource) {
            InferenceSource.TFLITE -> "TFLite"
            InferenceSource.FALLBACK -> "Fallback"
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    private fun loadNormalizer(assetManager: AssetManager): Normalizer? {
        return runCatching {
            val json = assetManager.open(normFileName).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val meanArray = obj.optJSONArray("mean") ?: return null
            val stdArray = obj.optJSONArray("std") ?: return null
            if (meanArray.length() != 4 || stdArray.length() != 4) return null

            val mean = FloatArray(4) { idx -> meanArray.optDouble(idx, 0.0).toFloat() }
            val std = FloatArray(4) { idx ->
                val v = stdArray.optDouble(idx, 1.0).toFloat()
                if (v == 0f) 1f else v
            }
            Normalizer(mean, std)
        }.getOrNull()
    }

    private data class Normalizer(
        val mean: FloatArray,
        val std: FloatArray
    ) {
        fun normalize(value: Float, index: Int): Float {
            val m = mean.getOrNull(index) ?: 0f
            val s = std.getOrNull(index)?.takeIf { it != 0f } ?: 1f
            return (value - m) / s
        }

        companion object {
            fun default(): Normalizer = Normalizer(
                mean = floatArrayOf(30f, 60f, 2f, 35f),
                std = floatArrayOf(10f, 20f, 2f, 25f)
            )
        }
    }
}
