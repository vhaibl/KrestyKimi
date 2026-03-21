package com.kresty.isolation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.kresty.isolation.R
import com.kresty.isolation.model.AppInfo

class AppSelectionAdapter(
    private val onAddClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppSelectionAdapter.AppViewHolder>(AppDiffCallback()) {

    private val addedPackages = mutableSetOf<String>()

    fun setAddedPackages(packages: Set<String>) {
        addedPackages.clear()
        addedPackages.addAll(packages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selectable, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val appPackage: TextView = itemView.findViewById(R.id.appPackage)
        private val typeChip: Chip = itemView.findViewById(R.id.typeChip)
        private val addButton: MaterialButton = itemView.findViewById(R.id.addButton)
        private val alreadyAddedText: TextView = itemView.findViewById(R.id.alreadyAddedText)

        fun bind(app: AppInfo) {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            appPackage.text = app.packageName

            // Set type chip
            if (app.isSystemApp) {
                typeChip.text = itemView.context.getString(R.string.system_app)
                typeChip.setChipBackgroundColorResource(R.color.primary_light)
            } else {
                typeChip.text = itemView.context.getString(R.string.user_app)
                typeChip.setChipBackgroundColorResource(R.color.primary_light)
            }

            // Check if already added
            val isAdded = addedPackages.contains(app.packageName)
            if (isAdded) {
                addButton.visibility = View.GONE
                alreadyAddedText.visibility = View.VISIBLE
            } else {
                addButton.visibility = View.VISIBLE
                alreadyAddedText.visibility = View.GONE
                addButton.setOnClickListener {
                    onAddClick(app)
                }
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
