package com.example

import com.example.domain.DailyFocusGoalProgress
import com.example.domain.FocusTimerEngine
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.domain.IntentRealitySummary
import com.example.domain.PurposeMatchingEngine
import com.example.domain.TemporaryAccessPolicy
import com.example.model.BreakPlanConfig
import com.example.model.BreakPlanMode
import com.example.model.InsightsRangeFilter
import com.example.model.PurposeSessionRecord
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.system.BrainBufferHaptics
import com.example.system.DistractionAlertCoordinator
import com.example.system.HapticEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM unit verification suite for BRAIN BUFFER Purpose-Aware Focus System business logic.
 * Executes deterministically without network or emulator dependencies.
 */
class PurposeAwareDomainLogicTest {

    @Before
    fun setup() {
        DistractionAlertCoordinator.resetAllForTest()
    }

    @After
    fun tearDown() {
        DistractionAlertCoordinator.resetAllForTest()
    }

    @Test
    fun `1 starting a purpose-based session validates required fields and starts accurately`() {
        val now = 1_700_000_000_000L
        val mono = 50_000L

        assertNull(
            FocusTimerEngine.startPurposeSession(
                purposeCategory = SessionPurposeCategory.STUDY,
                sessionName = "   ",
                durationMinutes = 30,
                selectedApps = emptyList(),
                nowEpochMs = now,
                nowElapsedRealtimeMs = mono
            )
        )
        assertNull(
            FocusTimerEngine.startPurposeSession(
                purposeCategory = SessionPurposeCategory.WORK,
                sessionName = "Prepare deck",
                durationMinutes = 0,
                selectedApps = emptyList(),
                nowEpochMs = now,
                nowElapsedRealtimeMs = mono
            )
        )

        val started = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Study Physics",
            durationMinutes = 45,
            selectedApps = listOf(SelectedSessionApp("com.google.android.apps.docs", "Google Docs")),
            breakPlan = BreakPlanConfig(mode = BreakPlanMode.SINGLE, breakDurationMinutes = 5, intervalMinutes = 25),
            nowEpochMs = now,
            nowElapsedRealtimeMs = mono
        )

        assertNotNull(started)
        started!!
        assertTrue(started.hasStarted)
        assertEquals(SessionPurposeCategory.STUDY, started.purposeCategory)
        assertEquals("Study Physics", started.sessionGoal)
        assertEquals(45 * 60, started.totalSeconds)
        assertEquals(BreakPlanMode.SINGLE, started.breakPlanMode)
        assertEquals(5, started.breakDurationMinutes)
        assertEquals(now, started.runningSinceEpochMs)
    }

    @Test
    fun `2 selected apps are persisted on active snapshot without duplicates`() {
        val started = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Quarterly report",
            durationMinutes = 30,
            selectedApps = listOf(
                SelectedSessionApp("com.slack", "Slack"),
                SelectedSessionApp("com.notion.id", "Notion"),
                SelectedSessionApp("com.slack", "Slack")
            ),
            nowEpochMs = 100_000L,
            nowElapsedRealtimeMs = 10_000L
        )!!

        assertEquals(setOf("com.slack", "com.notion.id"), started.allowedPackages)
        assertTrue(started.allowedAppLabels.containsAll(listOf("Slack", "Notion")))
    }

    @Test
    fun `3 approved apps matching declared purpose return ApprovedForPurpose`() {
        val now = 200_000L
        val session = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Chemistry notes",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.google.android.apps.docs", "Docs")),
            nowEpochMs = now,
            nowElapsedRealtimeMs = 1_000L
        )!!

        val decision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.google.android.apps.docs",
            observedAppLabel = "Docs",
            snapshot = session,
            activeTempAccess = null,
            nowEpochMs = now + 10_000L,
            ownPackageName = "com.aistudio.brainbuffer.vkrqmx"
        )

        assertTrue(decision is PurposeMatchingEngine.MatchDecision.ApprovedForPurpose)
        val approved = decision as PurposeMatchingEngine.MatchDecision.ApprovedForPurpose
        assertEquals(SessionPurposeCategory.STUDY, approved.declaredPurpose)
        assertEquals("com.google.android.apps.docs", approved.packageName)
    }

    @Test
    fun `4 unapproved app returns calm PotentialDistraction while essential system apps stay exempt`() {
        val now = 300_000L
        val session = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Physics Revision",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.google.android.apps.docs", "Docs")),
            nowEpochMs = now,
            nowElapsedRealtimeMs = 1_000L
        )!!

        val distraction = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = session,
            activeTempAccess = null,
            nowEpochMs = now + 20_000L,
            ownPackageName = "com.aistudio.brainbuffer.vkrqmx",
            gracePeriodSeconds = 15,
            cooldownSeconds = 90
        )
        assertTrue(distraction is PurposeMatchingEngine.MatchDecision.PotentialDistraction)
        val pd = distraction as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertTrue(pd.message.contains("Study"))
        assertTrue(pd.message.contains("Instagram"))
        assertTrue(pd.shouldEmitAlert)

        listOf(
            "com.android.dialer",
            "com.android.settings",
            "com.android.systemui",
            "com.aistudio.brainbuffer.vkrqmx"
        ).forEach { exemptPkg ->
            val exemptDecision = PurposeMatchingEngine.evaluateForegroundApp(
                observedPackage = exemptPkg,
                observedAppLabel = exemptPkg,
                snapshot = session,
                activeTempAccess = null,
                nowEpochMs = now + 25_000L,
                ownPackageName = "com.aistudio.brainbuffer.vkrqmx"
            )
            assertTrue("Expected $exemptPkg to be exempt", exemptDecision is PurposeMatchingEngine.MatchDecision.ExemptSystemApp)
        }
    }

    @Test
    fun `5 mid-session purpose switch updates purpose and allowed apps while preserving elapsed focus`() {
        val startEpoch = 1_000_000L
        val startMono = 10_000L
        val session = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Read textbook",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.adobe.reader", "Acrobat")),
            nowEpochMs = startEpoch,
            nowElapsedRealtimeMs = startMono
        )!!

        val afterTenMin = FocusTimerEngine.pause(session, startEpoch + 600_000L, startMono + 600_000L)
        val switched = PurposeMatchingEngine.switchPurpose(
            current = afterTenMin,
            newPurpose = SessionPurposeCategory.COMMUNICATION,
            newSessionName = "Urgent team messages",
            newAllowedPackages = setOf("com.whatsapp"),
            newAllowedLabels = listOf("WhatsApp")
        )
        val resumed = FocusTimerEngine.startOrResume(switched, startEpoch + 605_000L, startMono + 605_000L)

        assertEquals(SessionPurposeCategory.COMMUNICATION, resumed.purposeCategory)
        assertEquals("Urgent team messages", resumed.sessionGoal)
        assertEquals(setOf("com.whatsapp"), resumed.allowedPackages)
        assertEquals(600_000L, resumed.accumulatedFocusedMs)

        val decision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.whatsapp",
            observedAppLabel = "WhatsApp",
            snapshot = resumed,
            activeTempAccess = null,
            nowEpochMs = startEpoch + 610_000L
        )
        assertTrue(decision is PurposeMatchingEngine.MatchDecision.ApprovedForPurpose)
    }

    @Test
    fun `6 temporary app access enforces single active allowance and pauses parent focus timer`() {
        val t0 = 5_000_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Calculus",
            durationMinutes = 40,
            selectedApps = emptyList(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = 1_000L
        )!!

        val pausedForSwitch = FocusTimerEngine.pauseForTemporaryAccess(focus, t0 + 300_000L, 301_000L)
        assertNull(pausedForSwitch.runningSinceEpochMs)
        assertEquals(300_000L, pausedForSwitch.accumulatedFocusedMs)

        val firstRes = TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = pausedForSwitch.sessionId,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            purposeLabel = "Quick reply",
            durationMinutes = 5,
            nowEpochMs = t0 + 300_000L
        )
        assertTrue(firstRes is TemporaryAccessPolicy.StartResult.Started)
        val firstSession = (firstRes as TemporaryAccessPolicy.StartResult.Started).session
        assertNull(firstRes.replacedPrevious)

        val secondRes = TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = pausedForSwitch.sessionId,
            packageName = "com.google.android.gm",
            appLabel = "Gmail",
            purposeLabel = "Check OTP",
            durationMinutes = 3,
            nowEpochMs = t0 + 360_000L,
            existingActive = firstSession
        )
        assertTrue(secondRes is TemporaryAccessPolicy.StartResult.Started)
        val startedSecond = secondRes as TemporaryAccessPolicy.StartResult.Started
        assertNotNull(startedSecond.replacedPrevious)
        assertEquals(t0 + 360_000L, startedSecond.replacedPrevious!!.endedAtEpochMs)
        assertEquals("com.google.android.gm", startedSecond.session.packageName)

        val match = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.google.android.gm",
            observedAppLabel = "Gmail",
            snapshot = pausedForSwitch,
            activeTempAccess = startedSecond.session,
            nowEpochMs = t0 + 400_000L
        )
        assertTrue(match is PurposeMatchingEngine.MatchDecision.TemporaryAccessActive)
    }

    @Test
    fun `7 temporary access expiry returns exact return-to-focus prompt`() {
        val t0 = 6_000_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Design spec",
            durationMinutes = 30,
            selectedApps = emptyList(),
            nowEpochMs = t0
        )!!
        val paused = FocusTimerEngine.pauseForTemporaryAccess(focus, t0 + 120_000L)
        val temp = (TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = paused.sessionId,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            purposeLabel = "Reply",
            durationMinutes = 5,
            nowEpochMs = t0 + 120_000L
        ) as TemporaryAccessPolicy.StartResult.Started).session

        val decision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.whatsapp",
            observedAppLabel = "WhatsApp",
            snapshot = paused,
            activeTempAccess = temp,
            nowEpochMs = temp.expiresAtEpochMs + 1_000L
        )

        assertTrue(decision is PurposeMatchingEngine.MatchDecision.TemporaryAccessExpired)
        val expired = decision as PurposeMatchingEngine.MatchDecision.TemporaryAccessExpired
        assertEquals("Time is up. Return to your focus session?", expired.promptTitle)
        assertTrue(expired.canExtend)
    }

    @Test
    fun `8 business logic strictly rejects temporary access extensions above 5 minutes or non-positive`() {
        val t0 = 7_000_000L
        val temp = (TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = "s-1",
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            purposeLabel = "Check message",
            durationMinutes = 5,
            nowEpochMs = t0
        ) as TemporaryAccessPolicy.StartResult.Started).session

        val sixMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 6, nowEpochMs = t0 + 300_000L)
        assertTrue(sixMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)
        assertEquals(5, (sixMin as TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit).maxAllowedMinutes)

        val fifteenMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 15, nowEpochMs = t0 + 300_000L)
        assertTrue(fifteenMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)

        val zeroMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 0, nowEpochMs = t0 + 300_000L)
        assertTrue(zeroMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)

        val fiveMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 5, nowEpochMs = t0 + 300_000L)
        assertTrue(fiveMin is TemporaryAccessPolicy.ExtensionResult.Granted)
        val granted = fiveMin as TemporaryAccessPolicy.ExtensionResult.Granted
        assertEquals(5, granted.grantedMinutes)
        assertEquals(5, granted.updated.extensionMinutesUsed)
        assertTrue(granted.updated.hasUsedExtension)
        assertFalse(granted.updated.canRequestExtension)
    }

    @Test
    fun `9 duplicate or repeat extension requests are strictly rejected with no silent renewal`() {
        val t0 = 8_000_000L
        val temp = (TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = "s-2",
            packageName = "com.slack",
            appLabel = "Slack",
            purposeLabel = "Team update",
            durationMinutes = 5,
            nowEpochMs = t0
        ) as TemporaryAccessPolicy.StartResult.Started).session

        val firstExt = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 3, nowEpochMs = t0 + 300_000L)
        assertTrue(firstExt is TemporaryAccessPolicy.ExtensionResult.Granted)
        val extended = (firstExt as TemporaryAccessPolicy.ExtensionResult.Granted).updated

        val secondExt = TemporaryAccessPolicy.requestExtension(extended, requestedMinutes = 1, nowEpochMs = extended.expiresAtEpochMs)
        assertTrue(secondExt is TemporaryAccessPolicy.ExtensionResult.RejectedAlreadyExtended)
        assertFalse(extended.canRequestExtension)

        val closed = TemporaryAccessPolicy.closeTemporaryAccess(temp, nowEpochMs = t0 + 60_000L)
        val closedExt = TemporaryAccessPolicy.requestExtension(closed, requestedMinutes = 5, nowEpochMs = t0 + 70_000L)
        assertTrue(closedExt is TemporaryAccessPolicy.ExtensionResult.RejectedSessionClosed)
    }

    @Test
    fun `10 break pauses parent focus, tracks break duration separately, and resumes parent cleanly`() {
        val t0 = 9_000_000L
        val m0 = 1_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Organic Chemistry",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.google.android.apps.docs", "Docs")),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = m0
        )!!

        val onBreak = FocusTimerEngine.startBreakPreservingParent(
            current = focus,
            breakMinutes = 5,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = m0 + 600_000L
        )
        assertTrue(onBreak.isOnBreak)
        assertNotNull(onBreak.pausedParentSession)
        assertEquals(600_000L, onBreak.pausedParentSession!!.accumulatedFocusedMs)

        val duringBreakDecision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = onBreak,
            activeTempAccess = null,
            nowEpochMs = t0 + 720_000L
        )
        assertTrue(duringBreakDecision is PurposeMatchingEngine.MatchDecision.Inactive)

        val resumed = FocusTimerEngine.returnToPausedStudySession(
            current = onBreak,
            nowEpochMs = t0 + 780_000L,
            nowElapsedRealtimeMs = m0 + 780_000L
        )!!
        assertFalse(resumed.isOnBreak)
        assertEquals("Organic Chemistry", resumed.sessionGoal)
        assertEquals(600_000L, resumed.accumulatedFocusedMs)
        assertEquals(180, resumed.accumulatedBreakSeconds)
        assertEquals(1, resumed.breaksTakenCount)
        assertEquals(1200, FocusTimerEngine.remainingSeconds(resumed, t0 + 780_000L, m0 + 780_000L))
    }

    @Test
    fun `11 focus timer only grants natural completion at 00_00 and deduplicates repeat calls`() {
        val t0 = 10_000_000L
        val m0 = 5_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Write tests",
            durationMinutes = 25,
            selectedApps = emptyList(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = m0
        )!!

        val premature = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = m0 + 600_000L
        )
        assertTrue(premature is FocusTimerEngine.SessionCompletionDecision.RejectedBeforeExpiry)

        val natural = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 1_500_000L,
            nowElapsedRealtimeMs = m0 + 1_500_000L
        )
        assertTrue(natural is FocusTimerEngine.SessionCompletionDecision.NaturalCompletion)
        val done = natural as FocusTimerEngine.SessionCompletionDecision.NaturalCompletion
        assertEquals(1500, done.actualSeconds)
        assertEquals(1500, done.plannedSeconds)

        val duplicate = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 1_501_000L,
            nowElapsedRealtimeMs = m0 + 1_501_000L,
            completedSessionIds = setOf(done.sessionId)
        )
        assertTrue(duplicate is FocusTimerEngine.SessionCompletionDecision.RejectedDuplicate)
    }

    @Test
    fun `12 early exit logs only actual focused elapsed seconds as ENDED_EARLY and never full planned time`() {
        val t0 = 11_000_000L
        val m0 = 2_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Biology Chapter 4",
            durationMinutes = 40,
            selectedApps = emptyList(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = m0
        )!!

        val paused = FocusTimerEngine.pause(focus, t0 + 720_000L, m0 + 720_000L)
        val early = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = paused,
            nowEpochMs = t0 + 1_320_000L,
            nowElapsedRealtimeMs = m0 + 1_320_000L
        )

        assertTrue(early.shouldPersist)
        assertEquals(PurposeSessionRecord.STATUS_ENDED_EARLY, early.status)
        assertEquals(720, early.actualSeconds)
        assertEquals(2400, early.plannedSeconds)
    }

    @Test
    fun `13 process recreation restoration restores running, paused, and expired sessions without duplicate completion`() {
        val t0 = 12_000_000L
        val focus = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Architecture Review",
            durationMinutes = 20,
            selectedApps = listOf(SelectedSessionApp("com.slack", "Slack")),
            nowEpochMs = t0
        )!!

        // Running restoration after 5 minutes
        val runningRes = FocusTimerEngine.restoreAfterProcessRecreation(focus, nowEpochMs = t0 + 300_000L)
        assertTrue(runningRes is FocusTimerEngine.ProcessRestorationResult.Running)
        assertEquals(900, (runningRes as FocusTimerEngine.ProcessRestorationResult.Running).remainingSeconds)

        // Pending natural completion if expired while process was dead
        val expiredRes = FocusTimerEngine.restoreAfterProcessRecreation(focus, nowEpochMs = t0 + 1_300_000L)
        assertTrue(expiredRes is FocusTimerEngine.ProcessRestorationResult.NaturalCompletionPending)

        // Already completed session ID is never re-completed
        val alreadyDone = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = focus,
            nowEpochMs = t0 + 1_300_000L,
            completedSessionIds = setOf(focus.sessionId)
        )
        assertTrue(alreadyDone is FocusTimerEngine.ProcessRestorationResult.AlreadyCompleted)
    }

    @Test
    fun `15 focus, break, temporary access, and distraction durations remain strictly separated`() {
        val t0 = 13_000_000L
        val m0 = 1_000L
        var snap = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Code Architecture",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.termux", "Termux")),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = m0
        )!!

        snap = FocusTimerEngine.pauseForTemporaryAccess(snap, t0 + 600_000L, m0 + 600_000L)
        snap = FocusTimerEngine.resumeFromTemporaryAccess(
            current = snap,
            tempAccessActualSeconds = 240,
            nowEpochMs = t0 + 840_000L,
            nowElapsedRealtimeMs = m0 + 840_000L
        )
        snap = FocusTimerEngine.recordDistractionIntervention(snap, distractionSeconds = 60)
        snap = FocusTimerEngine.startBreakPreservingParent(snap, breakMinutes = 5, nowEpochMs = t0 + 840_000L, nowElapsedRealtimeMs = m0 + 840_000L)
        snap = FocusTimerEngine.returnToPausedStudySession(snap, nowEpochMs = t0 + 1_020_000L, nowElapsedRealtimeMs = m0 + 1_020_000L)!!

        assertEquals(600, FocusTimerEngine.actualFocusedSeconds(snap, t0 + 1_020_000L, m0 + 1_020_000L))
        assertEquals(180, snap.accumulatedBreakSeconds)
        assertEquals(240, snap.accumulatedTempAccessSeconds)
        assertEquals(60, snap.accumulatedDistractionSeconds)
        assertEquals(1, snap.interventionCount)
    }

    @Test
    fun `16 intent vs reality alignment uses one consistent formula and never fabricates empty metrics`() {
        val today = LocalDate.of(2026, 11, 22).toEpochDay()

        val emptySummary = IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = emptyList(),
            legacyFocusSessions = emptyList(),
            interventionEvents = emptyList(),
            range = InsightsRangeFilter.TODAY,
            todayEpochDay = today
        )
        assertNull(emptySummary.alignmentPercent)
        assertFalse(emptySummary.hasAnyRecordedData)
        assertEquals(0, emptySummary.approvedFocusMinutes)
        assertEquals(0, emptySummary.potentialDistractionMinutes)

        assertEquals(80, IntentRealityAnalyticsEngine.calculateAlignmentPercent(2400, 600))
        assertNull(IntentRealityAnalyticsEngine.calculateAlignmentPercent(0, 0))
        assertTrue(IntentRealitySummary.METHODOLOGY_NOTE.contains("never inspects in-app content"))

        val session = PurposeSessionRecord(
            sessionKey = "s-align-1",
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Algorithms",
            plannedSeconds = 3000,
            actualFocusedSeconds = 2400,
            breakSeconds = 300,
            temporaryAccessSeconds = 300,
            distractionSeconds = 600,
            interventionCount = 2,
            status = PurposeSessionRecord.STATUS_COMPLETED,
            startedAtMs = 1_000L,
            endedAtMs = 3_600_000L,
            dateEpochDay = today
        )
        val summary = IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = listOf(session),
            range = InsightsRangeFilter.TODAY,
            todayEpochDay = today
        )

        assertTrue(summary.hasAnyRecordedData)
        assertEquals(80, summary.alignmentPercent)
        assertEquals(50, summary.intendedPlannedMinutes)
        assertEquals(40, summary.approvedFocusMinutes)
        assertEquals(10, summary.potentialDistractionMinutes)
        assertEquals(5, summary.breakMinutes)
        assertEquals(5, summary.temporaryAccessMinutes)
        assertEquals(1, summary.purposeBreakdown.size)
        assertEquals(80, summary.purposeBreakdown.first().alignmentPercent)
    }

    @Test
    fun `17 haptic waveforms are valid, amplitude-bounded, and distinct across key events`() {
        HapticEvent.entries.forEach { event ->
            val spec = BrainBufferHaptics.waveformFor(event)
            assertEquals("Event mismatch for $event", event, spec.event)
            assertTrue("Timings must be non-empty for $event", spec.timingsMs.isNotEmpty())
            assertEquals(
                "Timings and amplitudes lengths must match for $event",
                spec.timingsMs.size,
                spec.amplitudes.size
            )
            assertTrue(
                "All timings must be non-negative for $event",
                spec.timingsMs.all { it >= 0L }
            )
            assertTrue(
                "Total waveform duration must be positive for $event",
                spec.timingsMs.sum() > 0L
            )
            assertTrue(
                "Amplitudes must stay within Android 0..255 bounds for $event",
                spec.amplitudes.all { it in 0..255 }
            )
            assertTrue(
                "At least one non-zero amplitude pulse required for $event",
                spec.amplitudes.any { it > 0 }
            )
        }

        val startSpec = BrainBufferHaptics.waveformFor(HapticEvent.SESSION_START)
        val completeSpec = BrainBufferHaptics.waveformFor(HapticEvent.SESSION_COMPLETE)
        val distractionSpec = BrainBufferHaptics.waveformFor(HapticEvent.DISTRACTION_ALERT)

        assertFalse(startSpec.timingsMs.contentEquals(completeSpec.timingsMs))
        assertFalse(startSpec.timingsMs.contentEquals(distractionSpec.timingsMs))
        assertFalse(completeSpec.timingsMs.contentEquals(distractionSpec.timingsMs))
    }

    @Test
    fun `18 daily focus goal progress tracks real approved focus minutes, clamps targets, and formats durations`() {
        // Zero progress
        val zero = IntentRealityAnalyticsEngine.computeDailyGoalProgress(0, 60)
        assertEquals(0, zero.focusedMinutes)
        assertEquals(60, zero.targetMinutes)
        assertEquals(60, zero.remainingMinutes)
        assertEquals(0, zero.bonusMinutes)
        assertEquals(0f, zero.progressFraction, 0.0001f)
        assertEquals(0, zero.percentComplete)
        assertFalse(zero.isGoalReached)

        // Partial progress
        val partial = IntentRealityAnalyticsEngine.computeDailyGoalProgress(35, 60)
        assertEquals(35, partial.focusedMinutes)
        assertEquals(25, partial.remainingMinutes)
        assertEquals(0, partial.bonusMinutes)
        assertEquals(35f / 60f, partial.progressFraction, 0.001f)
        assertEquals(58, partial.percentComplete)
        assertFalse(partial.isGoalReached)
        assertEquals("35m", partial.formattedFocused)
        assertEquals("1h", partial.formattedTarget)
        assertEquals("25m", partial.formattedRemaining)

        // Exact completion
        val exact = IntentRealityAnalyticsEngine.computeDailyGoalProgress(90, 90)
        assertEquals(0, exact.remainingMinutes)
        assertEquals(0, exact.bonusMinutes)
        assertEquals(1f, exact.progressFraction, 0.0001f)
        assertEquals(100, exact.percentComplete)
        assertTrue(exact.isGoalReached)
        assertEquals("1h 30m", exact.formattedTarget)

        // Exceeded goal with bonus minutes (progress bar clamps to 1f, percent & bonus reflect real effort)
        val exceeded = IntentRealityAnalyticsEngine.computeDailyGoalProgress(105, 90)
        assertEquals(0, exceeded.remainingMinutes)
        assertEquals(15, exceeded.bonusMinutes)
        assertEquals(1f, exceeded.progressFraction, 0.0001f)
        assertEquals(117, exceeded.percentComplete)
        assertTrue(exceeded.isGoalReached)
        assertEquals("15m", exceeded.formattedBonus)

        // Clamping lower and upper bounds + negative focus safety
        val clampedLow = IntentRealityAnalyticsEngine.computeDailyGoalProgress(-10, 5)
        assertEquals(0, clampedLow.focusedMinutes)
        assertEquals(DailyFocusGoalProgress.MIN_GOAL_MINUTES, clampedLow.targetMinutes)

        val clampedHigh = IntentRealityAnalyticsEngine.computeDailyGoalProgress(120, 999)
        assertEquals(DailyFocusGoalProgress.MAX_GOAL_MINUTES, clampedHigh.targetMinutes)
        assertEquals("8h", clampedHigh.formattedTarget)
    }
}
