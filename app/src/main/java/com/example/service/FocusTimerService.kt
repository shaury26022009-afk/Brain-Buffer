package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.BrainBufferRepository
import com.example.domain.FocusTimerEngine
import com.example.domain.PurposeMatchingEngine
import com.example.model.ActivityLogRecord
import com.example.model.FocusGuardLevel
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PersistedTimerSnapshot
import com.example.model.PurposeSessionRecord
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.BrainBufferHaptics
import com.example.system.DistractionAlertCoordinator
import com.example.system.DndFocusController
import com.example.system.HapticEvent
import com.example.ui.FocusBlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lightweight, session-scoped foreground service for Focus Guard 3.0.
 *
 * Lifecycle & Battery guarantees:
 * - Starts ONLY when a focus or activity session is actively in progress (`hasStarted == true`).
 * - Stops itself immediately (`stopForeground` + `stopSelf`) when the session completes or resets.
 * - Uses native notification chronometer countdown (`setUsesChronometer`) so progress updates are
 *   low-frequency (~30s).
 * - When optional Usage Access is granted AND distraction warnings are enabled during an active
 *   High/Custom Focus session, performs an efficient low-frequency check (~12s) to send a real
 *   Android Distraction Warning notification with [Return to Study], [Allow for 5 min], and
 *   [Continue Anyway], strictly respecting per-package cooldown & dismissal memory.
 */
class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        // Guarantee startForeground() is called synchronously before any async IO
        // (eliminates ForegroundServiceDidNotStartInTimeException on Android 12–16).
        val fallbackTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "BRAIN BUFFER LIVE"
        val fallbackText = intent?.getStringExtra(EXTRA_TEXT) ?: "Focus session active"
        val fallbackRemaining = intent?.getIntExtra(EXTRA_REMAINING_SEC, 0) ?: 0
        val fallbackRunning = intent?.getBooleanExtra(EXTRA_IS_RUNNING, true) ?: true
        val immediateNotification = BrainBufferNotificationManager.buildImmediateFallbackNotification(
            context = applicationContext,
            title = fallbackTitle,
            contentText = fallbackText,
            remainingSec = fallbackRemaining,
            isRunning = fallbackRunning
        )
        startForegroundSafely(immediateNotification)

        serviceScope.launch {
            refreshAndMonitor()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep active running session alive; refresh notification state safely if still started
        serviceScope.launch {
            val repository = BrainBufferRepository.getInstance(applicationContext)
            val snapshot = repository.getTimerSnapshotOnce()
            if (!snapshot.hasStarted) {
                stopForegroundAndSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun refreshAndMonitor() {
        val appContext = applicationContext
        val repository = BrainBufferRepository.getInstance(appContext)
        val snapshot = repository.getTimerSnapshotOnce()
        val prefs = repository.getPreferencesOnce()
        val manualBlocking = repository.getManualBlockingSessionOnce()
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val manualEnforcing = manualBlocking.isEnforcing(nowEpoch) &&
            AppUsageAwarenessHelper.hasUsageStatsPermission(appContext)

        if (!snapshot.hasStarted && !manualEnforcing) {
            stopForegroundAndSelf()
            return
        }

        if (snapshot.hasStarted && (!prefs.notificationsEnabled || !prefs.notificationPrefs.activeFocusEnabled) && !manualEnforcing) {
            stopForegroundAndSelf()
            return
        }

        val subjects = repository.getSubjectsOnce()
        val subjectName = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }?.name ?: "General Study"

        if (snapshot.hasStarted && FocusTimerEngine.isCompleted(snapshot, nowEpoch, nowElapsed)) {
            completeFromService(appContext, repository, snapshot, subjectName, nowEpoch, nowElapsed)
            if (!manualEnforcing) {
                stopForegroundAndSelf()
                return
            }
        }

        val notification = if (snapshot.hasStarted) {
            BrainBufferNotificationManager.buildActiveTimerNotification(
                context = appContext,
                snapshot = snapshot,
                subjectName = subjectName,
                prefs = prefs.notificationPrefs,
                nowEpochMs = nowEpoch,
                nowElapsedRealtimeMs = nowElapsed,
                liveUpdatesEnabled = prefs.focusGuardSettings.liveUpdatesEnabled
            )
        } else {
            val remainingMin = ((manualBlocking.remainingSeconds(nowEpoch) + 59) / 60).coerceAtLeast(1)
            val label = if (manualBlocking.endsAtEpochMs > 0L) {
                "Focus Guard active · ${remainingMin}m left"
            } else {
                "Focus Guard active · Monitoring blocked apps"
            }
            BrainBufferNotificationManager.buildImmediateFallbackNotification(
                context = appContext,
                title = "BRAIN BUFFER · Focus Guard",
                contentText = label,
                remainingSec = manualBlocking.remainingSeconds(nowEpoch),
                isRunning = true
            )
        }

        startForegroundSafely(notification)

        monitorJob?.cancel()
        val initialTemp = repository.getActiveTempAccessOnce()
        val hasOpenTemp = initialTemp != null && initialTemp.endedAtEpochMs == null
        if (snapshot.hasStarted && snapshot.runningSinceEpochMs == null && !manualEnforcing && !hasOpenTemp) {
            return
        }

        monitorJob = serviceScope.launch {
            var lastNotificationRefreshMs = nowEpoch
            while (isActive) {
                val latest = repository.getTimerSnapshotOnce()
                val latestPrefs = repository.getPreferencesOnce()
                val latestManual = repository.getManualBlockingSessionOnce()
                val latestTemp = repository.getActiveTempAccessOnce()
                val epochNow = System.currentTimeMillis()
                val elapsedNow = SystemClock.elapsedRealtime()
                val usageGranted = AppUsageAwarenessHelper.hasUsageStatsPermission(appContext)
                val manualActiveNow = latestManual.isEnforcing(epochNow) && usageGranted
                val openTempNow = latestTemp != null && latestTemp.endedAtEpochMs == null

                if (latestManual.isActive && !latestManual.isPaused &&
                    latestManual.endsAtEpochMs > 0L && epochNow >= latestManual.endsAtEpochMs
                ) {
                    repository.saveManualBlockingSession(com.example.model.ManualBlockingSession())
                }

                // Check temporary access expiry promptly without silent renewal
                if (openTempNow && latestTemp != null && latestTemp.isExpired(epochNow)) {
                    val vibrationAllowed = latestPrefs.hapticsEnabled && latestPrefs.focusGuardSettings.vibrationEnabled
                    val posted = BrainBufferNotificationManager.postTemporaryAccessExpiredNotification(
                        context = appContext,
                        tempAccessId = latestTemp.id,
                        appLabel = latestTemp.appLabel,
                        canExtend = latestTemp.canRequestExtension,
                        masterNotificationsEnabled = latestPrefs.notificationsEnabled,
                        soundEnabled = latestPrefs.soundEnabled && latestPrefs.focusGuardSettings.notificationSoundEnabled,
                        vibrationEnabled = vibrationAllowed
                    )
                    if (posted) {
                        BrainBufferHaptics.perform(
                            context = appContext,
                            event = HapticEvent.DISTRACTION_ALERT,
                            enabled = vibrationAllowed
                        )
                    }
                }

                val timerRunningNow = latest.hasStarted && latest.runningSinceEpochMs != null
                if (!timerRunningNow && !manualActiveNow && !openTempNow) {
                    if (!latest.hasStarted) {
                        stopForegroundAndSelf()
                    }
                    break
                }

                val remainingSec = if (timerRunningNow) {
                    FocusTimerEngine.remainingSeconds(latest, epochNow, elapsedNow)
                } else {
                    Int.MAX_VALUE
                }

                if (timerRunningNow && remainingSec <= 0) {
                    val latestSubjects = repository.getSubjectsOnce()
                    val latestSubject = latestSubjects.firstOrNull { it.id == latest.selectedSubjectId }?.name
                        ?: "General Study"
                    completeFromService(appContext, repository, latest, latestSubject, epochNow, elapsedNow)
                    if (!manualActiveNow) {
                        stopForegroundAndSelf()
                        break
                    }
                }

                val currentBlockedApps = if (usageGranted) repository.getBlockedAppsOnce() else emptyList()
                val hasActiveBlocklistRules = (timerRunningNow &&
                    latestPrefs.focusGuardSettings.blockDuringActiveFocus &&
                    currentBlockedApps.any { it.isBlockedFor(latest.focusGuardLevel) }) ||
                    (manualActiveNow && currentBlockedApps.any { it.isBlocked })

                val purposeWatchActive = usageGranted && timerRunningNow && !latest.isOnBreak &&
                    latestPrefs.focusGuardSettings.distractionWarningsEnabled

                val distractionWatchActive = usageGranted &&
                    (hasActiveBlocklistRules || purposeWatchActive || (
                        timerRunningNow &&
                            (latest.isStrictFocus || latest.focusGuardLevel != FocusGuardLevel.NORMAL) &&
                            latestPrefs.appUsageAwarenessEnabled &&
                            latestPrefs.focusGuardSettings.distractionWarningsEnabled &&
                            latestPrefs.notificationPrefs.distractionWarningsEnabled
                        ))

                val baseIntervalMs = when {
                    openTempNow -> 3_000L
                    hasActiveBlocklistRules || purposeWatchActive -> 4_000L
                    distractionWatchActive -> 10_000L
                    else -> 30_000L
                }
                val sleepMs = if (timerRunningNow) {
                    minOf(baseIntervalMs, (remainingSec * 1000L).coerceAtLeast(1_000L))
                } else {
                    baseIntervalMs
                }
                delay(sleepMs)

                val refreshed = repository.getTimerSnapshotOnce()
                val refreshedManual = repository.getManualBlockingSessionOnce()
                val refreshedTemp = repository.getActiveTempAccessOnce()
                val refreshEpoch = System.currentTimeMillis()
                val refreshElapsed = SystemClock.elapsedRealtime()
                val refreshedTimerRunning = refreshed.hasStarted && refreshed.runningSinceEpochMs != null
                val refreshedManualEnforcing = refreshedManual.isEnforcing(refreshEpoch) &&
                    AppUsageAwarenessHelper.hasUsageStatsPermission(appContext)
                val refreshedOpenTemp = refreshedTemp != null && refreshedTemp.endedAtEpochMs == null

                if (!refreshedTimerRunning && !refreshedManualEnforcing && !refreshedOpenTemp) continue
                if (refreshedTimerRunning && FocusTimerEngine.isCompleted(refreshed, refreshEpoch, refreshElapsed)) continue

                val refreshedPrefs = repository.getPreferencesOnce()
                val activeSubject = repository.getSubjectsOnce()
                    .firstOrNull { it.id == refreshed.selectedSubjectId }?.name
                    ?: subjectName

                // Purpose -> Activity foreground matching & temporary access expiry monitoring
                if (distractionWatchActive || refreshedOpenTemp) {
                    val fgPkg = AppUsageAwarenessHelper.queryCurrentForegroundPackage(
                        context = appContext,
                        enabledByUser = true,
                        nowEpochMs = refreshEpoch
                    )
                    if (fgPkg != null && refreshed.hasStarted) {
                        val fgLabel = runCatching {
                            val pm = appContext.packageManager
                            pm.getApplicationLabel(pm.getApplicationInfo(fgPkg, 0)).toString()
                        }.getOrDefault(fgPkg.substringAfterLast('.'))

                        when (
                            val decision = PurposeMatchingEngine.evaluateForegroundApp(
                                observedPackage = fgPkg,
                                observedAppLabel = fgLabel,
                                snapshot = refreshed,
                                activeTempAccess = refreshedTemp,
                                nowEpochMs = refreshEpoch,
                                ownPackageName = appContext.packageName,
                                gracePeriodSeconds = refreshedPrefs.focusGuardSettings.gracePeriodSeconds,
                                cooldownSeconds = refreshedPrefs.focusGuardSettings.distractionCooldownSeconds,
                                enforceGracePeriod = true
                            )
                        ) {
                            is PurposeMatchingEngine.MatchDecision.TemporaryAccessExpired -> {
                                val vibrationAllowed = refreshedPrefs.hapticsEnabled &&
                                    refreshedPrefs.focusGuardSettings.vibrationEnabled
                                val posted = BrainBufferNotificationManager.postTemporaryAccessExpiredNotification(
                                    context = appContext,
                                    tempAccessId = decision.session.id,
                                    appLabel = decision.session.appLabel,
                                    canExtend = decision.canExtend,
                                    masterNotificationsEnabled = refreshedPrefs.notificationsEnabled,
                                    soundEnabled = refreshedPrefs.soundEnabled &&
                                        refreshedPrefs.focusGuardSettings.notificationSoundEnabled,
                                    vibrationEnabled = vibrationAllowed
                                )
                                if (posted) {
                                    BrainBufferHaptics.perform(
                                        context = appContext,
                                        event = HapticEvent.DISTRACTION_ALERT,
                                        enabled = vibrationAllowed
                                    )
                                }
                            }

                            is PurposeMatchingEngine.MatchDecision.PotentialDistraction -> {
                                if (decision.shouldEmitAlert) {
                                    val vibrationAllowed = refreshedPrefs.hapticsEnabled &&
                                        refreshedPrefs.focusGuardSettings.vibrationEnabled
                                    val blockedApps = repository.getBlockedAppsOnce()
                                    val isExplicitlyBlocked = blockedApps.any {
                                        it.packageName.equals(decision.packageName, ignoreCase = true) &&
                                            it.isBlockedFor(refreshed.focusGuardLevel)
                                    }
                                    if (isExplicitlyBlocked || refreshed.allowedPackages.isNotEmpty()) {
                                        runCatching {
                                            val blockIntent = FocusBlockedActivity.createIntent(
                                                context = appContext,
                                                appName = decision.appLabel,
                                                packageName = decision.packageName,
                                                categoryLabel = decision.declaredPurpose.label,
                                                reason = decision.message
                                            )
                                            appContext.startActivity(blockIntent)
                                        }
                                    }
                                    val posted = BrainBufferNotificationManager.postDistractionWarningNotification(
                                        context = appContext,
                                        snapshot = refreshed,
                                        subjectName = decision.sessionName,
                                        detectedPackageName = decision.packageName,
                                        detectedAppLabel = decision.appLabel,
                                        prefs = refreshedPrefs.notificationPrefs,
                                        masterNotificationsEnabled = refreshedPrefs.notificationsEnabled,
                                        soundEnabled = refreshedPrefs.soundEnabled &&
                                            refreshedPrefs.focusGuardSettings.notificationSoundEnabled,
                                        vibrationEnabled = vibrationAllowed,
                                        isExplicitlyBlocked = isExplicitlyBlocked || refreshed.allowedPackages.isNotEmpty(),
                                        categoryLabel = decision.declaredPurpose.label,
                                        blockReason = decision.message
                                    )
                                    if (posted || isExplicitlyBlocked || refreshed.allowedPackages.isNotEmpty()) {
                                        BrainBufferHaptics.perform(
                                            context = appContext,
                                            event = HapticEvent.DISTRACTION_ALERT,
                                            enabled = vibrationAllowed
                                        )
                                        DistractionAlertCoordinator.markNotified(
                                            sessionId = refreshed.sessionId,
                                            packageName = decision.packageName,
                                            nowEpochMs = refreshEpoch
                                        )
                                        repository.recordDistractionWarning()
                                        repository.logInterventionEvent(
                                            InterventionEventRecord(
                                                sessionKey = refreshed.sessionId,
                                                eventType = InterventionEventType.POTENTIAL_DISTRACTION,
                                                packageName = decision.packageName,
                                                appLabel = decision.appLabel,
                                                declaredPurpose = decision.declaredPurpose.label,
                                                actionTaken = decision.message,
                                                timestampMs = refreshEpoch,
                                                dateEpochDay = LocalDate.now().toEpochDay()
                                            )
                                        )
                                        val updatedSnap = FocusTimerEngine.recordDistractionIntervention(
                                            current = refreshed,
                                            distractionSeconds = refreshedPrefs.focusGuardSettings.gracePeriodSeconds.coerceAtLeast(15)
                                        )
                                        repository.saveTimerSnapshot(updatedSnap)
                                    }
                                }
                            }

                            else -> Unit
                        }
                    } else if (refreshedManualEnforcing && fgPkg != null) {
                        val blockedApps = repository.getBlockedAppsOnce()
                        val tempAllowances = repository.getTemporaryAllowancesOnce().filter { it.isActive(refreshEpoch) }
                        val warning = AppUsageAwarenessHelper.evaluateStandaloneBlock(
                            observedPackage = fgPkg,
                            blockedApps = blockedApps,
                            temporaryAllowances = tempAllowances,
                            nowEpochMs = refreshEpoch,
                            ownPackageName = appContext.packageName
                        )
                        if (warning != null) {
                            val sessionKeyForAlert = "manual_${refreshedManual.startedAtEpochMs}"
                            if (DistractionAlertCoordinator.shouldEmitAlert(
                                    sessionId = sessionKeyForAlert,
                                    packageName = warning.packageName,
                                    nowEpochMs = refreshEpoch,
                                    cooldownSeconds = minOf(refreshedPrefs.focusGuardSettings.distractionCooldownSeconds, 20)
                                )
                            ) {
                                BrainBufferHaptics.perform(
                                    context = appContext,
                                    event = HapticEvent.DISTRACTION_ALERT,
                                    enabled = refreshedPrefs.hapticsEnabled &&
                                        refreshedPrefs.focusGuardSettings.vibrationEnabled
                                )
                                runCatching {
                                    appContext.startActivity(
                                        FocusBlockedActivity.createIntent(
                                            context = appContext,
                                            appName = warning.appDisplayName,
                                            packageName = warning.packageName,
                                            categoryLabel = warning.observedCategory.label,
                                            reason = warning.message
                                        )
                                    )
                                }
                                DistractionAlertCoordinator.markNotified(
                                    sessionId = sessionKeyForAlert,
                                    packageName = warning.packageName,
                                    nowEpochMs = refreshEpoch
                                )
                                repository.recordDistractionWarning()
                            }
                        }
                    }
                }

                // Refresh live progress notification every ~30s to keep battery impact negligible
                if (refreshedTimerRunning && refreshEpoch - lastNotificationRefreshMs >= 28_000L) {
                    lastNotificationRefreshMs = refreshEpoch
                    val updatedNotif = BrainBufferNotificationManager.buildActiveTimerNotification(
                        context = appContext,
                        snapshot = refreshed,
                        subjectName = activeSubject,
                        prefs = refreshedPrefs.notificationPrefs,
                        nowEpochMs = refreshEpoch,
                        nowElapsedRealtimeMs = refreshElapsed,
                        liveUpdatesEnabled = refreshedPrefs.focusGuardSettings.liveUpdatesEnabled
                    )
                    startForegroundSafely(updatedNotif)
                }
            }
        }
    }

    private suspend fun completeFromService(
        appContext: Context,
        repository: BrainBufferRepository,
        snapshot: PersistedTimerSnapshot,
        subjectName: String,
        nowEpochMs: Long,
        nowElapsedMs: Long
    ) {
        val decision = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = snapshot,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = nowElapsedMs
        )
        if (decision !is FocusTimerEngine.SessionCompletionDecision.NaturalCompletion) {
            return
        }
        val prefs = repository.getPreferencesOnce()
        val subjects = repository.getSubjectsOnce()
        val subject = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }
        val sessionKey = decision.sessionId
        val todayEpoch = LocalDate.now().toEpochDay()

        // Race-proof deduplication: skip if ViewModel already completed & recorded this sessionKey
        val latestPersistedSnap = repository.getTimerSnapshotOnce()
        val isPurposeFocusSession = snapshot.mode == FocusMode.FOCUS && !snapshot.isOnBreak
        if (latestPersistedSnap.lastCompletedSessionId == sessionKey ||
            (isPurposeFocusSession && repository.hasPurposeSession(sessionKey))
        ) {
            return
        }

        val openTempSec = repository.getActiveTempAccessOnce()
            ?.takeIf { it.endedAtEpochMs == null }
            ?.actualDurationSeconds(nowEpochMs)
            ?.coerceAtLeast(0)
            ?: 0

        repository.upsertStudySession(
            sessionKey = sessionKey,
            subjectId = subject?.id,
            subjectName = subject?.name ?: subjectName,
            startTimeMs = snapshot.activityStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
            endTimeMs = nowEpochMs,
            totalDurationSeconds = decision.actualSeconds,
            plannedDurationSeconds = decision.plannedSeconds,
            sessionStatus = "COMPLETED",
            focusGuardLevel = snapshot.focusGuardLevel.name,
            activityType = snapshot.activeActivityType.name
        )

        // Always record into activity_logs (deduplicated by sessionKey)
        repository.recordActivityLog(
            ActivityLogRecord(
                sessionKey = sessionKey,
                activityType = snapshot.activeActivityType,
                activityLabel = snapshot.activeActivityLabel,
                category = snapshot.activeActivityCategory,
                subjectId = subject?.id,
                subjectName = subject?.name ?: subjectName,
                subjectColorHex = subject?.colorHex ?: snapshot.activeActivityType.accentHex,
                plannedSeconds = decision.plannedSeconds,
                actualSeconds = decision.actualSeconds,
                startedAtMs = snapshot.activityStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
                endedAtMs = nowEpochMs,
                dateEpochDay = todayEpoch,
                wasPlanned = snapshot.activityWasPlanned,
                notes = snapshot.sessionGoal
            )
        )

        // Persist PurposeSessionRecord for Intent vs. Reality analytics (never mixes breaks/temp access)
        if (isPurposeFocusSession) {
            repository.savePurposeSession(
                PurposeSessionRecord(
                    sessionKey = sessionKey,
                    purposeCategory = snapshot.purposeCategory,
                    sessionName = snapshot.sessionGoal.ifBlank { snapshot.purposeCategory.label },
                    allowedPackages = snapshot.allowedPackages.toList(),
                    allowedAppLabels = snapshot.allowedAppLabels,
                    plannedSeconds = decision.plannedSeconds,
                    actualFocusedSeconds = decision.actualSeconds,
                    breakSeconds = snapshot.accumulatedBreakSeconds,
                    temporaryAccessSeconds = snapshot.accumulatedTempAccessSeconds + openTempSec,
                    distractionSeconds = snapshot.accumulatedDistractionSeconds,
                    interventionCount = snapshot.interventionCount,
                    status = PurposeSessionRecord.STATUS_COMPLETED,
                    breakPlanMode = snapshot.breakPlanMode,
                    linkedTaskId = snapshot.linkedTaskId,
                    startedAtMs = snapshot.sessionStartedAtEpochMs ?: snapshot.activityStartedAtEpochMs ?: (nowEpochMs - decision.actualSeconds * 1000L),
                    endedAtMs = nowEpochMs,
                    dateEpochDay = todayEpoch
                )
            )
            repository.saveActiveTempAccess(null)
        }

        // ONLY focused study activities count toward study stats & garden growth (never breaks/gaming/entertainment)
        if (snapshot.activeActivityCategory.countsAsFocusedStudy && snapshot.mode == FocusMode.FOCUS) {
            val existingSessions = repository.focusSessions.first()
            repository.recordFocusSessionAndGrowGarden(
                session = FocusSessionRecord(
                    subjectId = subject?.id,
                    subjectName = snapshot.sessionGoal.ifBlank { subject?.name ?: subjectName },
                    subjectColorHex = subject?.colorHex ?: snapshot.purposeCategory.accentHex,
                    durationSeconds = decision.actualSeconds,
                    plannedSeconds = decision.plannedSeconds,
                    sessionGoal = snapshot.sessionGoal,
                    mode = FocusMode.FOCUS,
                    completedAtMs = nowEpochMs,
                    dateEpochDay = todayEpoch
                ),
                dailyTargetMinutes = prefs.dailyTargetMinutes,
                allExistingSessions = existingSessions,
                existingDecorations = prefs.unlockedDecorations,
                sessionKey = sessionKey
            )
        }

        // Restore DND cleanly if this session enabled it and has no paused parent returning immediately
        if (snapshot.pausedParentSession == null &&
            prefs.savedDndInterruptionFilter != DndFocusController.NO_SAVED_FILTER
        ) {
            DndFocusController.restorePreviousDnd(
                backend = AndroidDndBackend(appContext),
                savedFilter = prefs.savedDndInterruptionFilter,
                clearSavedFilter = {
                    repository.updateSavedDndInterruptionFilter(DndFocusController.NO_SAVED_FILTER)
                }
            )
        }

        BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
        DistractionAlertCoordinator.clearSession(snapshot.sessionId)

        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.SESSION_COMPLETE,
            enabled = prefs.hapticsEnabled
        )

        // Fire deduplicated completion / break / activity reminder notification
        BrainBufferNotificationManager.postCompletionNotification(
            context = appContext,
            snapshot = snapshot,
            subjectName = subject?.name ?: subjectName,
            prefs = prefs.notificationPrefs,
            masterNotificationsEnabled = prefs.notificationsEnabled,
            studentName = prefs.studentName,
            soundEnabled = prefs.soundEnabled && prefs.focusGuardSettings.notificationSoundEnabled,
            vibrationEnabled = prefs.hapticsEnabled && prefs.focusGuardSettings.vibrationEnabled,
            extensionMinutes = prefs.focusGuardSettings.customExtensionMinutes
        )

        // Freeze snapshot with lastCompletedSessionId recorded so process recreation never re-completes or restarts it!
        val completedSnapshot = if (snapshot.pausedParentSession != null) {
            snapshot.copy(
                accumulatedFocusedMs = snapshot.totalSeconds * 1000L,
                runningSinceEpochMs = null,
                runningSinceElapsedRealtimeMs = null,
                hasStarted = false,
                strictFocusDndActive = false,
                lastCompletedSessionId = sessionKey
            )
        } else {
            FocusTimerEngine.markCompletedAndReset(snapshot, sessionKey)
        }
        repository.saveTimerSnapshot(completedSnapshot)
        com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    BrainBufferNotificationManager.ID_ACTIVE_TIMER,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(BrainBufferNotificationManager.ID_ACTIVE_TIMER, notification)
            }
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: IllegalStateException) {
            stopSelf()
        }
    }

    private fun stopForegroundAndSelf() {
        monitorJob?.cancel()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Safe fallback
        }
        BrainBufferNotificationManager.cancelActiveTimerNotification(applicationContext)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SYNC = "com.example.brainbuffer.service.SYNC"
        private const val ACTION_STOP = "com.example.brainbuffer.service.STOP"
        private const val EXTRA_TITLE = "bb_fgs_title"
        private const val EXTRA_TEXT = "bb_fgs_text"
        private const val EXTRA_REMAINING_SEC = "bb_fgs_remaining_sec"
        private const val EXTRA_IS_RUNNING = "bb_fgs_is_running"

        fun syncWithSnapshot(context: Context, snapshot: PersistedTimerSnapshot) {
            val appContext = context.applicationContext
            if (!snapshot.hasStarted) {
                stopService(appContext)
                return
            }
            if (!BrainBufferNotificationManager.hasNotificationPermission(appContext)) {
                return
            }
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val remainingSec = FocusTimerEngine.remainingSeconds(snapshot, nowEpoch, nowElapsed)
            val isRunning = snapshot.runningSinceEpochMs != null
            val formattedRemaining = BrainBufferNotificationManager.formatMinSec(remainingSec)
            val intent = Intent(appContext, FocusTimerService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_TITLE, if (isRunning) "BRAIN BUFFER LIVE" else "BRAIN BUFFER · Paused")
                putExtra(
                    EXTRA_TEXT,
                    "${snapshot.activeActivityLabel} · $formattedRemaining remaining"
                )
                putExtra(EXTRA_REMAINING_SEC, remainingSec)
                putExtra(EXTRA_IS_RUNNING, isRunning)
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                // Handled gracefully without crashing on Android 12+ background FGS restrictions
            }
        }

        fun syncForManualBlocking(context: Context, session: com.example.model.ManualBlockingSession) {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            if (!session.isEnforcing(now)) {
                return
            }
            if (!BrainBufferNotificationManager.hasNotificationPermission(appContext)) {
                return
            }
            val remainingSec = session.remainingSeconds(now)
            val intent = Intent(appContext, FocusTimerService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_TITLE, "BRAIN BUFFER · Focus Guard")
                putExtra(
                    EXTRA_TEXT,
                    if (remainingSec > 0) "Blocking distractions · ${BrainBufferNotificationManager.formatMinSec(remainingSec)} left"
                    else "Blocking distractions"
                )
                putExtra(EXTRA_REMAINING_SEC, remainingSec)
                putExtra(EXTRA_IS_RUNNING, true)
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                // Handled gracefully on Android 12+ background FGS restrictions
            }
        }

        fun stopService(context: Context) {
            val appContext = context.applicationContext
            BrainBufferNotificationManager.cancelActiveTimerNotification(appContext)
            try {
                appContext.stopService(Intent(appContext, FocusTimerService::class.java))
            } catch (_: Exception) {
                // Safe no-op
            }
        }
    }
}
