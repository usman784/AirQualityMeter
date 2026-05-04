package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.model.HealthRecommendation
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.FragmentAdminRecommendationsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.*

/**
 * UC11 — Manage Health Recommendations.
 * Admins can add, edit, view, and delete health tips mapped to AQI categories.
 */
class AdminRecommendationsFragment : Fragment() {

    private var _binding: FragmentAdminRecommendationsBinding? = null
    private val binding get() = _binding!!

    private val userRepo = UserRepository()
    private lateinit var adapter: AdminRecommendationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminRecommendationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminRecommendationAdapter(
            onEdit = { tip -> showTipDialog(existing = tip) },
            onDelete = { tip -> deleteTip(tip) }
        )
        binding.rvAdminRecommendations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdminRecommendations.adapter       = adapter

        loadRecommendations()

        binding.fabAddRecommendation.setOnClickListener { showTipDialog(existing = null) }
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            userRepo.getRecommendations().fold(
                onSuccess = { list ->
                    if (!isAdded) return@fold
                    if (list.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvAdminRecommendations.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvAdminRecommendations.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                },
                onFailure = { /* Handle error */ }
            )
        }
    }

    private fun deleteTip(tip: HealthRecommendation) {
        lifecycleScope.launch {
            userRepo.deleteRecommendation(tip.id).fold(
                onSuccess = {
                    if (isAdded) {
                        loadRecommendations()
                        Snackbar.make(binding.root, "Tip deleted", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    if (isAdded) Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showTipDialog(existing: HealthRecommendation?) {
        val view = LayoutInflater.from(requireContext()).inflate(com.air.quality.meter.R.layout.dialog_add_recommendation, null)
        val tilTitle = view.findViewById<TextInputLayout>(com.air.quality.meter.R.id.til_tip_title)
        val tilDesc  = view.findViewById<TextInputLayout>(com.air.quality.meter.R.id.til_tip_desc)
        val tilCat   = view.findViewById<TextInputLayout>(com.air.quality.meter.R.id.til_tip_cat)
        val etTitle = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_title)
        val etDesc  = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_desc)
        val etCat   = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_cat)

        if (existing != null) {
            etTitle.setText(existing.title)
            etDesc.setText(existing.description)
            etCat.setText(existing.aqiCategory)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New Health Tip" else "Edit Health Tip")
            .setView(view)
            .setPositiveButton(if (existing == null) "Add" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = etTitle.text?.toString()?.trim().orEmpty()
                val description = etDesc.text?.toString()?.trim().orEmpty()
                val category = etCat.text?.toString()?.trim().orEmpty()

                var hasError = false
                if (title.isBlank()) {
                    tilTitle.error = "Missing info: title required"
                    hasError = true
                } else tilTitle.error = null

                if (description.isBlank()) {
                    tilDesc.error = "Missing info: description required"
                    hasError = true
                } else tilDesc.error = null

                if (category.isBlank()) {
                    tilCat.error = "Missing info: AQI category required"
                    hasError = true
                } else tilCat.error = null

                if (hasError) {
                    if (isAdded) Snackbar.make(binding.root, "Missing info -> error", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val tip = HealthRecommendation(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    aqiCategory = category,
                    iconEmoji = existing?.iconEmoji ?: "💡",
                    updatedAt = System.currentTimeMillis()
                )
                saveTip(tip, isEdit = existing != null)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveTip(tip: HealthRecommendation, isEdit: Boolean) {
        lifecycleScope.launch {
            userRepo.saveRecommendation(tip).fold(
                onSuccess = { _ ->
                    if (isAdded) {
                        loadRecommendations()
                        Snackbar.make(
                            binding.root,
                            if (isEdit) "Tip updated" else "Tip added",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                },
                onFailure = { e ->
                    if (isAdded) Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
