package com.thefactor1.taskskiller.kill

import android.content.Context
import com.thefactor1.taskskiller.data.RuleStore

object KillBackends {

    /** Strongest first. */
    val all: List<KillBackend> = listOf(
        ShizukuBackend,
        DeviceOwnerBackend,
        RootBackend,
        BackgroundProcessBackend
    ).sortedByDescending { it.rank }

    fun byId(id: String): KillBackend? = all.firstOrNull { it.id == id }

    /**
     * The backend a run will actually use: the user's pick when it is usable,
     * otherwise the strongest one that is. [BackgroundProcessBackend] is always
     * available, so this never returns null.
     */
    fun resolve(context: Context): KillBackend {
        val preferred = RuleStore.get(context).preferredBackend
        if (preferred != RuleStore.BACKEND_AUTO) {
            byId(preferred)?.takeIf { it.isAvailable(context) }?.let { return it }
        }
        return all.firstOrNull { it.isAvailable(context) } ?: BackgroundProcessBackend
    }

    data class Status(val backend: KillBackend, val available: Boolean, val reason: String)

    fun statuses(context: Context): List<Status> = all.map { backend ->
        val available = backend.isAvailable(context)
        Status(backend, available, if (available) "" else backend.unavailableReason(context))
    }
}
