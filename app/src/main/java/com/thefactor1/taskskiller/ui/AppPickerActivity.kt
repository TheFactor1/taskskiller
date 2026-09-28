package com.thefactor1.taskskiller.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewOutlineProvider
import androidx.appcompat.app.AppCompatActivity
import com.thefactor1.taskskiller.databinding.ActivityAppPickerBinding
import com.thefactor1.taskskiller.util.PackageUtil

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppAdapter
    private var allApps: List<PackageUtil.InstalledApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppAdapter { app ->
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(RESULT_PACKAGE, app.packageName)
                    .putExtra(RESULT_LABEL, app.label)
            )
            finish()
        }
        binding.appRecycler.layoutManager = CenteringLayoutManager(this)
        // Rows are clipped to the list so they cannot scroll over the switch above.
        binding.appRecycler.outlineProvider = ViewOutlineProvider.BOUNDS
        binding.appRecycler.clipToOutline = true
        binding.appRecycler.adapter = adapter
        // Only the rows take focus, as in MainActivity. RecyclerView makes
        // itself focusable in its constructor, and this screen shows an empty
        // list while the packages are still being enumerated — so DOWN landed
        // on the list itself, with nothing highlighted and OK doing nothing.
        binding.appRecycler.isFocusable = false
        binding.appRecycler.isFocusableInTouchMode = false

        binding.systemAppsSwitch.setOnCheckedChangeListener { _, _ -> applyFilter() }
        binding.systemAppsSwitch.requestFocus()

        // Enumerating every installed package takes long enough on a TV box to
        // be worth keeping off the main thread.
        Thread {
            val apps = PackageUtil.installedApps(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps = apps
                applyFilter()
            }
        }.start()
    }

    /** System packages are hidden by default; most of them are not worth restarting. */
    private fun applyFilter() {
        val showSystem = binding.systemAppsSwitch.isChecked
        adapter.submit(allApps.filter { showSystem || !it.isSystem || it.hasLauncherEntry })
    }

    companion object {
        const val RESULT_PACKAGE = "package"
        const val RESULT_LABEL = "label"
    }
}
