package com.thefactor1.taskskiller.ui

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
import com.thefactor1.taskskiller.databinding.ActivitySetupBinding
import com.thefactor1.taskskiller.kill.KillBackends
import com.thefactor1.taskskiller.kill.ShizukuBackend
import com.thefactor1.taskskiller.schedule.RestartScheduler

/**
 * Status panel for everything that has to be granted outside the app, plus the
 * ADB commands for the settings Android TV has no screen for.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.shizukuButton.setOnClickListener {
            ShizukuBackend.requestPermission()
            // The grant arrives asynchronously; refreshing on resume picks it up.
            Toast.makeText(this, R.string.grant_shizuku, Toast.LENGTH_SHORT).show()
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val active = KillBackends.resolve(this)
        binding.activeBackendText.text = getString(R.string.active_backend, active.displayName)

        binding.backendListText.text = KillBackends.statuses(this).joinToString("\n") { status ->
            val strength = getString(
                if (status.backend.isForceStop) R.string.backend_force_stop else R.string.backend_partial
            )
            val state = if (status.available) getString(R.string.backend_available) else status.reason
            "${if (status.available) "✓" else "✗"}  ${status.backend.displayName} ($strength)\n     $state"
        }

        binding.shizukuButton.visibility =
            if (ShizukuBackend.isBinderAlive() && !ShizukuBackend.hasPermission()) View.VISIBLE else View.GONE

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
