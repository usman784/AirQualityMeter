package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.air.quality.meter.databinding.FragmentAdminThresholdsBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore

/**
 * UC10 — Configure AQI Thresholds.
 * Admins can set the AQI ranges for each category.
 * These are stored in Firestore /settings/aqi_thresholds.
 */
class AdminThresholdsFragment : Fragment() {

    private var _binding: FragmentAdminThresholdsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminThresholdsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadThresholds()
        binding.btnSaveThresholds.setOnClickListener { saveThresholds() }
    }

    private fun loadThresholds() {
        db.collection("settings").document("aqi_thresholds")
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                if (doc.exists()) {
                    binding.etThresholdGood.setText(doc.getLong("good")?.toString() ?: "50")
                    binding.etThresholdModerate.setText(doc.getLong("moderate")?.toString() ?: "100")
                    binding.etThresholdSensitive.setText(doc.getLong("sensitive")?.toString() ?: "150")
                    binding.etThresholdUnhealthy.setText(doc.getLong("unhealthy")?.toString() ?: "200")
                    binding.etThresholdHazardous.setText(doc.getLong("hazardous")?.toString() ?: "301")
                }
            }
    }

    private fun saveThresholds() {
        val data = mapOf(
            "good"      to (binding.etThresholdGood.text.toString().toLongOrNull() ?: 50L),
            "moderate"  to (binding.etThresholdModerate.text.toString().toLongOrNull() ?: 100L),
            "sensitive" to (binding.etThresholdSensitive.text.toString().toLongOrNull() ?: 150L),
            "unhealthy" to (binding.etThresholdUnhealthy.text.toString().toLongOrNull() ?: 200L),
            "hazardous" to (binding.etThresholdHazardous.text.toString().toLongOrNull() ?: 301L)
        )

        db.collection("settings").document("aqi_thresholds")
            .set(data)
            .addOnSuccessListener {
                if (isAdded) Snackbar.make(binding.root, "Thresholds updated successfully", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (isAdded) Snackbar.make(binding.root, "Failed to update thresholds", Snackbar.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
