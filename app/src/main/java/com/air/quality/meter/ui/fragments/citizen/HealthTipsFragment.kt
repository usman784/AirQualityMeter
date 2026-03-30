package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.FragmentHealthTipsBinding
import com.air.quality.meter.util.AQIClassifier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * UC06 — Health Recommendations.
 * Shows admin-managed tips filtered/sorted by current AQI category.
 * Also shows the current AQI as a contextual banner.
 */
class HealthTipsFragment : Fragment() {

    private var _binding: FragmentHealthTipsBinding? = null
    private val binding get() = _binding!!

    private val uid       by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val userRepo  = UserRepository()
    private val aqiRepo   by lazy { AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao()) }
    private lateinit var adapter: RecommendationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHealthTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecommendationAdapter()
        binding.rvRecommendations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecommendations.adapter       = adapter

        loadCurrentAqi()
        loadRecommendations()
    }

    private fun loadCurrentAqi() {
        lifecycleScope.launch {
            val latest = aqiRepo.getLatestRecord(uid)
            if (!isAdded) return@launch
            if (latest != null) {
                val cat = AQIClassifier.classify(latest.aqi)
                binding.tvCurrentEmoji.text     = cat.emoji
                binding.tvCurrentAqiValue.text  = "${latest.aqi.toInt()}  •  ${cat.name}"
            }
        }
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            val result = userRepo.getRecommendations()
            if (!isAdded) return@launch
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        binding.layoutEmpty.visibility    = View.VISIBLE
                        binding.rvRecommendations.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility    = View.GONE
                        binding.rvRecommendations.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                },
                onFailure = {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvRecommendations.visibility = View.GONE
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
