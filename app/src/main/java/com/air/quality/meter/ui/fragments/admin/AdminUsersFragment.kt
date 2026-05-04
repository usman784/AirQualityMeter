package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.model.UserModel
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.DialogAdminManageUserBinding
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

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
    private val currentUid: String by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    private var usersListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminCitizenAdapter(
            onManage = { user -> showManageDialog(user) },
            onToggleActive = { user -> confirmToggleActive(user) },
            onDelete = { user -> confirmDelete(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter       = adapter

        // Setup real-time listener instead of loadUsers()
        setupSnapshotListener()

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

    private fun setupSnapshotListener() {
        usersListener = userRepo.listenerAllCitizens(
            onUpdate = { users ->
                if (!isAdded) return@listenerAllCitizens

                lifecycleScope.launch {
                    try {
                        val adminUidResult = userRepo.getAdminUids()
                        val adminUids = adminUidResult.getOrNull().orEmpty()

                        Log.d("AdminUsers", "=== SNAPSHOT UPDATE ===")
                        Log.d("AdminUsers", "Total users from Firebase: ${users.size}")

                        val citizens = users
                            .filter { it.uid != currentUid }
                            .filter { it.uid !in adminUids }
                            .map { u: UserModel ->
                                Log.d("AdminUsers", "User from Firebase: uid=${u.uid}, name=${u.name}, isActive=${u.isActive}")

                                val citizen = CitizenModel(
                                    uid = u.uid.ifBlank { UUID.randomUUID().toString() },
                                    name = u.name,
                                    email = u.email,
                                    role = u.role.ifBlank { "citizen" },
                                    isActive = u.isActive,
                                    age = u.age,
                                    gender = u.gender,
                                    cellNumber = u.cellNumber,
                                    countryCode = u.countryCode.ifBlank { "+92" },
                                    fullPhone = u.fullPhone.ifBlank { u.cellNumber },
                                    createdAt = u.createdAt
                                )

                                Log.d("AdminUsers", "Mapped to CitizenModel: uid=${citizen.uid}, name=${citizen.name}, isActive=${citizen.isActive}")
                                citizen
                            }

                        Log.d("AdminUsers", "After filtering: ${citizens.size} citizens")
                        citizens.forEach { c ->
                            Log.d("AdminUsers", "Final citizen list: ${c.name} - isActive=${c.isActive}")
                        }

                        allUsers = citizens
                        binding.tvTotalUsers.text = citizens.size.toString()

                        val twentyFourHoursAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
                        val newToday = users
                            .filter { it.uid !in adminUids && it.uid != currentUid }
                            .count { it.createdAt >= twentyFourHoursAgo }
                        binding.tvNewToday.text = newToday.toString()

                        filterUsers(binding.etSearch.text?.toString().orEmpty())
                    } catch (e: Exception) {
                        Log.e("AdminUsers", "Error in snapshot listener", e)
                    }
                }
            },
            onError = { error ->
                if (!isAdded) return@listenerAllCitizens
                Log.e("AdminUsers", "Snapshot listener error", error)
                Snackbar.make(binding.root, "Failed to load users: ${error.message}", Snackbar.LENGTH_SHORT).show()
            }
        )
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
            .setMessage("Delete ${user.name.ifBlank { user.email }}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmToggleActive(user: CitizenModel) {
        val targetActive = !user.isActive
        val actionText = if (targetActive) "activate" else "deactivate"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${actionText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }} User")
            .setMessage("Do you want to $actionText ${user.name.ifBlank { user.email }}?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    userRepo.setCitizenActive(user.uid, targetActive).fold(
                        onSuccess = {
                            if (!isAdded) return@fold
                            // Snapshot listener will automatically update the UI
                            // No need to call loadUsers() - listener handles it
                            Snackbar.make(
                                binding.root,
                                if (targetActive) "User activated" else "User deactivated",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { e ->
                            if (!isAdded) return@fold
                            Snackbar.make(binding.root, "Status update failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageDialog(user: CitizenModel) {
        val dialogBinding = DialogAdminManageUserBinding.inflate(layoutInflater)

        // Populate all fields
        dialogBinding.etManageEmail.setText(user.email)
        dialogBinding.etManageName.setText(user.name)
        dialogBinding.etManageAge.setText(user.age)
        dialogBinding.etManageGender.setText(user.gender)
        dialogBinding.etManageCountryCode.setText(user.countryCode.ifBlank { "+92" })
        dialogBinding.etManageCell.setText(user.cellNumber)
        dialogBinding.switchManageActive.isChecked = user.isActive

        // Setup role dropdown
        val roleOptions = arrayOf("citizen", "admin")
        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roleOptions)
        dialogBinding.etManageRole.setAdapter(roleAdapter)
        dialogBinding.etManageRole.setText(user.role.ifBlank { "citizen" }, false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update User Profile")
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialogBinding.btnManageSave
            val cancelButton = dialogBinding.btnManageCancel
            val defaultSaveText = saveButton.text

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }

            saveButton.setOnClickListener {
                val name = dialogBinding.etManageName.text?.toString()?.trim().orEmpty()
                val age = dialogBinding.etManageAge.text?.toString()?.trim().orEmpty()
                val gender = dialogBinding.etManageGender.text?.toString()?.trim().orEmpty()
                val countryCode = dialogBinding.etManageCountryCode.text?.toString()?.trim().orEmpty()
                val cell = dialogBinding.etManageCell.text?.toString()?.trim().orEmpty()
                val role = dialogBinding.etManageRole.text
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.US)
                    .orEmpty()
                val isActive = dialogBinding.switchManageActive.isChecked

                if (name.isBlank()) {
                    dialogBinding.etManageName.error = "Name is required"
                    return@setOnClickListener
                }
                dialogBinding.etManageName.error = null

                if (age.isNotBlank()) {
                    val ageNumber = age.toIntOrNull()
                    if (ageNumber == null || ageNumber !in 1..120) {
                        dialogBinding.etManageAge.error = "Enter valid age (1-120)"
                        return@setOnClickListener
                    }
                }
                dialogBinding.etManageAge.error = null

                if (gender.isBlank()) {
                    dialogBinding.etManageGender.error = "Gender is required"
                    return@setOnClickListener
                }
                dialogBinding.etManageGender.error = null

                if (cell.isBlank()) {
                    dialogBinding.etManageCell.error = "Cell number is required"
                    return@setOnClickListener
                }
                if (!Patterns.PHONE.matcher(cell).matches()) {
                    dialogBinding.etManageCell.error = "Enter a valid cell number"
                    return@setOnClickListener
                }
                dialogBinding.etManageCell.error = null

                if (role != "citizen" && role != "admin") {
                    dialogBinding.etManageRole.error = "Role must be citizen or admin"
                    return@setOnClickListener
                }
                dialogBinding.etManageRole.error = null

                // Keep feedback simple: disable buttons while request is in-flight.
                saveButton.isEnabled = false
                cancelButton.isEnabled = false
                saveButton.text = "Saving..."

                lifecycleScope.launch {
                    userRepo.adminUpdateUser(
                        uid = user.uid,
                        name = name,
                        age = age,
                        gender = gender,
                        cellNumber = cell,
                        countryCode = countryCode.ifBlank { "+92" },
                        role = role,
                        isActive = isActive
                    ).fold(
                        onSuccess = {
                            if (!isAdded) return@fold

                            dialog.dismiss()
                            // Snapshot listener will automatically handle updates
                            // No need for applyLocalUpdate - listener detects changes
                            Snackbar.make(binding.root, "User profile updated successfully", Snackbar.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            if (!isAdded) return@fold

                            saveButton.isEnabled = true
                            cancelButton.isEnabled = true
                            saveButton.text = defaultSaveText

                            Log.e("ManageUser", "Update failed", e)
                            Snackbar.make(binding.root, "Update failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        dialog.show()
    }


    private fun deleteUser(user: CitizenModel) {
        lifecycleScope.launch {
            userRepo.deleteUser(user.uid).fold(
                onSuccess = {
                    if (!isAdded) return@fold
                    // Snapshot listener will automatically remove the user from UI
                    Snackbar.make(binding.root, "${user.name.ifBlank { user.email }} deleted", Snackbar.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    if (!isAdded) return@fold
                    Snackbar.make(binding.root, "Delete failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        usersListener?.remove()  // Clean up the real-time listener
        super.onDestroyView()
        _binding = null
    }
}
