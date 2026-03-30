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
import com.air.quality.meter.ui.fragments.citizen.RecommendationAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.*

/**
 * UC11 — Manage Health Recommendations.
 * Admins can add, view, and delete health tips mapped to AQI categories.
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

        adapter = AdminRecommendationAdapter(onDelete = { tip -> deleteTip(tip) })
        binding.rvAdminRecommendations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdminRecommendations.adapter       = adapter

        loadRecommendations()

        binding.fabAddRecommendation.setOnClickListener { showAddDialog() }
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

    private fun showAddDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(com.air.quality.meter.R.layout.dialog_add_recommendation, null)
        val etTitle = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_title)
        val etDesc  = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_desc)
        val etCat   = view.findViewById<TextInputEditText>(com.air.quality.meter.R.id.et_tip_cat)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Health Tip")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val tip = HealthRecommendation(
                    id = UUID.randomUUID().toString(),
                    title = etTitle.text.toString(),
                    description = etDesc.text.toString(),
                    aqiCategory = etCat.text.toString(),
                    iconEmoji = "💡"
                )
                saveTip(tip)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTip(tip: HealthRecommendation) {
        lifecycleScope.launch {
            userRepo.saveRecommendation(tip).fold(
                onSuccess = { _ ->
                    if (isAdded) {
                        loadRecommendations()
                        Snackbar.make(binding.root, "Tip added", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { /* Handle error */ }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
