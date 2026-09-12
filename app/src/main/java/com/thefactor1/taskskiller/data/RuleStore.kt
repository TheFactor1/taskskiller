package com.thefactor1.taskskiller.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

/**
 * Rules live in a JSON blob inside device-protected SharedPreferences, so the
 * boot receiver can read them before the user unlocks the device.
 */
class RuleStore private constructor(private val prefs: SharedPreferences) {

    fun all(): List<Rule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                try {
                    Rule.fromJson(array.getJSONObject(i))
                } catch (e: JSONException) {
                    null // Drop a single corrupt entry rather than losing every rule.
                }
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun enabled(): List<Rule> = all().filter { it.enabled }

    fun find(id: Long): Rule? = all().firstOrNull { it.id == id }

    fun upsert(rule: Rule) {
        val rules = all().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules[index] = rule else rules.add(rule)
        write(rules)
    }

    fun delete(id: Long) = write(all().filterNot { it.id == id })

    fun nextId(): Long = (all().maxOfOrNull { it.id } ?: 0L) + 1L

    /** Global kill switch, so the user can pause everything without editing rules. */
    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, true)
        set(value) = prefs.edit().putBoolean(KEY_MASTER, value).apply()

    var preferredBackend: String
        get() = prefs.getString(KEY_BACKEND, BACKEND_AUTO) ?: BACKEND_AUTO
        set(value) = prefs.edit().putString(KEY_BACKEND, value).apply()

    private fun write(rules: List<Rule>) {
        val array = JSONArray()
        rules.sortedBy { it.id }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taskskiller_rules"
        private const val KEY_RULES = "rules"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_BACKEND = "preferred_backend"

        const val BACKEND_AUTO = "auto"

        @Volatile
        private var instance: RuleStore? = null

        fun get(context: Context): RuleStore = instance ?: synchronized(this) {
            instance ?: RuleStore(
                context.applicationContext
                    .createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
