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
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityLogRecord
import com.example.model.FocusGuardLevel
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.PersistedTimerSnapshot
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DistractionAlertCoordinator
import com.example.system.DndFocusController
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

        serviceScope.launch {
            refreshAndMonitor()
        }
        return START_STICKY
    }

    private suspend fun refreshAndMonitor() {
        val appContext = applicationContext
        val repository = BrainBufferRepository(
            BrainBufferDatabase.getInstance(appContext),
            UserPreferencesStore(appContext)
        )
        val snapshot = repository.getTimerSnapshotOnce()
        val prefs = repository.getPreferencesOnce()

        if (!snapshot.hasStarted || !prefs.notificationsEnabled || !prefs.notificationPrefs.activeFocusEnabled) {
            stopForegroundAndSelf()
            return
        }

        val subjects = repository.getSubjectsOnce()
        val subjectName = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }?.name ?: "General Study"

        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        if (FocusTimerEngine.isCompleted(snapshot, nowEpoch, nowElapsed)) {
            completeFromService(appContext, repository, snapshot, subjectName, nowEpoch)
            stopForegroundAndSelf()
            return
        }

        val notification = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = appContext,
            snapshot = snapshot,
            subjectName = subjectName,
            prefs = prefs.notificationPrefs,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed,
            liveUpdatesEnabled = prefs.focusGuardSettings.liveUpdatesEnabled
        )

        startForegroundSafely(notification)

        monitorJob?.cancel()
        if (snapshot.runningSinceEpochMs == null) {
            // Timer is paused: keep notification visible with Resume/End actions, zero wakeups!
            return
        }

        monitorJob = serviceScope.launch {
            var lastNotificationRefreshMs = nowEpoch
            while (isActive) {
                val latest = repository.getTimerSnapshotOnce()
                val latestPrefs = repository.getPreferencesOnce()
                if (!latest.hasStarted || latest.runningSinceEpochMs == null) {
                    if (!latest.hasStarted) {
                        stopForegroundAndSelf()
                    }
                    break
                }
                val epochNow = System.currentTimeMillis()
                val elapsedNow = SystemClock.elapsedRealtime()
                val remainingSec = FocusTimerEngine.remainingSeconds(latest, epochNow, elapsedNow)

                if (remainingSec <= 0) {
                    val latestSubjects = repository.getSubjectsOnce()
                    val latestSubject = latestSubjects.firstOrNull { it.id == latest.selectedSubjectId }?.name
                        ?: "General Study"
                    completeFromService(appContext, repository, latest, latestSubject, epochNow)
                    stopForegroundAndSelf()
                    break
                }

                // Determine efficient poll interval: 12s ONLY when opt-in distraction monitoring is active,
                // otherwise ultra-low-power 30s interval (native OS chronometer handles per-second display).
                val distractionWatchActive = (latest.isStrictFocus || latest.focusGuardLevel != FocusGuardLevel.NORMAL) &&
                    latestPrefs.appUsageAwarenessEnabled &&
                    latestPrefs.focusGuardSettings.distractionWarningsEnabled &&
                    latestPrefs.notificationPrefs.distractionWarningsEnabled &&
                    AppUsageAwarenessHelper.hasUsageStatsPermission(appContext)

                val baseIntervalMs = if (distractionWatchActive) 12_000L else 30_000L
                val sleepMs = minOf(baseIntervalMs, (remainingSec * 1000L).coerceAtLeast(1_000L))
                delay(sleepMs)

                val refreshed = repository.getTimerSnapshotOnce()
                if (!refreshed.hasStarted || refreshed.runningSinceEpochMs == null) continue

                val refreshEpoch = System.currentTimeMillis()
                val refreshElapsed = SystemClock.elapsedRealtime()
                if (FocusTimerEngine.isCompleted(refreshed, refreshEpoch, refreshElapsed)) continue

                val refreshedPrefs = repository.getPreferencesOnce()
                val activeSubject = repository.getSubjectsOnce()
                    .firstOrNull { it.id == refreshed.selectedSubjectId }?.name
                    ?: subjectName

                // Optional Usage Access distraction check during active High/Custom Focus
                if (distractionWatchActive) {
                    val mappings = repository.getAppCategoryMappingsOnce()
                    val warning = AppUsageAwarenessHelper.detectUnapprovedAppMismatch(
                        context = appContext,
                        enabledByUser = true,
                        snapshot = refreshed,
                        userMappings = mappings,
                        nowEpochMs = refreshEpoch
                    )
                    if (warning != null &&
                        DistractionAlertCoordinator.shouldEmitAlert(
                            sessionId = refreshed.sessionId,
                            packageName = warning.packageName,
                            nowEpochMs = refreshEpoch,
                            cooldownSeconds = refreshedPrefs.focusGuardSettings.distractionCooldownSeconds
                        )
                    ) {
                        val posted = BrainBufferNotificationManager.postDistractionWarningNotification(
                            context = appContext,
                            snapshot = refreshed,
                            subjectName = activeSubject,
                            detectedPackageName = warning.packageName,
                            detectedAppLabel = warning.appDisplayName,
                            prefs = refreshedPrefs.notificationPrefs,
                            masterNotificationsEnabled = refreshedPrefs.notificationsEnabled,
                            soundEnabled = refreshedPrefs.soundEnabled &&
                                refreshedPrefs.focusGuardSettings.notificationSoundEnabled,
                            vibrationEnabled = refreshedPrefs.hapticsEnabled &&
                                refreshedPrefs.focusGuardSettings.vibrationEnabled
                        )
                        if (posted) {
                            DistractionAlertCoordinator.markNotified(
                                sessionId = refreshed.sessionId,
                                packageName = warning.packageName,
                                nowEpochMs = refreshEpoch
                            )
                            repository.recordDistractionWarning()
                        }
                    }
                }

                // Refresh live progress notification every ~30s to keep battery impact negligible
                if (refreshEpoch - lastNotificationRefreshMs >= 28_000L) {
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
        nowEpochMs: Long
    ) {
        val prefs = repository.getPreferencesOnce()
        val subjects = repository.getSubjectsOnce()
        val subject = subjects.firstOrNull { it.id == snapshot.selectedSubjectId }
        val sessionKey = snapshot.sessionId.ifBlank { FocusTimerEngine.generateSessionId() }
        val todayEpoch = LocalDate.now().toEpochDay()

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
                plannedSeconds = snapshot.totalSeconds,
                actualSeconds = snapshot.totalSeconds,
                startedAtMs = snapshot.activityStartedAtEpochMs ?: (nowEpochMs - snapshot.totalSeconds * 1000L),
                endedAtMs = nowEpochMs,
                dateEpochDay = todayEpoch,
                wasPlanned = snapshot.activityWasPlanned,
                notes = snapshot.sessionGoal
            )
        )

        // ONLY focused study activities count toward study stats & garden growth (never breaks/gaming/entertainment)
        if (snapshot.activeActivityCategory.countsAsFocusedStudy && snapshot.mode == FocusMode.FOCUS) {
            val existingSessions = repository.focusSessions.first()
            repository.recordFocusSessionAndGrowGarden(
                session = FocusSessionRecord(
                    subjectId = subject?.id,
                    subjectName = subject?.name ?: subjectName,
                    subjectColorHex = subject?.colorHex ?: 0xFF7B5CFA,
                    durationSeconds = snapshot.totalSeconds,
                    plannedSeconds = snapshot.totalSeconds,
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

        // Freeze snapshot at 00:00 (paused) while keeping pausedParentSession intact so "Return to Study" works!
        val completedSnapshot = snapshot.copy(
            accumulatedFocusedMs = snapshot.totalSeconds * 1000L,
            runningSinceEpochMs = null,
            runningSinceElapsedRealtimeMs = null,
            hasStarted = false,
            strictFocusDndActive = false
        )
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

        fun syncWithSnapshot(context: Context, snapshot: PersistedTimerSnapshot) {
            val appContext = context.applicationContext
            if (!snapshot.hasStarted) {
                stopService(appContext)
                return
            }
            if (!BrainBufferNotificationManager.hasNotificationPermission(appContext)) {
                return
            }
            val intent = Intent(appContext, FocusTimerService::class.java).apply {
                action = ACTION_SYNC
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Explicitly handled without crashing on Android 12+ background FGS restrictions
                }
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
