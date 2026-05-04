package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.databinding.FragmentAdminActivityBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Locale

/**
 * UC12 — Monitor System Activity.
 * Admin monitors activity logs with real-time updates and summary statistics.
 */
class AdminActivityFragment : Fragment() {

    private var _binding: FragmentAdminActivityBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: AdminActivityLogAdapter
    private var logsListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLogsList()
        loadSummaryStats()
        startRealtimeLogsListener()
    }

    private fun setupLogsList() {
        adapter = AdminActivityLogAdapter()
        binding.rvActivityLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActivityLogs.adapter = adapter
    }

    private fun loadSummaryStats() {
        db.collection("users").get().addOnSuccessListener {
            if (isAdded) binding.tvStatUsers.text = it.size().toString()
        }

        val startOfDay = startOfTodayMillis()
        db.collection("activity_logs")
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val logs = snapshot.toObjects(ActivityLog::class.java)
                val alertsToday = logs.count { it.action.equals("ALERT_TRIGGERED", ignoreCase = true) }
                val manualToday = logs.count { it.action.equals("MANUAL_SUBMISSION", ignoreCase = true) }
                val loginsToday = logs.count { it.action.equals("USER_LOGIN", ignoreCase = true) }

                binding.tvStatAlertsToday.text = alertsToday.toString()
                binding.tvStatManualToday.text = manualToday.toString()
                binding.tvStatLoginsToday.text = loginsToday.toString()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                binding.tvMonitorStatus.text = "Monitoring: Data sync issue"
                binding.tvMonitorStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), com.air.quality.meter.R.color.color_error)
                )
            }
    }

    private fun startRealtimeLogsListener() {
        logsListener?.remove()
        logsListener = db.collection("activity_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener

                if (error != null) {
                    binding.tvMonitorStatus.text = "Monitoring: Data sync issue"
                    binding.tvMonitorStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), com.air.quality.meter.R.color.color_error)
                    )
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    val m = doc.toObject(ActivityLog::class.java)
                    if (m == null) null else if (m.id.isBlank()) m.copy(id = doc.id) else m
                }.orEmpty()

                adapter.submitList(items)
                val hasData = items.isNotEmpty()
                binding.rvActivityLogs.visibility = if (hasData) View.VISIBLE else View.GONE
                binding.layoutEmptyLogs.visibility = if (hasData) View.GONE else View.VISIBLE

                binding.tvMonitorStatus.text = "Monitoring: Live"
                binding.tvMonitorStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), com.air.quality.meter.R.color.aqi_good)
                )

                loadSummaryStats()
            }
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroyView() {
        logsListener?.remove()
        logsListener = null
        super.onDestroyView()
        _binding = null
    }
}
