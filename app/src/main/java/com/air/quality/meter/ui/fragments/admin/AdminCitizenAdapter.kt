package com.air.quality.meter.ui.fragments.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.air.quality.meter.databinding.ItemCitizenAdminBinding
import com.air.quality.meter.ui.fragments.citizen.CitizenModel

/**
 * RecyclerView adapter for the admin user list (UC08).
 * Shows citizen name, email, and a delete action.
 */
class AdminCitizenAdapter(
    private val onDelete: (CitizenModel) -> Unit
) : ListAdapter<CitizenModel, AdminCitizenAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCitizenAdminBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(user: CitizenModel) {
            b.tvName.text  = user.name.ifBlank { "—" }
            b.tvEmail.text = user.email
            b.tvAge.text   = if (user.age.isNotBlank()) "Age: ${user.age}" else "Age: —"
            b.btnDelete.setOnClickListener { onDelete(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCitizenAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CitizenModel>() {
            override fun areItemsTheSame(a: CitizenModel, b: CitizenModel) = a.uid == b.uid
            override fun areContentsTheSame(a: CitizenModel, b: CitizenModel) = a == b
        }
    }
}
