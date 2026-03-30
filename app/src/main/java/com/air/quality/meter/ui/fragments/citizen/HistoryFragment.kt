package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.databinding.FragmentHistoryBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * UC05 — AQI History & Trend Analysis.
 * Shows a MPAndroidChart line chart with daily/weekly/monthly range filters.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val uid  by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val repo by lazy { AQIRepository(AppDatabase.getInstance(requireContext()).aqiRecordDao()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart(binding.lineChart)
        loadRange(days = 1)   // default: Daily

        binding.chipGroupRange.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                binding.chipDaily.id   -> loadRange(1)
                binding.chipWeekly.id  -> loadRange(7)
                binding.chipMonthly.id -> loadRange(30)
            }
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

    private fun loadRange(days: Int) {
        val now  = System.currentTimeMillis()
        val from = now - (days * 24L * 60 * 60 * 1000)

        lifecycleScope.launch {
            repo.getRecordsInRange(uid, from, now).collectLatest { records ->
                if (!isAdded) return@collectLatest
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
