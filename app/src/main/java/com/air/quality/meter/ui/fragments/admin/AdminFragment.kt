package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.ui.fragments.citizen.CitizenAdapter
import com.air.quality.meter.ui.fragments.citizen.CitizenModel
import com.air.quality.meter.R
import com.air.quality.meter.databinding.FragmentAdminBinding
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var snapshotListener: ListenerRegistration? = null
    private lateinit var adapter: CitizenAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        listenToCitizens()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_sign_out -> {
                    signOut()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CitizenAdapter(emptyList())
        binding.rvCitizens.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCitizens.adapter = adapter
    }

    /**
     * Real-time listener on the "users" Firestore collection.
     * Shows all citizen documents to admin.
     */
    private fun listenToCitizens() {
        snapshotListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null) {
                    Snackbar.make(
                        binding.root,
                        "Failed to load citizens: ${error.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                val citizens = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CitizenModel::class.java)?.copy(uid = doc.id)
                } ?: emptyList()

                adapter.updateData(citizens)

                if (citizens.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvCitizens.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvCitizens.visibility = View.VISIBLE
                }
            }
    }

    private fun signOut() {
        AuthUI.getInstance().signOut(requireContext())
            .addOnCompleteListener {
                if (!isAdded) return@addOnCompleteListener
                findNavController().navigate(R.id.action_admin_to_splash)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Snackbar.make(
                    binding.root,
                    "Sign out failed: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
    }

    override fun onStop() {
        super.onStop()
        snapshotListener?.remove()
        snapshotListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}