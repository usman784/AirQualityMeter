package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.databinding.FragmentAdminDatasetsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * UC09 — Manage Datasets.
 * Admin can view historical AQI records collected by all users.
 */
class AdminDatasetsFragment : Fragment() {

    private var _binding: FragmentAdminDatasetsBinding? = null
    private val binding get() = _binding!!

    private val aqiRepo by lazy { 
        AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao()) 
    }
    private val adapter = DatasetAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDatasetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvDatasets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDatasets.adapter = adapter
        loadDatasets()
    }

    private fun loadDatasets() {
        lifecycleScope.launch {
            aqiRepo.getAllAqiRecords().fold(
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
