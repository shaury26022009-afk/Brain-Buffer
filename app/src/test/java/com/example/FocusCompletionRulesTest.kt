package com.example

import com.example.domain.FocusTimerEngine
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.FocusMode
import com.example.model.ManualBlockingSession
import com.example.model.PersistedTimerSnapshot
import com.example.model.TemporaryAppAllowance
import com.example.system.AppUsageAwarenessHelper
import com.example.system.PhoneUsageAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandatory unit tests for Focus Completion Rules (Critical Green Completion Button bug fix),
 * early session termination, process recreation idempotency, temporary app allowances,
 * and Shopping distraction category classification.
 */
class FocusCompletionRulesTest {

    private fun runningSnapshot(
        sessionId: String = "sess-1001",
        totalSeconds: Int = 25 * 60,
        accumulatedFocusedMs: Long = 0L,
        runningSinceEpochMs: Long? = 1_700_000_000_000L,
        runningSinceElapsedRealtimeMs: Long? = 100_000L,
        hasStarted: Boolean = true,
        linkedTaskId: Long? = 42L,
        lastCompletedSessionId: String = ""
    ): PersistedTimerSnapshot = PersistedTimerSnapshot(
        mode = FocusMode.FOCUS,
        selectedSubjectId = 7L,
        sessionGoal = "Finish chapter notes",
        totalSeconds = totalSeconds,
        accumulatedFocusedMs = accumulatedFocusedMs,
        runningSinceEpochMs = runningSinceEpochMs,
        runningSinceElapsedRealtimeMs = runningSinceElapsedRealtimeMs,
        hasStarted = hasStarted,
        sessionId = sessionId,
        linkedTaskId = linkedTaskId,
        lastCompletedSessionId = lastCompletedSessionId
    )

    @Test
    fun `1 - natural countdown reaching zero completes once with full duration and resets`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val started = runningSnapshot(
            sessionId = "sess-5001",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0
        )

        // Evaluate right at natural finish time (+1500s)
        val decision = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = started,
            nowEpochMs = t0 + 1_500_000L,
            nowElapsedRealtimeMs = e0 + 1_500_000L
        )
        assertTrue(
            "Expected NaturalCompletion when remaining <= 0",
            decision is FocusTimerEngine.SessionCompletionDecision.NaturalCompletion
        )
        val completed = decision as FocusTimerEngine.SessionCompletionDecision.NaturalCompletion
        assertEquals("sess-5001", completed.sessionId)
        assertEquals(1500, completed.actualSeconds)
        assertEquals(1500, completed.plannedSeconds)
        assertEquals(42L, completed.linkedTaskId)

        // Reset snapshot marks session as completed and prevents restart
        val reset = FocusTimerEngine.markCompletedAndReset(started, completed.sessionId)
        assertNull(reset.runningSinceEpochMs)
        assertNull(reset.runningSinceElapsedRealtimeMs)
        assertFalse(reset.hasStarted)
        assertEquals("sess-5001", reset.lastCompletedSessionId)
        assertEquals(0L, reset.accumulatedFocusedMs)
        assertEquals(1500, FocusTimerEngine.remainingSeconds(reset, t0 + 1_510_000L, e0 + 1_510_000L))
    }

    @Test
    fun `2 - natural completion is strictly refused while remainingSeconds is positive`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val midSession = runningSnapshot(
            sessionId = "sess-5002",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0
        )

        // Only 600 seconds elapsed -> 900 seconds still left
        val decision = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = midSession,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = e0 + 600_000L
        )
        assertTrue(
            "Must reject premature completion when timer has not reached zero",
            decision is FocusTimerEngine.SessionCompletionDecision.RejectedBeforeExpiry
        )
        val rejected = decision as FocusTimerEngine.SessionCompletionDecision.RejectedBeforeExpiry
        assertEquals(900, rejected.remainingSeconds)
        assertEquals(600, rejected.actualElapsedSeconds)
    }

    @Test
    fun `3 - duplicate completion attempts for the same sessionId are deduplicated`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val alreadyCompleted = runningSnapshot(
            sessionId = "sess-5003",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0,
            lastCompletedSessionId = "sess-5003"
        )
        val decision = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = alreadyCompleted,
            nowEpochMs = t0 + 1_505_000L,
            nowElapsedRealtimeMs = e0 + 1_505_000L
        )
        assertTrue(
            "Duplicate completion for already-logged sessionId must return RejectedDuplicate",
            decision is FocusTimerEngine.SessionCompletionDecision.RejectedDuplicate
        )

        // Also rejected when sessionId is already present in completedSessionIds set
        val secondAttempt = runningSnapshot(
            sessionId = "sess-5003-set",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0
        )
        val decisionFromSet = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = secondAttempt,
            nowEpochMs = t0 + 1_505_000L,
            nowElapsedRealtimeMs = e0 + 1_505_000L,
            completedSessionIds = setOf("sess-5003-set")
        )
        assertTrue(decisionFromSet is FocusTimerEngine.SessionCompletionDecision.RejectedDuplicate)
    }

    @Test
    fun `4 - early termination logs ONLY actual elapsed time and never awards full planned duration`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        // Planned 25m (1500s), user ends early after 7m 30s (450s elapsed)
        val active = runningSnapshot(
            sessionId = "sess-5004",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0,
            linkedTaskId = 99L
        )
        val decision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = active,
            nowEpochMs = t0 + 450_000L,
            nowElapsedRealtimeMs = e0 + 450_000L
        )
        assertEquals("sess-5004", decision.sessionId)
        assertEquals(450, decision.actualSeconds)
        assertEquals(1500, decision.plannedSeconds)
        assertTrue("Elapsed > 0s should persist honest ENDED_EARLY record", decision.shouldPersist)
        assertEquals("ENDED_EARLY", decision.status)
        assertTrue("Early end never claims full planned duration", decision.actualSeconds < decision.plannedSeconds)
    }

    @Test
    fun `5 - early termination with zero elapsed or unstarted session cancels cleanly without persisting`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val unstarted = runningSnapshot(
            sessionId = "sess-5005",
            totalSeconds = 1500,
            runningSinceEpochMs = null,
            runningSinceElapsedRealtimeMs = null,
            hasStarted = false
        )
        val decision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = unstarted,
            nowEpochMs = t0 + 20_000L,
            nowElapsedRealtimeMs = e0 + 20_000L
        )
        assertEquals(0, decision.actualSeconds)
        assertFalse("Unstarted abort must not persist focus history", decision.shouldPersist)
        assertEquals("CANCELLED", decision.status)

        // Immediate 0-second abort right after start also cancels cleanly
        val zeroElapsed = runningSnapshot(
            sessionId = "sess-5005-zero",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0,
            hasStarted = true
        )
        val zeroDecision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = zeroElapsed,
            nowEpochMs = t0 + 500L,
            nowElapsedRealtimeMs = e0 + 500L
        )
        assertEquals(0, zeroDecision.actualSeconds)
        assertFalse(zeroDecision.shouldPersist)
        assertEquals("CANCELLED", zeroDecision.status)
    }

    @Test
    fun `6 - process recreation after natural finish completes once and never restarts on repeat restore`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val finishedWhileKilled = runningSnapshot(
            sessionId = "sess-5006",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0,
            lastCompletedSessionId = ""
        )

        // First restoration after expiry (+2000s > 1500s) -> surfaces NaturalCompletionPending once
        val firstRestore = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = finishedWhileKilled,
            nowEpochMs = t0 + 2_000_000L,
            nowElapsedRealtimeMs = e0 + 2_000_000L
        )
        assertTrue(
            "Expected NaturalCompletionPending on first restore after expiry",
            firstRestore is FocusTimerEngine.ProcessRestorationResult.NaturalCompletionPending
        )
        val completed = firstRestore as FocusTimerEngine.ProcessRestorationResult.NaturalCompletionPending
        assertEquals("sess-5006", completed.decision.sessionId)
        assertEquals(1500, completed.decision.actualSeconds)

        // Second restoration after reset snapshot was persisted -> stays Idle, never restarts or double-logs
        val resetSnapshot = FocusTimerEngine.markCompletedAndReset(finishedWhileKilled, "sess-5006")
        val secondRestore = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = resetSnapshot,
            nowEpochMs = t0 + 2_100_000L,
            nowElapsedRealtimeMs = e0 + 2_100_000L
        )
        assertTrue(
            "Reset snapshot after completion must remain Idle and never restart",
            secondRestore is FocusTimerEngine.ProcessRestorationResult.Idle
        )
        val idleSnapshot = (secondRestore as FocusTimerEngine.ProcessRestorationResult.Idle).snapshot
        assertNull(idleSnapshot.runningSinceEpochMs)
        assertFalse(idleSnapshot.hasStarted)

        // Even if the stale running snapshot were replayed with completedSessionIds, it returns AlreadyCompleted
        val staleReplay = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = finishedWhileKilled,
            nowEpochMs = t0 + 2_100_000L,
            nowElapsedRealtimeMs = e0 + 2_100_000L,
            completedSessionIds = setOf("sess-5006")
        )
        assertTrue(staleReplay is FocusTimerEngine.ProcessRestorationResult.AlreadyCompleted)
    }

    @Test
    fun `7 - process recreation mid-session preserves accurate remaining seconds and linked task`() {
        val t0 = 1_700_000_000_000L
        val e0 = 100_000L
        val midSession = runningSnapshot(
            sessionId = "sess-5007",
            totalSeconds = 1500,
            runningSinceEpochMs = t0,
            runningSinceElapsedRealtimeMs = e0,
            linkedTaskId = 77L
        )

        // 500 seconds elapsed -> 1000 seconds remaining
        val restore = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = midSession,
            nowEpochMs = t0 + 500_000L,
            nowElapsedRealtimeMs = e0 + 500_000L
        )
        assertTrue(restore is FocusTimerEngine.ProcessRestorationResult.Running)
        val running = restore as FocusTimerEngine.ProcessRestorationResult.Running
        assertEquals(1000, running.remainingSeconds)
        assertEquals(77L, running.snapshot.linkedTaskId)
        assertTrue(running.snapshot.runningSinceEpochMs != null)
    }

    @Test
    fun `8 - temporary app allowance temporarily exempts blocked app and expires automatically`() {
        val blockedApps = listOf(
            BlockedAppRule(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                category = BlockedAppCategory.SOCIAL_MEDIA,
                blockInNormal = true,
                blockInHigh = true,
                blockInCustom = true,
                isBlocked = true
            )
        )
        val manualSession = ManualBlockingSession(
            isActive = true,
            isPaused = false,
            startedAtEpochMs = 1_000_000L,
            endsAtEpochMs = 5_000_000L
        )
        assertTrue(manualSession.isEnforcing(1_200_000L))
        val activeAllowance = listOf(
            TemporaryAppAllowance(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                reason = "Work message",
                grantedAtEpochMs = 1_100_000L,
                expiresAtEpochMs = 1_400_000L
            )
        )

        // While temporary allowance is valid (now < expiresAtEpochMs), app is NOT flagged as blocked
        val duringAllowance = AppUsageAwarenessHelper.evaluateStandaloneBlock(
            observedPackage = "com.instagram.android",
            blockedApps = blockedApps,
            temporaryAllowances = activeAllowance,
            nowEpochMs = 1_200_000L
        )
        assertNull("App with active temporary allowance must not be blocked", duringAllowance)

        // Once temporary allowance expires (now >= expiresAtEpochMs), blocking resumes automatically
        val afterExpiry = AppUsageAwarenessHelper.evaluateStandaloneBlock(
            observedPackage = "com.instagram.android",
            blockedApps = blockedApps,
            temporaryAllowances = activeAllowance,
            nowEpochMs = 1_500_000L
        )
        assertEquals("com.instagram.android", afterExpiry?.packageName)
        assertEquals("Instagram", afterExpiry?.appDisplayName)
    }

    @Test
    fun `9 - shopping category classification and essential emergency app exemption`() {
        assertEquals(
            BlockedAppCategory.SHOPPING,
            PhoneUsageAnalyticsEngine.suggestBlockedCategory("in.amazon.mShop.android.shopping", "Amazon Shopping")
        )
        assertEquals(
            BlockedAppCategory.SHOPPING,
            PhoneUsageAnalyticsEngine.suggestBlockedCategory("com.flipkart.android", "Flipkart")
        )
        // Essential phone/settings packages are always exempt from blocking
        assertTrue(AppUsageAwarenessHelper.isExemptSystemPackage("com.android.dialer"))
        assertTrue(AppUsageAwarenessHelper.isExemptSystemPackage("com.android.settings"))
        assertTrue(AppUsageAwarenessHelper.isExemptSystemPackage("com.google.android.deskclock"))
    }
}
