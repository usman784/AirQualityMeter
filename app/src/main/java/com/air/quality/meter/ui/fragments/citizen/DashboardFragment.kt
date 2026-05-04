package com.air.quality.meter.ui.fragments.citizen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.air.quality.meter.databinding.FragmentDashboardBinding
import com.air.quality.meter.ui.viewmodel.AQIDashboardViewModel
import com.air.quality.meter.util.NetworkStatus
import com.air.quality.meter.util.NetworkStatusTracker
import com.air.quality.meter.work.SyncWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * UC02 — Live AQI Dashboard tab.
 * Requests device location, fetches live AQI + weather data, shows offline fallback.
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AQIDashboardViewModel by viewModels()
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var networkTracker: NetworkStatusTracker
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val LOCATION_PERMISSION_CODE = 1001
    private var unsafeThresholdAqi: Float = 151f
    private var lastWarnedRecordId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        fusedLocation = LocationServices.getFusedLocationProviderClient(requireContext())
        networkTracker = NetworkStatusTracker(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        loadAlertThreshold()
        observeState()
        observeNetwork()
        viewModel.loadCached()  // show cached immediately while fetch runs
        requestLocationAndFetch()

        binding.swipeRefresh.setOnRefreshListener { requestLocationAndFetch() }
        binding.btnRefresh.setOnClickListener { requestLocationAndFetch() }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when {
            hour < 12 -> "Good morning 👋"
            hour < 17 -> "Good afternoon 👋"
            else      -> "Good evening 👋"
        }
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AQIDashboardViewModel.UiState.Loading -> showLoading()

                is AQIDashboardViewModel.UiState.Success -> {
                    binding.chipOffline.visibility = View.GONE
                    bindRecord(state.record, state.category)
                }

                is AQIDashboardViewModel.UiState.Offline -> {
                    binding.chipOffline.visibility = View.VISIBLE
                    bindRecord(state.record, state.category)
                }

                is AQIDashboardViewModel.UiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    //Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeNetwork() {
        networkTracker.networkStatus
            .onEach { status ->
                if (!isAdded) return@onEach
                when (status) {
                    NetworkStatus.Available -> {
                        binding.cardOfflineBanner.visibility = View.GONE
                        WorkManager.getInstance(requireContext())
                            .enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
                        requestLocationAndFetch()
                    }
                    NetworkStatus.Unavailable -> {
                        binding.cardOfflineBanner.visibility = View.VISIBLE
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showLoading() {
        binding.tvAqiValue.text = "…"
        binding.tvAqiCategory.text = "Fetching…"
        binding.tvTemperature.text = "—°C"
        binding.tvHumidity.text = "—%"
        binding.tvWind.text = "— m/s"
        binding.tvPm25.text = "— µg/m³"
        binding.tvAdvice.text = "Loading…"
        binding.tvClassificationSummary.text = "Loading AQI classification…"
        binding.tvClassificationTitle.text = "🧭  AQI Classification"
        binding.tvAdviceTitle.text = "💡  Health Advice"
    }

    private fun bindRecord(record: com.air.quality.meter.data.model.AQIRecord, category: com.air.quality.meter.util.AQIClassifier.AQICategory) {
        // AQI value + color
        binding.tvAqiValue.text = record.aqi.toInt().toString()
        binding.tvAqiCategory.text = category.name
        binding.tvBadge.text = category.name
        binding.tvAqiValue.setTextColor(android.graphics.Color.parseColor(category.colorHex))

        // Badge background color
        try {
            binding.cardBadge.setCardBackgroundColor(android.graphics.Color.parseColor(category.colorHex))
        } catch (_: Exception) {}

        // Text color contrast — dark bg = white text, light bg = black text
        val luminance = android.graphics.Color.luminance(android.graphics.Color.parseColor(category.colorHex))
        binding.tvBadge.setTextColor(if (luminance > 0.5f) android.graphics.Color.BLACK else android.graphics.Color.WHITE)

        // Weather params
        binding.tvTemperature.text = "%.1f°C".format(record.temperature)
        binding.tvHumidity.text = "%.0f%%".format(record.humidity)
        binding.tvWind.text = "%.1f m/s".format(record.windSpeed)
        binding.tvPm25.text = "%.1f µg/m³".format(record.pm25)

        // Health advice
        binding.tvAdvice.text = "${category.emoji}  ${category.advice}"
        binding.tvClassificationSummary.text =
            "AQI ${record.aqi.toInt()} is classified as ${category.name} (range ${category.aqiRange})."
        bindAiPoweredLabels()

        // Location
        if (record.location.isNotBlank()) binding.tvLocation.text = record.location

        // Last updated
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        binding.tvLastUpdated.text = "Last updated: ${sdf.format(Date(record.timestamp))}"

        showInAppFallbackAlertIfNeeded(record, category)
    }

    private fun bindAiPoweredLabels() {
        binding.tvClassificationTitle.text = "🧭  AQI Classification"
        binding.tvAdviceTitle.text = "💡  Health Advice (AI-Powered)"
    }

    private fun loadAlertThreshold() {
        firestore.collection("settings").document("aqi_thresholds")
            .get()
            .addOnSuccessListener { doc ->
                // "sensitive" is treated as 101–150 upper bound in current admin settings.
                // In-app unsafe alert should start when AQI becomes 151+.
                val threshold = (doc.getLong("sensitive") ?: 150L).toFloat() + 1f
                unsafeThresholdAqi = threshold
            }
            .addOnFailureListener {
                unsafeThresholdAqi = 151f
            }
    }

    /**
     * In-app fallback alert when notifications are disabled.
     */
    private fun showInAppFallbackAlertIfNeeded(
        record: com.air.quality.meter.data.model.AQIRecord,
        category: com.air.quality.meter.util.AQIClassifier.AQICategory
    ) {
        if (record.aqi < unsafeThresholdAqi) return
        if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) return
        if (lastWarnedRecordId == record.id) return

        lastWarnedRecordId = record.id
        Snackbar.make(
            binding.root,
            "High AQI (${record.aqi.toInt()} - ${category.name}). Notifications are off, so showing in-app alert: ${category.advice}",
            Snackbar.LENGTH_LONG
        ).show()
    }

    // ─── Location ───────────────────────────────────────────────────────────

    private fun requestLocationAndFetch() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)
            return
        }
        fusedLocation.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val locName = getLocationName(location.latitude, location.longitude)
                viewModel.fetchAQI(location.latitude, location.longitude, locName)
            } else {
                // Default to Islamabad if location unavailable (common FYP test location)
                viewModel.fetchAQI(33.7294, 73.0931, "Islamabad")
            }
        }.addOnFailureListener {
            viewModel.fetchAQI(33.7294, 73.0931, "Islamabad")
        }
    }

    private fun getLocationName(lat: Double, lon: Double): String {
        return try {
            val geo = Geocoder(requireContext(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geo.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: "My Location"
        } catch (_: Exception) { "My Location" }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestLocationAndFetch()
        } else {
            viewModel.fetchAQI(33.7294, 73.0931, "Islamabad")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
