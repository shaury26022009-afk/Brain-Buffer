package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.Subject
import com.example.model.ThemeMode
import com.example.notifications.BrainBufferNotificationManager
import com.example.performance.DevicePerformanceSignals
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileEngine
import com.example.performance.PerformanceProfileOverride
import com.example.performance.RenderQualityTokens
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndBackend
import com.example.system.DndFocusController
import com.example.ui.components.BrainBufferLivePill
import com.example.ui.screens.StrictFocusScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.viewmodel.BrainBufferUiState
import com.example.viewmodel.FocusTimerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NextGenCompanionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: BrainBufferDatabase
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var repository: BrainBufferRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BrainBufferNotificationManager.clearPostedSessionKeysForTest()
        database = Room.inMemoryDatabaseBuilder(context, BrainBufferDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesStore = UserPreferencesStore(context)
        repository = BrainBufferRepository(database, preferencesStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -------------------------------------------------------------------------
    // PART 1: Adaptive Performance Profile Engine (multi-signal, never RAM-only)
    // -------------------------------------------------------------------------

    @Test
    fun `performance profile engine resolves Performance, Balanced, Premium and overrides accurately`() {
        val budgetAndroid10 = DevicePerformanceSignals(
            totalRamMb = 2048,
            isLowRamDevice = true,
            cpuCores = 4,
            sdkInt = Build.VERSION_CODES.Q,
            refreshRateHz = 60f,
            isPowerSaveMode = false
        )
        assertEquals(
            PerformanceProfile.PERFORMANCE,
            PerformanceProfileEngine.resolveProfile(budgetAndroid10)
        )

        val midRangePhone = DevicePerformanceSignals(
            totalRamMb = 4096,
            isLowRamDevice = false,
            cpuCores = 6,
            sdkInt = Build.VERSION_CODES.S,
            refreshRateHz = 60f,
            isPowerSaveMode = false
        )
        assertEquals(
            PerformanceProfile.BALANCED,
            PerformanceProfileEngine.resolveProfile(midRangePhone)
        )

        val flagshipPhone = DevicePerformanceSignals(
            totalRamMb = 8192,
            isLowRamDevice = false,
            cpuCores = 8,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            refreshRateHz = 120f,
            isPowerSaveMode = false
        )
        assertEquals(
            PerformanceProfile.PREMIUM,
            PerformanceProfileEngine.resolveProfile(flagshipPhone)
        )

        // Power saver forces Performance mode even on flagship hardware
        assertEquals(
            PerformanceProfile.PERFORMANCE,
            PerformanceProfileEngine.resolveProfile(flagshipPhone.copy(isPowerSaveMode = true))
        )

        // Severe live jank automatically downgrades to Performance mode
        assertEquals(
            PerformanceProfile.PERFORMANCE,
            PerformanceProfileEngine.resolveProfile(flagshipPhone.copy(observedJankRatio = 0.30f))
        )

        // RAM alone does NOT dictate profile: 8 GB RAM on a weak 2-core 60Hz Android 10 device -> BALANCED, not PREMIUM
        val highRamWeakCpu = DevicePerformanceSignals(
            totalRamMb = 8192,
            isLowRamDevice = false,
            cpuCores = 2,
            sdkInt = Build.VERSION_CODES.Q,
            refreshRateHz = 60f,
            isPowerSaveMode = false
        )
        assertEquals(
            PerformanceProfile.PERFORMANCE,
            PerformanceProfileEngine.resolveProfile(highRamWeakCpu)
        )

        // Explicit user override always wins
        assertEquals(
            PerformanceProfile.PERFORMANCE,
            PerformanceProfileEngine.resolveProfile(flagshipPhone, PerformanceProfileOverride.PERFORMANCE)
        )
        assertEquals(
            PerformanceProfile.PREMIUM,
            PerformanceProfileEngine.resolveProfile(budgetAndroid10, PerformanceProfileOverride.PREMIUM)
        )

        // RenderQualityTokens disables expensive blur/shadows/fireflies on Performance & API 29
        val perfTokens = RenderQualityTokens.forProfile(PerformanceProfile.PERFORMANCE, sdkInt = 35)
        assertFalse(perfTokens.enableGlassBlur)
        assertFalse(perfTokens.enableAmbientShadows)
        assertFalse(perfTokens.enableContinuousCanvasMotion)
        assertEquals(0, perfTokens.gardenFireflyCount)

        val api29Balanced = RenderQualityTokens.forProfile(PerformanceProfile.BALANCED, sdkInt = Build.VERSION_CODES.Q)
        assertFalse("RenderEffect blur must stay disabled on Android 10", api29Balanced.enableGlassBlur)
    }

    // -------------------------------------------------------------------------
    // PART 2 & 8: Monotonic Clock, Reboot Fallback, Pause & Process-Recreation
    // -------------------------------------------------------------------------

    @Test
    fun `monotonic timer ignores wall-clock tampering and falls back safely after reboot`() {
        val initial = PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            totalSeconds = 30 * 60
        )
        val wallStart = 1_700_000_000_000L
        val monoStart = 500_000L

        val running = FocusTimerEngine.startOrResume(
            snapshot = initial,
            nowEpochMs = wallStart,
            nowElapsedRealtimeMs = monoStart
        )
        assertTrue(running.sessionId.isNotBlank())

        // 10 real minutes pass on monotonic clock, while user shifts system clock backward by 2 hours
        val monoAfter10Min = monoStart + 10 * 60 * 1000L
        val tamperedWallClock = wallStart - 2 * 60 * 60 * 1000L
        assertEquals(
            20 * 60,
            FocusTimerEngine.remainingSeconds(running, tamperedWallClock, monoAfter10Min)
        )
        assertEquals(
            10 * 60,
            FocusTimerEngine.actualFocusedSeconds(running, tamperedWallClock, monoAfter10Min)
        )

        // Simulate device reboot: monotonic clock resets below monoStart (e.g., 15_000ms),
        // while wall clock advanced by 12 minutes -> falls back safely to wall clock
        val wallAfterReboot = wallStart + 12 * 60 * 1000L
        val rebootedMono = 15_000L
        assertEquals(
            18 * 60,
            FocusTimerEngine.remainingSeconds(running, wallAfterReboot, rebootedMono)
        )
    }

    @Test
    fun `timer snapshot persists and restores across simulated process death`() = runTest {
        val nowWall = 1_700_100_000_000L
        val nowMono = 200_000L
        val study = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                selectedSubjectId = 42L,
                sessionGoal = "Finish linear algebra proof",
                totalSeconds = 45 * 60
            ),
            nowEpochMs = nowWall,
            nowElapsedRealtimeMs = nowMono
        )
        // Switch mid-session to a 15m short break so pausedParentSession is also populated
        val switched = FocusTimerEngine.switchActivity(
            current = study,
            activityType = StudyActivityType.SHORT_BREAK,
            activityLabel = "Taking a short break",
            category = ActivityCategory.BREAK,
            durationMinutes = 15,
            subjectId = 42L,
            wasPlanned = true,
            nowEpochMs = nowWall + 10 * 60 * 1000L,
            nowElapsedRealtimeMs = nowMono + 10 * 60 * 1000L
        )

        preferencesStore.saveTimerSnapshot(switched)
        val restored = preferencesStore.getTimerSnapshotOnce()

        assertEquals(switched.sessionId, restored.sessionId)
        assertEquals(StudyActivityType.SHORT_BREAK, restored.activeActivityType)
        assertEquals(ActivityCategory.BREAK, restored.activeActivityCategory)
        assertNotNull("Paused parent study session must survive process recreation", restored.pausedParentSession)
        assertEquals(10 * 60 * 1000L, restored.pausedParentSession?.accumulatedFocusedMs)
        assertEquals(45 * 60, restored.pausedParentSession?.totalSeconds)
    }

    // -------------------------------------------------------------------------
    // PART 5 & 7: Smart Activity Switching, Extension & Study vs Break Separation
    // -------------------------------------------------------------------------

    @Test
    fun `activity switching preserves parent study session and separates non-study activities`() {
        val t0 = 10_000_000L
        val m0 = 1_000_000L

        // Start 60m Studying
        val studyStarted = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                selectedSubjectId = 7L,
                sessionGoal = "Organic chemistry mechanisms",
                totalSeconds = 60 * 60
            ),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = m0
        )

        // Study for 20 minutes, then switch to 30m Study Lecture
        val t20 = t0 + 20 * 60 * 1000L
        val m20 = m0 + 20 * 60 * 1000L
        val lecture = FocusTimerEngine.switchActivity(
            current = studyStarted,
            activityType = StudyActivityType.STUDY_LECTURE,
            activityLabel = "Watching a study lecture",
            category = ActivityCategory.LECTURE,
            durationMinutes = 30,
            subjectId = 7L,
            wasPlanned = true,
            nowEpochMs = t20,
            nowElapsedRealtimeMs = m20
        )

        assertTrue(lecture.isSwitchedActivity)
        assertEquals(StudyActivityType.STUDY_LECTURE, lecture.activeActivityType)
        assertFalse(
            "Lecture is tracked separately from pure focused study",
            lecture.activeActivityCategory.countsAsFocusedStudy
        )
        val parent = requireNotNull(lecture.pausedParentSession)
        assertEquals(40 * 60, (parent.totalSeconds - (parent.accumulatedFocusedMs / 1000L).toInt()))

        // Switch again to 20m Gaming -> original parent study session must STILL be preserved
        val t35 = t20 + 15 * 60 * 1000L
        val m35 = m20 + 15 * 60 * 1000L
        val gaming = FocusTimerEngine.switchActivity(
            current = lecture,
            activityType = StudyActivityType.PLAYING_GAME,
            activityLabel = "Playing a game",
            category = ActivityCategory.ENTERTAINMENT,
            durationMinutes = 20,
            subjectId = null,
            wasPlanned = false,
            nowEpochMs = t35,
            nowElapsedRealtimeMs = m35
        )
        assertFalse(gaming.activeActivityCategory.countsAsFocusedStudy)
        assertEquals(studyStarted.sessionId, gaming.pausedParentSession?.sessionId)

        // Extend gaming timer by +5m
        val extendedGaming = FocusTimerEngine.extendActiveTimer(
            snapshot = gaming,
            extraMinutes = 5,
            nowEpochMs = t35 + 20 * 60 * 1000L,
            nowElapsedRealtimeMs = m35 + 20 * 60 * 1000L
        )
        assertEquals(25 * 60, extendedGaming.totalSeconds)
        assertEquals(5 * 60, FocusTimerEngine.remainingSeconds(extendedGaming, t35 + 20 * 60 * 1000L, m35 + 20 * 60 * 1000L))

        // Return to Study -> resumes exact 40 minutes remaining of original 60m Studying session
        val tReturn = t35 + 25 * 60 * 1000L
        val mReturn = m35 + 25 * 60 * 1000L
        val resumedStudy = FocusTimerEngine.returnToPausedStudySession(
            current = extendedGaming,
            nowEpochMs = tReturn,
            nowElapsedRealtimeMs = mReturn
        )
        assertNotNull(resumedStudy)
        assertEquals(StudyActivityType.STUDYING, resumedStudy!!.activeActivityType)
        assertTrue(resumedStudy.activeActivityCategory.countsAsFocusedStudy)
        assertNull(resumedStudy.pausedParentSession)
        assertEquals(40 * 60, FocusTimerEngine.remainingSeconds(resumedStudy, tReturn, mReturn))
    }

    // -------------------------------------------------------------------------
    // PART 8 & 14: No Duplicate Session Records & No Duplicate Completion Alerts
    // -------------------------------------------------------------------------

    @Test
    fun `repository strictly deduplicates focus sessions and activity logs by sessionKey`() = runTest {
        val today = LocalDate.now().toEpochDay()
        val key = "session-dedup-001"
        val session = FocusSessionRecord(
            subjectId = null,
            subjectName = "Mathematics",
            subjectColorHex = 0xFF7B5CFA,
            durationSeconds = 25 * 60,
            plannedSeconds = 25 * 60,
            sessionGoal = "Matrix eigenvalues",
            mode = FocusMode.FOCUS,
            completedAtMs = System.currentTimeMillis(),
            dateEpochDay = today
        )

        // Simulate Foreground Service and ViewModel both completing the same session concurrently
        repository.recordFocusSessionAndGrowGarden(
            session = session,
            dailyTargetMinutes = 60,
            allExistingSessions = emptyList(),
            existingDecorations = emptySet(),
            sessionKey = key
        )
        repository.recordFocusSessionAndGrowGarden(
            session = session,
            dailyTargetMinutes = 60,
            allExistingSessions = listOf(session),
            existingDecorations = emptySet(),
            sessionKey = key
        )
        assertEquals("Focus session must not duplicate", 1, repository.focusSessions.first().size)
        assertEquals("Garden plant must grow only once", 1, repository.gardenPlants.first().first().sessionsLogged)

        val log = ActivityLogRecord(
            sessionKey = key,
            activityType = StudyActivityType.PLAYING_GAME,
            activityLabel = "Playing a game",
            category = ActivityCategory.ENTERTAINMENT,
            subjectId = null,
            subjectName = "General",
            subjectColorHex = 0xFFEF6C00,
            plannedSeconds = 15 * 60,
            actualSeconds = 15 * 60,
            startedAtMs = System.currentTimeMillis() - 900_000L,
            endedAtMs = System.currentTimeMillis(),
            dateEpochDay = today,
            wasPlanned = false
        )
        assertTrue(repository.recordActivityLog(log))
        assertFalse("Duplicate activity log with same sessionKey must be rejected", repository.recordActivityLog(log))
        assertEquals(1, repository.activityLogs.first().size)
    }

    // -------------------------------------------------------------------------
    // PART 3 & 9: 7 Notification Channels, Quiet Hours, Lock-Screen Privacy, Dedup
    // -------------------------------------------------------------------------

    @Test
    fun `notification manager creates all 7 channels, enforces quiet hours, privacy, actions, and dedup`() {
        BrainBufferNotificationManager.ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelIds = nm.notificationChannels.map { it.id }.toSet()

        assertTrue(BrainBufferNotificationManager.CHANNEL_ACTIVE_FOCUS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_BREAK_REMINDERS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_ACTIVITY_REMINDERS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_UPCOMING_TASKS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_EXAM_COUNTDOWNS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_STUDY_REMINDERS in channelIds)
        assertTrue(BrainBufferNotificationManager.CHANNEL_COMPLETED_SESSIONS in channelIds)

        // Quiet hours with midnight wrap-around (22:00 -> 07:00)
        val wrapPrefs = NotificationPreferences(
            quietHoursEnabled = true,
            quietStartHour = 22,
            quietEndHour = 7
        )
        assertTrue(BrainBufferNotificationManager.isWithinQuietHours(wrapPrefs, hourOfDay = 23))
        assertTrue(BrainBufferNotificationManager.isWithinQuietHours(wrapPrefs, hourOfDay = 3))
        assertFalse(BrainBufferNotificationManager.isWithinQuietHours(wrapPrefs, hourOfDay = 14))

        // Active timer notification with lock-screen privacy enabled hides private sessionGoal in publicVersion
        val running = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                sessionGoal = "Top secret essay draft",
                totalSeconds = 25 * 60
            ),
            nowEpochMs = 1_700_000_000_000L,
            nowElapsedRealtimeMs = 100_000L
        )
        val privacyPrefs = NotificationPreferences(hidePrivateNotesOnLockScreen = true)
        val notif = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = context,
            snapshot = running,
            subjectName = "History",
            prefs = privacyPrefs,
            nowEpochMs = 1_700_000_000_000L,
            nowElapsedRealtimeMs = 100_000L
        )
        assertEquals(Notification.VISIBILITY_PRIVATE, notif.visibility)
        assertNotNull(notif.publicVersion)
        val publicText = notif.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertFalse("Public lock-screen notification must never expose private goals", publicText.contains("Top secret"))
        val actionTitles = notif.actions.map { it.title.toString() }
        assertTrue("Running notification must expose Pause action", "Pause" in actionTitles)
        assertTrue("Running notification must expose End action", "End" in actionTitles)

        // Completion notification deduplication & quiet-hours gating
        val shadowApp = Shadows.shadowOf(context.applicationContext as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val postedFirst = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = running,
            subjectName = "History",
            prefs = NotificationPreferences(quietHoursEnabled = false),
            masterNotificationsEnabled = true,
            hourOfDay = 14
        )
        val postedSecond = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = running,
            subjectName = "History",
            prefs = NotificationPreferences(quietHoursEnabled = false),
            masterNotificationsEnabled = true,
            hourOfDay = 14
        )
        assertTrue("First completion alert should post", postedFirst)
        assertFalse("Duplicate completion alert for same session must be suppressed", postedSecond)

        // Quiet hours suppresses alerts
        val quietSuppressed = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = running.copy(sessionId = "quiet-session-id"),
            subjectName = "History",
            prefs = wrapPrefs,
            masterNotificationsEnabled = true,
            hourOfDay = 23
        )
        assertFalse("Quiet hours must suppress completion notifications", quietSuppressed)
    }

    @Test
    @Config(sdk = [29])
    fun `android 10 API 29 compatibility for notifications and performance inspection`() {
        val api29Context = ApplicationProvider.getApplicationContext<Context>()
        BrainBufferNotificationManager.ensureChannels(api29Context)
        val signals = PerformanceProfileEngine.inspectDevice(api29Context)
        assertEquals(29, signals.sdkInt)
        val tokens = RenderQualityTokens.forProfile(
            PerformanceProfileEngine.resolveProfile(signals),
            sdkInt = signals.sdkInt
        )
        assertFalse("Blur must never be enabled on Android 10 (API 29)", tokens.enableGlassBlur)

        val snapshot = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 20 * 60),
            nowEpochMs = 1_700_000_000_000L
        )
        val notif = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = api29Context,
            snapshot = snapshot,
            subjectName = "Physics",
            prefs = NotificationPreferences(),
            nowEpochMs = 1_700_000_000_000L
        )
        assertNotNull(notif)
        assertFalse(
            "API 36 Live Update extras must not be injected on Android 10",
            notif.extras.containsKey("android.requestPromotedOngoing")
        )
    }

    // -------------------------------------------------------------------------
    // PART 4: Voluntary Do Not Disturb Save & Exact Restore
    // -------------------------------------------------------------------------

    @Test
    fun `voluntary DND controller respects permission, enables priority mode, and restores exact prior filter`() = runTest {
        var currentFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        var accessGranted = false
        var savedFilter = DndFocusController.NO_SAVED_FILTER

        val fakeBackend = object : DndBackend {
            override fun isPolicyAccessGranted(): Boolean = accessGranted
            override fun getCurrentInterruptionFilter(): Int = currentFilter
            override fun setInterruptionFilter(filter: Int) {
                currentFilter = filter
            }
        }

        // Denied permission -> strict no-op
        val enabledWhenDenied = DndFocusController.enableFocusDndIfGranted(
            backend = fakeBackend,
            alreadySavedFilter = savedFilter,
            savePreviousFilter = { savedFilter = it }
        )
        assertFalse(enabledWhenDenied)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, currentFilter)
        assertEquals(DndFocusController.NO_SAVED_FILTER, savedFilter)

        // Granted permission with prior ALARMS filter -> saves ALARMS, switches to PRIORITY, then restores ALARMS
        accessGranted = true
        currentFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS
        val enabledWhenGranted = DndFocusController.enableFocusDndIfGranted(
            backend = fakeBackend,
            alreadySavedFilter = savedFilter,
            savePreviousFilter = { savedFilter = it }
        )
        assertTrue(enabledWhenGranted)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, savedFilter)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, currentFilter)

        val restored = DndFocusController.restorePreviousDnd(
            backend = fakeBackend,
            savedFilter = savedFilter,
            clearSavedFilter = { savedFilter = DndFocusController.NO_SAVED_FILTER }
        )
        assertTrue(restored)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, currentFilter)
        assertEquals(DndFocusController.NO_SAVED_FILTER, savedFilter)
    }

    // -------------------------------------------------------------------------
    // PART 6: Optional Local App-Usage Awareness Suggestions
    // -------------------------------------------------------------------------

    @Test
    fun `local app usage matcher produces calm activity suggestions without false positives`() {
        val youtube = AppUsageAwarenessHelper.matchPackageToSuggestion("com.google.android.youtube")
        assertEquals(StudyActivityType.STUDY_LECTURE, youtube?.suggestedActivity)
        assertEquals("YouTube", youtube?.appDisplayName)

        val notion = AppUsageAwarenessHelper.matchPackageToSuggestion("notion.id")
        assertEquals(StudyActivityType.WRITING_NOTES, notion?.suggestedActivity)

        val kindle = AppUsageAwarenessHelper.matchPackageToSuggestion("com.amazon.kindle")
        assertEquals(StudyActivityType.READING, kindle?.suggestedActivity)

        val minecraft = AppUsageAwarenessHelper.matchPackageToSuggestion("com.mojang.minecraftpe")
        assertEquals(StudyActivityType.PLAYING_GAME, minecraft?.suggestedActivity)

        val instagram = AppUsageAwarenessHelper.matchPackageToSuggestion("com.instagram.android")
        assertEquals(StudyActivityType.ENTERTAINMENT, instagram?.suggestedActivity)

        assertNull(AppUsageAwarenessHelper.matchPackageToSuggestion("com.android.calculator2"))
    }

    // -------------------------------------------------------------------------
    // PART 13: Safe Non-Destructive Room MIGRATION_1_2
    // -------------------------------------------------------------------------

    @Test
    fun `room MIGRATION_1_2 preserves existing v1 user data and creates v2 tables cleanly`() = runTest {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile: File = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create a real SQLite v1 schema with existing student data
        val openHelperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE subjects (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            colorHex INTEGER NOT NULL,
                            iconKey TEXT NOT NULL,
                            targetHoursPerWeek REAL NOT NULL,
                            examDateEpochDay INTEGER,
                            createdAtMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE study_tasks (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            subjectId INTEGER,
                            dueDateEpochDay INTEGER NOT NULL,
                            dueTimeLabel TEXT NOT NULL,
                            estimatedMinutes INTEGER NOT NULL,
                            priority TEXT NOT NULL,
                            isCompleted INTEGER NOT NULL,
                            completedAtMs INTEGER,
                            notes TEXT NOT NULL,
                            createdAtMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE focus_sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            subjectId INTEGER,
                            subjectName TEXT NOT NULL,
                            subjectColorHex INTEGER NOT NULL,
                            durationSeconds INTEGER NOT NULL,
                            plannedSeconds INTEGER NOT NULL,
                            sessionGoal TEXT NOT NULL,
                            mode TEXT NOT NULL,
                            completedAtMs INTEGER NOT NULL,
                            dateEpochDay INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE garden_plants (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            species TEXT NOT NULL,
                            nickname TEXT NOT NULL,
                            sessionsLogged INTEGER NOT NULL,
                            totalMinutes INTEGER NOT NULL,
                            plantedDateEpochDay INTEGER NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE mind_notes (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            type TEXT NOT NULL,
                            content TEXT NOT NULL,
                            moodTag TEXT NOT NULL,
                            createdAtMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO subjects (name, colorHex, iconKey, targetHoursPerWeek, examDateEpochDay, createdAtMs) VALUES ('Biology', 4286274810, 'science', 4.0, NULL, 1700000000000)"
                    )
                    db.execSQL(
                        "INSERT INTO focus_sessions (subjectId, subjectName, subjectColorHex, durationSeconds, plannedSeconds, sessionGoal, mode, completedAtMs, dateEpochDay) VALUES (1, 'Biology', 4286274810, 1500, 1500, 'Chapter 1', 'FOCUS', 1700000000000, 20000)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(openHelperConfig)
        v1Helper.writableDatabase.close()
        v1Helper.close()

        // 2. Open with Room v3 + MIGRATION_1_2 & MIGRATION_2_3 and verify zero data loss + working new tables
        val migratedDb = Room.databaseBuilder(context, BrainBufferDatabase::class.java, dbName)
            .addMigrations(BrainBufferDatabase.MIGRATION_1_2, BrainBufferDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val subjects = migratedDb.subjectDao().getAllOnce()
            assertEquals(1, subjects.size)
            assertEquals("Biology", subjects.first().name)

            val sessions = migratedDb.focusSessionDao().observeAll().first()
            assertEquals(1, sessions.size)
            assertEquals("Chapter 1", sessions.first().sessionGoal)
            assertEquals("", sessions.first().sessionKey)

            assertTrue(migratedDb.activityLogDao().observeAll().first().isEmpty())
            assertTrue(migratedDb.strictFocusPresetDao().observeAll().first().isEmpty())
            assertTrue(migratedDb.customActivityDao().observeAll().first().isEmpty())
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    // -------------------------------------------------------------------------
    // PART 3, 4, 10 & 12: Compose UI Verification (Light, Dark & Performance Mode)
    // -------------------------------------------------------------------------

    @Test
    fun `live pill expands and collapses cleanly in light theme with Performance profile`() {
        var pausedCalled = false
        val runningTimer = FocusTimerUiState(
            snapshot = PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                totalSeconds = 25 * 60,
                hasStarted = true,
                runningSinceEpochMs = 1_700_000_000_000L
            ),
            remainingSeconds = 18 * 60 + 30,
            progress = 0.26f
        )

        composeRule.setContent {
            BrainBufferTheme(
                themeMode = ThemeMode.LIGHT,
                performanceOverride = PerformanceProfileOverride.PERFORMANCE
            ) {
                BrainBufferLivePill(
                    timer = runningTimer,
                    subjectName = "Calculus",
                    onPause = { pausedCalled = true },
                    onResume = {},
                    onOpenFocus = {},
                    onSwitchActivity = {},
                    onReturnToStudy = {},
                    onExtendFiveMin = {},
                    onEndSession = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("brain_buffer_live_pill").assertIsDisplayed()
        composeRule.onNodeWithText("18:30").assertIsDisplayed()

        // Tap pill header to expand controls
        composeRule.onNodeWithTag("brain_buffer_live_pill_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_pill_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("live_pill_play_pause").performClick()
        composeRule.waitForIdle()
        assertTrue(pausedCalled)
    }

    @Test
    fun `strict focus AOD-inspired pure black display renders cleanly and supports exit in dark theme`() {
        val strictTimer = FocusTimerUiState(
            snapshot = PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                totalSeconds = 45 * 60,
                hasStarted = true,
                runningSinceEpochMs = 1_700_000_000_000L,
                isStrictFocus = true,
                strictFocusAodStyle = true,
                strictFocusStrictness = StrictnessLevel.GENTLE,
                activeActivityType = StudyActivityType.STUDYING,
                activeActivityLabel = "Studying"
            ),
            remainingSeconds = 42 * 60,
            progress = 0.06f
        )
        val uiState = BrainBufferUiState(
            subjects = listOf(Subject(id = 1L, name = "Algorithms", colorHex = 0xFF7B5CFA)),
            timer = strictTimer
        )

        composeRule.setContent {
            BrainBufferTheme(
                themeMode = ThemeMode.DARK,
                performanceOverride = PerformanceProfileOverride.BALANCED
            ) {
                StrictFocusScreen(
                    uiState = uiState,
                    onBack = {},
                    onStartStrictFocus = { _, _, _, _, _, _, _ -> },
                    onPause = {},
                    onResume = {},
                    onExtendFiveMin = {},
                    onToggleAodStyle = {},
                    onOpenActivitySwitcher = {},
                    onExitStrictFocus = {},
                    onSavePreset = {},
                    onDeletePreset = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("aod_inspired_focus_display").assertIsDisplayed()
        composeRule.onNodeWithText("42:00").assertIsDisplayed()
    }
}
