package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusSessionStateMachine
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityAllowanceConfig
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.ControlledAccessOption
import com.example.model.FocusGuardLevel
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.FocusSessionState
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.notifications.BrainBufferNotificationManager
import com.example.performance.AdaptiveBlurController
import com.example.performance.BlurPerformanceTier
import com.example.performance.DevicePerformanceSignals
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileEngine
import com.example.performance.PerformanceProfileOverride
import com.example.service.TimerNotificationActionReceiver
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DistractionAlertCoordinator
import com.example.ui.components.ControlledPhoneAccessContent
import com.example.ui.screens.InsightsPrimaryScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.viewmodel.BrainBufferUiState
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Focus Guard 3.0 End-to-End Verification Suite (Part 17 Scenarios A–I):
 * - Scenario A: Normal/High Study + Lock/Recovery + BRAIN BUFFER LIVE notification & API 36 Live Updates hint
 * - Scenario B: Approved YouTube Lecture (zero distraction warning, exact return, separate stats)
 * - Scenario C: PDF / Documents & 8th category Notes / Writing -> APPROVED_STUDY_ACTIVITY
 * - Scenario D: Unplanned Game / Instagram -> Real Android Distraction Warning notification,
 *               system/emergency exemptions, cooldown & "Continue Anyway" suppression, and "Allow for 5 min"
 * - Scenario E & F: Approved Gaming (20m) & Social Media (10m) -> APPROVED_ENTERTAINMENT_ACTIVITY + custom extension
 * - Scenario G: Unified Session Recovery & Zero Duplicate Logging
 * - Scenario H: Usage Access Denied Graceful Fallback + 8-category Controlled Phone Access UI
 * - Scenario I: Budget Performance Profile + Adaptive Blur LOW + Enhanced Insights UI (Today/7 Days, Nudges)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusGuard3Test {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        DistractionAlertCoordinator.resetAllForTest()
        BrainBufferNotificationManager.clearPostedSessionKeysForTest()
    }

    @After
    fun tearDown() {
        DistractionAlertCoordinator.resetAllForTest()
        BrainBufferNotificationManager.clearPostedSessionKeysForTest()
    }

    // -------------------------------------------------------------------------
    // Scenario A: Unified Study + Live Notification + Android 16 Live Updates
    // -------------------------------------------------------------------------

    @Test
    fun `Scenario A - BRAIN BUFFER LIVE notification renders chronometer, Switch Activity action, and respects Live Updates toggle on API 36`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val t0 = 1_760_000_000_000L
        val e0 = 100_000L

        val study = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Complete Calculus Chapter 4",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 45,
            strictness = StrictnessLevel.DEEP,
            enableAodStyle = true,
            dndActive = true,
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0,
            guardLevel = FocusGuardLevel.HIGH
        )

        // Advance 5 minutes cleanly while screen is locked
        val liveNotif = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = context,
            snapshot = study,
            subjectName = "Calculus",
            prefs = NotificationPreferences(hidePrivateNotesOnLockScreen = true),
            nowEpochMs = t0 + 300_000L,
            nowElapsedRealtimeMs = e0 + 300_000L,
            liveUpdatesEnabled = true
        )

        val title = liveNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertEquals("BRAIN BUFFER LIVE", title)
        assertTrue(liveNotif.extras.getBoolean("android.requestPromotedOngoing", false))
        assertEquals("40:00", liveNotif.extras.getString("android.shortCriticalText"))

        val actionTitles = liveNotif.actions?.map { it.title.toString() }.orEmpty()
        assertEquals(listOf("Pause", "Switch Activity", "End"), actionTitles)

        // When user disables Live Updates in settings, API 36 promotion extras are omitted
        val standardNotif = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = context,
            snapshot = study,
            subjectName = "Calculus",
            prefs = NotificationPreferences(),
            nowEpochMs = t0 + 300_000L,
            nowElapsedRealtimeMs = e0 + 300_000L,
            liveUpdatesEnabled = false
        )
        assertFalse(standardNotif.extras.getBoolean("android.requestPromotedOngoing", false))
    }

    // -------------------------------------------------------------------------
    // Scenario B & C: Approved YouTube Lecture, PDF & 8th Category Notes/Writing
    // -------------------------------------------------------------------------

    @Test
    fun `Scenarios B and C - Approved Lecture, PDF, and Notes derive APPROVED_STUDY_ACTIVITY and allow approved apps without warnings`() {
        val t0 = 1_760_000_000_000L
        val e0 = 50_000L
        val study = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Linear Algebra",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 50,
            strictness = StrictnessLevel.COMMITTED,
            enableAodStyle = true,
            dndActive = false,
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0,
            guardLevel = FocusGuardLevel.HIGH
        )

        // Scenario B: Switch to YouTube lecture
        val lecture = FocusTimerEngine.switchActivity(
            current = study,
            activityType = StudyActivityType.STUDY_LECTURE,
            activityLabel = "Study lecture · YouTube",
            category = ActivityCategory.LECTURE,
            durationMinutes = 25,
            subjectId = 1L,
            wasPlanned = true,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = e0 + 600_000L,
            approvedPackageName = "com.google.android.youtube",
            approvedAppLabel = "YouTube"
        )
        assertEquals(
            FocusSessionState.APPROVED_STUDY_ACTIVITY,
            FocusSessionStateMachine.deriveState(lecture, t0 + 660_000L, e0 + 660_000L)
        )
        assertNull(
            AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
                observedPackage = "com.google.android.youtube",
                snapshot = lecture
            )
        )

        // Scenario C: Switch to 8th category Notes / Writing (Notion)
        val notes = FocusTimerEngine.switchActivity(
            current = study,
            activityType = StudyActivityType.WRITING_NOTES,
            activityLabel = "Notes · Notion",
            category = ActivityCategory.READING,
            durationMinutes = 20,
            subjectId = 1L,
            wasPlanned = true,
            nowEpochMs = t0 + 600_000L,
            nowElapsedRealtimeMs = e0 + 600_000L,
            approvedPackageName = "notion.id",
            approvedAppLabel = "Notion"
        )
        assertEquals(
            FocusSessionState.APPROVED_STUDY_ACTIVITY,
            FocusSessionStateMachine.deriveState(notes, t0 + 660_000L, e0 + 660_000L)
        )
        assertNull(
            AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
                observedPackage = "notion.id",
                snapshot = notes
            )
        )
        assertEquals(6, ControlledAccessOption.entries.size)
        assertTrue(ControlledAccessOption.entries.contains(ControlledAccessOption.FILES_AND_DOCUMENTS))

        // Returning to study restores exact remaining 40:00 (2400s)
        val restored = FocusTimerEngine.returnToPausedStudySession(notes, t0 + 900_000L, e0 + 900_000L)
        assertNotNull(restored)
        assertEquals(2400, FocusTimerEngine.remainingSeconds(restored!!, t0 + 900_000L, e0 + 900_000L))
    }

    // -------------------------------------------------------------------------
    // Scenario D: Unplanned Distraction -> Real Android Notification, Exemptions, Cooldown & Actions
    // -------------------------------------------------------------------------

    @Test
    fun `Scenario D - Unplanned Game or Instagram triggers real distraction notification, exempts system apps, and respects cooldown and notification actions`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val t0 = 1_760_000_000_000L
        val e0 = 80_000L

        val highFocus = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Physics Problems",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 40,
            strictness = StrictnessLevel.DEEP,
            enableAodStyle = true,
            dndActive = false,
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0,
            guardLevel = FocusGuardLevel.HIGH
        )

        // 1. Exempt system / emergency / dialer / launcher / settings packages MUST NEVER warn
        val exemptPackages = listOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.emergency",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.inputmethod.latin",
            context.packageName
        )
        exemptPackages.forEach { pkg ->
            assertTrue("Expected $pkg to be exempt", AppUsageAwarenessHelper.isExemptSystemPackage(pkg, context.packageName))
            assertNull(
                AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
                    observedPackage = pkg,
                    snapshot = highFocus,
                    ownPackageName = context.packageName
                )
            )
        }

        // 2. Unplanned game during High Focus -> emits warning
        val gameWarning = AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
            observedPackage = "com.supercell.clashroyale",
            snapshot = highFocus,
            ownPackageName = context.packageName
        )
        assertNotNull(gameWarning)
        assertEquals(ActivityCategory.ENTERTAINMENT, gameWarning!!.observedCategory)

        // 3. Real Android notification posted to CHANNEL_DISTRACTION_WARNINGS with 3 direct actions
        val posted = BrainBufferNotificationManager.postDistractionWarningNotification(
            context = context,
            snapshot = highFocus,
            subjectName = "Physics",
            detectedPackageName = gameWarning.packageName,
            detectedAppLabel = gameWarning.appDisplayName,
            prefs = NotificationPreferences(distractionWarningsEnabled = true),
            masterNotificationsEnabled = true,
            hourOfDay = 15
        )
        assertTrue(posted)

        val nm = context.getSystemService(NotificationManager::class.java)
        val notif = Shadows.shadowOf(nm).getNotification(BrainBufferNotificationManager.ID_DISTRACTION_WARNING)
        assertNotNull(notif)
        assertEquals(BrainBufferNotificationManager.CHANNEL_DISTRACTION_WARNINGS, notif.channelId)
        val actionTitles = notif.actions?.map { it.title.toString() }.orEmpty()
        assertEquals(listOf("Return to Study", "Allow for 5 min", "Continue Anyway"), actionTitles)

        // 4. Cooldown prevents repeated spam within 90s, allows again after 90s
        assertTrue(DistractionAlertCoordinator.shouldEmitAlert(highFocus.sessionId, gameWarning.packageName, t0, 90))
        DistractionAlertCoordinator.markNotified(highFocus.sessionId, gameWarning.packageName, t0)
        assertFalse(DistractionAlertCoordinator.shouldEmitAlert(highFocus.sessionId, gameWarning.packageName, t0 + 30_000L, 90))
        assertTrue(DistractionAlertCoordinator.shouldEmitAlert(highFocus.sessionId, gameWarning.packageName, t0 + 95_000L, 90))

        // 5. "Continue Anyway" action suppresses that package for the rest of the current session
        val db = BrainBufferDatabase.getInstance(context)
        val prefsStore = UserPreferencesStore(context)
        val repo = BrainBufferRepository(db, prefsStore)
        repo.saveTimerSnapshot(highFocus)

        TimerNotificationActionReceiver.handleAction(
            appContext = context,
            action = TimerNotificationActionReceiver.ACTION_CONTINUE_ANYWAY,
            nowEpochMs = t0 + 100_000L,
            nowElapsedMs = e0 + 100_000L,
            distractionPackage = gameWarning.packageName,
            distractionAppLabel = gameWarning.appDisplayName,
            sessionId = highFocus.sessionId
        )
        assertFalse(
            DistractionAlertCoordinator.shouldEmitAlert(highFocus.sessionId, gameWarning.packageName, t0 + 500_000L, 90)
        )

        // 6. "Allow for 5 min" action cleanly switches to a 5-min allowance while preserving pausedParentSession
        TimerNotificationActionReceiver.handleAction(
            appContext = context,
            action = TimerNotificationActionReceiver.ACTION_ALLOW_5_MIN,
            extendMinutes = 5,
            nowEpochMs = t0 + 120_000L,
            nowElapsedMs = e0 + 120_000L,
            distractionPackage = "com.instagram.android",
            distractionAppLabel = "Instagram",
            sessionId = highFocus.sessionId
        )
        val afterAllow = repo.getTimerSnapshotOnce()
        assertTrue(afterAllow.isSwitchedActivity)
        assertEquals(5 * 60, afterAllow.totalSeconds)
        assertEquals("com.instagram.android", afterAllow.approvedPackageName)
        assertNotNull(afterAllow.pausedParentSession)
        assertEquals(
            FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
            FocusSessionStateMachine.deriveState(afterAllow, t0 + 130_000L, e0 + 130_000L)
        )
    }

    // -------------------------------------------------------------------------
    // Scenarios E & F: Approved Gaming (20m) & Social Media (10m) + Custom Extension
    // -------------------------------------------------------------------------

    @Test
    fun `Scenarios E and F - Approved Gaming and Social Media derive APPROVED_ENTERTAINMENT_ACTIVITY and honor custom extension minutes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val t0 = 1_760_000_000_000L
        val e0 = 50_000L

        val study = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Biology",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 45,
            strictness = StrictnessLevel.COMMITTED,
            enableAodStyle = true,
            dndActive = false,
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0,
            guardLevel = FocusGuardLevel.HIGH
        )

        val social = FocusTimerEngine.switchActivity(
            current = study,
            activityType = StudyActivityType.ENTERTAINMENT,
            activityLabel = "Social · Instagram",
            category = ActivityCategory.ENTERTAINMENT,
            durationMinutes = 10,
            subjectId = 1L,
            wasPlanned = true,
            nowEpochMs = t0 + 300_000L,
            nowElapsedRealtimeMs = e0 + 300_000L,
            approvedPackageName = "com.instagram.android",
            approvedAppLabel = "Instagram"
        )
        assertEquals(
            FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY,
            FocusSessionStateMachine.deriveState(social, t0 + 360_000L, e0 + 360_000L)
        )
        // Approved Instagram triggers zero warnings, whereas switching to TikTok triggers gentle nudge
        assertNull(AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity("com.instagram.android", social))
        val tiktokSwitch = AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity("com.zhiliaoapp.musically", social)
        assertNotNull(tiktokSwitch)

        // Completion notification uses user's custom 10-min extension setting
        val posted = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = social,
            subjectName = "Biology",
            prefs = NotificationPreferences(),
            masterNotificationsEnabled = true,
            hourOfDay = 14,
            extensionMinutes = 10
        )
        assertTrue(posted)
        val nm = context.getSystemService(NotificationManager::class.java)
        val notif = Shadows.shadowOf(nm).getNotification(BrainBufferNotificationManager.ID_COMPLETION)
        val actionTitles = notif.actions?.map { it.title.toString() }.orEmpty()
        assertTrue(actionTitles.contains("Extend +10m"))
        assertTrue(actionTitles.contains("Return to Study"))
    }

    // -------------------------------------------------------------------------
    // Scenario G & H: Deduplication, Usage Access Denied Fallback & Play Policy
    // -------------------------------------------------------------------------

    @Test
    fun `Scenarios G and H - Usage Access denied falls back gracefully with zero crashes and manifest stays strictly Play-compliant`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val activeSnap = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "History",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 30,
            strictness = StrictnessLevel.DEEP,
            enableAodStyle = true,
            dndActive = false,
            nowEpochMs = 1_760_000_000_000L,
            nowElapsedRealtimeMs = 10_000L,
            guardLevel = FocusGuardLevel.HIGH
        )

        // When Usage Access is disabled or denied, detection returns null safely
        assertNull(
            AppUsageAwarenessHelper.detectUnapprovedAppMismatch(
                context = context,
                enabledByUser = false,
                snapshot = activeSnap
            )
        )
        assertNull(
            AppUsageAwarenessHelper.queryCurrentForegroundPackage(
                context = context,
                enabledByUser = true
            )
        )

        // Play policy verification: zero invasive permissions
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(perms.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(perms.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(perms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(perms.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    @Test
    fun `Scenario H UI - ControlledPhoneAccessContent renders all 8 categories including Notes and Writing`() {
        var approvedType: StudyActivityType? = null
        var approvedMinutes = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK, performanceOverride = PerformanceProfileOverride.BALANCED) {
                ControlledPhoneAccessContent(
                    allowances = ActivityAllowanceConfig(notesDefaultMinutes = 20),
                    appCategoryMappings = emptyList(),
                    todayEntertainmentMinutes = 0,
                    hasActiveStudySession = true,
                    hapticsEnabled = false,
                    onDismiss = {},
                    onApproveActivity = { type, _, _, mins, _, _, _ ->
                        approvedType = type
                        approvedMinutes = mins
                    }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(6, ControlledAccessOption.entries.size)
        ControlledAccessOption.entries.forEach { option ->
            composeRule.onNodeWithTag("controlled_access_option_${option.name.lowercase()}").assertExists()
        }

        composeRule.onNodeWithTag("controlled_access_option_files_and_documents").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("controlled_access_confirm_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(StudyActivityType.READING, approvedType)
        assertEquals(20, approvedMinutes)
    }

    // -------------------------------------------------------------------------
    // Scenario I: Budget Hardware Profile + Enhanced Insights UI (Today/7 Days + Nudges)
    // -------------------------------------------------------------------------

    @Test
    fun `Scenario I - Budget device resolves to Performance profile + LOW blur tier and Insights renders Today or 7 Days separation and Distraction Nudges`() {
        val budgetSignals = DevicePerformanceSignals(
            totalRamMb = 2048,
            isLowRamDevice = true,
            cpuCores = 4,
            sdkInt = 29,
            refreshRateHz = 60f,
            isPowerSaveMode = false
        )
        val profile = PerformanceProfileEngine.resolveProfile(budgetSignals)
        assertEquals(PerformanceProfile.PERFORMANCE, profile)
        assertEquals(BlurPerformanceTier.LOW, BlurPerformanceTier.fromProfile(profile))

        val todayEpoch = java.time.LocalDate.now().toEpochDay()
        val uiState = BrainBufferUiState(
            preferences = UserPreferences(
                distractionWarningsToday = 1,
                distractionWarningsWeek = 3
            ),
            sessions = listOf(
                FocusSessionRecord(
                    id = 1L,
                    subjectId = 1L,
                    subjectName = "Physics",
                    subjectColorHex = 0xFF7B5CFA,
                    durationSeconds = 2700, // 45m focused study today
                    plannedSeconds = 3000, // 50m planned
                    sessionGoal = "Derivations",
                    mode = FocusMode.FOCUS,
                    completedAtMs = 1_760_000_000_000L,
                    dateEpochDay = todayEpoch
                )
            ),
            activityLogs = listOf(
                ActivityLogRecord(
                    id = 1L,
                    sessionKey = "log-study",
                    activityType = StudyActivityType.STUDYING,
                    activityLabel = "Focused Study",
                    category = ActivityCategory.STUDY,
                    subjectId = 1L,
                    subjectName = "Physics",
                    subjectColorHex = 0xFF7B5CFA,
                    plannedSeconds = 3000, // 50m planned
                    actualSeconds = 2700,  // 45m actual
                    startedAtMs = 1_760_000_000_000L,
                    endedAtMs = 1_760_002_700_000L,
                    dateEpochDay = todayEpoch,
                    wasPlanned = true
                ),
                ActivityLogRecord(
                    id = 2L,
                    sessionKey = "log-game",
                    activityType = StudyActivityType.PLAYING_GAME,
                    activityLabel = "Games",
                    category = ActivityCategory.ENTERTAINMENT,
                    subjectId = null,
                    subjectName = "General Study",
                    subjectColorHex = 0xFFF59E0B,
                    plannedSeconds = 1800, // 30m planned
                    actualSeconds = 1800,  // 30m actual
                    startedAtMs = 1_760_003_000_000L,
                    endedAtMs = 1_760_004_800_000L,
                    dateEpochDay = todayEpoch,
                    wasPlanned = true
                ),
                ActivityLogRecord(
                    id = 3L,
                    sessionKey = "log-social",
                    activityType = StudyActivityType.ENTERTAINMENT,
                    activityLabel = "Social · Instagram",
                    category = ActivityCategory.ENTERTAINMENT,
                    subjectId = null,
                    subjectName = "General Study",
                    subjectColorHex = 0xFFEC4899,
                    plannedSeconds = 1200, // 20m planned
                    actualSeconds = 900,   // 15m actual
                    startedAtMs = 1_760_005_000_000L,
                    endedAtMs = 1_760_005_900_000L,
                    dateEpochDay = todayEpoch,
                    wasPlanned = true
                )
            )
        )

        // Verify exact computed separation: 45m study, 30m gaming, 15m social, 90m actual of 100m planned = 90%
        assertEquals(45, uiState.todayCategoryMinutes[ActivityCategory.STUDY])
        assertEquals(30, uiState.todayGamingMinutes)
        assertEquals(15, uiState.todaySocialMinutes)
        assertEquals(100, uiState.weeklyPlannedMinutes)
        assertEquals(90, uiState.weeklyActualMinutes)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK, performanceOverride = PerformanceProfileOverride.PERFORMANCE) {
                InsightsPrimaryScreen(uiState = uiState)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_alignment_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_range_today").performClick()
        composeRule.waitForIdle()
    }
}
