package com.example.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Abstraction over Android's NotificationManager DND controls so unit/Robolectric tests
 * can deterministically verify save, enable, and exact restoration of interruption filters.
 */
interface DndBackend {
    fun isPolicyAccessGranted(): Boolean
    fun getCurrentInterruptionFilter(): Int
    fun setInterruptionFilter(filter: Int)
}

class AndroidDndBackend(context: Context) : DndBackend {
    private val notificationManager =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun isPolicyAccessGranted(): Boolean {
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    override fun getCurrentInterruptionFilter(): Int {
        return notificationManager?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
    }

    override fun setInterruptionFilter(filter: Int) {
        if (!isPolicyAccessGranted()) return
        try {
            notificationManager?.setInterruptionFilter(filter)
        } catch (_: SecurityException) {
            // Permission may have been revoked between check and call; degrade safely.
        }
    }
}

/**
 * Voluntary Do Not Disturb helper for Strict Focus Mode.
 *
 * Guarantees:
 * - Never changes DND unless the user explicitly granted Notification Policy access AND opted in.
 * - Uses [NotificationManager.INTERRUPTION_FILTER_PRIORITY] so alarms, emergency alerts,
 *   and starred/priority contacts remain reachable according to Android system settings.
 * - Always captures the prior interruption filter before enabling, and restores it cleanly when
 *   the focus session ends or is cancelled.
 */
object DndFocusController {

    const val NO_SAVED_FILTER = -1

    fun isAccessGranted(context: Context): Boolean {
        return AndroidDndBackend(context).isPolicyAccessGranted()
    }

    fun openDndAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun enableFocusDndIfGranted(
        backend: DndBackend,
        alreadySavedFilter: Int = NO_SAVED_FILTER,
        savePreviousFilter: suspend (Int) -> Unit
    ): Boolean {
        if (!backend.isPolicyAccessGranted()) return false
        val previous = backend.getCurrentInterruptionFilter()
        if (alreadySavedFilter == NO_SAVED_FILTER) {
            savePreviousFilter(previous)
        }
        backend.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    suspend fun restorePreviousDnd(
        backend: DndBackend,
        savedFilter: Int,
        clearSavedFilter: suspend () -> Unit
    ): Boolean {
        if (savedFilter == NO_SAVED_FILTER) return false
        val restored = if (backend.isPolicyAccessGranted()) {
            backend.setInterruptionFilter(savedFilter)
            true
        } else {
            false
        }
        clearSavedFilter()
        return restored
    }
}
