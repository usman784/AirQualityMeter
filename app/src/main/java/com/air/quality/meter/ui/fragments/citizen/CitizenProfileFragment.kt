package com.air.quality.meter.ui.fragments.citizen

// Image picker imports — disabled until feature is enabled
// import android.app.Activity
// import android.content.Intent
// import android.net.Uri
// import android.provider.MediaStore
// import androidx.activity.result.contract.ActivityResultContracts

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.air.quality.meter.R
import com.air.quality.meter.databinding.FragmentCitizenProfileBinding
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CitizenProfileFragment : Fragment() {

    private var _binding: FragmentCitizenProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // ─── Image Picker (disabled — re-enable when storage feature is added) ────
    // private val pickImageLauncher =
    //     registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    //         if (result.resultCode == Activity.RESULT_OK) {
    //             val uri: Uri? = result.data?.data
    //             if (uri != null) {
    //                 binding.ivAvatar.setImageURI(uri)
    //                 binding.ivAvatar.clearColorFilter()
    //             }
    //         }
    //     }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCitizenProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupGenderDropdown()
        // setupAvatarPicker() — disabled until image picker feature is re-enabled
        loadProfile()
        setupButtons()
        runEntryAnimation()
    }

    // ─── Avatar initials ──────────────────────────────────────────────────────
    // Shows first letter of name, or email initial as fallback

    private fun updateAvatarInitials(name: String?, email: String?) {
        val initial = when {
            !name.isNullOrBlank()  -> name.trim().first().uppercaseChar().toString()
            !email.isNullOrBlank() -> email.trim().first().uppercaseChar().toString()
            else                   -> "?"
        }
        binding.tvAvatarInitials.text = initial
    }

    // ─── Avatar picker (disabled — kept for future use) ───────────────────────
    // private fun setupAvatarPicker() {
    //     binding.flAvatar.setOnClickListener {
    //         val intent = Intent(Intent.ACTION_PICK)
    //         intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
    //         pickImageLauncher.launch(intent)
    //     }
    // }

    // ─── Entry Animation ──────────────────────────────────────────────────────

    private fun runEntryAnimation() {
        listOf(binding.tvAvatarInitials, binding.tvDisplayName, binding.tvUserEmail)
            .forEachIndexed { i, v ->
                v.alpha = 0f
                v.translationY = 16f
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay((i * 80).toLong()).setDuration(400).start()
            }
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_sign_out -> { signOut(); true }
                else -> false
            }
        }
    }

    // ─── Gender picker (AlertDialog — works on all devices) ──────────────────

    private val genderOptions by lazy { resources.getStringArray(R.array.gender_options) }

    private fun setupGenderDropdown() {
        binding.etGender.setOnClickListener { showGenderPicker() }
        binding.tilGender.setOnClickListener { showGenderPicker() }
    }

    private fun showGenderPicker() {
        val current      = binding.etGender.text.toString()
        val checkedIndex = genderOptions.indexOf(current)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.hint_gender))
            .setSingleChoiceItems(genderOptions, checkedIndex) { dialog, which ->
                binding.etGender.setText(genderOptions[which])
                binding.tilGender.error = null
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.delete_confirm_no)) { d, _ -> d.dismiss() }
            .show()
    }

    // ─── Load Profile ─────────────────────────────────────────────────────────

    private fun loadProfile() {
        val user = auth.currentUser ?: return
        val email = user.email ?: ""
        binding.tvUserEmail.text   = email
        // Show Firebase display name or default until Firestore data loads
        val firebaseName = user.displayName?.ifBlank { null }
        binding.tvDisplayName.text = firebaseName ?: getString(R.string.profile_default_name)
        updateAvatarInitials(firebaseName, email)

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                if (doc.exists()) {
                    val name   = doc.getString("name")   ?: ""
                    val age    = doc.getString("age")    ?: ""
                    val gender = doc.getString("gender") ?: ""
                    val cell   = doc.getString("cellNumber") ?: ""
                    val code   = doc.getString("countryCode") ?: "+92"

                    // Fill all form fields
                    binding.etName.setText(name)
                    binding.etAge.setText(age)
                    binding.etGender.setText(gender)
                    binding.etCell.setText(cell)
                    try { binding.ccp.setCountryForPhoneCode(code.removePrefix("+").toInt()) }
                    catch (_: Exception) {}

                    // ── Update hero header with saved name ────────────────
                    if (name.isNotBlank()) {
                        binding.tvDisplayName.text = name
                        updateAvatarInitials(name, email)
                    }
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                showSnack(getString(R.string.profile_load_failed) + ": ${e.message}", isError = true)
            }
    }

    // ─── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private fun validateInputs(): Boolean {
        var valid = true
        val name   = binding.etName.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val gender = binding.etGender.text.toString().trim()
        val cell   = binding.etCell.text.toString().trim()

        if (name.isEmpty()) { binding.tilName.error = getString(R.string.error_name_empty); valid = false }
        else binding.tilName.error = null

        if (ageStr.isEmpty()) { binding.tilAge.error = getString(R.string.error_age_empty); valid = false }
        else {
            val age = ageStr.toIntOrNull()
            if (age == null || age < 1 || age > 120) {
                binding.tilAge.error = getString(R.string.error_age_invalid); valid = false
            } else binding.tilAge.error = null
        }

        if (gender.isEmpty()) { binding.tilGender.error = getString(R.string.error_gender_empty); valid = false }
        else binding.tilGender.error = null

        if (cell.isEmpty()) { showCellError(getString(R.string.error_cell_empty)); valid = false }
        else if (!Patterns.PHONE.matcher(cell).matches()) {
            showCellError(getString(R.string.error_cell_invalid)); valid = false
        } else hideCellError()

        return valid
    }

    private fun showCellError(msg: String) {
        binding.tvCellError.text = msg
        binding.tvCellError.visibility = View.VISIBLE
    }

    private fun hideCellError() {
        binding.tvCellError.visibility = View.GONE
    }

    // ─── Save Profile ─────────────────────────────────────────────────────────

    private fun saveProfile() {
        if (!validateInputs()) return
        val user  = auth.currentUser ?: return
        val name  = binding.etName.text.toString().trim()
        val phone = binding.etCell.text.toString().trim()
        val code  = "+${binding.ccp.selectedCountryCode}"

        val data = hashMapOf(
            "uid"         to user.uid,
            "name"        to name,
            "age"         to binding.etAge.text.toString().trim(),
            "gender"      to binding.etGender.text.toString().trim(),
            "cellNumber"  to phone,
            "countryCode" to code,
            "fullPhone"   to "$code$phone",
            "email"       to (user.email ?: "")
        )

        binding.btnSave.isEnabled = false
        binding.tvDisplayName.text = name
        updateAvatarInitials(name, user.email)

        db.collection("users").document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                binding.btnSave.isEnabled = true
                showSnack(getString(R.string.profile_saved))
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                binding.btnSave.isEnabled = true
                showSnack(getString(R.string.profile_save_failed) + ": ${e.message}", isError = true)
            }
    }

    // ─── Delete Account ───────────────────────────────────────────────────────

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message))
            .setPositiveButton(getString(R.string.delete_confirm_yes)) { d, _ -> d.dismiss(); deleteAccount() }
            .setNegativeButton(getString(R.string.delete_confirm_no))   { d, _ -> d.dismiss() }
            .setCancelable(true).show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        binding.btnDelete.isEnabled = false
        db.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        showSnack(getString(R.string.account_deleted))
                        findNavController().navigate(R.id.action_citizen_to_splash)
                    }
                    .addOnFailureListener { e ->
                        if (!isAdded) return@addOnFailureListener
                        binding.btnDelete.isEnabled = true
                        showSnack(getString(R.string.account_delete_failed) + ": ${e.message}", isError = true)
                    }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                binding.btnDelete.isEnabled = true
                showSnack(getString(R.string.account_delete_failed) + ": ${e.message}", isError = true)
            }
    }

    // ─── Sign Out ─────────────────────────────────────────────────────────────

    private fun signOut() {
        AuthUI.getInstance().signOut(requireContext()).addOnCompleteListener {
            if (!isAdded) return@addOnCompleteListener
            findNavController().navigate(R.id.action_citizen_to_splash)
        }
    }

    // ─── Snackbar helper ─────────────────────────────────────────────────────

    private fun showSnack(msg: String, isError: Boolean = false) {
        val snack = Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
        if (isError) snack.setBackgroundTint(requireContext().getColor(R.color.color_error))
        snack.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

