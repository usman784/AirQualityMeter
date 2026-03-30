package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.model.UserModel
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.FragmentAdminUsersBinding
import com.air.quality.meter.ui.fragments.citizen.CitizenModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.UUID
import com.firebase.ui.auth.AuthUI
import androidx.navigation.fragment.findNavController
import com.air.quality.meter.R
import android.util.Log

/**
 * UC08 — User Management.
 * Shows paginated list of registered citizens with search, view and delete.
 */
class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    private val userRepo = UserRepository()
    private lateinit var adapter: AdminCitizenAdapter
    private var allUsers = listOf<CitizenModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminCitizenAdapter(
            onDelete = { user -> confirmDelete(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter       = adapter

        loadUsers()

        binding.btnLogout.setOnClickListener { signOut() }

        // Search filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterUsers(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun signOut() {
        AuthUI.getInstance().signOut(requireContext())
            .addOnCompleteListener {
                if (!isAdded) return@addOnCompleteListener
                findNavController().navigate(R.id.action_admin_to_splash)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Snackbar.make(binding.root, "Logout failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            // Debugging fetch: getting all users to ensure we see them regardless of role
            userRepo.getAllCitizens().fold(
                onSuccess = { users ->
                    if (!isAdded) return@fold
                    Log.d("AdminUsers", "Success: Fetched ${users.size} users")
                    // Map UserModel -> CitizenModel for UI adapter
                    val citizens = users.map { u: UserModel ->
                        CitizenModel(
                            uid = u.uid.ifBlank { UUID.randomUUID().toString() }, // Ensure UID for DIFF
                            name = u.name,
                            email = u.email,
                            age = u.age,
                            gender = u.gender,
                            cellNumber = u.fullPhone.ifBlank { u.cellNumber }
                        )
                    }
                    allUsers = citizens
                    binding.tvTotalUsers.text = citizens.size.toString()
                    // Count users registered in last 24 hours
                    val twentyFourHoursAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
                    val newToday = users.count { it.createdAt >= twentyFourHoursAgo }
                    binding.tvNewToday.text   = newToday.toString()
                    Log.d("AdminUsers", "Showing list with ${citizens.size} citizens")
                    updateList(citizens)
                },
                onFailure = { e ->
                    if (!isAdded) return@fold
                    Log.e("AdminUsers", "Error fetching users", e)
                    Snackbar.make(binding.root, "Failed to load: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun filterUsers(query: String) {
        val filtered = if (query.isBlank()) allUsers
        else allUsers.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.email.contains(query, ignoreCase = true)
        }
        updateList(filtered)
    }

    private fun updateList(users: List<CitizenModel>) {
        if (users.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvUsers.visibility     = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvUsers.visibility     = View.VISIBLE
            adapter.submitList(users)
        }
    }

    private fun confirmDelete(user: CitizenModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete User")
            .setMessage("Delete ${'$'}{user.name}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(user: CitizenModel) {
        lifecycleScope.launch {
            userRepo.deleteUser(user.uid).fold(
                onSuccess = {
                    if (!isAdded) return@fold
                    loadUsers()
                    Snackbar.make(binding.root, "${'$'}{user.name} deleted", Snackbar.LENGTH_SHORT).show()
                },
                onFailure = {
                    if (!isAdded) return@fold
                    Snackbar.make(binding.root, "Delete failed", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
