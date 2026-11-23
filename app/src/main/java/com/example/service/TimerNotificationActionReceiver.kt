package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.BrainBufferRepository
import com.example.domain.FocusTimerEngine
import com.example.domain.TemporaryAccessPolicy
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PurposeSessionRecord
import com.example.model.StudyActivityType
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DistractionAlertCoordinator
import com.example.system.DndFocusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        const val ACTION_RETURN_TO_FOCUS_TEMP = "com.example.brainbuffer.action.TEMP_RETURN_TO_FOCUS"
        const val ACTION_EXTEND_TEMP_5M = "com.example.brainbuffer.action.TEMP_EXTEND_5M"

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
            val repository = BrainBufferRepository.getInstance(appContext)
            val snapshot = repository.getTimerSnapshotOnce()
            val prefs = repository.getPreferencesOnce()
            val subjects = repository.getSubjectsOnce()

            fun syncActiveNotificationAndService(updated: com.example.model.PersistedTimerSnapshot) {
                val subjectName = subjects.firstOrNull { it.id == updated.selectedSubjectId }?.name
                    ?: "General Study"
                BrainBufferNotificationManager.postOrUpdateActiveTimerNotification(
                    context = appContext,
                    snapshot = updated,
                    subjectName = subjectName,
                    prefs = prefs.notificationPrefs,
                    masterNotificationsEnabled = prefs.notificationsEnabled,
                    nowEpochMs = nowEpochMs,
                    nowElapsedRealtimeMs = nowElapsedMs,
                    liveUpdatesEnabled = prefs.focusGuardSettings.liveUpdatesEnabled
                )
                FocusTimerService.syncWithSnapshot(appContext, updated)
            }

            when (action) {
                ACTION_PAUSE -> {
                    val paused = FocusTimerEngine.pause(snapshot, nowEpochMs, nowElapsedMs)
                    repository.saveTimerSnapshot(paused)
                    syncActiveNotificationAndService(paused)
                }

                ACTION_RESUME -> {
                    val resumed = FocusTimerEngine.startOrResume(snapshot, nowEpochMs, nowElapsedMs)
                    repository.saveTimerSnapshot(resumed)
                    syncActiveNotificationAndService(resumed)
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
                    syncActiveNotificationAndService(extended)
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
                    syncActiveNotificationAndService(onBreak)
                }

                ACTION_ALLOW_5_MIN -> {
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    val label = distractionAppLabel.ifBlank {
                        distractionPackage.substringAfterLast('.').ifBlank { "App" }
                    }
                    val existingTemp = repository.getActiveTempAccessOnce()
                    val clampedMinutes = extendMinutes.coerceIn(1, TemporaryAccessPolicy.MAX_EXTENSION_MINUTES)
                    val startRes = TemporaryAccessPolicy.startTemporaryAccess(
                        parentSessionId = snapshot.sessionId,
                        packageName = distractionPackage,
                        appLabel = label,
                        purposeLabel = "Temporary allowance",
                        durationMinutes = clampedMinutes,
                        nowEpochMs = nowEpochMs,
                        existingActive = existingTemp,
                        ownPackageName = appContext.packageName
                    )
                    if (startRes is TemporaryAccessPolicy.StartResult.Started) {
                        val pausedSnapshot = FocusTimerEngine.pauseForTemporaryAccess(snapshot, nowEpochMs, nowElapsedMs)
                        repository.saveTimerSnapshot(pausedSnapshot)
                        repository.saveActiveTempAccess(startRes.session)
                        repository.logInterventionEvent(
                            InterventionEventRecord(
                                sessionKey = snapshot.sessionId,
                                eventType = InterventionEventType.APP_SWITCH,
                                packageName = startRes.session.packageName,
                                appLabel = startRes.session.appLabel,
                                declaredPurpose = snapshot.purposeCategory.label,
                                actionTaken = "Temporarily allowed for ${clampedMinutes}m from notification",
                                plannedSeconds = clampedMinutes * 60,
                                timestampMs = nowEpochMs,
                                dateEpochDay = LocalDate.now().toEpochDay()
                            )
                        )
                        syncActiveNotificationAndService(pausedSnapshot)
                    }
                }

                ACTION_RETURN_TO_FOCUS_TEMP -> {
                    BrainBufferNotificationManager.cancelTemporaryAccessExpiredNotification(appContext)
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    val currentTemp = repository.getActiveTempAccessOnce()
                    if (currentTemp != null) {
                        val closed = TemporaryAccessPolicy.closeTemporaryAccess(currentTemp, nowEpochMs)
                        val elapsedTempSec = closed.actualDurationSeconds(nowEpochMs)
                        repository.saveActiveTempAccess(null)
                        repository.logInterventionEvent(
                            InterventionEventRecord(
                                sessionKey = snapshot.sessionId,
                                eventType = InterventionEventType.TEMP_ACCESS_EXPIRED,
                                packageName = closed.packageName,
                                appLabel = closed.appLabel,
                                declaredPurpose = snapshot.purposeCategory.label,
                                actionTaken = "Returned to focus",
                                plannedSeconds = closed.originalAllowanceMinutes * 60,
                                extensionSeconds = closed.extensionMinutesUsed * 60,
                                actualSeconds = elapsedTempSec,
                                timestampMs = nowEpochMs,
                                dateEpochDay = LocalDate.now().toEpochDay()
                            )
                        )
                        val resumed = FocusTimerEngine.resumeFromTemporaryAccess(
                            current = snapshot,
                            tempAccessActualSeconds = elapsedTempSec,
                            nowEpochMs = nowEpochMs,
                            nowElapsedRealtimeMs = nowElapsedMs
                        )
                        repository.saveTimerSnapshot(resumed)
                        syncActiveNotificationAndService(resumed)
                    }
                }

                ACTION_EXTEND_TEMP_5M -> {
                    BrainBufferNotificationManager.cancelTemporaryAccessExpiredNotification(appContext)
                    val currentTemp = repository.getActiveTempAccessOnce()
                    if (currentTemp != null) {
                        val res = TemporaryAccessPolicy.requestExtension(
                            current = currentTemp,
                            requestedMinutes = extendMinutes.coerceAtMost(TemporaryAccessPolicy.MAX_EXTENSION_MINUTES),
                            nowEpochMs = nowEpochMs
                        )
                        if (res is TemporaryAccessPolicy.ExtensionResult.Granted) {
                            repository.saveActiveTempAccess(res.updated)
                            repository.logInterventionEvent(
                                InterventionEventRecord(
                                    sessionKey = snapshot.sessionId,
                                    eventType = InterventionEventType.TEMP_ACCESS_EXTENDED,
                                    packageName = res.updated.packageName,
                                    appLabel = res.updated.appLabel,
                                    declaredPurpose = snapshot.purposeCategory.label,
                                    actionTaken = "Extended +${res.grantedMinutes}m (one-time max 5m)",
                                    plannedSeconds = res.updated.originalAllowanceMinutes * 60,
                                    extensionSeconds = res.grantedMinutes * 60,
                                    timestampMs = nowEpochMs,
                                    dateEpochDay = LocalDate.now().toEpochDay()
                                )
                            )
                        }
                    }
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
                        syncActiveNotificationAndService(restored)
                    } else if (snapshot.hasStarted && snapshot.runningSinceEpochMs == null) {
                        val resumed = FocusTimerEngine.startOrResume(snapshot, nowEpochMs, nowElapsedMs)
                        repository.saveTimerSnapshot(resumed)
                        syncActiveNotificationAndService(resumed)
                    }
                }

                ACTION_END -> {
                    BrainBufferNotificationManager.cancelCompletionNotification(appContext)
                    BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                    BrainBufferNotificationManager.cancelTemporaryAccessExpiredNotification(appContext)

                    val activeTemp = repository.getActiveTempAccessOnce()
                    val effectiveSnap = FocusTimerEngine.resolveParentForEarlyExit(
                        snapshot = snapshot,
                        activeTempAccess = activeTemp,
                        nowEpochMs = nowEpochMs,
                        nowElapsedRealtimeMs = nowElapsedMs
                    )
                    val decision = FocusTimerEngine.evaluateEarlyTermination(
                        snapshot = effectiveSnap,
                        nowEpochMs = nowEpochMs,
                        nowElapsedRealtimeMs = nowElapsedMs
                    )
                    val alreadySaved = decision.shouldPersist && repository.hasPurposeSession(decision.sessionId)
                    if (decision.shouldPersist && !alreadySaved) {
                        val subject = subjects.firstOrNull { it.id == effectiveSnap.selectedSubjectId }
                        val subjectName = subject?.name ?: "General Focus"
                        val todayEpoch = LocalDate.now().toEpochDay()

                        repository.upsertStudySession(
                            sessionKey = decision.sessionId,
                            subjectId = subject?.id,
                            subjectName = subjectName,
                            startTimeMs = effectiveSnap.activityStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
                            endTimeMs = nowEpochMs,
                            totalDurationSeconds = decision.actualSeconds,
                            plannedDurationSeconds = decision.plannedSeconds,
                            sessionStatus = decision.status, // "ENDED_EARLY"
                            focusGuardLevel = effectiveSnap.focusGuardLevel.name,
                            activityType = effectiveSnap.activeActivityType.name
                        )
                        repository.recordActivityLog(
                            ActivityLogRecord(
                                sessionKey = decision.sessionId,
                                activityType = effectiveSnap.activeActivityType,
                                activityLabel = "${effectiveSnap.activeActivityLabel} (Ended early)",
                                category = effectiveSnap.activeActivityCategory,
                                subjectId = subject?.id,
                                subjectName = subjectName,
                                subjectColorHex = subject?.colorHex ?: effectiveSnap.activeActivityType.accentHex,
                                plannedSeconds = decision.plannedSeconds,
                                actualSeconds = decision.actualSeconds,
                                startedAtMs = effectiveSnap.activityStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
                                endedAtMs = nowEpochMs,
                                dateEpochDay = todayEpoch,
                                wasPlanned = false,
                                notes = effectiveSnap.sessionGoal.trim()
                            )
                        )
                        if (effectiveSnap.mode == FocusMode.FOCUS && !effectiveSnap.isOnBreak) {
                            repository.savePurposeSession(
                                PurposeSessionRecord(
                                    sessionKey = decision.sessionId,
                                    purposeCategory = effectiveSnap.purposeCategory,
                                    sessionName = effectiveSnap.sessionGoal.ifBlank { subjectName },
                                    allowedPackages = effectiveSnap.allowedPackages.toList(),
                                    allowedAppLabels = effectiveSnap.allowedAppLabels,
                                    plannedSeconds = decision.plannedSeconds,
                                    actualFocusedSeconds = decision.actualSeconds,
                                    breakSeconds = effectiveSnap.accumulatedBreakSeconds,
                                    temporaryAccessSeconds = effectiveSnap.accumulatedTempAccessSeconds,
                                    distractionSeconds = effectiveSnap.accumulatedDistractionSeconds,
                                    interventionCount = effectiveSnap.interventionCount,
                                    status = PurposeSessionRecord.STATUS_ENDED_EARLY,
                                    breakPlanMode = effectiveSnap.breakPlanMode,
                                    linkedTaskId = effectiveSnap.linkedTaskId,
                                    startedAtMs = effectiveSnap.sessionStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
                                    endedAtMs = nowEpochMs,
                                    dateEpochDay = todayEpoch
                                )
                            )
                        }
                        if (effectiveSnap.mode == FocusMode.FOCUS && !effectiveSnap.isOnBreak && decision.actualSeconds >= 60) {
                            val existingSessions = repository.focusSessions.first()
                            repository.recordFocusSessionAndGrowGarden(
                                session = FocusSessionRecord(
                                    subjectId = subject?.id,
                                    subjectName = effectiveSnap.sessionGoal.ifBlank { subjectName },
                                    subjectColorHex = subject?.colorHex ?: effectiveSnap.purposeCategory.accentHex,
                                    durationSeconds = decision.actualSeconds,
                                    plannedSeconds = decision.plannedSeconds,
                                    sessionGoal = effectiveSnap.sessionGoal.trim(),
                                    mode = FocusMode.FOCUS,
                                    completedAtMs = nowEpochMs,
                                    dateEpochDay = todayEpoch
                                ),
                                dailyTargetMinutes = prefs.dailyTargetMinutes,
                                allExistingSessions = existingSessions,
                                existingDecorations = prefs.unlockedDecorations,
                                sessionKey = decision.sessionId
                            )
                        }
                    }

                    DistractionAlertCoordinator.clearSession(snapshot.sessionId)
                    DistractionAlertCoordinator.clearSession(effectiveSnap.sessionId)
                    repository.saveActiveTempAccess(null)
                    val reset = FocusTimerEngine.markCompletedAndReset(effectiveSnap, decision.sessionId)
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
