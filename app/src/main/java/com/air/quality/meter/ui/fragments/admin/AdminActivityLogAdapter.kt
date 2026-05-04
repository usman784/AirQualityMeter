package com.air.quality.meter.ui.fragments.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.air.quality.meter.data.model.ActivityLog
import com.air.quality.meter.databinding.ItemActivityLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminActivityLogAdapter :
    ListAdapter<ActivityLog, AdminActivityLogAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemActivityLogBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ActivityLog) {
            b.tvLogAction.text = actionLabel(item.action)
            b.tvLogDetails.text = item.details.ifBlank { "No details" }
            b.tvLogUid.text = if (item.uid.isBlank()) "UID: —" else "UID: ${item.uid.take(10)}"

            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            b.tvLogTime.text = sdf.format(Date(item.timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemActivityLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private fun actionLabel(action: String): String = when (action.trim().uppercase(Locale.US)) {
        "USER_LOGIN" -> "User Login"
        "MANUAL_SUBMISSION" -> "Manual Submission"
        "ALERT_TRIGGERED" -> "Alert Triggered"
        "THRESHOLD_UPDATED" -> "Threshold Updated"
        else -> action.ifBlank { "Activity" }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ActivityLog>() {
            override fun areItemsTheSame(oldItem: ActivityLog, newItem: ActivityLog): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ActivityLog, newItem: ActivityLog): Boolean {
                return oldItem == newItem
            }
        }
    }
}
