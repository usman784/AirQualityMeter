package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.air.quality.meter.databinding.FragmentAdminActivityBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * UC12 — Monitor System Activity.
 * dashboard for admin to see total users, total records, and recent activity logs.
 */
class AdminActivityFragment : Fragment() {

    private var _binding: FragmentAdminActivityBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()
    }

    private fun loadStats() {
        // Users count
        db.collection("users").get().addOnSuccessListener { 
            if (isAdded) binding.tvStatUsers.text = it.size().toString()
        }

        // Records count
        db.collection("aqi_records").get().addOnSuccessListener { 
            if (isAdded) binding.tvStatRecords.text = it.size().toString()
        }

        // Feedback count
        db.collection("feedback").get().addOnSuccessListener { 
            if (isAdded) binding.tvStatFeedback.text = it.size().toString()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
