package com.thefactor1.taskskiller.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.databinding.ActivitySetupBinding
import com.thefactor1.taskskiller.kill.KillBackends
import com.thefactor1.taskskiller.kill.ShizukuBackend
import com.thefactor1.taskskiller.schedule.RestartScheduler
import com.thefactor1.taskskiller.setup.ShizukuStarter
import java.util.concurrent.Executors

/**
 * Status panel for everything that has to be granted outside the app. Shizuku
 * can be installed, started and configured from here without a computer; the
 * ADB commands remain at the bottom for boxes where that is not possible.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val setupExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.autoSetupButton.setOnClickListener { runAutoSetup() }
        binding.getShizukuButton.setOnClickListener { openShizukuDownload() }

        binding.shizukuButton.setOnClickListener {
            ShizukuBackend.requestPermission()
            // The grant arrives asynchronously; refreshing on resume picks it up.
            Toast.makeText(this, R.string.grant_shizuku, Toast.LENGTH_SHORT).show()
        }

        binding.autoStartShizukuSwitch.setOnCheckedChangeListener { _, checked ->
            RuleStore.get(this).autoStartShizuku = checked
        }

        binding.exactAlarmButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri()))
            }
        }

        binding.batteryButton.setOnClickListener {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri()))
        }

        binding.overlayButton.setOnClickListener {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri()))
        }

        binding.autoSetupButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        setupExecutor.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val active = KillBackends.resolve(this)
        binding.activeBackendText.text = getString(R.string.active_backend, active.displayName)

        val installed = ShizukuStarter.isInstalled(this)
        val running = ShizukuBackend.isBinderAlive()
        val permitted = running && ShizukuBackend.hasPermission()
        binding.shizukuStatusText.text = getString(
            when {
                !installed -> R.string.shizuku_status_missing
                !running -> R.string.shizuku_status_stopped
                !permitted -> R.string.shizuku_status_no_permission
                else -> R.string.shizuku_status_ready
            }
        )
        binding.getShizukuButton.setText(if (installed) R.string.update_shizuku else R.string.get_shizuku)
        binding.shizukuButton.visibility = if (running && !permitted) View.VISIBLE else View.GONE
        binding.autoStartShizukuSwitch.isChecked = RuleStore.get(this).autoStartShizuku

        binding.backendListText.text = KillBackends.statuses(this).joinToString("\n") { status ->
            val strength = getString(
                if (status.backend.isForceStop) R.string.backend_force_stop else R.string.backend_partial
            )
            val state = if (status.available) getString(R.string.backend_available) else status.reason
            "${if (status.available) "✓" else "✗"}  ${status.backend.displayName} ($strength)\n     $state"
        }

        bindPermission(
            granted = RestartScheduler.canScheduleExact(this),
            title = R.string.exact_alarms,
            description = R.string.exact_alarms_desc,
            textView = binding.exactAlarmText,
            button = binding.exactAlarmButton,
            settingExists = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )

        bindPermission(
            granted = isIgnoringBatteryOptimizations(),
            title = R.string.battery_exemption,
            description = R.string.battery_exemption_desc,
            textView = binding.batteryText,
            button = binding.batteryButton,
            settingExists = true
        )

        bindPermission(
            granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this),
            title = R.string.overlay_permission,
            description = R.string.overlay_permission_desc,
            textView = binding.overlayText,
            button = binding.overlayButton,
            settingExists = true
        )
    }

    /**
     * Network I/O and an authorization prompt the user may take a while to
     * answer, so the work runs on a background thread and reports each step.
     */
    private fun runAutoSetup() {
        binding.autoSetupButton.isEnabled = false
        showSetupResult(getString(R.string.auto_setup_running))

        setupExecutor.execute {
            val steps = ShizukuStarter.runSetup(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.autoSetupButton.isEnabled = true

                val connected = steps.firstOrNull()?.ok == true
                val allOk = steps.isNotEmpty() && steps.all { it.ok }
                val lines = steps.joinToString("\n") { step ->
                    val detail = if (step.detail.isBlank()) "" else "\n     ${step.detail}"
                    "${if (step.ok) "✓" else "✗"}  ${step.label}$detail"
                }
                showSetupResult(
                    getString(if (allOk) R.string.auto_setup_done else R.string.auto_setup_failed) + "\n" + lines
                )

                // A connection that worked once will work after a reboot too, so
                // that is the point to opt in to starting Shizuku automatically.
                if (connected && ShizukuStarter.isInstalled(this)) {
                    RuleStore.get(this).autoStartShizuku = true
                }
                if (ShizukuBackend.isBinderAlive() && !ShizukuBackend.hasPermission()) {
                    ShizukuBackend.requestPermission()
                }
                refresh()
            }
        }
    }

    private fun showSetupResult(text: String) {
        binding.autoSetupResultText.text = text
        binding.autoSetupResultText.visibility = View.VISIBLE
    }

    /** Play Store first; boxes without one may still have a browser for the releases page. */
    private fun openShizukuDownload() {
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuStarter.PLAY_STORE_URI)),
            Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuStarter.DOWNLOAD_URL))
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next one.
            }
        }
        Toast.makeText(
            this,
            getString(R.string.shizuku_store_unavailable, ShizukuStarter.DOWNLOAD_URL),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun bindPermission(
        granted: Boolean,
        title: Int,
        description: Int,
        textView: android.widget.TextView,
        button: android.widget.Button,
        settingExists: Boolean
    ) {
        val state = getString(if (granted) R.string.granted else R.string.not_granted)
        textView.text = "${getString(title)} — $state\n${getString(description)}"
        button.visibility = if (granted || !settingExists) View.GONE else View.VISIBLE
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun appUri(): Uri = Uri.parse("package:$packageName")

    /** Many TV builds ship without these settings screens at all. */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.setting_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
