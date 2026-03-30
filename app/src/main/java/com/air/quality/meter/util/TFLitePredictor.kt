package com.air.quality.meter.util

import android.content.Context
import android.content.res.AssetManager
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLitePredictor — Handles AI inference using a TensorFlow Lite model file.
 * 
 * This class loads 'aqi_model.tflite' from the assets folder and performs 
 * regression to predict AQI based on [Temp, Humidity, Wind, PM2.5].
 */
//class TFLitePredictor(context: Context) {
//
////    private var interpreter: Interpreter? = null
//    private val MODEL_FILE = "aqi_model.tflite"
//
//    init {
//        try {
//            val model = loadModelFile(context.assets, MODEL_FILE)
////            interpreter = Interpreter(model)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            // Fallback: If model is missing, we'll use the AqiMlPredictor formula
//        }
//    }
//
//    /**
//     * Prediction using TFLite Model.
//     * Inputs: FloatArray(4) -> [temp, humidity, wind, pm25]
//     * Output: FloatArray(1) -> [predicted_aqi]
//     */
//    fun predict(temp: Float, humidity: Float, wind: Float, pm25: Float): Float {
//        val tflite = interpreter
//        if (tflite == null) {
//            // FALLBACK to Mathematical Formula if TFLite model is not provided yet
//            return AqiMlPredictor.predict(temp, humidity, wind, pm25)
//        }
//
//        val inputs = arrayOf(floatArrayOf(temp, humidity, wind, pm25))
//        val outputs = Array(1) { FloatArray(1) }
//
//        tflite.run(inputs, outputs)
//        return outputs[0][0].coerceIn(0f, 500f)
//    }
//
//    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
//        val fileDescriptor = assetManager.openFd(modelPath)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    fun close() {
//        interpreter?.close()
//    }
//}
