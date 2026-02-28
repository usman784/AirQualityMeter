package com.air.quality.meter.ui.fragments.citizen

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.air.quality.meter.databinding.ItemCitizenBinding

class CitizenAdapter(private var citizens: List<CitizenModel>) :
    RecyclerView.Adapter<CitizenAdapter.CitizenViewHolder>() {

    inner class CitizenViewHolder(private val binding: ItemCitizenBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(citizen: CitizenModel) {
            binding.tvCitizenName.text = citizen.name.ifBlank { "No name" }
            binding.tvCitizenEmail.text = citizen.email
            binding.tvCitizenAge.text = if (citizen.age.isNotBlank()) "Age: ${citizen.age}" else ""
            binding.tvCitizenGender.text = if (citizen.gender.isNotBlank()) "· ${citizen.gender}" else ""
            binding.tvCitizenCell.text = if (citizen.cellNumber.isNotBlank()) "📞 ${citizen.cellNumber}" else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CitizenViewHolder {
        val binding = ItemCitizenBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CitizenViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CitizenViewHolder, position: Int) {
        holder.bind(citizens[position])
    }

    override fun getItemCount(): Int = citizens.size

    fun updateData(newCitizens: List<CitizenModel>) {
        citizens = newCitizens
        notifyDataSetChanged()
    }
}
