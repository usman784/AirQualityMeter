package com.air.quality.meter.ui.fragments.citizen

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.air.quality.meter.data.model.HealthRecommendation
import com.air.quality.meter.databinding.ItemRecommendationBinding

class RecommendationAdapter :
    ListAdapter<HealthRecommendation, RecommendationAdapter.VH>(DIFF) {

    inner class VH(val b: ItemRecommendationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: HealthRecommendation) {
            b.tvIcon.text        = item.iconEmoji
            b.tvTitle.text       = item.title
            b.tvCategoryBadge.text = item.aqiCategory
            b.tvDescription.text = item.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HealthRecommendation>() {
            override fun areItemsTheSame(a: HealthRecommendation, b: HealthRecommendation) = a.id == b.id
            override fun areContentsTheSame(a: HealthRecommendation, b: HealthRecommendation) = a == b
        }
    }
}
