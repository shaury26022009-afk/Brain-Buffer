package com.example.domain

import com.example.model.ActivityCategory
import com.example.model.FocusMode
import com.example.model.FocusSessionState
import com.example.model.PersistedTimerSnapshot

/**
 * Deterministic 10-state Focus Guard 3.0 session state machine (Part 9).
 *
 * States:
 * 1. IDLE
 * 2. FOCUSING ("Studying")
 * 3. ACTIVITY_REQUEST
 * 4. APPROVED_STUDY_ACTIVITY (Lecture, PDF, Reading, Notes)
 * 5. APPROVED_ENTERTAINMENT_ACTIVITY (Gaming, Social Media, Other timed allowance)
 * 6. BREAK
 * 7. PAUSED
 * 8. RETURNING_TO_FOCUS
 * 9. COMPLETED
 * 10. CANCELLED
 */
object FocusSessionStateMachine {

    fun deriveState(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        isRequestingActivity: Boolean = false,
        isReturningToFocus: Boolean = false,
        wasRecentlyCancelled: Boolean = false
    ): FocusSessionState {
        if (isRequestingActivity) return FocusSessionState.ACTIVITY_REQUEST
        if (isReturningToFocus) return FocusSessionState.RETURNING_TO_FOCUS
        if (wasRecentlyCancelled && !snapshot.hasStarted && snapshot.accumulatedFocusedMs == 0L) {
            return FocusSessionState.CANCELLED
        }

        val remaining = FocusTimerEngine.remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        val isRunning = snapshot.runningSinceEpochMs != null

        if (!snapshot.hasStarted) {
            return if (snapshot.totalSeconds > 0 && snapshot.accumulatedFocusedMs >= snapshot.totalSeconds * 1000L) {
                if (snapshot.pausedParentSession != null) {
                    FocusSessionState.RETURNING_TO_FOCUS
                } else {
                    FocusSessionState.COMPLETED
                }
            } else {
                FocusSessionState.IDLE
            }
        }

        if (remaining <= 0) {
            return if (snapshot.pausedParentSession != null) {
                FocusSessionState.RETURNING_TO_FOCUS
            } else {
                FocusSessionState.COMPLETED
            }
        }

        if (!isRunning) {
            return FocusSessionState.PAUSED
        }

        return when {
            snapshot.activeActivityCategory == ActivityCategory.BREAK ||
                snapshot.mode == FocusMode.SHORT_BREAK ||
                snapshot.mode == FocusMode.LONG_BREAK -> FocusSessionState.BREAK

            snapshot.activeActivityCategory == ActivityCategory.LECTURE ||
                snapshot.activeActivityCategory == ActivityCategory.READING ->
                FocusSessionState.APPROVED_STUDY_ACTIVITY

            snapshot.activeActivityCategory == ActivityCategory.ENTERTAINMENT ||
                snapshot.activeActivityCategory == ActivityCategory.OTHER ->
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY

            snapshot.isSwitchedActivity ->
                FocusSessionState.APPROVED_STUDY_ACTIVITY

            else -> FocusSessionState.FOCUSING
        }
    }

    /**
     * Validates legal transitions across all 10 states for deterministic orchestration and testing.
     */
    fun canTransition(from: FocusSessionState, to: FocusSessionState): Boolean {
        if (from == to) return true
        return when (from) {
            FocusSessionState.IDLE -> to in setOf(
                FocusSessionState.FOCUSING,
                FocusSessionState.BREAK,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.ACTIVITY_REQUEST
            )
            FocusSessionState.FOCUSING -> to in setOf(
                FocusSessionState.PAUSED,
                FocusSessionState.ACTIVITY_REQUEST,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.BREAK,
                FocusSessionState.COMPLETED,
                FocusSessionState.CANCELLED
            )
            FocusSessionState.PAUSED -> to in setOf(
                FocusSessionState.FOCUSING,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.BREAK,
                FocusSessionState.ACTIVITY_REQUEST,
                FocusSessionState.RETURNING_TO_FOCUS,
                FocusSessionState.CANCELLED,
                FocusSessionState.IDLE
            )
            FocusSessionState.ACTIVITY_REQUEST -> to in setOf(
                FocusSessionState.FOCUSING,
                FocusSessionState.PAUSED,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.BREAK,
                FocusSessionState.IDLE
            )
            FocusSessionState.APPROVED_STUDY_ACTIVITY -> to in setOf(
                FocusSessionState.PAUSED,
                FocusSessionState.ACTIVITY_REQUEST,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.BREAK,
                FocusSessionState.RETURNING_TO_FOCUS,
                FocusSessionState.COMPLETED,
                FocusSessionState.CANCELLED
            )
            FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY -> to in setOf(
                FocusSessionState.PAUSED,
                FocusSessionState.ACTIVITY_REQUEST,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.BREAK,
                FocusSessionState.RETURNING_TO_FOCUS,
                FocusSessionState.COMPLETED,
                FocusSessionState.CANCELLED
            )
            FocusSessionState.BREAK -> to in setOf(
                FocusSessionState.PAUSED,
                FocusSessionState.ACTIVITY_REQUEST,
                FocusSessionState.RETURNING_TO_FOCUS,
                FocusSessionState.FOCUSING,
                FocusSessionState.COMPLETED,
                FocusSessionState.CANCELLED
            )
            FocusSessionState.RETURNING_TO_FOCUS -> to in setOf(
                FocusSessionState.FOCUSING,
                FocusSessionState.IDLE,
                FocusSessionState.CANCELLED
            )
            FocusSessionState.COMPLETED -> to in setOf(
                FocusSessionState.IDLE,
                FocusSessionState.FOCUSING,
                FocusSessionState.BREAK,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
                FocusSessionState.RETURNING_TO_FOCUS
            )
            FocusSessionState.CANCELLED -> to in setOf(
                FocusSessionState.IDLE,
                FocusSessionState.FOCUSING,
                FocusSessionState.BREAK,
                FocusSessionState.APPROVED_STUDY_ACTIVITY,
                FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY
            )
        }
    }
}
