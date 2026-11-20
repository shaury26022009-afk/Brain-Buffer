package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.StudyActivityType
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DistractionAlertCoordinator
import com.example.system.DndFocusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles live timer, completion, and Focus Guard 3.0 distraction warning notification actions
 * (Pause, Resume, Return to Study, Extend, Take Break, Allow for 5 min, Continue Anyway, End)
 * directly against Room + DataStore so controls work reliably even when the Activity is closed.
 */
class TimerNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val extendMinutes = intent.getIntExtra(EXTRA_EXTEND_MINUTES, 5)
        val distractionPkg = intent.getStringExtra(EXTRA_DISTRACTION_PACKAGE).orEmpty()
        val distractionLabel = intent.getStringExtra(EXTRA_DISTRACTION_APP_LABEL).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleAction(
                    appContext = appContext,
                    action = action,
                    extendMinutes = extendMinutes,
                    distractionPackage = distractionPkg,
                    distractionAppLabel = distractionLabel,
                    sessionId = sessionId
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.brainbuffer.action.TIMER_PAUSE"
        const val ACTION_RESUME = "com.example.brainbuffer.action.TIMER_RESUME"
        const val ACTION_END = "com.example.brainbuffer.action.TIMER_END"
        const val ACTION_EXTEND = "com.example.brainbuffer.action.TIMER_EXTEND"
        const val ACTION_RETURN_TO_STUDY = "com.example.brainbuffer.action.TIMER_RETURN_TO_STUDY"
        const val ACTION_TAKE_BREAK = "com.example.brainbuffer.action.TIMER_TAKE_BREAK"
        const val ACTION_ALLOW_5_MIN = "com.example.brainbuffer.action.DISTRACTION_ALLOW_5_MIN"
        const val ACTION_CONTINUE_ANYWAY = "com.example.brainbuffer.action.DISTRACTION_CONTINUE_ANYWAY"

        const val EXTRA_EXTEND_MINUTES = "extra_extend_minutes"
        const val EXTRA_DISTRACTION_PACKAGE = "extra_distraction_package"
        const val EXTRA_DISTRACTION_APP_LABEL = "extra_distraction_app_label"
        const val EXTRA_SESSION_ID = "extra_session_id"

        suspend fun handleAction(
            appContext: Context,
            action: String,
            extendMinutes: Int = 5,
            nowEpochMs: Long = System.currentTimeMillis(),
            nowElapsedMs: Long = SystemClock.elapsedRealtime(),
            distractionPackage: String = "",
            distractionAppLabel: String = "",
            sessionId: String = ""
        ) {
            val db = BrainBufferDatabase.getInstance(appContext)
            val prefsStore = UserPreferencesStore(appContext)
            val repository = BrainBufferRepository(db, prefsStore)
            val snapshot = repository.getTimerSnapshotOnce()
            val prefs = repository.getPreferencesOnce()
            val subjects = repository.getSubjectsOnce()

            when (action) {
                ACTION_PAUSE -> {
                    val paused = FocusTimerEngine.pause(snapshot, nowEpochMs, nowElapsedMs)
                    repository.saveTimerSnapshot(paused)
                    FocusTimerService.syncWithSnapshot(appContext, paused)
                }

                ACTION_RESUME -> {
                    val resumed = FocusTimerEngine.startOrResume(snapshot, nowEpochMs, nowElapsedMs)
                    repository.saveTimerSnapshot(resumed)
                    FocusTimerService.syncWithSnapshot(appContext, resumed)
                }

                ACTION_EXTEND -> {
                    BrainBufferNotificationManager.cancelCompletionNotification(appContext)
                    val extended = FocusTimerEngine.extendActiveTimer(
                        snapshot = snapshot,
                        extraMinutes = extendMinutes,
                        nowEpochMs = nowEpochMs,
                        nowElapsedRealtimeMs = nowElapsedMs
                    )
                    repository.saveTimerSnapshot(extended)
                    FocusTimerService.syncWithSnapshot(appContext, extended)
                }

                ACTION_TAKE_BREAK -> {
                    BrainBufferNotificationManager.cancelCompletionNotification(appContext)
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    val breakMins = prefs.focusGuardSettings.defaultBreakMinutes.coerceIn(3, 30)
                    val onBreak = FocusTimerEngine.startBreakPreservingParent(
                        current = snapshot,
                        breakMinutes = breakMins,
                        nowEpochMs = nowEpochMs,
                        nowElapsedRealtimeMs = nowElapsedMs
                    )
                    repository.saveTimerSnapshot(onBreak)
                    FocusTimerService.syncWithSnapshot(appContext, onBreak)
                }

                ACTION_ALLOW_5_MIN -> {
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    val mappings = repository.getAppCategoryMappingsOnce()
                    val suggestion = if (distractionPackage.isNotBlank()) {
                        AppUsageAwarenessHelper.matchPackageToSuggestion(distractionPackage, mappings)
                    } else null
                    val activityType = suggestion?.suggestedActivity ?: StudyActivityType.ENTERTAINMENT
                    val category = activityType.category
                    val label = distractionAppLabel.ifBlank {
                        suggestion?.appDisplayName ?: activityType.label
                    }
                    val allowanceMinutes = extendMinutes.coerceIn(1, 30)
                    val switched = FocusTimerEngine.switchActivity(
                        current = snapshot,
                        activityType = activityType,
                        activityLabel = "${activityType.shortLabel} · $label",
                        category = category,
                        durationMinutes = allowanceMinutes,
                        subjectId = snapshot.selectedSubjectId,
                        wasPlanned = false,
                        nowEpochMs = nowEpochMs,
                        nowElapsedRealtimeMs = nowElapsedMs,
                        approvedPackageName = distractionPackage,
                        approvedAppLabel = label
                    )
                    repository.saveTimerSnapshot(switched)
                    FocusTimerService.syncWithSnapshot(appContext, switched)
                }

                ACTION_CONTINUE_ANYWAY -> {
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    val activeSessionId = sessionId.ifBlank { snapshot.sessionId }
                    if (distractionPackage.isNotBlank()) {
                        DistractionAlertCoordinator.suppressForSession(activeSessionId, distractionPackage)
                    }
                }

                ACTION_RETURN_TO_STUDY -> {
                    BrainBufferNotificationManager.cancelCompletionNotification(appContext)
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    if (snapshot.isSwitchedActivity) {
                        val elapsedSec = FocusTimerEngine.actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedMs)
                        if (snapshot.hasStarted && elapsedSec >= 30) {
                            val subject = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }
                            repository.recordActivityLog(
                                ActivityLogRecord(
                                    sessionKey = snapshot.sessionId.ifBlank { FocusTimerEngine.generateSessionId() },
                                    activityType = snapshot.activeActivityType,
                                    activityLabel = snapshot.activeActivityLabel,
                                    category = snapshot.activeActivityCategory,
                                    subjectId = subject?.id,
                                    subjectName = subject?.name ?: "General Study",
                                    subjectColorHex = subject?.colorHex ?: snapshot.activeActivityType.accentHex,
                                    plannedSeconds = snapshot.totalSeconds,
                                    actualSeconds = elapsedSec,
                                    startedAtMs = snapshot.activityStartedAtEpochMs ?: (nowEpochMs - elapsedSec * 1000L),
                                    endedAtMs = nowEpochMs,
                                    dateEpochDay = LocalDate.now().toEpochDay(),
                                    wasPlanned = snapshot.activityWasPlanned
                                )
                            )
                        }
                        val restored = FocusTimerEngine.returnToPausedStudySession(
                            current = snapshot,
                            nowEpochMs = nowEpochMs,
                            nowElapsedRealtimeMs = nowElapsedMs
                        ) ?: FocusTimerEngine.reset(snapshot)
                        repository.saveTimerSnapshot(restored)
                        FocusTimerService.syncWithSnapshot(appContext, restored)
                    } else if (snapshot.hasStarted && snapshot.runningSinceEpochMs == null) {
                        val resumed = FocusTimerEngine.startOrResume(snapshot, nowEpochMs, nowElapsedMs)
                        repository.saveTimerSnapshot(resumed)
                        FocusTimerService.syncWithSnapshot(appContext, resumed)
                    }
                }

                ACTION_END -> {
                    BrainBufferNotificationManager.cancelCompletionNotification(appContext)
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    DistractionAlertCoordinator.clearSession(snapshot.sessionId)
                    val reset = FocusTimerEngine.reset(snapshot)
                    repository.saveTimerSnapshot(reset)
                    if (prefs.savedDndInterruptionFilter != DndFocusController.NO_SAVED_FILTER) {
                        DndFocusController.restorePreviousDnd(
                            backend = AndroidDndBackend(appContext),
                            savedFilter = prefs.savedDndInterruptionFilter,
                            clearSavedFilter = {
                                repository.updateSavedDndInterruptionFilter(DndFocusController.NO_SAVED_FILTER)
                            }
                        )
                    }
                    FocusTimerService.stopService(appContext)
                }
            }
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }
}
