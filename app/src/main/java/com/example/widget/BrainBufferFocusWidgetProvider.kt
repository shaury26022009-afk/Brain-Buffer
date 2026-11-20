package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.model.FocusMode
import com.example.notifications.BrainBufferNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lightweight native Android Home-Screen Focus Widget (Android 10–16+).
 * Shows real active Focus Guard countdown or today's real focused study minutes,
 * with a one-tap jump into High Focus or the active session.
 */
class BrainBufferFocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                renderWidgets(appContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, BrainBufferFocusWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    renderWidgets(appContext, manager, ids)
                }
            }
        }

        private suspend fun renderWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val repository = BrainBufferRepository(
                BrainBufferDatabase.getInstance(context),
                UserPreferencesStore(context)
            )
            val snapshot = repository.getTimerSnapshotOnce()
            val prefs = repository.getPreferencesOnce()
            val subjects = repository.getSubjectsOnce()
            val sessions = repository.focusSessions.first()

            val today = LocalDate.now().toEpochDay()
            val todayMinutes = sessions
                .filter { it.mode == FocusMode.FOCUS && it.dateEpochDay == today }
                .sumOf { it.durationMinutes }

            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val remainingSec = FocusTimerEngine.remainingSeconds(snapshot, nowEpoch, nowElapsed)
            val isActive = snapshot.hasStarted && remainingSec > 0
            val subjectName = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }?.name ?: "General Study"

            val statusBadge: String
            val primaryMetric: String
            val subtitle: String
            val buttonLabel: String
            val targetRoute: String

            if (isActive) {
                val running = snapshot.runningSinceEpochMs != null
                statusBadge = if (running) snapshot.focusGuardLevel.label else "Paused"
                primaryMetric = BrainBufferNotificationManager.formatMinSec(remainingSec)
                subtitle = "${snapshot.activeActivityLabel} · $subjectName"
                buttonLabel = if (snapshot.isStrictFocus) "Open High Focus" else "Open Timer"
                targetRoute = if (snapshot.isStrictFocus) "strict_focus" else "focus"
            } else {
                statusBadge = "${todayMinutes}/${prefs.dailyTargetMinutes}m today"
                primaryMetric = if (todayMinutes > 0) "${todayMinutes}m focused" else "Ready to focus"
                subtitle = "Focus Guard · ${prefs.focusGuardSettings.defaultFocusMinutes}m default"
                buttonLabel = "Start High Focus"
                targetRoute = "strict_focus"
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, targetRoute)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                4001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_brain_buffer_focus).apply {
                    setTextViewText(R.id.widget_status_badge, statusBadge)
                    setTextViewText(R.id.widget_primary_metric, primaryMetric)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                    setTextViewText(R.id.widget_action_button, buttonLabel)
                    setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                    setOnClickPendingIntent(R.id.widget_action_button, openPendingIntent)
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
