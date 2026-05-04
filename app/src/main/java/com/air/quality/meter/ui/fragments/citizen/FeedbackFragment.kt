package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.air.quality.meter.data.model.FeedbackModel
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.FragmentFeedbackBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * UC07 — Feedback & Issue Reporting.
 * Submits citizen feedback to Firestore /feedback collection.
 */
class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val uid      by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val userRepo = UserRepository()
    private val db       = FirebaseFirestore.getInstance()
    private var reporterName: String = "Citizen"

    private val categories = listOf(
        "General Feedback", "AQI Data Issue", "App Bug",
        "Feature Request", "Health Information", "Other"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Populate category dropdown
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.spinnerCategory.setAdapter(adapter)
        binding.spinnerCategory.setText(categories[0], false)

        if (uid.isBlank()) {
            binding.tvLoginState.text = "Pre-condition: User not logged in"
            binding.btnSubmit.isEnabled = false
        } else {
            binding.tvLoginState.text = "Pre-condition: User logged in"
            binding.btnSubmit.isEnabled = true
            resolveReporterName()
        }

        binding.btnSubmit.setOnClickListener { submitFeedback() }
    }

    private fun submitFeedback() {
        if (uid.isBlank()) {
            Snackbar.make(binding.root, "Please login first to submit feedback.", Snackbar.LENGTH_LONG).show()
            return
        }

        val category = binding.spinnerCategory.text.toString()
        val message  = binding.etMessage.text.toString().trim()

        if (category.isBlank()) {
            binding.tilCategory.error = "Please select a category"
            return
        }
        binding.tilCategory.error = null

        if (message.isBlank()) {
            binding.tilMessage.error = "Please enter your message"
            return
        }
        if (message.length < 8) {
            binding.tilMessage.error = "Please provide at least 8 characters"
            return
        }
        binding.tilMessage.error = null

        setLoading(true)

        val feedback = FeedbackModel(
            id        = UUID.randomUUID().toString(),
            uid       = uid,
            userName  = reporterName.ifBlank { "Citizen" },
            category  = category,
            message   = message,
            timestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            userRepo.submitFeedback(feedback).fold(
                onSuccess = {
                    if (!isAdded) return@fold
                    setLoading(false)
                    binding.etMessage.setText("")
                    binding.spinnerCategory.setText(categories[0], false)
                    Snackbar.make(binding.root, "Feedback submitted successfully. Thank you!", Snackbar.LENGTH_LONG).show()
                },
                onFailure = {
                    if (!isAdded) return@fold
                    setLoading(false)
                    Snackbar.make(binding.root, "Failed to submit feedback. Please try again.", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility  = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled  = !loading
    }

    private fun resolveReporterName() {
        val authUser = FirebaseAuth.getInstance().currentUser
        val authName = authUser?.displayName?.trim().orEmpty()
        val authEmail = authUser?.email?.trim().orEmpty()
        reporterName = when {
            authName.isNotBlank() -> authName
            authEmail.isNotBlank() -> authEmail.substringBefore("@")
            else -> "Citizen"
        }

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val profileName = doc.getString("name")?.trim().orEmpty()
                if (profileName.isNotBlank()) reporterName = profileName
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
