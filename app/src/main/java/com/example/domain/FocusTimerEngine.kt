package com.example.domain

import com.example.model.ActivityCategory
import com.example.model.FocusMode
import com.example.model.PausedParentSession
import com.example.model.PersistedTimerSnapshot
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import java.util.UUID

/**
 * Pure, monotonic + wall-clock hybrid focus timer calculator.
 *
 * Guarantees:
 * - Uses monotonic `elapsedRealtimeMs` when available so wall-clock adjustments, timezone shifts,
 *   or NTP updates never distort an active study session.
 * - Falls back safely to wall-clock `epochMs` if the device rebooted and `elapsedRealtimeMs` reset
 *   below [PersistedTimerSnapshot.runningSinceElapsedRealtimeMs].
 * - Paused intervals are NEVER counted as focused time because active start anchors are cleared on pause
 *   after folding only the active segment into [PersistedTimerSnapshot.accumulatedFocusedMs].
 * - Preserves the original study session inside [PersistedTimerSnapshot.pausedParentSession] when
 *   switching temporarily to a lecture, break, reading, gaming, or custom activity.
 */
object FocusTimerEngine {

    fun generateSessionId(): String = UUID.randomUUID().toString()

    fun elapsedFocusedMs(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): Long {
        val activeSegmentMs = when {
            snapshot.runningSinceElapsedRealtimeMs != null &&
                nowElapsedRealtimeMs != null &&
                nowElapsedRealtimeMs >= snapshot.runningSinceElapsedRealtimeMs -> {
                (nowElapsedRealtimeMs - snapshot.runningSinceElapsedRealtimeMs).coerceAtLeast(0L)
            }
            snapshot.runningSinceEpochMs != null -> {
                (nowEpochMs - snapshot.runningSinceEpochMs).coerceAtLeast(0L)
            }
            else -> 0L
        }
        return (snapshot.accumulatedFocusedMs + activeSegmentMs).coerceAtLeast(0L)
    }

    fun remainingSeconds(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): Int {
        val elapsedSec = (elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs) / 1000L).toInt()
        return (snapshot.totalSeconds - elapsedSec).coerceAtLeast(0)
    }

    fun isCompleted(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): Boolean {
        return snapshot.hasStarted && remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs) <= 0
    }

    fun progress(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): Float {
        if (snapshot.totalSeconds <= 0) return 0f
        val elapsed = elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs).toFloat() / 1000f
        return (elapsed / snapshot.totalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    fun startOrResume(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        if (snapshot.runningSinceEpochMs != null) return snapshot
        val restartingFromDone = remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs) <= 0
        val freshIfDone = if (restartingFromDone) {
            snapshot.copy(
                accumulatedFocusedMs = 0L,
                sessionId = generateSessionId(),
                activityStartedAtEpochMs = nowEpochMs
            )
        } else {
            snapshot.copy(
                sessionId = snapshot.sessionId.ifBlank { generateSessionId() },
                activityStartedAtEpochMs = snapshot.activityStartedAtEpochMs ?: nowEpochMs
            )
        }
        return freshIfDone.copy(
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true
        )
    }

    fun pause(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        if (snapshot.runningSinceEpochMs == null && snapshot.runningSinceElapsedRealtimeMs == null) {
            return snapshot
        }
        val updatedAccumulated = elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs)
            .coerceAtMost(snapshot.totalSeconds * 1000L)
        return snapshot.copy(
            accumulatedFocusedMs = updatedAccumulated,
            runningSinceEpochMs = null,
            runningSinceElapsedRealtimeMs = null
        )
    }

    fun reset(snapshot: PersistedTimerSnapshot): PersistedTimerSnapshot {
        return snapshot.copy(
            accumulatedFocusedMs = 0L,
            runningSinceEpochMs = null,
            runningSinceElapsedRealtimeMs = null,
            hasStarted = false,
            sessionId = "",
            activityStartedAtEpochMs = null,
            isStrictFocus = false,
            strictFocusAodStyle = false,
            strictFocusDndActive = false,
            pausedParentSession = null,
            focusGuardLevel = com.example.model.FocusGuardLevel.NORMAL,
            approvedPackageName = "",
            approvedAppLabel = "",
            approvedDocumentUri = ""
        )
    }

    fun configureMode(
        snapshot: PersistedTimerSnapshot,
        mode: FocusMode,
        durationMinutes: Int
    ): PersistedTimerSnapshot {
        val clampedSeconds = durationMinutes.coerceIn(1, 180) * 60
        val defaultActivity = when (mode) {
            FocusMode.FOCUS -> StudyActivityType.STUDYING
            FocusMode.SHORT_BREAK, FocusMode.LONG_BREAK -> StudyActivityType.SHORT_BREAK
        }
        return snapshot.copy(
            mode = mode,
            totalSeconds = clampedSeconds,
            accumulatedFocusedMs = 0L,
            runningSinceEpochMs = null,
            runningSinceElapsedRealtimeMs = null,
            hasStarted = false,
            sessionId = "",
            activeActivityType = defaultActivity,
            activeActivityLabel = defaultActivity.label,
            activeActivityCategory = defaultActivity.category,
            activityStartedAtEpochMs = null,
            activityWasPlanned = true,
            isStrictFocus = false,
            strictFocusAodStyle = false,
            strictFocusDndActive = false,
            pausedParentSession = null,
            focusGuardLevel = com.example.model.FocusGuardLevel.NORMAL,
            approvedPackageName = "",
            approvedAppLabel = "",
            approvedDocumentUri = ""
        )
    }

    /**
     * Switches to a new activity (e.g., Study PDF, Study Lecture, Short Break, Gaming, Social, Custom).
     * If a study session was already in progress, snapshots it into [PersistedTimerSnapshot.pausedParentSession]
     * so the student can seamlessly return to the exact remaining time later.
     */
    fun switchActivity(
        current: PersistedTimerSnapshot,
        activityType: StudyActivityType,
        activityLabel: String,
        category: ActivityCategory,
        durationMinutes: Int,
        subjectId: Long?,
        wasPlanned: Boolean,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        approvedPackageName: String = "",
        approvedAppLabel: String = "",
        approvedDocumentUri: String = ""
    ): PersistedTimerSnapshot {
        val pausedCurrent = pause(current, nowEpochMs, nowElapsedRealtimeMs)
        val preservedParent: PausedParentSession? = when {
            // Already on a switched activity: keep original root study session intact!
            pausedCurrent.pausedParentSession != null -> pausedCurrent.pausedParentSession
            // Currently in an active study/focus session with remaining time: save it
            pausedCurrent.hasStarted &&
                pausedCurrent.activeActivityCategory.countsAsFocusedStudy &&
                remainingSeconds(pausedCurrent, nowEpochMs, nowElapsedRealtimeMs) > 0 -> {
                PausedParentSession(
                    sessionId = pausedCurrent.sessionId.ifBlank { generateSessionId() },
                    mode = pausedCurrent.mode,
                    activityType = pausedCurrent.activeActivityType,
                    activityLabel = pausedCurrent.activeActivityLabel,
                    selectedSubjectId = pausedCurrent.selectedSubjectId,
                    sessionGoal = pausedCurrent.sessionGoal,
                    totalSeconds = pausedCurrent.totalSeconds,
                    accumulatedFocusedMs = pausedCurrent.accumulatedFocusedMs,
                    isStrictFocus = pausedCurrent.isStrictFocus,
                    strictFocusAodStyle = pausedCurrent.strictFocusAodStyle,
                    focusGuardLevel = pausedCurrent.focusGuardLevel
                )
            }
            else -> null
        }

        val targetMode = when (category) {
            ActivityCategory.BREAK -> if (durationMinutes >= 15) FocusMode.LONG_BREAK else FocusMode.SHORT_BREAK
            else -> FocusMode.FOCUS
        }

        return PersistedTimerSnapshot(
            mode = targetMode,
            selectedSubjectId = subjectId ?: pausedCurrent.selectedSubjectId,
            sessionGoal = pausedCurrent.sessionGoal,
            totalSeconds = durationMinutes.coerceIn(1, 180) * 60,
            accumulatedFocusedMs = 0L,
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true,
            sessionId = generateSessionId(),
            activeActivityType = activityType,
            activeActivityLabel = activityLabel.trim().ifBlank { activityType.label },
            activeActivityCategory = category,
            activityStartedAtEpochMs = nowEpochMs,
            activityWasPlanned = wasPlanned,
            isStrictFocus = pausedCurrent.isStrictFocus && category.countsAsFocusedStudy,
            strictFocusAodStyle = pausedCurrent.strictFocusAodStyle && category.countsAsFocusedStudy,
            strictFocusDndActive = pausedCurrent.strictFocusDndActive,
            strictFocusStrictness = pausedCurrent.strictFocusStrictness,
            pausedParentSession = preservedParent,
            focusGuardLevel = pausedCurrent.focusGuardLevel,
            approvedPackageName = approvedPackageName.trim(),
            approvedAppLabel = approvedAppLabel.trim(),
            approvedDocumentUri = approvedDocumentUri.trim()
        )
    }

    /**
     * Restores the previously paused parent study session and resumes its countdown.
     */
    fun returnToPausedStudySession(
        current: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot? {
        val parent = current.pausedParentSession ?: return null
        return PersistedTimerSnapshot(
            mode = parent.mode,
            selectedSubjectId = parent.selectedSubjectId,
            sessionGoal = parent.sessionGoal,
            totalSeconds = parent.totalSeconds,
            accumulatedFocusedMs = parent.accumulatedFocusedMs,
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true,
            sessionId = parent.sessionId,
            activeActivityType = parent.activityType,
            activeActivityLabel = parent.activityLabel,
            activeActivityCategory = parent.activityType.category,
            activityStartedAtEpochMs = nowEpochMs,
            activityWasPlanned = true,
            isStrictFocus = parent.isStrictFocus,
            strictFocusAodStyle = parent.strictFocusAodStyle,
            strictFocusDndActive = current.strictFocusDndActive,
            strictFocusStrictness = current.strictFocusStrictness,
            pausedParentSession = null,
            focusGuardLevel = parent.focusGuardLevel,
            approvedPackageName = "",
            approvedAppLabel = "",
            approvedDocumentUri = ""
        )
    }

    /**
     * Extends the active timer (study, break, lecture, gaming, etc.) by [extraMinutes] without guilt
     * and resumes if it had already reached 00:00.
     */
    fun extendActiveTimer(
        snapshot: PersistedTimerSnapshot,
        extraMinutes: Int,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        val clampedExtraSec = extraMinutes.coerceIn(1, 60) * 60
        val paused = pause(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        return paused.copy(
            totalSeconds = (paused.totalSeconds + clampedExtraSec).coerceAtMost(240 * 60),
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true
        )
    }

    /**
     * Starts a timed break directly while keeping any paused parent study session intact.
     */
    fun startBreakPreservingParent(
        current: PersistedTimerSnapshot,
        breakMinutes: Int,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        return switchActivity(
            current = current,
            activityType = StudyActivityType.SHORT_BREAK,
            activityLabel = "Taking a short break",
            category = ActivityCategory.BREAK,
            durationMinutes = breakMinutes.coerceIn(1, 60),
            subjectId = current.selectedSubjectId,
            wasPlanned = true,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs
        )
    }

    /**
     * Configures and starts a Focus Guard session (Normal, High, or Custom).
     */
    fun startStrictFocus(
        subjectId: Long?,
        sessionGoal: String,
        activityType: StudyActivityType,
        durationMinutes: Int,
        strictness: StrictnessLevel,
        enableAodStyle: Boolean,
        dndActive: Boolean,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        guardLevel: com.example.model.FocusGuardLevel = com.example.model.FocusGuardLevel.HIGH
    ): PersistedTimerSnapshot {
        val isDedicatedGuard = guardLevel != com.example.model.FocusGuardLevel.NORMAL
        return PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            selectedSubjectId = subjectId,
            sessionGoal = sessionGoal.take(80),
            totalSeconds = durationMinutes.coerceIn(5, 180) * 60,
            accumulatedFocusedMs = 0L,
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true,
            sessionId = generateSessionId(),
            activeActivityType = activityType,
            activeActivityLabel = activityType.label,
            activeActivityCategory = activityType.category,
            activityStartedAtEpochMs = nowEpochMs,
            activityWasPlanned = true,
            isStrictFocus = isDedicatedGuard,
            strictFocusAodStyle = isDedicatedGuard && enableAodStyle,
            strictFocusDndActive = dndActive,
            strictFocusStrictness = strictness,
            pausedParentSession = null,
            focusGuardLevel = guardLevel,
            approvedPackageName = "",
            approvedAppLabel = "",
            approvedDocumentUri = ""
        )
    }

    /**
     * Returns actual focused seconds (capped at planned totalSeconds) — never includes paused time.
     */
    fun actualFocusedSeconds(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): Int {
        val elapsedSec = (elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs) / 1000L).toInt()
        return elapsedSec.coerceIn(0, snapshot.totalSeconds)
    }
}
