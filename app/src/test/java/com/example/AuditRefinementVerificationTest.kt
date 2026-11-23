package com.example

import com.example.cloud.SupabaseConfig
import com.example.data.local.BrainBufferDatabase
import com.example.domain.FocusTimerEngine
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.domain.PurposeMatchingEngine
import com.example.domain.TemporaryAccessPolicy
import com.example.model.BreakPlanConfig
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.InsightsRangeFilter
import com.example.model.PurposeSessionRecord
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.model.SupabaseAuthSession
import com.example.system.DistractionAlertCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM verification suite covering the final audit fixes:
 * - Grace period continuous dwell enforcement & reset on allowed/exempt return
 * - Early exit unwinding from Break and Temporary Access without losing parent focus time
 * - Room migration continuity (v1 -> v6)
 * - Supabase auth session semantics & URL helpers (with zero Google Sign-In dependency)
 * - Intent vs. Reality multi-range filtering, empty states, and legacy deduplication
 */
class AuditRefinementVerificationTest {

    @Before
    fun setUp() {
        DistractionAlertCoordinator.resetAllForTest()
    }

    @Test
    fun gracePeriod_blocksAlertUntilContinuousDwellReachesConfiguredSeconds() {
        val t0 = 1_700_000_000_000L
        val started = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Physics Chapter 4",
            durationMinutes = 30,
            selectedApps = listOf(SelectedSessionApp("com.example.reader", "Reader")),
            breakPlan = BreakPlanConfig(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = 1_000L
        )!!

        // Poll at 0s -> first observation, grace (15s) not yet met
        val d0 = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 1_000L,
            gracePeriodSeconds = 15,
            cooldownSeconds = 60,
            enforceGracePeriod = true
        ) as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertFalse("Alert must NOT fire on first sighting before grace period", d0.shouldEmitAlert)

        // Poll at +10s -> still within 15s grace period
        val d10 = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 11_000L,
            gracePeriodSeconds = 15,
            cooldownSeconds = 60,
            enforceGracePeriod = true
        ) as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertFalse("Alert must NOT fire at 10s when grace period is 15s", d10.shouldEmitAlert)

        // Poll at +15s -> grace period satisfied, alert fires!
        val d15 = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 16_000L,
            gracePeriodSeconds = 15,
            cooldownSeconds = 60,
            enforceGracePeriod = true
        ) as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertTrue("Alert MUST fire once continuous dwell reaches 15s", d15.shouldEmitAlert)

        // Mark notified -> cooldown suppresses immediate repeat even though dwell >= 15s
        DistractionAlertCoordinator.markNotified(started.sessionId, "com.instagram.android", t0 + 16_000L)
        val d20 = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.instagram.android",
            observedAppLabel = "Instagram",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 21_000L,
            gracePeriodSeconds = 15,
            cooldownSeconds = 60,
            enforceGracePeriod = true
        ) as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertFalse("Cooldown must prevent repeat alert within 60s", d20.shouldEmitAlert)
    }

    @Test
    fun gracePeriod_resetsWhenUserReturnsToAllowedApp() {
        val t0 = 1_700_000_000_000L
        val started = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Sprint Planning",
            durationMinutes = 45,
            selectedApps = listOf(SelectedSessionApp("com.slack", "Slack")),
            breakPlan = BreakPlanConfig(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = 1_000L
        )!!

        // User accidentally taps Reddit at t+0s and stays 10s
        PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.reddit.frontpage",
            observedAppLabel = "Reddit",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 1_000L,
            gracePeriodSeconds = 15,
            enforceGracePeriod = true
        )
        // User returns to approved Slack at t+11s -> clears dwell!
        val backToWork = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.slack",
            observedAppLabel = "Slack",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 11_000L,
            gracePeriodSeconds = 15,
            enforceGracePeriod = true
        )
        assertTrue(backToWork is PurposeMatchingEngine.MatchDecision.ApprovedForPurpose)

        // User opens Reddit again at t+20s -> dwell must restart from 0s, NOT fire immediately
        val secondGlance = PurposeMatchingEngine.evaluateForegroundApp(
            observedPackage = "com.reddit.frontpage",
            observedAppLabel = "Reddit",
            snapshot = started,
            activeTempAccess = null,
            nowEpochMs = t0 + 20_000L,
            gracePeriodSeconds = 15,
            enforceGracePeriod = true
        ) as PurposeMatchingEngine.MatchDecision.PotentialDistraction
        assertFalse("Dwell timer must reset after returning to an allowed app", secondGlance.shouldEmitAlert)
    }

    @Test
    fun earlyExitDuringBreak_unwindsParentFocusTimeAndKeepsBreakSeparate() {
        val t0 = 1_700_000_000_000L
        val e0 = 10_000L
        val focusStarted = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Algorithms",
            durationMinutes = 45,
            selectedApps = emptyList(),
            breakPlan = BreakPlanConfig(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0
        )!!

        // Focus for 12 minutes (720 seconds), then start a 5-minute break
        val tBreakStart = t0 + 720_000L
        val eBreakStart = e0 + 720_000L
        val onBreak = FocusTimerEngine.startBreakPreservingParent(
            current = focusStarted,
            breakMinutes = 5,
            nowEpochMs = tBreakStart,
            nowElapsedRealtimeMs = eBreakStart
        )
        assertTrue(onBreak.isOnBreak)
        assertNotNull(onBreak.pausedParentSession)

        // User takes 2 minutes (120s) of the break and taps Exit -> Finish Early
        val tExit = tBreakStart + 120_000L
        val eExit = eBreakStart + 120_000L
        val resolved = FocusTimerEngine.resolveParentForEarlyExit(
            snapshot = onBreak,
            activeTempAccess = null,
            nowEpochMs = tExit,
            nowElapsedRealtimeMs = eExit
        )
        val decision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = resolved,
            nowEpochMs = tExit,
            nowElapsedRealtimeMs = eExit
        )

        assertTrue("Parent focus session with 12m elapsed MUST be persisted", decision.shouldPersist)
        assertEquals(PurposeSessionRecord.STATUS_ENDED_EARLY, decision.status)
        assertEquals("Only actual focus seconds (720s) should be logged, not break or planned time", 720, decision.actualSeconds)
        assertEquals("Planned seconds must reflect parent focus session (45m)", 45 * 60, decision.plannedSeconds)
        assertEquals("Break time (120s) must be tracked separately in accumulatedBreakSeconds", 120, resolved.accumulatedBreakSeconds)
        assertEquals(FocusMode.FOCUS, resolved.mode)
        assertFalse(resolved.isOnBreak)
    }

    @Test
    fun earlyExitDuringTemporaryAccess_foldsTempSecondsSeparatelyFromFocusTime() {
        val t0 = 1_700_000_000_000L
        val e0 = 5_000L
        val focusStarted = FocusTimerEngine.startPurposeSession(
            purposeCategory = SessionPurposeCategory.WORK,
            sessionName = "Code Review",
            durationMinutes = 30,
            selectedApps = emptyList(),
            breakPlan = BreakPlanConfig(),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0
        )!!

        // Focus for 10 minutes (600s), then pause for temporary access to WhatsApp (5m)
        val tTempStart = t0 + 600_000L
        val eTempStart = e0 + 600_000L
        val pausedForTemp = FocusTimerEngine.pauseForTemporaryAccess(focusStarted, tTempStart, eTempStart)
        val tempResult = TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = focusStarted.sessionId,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            purposeLabel = "Code Review",
            durationMinutes = 5,
            nowEpochMs = tTempStart
        ) as TemporaryAccessPolicy.StartResult.Started

        // Exit early 90 seconds into temporary access
        val tExit = tTempStart + 90_000L
        val eExit = eTempStart + 90_000L
        val resolved = FocusTimerEngine.resolveParentForEarlyExit(
            snapshot = pausedForTemp,
            activeTempAccess = tempResult.session,
            nowEpochMs = tExit,
            nowElapsedRealtimeMs = eExit
        )
        val decision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = resolved,
            nowEpochMs = tExit,
            nowElapsedRealtimeMs = eExit
        )

        assertTrue(decision.shouldPersist)
        assertEquals("Focus time must remain strictly 600s (temp access never inflates focus)", 600, decision.actualSeconds)
        assertEquals("Temp access duration (90s) must be folded separately", 90, resolved.accumulatedTempAccessSeconds)
    }

    @Test
    fun roomMigrations_areContiguousFromVersion1ToVersion6() {
        val migrations = listOf(
            BrainBufferDatabase.MIGRATION_1_2,
            BrainBufferDatabase.MIGRATION_2_3,
            BrainBufferDatabase.MIGRATION_3_4,
            BrainBufferDatabase.MIGRATION_4_5,
            BrainBufferDatabase.MIGRATION_5_6
        )
        assertEquals(1, migrations.first().startVersion)
        assertEquals(6, migrations.last().endVersion)
        for (i in 0 until migrations.lastIndex) {
            assertEquals(
                "Migration chain must be strictly contiguous without gaps",
                migrations[i].endVersion,
                migrations[i + 1].startVersion
            )
        }
    }

    @Test
    fun supabaseSessionAndConfig_preserveEmailAuthWithoutGoogleOAuth() {
        val guest = SupabaseAuthSession()
        assertFalse(guest.isSignedIn)
        assertTrue(guest.providerLabels.isEmpty())

        val futureEpochSec = (System.currentTimeMillis() / 1000L) + 3600L
        val signedIn = SupabaseAuthSession(
            userId = "user-uuid-123",
            email = "student@example.edu",
            accessToken = "jwt-access-token",
            refreshToken = "jwt-refresh-token",
            expiresAtEpochSec = futureEpochSec,
            providers = listOf("email")
        )
        assertTrue(signedIn.isSignedIn)
        assertFalse(signedIn.isExpired())
        assertEquals(listOf("Email"), signedIn.providerLabels)

        val expired = signedIn.copy(expiresAtEpochSec = 100L)
        assertTrue(expired.isExpired())

        // Verify URL builders never crash and point to standard Supabase paths
        assertTrue(SupabaseConfig.authUrl("token?grant_type=password").contains("/auth/v1/token"))
        assertTrue(SupabaseConfig.restUrl("profiles?limit=1").contains("/rest/v1/profiles"))
        assertTrue(SupabaseConfig.functionUrl("delete-account").contains("/functions/v1/delete-account"))
    }

    @Test
    fun intentRealityAnalytics_handlesEmptyRangeAndDeduplicatesLegacyRecords() {
        val today = 20_000L

        // Empty state: zero fabricated numbers
        val emptySummary = IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = emptyList(),
            legacyFocusSessions = emptyList(),
            interventionEvents = emptyList(),
            range = InsightsRangeFilter.TODAY,
            todayEpochDay = today
        )
        assertFalse(emptySummary.hasAnyRecordedData)
        assertNull(emptySummary.alignmentPercent)
        assertEquals(0, emptySummary.approvedFocusMinutes)
        assertEquals(0, emptySummary.intendedPlannedMinutes)

        // Matching purpose session + legacy focus session at same timestamp -> MUST NOT double count
        val endedAt = 1_700_001_800_000L
        val purposeRec = PurposeSessionRecord(
            id = 1L,
            sessionKey = "sess_1",
            purposeCategory = SessionPurposeCategory.STUDY,
            sessionName = "Linear Algebra",
            plannedSeconds = 1800,
            actualFocusedSeconds = 1800,
            breakSeconds = 300,
            temporaryAccessSeconds = 120,
            distractionSeconds = 0,
            interventionCount = 0,
            status = PurposeSessionRecord.STATUS_COMPLETED,
            startedAtMs = endedAt - 1_800_000L,
            endedAtMs = endedAt,
            dateEpochDay = today
        )
        val duplicateLegacy = FocusSessionRecord(
            id = 10L,
            subjectId = null,
            subjectName = "Linear Algebra",
            subjectColorHex = 0xFF7B5CFA,
            durationSeconds = 1800,
            plannedSeconds = 1800,
            sessionGoal = "Linear Algebra",
            mode = FocusMode.FOCUS,
            completedAtMs = endedAt + 500L,
            dateEpochDay = today
        )
        val olderSession = purposeRec.copy(
            id = 2L,
            sessionKey = "sess_old",
            dateEpochDay = today - 10L,
            endedAtMs = endedAt - 10 * 86_400_000L
        )

        val todaySummary = IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = listOf(purposeRec, olderSession),
            legacyFocusSessions = listOf(duplicateLegacy),
            interventionEvents = emptyList(),
            range = InsightsRangeFilter.TODAY,
            todayEpochDay = today
        )
        assertEquals("Legacy duplicate within 5s must NOT be double-counted", 30, todaySummary.approvedFocusMinutes)
        assertEquals(1, todaySummary.completedSessionCount)
        assertEquals(100, todaySummary.alignmentPercent)
        assertEquals(5, todaySummary.breakMinutes)
        assertEquals(2, todaySummary.temporaryAccessMinutes)

        val thirtyDaySummary = IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = listOf(purposeRec, olderSession),
            legacyFocusSessions = listOf(duplicateLegacy),
            interventionEvents = emptyList(),
            range = InsightsRangeFilter.THIRTY_DAYS,
            todayEpochDay = today
        )
        assertEquals("30-day range should include both today and 10-days-ago sessions", 60, thirtyDaySummary.approvedFocusMinutes)
        assertEquals(2, thirtyDaySummary.completedSessionCount)
    }
}
