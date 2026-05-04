package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.air.quality.meter.databinding.FragmentManualEntryBinding
import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.util.AQIClassifier
import com.air.quality.meter.util.TFLitePredictor
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.UUID
import android.graphics.Color.parseColor

/**
 * UC03 — Manual Data Entry & AI Prediction.
 * Uses LiteRT / TensorFlow Lite model inference via TFLitePredictor.
 * Falls back to formula-based AqiMlPredictor if model asset is unavailable.
 */
class ManualEntryFragment : Fragment() {

    private var _binding: FragmentManualEntryBinding? = null
    private val binding get() = _binding!!

    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val repo by lazy {
        AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao())
    }
    private val userRepo = UserRepository()
    private lateinit var tflitePredictor: TFLitePredictor

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManualEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tflitePredictor = TFLitePredictor(requireContext())
        binding.btnPredict.setOnClickListener { predict() }
    }

    private fun predict() {
        if (!validateInputs()) return

        val temp     = binding.etTemp.text.toString().toFloat()
        val humidity = binding.etHumidity.text.toString().toFloat()
        val wind     = binding.etWind.text.toString().toFloat()
        val pm25Raw  = binding.etPm25.text.toString().toFloatOrNull() ?: 0f

        // Primary path: TFLite inference. Fallback path handled inside TFLitePredictor.
        val aqi = tflitePredictor.predict(temp, humidity, wind, pm25Raw)

        val category = AQIClassifier.classify(aqi)
        showResult(aqi, category, temp, humidity, wind, pm25Raw)
        saveEntry(aqi, category.name, temp, humidity, wind, pm25Raw)
    }

    private fun showResult(aqi: Float, cat: AQIClassifier.AQICategory,
                           temp: Float, humidity: Float, wind: Float, pm25: Float) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultEmoji.text    = cat.emoji
        binding.tvResultAqi.text      = "AQI: ${aqi.toInt()}"
        binding.tvResultCategory.text = cat.name
        binding.tvResultAdvice.text   = cat.advice

        // Card border = AQI color
        try { binding.cardResult.strokeColor = parseColor(cat.colorHex) } catch (_: Exception) {}

        // Animate card in
        binding.cardResult.alpha = 0f
        binding.cardResult.translationY = 30f
        binding.cardResult.animate().alpha(1f).translationY(0f).setDuration(400).start()
    }

    private fun saveEntry(aqi: Float, category: String, temp: Float, humidity: Float, wind: Float, pm25: Float) {
        val record = AQIRecord(
            id          = UUID.randomUUID().toString(),
            uid         = uid,
            aqi         = aqi,
            aqiCategory = category,
            temperature = temp,
            humidity    = humidity,
            windSpeed   = wind,
            pm25        = pm25,
            source      = "manual",
            synced      = false,
            timestamp   = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            repo.saveManualEntry(record)
            userRepo.logActivity(
                ActivityLog(
                    uid = uid,
                    action = "MANUAL_SUBMISSION",
                    details = "Manual AQI submitted: AQI=${aqi.toInt()}, Temp=$temp, Humidity=${humidity.toInt()}%, Wind=$wind",
                    timestamp = System.currentTimeMillis()
                )
            )
            if (isAdded) {
                binding.chipOfflineNote.visibility = View.VISIBLE
            }
        }
    }

    private fun validateInputs(): Boolean {
        var ok = true
        if (binding.etTemp.text.isNullOrBlank()) {
            binding.tilTemp.error = "Required"; ok = false
        } else binding.tilTemp.error = null

        if (binding.etHumidity.text.isNullOrBlank()) {
            binding.tilHumidity.error = "Required"; ok = false
        } else {
            val h = binding.etHumidity.text.toString().toFloatOrNull()
            if (h == null || h < 0 || h > 100) {
                binding.tilHumidity.error = "Enter 0–100 %"; ok = false
            } else binding.tilHumidity.error = null
        }

        if (binding.etWind.text.isNullOrBlank()) {
            binding.tilWind.error = "Required"; ok = false
        } else binding.tilWind.error = null

        return ok
    }

    override fun onDestroyView() { 
        if (::tflitePredictor.isInitialized) tflitePredictor.close()
        super.onDestroyView()
        _binding = null 
    }
}
