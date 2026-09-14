package com.thefactor1.taskskiller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.Rule
import com.thefactor1.taskskiller.databinding.ItemRuleBinding
import com.thefactor1.taskskiller.schedule.RestartScheduler
import com.thefactor1.taskskiller.util.Format
import com.thefactor1.taskskiller.util.PackageUtil

class RuleAdapter(
    private val onClick: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

    private var rules: List<Rule> = emptyList()
    private var masterEnabled: Boolean = true

    fun submit(rules: List<Rule>, masterEnabled: Boolean) {
        this.rules = rules
        this.masterEnabled = masterEnabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = rules.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]
        val context = holder.itemView.context

        holder.binding.ruleLabel.text = rule.label
        holder.binding.ruleIcon.setImageDrawable(PackageUtil.icon(context, rule.packageName))

        val schedule = context.getString(
            R.string.every_interval, Format.interval(rule.intervalMinutes)
        )
        val next = if (masterEnabled && rule.enabled) {
            context.getString(R.string.next_run, Format.relative(RestartScheduler.nextRunAt(rule)))
        } else {
            context.getString(R.string.next_run_paused)
        }
        holder.binding.ruleSchedule.text = "$schedule  ·  $next"

        holder.binding.ruleStatus.text = if (rule.lastRunAt > 0) {
            context.getString(
                R.string.last_run,
                "${Format.timestamp(context, rule.lastRunAt)} — ${rule.lastResult}"
            )
        } else {
            context.getString(R.string.never_run)
        }

        holder.itemView.setOnClickListener { onClick(rule) }
    }

    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)
}
