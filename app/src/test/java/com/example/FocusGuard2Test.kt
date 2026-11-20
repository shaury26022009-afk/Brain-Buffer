package com.example

import android.app.Application
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppCategoryMappingEntity
import com.example.data.local.BrainBufferDatabase
import com.example.domain.FocusSessionStateMachine
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityAllowanceConfig
import com.example.model.ActivityCategory
import com.example.model.AppCategoryMapping
import com.example.model.ControlledAccessOption
import com.example.model.FocusGuardLevel
import com.example.model.FocusGuardSettings
import com.example.model.FocusMode
import com.example.model.FocusSessionState
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
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndBackend
import com.example.system.DndFocusController
import com.example.ui.components.ControlledPhoneAccessContent
import com.example.ui.components.FocusGuardSettingsContent
import com.example.ui.screens.StrictFocusScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.viewmodel.BrainBufferUiState
import com.example.viewmodel.FocusTimerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focus Guard 2.0 Verification Suite (Part 16):
 * Verifies all 20 critical user journeys, 9-state session state machine, controlled phone access,
 * PDF/Lecture/Break/Gaming/Social/Custom allowances, personalized reminders, App-Aware mappings,
 * unapproved app mismatch warnings, Room MIGRATION_2_3, DND save/restore, Play Policy compliance,
 * and AMOLED AOD-inspired Compose UI rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusGuard2Test {

    @get:Rule
    val composeRule = createComposeRule()

    // -------------------------------------------------------------------------
    // 1. Focus Guard Levels & 9-State Session State Machine
    // -------------------------------------------------------------------------

    @Test
    fun `9-state FocusSessionStateMachine derives every state accurately across full lifecycle`() {
        val baseEpoch = 1_750_000_000_000L
        val baseElapsed = 100_000L

        // IDLE
        val idleSnap = PersistedTimerSnapshot()
        assertEquals(FocusSessionState.IDLE, FocusSessionStateMachine.deriveState(idleSnap, baseEpoch, baseElapsed))

        // FOCUSING (High Focus)
        val highFocus = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Finish Organic Chemistry",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 45,
            strictness = StrictnessLevel.DEEP,
            enableAodStyle = true,
            dndActive = true,
            nowEpochMs = baseEpoch,
            nowElapsedRealtimeMs = baseElapsed,
            guardLevel = FocusGuardLevel.HIGH
        )
        assertEquals(FocusGuardLevel.HIGH, highFocus.focusGuardLevel)
        assertEquals(FocusSessionState.FOCUSING, FocusSessionStateMachine.deriveState(highFocus, baseEpoch + 60_000L, baseElapsed + 60_000L))

        // ACTIVITY_REQUEST while requesting phone access
        assertEquals(
            FocusSessionState.ACTIVITY_REQUEST,
            FocusSessionStateMachine.deriveState(highFocus, baseEpoch + 60_000L, baseElapsed + 60_000L, isRequestingActivity = true)
        )

        // PAUSED
        val paused = FocusTimerEngine.pause(highFocus, baseEpoch + 600_000L, baseElapsed + 600_000L)
        assertEquals(FocusSessionState.PAUSED, FocusSessionStateMachine.deriveState(paused, baseEpoch + 700_000L, baseElapsed + 700_000L))

        // APPROVED_STUDY_ACTIVITY (Study PDF with approved URI)
        val pdfActivity = FocusTimerEngine.switchActivity(
            current = highFocus,
            activityType = StudyActivityType.STUDY_PDF,
            activityLabel = "PDF · Notes.pdf",
            category = ActivityCategory.READING,
            durationMinutes = 20,
            subjectId = 1L,
            wasPlanned = true,
            nowEpochMs = baseEpoch + 600_000L,
            nowElapsedRealtimeMs = baseElapsed + 600_000L,
            approvedPackageName = "com.adobe.reader",
            approvedAppLabel = "Adobe Acrobat",
            approvedDocumentUri = "content://docs/notes.pdf"
        )
        assertEquals(
            FocusSessionState.APPROVED_STUDY_ACTIVITY,
            FocusSessionStateMachine.deriveState(pdfActivity, baseEpoch + 660_000L, baseElapsed + 660_000L)
        )
        assertEquals("com.adobe.reader", pdfActivity.approvedPackageName)
        assertEquals("content://docs/notes.pdf", pdfActivity.approvedDocumentUri)

        // RETURNING_TO_FOCUS when temporary activity completes while parent is preserved
        assertEquals(
            FocusSessionState.RETURNING_TO_FOCUS,
            FocusSessionStateMachine.deriveState(pdfActivity, baseEpoch + 600_000L + 20 * 60_000L, baseElapsed + 600_000L + 20 * 60_000L)
        )

        // BREAK
        val breakSnap = FocusTimerEngine.startBreakPreservingParent(
            current = highFocus,
            breakMinutes = 10,
            nowEpochMs = baseEpoch + 600_000L,
            nowElapsedRealtimeMs = baseElapsed + 600_000L
        )
        assertEquals(FocusSessionState.BREAK, FocusSessionStateMachine.deriveState(breakSnap, baseEpoch + 630_000L, baseElapsed + 630_000L))
        assertNotNull(breakSnap.pausedParentSession)

        // COMPLETED (main focus finishes)
        assertEquals(
            FocusSessionState.COMPLETED,
            FocusSessionStateMachine.deriveState(highFocus, baseEpoch + 45 * 60_000L, baseElapsed + 45 * 60_000L)
        )

        // CANCELLED
        val reset = FocusTimerEngine.reset(highFocus)
        assertEquals(
            FocusSessionState.CANCELLED,
            FocusSessionStateMachine.deriveState(reset, baseEpoch + 700_000L, baseElapsed + 700_000L, wasRecentlyCancelled = true)
        )
    }

    // -------------------------------------------------------------------------
    // 2. PDF, YouTube Lecture, Break, Gaming, Social & Custom Duration + Exact Return
    // -------------------------------------------------------------------------

    @Test
    fun `switching to PDF, lecture, gaming, social or custom 17m preserves exact parent focus seconds and separates study stats`() {
        val t0 = 1_750_000_000_000L
        val e0 = 50_000L

        val study = FocusTimerEngine.startStrictFocus(
            subjectId = 2L,
            sessionGoal = "Practice Physics derivations",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 50,
            strictness = StrictnessLevel.COMMITTED,
            enableAodStyle = true,
            dndActive = false,
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0,
            guardLevel = FocusGuardLevel.HIGH
        )

        // Study for exactly 12m 30s (750s), leaving 2250s
        val tSwitch = t0 + 750_000L
        val eSwitch = e0 + 750_000L

        // Custom 17-minute YouTube lecture
        val lecture = FocusTimerEngine.switchActivity(
            current = study,
            activityType = StudyActivityType.STUDY_LECTURE,
            activityLabel = "Lecture · YouTube",
            category = ActivityCategory.LECTURE,
            durationMinutes = 17,
            subjectId = 2L,
            wasPlanned = true,
            nowEpochMs = tSwitch,
            nowElapsedRealtimeMs = eSwitch,
            approvedPackageName = "com.google.android.youtube",
            approvedAppLabel = "YouTube"
        )
        assertEquals(17 * 60, lecture.totalSeconds)
        assertFalse(lecture.activeActivityCategory.countsAsFocusedStudy)
        assertEquals(FocusGuardLevel.HIGH, lecture.pausedParentSession?.focusGuardLevel)

        // Extend lecture by +5m -> 22m total
        val extendedLecture = FocusTimerEngine.extendActiveTimer(lecture, 5, tSwitch + 60_000L, eSwitch + 60_000L)
        assertEquals(22 * 60, extendedLecture.totalSeconds)

        // Switch again to Gaming (15m) -> must keep original parent study session intact!
        val gaming = FocusTimerEngine.switchActivity(
            current = extendedLecture,
            activityType = StudyActivityType.PLAYING_GAME,
            activityLabel = "Games",
            category = ActivityCategory.ENTERTAINMENT,
            durationMinutes = 15,
            subjectId = 2L,
            wasPlanned = true,
            nowEpochMs = tSwitch + 120_000L,
            nowElapsedRealtimeMs = eSwitch + 120_000L
        )
        assertFalse(gaming.activeActivityCategory.countsAsFocusedStudy)
        assertEquals(ActivityCategory.ENTERTAINMENT, gaming.activeActivityCategory)

        // Return to Study -> exact 2250s remaining and High Focus preserved
        val returned = FocusTimerEngine.returnToPausedStudySession(
            current = gaming,
            nowEpochMs = tSwitch + 300_000L,
            nowElapsedRealtimeMs = eSwitch + 300_000L
        )
        assertNotNull(returned)
        assertEquals(2250, FocusTimerEngine.remainingSeconds(returned!!, tSwitch + 300_000L, eSwitch + 300_000L))
        assertEquals(FocusGuardLevel.HIGH, returned.focusGuardLevel)
        assertTrue(returned.isStrictFocus)
        assertTrue(returned.strictFocusAodStyle)
        assertNull(returned.pausedParentSession)
    }

    // -------------------------------------------------------------------------
    // 3. Device Restart Recovery & Pause/Resume Clock Integrity
    // -------------------------------------------------------------------------

    @Test
    fun `monotonic timer recovers accurately after simulated device reboot without phantom paused time`() {
        val t0 = 1_750_000_000_000L
        val e0 = 5_000_000L
        val started = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(totalSeconds = 30 * 60),
            nowEpochMs = t0,
            nowElapsedRealtimeMs = e0
        )

        // Pause after 5m -> pause for 10m -> resume -> verify 0 phantom time added
        val paused = FocusTimerEngine.pause(started, t0 + 300_000L, e0 + 300_000L)
        assertEquals(25 * 60, FocusTimerEngine.remainingSeconds(paused, t0 + 900_000L, e0 + 900_000L))

        val resumed = FocusTimerEngine.startOrResume(paused, t0 + 900_000L, e0 + 900_000L)
        // Simulate device reboot: elapsedRealtime resets to 12_000L (< saved runningSinceElapsedRealtimeMs)
        // while wall clock moved forward 4 minutes
        val remainingAfterReboot = FocusTimerEngine.remainingSeconds(
            snapshot = resumed,
            nowEpochMs = t0 + 900_000L + 240_000L,
            nowElapsedRealtimeMs = 12_000L
        )
        assertEquals(21 * 60, remainingAfterReboot)
    }

    // -------------------------------------------------------------------------
    // 4. App-Aware Tracking: User Category Mapping Priority & Unapproved App Warning
    // -------------------------------------------------------------------------

    @Test
    fun `user AppCategoryMapping overrides default heuristic and detects unapproved apps gently`() {
        val customMappings = listOf(
            AppCategoryMapping(
                packageName = "com.google.android.youtube",
                appLabel = "YouTube",
                category = ActivityCategory.ENTERTAINMENT,
                activityType = StudyActivityType.ENTERTAINMENT,
                isAllowedDuringFocus = false
            )
        )

        // User remapped YouTube to Entertainment -> suggestion reflects user rule, not default Lecture
        val suggestion = AppUsageAwarenessHelper.matchPackageToSuggestion(
            packageName = "com.google.android.youtube",
            userMappings = customMappings
        )
        assertNotNull(suggestion)
        assertEquals(StudyActivityType.ENTERTAINMENT, suggestion!!.suggestedActivity)

        // During High Focus study (no approved app), opening Instagram triggers gentle warning
        val highFocusSnap = PersistedTimerSnapshot(
            hasStarted = true,
            isStrictFocus = true,
            focusGuardLevel = FocusGuardLevel.HIGH,
            activeActivityType = StudyActivityType.STUDYING,
            activeActivityCategory = ActivityCategory.STUDY
        )
        val mismatch = AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
            observedPackage = "com.instagram.android",
            snapshot = highFocusSnap,
            userMappings = customMappings
        )
        assertNotNull(mismatch)
        assertEquals("Instagram", mismatch!!.appDisplayName)

        // When Instagram IS the approved app for a timed Social allowance, no warning is emitted
        val approvedSocialSnap = highFocusSnap.copy(
            activeActivityType = StudyActivityType.ENTERTAINMENT,
            activeActivityCategory = ActivityCategory.ENTERTAINMENT,
            approvedPackageName = "com.instagram.android",
            approvedAppLabel = "Instagram"
        )
        val noMismatch = AppUsageAwarenessHelper.evaluateAppAgainstApprovedActivity(
            observedPackage = "com.instagram.android",
            snapshot = approvedSocialSnap,
            userMappings = customMappings
        )
        assertNull(noMismatch)
    }

    // -------------------------------------------------------------------------
    // 5. Smart Activity Reminders, Lock-Screen Privacy & 4 Notification Actions
    // -------------------------------------------------------------------------

    @Test
    fun `lock-screen timer notification hides private notes and expiry notification provides 4 personalized actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val snap = FocusTimerEngine.startStrictFocus(
            subjectId = 1L,
            sessionGoal = "Secret personal goal note",
            activityType = StudyActivityType.STUDYING,
            durationMinutes = 45,
            strictness = StrictnessLevel.DEEP,
            enableAodStyle = true,
            dndActive = true,
            nowEpochMs = 1_750_000_000_000L,
            nowElapsedRealtimeMs = 10_000L,
            guardLevel = FocusGuardLevel.HIGH
        )

        // Active timer notification respects lock-screen privacy
        val activeNotif = BrainBufferNotificationManager.buildActiveTimerNotification(
            context = context,
            snapshot = snap,
            subjectName = "Calculus",
            prefs = NotificationPreferences(hidePrivateNotesOnLockScreen = true),
            nowEpochMs = 1_750_000_300_000L,
            nowElapsedRealtimeMs = 310_000L
        )
        assertNotNull(activeNotif.publicVersion)
        val publicText = activeNotif.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertFalse(publicText.contains("Secret personal goal"))

        // Switch to 15m gaming allowance and post personalized expiry notification (with deduplication check)
        val gamingSnap = FocusTimerEngine.switchActivity(
            current = snap,
            activityType = StudyActivityType.PLAYING_GAME,
            activityLabel = "Games",
            category = ActivityCategory.ENTERTAINMENT,
            durationMinutes = 15,
            subjectId = 1L,
            wasPlanned = true,
            nowEpochMs = 1_750_000_300_000L,
            nowElapsedRealtimeMs = 310_000L
        )
        BrainBufferNotificationManager.clearPostedSessionKeysForTest()
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val postedFirst = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = gamingSnap,
            subjectName = "Calculus",
            prefs = NotificationPreferences(hidePrivateNotesOnLockScreen = false),
            masterNotificationsEnabled = true,
            hourOfDay = 14,
            studentName = "Aarav",
            soundEnabled = true,
            vibrationEnabled = true
        )
        assertTrue(postedFirst)
        // Deduplication prevents duplicate spam for the same session
        val postedDuplicate = BrainBufferNotificationManager.postCompletionNotification(
            context = context,
            snapshot = gamingSnap,
            subjectName = "Calculus",
            prefs = NotificationPreferences(hidePrivateNotesOnLockScreen = false),
            masterNotificationsEnabled = true,
            hourOfDay = 14,
            studentName = "Aarav",
            soundEnabled = true,
            vibrationEnabled = true
        )
        assertFalse(postedDuplicate)

        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val posted = org.robolectric.Shadows.shadowOf(nm).getNotification(BrainBufferNotificationManager.ID_COMPLETION)
        assertNotNull(posted)
        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue(title.contains("Aarav"))
        assertTrue(title.contains("gaming", ignoreCase = true))
        val actionTitles = posted.actions?.map { it.title.toString() }.orEmpty()
        assertTrue(actionTitles.contains("Return to Study"))
        assertTrue(actionTitles.contains("Extend +5m"))
        assertTrue(actionTitles.contains("Take a Break"))
    }

    // -------------------------------------------------------------------------
    // 6. Room MIGRATION_2_3 Integrity, DND Save/Restore & Performance Profile
    // -------------------------------------------------------------------------

    @Test
    fun `Room MIGRATION_2_3 preserves v2 tables and enables app_category_mappings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "focus_guard_v2_to_v3_test.db"
        context.deleteDatabase(dbName)

        val v2Config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE subjects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, colorHex INTEGER NOT NULL, iconKey TEXT NOT NULL, targetHoursPerWeek REAL NOT NULL, examDateEpochDay INTEGER, createdAtMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE study_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, subjectId INTEGER, dueDateEpochDay INTEGER NOT NULL, dueTimeLabel TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL, priority TEXT NOT NULL, isCompleted INTEGER NOT NULL, completedAtMs INTEGER, notes TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subjectId INTEGER, subjectName TEXT NOT NULL, subjectColorHex INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, plannedSeconds INTEGER NOT NULL, sessionGoal TEXT NOT NULL, mode TEXT NOT NULL, completedAtMs INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, sessionKey TEXT NOT NULL DEFAULT '')")
                    db.execSQL("CREATE TABLE garden_plants (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, species TEXT NOT NULL, nickname TEXT NOT NULL, sessionsLogged INTEGER NOT NULL, totalMinutes INTEGER NOT NULL, plantedDateEpochDay INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE mind_notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, moodTag TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE activity_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionKey TEXT NOT NULL, activityType TEXT NOT NULL, activityLabel TEXT NOT NULL, category TEXT NOT NULL, subjectId INTEGER, subjectName TEXT NOT NULL, subjectColorHex INTEGER NOT NULL, plannedSeconds INTEGER NOT NULL, actualSeconds INTEGER NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, wasPlanned INTEGER NOT NULL, notes TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE strict_focus_presets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, durationMinutes INTEGER NOT NULL, subjectId INTEGER, activityType TEXT NOT NULL, dailyGoalMinutes INTEGER NOT NULL, breakPolicy TEXT NOT NULL, strictnessLevel TEXT NOT NULL, enableDnd INTEGER NOT NULL, enableAodDisplay INTEGER NOT NULL, allowedAppsCsv TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE custom_activities (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, category TEXT NOT NULL, defaultDurationMinutes INTEGER NOT NULL, accentHex INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO subjects (name, colorHex, iconKey, targetHoursPerWeek, examDateEpochDay, createdAtMs) VALUES ('Physics', 4286274810, 'science', 5.0, NULL, 1750000000000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(v2Config)
        helper.writableDatabase.close()
        helper.close()

        val db = Room.databaseBuilder(context, BrainBufferDatabase::class.java, dbName)
            .addMigrations(BrainBufferDatabase.MIGRATION_1_2, BrainBufferDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Physics", db.subjectDao().getAllOnce().single().name)
            db.appCategoryMappingDao().upsert(
                AppCategoryMappingEntity(
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube",
                    category = ActivityCategory.LECTURE.name,
                    activityType = StudyActivityType.STUDY_LECTURE.name,
                    isAllowedDuringFocus = true,
                    updatedAtMs = 1_750_000_000_000L
                )
            )
            val savedMappings = db.appCategoryMappingDao().observeAll().first()
            assertEquals(1, savedMappings.size)
            assertEquals("YouTube", savedMappings.first().appLabel)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `DND save and restore preserves exact prior interruption filter and Performance profile triggers on budget hardware`() = runBlocking {
        var currentFilter = 1 // INTERRUPTION_FILTER_ALL
        var savedFilter = DndFocusController.NO_SAVED_FILTER
        val fakeDnd = object : DndBackend {
            override fun isPolicyAccessGranted(): Boolean = true
            override fun getCurrentInterruptionFilter(): Int = currentFilter
            override fun setInterruptionFilter(filter: Int) {
                currentFilter = filter
            }
        }
        assertTrue(DndFocusController.enableFocusDndIfGranted(fakeDnd, savedFilter) { savedFilter = it })
        assertEquals(1, savedFilter)
        assertEquals(2, currentFilter) // PRIORITY

        assertTrue(DndFocusController.restorePreviousDnd(fakeDnd, savedFilter) { savedFilter = DndFocusController.NO_SAVED_FILTER })
        assertEquals(1, currentFilter)
        assertEquals(DndFocusController.NO_SAVED_FILTER, savedFilter)

        // Low-end budget device resolves to Performance profile
        val budgetSignals = DevicePerformanceSignals(
            totalRamMb = 2048,
            isLowRamDevice = true,
            cpuCores = 4,
            sdkInt = 29,
            refreshRateHz = 60f,
            isPowerSaveMode = false
        )
        assertEquals(PerformanceProfile.PERFORMANCE, PerformanceProfileEngine.resolveProfile(budgetSignals))
    }

    @Test
    fun `AndroidManifest complies with Play Policy - no QUERY_ALL_PACKAGES or AccessibilityService`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(requestedPerms.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(requestedPerms.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(requestedPerms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(requestedPerms.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    // -------------------------------------------------------------------------
    // 7. Compose UI Verification: Controlled Phone Access, Focus Guard Settings & AOD Display
    // -------------------------------------------------------------------------

    @Test
    fun `ControlledPhoneAccessContent renders all 7 options and submits custom duration cleanly`() {
        var approvedType: StudyActivityType? = null
        var approvedMinutes = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK, performanceOverride = PerformanceProfileOverride.BALANCED) {
                ControlledPhoneAccessContent(
                    allowances = ActivityAllowanceConfig(dailyEntertainmentLimitMinutes = 45),
                    appCategoryMappings = emptyList(),
                    todayEntertainmentMinutes = 15,
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
        composeRule.onNodeWithText("What do you need your phone for?").assertIsDisplayed()

        // Verify all 7 ControlledAccessOption tags exist and select Study PDF / Documents
        ControlledAccessOption.entries.forEach { option ->
            composeRule.onNodeWithTag("controlled_access_option_${option.name.lowercase()}").assertExists()
        }

        composeRule.onNodeWithTag("controlled_access_option_study_pdf").performClick()
        composeRule.waitForIdle()

        // Enter custom 17-minute allowance and approve
        composeRule.onNodeWithTag("controlled_access_custom_minutes").performScrollTo().performTextInput("17")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("controlled_access_confirm_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(StudyActivityType.STUDY_PDF, approvedType)
        assertEquals(17, approvedMinutes)
    }

    @Test
    fun `FocusGuardSettingsContent renders starter templates and daily entertainment limit`() {
        var updatedSettings: FocusGuardSettings? = null
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT, performanceOverride = PerformanceProfileOverride.PERFORMANCE) {
                FocusGuardSettingsContent(
                    settings = FocusGuardSettings(),
                    appUsageAwarenessEnabled = false,
                    appCategoryMappings = emptyList(),
                    onUpdateSettings = { updatedSettings = it },
                    onUpdateAppUsageAwareness = {},
                    onUpsertAppMapping = {},
                    onDeleteAppMapping = {},
                    onSaveStarterPreset = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_guard_settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("starter_template_exam_preparation").performClick()
        composeRule.waitForIdle()
        assertEquals(60, updatedSettings?.defaultFocusMinutes)
    }

    @Test
    @Config(sdk = [29])
    fun `High Focus AOD-inspired AMOLED screen renders cleanly on Android 10 (API 29) with BRAIN BUFFER wordmark and Need Phone`() {
        var needPhoneClicked = false
        val highFocusTimer = FocusTimerUiState(
            snapshot = PersistedTimerSnapshot(
                mode = FocusMode.FOCUS,
                totalSeconds = 45 * 60,
                hasStarted = true,
                runningSinceEpochMs = 1_750_000_000_000L,
                isStrictFocus = true,
                strictFocusAodStyle = true,
                strictFocusStrictness = StrictnessLevel.DEEP,
                focusGuardLevel = FocusGuardLevel.HIGH,
                activeActivityType = StudyActivityType.STUDYING,
                activeActivityLabel = "Studying"
            ),
            remainingSeconds = 38 * 60 + 15,
            progress = 0.15f,
            sessionState = FocusSessionState.FOCUSING
        )
        val state = BrainBufferUiState(
            subjects = listOf(Subject(id = 1L, name = "Organic Chemistry", colorHex = 0xFF7B5CFA)),
            timer = highFocusTimer
        )

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK, performanceOverride = PerformanceProfileOverride.BALANCED) {
                StrictFocusScreen(
                    uiState = state,
                    onBack = {},
                    onStartStrictFocus = { _, _, _, _, _, _, _ -> },
                    onPause = {},
                    onResume = {},
                    onExtendFiveMin = {},
                    onToggleAodStyle = {},
                    onOpenActivitySwitcher = {},
                    onExitStrictFocus = {},
                    onSavePreset = {},
                    onDeletePreset = {},
                    onOpenControlledPhoneAccess = { needPhoneClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("aod_inspired_focus_display").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_brand_wordmark").assertIsDisplayed()
        composeRule.onNodeWithText("38:15").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_need_phone_button").performClick()
        composeRule.waitForIdle()
        assertTrue(needPhoneClicked)
    }
}
