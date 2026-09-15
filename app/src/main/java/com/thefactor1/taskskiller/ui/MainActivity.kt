package com.thefactor1.taskskiller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.data.RunLog
import com.thefactor1.taskskiller.databinding.ActivityMainBinding
import com.thefactor1.taskskiller.kill.KillBackends
import com.thefactor1.taskskiller.launch.AppLauncher
import com.thefactor1.taskskiller.schedule.RestartScheduler

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var logAdapter: LogAdapter

    private val pickAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val packageName = result.data?.getStringExtra(AppPickerActivity.RESULT_PACKAGE) ?: return@registerForActivityResult
        startActivity(
            Intent(this, RuleEditActivity::class.java)
                .putExtra(RuleEditActivity.EXTRA_PACKAGE, packageName)
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The service still runs without it; the notification is just hidden. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ruleAdapter = RuleAdapter { rule ->
            startActivity(
                Intent(this, RuleEditActivity::class.java)
                    .putExtra(RuleEditActivity.EXTRA_RULE_ID, rule.id)
            )
        }
        logAdapter = LogAdapter()

        binding.rulesRecycler.layoutManager = CenteringLayoutManager(this)
        // Rows are clipped to the list itself so they cannot scroll over the
        // header; clipping the whole column instead cut off buttons' focus lift.
        binding.rulesRecycler.outlineProvider = ViewOutlineProvider.BOUNDS
        binding.rulesRecycler.clipToOutline = true
        binding.rulesRecycler.adapter = ruleAdapter
        binding.logRecycler.layoutManager = CenteringLayoutManager(this)
        binding.logRecycler.adapter = logAdapter

        // Only the rows take focus. RecyclerView makes itself focusable in its
        // constructor, and an empty list would otherwise swallow the D-pad
        // invisibly (right from a rule landing on nothing).
        listOf(binding.rulesRecycler, binding.logRecycler).forEach {
            it.isFocusable = false
            it.isFocusableInTouchMode = false
        }

        binding.addRuleButton.setOnClickListener {
            pickAppLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.clearLogButton.setOnClickListener {
            RunLog.get(this).clear()
            refresh()
        }
        binding.masterSwitch.setOnCheckedChangeListener { _, checked ->
            val store = RuleStore.get(this)
            if (store.masterEnabled == checked) return@setOnCheckedChangeListener
            store.masterEnabled = checked
            RestartScheduler.scheduleAll(this)
            refresh()
        }

        requestNotificationPermissionIfNeeded()
        binding.addRuleButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Alarms can be dropped by firmware task-killers, so re-arm on every visit.
        RestartScheduler.scheduleAll(this)
        refresh()
    }

    private fun refresh() {
        val store = RuleStore.get(this)
        val rules = store.all()

        binding.masterSwitch.isChecked = store.masterEnabled
        ruleAdapter.submit(rules, store.masterEnabled)
        binding.emptyRulesText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        val entries = RunLog.get(this).entries()
        logAdapter.submit(entries)
        binding.emptyLogText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val backend = KillBackends.resolve(this)
        // The card is already labelled "Kill method", so just name it.
        binding.backendText.text = backend.displayName
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (backend.isForceStop) R.color.ok else R.color.warn)
        )

        val warning = when {
            !backend.isAvailable(this) -> getString(R.string.warning_no_backend)
            !backend.isForceStop -> getString(R.string.warning_weak_backend)
            !backend.hasPrivilegedLaunch && AppLauncher.backgroundStartsLikelyBlocked(this) ->
                getString(R.string.warning_background_launch)
            else -> null
        }
        binding.warningText.text = warning ?: ""
        binding.warningText.visibility = if (warning == null) View.GONE else View.VISIBLE
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
