package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AmbientSoundscapeController
import com.example.audio.LocalAmbientSoundscapeController
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.InterventionEventEntity
import com.example.data.local.PurposeSessionEntity
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.domain.IntentRealitySummary
import com.example.domain.PurposeMatchingEngine
import com.example.domain.TemporaryAccessPolicy
import com.example.model.BreakPlanConfig
import com.example.model.BreakPlanMode
import com.example.model.InsightsRangeFilter
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PersistedTimerSnapshot
import com.example.model.PurposeSessionRecord
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.model.ThemeMode
import com.example.system.DistractionAlertCoordinator
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.InsightsPrimaryScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import com.example.viewmodel.FocusTimerUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * End-to-end JVM + Robolectric verification suite for the BRAIN BUFFER Purpose-Aware Focus System.
 *
 * Covers all 16 core functional scenarios plus Light & AMOLED Dark Compose UI verification:
 *  1. Starting a purpose-based session (validation + configuration)
 *  2. Selected-app configuration persistence
 *  3. Approved app detection (package-level matching)
 *  4. Unapproved app detection + essential system app exemptions
 *  5. Mid-session purpose switching preserving elapsed focus accounting
 *  6. Temporary app access ("App Switch") single-allowance policy + pausing parent focus
 *  7. Temporary access expiry prompt ("Time is up. Return to your focus session?")
 *  8. Strict maximum 5-minute extension enforcement in business logic
 *  9. Duplicate / silent extension prevention
 * 10. Break pause/resume preserving parent session and separating break accounting
 * 11. Natural focus timer completion & deduplication
 * 12. Early exit recording only actual focused elapsed seconds (ENDED_EARLY)
 * 13. Process recreation restoration for active session & temporary access via DataStore
 * 14. Durable Room v6 persistence for PurposeSessionEntity & InterventionEventEntity
 * 15. Strict separation of focus, break, temporary access, and distraction accounting
 * 16. Consistent Intent-vs-Reality alignment formula & zero-demo-data empty state
 * 17. Light & AMOLED Dark Compose UI verification (Focus setup, 3-item overflow menu, Insights empty state)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PurposeAwareFocusSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: BrainBufferDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BrainBufferDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DistractionAlertCoordinator.resetAllForTest()
    }

    @After
    fun tearDown() {
        db.close()
        DistractionAlertCoordinator.resetAllForTest()
    }

    // 1. Starting a purpose-based session
    @Test
    fun `1 starting a purpose-based session validates required fields and starts accurately`() {
        val now = 1_700_000_000_000L
        val mono = 50_000L

        // Blank session name or invalid duration must be rejected
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

    // 2. Selected-app configuration
    @Test
    fun `2 selected apps are persisted on the active timer snapshot without duplicates`() {
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

    // 3. Approved app detection
    @Test
    fun `3 approved apps matching declared purpose return ApprovedForPurpose without distraction alerts`() {
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

    // 4. Unapproved app detection + exempt system apps
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

        // Unapproved app triggers PotentialDistraction with calm purpose-aware phrasing
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

        // Essential system apps (dialer, settings, systemui, BRAIN BUFFER) are ALWAYS exempt
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

    // 5. Purpose switching
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

        // Focus for 10 minutes then switch purpose to Communication (allowing WhatsApp)
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

        // WhatsApp is now approved under the new declared purpose
        val decision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.whatsapp",
            observedAppLabel = "WhatsApp",
            snapshot = resumed,
            activeTempAccess = null,
            nowEpochMs = startEpoch + 610_000L
        )
        assertTrue(decision is PurposeMatchingEngine.MatchDecision.ApprovedForPurpose)
    }

    // 6. Temporary app access (single active allowance + pauses parent focus)
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

        // Pause parent focus at 5 minutes elapsed
        val pausedForSwitch = FocusTimerEngine.pauseForTemporaryAccess(focus, t0 + 300_000L, 301_000L)
        assertNull(pausedForSwitch.runningSinceEpochMs)
        assertEquals(300_000L, pausedForSwitch.accumulatedFocusedMs)

        // Start first temporary access for WhatsApp (5 min)
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

        // Starting a second temporary access replaces/closes the first (single active allowance invariant)
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

        // Active temporary access allows Gmail without triggering distraction alert
        val match = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.google.android.gm",
            observedAppLabel = "Gmail",
            snapshot = pausedForSwitch,
            activeTempAccess = startedSecond.session,
            nowEpochMs = t0 + 400_000L
        )
        assertTrue(match is PurposeMatchingEngine.MatchDecision.TemporaryAccessActive)
    }

    // 7. Temporary access expiry
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

        // At 5 minutes after start, temp access expires
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

    // 8. Maximum 5-minute extension enforcement
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

        // > 5 minutes MUST be rejected by domain policy
        val sixMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 6, nowEpochMs = t0 + 300_000L)
        assertTrue(sixMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)
        assertEquals(5, (sixMin as TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit).maxAllowedMinutes)

        val fifteenMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 15, nowEpochMs = t0 + 300_000L)
        assertTrue(fifteenMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)

        val zeroMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 0, nowEpochMs = t0 + 300_000L)
        assertTrue(zeroMin is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit)

        // Exactly 5 minutes succeeds once
        val fiveMin = TemporaryAccessPolicy.requestExtension(temp, requestedMinutes = 5, nowEpochMs = t0 + 300_000L)
        assertTrue(fiveMin is TemporaryAccessPolicy.ExtensionResult.Granted)
        val granted = fiveMin as TemporaryAccessPolicy.ExtensionResult.Granted
        assertEquals(5, granted.grantedMinutes)
        assertEquals(5, granted.updated.extensionMinutesUsed)
        assertTrue(granted.updated.hasUsedExtension)
        assertFalse(granted.updated.canRequestExtension)
    }

    // 9. Duplicate extension prevention
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

        // Second extension attempt (even for 1 minute!) MUST be rejected
        val secondExt = TemporaryAccessPolicy.requestExtension(extended, requestedMinutes = 1, nowEpochMs = extended.expiresAtEpochMs)
        assertTrue(secondExt is TemporaryAccessPolicy.ExtensionResult.RejectedAlreadyExtended)
        assertFalse(extended.canRequestExtension)

        // Closed session cannot be extended either
        val closed = TemporaryAccessPolicy.closeTemporaryAccess(temp, nowEpochMs = t0 + 60_000L)
        val closedExt = TemporaryAccessPolicy.requestExtension(closed, requestedMinutes = 5, nowEpochMs = t0 + 70_000L)
        assertTrue(closedExt is TemporaryAccessPolicy.ExtensionResult.RejectedSessionClosed)
    }

    // 10. Break pause/resume
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

        // Focus for 10 minutes, then take a 5-minute break
        val onBreak = FocusTimerEngine.startBreakPreservingParent(
            current = focus,
            breakMinutes = 5,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = m0 + 600_000L
        )
        assertTrue(onBreak.isOnBreak)
        assertNotNull(onBreak.pausedParentSession)
        assertEquals(600_000L, onBreak.pausedParentSession!!.accumulatedFocusedMs)

        // During break, unapproved apps do NOT trigger distraction alerts
        val duringBreakDecision = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = onBreak,
            activeTempAccess = null,
            nowEpochMs = t0 + 720_000L
        )
        assertTrue(duringBreakDecision is PurposeMatchingEngine.MatchDecision.Inactive)

        // Return early after 3 minutes of break
        val resumed = FocusTimerEngine.returnToPausedStudySession(
            current = onBreak,
            nowEpochMs = t0 + 780_000L,
            nowElapsedRealtimeMs = m0 + 780_000L
        )
        assertNotNull(resumed)
        resumed!!
        assertFalse(resumed.isOnBreak)
        assertEquals("Organic Chemistry", resumed.sessionGoal)
        assertEquals(600_000L, resumed.accumulatedFocusedMs) // Break time NEVER added to focus
        assertEquals(180, resumed.accumulatedBreakSeconds)   // Break time recorded separately
        assertEquals(1, resumed.breaksTakenCount)
        assertEquals(1200, FocusTimerEngine.remainingSeconds(resumed, t0 + 780_000L, m0 + 780_000L))
    }

    // 11. Focus timer completion
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

        // Premature completion at 10 minutes MUST be rejected
        val premature = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = m0 + 600_000L
        )
        assertTrue(premature is FocusTimerEngine.SessionCompletionDecision.RejectedBeforeExpiry)

        // Natural completion at 25:00 succeeds
        val natural = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 1_500_000L,
            nowElapsedRealtimeMs = m0 + 1_500_000L
        )
        assertTrue(natural is FocusTimerEngine.SessionCompletionDecision.NaturalCompletion)
        val done = natural as FocusTimerEngine.SessionCompletionDecision.NaturalCompletion
        assertEquals(1500, done.actualSeconds)
        assertEquals(1500, done.plannedSeconds)

        // Duplicate completion with same sessionId is rejected
        val duplicate = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = focus,
            nowEpochMs = t0 + 1_501_000L,
            nowElapsedRealtimeMs = m0 + 1_501_000L,
            completedSessionIds = setOf(done.sessionId)
        )
        assertTrue(duplicate is FocusTimerEngine.SessionCompletionDecision.RejectedDuplicate)
    }

    // 12. Early exit
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

        // Run 12 minutes, pause 10 minutes, then Finish Early
        val paused = FocusTimerEngine.pause(focus, t0 + 720_000L, m0 + 720_000L)
        val early = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = paused,
            nowEpochMs = t0 + 1_320_000L,
            nowElapsedRealtimeMs = m0 + 1_320_000L
        )

        assertTrue(early.shouldPersist)
        assertEquals(PurposeSessionRecord.STATUS_ENDED_EARLY, early.status)
        assertEquals(720, early.actualSeconds)      // Exactly 12 minutes, NOT 22m and NOT 40m
        assertEquals(2400, early.plannedSeconds)
    }

    // 13. Process recreation restoration
    @Test
    fun `13 active purpose session and temporary access survive DataStore persistence and process recreation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = UserPreferencesStore(context)
        val t0 = 12_000_000L

        val original = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Sprint Planning",
            durationMinutes = 35,
            selectedApps = listOf(SelectedSessionApp("com.slack", "Slack")),
            breakPlan = BreakPlanConfig(BreakPlanMode.REPEATING, 5, 25),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = 1_000L
        )!!

        val temp = (TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = original.sessionId,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            purposeLabel = "Quick call check",
            durationMinutes = 5,
            nowEpochMs = t0 + 60_000L
        ) as TemporaryAccessPolicy.StartResult.Started).session

        val pausedSnapshot = FocusTimerEngine.pauseForTemporaryAccess(original, t0 + 60_000L, 61_000L)
        store.saveTimerSnapshot(pausedSnapshot)
        store.saveActiveTempAccess(temp)

        // Simulate fresh read after process recreation
        val restoredSnap = store.getTimerSnapshotOnce()
        val restoredTemp = store.getActiveTempAccessOnce()

        assertEquals(pausedSnapshot.sessionId, restoredSnap.sessionId)
        assertEquals(SessionPurposeCategory.WORK, restoredSnap.purposeCategory)
        assertEquals("Sprint Planning", restoredSnap.sessionGoal)
        assertEquals(setOf("com.slack"), restoredSnap.allowedPackages)
        assertEquals(BreakPlanMode.REPEATING, restoredSnap.breakPlanMode)

        val restoration = FocusTimerEngine.restoreAfterProcessRecreation(
            snapshot = restoredSnap,
            nowEpochMs = t0 + 120_000L
        )
        assertTrue(restoration is FocusTimerEngine.ProcessRestorationResult.Paused)

        assertNotNull(restoredTemp)
        assertEquals(temp.id, restoredTemp!!.id)
        assertEquals("com.whatsapp", restoredTemp.packageName)
        assertEquals(5, restoredTemp.originalAllowanceMinutes)
        assertFalse(restoredTemp.hasUsedExtension)

        // Cleanup DataStore state
        store.saveActiveTempAccess(null)
        store.saveTimerSnapshot(PersistedTimerSnapshot())
    }

    // 14. Session persistence (Room v6)
    @Test
    fun `14 Room v6 persists PurposeSessionRecord and InterventionEventRecord accurately`() = runBlocking {
        val today = LocalDate.of(2026, 11, 22).toEpochDay()
        val entity = PurposeSessionEntity(
            sessionKey = "session-room-1",
            purposeCategory = SessionPurposeCategory.STUDY.name,
            sessionName = "Linear Algebra",
            allowedPackagesCsv = "com.google.android.apps.docs,com.adobe.reader",
            allowedAppLabelsCsv = "Docs,Acrobat",
            plannedSeconds = 2700,
            actualFocusedSeconds = 2400,
            breakSeconds = 300,
            temporaryAccessSeconds = 180,
            distractionSeconds = 120,
            interventionCount = 2,
            status = PurposeSessionRecord.STATUS_ENDED_EARLY,
            breakPlanMode = BreakPlanMode.SINGLE.name,
            linkedTaskId = null,
            startedAtMs = 1_000_000L,
            endedAtMs = 4_000_000L,
            dateEpochDay = today
        )

        db.purposeSessionDao().upsert(entity)
        val savedSessions = db.purposeSessionDao().getAllOnce()
        assertEquals(1, savedSessions.size)
        val restored = savedSessions.first()
        assertEquals("session-room-1", restored.sessionKey)
        assertEquals(SessionPurposeCategory.STUDY.name, restored.purposeCategory)
        assertEquals("com.google.android.apps.docs,com.adobe.reader", restored.allowedPackagesCsv)
        assertEquals(2400, restored.actualFocusedSeconds)
        assertEquals(300, restored.breakSeconds)
        assertEquals(180, restored.temporaryAccessSeconds)
        assertEquals(120, restored.distractionSeconds)
        assertEquals(PurposeSessionRecord.STATUS_ENDED_EARLY, restored.status)

        val event = InterventionEventEntity(
            sessionKey = "session-room-1",
            eventType = InterventionEventType.TEMP_ACCESS_EXTENDED.name,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            declaredPurpose = "Study",
            actionTaken = "Extended +5m (max)",
            plannedSeconds = 300,
            extensionSeconds = 300,
            actualSeconds = 480,
            timestampMs = 2_000_000L,
            dateEpochDay = today
        )
        db.interventionEventDao().insert(event)
        val savedEvents = db.interventionEventDao().getAllOnce()
        assertEquals(1, savedEvents.size)
        assertEquals(InterventionEventType.TEMP_ACCESS_EXTENDED.name, savedEvents.first().eventType)
        assertEquals(300, savedEvents.first().extensionSeconds)
    }

    // 15. Separate focus, break, and temporary access accounting
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

        // 10m focus -> pause for 4m temporary access -> resume
        snap = FocusTimerEngine.pauseForTemporaryAccess(snap, t0 + 600_000L, m0 + 600_000L)
        snap = FocusTimerEngine.resumeFromTemporaryAccess(
            current = snap,
            tempAccessActualSeconds = 240,
            nowEpochMs = t0 + 840_000L,
            nowElapsedRealtimeMs = m0 + 840_000L
        )
        // Record 60s potential distraction
        snap = FocusTimerEngine.recordDistractionIntervention(snap, distractionSeconds = 60)
        // Take a 5m break and return after 3m (180s)
        snap = FocusTimerEngine.startBreakPreservingParent(snap, breakMinutes = 5, nowEpochMs = t0 + 840_000L, nowElapsedRealtimeMs = m0 + 840_000L)
        snap = FocusTimerEngine.returnToPausedStudySession(snap, nowEpochMs = t0 + 1_020_000L, nowElapsedRealtimeMs = m0 + 1_020_000L)!!

        // Verify all 4 buckets are completely independent
        assertEquals(600, FocusTimerEngine.actualFocusedSeconds(snap, t0 + 1_020_000L, m0 + 1_020_000L))
        assertEquals(180, snap.accumulatedBreakSeconds)
        assertEquals(240, snap.accumulatedTempAccessSeconds)
        assertEquals(60, snap.accumulatedDistractionSeconds)
        assertEquals(1, snap.interventionCount)
    }

    // 16. Intent-versus-reality alignment calculation + empty state honesty
    @Test
    fun `16 intent vs reality alignment uses one consistent formula and never fabricates empty metrics`() {
        val today = LocalDate.of(2026, 11, 22).toEpochDay()

        // Empty state -> null alignment, zeroed totals, hasAnyRecordedData == false
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

        // Formula: round(approvedFocus / (approvedFocus + distraction) * 100)
        // 2400s focus, 600s distraction -> 2400 / 3000 = 80%
        // Break (300s) and Temp Access (300s) MUST NOT distort alignment ratio!
        assertEquals(80, IntentRealityAnalyticsEngine.calculateAlignmentPercent(2400, 600))
        assertNull(IntentRealityAnalyticsEngine.calculateAlignmentPercent(0, 0))

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

    // 17. Light & AMOLED Dark Compose UI verification
    @Test
    fun `17 FocusScreen setup, active 3-action overflow menu, and Insights empty state render cleanly in Light and AMOLED Dark`() {
        var themeMode by mutableStateOf(ThemeMode.DARK)
        var showActive by mutableStateOf(false)
        val soundController = AmbientSoundscapeController(enableHardwareAudio = false)

        val activeSnapshot = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Study Physics",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.google.android.apps.docs", "Docs")),
            nowEpochMs = System.currentTimeMillis()
        )!!

        composeRule.setContent {
            BrainBufferTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalAmbientSoundscapeController provides soundController) {
                    ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                        if (!showActive) {
                            FocusScreen(uiState = BrainBufferUiState())
                        } else {
                            FocusScreen(
                                uiState = BrainBufferUiState(
                                    timer = FocusTimerUiState(
                                        snapshot = activeSnapshot,
                                        remainingSeconds = 1800,
                                        progress = 0f
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        // Setup state in AMOLED Dark
        composeRule.onNodeWithTag("focus_setup_headline").assertIsDisplayed()
        composeRule.onNodeWithTag("purpose_chip_study").assertIsDisplayed()
        composeRule.onNodeWithTag("start_focus_button").assertIsDisplayed()

        // Active state in Light theme with exact 3 overflow menu actions: App Switch, Break, Exit
        themeMode = ThemeMode.LIGHT
        showActive = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("active_focus_session_title").assertIsDisplayed()
        composeRule.onNodeWithTag("active_focus_countdown").assertIsDisplayed()
        composeRule.onNodeWithTag("active_focus_overflow_menu").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overflow_action_app_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_action_break").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_action_exit").assertIsDisplayed()
    }

    @Test
    fun `18 InsightsPrimaryScreen shows honest empty state and methodology disclaimer without fake data`() {
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                    InsightsPrimaryScreen(uiState = BrainBufferUiState())
                }
            }
        }

        composeRule.onNodeWithTag("insights_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_alignment_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_alignment_percent_value").assertTextContains("—")
        composeRule.onNodeWithTag("insights_empty_state_text").assertIsDisplayed()
        assertTrue(IntentRealitySummary.METHODOLOGY_NOTE.contains("never inspects in-app content"))
    }
}
