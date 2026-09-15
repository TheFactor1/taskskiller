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
import kotlin.math.roundToInt

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

    /** True if [submit] with these would change nothing, so a periodic refresh can just [tick]. */
    fun isShowing(rules: List<Rule>, masterEnabled: Boolean) =
        rules == this.rules && masterEnabled == this.masterEnabled

    /** Moves each countdown and progress bar on, without replaying the bars' sweep. */
    fun tick() = notifyItemRangeChanged(0, rules.size, PAYLOAD_TICK)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = rules.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_TICK in payloads) bindSchedule(holder, rules[position], sweep = false)
        else onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]
        val context = holder.itemView.context

        holder.binding.ruleLabel.text = rule.label
        holder.binding.ruleIcon.setImageDrawable(PackageUtil.icon(context, rule.packageName))
        bindSchedule(holder, rule, sweep = true)

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

    /** The "Every … · Next run …" line and the progress bar under it. */
    private fun bindSchedule(holder: ViewHolder, rule: Rule, sweep: Boolean) {
        val context = holder.itemView.context
        val now = System.currentTimeMillis()
        val live = masterEnabled && rule.enabled

        val schedule = context.getString(
            R.string.every_interval, Format.interval(rule.intervalMinutes)
        )
        val next = when {
            !live -> context.getString(R.string.next_run_paused)
            // Due, but a scheduled run waits while the screen is on, and the
            // screen is on for anyone reading this. "In 30 seconds" over and
            // over would be untrue.
            rule.skipWhileScreenOn && RestartScheduler.isDue(rule, now) ->
                context.getString(R.string.next_run_waiting)
            else -> context.getString(R.string.next_run, Format.relative(RestartScheduler.nextRunAt(rule)))
        }
        holder.binding.ruleSchedule.text = "$schedule  ·  $next"

        val progress = if (live) RestartScheduler.progress(rule, now) else 0f
        holder.binding.nextRunBar.setProgress(progress, live, fromZero = sweep)
        holder.binding.nextRunBar.contentDescription = if (live) {
            context.getString(R.string.next_run_progress, (progress * 100).roundToInt())
        } else {
            null
        }
    }

    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val PAYLOAD_TICK = "tick"
    }
}
