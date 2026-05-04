package com.air.quality.meter.ui.fragments.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.databinding.ItemAqiRecordAdminBinding
import com.air.quality.meter.util.AQIClassifier
import java.text.SimpleDateFormat
import java.util.*

class DatasetAdapter(
    private val onEdit: (AQIRecord) -> Unit,
    private val onDelete: (AQIRecord) -> Unit
) : ListAdapter<AQIRecord, DatasetAdapter.VH>(DIFF) {

    inner class VH(val b: ItemAqiRecordAdminBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(record: AQIRecord) {
            val cat = AQIClassifier.classify(record.aqi)
            b.tvAqiValue.text = record.aqi.toInt().toString()
            b.tvCategory.text = cat.name
            b.tvAqiValue.setTextColor(android.graphics.Color.parseColor(cat.colorHex))
            b.tvLocation.text = "UID: ${record.uid.take(8)}... | ${record.location}"
            b.tvSource.text = "Source: ${record.source.ifBlank { "admin" }}"

            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            b.tvTimestamp.text = sdf.format(Date(record.timestamp))
            b.tvParams.text = "T: %.1f°C | H: %.0f%% | PM2.5: %.1f".format(
                record.temperature, record.humidity, record.pm25
            )
            b.btnEdit.setOnClickListener { onEdit(record) }
            b.btnDelete.setOnClickListener { onDelete(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAqiRecordAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AQIRecord>() {
            override fun areItemsTheSame(a: AQIRecord, b: AQIRecord) = a.id == b.id
            override fun areContentsTheSame(a: AQIRecord, b: AQIRecord) = a == b
        }
    }
}
