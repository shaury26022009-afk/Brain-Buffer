package com.example.domain

import android.os.SystemClock
import com.example.model.ActivityCategory
import com.example.model.BreakPlanConfig
import com.example.model.BreakPlanMode
import com.example.model.FocusGuardLevel
import com.example.model.FocusMode
import com.example.model.FocusSessionState
import com.example.model.PausedParentSession
import com.example.model.PersistedTimerSnapshot
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Controllable clock abstraction separating monotonic duration measurement (`elapsedRealtimeMs`)
 * and wall-clock timestamps (`currentTimeMillis`) from Android framework globals so the timer
 * engine is 100% deterministic and unit-testable.
 */
interface TimerClock {
    fun elapsedRealtimeMs(): Long
    fun currentTimeMillis(): Long
}

object SystemTimerClock : TimerClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Single authoritative point-in-time timer readout produced by [FocusTimerEngine.computeTick]
 * and emitted by [FocusTimerEngine.tickState].
 */
data class FocusTimerTick(
    val snapshot: PersistedTimerSnapshot = PersistedTimerSnapshot(),
    val nowEpochMs: Long = 0L,
    val nowElapsedRealtimeMs: Long = 0L,
    val elapsedFocusedMs: Long = 0L,
    val elapsedSeconds: Int = 0,
    val remainingSeconds: Int = snapshot.totalSeconds.coerceAtLeast(0),
    val progress: Float = 0f,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val sessionState: FocusSessionState = FocusSessionState.IDLE
)

/**
 * Authoritative, monotonic + wall-clock hybrid focus timer engine.
 *
 * Instance runtime guarantees:
 * - Tracks active focus duration exclusively via [TimerClock.elapsedRealtimeMs]
 *   (defaulting to [SystemClock.elapsedRealtime]) so wall-clock jumps, timezone shifts,
 *   or NTP updates never distort an active session, with automatic wall-clock fallback across reboots.
 * - Survives configuration changes when scoped to a ViewModel/service coroutine scope, and survives
 *   process recreation via [exportPersistableSnapshot] + [restoreFromPersisted] / [reconcileExternalSnapshot].
 * - Manages all focus session states ([FocusSessionState]) atomically and race-safely under rapid taps.
 * - Maintains at most ONE active coroutine ticker loop aligned to exact second boundaries, sleeping
 *   with ZERO wakeups when idle or paused and performing ZERO database writes on per-second ticks.
 * - Enforces strictly idempotent natural completion so [onNaturalCompletion] fires at most once per sessionId.
 *
 * Companion pure helpers remain available for stateless calculations in services, notifications,
 * widgets, and deterministic unit tests.
 */
class FocusTimerEngine(
    private val scope: CoroutineScope,
    private val clock: TimerClock = SystemTimerClock,
    initialSnapshot: PersistedTimerSnapshot = PersistedTimerSnapshot(),
    private val minTickDelayMs: Long = 16L,
    private val onNaturalCompletion: (
        (PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion) -> Unit
    )? = null
) {
    private val stateLock = Any()
    private var currentSnapshot: PersistedTimerSnapshot = initialSnapshot
    private val completedSessionIds = mutableSetOf<String>()
    private var tickJob: Job? = null

    private val _tickState = MutableStateFlow(
        computeTick(
            snapshot = initialSnapshot,
            nowEpochMs = clock.currentTimeMillis(),
            nowElapsedRealtimeMs = clock.elapsedRealtimeMs()
        )
    )
    val tickState: StateFlow<FocusTimerTick> = _tickState.asStateFlow()

    val snapshot: PersistedTimerSnapshot
        get() = synchronized(stateLock) { currentSnapshot }

    val sessionState: FocusSessionState
        get() = _tickState.value.sessionState

    val isRunning: Boolean
        get() = _tickState.value.isRunning

    init {
        if (initialSnapshot.lastCompletedSessionId.isNotBlank()) {
            completedSessionIds.add(initialSnapshot.lastCompletedSessionId)
        }
        syncWithClock()
    }

    /**
     * Returns an immutable snapshot suitable for persisting to DataStore or SavedStateHandle
     * so the active or paused session survives process death and recreation.
     */
    fun exportPersistableSnapshot(): PersistedTimerSnapshot = snapshot

    /**
     * Atomically computes a new [PersistedTimerSnapshot] from the authoritative current snapshot
     * and current clock readings, updates [tickState] immediately, and manages the single ticker loop.
     */
    fun applyTransition(
        transform: (current: PersistedTimerSnapshot, nowEpochMs: Long, nowElapsedRealtimeMs: Long) -> PersistedTimerSnapshot
    ): PersistedTimerSnapshot {
        var completionToFire: Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? = null
        val updated = synchronized(stateLock) {
            val nowEpoch = clock.currentTimeMillis()
            val nowElapsed = clock.elapsedRealtimeMs()
            val next = transform(currentSnapshot, nowEpoch, nowElapsed)
            if (next.lastCompletedSessionId.isNotBlank()) {
                completedSessionIds.add(next.lastCompletedSessionId)
            }
            val changed = next != currentSnapshot
            currentSnapshot = next
            val tick = computeTick(next, nowEpoch, nowElapsed)
            _tickState.value = tick
            completionToFire = checkAndClaimNaturalCompletionLocked(next, nowEpoch, nowElapsed)
            if (completionToFire != null) {
                stopTickerLocked()
            } else if (tick.isRunning) {
                if (changed || tickJob?.isActive != true) {
                    restartTickerLocked()
                }
            } else {
                stopTickerLocked()
            }
            next
        }
        completionToFire?.let { (snap, decision) ->
            onNaturalCompletion?.invoke(snap, decision)
        }
        return updated
    }

    fun start(
        durationMinutes: Int,
        mode: FocusMode = FocusMode.FOCUS
    ): PersistedTimerSnapshot = applyTransition { current, nowEpoch, nowElapsed ->
        val configured = configureMode(current, mode, durationMinutes)
        startOrResume(configured, nowEpoch, nowElapsed)
    }

    fun startOrResume(): PersistedTimerSnapshot = applyTransition { current, nowEpoch, nowElapsed ->
        startOrResume(current, nowEpoch, nowElapsed)
    }

    fun pause(): PersistedTimerSnapshot = applyTransition { current, nowEpoch, nowElapsed ->
        pause(current, nowEpoch, nowElapsed)
    }

    fun togglePauseResume(): PersistedTimerSnapshot = applyTransition { current, nowEpoch, nowElapsed ->
        if (current.runningSinceEpochMs != null) {
            pause(current, nowEpoch, nowElapsed)
        } else {
            startOrResume(current, nowEpoch, nowElapsed)
        }
    }

    fun reset(): PersistedTimerSnapshot = applyTransition { current, _, _ ->
        reset(current)
    }

    fun extend(extraMinutes: Int): PersistedTimerSnapshot = applyTransition { current, nowEpoch, nowElapsed ->
        extendActiveTimer(current, extraMinutes, nowEpoch, nowElapsed)
    }

    /**
     * Restores persisted timer state after process recreation:
     * - Reconciles the snapshot atomically into this engine.
     * - Restarts the monotonic ticker if still running.
     * - Fires idempotent natural completion if the timer expired while the process was dead.
     * - Cleans already-completed snapshots so they can never restart accidentally.
     */
    fun restoreFromPersisted(persisted: PersistedTimerSnapshot): ProcessRestorationResult {
        var completionToFire: Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? = null
        val result = synchronized(stateLock) {
            if (persisted.lastCompletedSessionId.isNotBlank()) {
                completedSessionIds.add(persisted.lastCompletedSessionId)
            }
            val nowEpoch = clock.currentTimeMillis()
            val nowElapsed = clock.elapsedRealtimeMs()
            val restored = restoreAfterProcessRecreation(
                snapshot = persisted,
                nowEpochMs = nowEpoch,
                nowElapsedRealtimeMs = nowElapsed,
                completedSessionIds = completedSessionIds
            )
            when (restored) {
                is ProcessRestorationResult.AlreadyCompleted -> {
                    currentSnapshot = restored.snapshot
                    _tickState.value = computeTick(restored.snapshot, nowEpoch, nowElapsed)
                    stopTickerLocked()
                }
                is ProcessRestorationResult.Idle -> {
                    currentSnapshot = restored.snapshot
                    _tickState.value = computeTick(restored.snapshot, nowEpoch, nowElapsed)
                    stopTickerLocked()
                }
                is ProcessRestorationResult.Paused -> {
                    currentSnapshot = restored.snapshot
                    _tickState.value = computeTick(restored.snapshot, nowEpoch, nowElapsed)
                    stopTickerLocked()
                }
                is ProcessRestorationResult.Running -> {
                    val changed = restored.snapshot != currentSnapshot
                    currentSnapshot = restored.snapshot
                    _tickState.value = computeTick(restored.snapshot, nowEpoch, nowElapsed)
                    if (changed || tickJob?.isActive != true) {
                        restartTickerLocked()
                    }
                }
                is ProcessRestorationResult.NaturalCompletionPending -> {
                    currentSnapshot = restored.snapshot
                    _tickState.value = computeTick(restored.snapshot, nowEpoch, nowElapsed)
                    if (completedSessionIds.add(restored.decision.sessionId)) {
                        completionToFire = restored.snapshot to restored.decision
                    }
                    stopTickerLocked()
                }
            }
            restored
        }
        completionToFire?.let { (snap, decision) ->
            onNaturalCompletion?.invoke(snap, decision)
        }
        return result
    }

    /**
     * Adopts an externally persisted snapshot (e.g., on process recreation or when the user taps
     * Pause/Resume/Extend in the Android notification shade or [FocusTimerService] completes).
     */
    fun reconcileExternalSnapshot(external: PersistedTimerSnapshot) {
        var completionToFire: Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? = null
        synchronized(stateLock) {
            if (external.lastCompletedSessionId.isNotBlank()) {
                completedSessionIds.add(external.lastCompletedSessionId)
            }
            val changed = external != currentSnapshot
            if (changed) {
                currentSnapshot = external
            }
            val nowEpoch = clock.currentTimeMillis()
            val nowElapsed = clock.elapsedRealtimeMs()
            val tick = computeTick(currentSnapshot, nowEpoch, nowElapsed)
            _tickState.value = tick
            completionToFire = checkAndClaimNaturalCompletionLocked(currentSnapshot, nowEpoch, nowElapsed)
            if (completionToFire != null) {
                stopTickerLocked()
            } else if (tick.isRunning) {
                if (changed || tickJob?.isActive != true) {
                    restartTickerLocked()
                }
            } else {
                stopTickerLocked()
            }
        }
        completionToFire?.let { (snap, decision) ->
            onNaturalCompletion?.invoke(snap, decision)
        }
    }

    /**
     * Immediately recomputes elapsed/remaining time from [TimerClock.elapsedRealtimeMs], ensures
     * the ticker loop is running if active, and triggers idempotent completion if the session
     * expired while the screen was locked or the app was in the background.
     */
    fun syncWithClock(): FocusTimerTick {
        var completionToFire: Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? = null
        val tick = synchronized(stateLock) {
            val nowEpoch = clock.currentTimeMillis()
            val nowElapsed = clock.elapsedRealtimeMs()
            val computed = computeTick(currentSnapshot, nowEpoch, nowElapsed)
            _tickState.value = computed
            completionToFire = checkAndClaimNaturalCompletionLocked(currentSnapshot, nowEpoch, nowElapsed)
            if (completionToFire != null) {
                stopTickerLocked()
            } else if (computed.isRunning) {
                if (tickJob?.isActive != true) {
                    restartTickerLocked()
                }
            } else {
                stopTickerLocked()
            }
            computed
        }
        completionToFire?.let { (snap, decision) ->
            onNaturalCompletion?.invoke(snap, decision)
        }
        return tick
    }

    fun markSessionCompleted(sessionId: String) {
        if (sessionId.isBlank()) return
        synchronized(stateLock) {
            completedSessionIds.add(sessionId)
        }
    }

    fun hasCompletedSession(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        return synchronized(stateLock) { sessionId in completedSessionIds }
    }

    fun snapshotCompletedSessionIds(): Set<String> = synchronized(stateLock) {
        completedSessionIds.toSet()
    }

    fun close() {
        synchronized(stateLock) {
            stopTickerLocked()
        }
    }

    private fun checkAndClaimNaturalCompletionLocked(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long
    ): Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? {
        if (!snapshot.hasStarted) return null
        val decision = evaluateNaturalCompletion(
            snapshot = snapshot,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            completedSessionIds = completedSessionIds
        )
        return if (decision is SessionCompletionDecision.NaturalCompletion) {
            if (completedSessionIds.add(decision.sessionId)) {
                snapshot to decision
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun stopTickerLocked() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun restartTickerLocked() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                var completionToFire: Pair<PersistedTimerSnapshot, SessionCompletionDecision.NaturalCompletion>? = null
                val waitMs: Long = synchronized(stateLock) {
                    val snap = currentSnapshot
                    val nowEpoch = clock.currentTimeMillis()
                    val nowElapsed = clock.elapsedRealtimeMs()
                    val tick = computeTick(snap, nowEpoch, nowElapsed)
                    _tickState.value = tick
                    if (!snap.hasStarted || snap.runningSinceEpochMs == null) {
                        -1L
                    } else if (tick.isCompleted) {
                        completionToFire = checkAndClaimNaturalCompletionLocked(snap, nowEpoch, nowElapsed)
                        -1L
                    } else {
                        millisUntilNextSecondTick(
                            snapshot = snap,
                            nowEpochMs = nowEpoch,
                            nowElapsedRealtimeMs = nowElapsed,
                            minDelayMs = minTickDelayMs
                        )
                    }
                }
                completionToFire?.let { (snap, decision) ->
                    onNaturalCompletion?.invoke(snap, decision)
                }
                if (waitMs <= 0L) break
                delay(waitMs)
            }
        }
    }

    sealed class SessionCompletionDecision {
        data object RejectedNotStarted : SessionCompletionDecision()
        data class RejectedBeforeExpiry(
            val remainingSeconds: Int,
            val actualElapsedSeconds: Int
        ) : SessionCompletionDecision()
        data class RejectedDuplicate(val sessionId: String) : SessionCompletionDecision()
        data class NaturalCompletion(
            val sessionId: String,
            val actualSeconds: Int,
            val plannedSeconds: Int,
            val linkedTaskId: Long?
        ) : SessionCompletionDecision()
    }

    data class EarlyEndDecision(
        val sessionId: String,
        val actualSeconds: Int,
        val plannedSeconds: Int,
        val shouldPersist: Boolean,
        val status: String
    )

    sealed class ProcessRestorationResult {
        data class Idle(val snapshot: PersistedTimerSnapshot) : ProcessRestorationResult()
        data class Running(val snapshot: PersistedTimerSnapshot, val remainingSeconds: Int) : ProcessRestorationResult()
        data class Paused(val snapshot: PersistedTimerSnapshot, val remainingSeconds: Int) : ProcessRestorationResult()
        data class NaturalCompletionPending(
            val snapshot: PersistedTimerSnapshot,
            val decision: SessionCompletionDecision.NaturalCompletion
        ) : ProcessRestorationResult()
        data class AlreadyCompleted(val snapshot: PersistedTimerSnapshot) : ProcessRestorationResult()
    }

    companion object {

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

    /**
     * Computes a single consistent, invariant-preserving [FocusTimerTick] snapshot where
     * `elapsedSeconds + remainingSeconds == snapshot.totalSeconds` and values are never negative.
     */
    fun computeTick(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long
    ): FocusTimerTick {
        val totalSec = snapshot.totalSeconds.coerceAtLeast(0)
        val totalMs = totalSec.toLong() * 1000L
        val elapsedMs = elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs)
            .coerceIn(0L, totalMs)
        val elapsedSec = (elapsedMs / 1000L).toInt().coerceIn(0, totalSec)
        val remainingSec = (totalSec - elapsedSec).coerceAtLeast(0)
        val prog = if (totalMs > 0L) (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
        val completed = snapshot.hasStarted && remainingSec <= 0
        val running = snapshot.hasStarted && snapshot.runningSinceEpochMs != null && !completed
        val state = FocusSessionStateMachine.deriveState(
            snapshot = snapshot,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs
        )
        return FocusTimerTick(
            snapshot = snapshot,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            elapsedFocusedMs = elapsedMs,
            elapsedSeconds = elapsedSec,
            remainingSeconds = remainingSec,
            progress = prog,
            isRunning = running,
            isCompleted = completed,
            sessionState = state
        )
    }

    /**
     * Returns the exact delay in milliseconds until the next integer second boundary so countdown
     * ticks never drift, skip seconds, or linger for double intervals across pauses and OS jitter.
     */
    fun millisUntilNextSecondTick(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        minDelayMs: Long = 16L
    ): Long {
        if (!snapshot.hasStarted || snapshot.runningSinceEpochMs == null) return 1000L
        val totalMs = snapshot.totalSeconds.toLong().coerceAtLeast(0L) * 1000L
        val elapsedMs = elapsedFocusedMs(snapshot, nowEpochMs, nowElapsedRealtimeMs).coerceAtLeast(0L)
        val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0L)
        if (remainingMs == 0L) return 0L
        val msIntoCurrentSecond = elapsedMs % 1000L
        val msToNextBoundary = 1000L - msIntoCurrentSecond
        return minOf(remainingMs, msToNextBoundary).coerceAtLeast(minDelayMs.coerceAtLeast(1L))
    }

    /**
     * Enforces strict business-logic completion rules:
     * - NEVER completes an unstarted session.
     * - NEVER completes a session while remaining time > 0 (rejects premature/bypass attempts).
     * - NEVER completes the same sessionId twice (deduplicated via lastCompletedSessionId + completedSessionIds).
     * - Only grants natural completion when remainingSeconds <= 0.
     */
    fun evaluateNaturalCompletion(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        completedSessionIds: Set<String> = emptySet()
    ): SessionCompletionDecision {
        val currentId = snapshot.sessionId
        if (currentId.isNotBlank() &&
            (currentId == snapshot.lastCompletedSessionId || currentId in completedSessionIds)
        ) {
            return SessionCompletionDecision.RejectedDuplicate(currentId)
        }
        if (!snapshot.hasStarted) {
            return SessionCompletionDecision.RejectedNotStarted
        }
        val remaining = remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        val actual = actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        if (remaining > 0) {
            return SessionCompletionDecision.RejectedBeforeExpiry(
                remainingSeconds = remaining,
                actualElapsedSeconds = actual
            )
        }
        val resolvedId = currentId.ifBlank { generateSessionId() }
        return SessionCompletionDecision.NaturalCompletion(
            sessionId = resolvedId,
            actualSeconds = snapshot.totalSeconds,
            plannedSeconds = snapshot.totalSeconds,
            linkedTaskId = snapshot.linkedTaskId
        )
    }

    /**
     * Evaluates intentional early termination:
     * - Records ONLY actual focused elapsed seconds (never paused time, never full planned duration).
     * - Marks status as "ENDED_EARLY" (never "COMPLETED") when actual focused time > 0.
     * - Deduplicates against already-completed or already-recorded session IDs.
     */
    fun evaluateEarlyTermination(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        completedSessionIds: Set<String> = emptySet()
    ): EarlyEndDecision {
        val currentId = snapshot.sessionId.ifBlank { generateSessionId() }
        val isDuplicate = snapshot.sessionId.isNotBlank() &&
            (snapshot.sessionId == snapshot.lastCompletedSessionId || snapshot.sessionId in completedSessionIds)
        if (!snapshot.hasStarted || isDuplicate) {
            return EarlyEndDecision(
                sessionId = currentId,
                actualSeconds = 0,
                plannedSeconds = snapshot.totalSeconds,
                shouldPersist = false,
                status = "CANCELLED"
            )
        }
        val actualSeconds = actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
            .coerceAtMost((snapshot.totalSeconds - 1).coerceAtLeast(0))
        return if (actualSeconds > 0) {
            EarlyEndDecision(
                sessionId = currentId,
                actualSeconds = actualSeconds,
                plannedSeconds = snapshot.totalSeconds,
                shouldPersist = true,
                status = "ENDED_EARLY"
            )
        } else {
            EarlyEndDecision(
                sessionId = currentId,
                actualSeconds = 0,
                plannedSeconds = snapshot.totalSeconds,
                shouldPersist = false,
                status = "CANCELLED"
            )
        }
    }

    /**
     * Restores persisted timer state cleanly after app/process recreation:
     * - Never restarts a completed session accidentally.
     * - Never double-completes an already-recorded sessionId.
     * - Accurately surfaces pending natural completion if the timer expired while the process was dead.
     */
    fun restoreAfterProcessRecreation(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        completedSessionIds: Set<String> = emptySet()
    ): ProcessRestorationResult {
        val currentId = snapshot.sessionId
        val alreadyRecorded = currentId.isNotBlank() &&
            (currentId == snapshot.lastCompletedSessionId || currentId in completedSessionIds)
        if (alreadyRecorded) {
            val cleaned = markCompletedAndReset(snapshot, currentId)
            return ProcessRestorationResult.AlreadyCompleted(cleaned)
        }
        if (!snapshot.hasStarted) {
            return ProcessRestorationResult.Idle(snapshot)
        }
        val remaining = remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        if (remaining <= 0) {
            return when (val decision = evaluateNaturalCompletion(snapshot, nowEpochMs, nowElapsedRealtimeMs, completedSessionIds)) {
                is SessionCompletionDecision.NaturalCompletion ->
                    ProcessRestorationResult.NaturalCompletionPending(snapshot, decision)
                is SessionCompletionDecision.RejectedDuplicate ->
                    ProcessRestorationResult.AlreadyCompleted(markCompletedAndReset(snapshot, decision.sessionId))
                else -> ProcessRestorationResult.Idle(reset(snapshot))
            }
        }
        return if (snapshot.runningSinceEpochMs != null) {
            ProcessRestorationResult.Running(snapshot, remaining)
        } else {
            ProcessRestorationResult.Paused(snapshot, remaining)
        }
    }

    fun markCompletedAndReset(
        snapshot: PersistedTimerSnapshot,
        completedSessionId: String
    ): PersistedTimerSnapshot {
        return reset(snapshot).copy(
            sessionGoal = "",
            linkedTaskId = null,
            lastCompletedSessionId = completedSessionId
        )
    }

    fun startOrResume(
        snapshot: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        if (snapshot.runningSinceEpochMs != null) return snapshot
        val restartingFromDone = remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs) <= 0
        val needsFreshId = restartingFromDone ||
            snapshot.sessionId.isBlank() ||
            snapshot.sessionId == snapshot.lastCompletedSessionId
        val freshIfDone = if (restartingFromDone) {
            snapshot.copy(
                accumulatedFocusedMs = 0L,
                sessionId = generateSessionId(),
                activityStartedAtEpochMs = nowEpochMs
            )
        } else {
            snapshot.copy(
                sessionId = if (needsFreshId) generateSessionId() else snapshot.sessionId,
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
            approvedDocumentUri = "",
            linkedTaskId = null,
            breaksTakenCount = 0,
            accumulatedBreakSeconds = 0,
            accumulatedTempAccessSeconds = 0,
            accumulatedDistractionSeconds = 0,
            interventionCount = 0,
            sessionStartedAtEpochMs = null
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
            approvedDocumentUri = "",
            linkedTaskId = if (mode == FocusMode.FOCUS) snapshot.linkedTaskId else null
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
                    focusGuardLevel = pausedCurrent.focusGuardLevel,
                    purposeCategory = pausedCurrent.purposeCategory,
                    allowedPackages = pausedCurrent.allowedPackages,
                    allowedAppLabels = pausedCurrent.allowedAppLabels,
                    breakPlanMode = pausedCurrent.breakPlanMode,
                    breakDurationMinutes = pausedCurrent.breakDurationMinutes,
                    breakIntervalMinutes = pausedCurrent.breakIntervalMinutes,
                    breaksTakenCount = pausedCurrent.breaksTakenCount,
                    accumulatedBreakSeconds = pausedCurrent.accumulatedBreakSeconds,
                    accumulatedTempAccessSeconds = pausedCurrent.accumulatedTempAccessSeconds,
                    accumulatedDistractionSeconds = pausedCurrent.accumulatedDistractionSeconds,
                    interventionCount = pausedCurrent.interventionCount,
                    linkedTaskId = pausedCurrent.linkedTaskId,
                    sessionStartedAtEpochMs = pausedCurrent.sessionStartedAtEpochMs
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
            approvedDocumentUri = approvedDocumentUri.trim(),
            linkedTaskId = pausedCurrent.linkedTaskId,
            purposeCategory = pausedCurrent.purposeCategory,
            allowedPackages = pausedCurrent.allowedPackages,
            allowedAppLabels = pausedCurrent.allowedAppLabels,
            breakPlanMode = pausedCurrent.breakPlanMode,
            breakDurationMinutes = pausedCurrent.breakDurationMinutes,
            breakIntervalMinutes = pausedCurrent.breakIntervalMinutes,
            breaksTakenCount = pausedCurrent.breaksTakenCount,
            accumulatedBreakSeconds = pausedCurrent.accumulatedBreakSeconds,
            accumulatedTempAccessSeconds = pausedCurrent.accumulatedTempAccessSeconds,
            accumulatedDistractionSeconds = pausedCurrent.accumulatedDistractionSeconds,
            interventionCount = pausedCurrent.interventionCount,
            sessionStartedAtEpochMs = pausedCurrent.sessionStartedAtEpochMs
        )
    }

    /**
     * Restores the previously paused parent study/focus session and resumes its countdown.
     * Separately folds any elapsed break seconds into [PersistedTimerSnapshot.accumulatedBreakSeconds]
     * so break duration is NEVER counted as focused time.
     */
    fun returnToPausedStudySession(
        current: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot? {
        val parent = current.pausedParentSession ?: return null
        val breakElapsedSec = if (current.activeActivityCategory == ActivityCategory.BREAK) {
            actualFocusedSeconds(current, nowEpochMs, nowElapsedRealtimeMs)
        } else {
            0
        }
        val extraBreaks = if (current.activeActivityCategory == ActivityCategory.BREAK) 1 else 0
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
            approvedDocumentUri = "",
            linkedTaskId = parent.linkedTaskId,
            purposeCategory = parent.purposeCategory,
            allowedPackages = parent.allowedPackages,
            allowedAppLabels = parent.allowedAppLabels,
            breakPlanMode = parent.breakPlanMode,
            breakDurationMinutes = parent.breakDurationMinutes,
            breakIntervalMinutes = parent.breakIntervalMinutes,
            breaksTakenCount = parent.breaksTakenCount + extraBreaks,
            accumulatedBreakSeconds = parent.accumulatedBreakSeconds + breakElapsedSec,
            accumulatedTempAccessSeconds = parent.accumulatedTempAccessSeconds,
            accumulatedDistractionSeconds = parent.accumulatedDistractionSeconds,
            interventionCount = parent.interventionCount,
            sessionStartedAtEpochMs = parent.sessionStartedAtEpochMs
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

    /**
     * Resolves the real parent focus session before early termination when the user exits
     * during a Break or while Temporary App Access is open.
     *
     * Guarantees:
     * - Parent focus session's actual accumulated focus seconds are preserved (never discarded!).
     * - Break seconds are folded into `accumulatedBreakSeconds`, NEVER into `accumulatedFocusedMs`.
     * - Open Temporary App Access seconds are folded into `accumulatedTempAccessSeconds`,
     *   NEVER into `accumulatedFocusedMs`.
     */
    fun resolveParentForEarlyExit(
        snapshot: PersistedTimerSnapshot,
        activeTempAccess: com.example.model.TemporaryAppAccessSession?,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        val openTempSec = activeTempAccess
            ?.takeIf { it.endedAtEpochMs == null }
            ?.actualDurationSeconds(nowEpochMs)
            ?.coerceAtLeast(0)
            ?: 0

        val parent = snapshot.pausedParentSession
        if (parent != null) {
            val childElapsedSec = actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
            val isBreakChild = snapshot.isOnBreak
            return PersistedTimerSnapshot(
                mode = parent.mode,
                selectedSubjectId = parent.selectedSubjectId,
                sessionGoal = parent.sessionGoal,
                totalSeconds = parent.totalSeconds,
                accumulatedFocusedMs = parent.accumulatedFocusedMs,
                runningSinceEpochMs = null,
                runningSinceElapsedRealtimeMs = null,
                hasStarted = true,
                sessionId = parent.sessionId,
                activeActivityType = parent.activityType,
                activeActivityLabel = parent.activityLabel,
                activeActivityCategory = parent.activityType.category,
                activityStartedAtEpochMs = parent.sessionStartedAtEpochMs ?: snapshot.activityStartedAtEpochMs,
                activityWasPlanned = true,
                isStrictFocus = parent.isStrictFocus,
                strictFocusAodStyle = parent.strictFocusAodStyle,
                strictFocusDndActive = snapshot.strictFocusDndActive,
                strictFocusStrictness = snapshot.strictFocusStrictness,
                pausedParentSession = null,
                focusGuardLevel = parent.focusGuardLevel,
                linkedTaskId = parent.linkedTaskId,
                lastCompletedSessionId = snapshot.lastCompletedSessionId,
                purposeCategory = parent.purposeCategory,
                allowedPackages = parent.allowedPackages,
                allowedAppLabels = parent.allowedAppLabels,
                breakPlanMode = parent.breakPlanMode,
                breakDurationMinutes = parent.breakDurationMinutes,
                breakIntervalMinutes = parent.breakIntervalMinutes,
                breaksTakenCount = parent.breaksTakenCount + if (isBreakChild) 1 else 0,
                accumulatedBreakSeconds = parent.accumulatedBreakSeconds + if (isBreakChild) childElapsedSec else 0,
                accumulatedTempAccessSeconds = parent.accumulatedTempAccessSeconds + openTempSec,
                accumulatedDistractionSeconds = parent.accumulatedDistractionSeconds,
                interventionCount = parent.interventionCount,
                sessionStartedAtEpochMs = parent.sessionStartedAtEpochMs
            )
        }

        return if (openTempSec > 0) {
            snapshot.copy(
                accumulatedTempAccessSeconds = snapshot.accumulatedTempAccessSeconds + openTempSec
            )
        } else {
            snapshot
        }
    }

    /**
     * Creates and starts a Purpose-Aware Focus Session from validated user configuration.
     * Returns null if required fields (non-blank session name or duration >= 1 min) are missing.
     */
    fun startPurposeSession(
        purposeCategory: SessionPurposeCategory,
        sessionName: String,
        durationMinutes: Int,
        selectedApps: List<SelectedSessionApp>,
        breakPlan: BreakPlanConfig = BreakPlanConfig(),
        linkedTaskId: Long? = null,
        selectedSubjectId: Long? = null,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot? {
        val cleanName = sessionName.trim()
        if (cleanName.isEmpty() || durationMinutes !in 1..240) return null
        val allowedPackages = selectedApps.map { it.packageName.trim() }.filter { it.isNotEmpty() }.toSet()
        val allowedLabels = selectedApps.map { it.appLabel.trim().ifBlank { it.packageName.substringAfterLast('.') } }
        return PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            selectedSubjectId = selectedSubjectId,
            sessionGoal = cleanName.take(80),
            totalSeconds = durationMinutes * 60,
            accumulatedFocusedMs = 0L,
            runningSinceEpochMs = nowEpochMs,
            runningSinceElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasStarted = true,
            sessionId = generateSessionId(),
            activeActivityType = purposeCategory.defaultActivityType,
            activeActivityLabel = "$cleanName · ${purposeCategory.label}",
            activeActivityCategory = purposeCategory.defaultActivityType.category,
            activityStartedAtEpochMs = nowEpochMs,
            activityWasPlanned = true,
            isStrictFocus = false,
            strictFocusAodStyle = false,
            strictFocusDndActive = false,
            strictFocusStrictness = StrictnessLevel.COMMITTED,
            pausedParentSession = null,
            focusGuardLevel = FocusGuardLevel.HIGH,
            approvedPackageName = "",
            approvedAppLabel = "",
            approvedDocumentUri = "",
            linkedTaskId = linkedTaskId,
            purposeCategory = purposeCategory,
            allowedPackages = allowedPackages,
            allowedAppLabels = allowedLabels,
            breakPlanMode = breakPlan.mode,
            breakDurationMinutes = breakPlan.breakDurationMinutes.coerceIn(1, 60),
            breakIntervalMinutes = breakPlan.intervalMinutes.coerceIn(5, 180),
            breaksTakenCount = 0,
            accumulatedBreakSeconds = 0,
            accumulatedTempAccessSeconds = 0,
            accumulatedDistractionSeconds = 0,
            interventionCount = 0,
            sessionStartedAtEpochMs = nowEpochMs
        )
    }

    /**
     * Pauses focus accumulation while the user is inside a Temporary App Access ("App Switch")
     * so temporary access time is NEVER counted as focused time.
     */
    fun pauseForTemporaryAccess(
        current: PersistedTimerSnapshot,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        return pause(current, nowEpochMs, nowElapsedRealtimeMs)
    }

    /**
     * Resumes the parent focus session after a Temporary App Access ends and records
     * the actual temporary access seconds separately.
     */
    fun resumeFromTemporaryAccess(
        current: PersistedTimerSnapshot,
        tempAccessActualSeconds: Int,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null
    ): PersistedTimerSnapshot {
        val withSeparateAccounting = current.copy(
            accumulatedTempAccessSeconds = current.accumulatedTempAccessSeconds + tempAccessActualSeconds.coerceAtLeast(0)
        )
        return startOrResume(withSeparateAccounting, nowEpochMs, nowElapsedRealtimeMs)
    }

    /**
     * Records a potential distraction intervention duration separately from focus time.
     */
    fun recordDistractionIntervention(
        current: PersistedTimerSnapshot,
        distractionSeconds: Int
    ): PersistedTimerSnapshot {
        return current.copy(
            accumulatedDistractionSeconds = current.accumulatedDistractionSeconds + distractionSeconds.coerceAtLeast(0),
            interventionCount = current.interventionCount + 1
        )
    }
    }
}

/**
 * Backwards-compatible alias pointing to the unified [FocusTimerEngine] class.
 */
typealias FocusTimerController = FocusTimerEngine
