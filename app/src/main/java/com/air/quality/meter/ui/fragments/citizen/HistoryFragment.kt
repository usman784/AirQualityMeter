package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.databinding.FragmentHistoryBinding
import com.air.quality.meter.util.NetworkStatus
import com.air.quality.meter.util.NetworkStatusTracker
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * UC05 — AQI History & Trend Analysis.
 * Shows a MPAndroidChart line chart with daily/weekly/monthly range filters.
 */
class HistoryFragment : Fragment() {
    companion object {
        private const val TAG = "HistoryFragment"
    }

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val uid  by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val repo by lazy { AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao()) }
    private lateinit var networkTracker: NetworkStatusTracker
    private var reloadJob: Job? = null
    private var suppressRangeListener = false
    private var selectedDays = 1
    private var isInternetAvailable = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        networkTracker = NetworkStatusTracker(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart(binding.lineChart)

        binding.chipGroupRange.setOnCheckedStateChangeListener { _, ids ->
            if (suppressRangeListener) return@setOnCheckedStateChangeListener
            when (ids.firstOrNull()) {
                binding.chipDaily.id   -> updateSelectedRangeAndReload(1)
                binding.chipWeekly.id  -> updateSelectedRangeAndReload(7)
                binding.chipMonthly.id -> updateSelectedRangeAndReload(30)
            }
        }

        observeNetwork()
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
    }

    private fun observeNetwork() {
        networkTracker.networkStatus
            .onEach { status ->
                isInternetAvailable = status is NetworkStatus.Available
                Log.d(TAG, "Network status changed. isInternetAvailable=$isInternetAvailable")
                reloadHistory()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun updateSelectedRangeAndReload(days: Int) {
        selectedDays = days
        reloadHistory()
    }

    private fun reloadHistory() {
        val now = System.currentTimeMillis()
        val from = now - (selectedDays * 24L * 60 * 60 * 1000)
        Log.d(
            TAG,
            "reloadHistory() uid=$uid selectedDays=$selectedDays from=$from to=$now online=$isInternetAvailable"
        )

        if (uid.isBlank()) {
            Log.w(TAG, "reloadHistory() aborted because uid is blank")
        }

        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            if (isInternetAvailable) {
                binding.tvDataSource.text = "Data source: Internet available - showing Firebase records"
                val syncResult = runCatching { repo.syncUnsyncedToFirestore(uid) }
                val syncSuccess = syncResult.isSuccess
                if (syncSuccess) {
                    Log.d(TAG, "syncUnsyncedToFirestore() success")
                } else {
                    Log.e(
                        TAG,
                        "syncUnsyncedToFirestore() failed: ${syncResult.exceptionOrNull()?.message}",
                        syncResult.exceptionOrNull()
                    )
                }
                val remote = repo.getRecordsInRangeFromFirestore(uid, from, now)
                remote.fold(
                    onSuccess = { records ->
                        Log.d(TAG, "Firebase read success. records=${records.size}")
                        if (syncSuccess) {
                            // Clear local cache after successful sync while online-first mode is active.
                            val clearResult = runCatching { repo.clearLocalRecordsForUser(uid) }
                            if (clearResult.isSuccess) {
                                Log.d(TAG, "clearLocalRecordsForUser() success")
                            } else {
                                Log.e(
                                    TAG,
                                    "clearLocalRecordsForUser() failed: ${clearResult.exceptionOrNull()?.message}",
                                    clearResult.exceptionOrNull()
                                )
                            }
                        }
                        renderRecords(records)
                    },
                    onFailure = { error ->
                        Log.e(
                            TAG,
                            "Firebase read failed: ${error.message}",
                            error
                        )
                        // If cloud read fails despite connectivity, gracefully fallback to local.
                        binding.tvDataSource.text = "Data source: Firebase read failed - showing local cache"
                        val local = repo.getRecordsInRangeLocal(uid, from, now)
                        Log.d(TAG, "Fallback local read count=${local.size}")
                        renderRecords(local)
                    }
                )
            } else {
                binding.tvDataSource.text = "Data source: Offline - showing local records"
                val local = repo.getRecordsInRangeLocal(uid, from, now)
                Log.d(TAG, "Offline local read count=${local.size}")
                renderRecords(local)
            }
        }
    }

    private fun renderRecords(records: List<AQIRecord>) {
        if (!isAdded) return
        if (records.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutStats.visibility = View.GONE
            binding.lineChart.clear()
        } else {
            binding.layoutEmpty.visibility = View.GONE
            renderChart(records)
            renderStats(records)
        }
    }

    private fun setupChart(chart: LineChart) {
        chart.apply {
            description.isEnabled    = false
            legend.isEnabled         = false
            setTouchEnabled(true)
            isDragEnabled            = true
            setScaleEnabled(true)
            setDrawGridBackground(false)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            xAxis.apply {
                position          = XAxis.XAxisPosition.BOTTOM
                textColor         = 0xFF99B4D4.toInt()
                gridColor         = 0xFF1A2D5A.toInt()
                axisLineColor     = 0xFF1A2D5A.toInt()
                textSize          = 10f
                granularity       = 1f
                setDrawGridLines(true)
            }
            axisLeft.apply {
                textColor         = 0xFF99B4D4.toInt()
                gridColor         = 0xFF1A2D5A.toInt()
                axisLineColor     = 0xFF1A2D5A.toInt()
                textSize          = 10f
            }
            axisRight.isEnabled = false
        }
    }

    private fun renderChart(records: List<AQIRecord>) {
        val sdf = SimpleDateFormat(if (records.size > 15) "MM/dd" else "HH:mm", Locale.getDefault())
        val labels = records.map { sdf.format(Date(it.timestamp)) }
        val entries = records.mapIndexed { i, r -> Entry(i.toFloat(), r.aqi) }

        val dataSet = LineDataSet(entries, "AQI").apply {
            color           = 0xFF0DCAF0.toInt()
            valueTextColor  = 0xFF99B4D4.toInt()
            lineWidth       = 2.5f
            circleRadius    = 4f
            setCircleColor(0xFF0DCAF0.toInt())
            setDrawCircleHole(false)
            setDrawValues(false)
            mode            = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor       = 0xFF0DCAF0.toInt()
            fillAlpha       = 30
        }

        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
        binding.lineChart.animateX(600)
    }

    private fun renderStats(records: List<AQIRecord>) {
        binding.layoutStats.visibility = View.VISIBLE
        val values = records.map { it.aqi }
        binding.tvAvgAqi.text = "%.0f".format(values.average())
        binding.tvMinAqi.text = "%.0f".format(values.min())
        binding.tvMaxAqi.text = "%.0f".format(values.max())
    }

    override fun onDestroyView() {
        reloadJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
