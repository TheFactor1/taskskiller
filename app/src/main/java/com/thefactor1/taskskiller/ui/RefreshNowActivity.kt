package com.thefactor1.taskskiller.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.schedule.RestartService

/**
 * The "Refresh now" tile: one press refreshes every enabled rule. It has no UI
 * of its own; it starts the runs and finishes before drawing anything, so the
 * viewer stays on whatever they were watching. Being a launcher entry, it can
 * also be bound to a remote button or a launcher's quick action.
 */
class RefreshNowActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rules = RuleStore.get(this).enabled()
        if (rules.isEmpty()) {
            Toast.makeText(applicationContext, R.string.refresh_now_none, Toast.LENGTH_LONG).show()
        } else {
            // Nothing of ours is on screen to come back to, so the run returns
            // to whatever was in front before, not to Refresher.
            rules.forEach { RestartService.runRule(this, it.id, manual = true, returnToApp = false) }
            Toast.makeText(
                applicationContext,
                getString(R.string.refresh_now_started, rules.joinToString(", ") { it.label }),
                Toast.LENGTH_SHORT
            ).show()
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
