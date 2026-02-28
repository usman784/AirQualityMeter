package com.air.quality.meter.ui.fragments.splash

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.air.quality.meter.R
import com.air.quality.meter.databinding.FragmentSplashBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

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
        else checkRoleAndNavigate(auth.currentUser!!.uid)
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
            checkRoleAndNavigate(user.uid)
        } else {
            val errorMsg = result.idpResponse?.error?.message
                ?: getString(R.string.auth_error)
            showError(errorMsg)
            // Re-launch after short delay so user can retry
            binding.root.postDelayed({ launchFirebaseUI() }, 4000)
        }
    }

    private fun checkRoleAndNavigate(uid: String) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("admins").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                binding.progressBar.visibility = View.GONE
                if (doc.exists()) findNavController().navigate(R.id.action_splash_to_admin)
                else              findNavController().navigate(R.id.action_splash_to_citizen)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                showError("Role check failed: ${e.message}")
                findNavController().navigate(R.id.action_splash_to_citizen)
            }
    }

    private fun showError(msg: String) {
        if (isAdded) Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

