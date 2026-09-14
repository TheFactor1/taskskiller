package com.thefactor1.taskskiller.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.RunLog
import com.thefactor1.taskskiller.databinding.ItemLogBinding
import com.thefactor1.taskskiller.util.Format

class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private var entries: List<RunLog.Entry> = emptyList()

    fun submit(entries: List<RunLog.Entry>) {
        this.entries = entries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context
        holder.binding.logHeadline.text =
            "${Format.timestamp(context, entry.timestamp)}  ${entry.label}"
        holder.binding.logDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (entry.success) R.color.ok else R.color.bad)
        )
        holder.binding.logDetail.text = entry.detail
    }

    class ViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)
}
