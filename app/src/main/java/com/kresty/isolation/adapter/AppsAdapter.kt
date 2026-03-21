package com.kresty.isolation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.kresty.isolation.R
import com.kresty.isolation.model.AppInfo

class AppsAdapter(
    private val onFreezeClick: (AppInfo) -> Unit,
    private val onDeleteClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val appPackage: TextView = itemView.findViewById(R.id.appPackage)
        private val statusChip: Chip = itemView.findViewById(R.id.statusChip)
        private val freezeButton: ImageButton = itemView.findViewById(R.id.freezeButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(app: AppInfo) {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            appPackage.text = app.packageName

            // Update status chip
            if (app.isFrozen) {
                statusChip.text = itemView.context.getString(R.string.status_frozen)
                statusChip.setChipBackgroundColorResource(R.color.frozen)
                freezeButton.setImageResource(R.drawable.ic_unfreeze)
                freezeButton.contentDescription = itemView.context.getString(R.string.unfreeze_app)
            } else {
                statusChip.text = itemView.context.getString(R.string.status_active)
                statusChip.setChipBackgroundColorResource(R.color.active)
                freezeButton.setImageResource(R.drawable.ic_freeze)
                freezeButton.contentDescription = itemView.context.getString(R.string.freeze_app)
            }

            freezeButton.setOnClickListener {
                onFreezeClick(app)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(app)
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
