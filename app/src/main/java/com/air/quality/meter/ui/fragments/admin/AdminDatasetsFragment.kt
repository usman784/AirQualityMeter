package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.databinding.DialogAdminManageDatasetBinding
import com.air.quality.meter.databinding.FragmentAdminDatasetsBinding
import com.air.quality.meter.util.AQIClassifier
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * UC09 — Manage Datasets.
 * Admin can view, add, edit and delete historical AQI records.
 */
class AdminDatasetsFragment : Fragment() {

    private var _binding: FragmentAdminDatasetsBinding? = null
    private val binding get() = _binding!!

    private val aqiRepo by lazy { 
        AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao()) 
    }
    private lateinit var adapter: DatasetAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDatasetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DatasetAdapter(
            onEdit = { record -> showRecordDialog(existing = record) },
            onDelete = { record -> confirmDelete(record) }
        )
        binding.rvDatasets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDatasets.adapter = adapter
        binding.btnAddRecord.setOnClickListener { showRecordDialog(existing = null) }
        loadDatasets()
    }

    private fun loadDatasets() {
        lifecycleScope.launch {
            aqiRepo.getAllAqiRecords(limit = 400).fold(
                onSuccess = { records ->
                    if (!isAdded) return@fold
                    binding.tvTotalRecords.text = records.size.toString()
                    adapter.submitList(records)
                },
                onFailure = { e ->
                    if (!isAdded) return@fold
                    Snackbar.make(binding.root, "Failed to load datasets: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showRecordDialog(existing: AQIRecord?) {
        val dialogBinding = DialogAdminManageDatasetBinding.inflate(layoutInflater)
        val sourceOptions = listOf("admin", "api", "manual")
        dialogBinding.etSource.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sourceOptions)
        )

        if (existing != null) {
            dialogBinding.etUid.setText(existing.uid)
            dialogBinding.etUid.isEnabled = false
            dialogBinding.etUid.isFocusable = false
            dialogBinding.etUid.isFocusableInTouchMode = false
            dialogBinding.etUid.isClickable = false
            dialogBinding.etLocation.setText(existing.location)
            dialogBinding.etLatitude.setText(existing.latitude.toString())
            dialogBinding.etLongitude.setText(existing.longitude.toString())
            dialogBinding.etAqi.setText(existing.aqi.toString())
            dialogBinding.etTemperature.setText(existing.temperature.toString())
            dialogBinding.etHumidity.setText(existing.humidity.toString())
            dialogBinding.etWindSpeed.setText(existing.windSpeed.toString())
            dialogBinding.etPm25.setText(existing.pm25.toString())
            dialogBinding.etPm10.setText(existing.pm10.toString())
            dialogBinding.etSource.setText(existing.source.ifBlank { "admin" }, false)
        } else {
            dialogBinding.etUid.isEnabled = true
            dialogBinding.etUid.isFocusable = true
            dialogBinding.etUid.isFocusableInTouchMode = true
            dialogBinding.etUid.isClickable = true
            dialogBinding.etSource.setText("admin", false)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Add AQI Record" else "Edit AQI Record")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val built = validateAndBuildRecord(dialogBinding, existing) ?: return@setOnClickListener
                lifecycleScope.launch {
                    aqiRepo.upsertAqiRecord(built).fold(
                        onSuccess = {
                            if (!isAdded) return@fold
                            dialog.dismiss()
                            loadDatasets()
                            Snackbar.make(
                                binding.root,
                                if (existing == null) "AQI record added" else "AQI record updated",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { e ->
                            if (!isAdded) return@fold
                            Snackbar.make(binding.root, "Save failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        dialog.show()
    }

    private fun validateAndBuildRecord(
        b: DialogAdminManageDatasetBinding,
        existing: AQIRecord?
    ): AQIRecord? {
        val uid = existing?.uid ?: b.etUid.text?.toString()?.trim().orEmpty()
        val location = b.etLocation.text?.toString()?.trim().orEmpty()
        val latStr = b.etLatitude.text?.toString()?.trim().orEmpty()
        val lonStr = b.etLongitude.text?.toString()?.trim().orEmpty()
        val aqiStr = b.etAqi.text?.toString()?.trim().orEmpty()
        val source = b.etSource.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()

        if (uid.isBlank()) {
            b.etUid.error = "User UID is required"
            return null
        } else b.etUid.error = null

        if (location.isBlank()) {
            b.etLocation.error = "Location is required"
            return null
        } else b.etLocation.error = null

        val latitude = latStr.toDoubleOrNull()
        if (latitude == null || latitude !in -90.0..90.0) {
            b.etLatitude.error = "Latitude must be between -90 and 90"
            return null
        } else b.etLatitude.error = null

        val longitude = lonStr.toDoubleOrNull()
        if (longitude == null || longitude !in -180.0..180.0) {
            b.etLongitude.error = "Longitude must be between -180 and 180"
            return null
        } else b.etLongitude.error = null

        val aqi = aqiStr.toFloatOrNull()
        if (aqi == null || aqi < 0f) {
            b.etAqi.error = "AQI must be a valid non-negative number"
            return null
        } else b.etAqi.error = null

        val safeSource = when (source) {
            "admin", "api", "manual" -> source
            "" -> "admin"
            else -> {
                b.etSource.error = "Source must be admin, api or manual"
                return null
            }
        }
        b.etSource.error = null

        val temperature = b.etTemperature.text?.toString()?.trim().orEmpty().toFloatOrNull() ?: 0f
        val humidity = b.etHumidity.text?.toString()?.trim().orEmpty().toFloatOrNull() ?: 0f
        if (humidity < 0f || humidity > 100f) {
            b.etHumidity.error = "Humidity must be 0-100"
            return null
        } else b.etHumidity.error = null

        val wind = b.etWindSpeed.text?.toString()?.trim().orEmpty().toFloatOrNull() ?: 0f
        val pm25 = b.etPm25.text?.toString()?.trim().orEmpty().toFloatOrNull() ?: 0f
        val pm10 = b.etPm10.text?.toString()?.trim().orEmpty().toFloatOrNull() ?: 0f

        if (pm25 < 0f) {
            b.etPm25.error = "PM2.5 must be >= 0"
            return null
        } else b.etPm25.error = null

        if (pm10 < 0f) {
            b.etPm10.error = "PM10 must be >= 0"
            return null
        } else b.etPm10.error = null

        val normalizedAqi = aqi.coerceAtLeast(0f)
        val category = AQIClassifier.classify(normalizedAqi)

        return AQIRecord(
            id = existing?.id ?: UUID.randomUUID().toString(),
            uid = uid,
            location = location,
            latitude = latitude,
            longitude = longitude,
            aqi = normalizedAqi,
            aqiCategory = category.name,
            temperature = temperature,
            humidity = humidity,
            windSpeed = wind.coerceAtLeast(0f),
            pm25 = pm25,
            pm10 = pm10,
            source = safeSource,
            synced = true,
            timestamp = existing?.timestamp ?: System.currentTimeMillis()
        )
    }

    private fun confirmDelete(record: AQIRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete AQI Record")
            .setMessage("Delete record for ${record.location.ifBlank { "Unknown Location" }} (AQI ${record.aqi.toInt()})?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    aqiRepo.deleteAqiRecord(record.id).fold(
                        onSuccess = {
                            if (!isAdded) return@fold
                            loadDatasets()
                            Snackbar.make(binding.root, "AQI record deleted", Snackbar.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            if (!isAdded) return@fold
                            Snackbar.make(binding.root, "Delete failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
