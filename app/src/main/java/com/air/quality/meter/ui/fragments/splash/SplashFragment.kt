package com.air.quality.meter.ui.fragments.splash

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.air.quality.meter.R
import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.data.repository.UserRepository
import com.air.quality.meter.databinding.FragmentSplashBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()
    private val userRepo = UserRepository()

    // Splash is visible for at least this long before auth fires (ms)
    private val SPLASH_MIN_DURATION = 2200L

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result: FirebaseAuthUIAuthenticationResult ->
        onSignInResult(result)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runSplashAnimation()
        // Auth check fires only after minimum splash duration
        binding.root.postDelayed({ checkAuthState() }, SPLASH_MIN_DURATION)
    }

    // ─── Splash entry animation ───────────────────────────────────────────────

    private fun runSplashAnimation() {
        val views = listOf(
            binding.iconBg,
            binding.tvAppName,
            binding.tvTagline
        )
        views.forEach { v ->
            v.alpha = 0f
            v.scaleX = 0.6f
            v.scaleY = 0.6f
        }
        binding.progressBar.alpha = 0f
        binding.tvStatus.alpha    = 0f

        // Logo — bounce in
        binding.iconBg.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(100).setDuration(600)
            .setInterpolator(OvershootInterpolator(1.5f)).start()

        // App name — fade + slide up
        binding.tvAppName.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationYBy(-10f)
            .setStartDelay(400).setDuration(500).start()

        // Tagline — fade in
        binding.tvTagline.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(650).setDuration(500).start()

        // Progress + status — fade in last
        binding.progressBar.animate()
            .alpha(1f).setStartDelay(1000).setDuration(400).start()

        binding.tvStatus.animate()
            .alpha(1f).setStartDelay(1200).setDuration(400).start()
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────

    private fun checkAuthState() {
        if (!isAdded) return
        if (auth.currentUser == null) launchFirebaseUI()
        else checkRoleAndNavigate(auth.currentUser!!.uid, logLoginEvent = false)
    }

    private fun launchFirebaseUI() {
        val providers = listOf(
            AuthUI.IdpConfig.GoogleBuilder().build(),
            AuthUI.IdpConfig.EmailBuilder().build()
        )
        signInLauncher.launch(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setLogo(R.drawable.ic_app_logo)
                .setTheme(R.style.FirebaseAuthUITheme)
                .build()
        )
    }


    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val user = auth.currentUser ?: return
            checkRoleAndNavigate(user.uid, logLoginEvent = true)
        } else {
            val errorMsg = result.idpResponse?.error?.message
                ?: getString(R.string.auth_error)
            showError(errorMsg)
            // Re-launch after short delay so user can retry
            binding.root.postDelayed({ launchFirebaseUI() }, 4000)
        }
    }

    private fun checkRoleAndNavigate(uid: String, logLoginEvent: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val role = resolveRole(uid)
                val isActive = resolveAccountActive(uid, role)
                if (!isActive) {
                    if (!isAdded) return@runCatching
                    auth.signOut()
                    binding.progressBar.visibility = View.GONE
                    showError("Your account is deactivated. Contact admin to reactivate.")
                    binding.root.postDelayed({ if (isAdded) launchFirebaseUI() }, 2500)
                    return@runCatching
                }
                // Requirement: admin accounts must stay in admin/admins collections only.
                // Do not auto-create admin profile under /users.
                if (role != "admin") {
                    upsertUserDocument(uid, role)
                }
                if (logLoginEvent) {
                    userRepo.logActivity(
                        ActivityLog(
                            uid = uid,
                            action = "USER_LOGIN",
                            details = "Role: $role login successful",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                navigateByRole(role)
            }.onFailure { e ->
                if (!isAdded) return@onFailure
                binding.progressBar.visibility = View.GONE
                showError("Role check failed: ${e.message}")
                auth.signOut()
                binding.root.postDelayed({ if (isAdded) launchFirebaseUI() }, 2500)
            }
        }
    }

    /**
     * Resolve role from /users first, with backward-compatibility fallbacks:
     *  - /admins
     *  - /admin
     *
     * Supports both patterns:
     *  1) document id == FirebaseAuth uid
     *  2) random document ids with uid/email fields inside the document
     *
     * If any source marks the user as admin, admin wins.
     */
    private suspend fun resolveRole(uid: String): String {
        val currentEmail = auth.currentUser?.email
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()

        val userDoc = runCatching {
            db.collection("users").document(uid).get().await()
        }.getOrNull()

        val roleFromUsers = userDoc?.getString("role")
            ?.trim()
            ?.lowercase(Locale.US)

        val adminCollections = listOf("admins", "admin")
        val adminFromCollections = adminCollections.any { collection ->
            isAdminInCollection(collection, uid, currentEmail)
        }

        return when {
            adminFromCollections -> "admin"
            roleFromUsers == "admin" -> "admin"
            else -> "citizen"
        }
    }

    private suspend fun isAdminInCollection(collection: String, uid: String, email: String): Boolean {
        val byDocId = runCatching {
            db.collection(collection).document(uid).get().await().exists()
        }.getOrDefault(false)
        if (byDocId) return true

        val byUidField = runCatching {
            db.collection(collection)
                .whereEqualTo("uid", uid)
                .limit(1)
                .get()
                .await()
                .documents
                .isNotEmpty()
        }.getOrDefault(false)
        if (byUidField) return true

        if (email.isNotBlank()) {
            val byEmailField = runCatching {
                db.collection(collection)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .isNotEmpty()
            }.getOrDefault(false)
            if (byEmailField) return true
        }

        return false
    }

    private suspend fun resolveAccountActive(uid: String, role: String): Boolean {
        val currentEmail = auth.currentUser?.email
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()

        // Citizen account state lives in /users/{uid}
        if (role != "admin") {
            val userDoc = runCatching {
                db.collection("users").document(uid).get().await()
            }.getOrNull()
            return userDoc?.getBoolean("isActive") ?: true
        }

        // Admin account state can be in /admins or /admin (legacy).
        val collections = listOf("admins", "admin")
        collections.forEach { collection ->
            val byDocId = runCatching {
                db.collection(collection).document(uid).get().await()
            }.getOrNull()
            if (byDocId?.exists() == true) return byDocId.getBoolean("isActive") ?: true

            val byUid = runCatching {
                db.collection(collection)
                    .whereEqualTo("uid", uid)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
            }.getOrNull()
            if (byUid != null) return byUid.getBoolean("isActive") ?: true

            if (currentEmail.isNotBlank()) {
                val byEmail = runCatching {
                    db.collection(collection)
                        .whereEqualTo("email", currentEmail)
                        .limit(1)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                }.getOrNull()
                if (byEmail != null) return byEmail.getBoolean("isActive") ?: true
            }
        }

        return true
    }

    /**
     * Keep /users/{uid} consistent so role-based rules/routing work reliably.
     */
    private suspend fun upsertUserDocument(uid: String, role: String) {
        val current = auth.currentUser ?: return
        val now = System.currentTimeMillis()
        val docRef = db.collection("users").document(uid)
        val exists = runCatching { docRef.get().await().exists() }.getOrDefault(false)

        val payload = mutableMapOf<String, Any>(
            "uid" to uid,
            "role" to role,
            "isActive" to true,
            "email" to (current.email ?: ""),
            "name" to (current.displayName ?: ""),
            "lastLoginAt" to now
        )
        if (!exists) payload["createdAt"] = now

        docRef.set(payload, SetOptions.merge()).await()
    }

    private fun navigateByRole(role: String) {
        if (!isAdded) return
        binding.progressBar.visibility = View.GONE

        val action = if (role == "admin") {
            R.id.action_splash_to_admin
        } else {
            R.id.action_splash_to_citizen
        }

        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.splashFragment) {
            navController.navigate(action)
        }
    }

    private fun showError(msg: String) {
        if (!isAdded) return
        val snack = Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)

        snack.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.color_error))
        snack.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_error))
        snack.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.white))

        val snackText = snack.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackText?.maxLines = 4
        snackText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_error))

        val topMargin = (16 * resources.displayMetrics.density).toInt()
        val lp = snack.view.layoutParams
        when (lp) {
            is FrameLayout.LayoutParams -> {
                lp.gravity = Gravity.TOP
                lp.topMargin = topMargin + binding.root.rootWindowInsets?.systemWindowInsetTop.orZero()
                snack.view.layoutParams = lp
            }
            is CoordinatorLayout.LayoutParams -> {
                lp.gravity = Gravity.TOP
                lp.topMargin = topMargin + binding.root.rootWindowInsets?.systemWindowInsetTop.orZero()
                snack.view.layoutParams = lp
            }
            else -> {
                // Fallback to top toast if parent layout params don't support gravity change.
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).apply {
                    setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120)
                    show()
                }
                return
            }
        }
        snack.show()
    }

    private fun Int?.orZero(): Int = this ?: 0

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
