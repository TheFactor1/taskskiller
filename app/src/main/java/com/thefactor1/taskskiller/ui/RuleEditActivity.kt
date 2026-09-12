package com.thefactor1.taskskiller.ui

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.Rule
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.databinding.ActivityRuleEditBinding
import com.thefactor1.taskskiller.schedule.RestartScheduler
import com.thefactor1.taskskiller.schedule.RestartService
import com.thefactor1.taskskiller.util.Format
import com.thefactor1.taskskiller.util.PackageUtil
import java.util.Locale

class RuleEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuleEditBinding
    private lateinit var store: RuleStore

    private var existing: Rule? = null
    private lateinit var targetPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = RuleStore.get(this)

        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        existing = if (ruleId >= 0) store.find(ruleId) else null
        targetPackage = existing?.packageName
            ?: intent.getStringExtra(EXTRA_PACKAGE)
            ?: run { finish(); return }

        binding.appLabelText.text = existing?.label ?: PackageUtil.label(this, targetPackage)
        binding.appPackageText.text = targetPackage

        setUpIntervalSeek()
        setUpDelaySeek()

        binding.enabledSwitch.isChecked = existing?.enabled ?: true
        binding.relaunchSwitch.isChecked = existing?.relaunch ?: true
        binding.wakeScreenSwitch.isChecked = existing?.wakeScreen ?: false
        binding.skipScreenOnSwitch.isChecked = existing?.skipWhileScreenOn ?: false

        binding.deleteButton.visibility = if (existing == null) View.GONE else View.VISIBLE
        binding.runNowButton.visibility = if (existing == null) View.GONE else View.VISIBLE

        binding.saveButton.setOnClickListener {
            save()
            finish()
        }
        binding.runNowButton.setOnClickListener {
            val saved = save()
            RestartService.runRule(this, saved.id, manual = true)
            Toast.makeText(this, R.string.run_now_started, Toast.LENGTH_LONG).show()
        }
        binding.deleteButton.setOnClickListener {
            existing?.let {
                RestartScheduler.cancel(this, it.id)
                store.delete(it.id)
            }
            finish()
        }

        binding.saveButton.requestFocus()
    }

    private fun save(): Rule {
        val current = existing
        val rule = Rule(
            id = current?.id ?: store.nextId(),
            packageName = targetPackage,
            label = PackageUtil.label(this, targetPackage),
            intervalMinutes = INTERVAL_CHOICES[binding.intervalSeek.progress],
            enabled = binding.enabledSwitch.isChecked,
            relaunch = binding.relaunchSwitch.isChecked,
            relaunchDelayMs = binding.delaySeek.progress * DELAY_STEP_MS,
            wakeScreen = binding.wakeScreenSwitch.isChecked,
            skipWhileScreenOn = binding.skipScreenOnSwitch.isChecked,
            // Keep the original anchor across edits so changing an option does
            // not restart the countdown of a rule that has not fired yet.
            anchorAt = current?.anchorAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
            lastRunAt = current?.lastRunAt ?: 0L,
            lastResult = current?.lastResult ?: ""
        )
        store.upsert(rule)
        existing = rule

        if (rule.enabled && store.masterEnabled) RestartScheduler.schedule(this, rule)
        else RestartScheduler.cancel(this, rule.id)

        return rule
    }

    private fun setUpIntervalSeek() {
        binding.intervalSeek.max = INTERVAL_CHOICES.lastIndex
        val minutes = existing?.intervalMinutes ?: DEFAULT_INTERVAL_MINUTES
        binding.intervalSeek.progress = closestIntervalIndex(minutes)
        renderInterval()
        binding.intervalSeek.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                renderInterval()
        })
    }

    private fun setUpDelaySeek() {
        binding.delaySeek.max = DELAY_MAX_STEPS
        val delay = existing?.relaunchDelayMs ?: DEFAULT_DELAY_MS
        binding.delaySeek.progress = (delay / DELAY_STEP_MS).toInt().coerceIn(0, DELAY_MAX_STEPS)
        renderDelay()
        binding.delaySeek.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                renderDelay()
        })
    }

    private fun renderInterval() {
        binding.intervalValueText.text = Format.interval(INTERVAL_CHOICES[binding.intervalSeek.progress])
    }

    private fun renderDelay() {
        val seconds = binding.delaySeek.progress * DELAY_STEP_MS / 1000.0
        binding.delayValueText.text = String.format(Locale.getDefault(), "%.1f s", seconds)
    }

    private fun closestIntervalIndex(minutes: Int): Int =
        INTERVAL_CHOICES.indices.minByOrNull { kotlin.math.abs(INTERVAL_CHOICES[it] - minutes) } ?: 0

    private open class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_PACKAGE = "package"

        private const val DEFAULT_INTERVAL_MINUTES = 60
        private const val DEFAULT_DELAY_MS = 3_000L
        private const val DELAY_STEP_MS = 500L

        /** 0 to 15 s of settling time between the kill and the relaunch. */
        private const val DELAY_MAX_STEPS = 30

        /**
         * Discrete choices rather than a free slider: a D-pad can only nudge a
         * SeekBar one step at a time, so a 1..1440 range would be unusable.
         */
        private val INTERVAL_CHOICES = intArrayOf(
            5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440
        )
    }
}
