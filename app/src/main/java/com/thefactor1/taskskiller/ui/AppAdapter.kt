package com.thefactor1.taskskiller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.databinding.ItemAppBinding
import com.thefactor1.taskskiller.util.PackageUtil

class AppAdapter(
    private val onClick: (PackageUtil.InstalledApp) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var apps: List<PackageUtil.InstalledApp> = emptyList()

    fun submit(apps: List<PackageUtil.InstalledApp>) {
        this.apps = apps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context

        holder.binding.appLabel.text = app.label

        val tags = buildList {
            add(app.packageName)
            if (app.isSystem) add(context.getString(R.string.system_app))
            if (!app.hasLauncherEntry) add(context.getString(R.string.no_launcher_entry))
        }
        holder.binding.appPackage.text = tags.joinToString("  ·  ")

        holder.itemView.setOnClickListener { onClick(app) }
    }

    class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}
