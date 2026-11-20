package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ActivityAllowanceConfig
import com.example.model.ActivityCategory
import com.example.model.FocusGuardLevel
import com.example.model.FocusGuardSettings
import com.example.model.FocusMode
import com.example.model.NotificationPreferences
import com.example.model.PausedParentSession
import com.example.model.PersistedTimerSnapshot
import com.example.model.PreferredStudyTime
import com.example.model.StrictnessLevel
import com.example.model.StudentAvatar
import com.example.model.StudyActivityType
import com.example.model.StudyLevel
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.performance.PerformanceProfileOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

private val Context.brainBufferDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "brain_buffer_preferences"
)

class UserPreferencesStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.brainBufferDataStore)

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val AVATAR_KEY = stringPreferencesKey("avatar_key")
        val STUDY_LEVEL = stringPreferencesKey("study_level")
        val COURSE_OR_EXAM = stringPreferencesKey("course_or_exam")
        val STUDY_GOALS = stringSetPreferencesKey("study_goals")
        val CUSTOM_GOAL = stringPreferencesKey("custom_goal")
        val PREFERRED_FOCUS_MIN = intPreferencesKey("preferred_focus_min")
        val SHORT_BREAK_MIN = intPreferencesKey("short_break_min")
        val LONG_BREAK_MIN = intPreferencesKey("long_break_min")
        val PREFERRED_STUDY_TIME = stringPreferencesKey("preferred_study_time")
        val WEEKLY_STUDY_DAYS = intPreferencesKey("weekly_study_days")
        val BIGGEST_DISTRACTIONS = stringSetPreferencesKey("biggest_distractions")
        val OTHER_DISTRACTION = stringPreferencesKey("other_distraction")
        val DAILY_TARGET_MIN = intPreferencesKey("daily_target_min")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val BREAK_REMINDERS_ENABLED = booleanPreferencesKey("break_reminders_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCED_MOTION_OVERRIDE = booleanPreferencesKey("reduced_motion_override")
        val SEEDS_OWNED = intPreferencesKey("seeds_owned")
        val UNLOCKED_DECORATIONS = stringSetPreferencesKey("unlocked_decorations")
        val MIND_RESET_COUNT = intPreferencesKey("mind_reset_count")

        // Adaptive performance, notifications & privacy keys
        val PERFORMANCE_OVERRIDE = stringPreferencesKey("performance_override")
        val NOTIF_ACTIVE_FOCUS = booleanPreferencesKey("notif_active_focus")
        val NOTIF_ACTIVITY_REMINDERS = booleanPreferencesKey("notif_activity_reminders")
        val NOTIF_UPCOMING_TASKS = booleanPreferencesKey("notif_upcoming_tasks")
        val NOTIF_EXAM_COUNTDOWNS = booleanPreferencesKey("notif_exam_countdowns")
        val NOTIF_STUDY_REMINDERS = booleanPreferencesKey("notif_study_reminders")
        val NOTIF_COMPLETED_SESSIONS = booleanPreferencesKey("notif_completed_sessions")
        val NOTIF_HIDE_PRIVATE_LOCKSCREEN = booleanPreferencesKey("notif_hide_private_lockscreen")
        val NOTIF_QUIET_HOURS_ENABLED = booleanPreferencesKey("notif_quiet_hours_enabled")
        val NOTIF_QUIET_START_HOUR = intPreferencesKey("notif_quiet_start_hour")
        val NOTIF_QUIET_END_HOUR = intPreferencesKey("notif_quiet_end_hour")
        val APP_USAGE_AWARENESS_ENABLED = booleanPreferencesKey("app_usage_awareness_enabled")
        val AOD_FOCUS_DISPLAY_ENABLED = booleanPreferencesKey("aod_focus_display_enabled")
        val SAVED_DND_INTERRUPTION_FILTER = intPreferencesKey("saved_dnd_interruption_filter")

        // Reliable monotonic + wall-clock timer snapshot keys
        val TIMER_MODE = stringPreferencesKey("timer_mode")
        val TIMER_SUBJECT_ID = longPreferencesKey("timer_subject_id")
        val TIMER_SESSION_GOAL = stringPreferencesKey("timer_session_goal")
        val TIMER_TOTAL_SECONDS = intPreferencesKey("timer_total_seconds")
        val TIMER_ACCUMULATED_MS = longPreferencesKey("timer_accumulated_ms")
        val TIMER_RUNNING_SINCE_MS = longPreferencesKey("timer_running_since_ms")
        val TIMER_RUNNING_SINCE_ELAPSED_MS = longPreferencesKey("timer_running_since_elapsed_ms")
        val TIMER_HAS_STARTED = booleanPreferencesKey("timer_has_started")
        val TIMER_SESSION_ID = stringPreferencesKey("timer_session_id")
        val TIMER_ACTIVITY_TYPE = stringPreferencesKey("timer_activity_type")
        val TIMER_ACTIVITY_LABEL = stringPreferencesKey("timer_activity_label")
        val TIMER_ACTIVITY_CATEGORY = stringPreferencesKey("timer_activity_category")
        val TIMER_ACTIVITY_STARTED_MS = longPreferencesKey("timer_activity_started_ms")
        val TIMER_ACTIVITY_WAS_PLANNED = booleanPreferencesKey("timer_activity_was_planned")
        val TIMER_IS_STRICT_FOCUS = booleanPreferencesKey("timer_is_strict_focus")
        val TIMER_STRICT_AOD = booleanPreferencesKey("timer_strict_aod")
        val TIMER_STRICT_DND_ACTIVE = booleanPreferencesKey("timer_strict_dnd_active")
        val TIMER_STRICT_LEVEL = stringPreferencesKey("timer_strict_level")
        val TIMER_PAUSED_PARENT_JSON = stringPreferencesKey("timer_paused_parent_json")
        val TIMER_GUARD_LEVEL = stringPreferencesKey("timer_guard_level")
        val TIMER_APPROVED_PACKAGE = stringPreferencesKey("timer_approved_package")
        val TIMER_APPROVED_APP_LABEL = stringPreferencesKey("timer_approved_app_label")
        val TIMER_APPROVED_DOC_URI = stringPreferencesKey("timer_approved_doc_uri")

        // Focus Guard 3.0 Settings & distraction counters (JSON encoded cleanly so defaults evolve safely)
        val FOCUS_GUARD_SETTINGS_JSON = stringPreferencesKey("focus_guard_settings_json")
        val NOTIF_DISTRACTION_WARNINGS = booleanPreferencesKey("notif_distraction_warnings")
        val NOTIF_TEMP_ACTIVITY_TIMER = booleanPreferencesKey("notif_temp_activity_timer")
        val DISTRACTION_WARNINGS_TODAY = intPreferencesKey("distraction_warnings_today")
        val DISTRACTION_WARNINGS_EPOCH_DAY = longPreferencesKey("distraction_warnings_epoch_day")
        val DISTRACTION_WARNINGS_WEEK = intPreferencesKey("distraction_warnings_week")
        val DISTRACTION_WARNINGS_WEEK_START = longPreferencesKey("distraction_warnings_week_start")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val masterNotif = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
            val breakNotif = prefs[Keys.BREAK_REMINDERS_ENABLED] ?: true
            UserPreferences(
                isLoaded = true,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                studentName = prefs[Keys.STUDENT_NAME] ?: "",
                avatarKey = prefs[Keys.AVATAR_KEY] ?: StudentAvatar.SPARK.key,
                studyLevel = prefs[Keys.STUDY_LEVEL]?.let { name ->
                    StudyLevel.entries.firstOrNull { it.name == name }
                } ?: StudyLevel.UNIVERSITY,
                courseOrExamName = prefs[Keys.COURSE_OR_EXAM] ?: "",
                studyGoals = prefs[Keys.STUDY_GOALS] ?: emptySet(),
                customGoal = prefs[Keys.CUSTOM_GOAL] ?: "",
                preferredFocusMinutes = prefs[Keys.PREFERRED_FOCUS_MIN] ?: 25,
                shortBreakMinutes = prefs[Keys.SHORT_BREAK_MIN] ?: 5,
                longBreakMinutes = prefs[Keys.LONG_BREAK_MIN] ?: 15,
                preferredStudyTime = prefs[Keys.PREFERRED_STUDY_TIME]?.let { name ->
                    PreferredStudyTime.entries.firstOrNull { it.name == name }
                } ?: PreferredStudyTime.EVENING,
                weeklyStudyDaysTarget = prefs[Keys.WEEKLY_STUDY_DAYS] ?: 5,
                biggestDistractions = prefs[Keys.BIGGEST_DISTRACTIONS] ?: emptySet(),
                otherDistraction = prefs[Keys.OTHER_DISTRACTION] ?: "",
                dailyTargetMinutes = prefs[Keys.DAILY_TARGET_MIN] ?: 60,
                themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                    ThemeMode.entries.firstOrNull { it.name == name }
                } ?: ThemeMode.SYSTEM,
                notificationsEnabled = masterNotif,
                breakRemindersEnabled = breakNotif,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
                reducedMotionOverride = prefs[Keys.REDUCED_MOTION_OVERRIDE] ?: false,
                seedsOwned = prefs[Keys.SEEDS_OWNED] ?: 1,
                unlockedDecorations = prefs[Keys.UNLOCKED_DECORATIONS] ?: emptySet(),
                mindResetCount = prefs[Keys.MIND_RESET_COUNT] ?: 0,
                performanceOverride = prefs[Keys.PERFORMANCE_OVERRIDE]?.let { name ->
                    PerformanceProfileOverride.entries.firstOrNull { it.name == name }
                } ?: PerformanceProfileOverride.AUTO,
                notificationPrefs = NotificationPreferences(
                    activeFocusEnabled = prefs[Keys.NOTIF_ACTIVE_FOCUS] ?: true,
                    breakRemindersEnabled = breakNotif,
                    activityRemindersEnabled = prefs[Keys.NOTIF_ACTIVITY_REMINDERS] ?: true,
                    tempActivityTimerEnabled = prefs[Keys.NOTIF_TEMP_ACTIVITY_TIMER] ?: true,
                    distractionWarningsEnabled = prefs[Keys.NOTIF_DISTRACTION_WARNINGS] ?: true,
                    upcomingTasksEnabled = prefs[Keys.NOTIF_UPCOMING_TASKS] ?: true,
                    examCountdownsEnabled = prefs[Keys.NOTIF_EXAM_COUNTDOWNS] ?: true,
                    studyRemindersEnabled = prefs[Keys.NOTIF_STUDY_REMINDERS] ?: masterNotif,
                    completedSessionsEnabled = prefs[Keys.NOTIF_COMPLETED_SESSIONS] ?: true,
                    hidePrivateNotesOnLockScreen = prefs[Keys.NOTIF_HIDE_PRIVATE_LOCKSCREEN] ?: true,
                    quietHoursEnabled = prefs[Keys.NOTIF_QUIET_HOURS_ENABLED] ?: false,
                    quietStartHour = (prefs[Keys.NOTIF_QUIET_START_HOUR] ?: 22).coerceIn(0, 23),
                    quietEndHour = (prefs[Keys.NOTIF_QUIET_END_HOUR] ?: 7).coerceIn(0, 23)
                ),
                appUsageAwarenessEnabled = prefs[Keys.APP_USAGE_AWARENESS_ENABLED] ?: false,
                aodFocusDisplayEnabled = prefs[Keys.AOD_FOCUS_DISPLAY_ENABLED] ?: false,
                savedDndInterruptionFilter = prefs[Keys.SAVED_DND_INTERRUPTION_FILTER] ?: -1,
                focusGuardSettings = decodeFocusGuardSettings(prefs[Keys.FOCUS_GUARD_SETTINGS_JSON]),
                distractionWarningsToday = run {
                    val today = java.time.LocalDate.now().toEpochDay()
                    if ((prefs[Keys.DISTRACTION_WARNINGS_EPOCH_DAY] ?: -1L) == today) {
                        prefs[Keys.DISTRACTION_WARNINGS_TODAY] ?: 0
                    } else 0
                },
                distractionWarningsWeek = run {
                    val today = java.time.LocalDate.now().toEpochDay()
                    val weekStart = prefs[Keys.DISTRACTION_WARNINGS_WEEK_START] ?: -1L
                    if (weekStart > 0L && today - weekStart in 0L..6L) {
                        prefs[Keys.DISTRACTION_WARNINGS_WEEK] ?: 0
                    } else 0
                }
            )
        }

    val timerSnapshotFlow: Flow<PersistedTimerSnapshot> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val defaultFocusMin = prefs[Keys.PREFERRED_FOCUS_MIN] ?: 25
            val storedSubject = prefs[Keys.TIMER_SUBJECT_ID] ?: -1L
            val storedRunningSince = prefs[Keys.TIMER_RUNNING_SINCE_MS] ?: -1L
            val storedRunningElapsed = prefs[Keys.TIMER_RUNNING_SINCE_ELAPSED_MS] ?: -1L
            val storedActivityStarted = prefs[Keys.TIMER_ACTIVITY_STARTED_MS] ?: -1L
            val activityType = prefs[Keys.TIMER_ACTIVITY_TYPE]?.let { name ->
                StudyActivityType.entries.firstOrNull { it.name == name }
            } ?: StudyActivityType.STUDYING
            val activityCategory = prefs[Keys.TIMER_ACTIVITY_CATEGORY]?.let { name ->
                ActivityCategory.entries.firstOrNull { it.name == name }
            } ?: activityType.category

            PersistedTimerSnapshot(
                mode = prefs[Keys.TIMER_MODE]?.let { name ->
                    FocusMode.entries.firstOrNull { it.name == name }
                } ?: FocusMode.FOCUS,
                selectedSubjectId = if (storedSubject > 0L) storedSubject else null,
                sessionGoal = prefs[Keys.TIMER_SESSION_GOAL] ?: "",
                totalSeconds = prefs[Keys.TIMER_TOTAL_SECONDS] ?: (defaultFocusMin * 60),
                accumulatedFocusedMs = prefs[Keys.TIMER_ACCUMULATED_MS] ?: 0L,
                runningSinceEpochMs = if (storedRunningSince > 0L) storedRunningSince else null,
                runningSinceElapsedRealtimeMs = if (storedRunningElapsed >= 0L) storedRunningElapsed else null,
                hasStarted = prefs[Keys.TIMER_HAS_STARTED] ?: false,
                sessionId = prefs[Keys.TIMER_SESSION_ID] ?: "",
                activeActivityType = activityType,
                activeActivityLabel = prefs[Keys.TIMER_ACTIVITY_LABEL]?.ifBlank { activityType.label } ?: activityType.label,
                activeActivityCategory = activityCategory,
                activityStartedAtEpochMs = if (storedActivityStarted > 0L) storedActivityStarted else null,
                activityWasPlanned = prefs[Keys.TIMER_ACTIVITY_WAS_PLANNED] ?: true,
                isStrictFocus = prefs[Keys.TIMER_IS_STRICT_FOCUS] ?: false,
                strictFocusAodStyle = prefs[Keys.TIMER_STRICT_AOD] ?: false,
                strictFocusDndActive = prefs[Keys.TIMER_STRICT_DND_ACTIVE] ?: false,
                strictFocusStrictness = prefs[Keys.TIMER_STRICT_LEVEL]?.let { name ->
                    StrictnessLevel.entries.firstOrNull { it.name == name }
                } ?: StrictnessLevel.COMMITTED,
                pausedParentSession = decodePausedParent(prefs[Keys.TIMER_PAUSED_PARENT_JSON]),
                focusGuardLevel = prefs[Keys.TIMER_GUARD_LEVEL]?.let { name ->
                    FocusGuardLevel.entries.firstOrNull { it.name == name }
                } ?: if (prefs[Keys.TIMER_IS_STRICT_FOCUS] == true) FocusGuardLevel.HIGH else FocusGuardLevel.NORMAL,
                approvedPackageName = prefs[Keys.TIMER_APPROVED_PACKAGE] ?: "",
                approvedAppLabel = prefs[Keys.TIMER_APPROVED_APP_LABEL] ?: "",
                approvedDocumentUri = prefs[Keys.TIMER_APPROVED_DOC_URI] ?: ""
            )
        }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesFlow.first()
    suspend fun getTimerSnapshotOnce(): PersistedTimerSnapshot = timerSnapshotFlow.first()

    suspend fun saveOnboardingPreferences(
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
            prefs[Keys.STUDENT_NAME] = studentName.trim()
            prefs[Keys.AVATAR_KEY] = avatarKey
            prefs[Keys.STUDY_LEVEL] = studyLevel.name
            prefs[Keys.COURSE_OR_EXAM] = courseOrExamName.trim()
            prefs[Keys.STUDY_GOALS] = studyGoals
            prefs[Keys.CUSTOM_GOAL] = customGoal.trim()
            prefs[Keys.PREFERRED_FOCUS_MIN] = preferredFocusMinutes.coerceIn(5, 120)
            prefs[Keys.SHORT_BREAK_MIN] = shortBreakMinutes.coerceIn(2, 30)
            prefs[Keys.PREFERRED_STUDY_TIME] = preferredStudyTime.name
            prefs[Keys.WEEKLY_STUDY_DAYS] = weeklyStudyDaysTarget.coerceIn(1, 7)
            prefs[Keys.BIGGEST_DISTRACTIONS] = biggestDistractions
            prefs[Keys.OTHER_DISTRACTION] = otherDistraction.trim()
            prefs[Keys.DAILY_TARGET_MIN] = dailyTargetMinutes.coerceIn(15, 480)
            if ((prefs[Keys.TIMER_HAS_STARTED] ?: false).not()) {
                prefs[Keys.TIMER_TOTAL_SECONDS] = preferredFocusMinutes.coerceIn(5, 120) * 60
            }
        }
    }

    suspend fun updateProfile(
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.STUDENT_NAME] = studentName.trim()
            prefs[Keys.AVATAR_KEY] = avatarKey
            prefs[Keys.STUDY_LEVEL] = studyLevel.name
            prefs[Keys.COURSE_OR_EXAM] = courseOrExamName.trim()
            prefs[Keys.STUDY_GOALS] = studyGoals
            prefs[Keys.CUSTOM_GOAL] = customGoal.trim()
            prefs[Keys.PREFERRED_STUDY_TIME] = preferredStudyTime.name
            prefs[Keys.WEEKLY_STUDY_DAYS] = weeklyStudyDaysTarget.coerceIn(1, 7)
            prefs[Keys.BIGGEST_DISTRACTIONS] = biggestDistractions
            prefs[Keys.OTHER_DISTRACTION] = otherDistraction.trim()
        }
    }

    suspend fun updateTimerAndTargetPrefs(
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyTargetMinutes: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_FOCUS_MIN] = preferredFocusMinutes.coerceIn(5, 120)
            prefs[Keys.SHORT_BREAK_MIN] = shortBreakMinutes.coerceIn(2, 30)
            prefs[Keys.LONG_BREAK_MIN] = longBreakMinutes.coerceIn(5, 60)
            prefs[Keys.DAILY_TARGET_MIN] = dailyTargetMinutes.coerceIn(15, 480)
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updatePerformanceOverride(override: PerformanceProfileOverride) {
        dataStore.edit { it[Keys.PERFORMANCE_OVERRIDE] = override.name }
    }

    suspend fun updateNotificationPreferences(notificationPrefs: NotificationPreferences) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIF_ACTIVE_FOCUS] = notificationPrefs.activeFocusEnabled
            prefs[Keys.BREAK_REMINDERS_ENABLED] = notificationPrefs.breakRemindersEnabled
            prefs[Keys.NOTIF_ACTIVITY_REMINDERS] = notificationPrefs.activityRemindersEnabled
            prefs[Keys.NOTIF_TEMP_ACTIVITY_TIMER] = notificationPrefs.tempActivityTimerEnabled
            prefs[Keys.NOTIF_DISTRACTION_WARNINGS] = notificationPrefs.distractionWarningsEnabled
            prefs[Keys.NOTIF_UPCOMING_TASKS] = notificationPrefs.upcomingTasksEnabled
            prefs[Keys.NOTIF_EXAM_COUNTDOWNS] = notificationPrefs.examCountdownsEnabled
            prefs[Keys.NOTIF_STUDY_REMINDERS] = notificationPrefs.studyRemindersEnabled
            prefs[Keys.NOTIFICATIONS_ENABLED] = notificationPrefs.studyRemindersEnabled
            prefs[Keys.NOTIF_COMPLETED_SESSIONS] = notificationPrefs.completedSessionsEnabled
            prefs[Keys.NOTIF_HIDE_PRIVATE_LOCKSCREEN] = notificationPrefs.hidePrivateNotesOnLockScreen
            prefs[Keys.NOTIF_QUIET_HOURS_ENABLED] = notificationPrefs.quietHoursEnabled
            prefs[Keys.NOTIF_QUIET_START_HOUR] = notificationPrefs.quietStartHour.coerceIn(0, 23)
            prefs[Keys.NOTIF_QUIET_END_HOUR] = notificationPrefs.quietEndHour.coerceIn(0, 23)
        }
    }

    suspend fun updateAppUsageAwareness(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_USAGE_AWARENESS_ENABLED] = enabled }
    }

    suspend fun updateAodFocusDisplay(enabled: Boolean) {
        dataStore.edit { it[Keys.AOD_FOCUS_DISPLAY_ENABLED] = enabled }
    }

    suspend fun updateSavedDndInterruptionFilter(filter: Int) {
        dataStore.edit { it[Keys.SAVED_DND_INTERRUPTION_FILTER] = filter }
    }

    suspend fun updateFocusGuardSettings(settings: FocusGuardSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.FOCUS_GUARD_SETTINGS_JSON] = encodeFocusGuardSettings(settings)
            prefs[Keys.SOUND_ENABLED] = settings.notificationSoundEnabled
            prefs[Keys.HAPTICS_ENABLED] = settings.vibrationEnabled
            prefs[Keys.NOTIF_DISTRACTION_WARNINGS] = settings.distractionWarningsEnabled
        }
    }

    suspend fun recordDistractionWarning(todayEpochDay: Long = java.time.LocalDate.now().toEpochDay()) {
        dataStore.edit { prefs ->
            val savedDay = prefs[Keys.DISTRACTION_WARNINGS_EPOCH_DAY] ?: -1L
            val nextToday = if (savedDay == todayEpochDay) {
                (prefs[Keys.DISTRACTION_WARNINGS_TODAY] ?: 0) + 1
            } else {
                1
            }
            prefs[Keys.DISTRACTION_WARNINGS_EPOCH_DAY] = todayEpochDay
            prefs[Keys.DISTRACTION_WARNINGS_TODAY] = nextToday

            val savedWeekStart = prefs[Keys.DISTRACTION_WARNINGS_WEEK_START] ?: -1L
            val withinWeek = savedWeekStart > 0L && (todayEpochDay - savedWeekStart) in 0L..6L
            if (withinWeek) {
                prefs[Keys.DISTRACTION_WARNINGS_WEEK] = (prefs[Keys.DISTRACTION_WARNINGS_WEEK] ?: 0) + 1
            } else {
                prefs[Keys.DISTRACTION_WARNINGS_WEEK_START] = todayEpochDay
                prefs[Keys.DISTRACTION_WARNINGS_WEEK] = 1
            }
        }
    }

    suspend fun updateAppSettings(
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = notificationsEnabled
            prefs[Keys.NOTIF_STUDY_REMINDERS] = notificationsEnabled
            prefs[Keys.BREAK_REMINDERS_ENABLED] = breakRemindersEnabled
            prefs[Keys.SOUND_ENABLED] = soundEnabled
            prefs[Keys.HAPTICS_ENABLED] = hapticsEnabled
            prefs[Keys.REDUCED_MOTION_OVERRIDE] = reducedMotionOverride
        }
    }

    suspend fun saveTimerSnapshot(snapshot: PersistedTimerSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.TIMER_MODE] = snapshot.mode.name
            prefs[Keys.TIMER_SUBJECT_ID] = snapshot.selectedSubjectId ?: -1L
            prefs[Keys.TIMER_SESSION_GOAL] = snapshot.sessionGoal
            prefs[Keys.TIMER_TOTAL_SECONDS] = snapshot.totalSeconds
            prefs[Keys.TIMER_ACCUMULATED_MS] = snapshot.accumulatedFocusedMs
            prefs[Keys.TIMER_RUNNING_SINCE_MS] = snapshot.runningSinceEpochMs ?: -1L
            prefs[Keys.TIMER_RUNNING_SINCE_ELAPSED_MS] = snapshot.runningSinceElapsedRealtimeMs ?: -1L
            prefs[Keys.TIMER_HAS_STARTED] = snapshot.hasStarted
            prefs[Keys.TIMER_SESSION_ID] = snapshot.sessionId
            prefs[Keys.TIMER_ACTIVITY_TYPE] = snapshot.activeActivityType.name
            prefs[Keys.TIMER_ACTIVITY_LABEL] = snapshot.activeActivityLabel
            prefs[Keys.TIMER_ACTIVITY_CATEGORY] = snapshot.activeActivityCategory.name
            prefs[Keys.TIMER_ACTIVITY_STARTED_MS] = snapshot.activityStartedAtEpochMs ?: -1L
            prefs[Keys.TIMER_ACTIVITY_WAS_PLANNED] = snapshot.activityWasPlanned
            prefs[Keys.TIMER_IS_STRICT_FOCUS] = snapshot.isStrictFocus
            prefs[Keys.TIMER_STRICT_AOD] = snapshot.strictFocusAodStyle
            prefs[Keys.TIMER_STRICT_DND_ACTIVE] = snapshot.strictFocusDndActive
            prefs[Keys.TIMER_STRICT_LEVEL] = snapshot.strictFocusStrictness.name
            prefs[Keys.TIMER_PAUSED_PARENT_JSON] = encodePausedParent(snapshot.pausedParentSession)
            prefs[Keys.TIMER_GUARD_LEVEL] = snapshot.focusGuardLevel.name
            prefs[Keys.TIMER_APPROVED_PACKAGE] = snapshot.approvedPackageName
            prefs[Keys.TIMER_APPROVED_APP_LABEL] = snapshot.approvedAppLabel
            prefs[Keys.TIMER_APPROVED_DOC_URI] = snapshot.approvedDocumentUri
        }
    }

    suspend fun updateGardenRewards(
        seedsDelta: Int = 0,
        newDecorations: Set<String> = emptySet(),
        mindResetDelta: Int = 0
    ) {
        dataStore.edit { prefs ->
            val currentSeeds = prefs[Keys.SEEDS_OWNED] ?: 1
            prefs[Keys.SEEDS_OWNED] = (currentSeeds + seedsDelta).coerceAtLeast(0)
            if (newDecorations.isNotEmpty()) {
                val existing = prefs[Keys.UNLOCKED_DECORATIONS] ?: emptySet()
                prefs[Keys.UNLOCKED_DECORATIONS] = existing + newDecorations
            }
            if (mindResetDelta != 0) {
                val currentCount = prefs[Keys.MIND_RESET_COUNT] ?: 0
                prefs[Keys.MIND_RESET_COUNT] = (currentCount + mindResetDelta).coerceAtLeast(0)
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun resetStudyRewardsOnly() {
        dataStore.edit { prefs ->
            prefs[Keys.SEEDS_OWNED] = 1
            prefs[Keys.UNLOCKED_DECORATIONS] = emptySet()
            prefs[Keys.MIND_RESET_COUNT] = 0
            prefs[Keys.TIMER_ACCUMULATED_MS] = 0L
            prefs[Keys.TIMER_RUNNING_SINCE_MS] = -1L
            prefs[Keys.TIMER_RUNNING_SINCE_ELAPSED_MS] = -1L
            prefs[Keys.TIMER_HAS_STARTED] = false
            prefs[Keys.TIMER_SESSION_ID] = ""
            prefs[Keys.TIMER_IS_STRICT_FOCUS] = false
            prefs[Keys.TIMER_STRICT_AOD] = false
            prefs[Keys.TIMER_STRICT_DND_ACTIVE] = false
            prefs[Keys.TIMER_PAUSED_PARENT_JSON] = ""
            prefs[Keys.DISTRACTION_WARNINGS_TODAY] = 0
            prefs[Keys.DISTRACTION_WARNINGS_WEEK] = 0
        }
    }

    private fun encodePausedParent(parent: PausedParentSession?): String {
        if (parent == null) return ""
        return JSONObject().apply {
            put("sessionId", parent.sessionId)
            put("mode", parent.mode.name)
            put("activityType", parent.activityType.name)
            put("activityLabel", parent.activityLabel)
            put("subjectId", parent.selectedSubjectId ?: -1L)
            put("sessionGoal", parent.sessionGoal)
            put("totalSeconds", parent.totalSeconds)
            put("accumulatedFocusedMs", parent.accumulatedFocusedMs)
            put("isStrictFocus", parent.isStrictFocus)
            put("strictFocusAodStyle", parent.strictFocusAodStyle)
            put("focusGuardLevel", parent.focusGuardLevel.name)
        }.toString()
    }

    private fun decodePausedParent(raw: String?): PausedParentSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val subj = json.optLong("subjectId", -1L)
            val isStrict = json.optBoolean("isStrictFocus", false)
            PausedParentSession(
                sessionId = json.optString("sessionId", ""),
                mode = FocusMode.entries.firstOrNull { it.name == json.optString("mode") } ?: FocusMode.FOCUS,
                activityType = StudyActivityType.entries.firstOrNull { it.name == json.optString("activityType") }
                    ?: StudyActivityType.STUDYING,
                activityLabel = json.optString("activityLabel", StudyActivityType.STUDYING.label),
                selectedSubjectId = if (subj > 0L) subj else null,
                sessionGoal = json.optString("sessionGoal", ""),
                totalSeconds = json.optInt("totalSeconds", 25 * 60),
                accumulatedFocusedMs = json.optLong("accumulatedFocusedMs", 0L),
                isStrictFocus = isStrict,
                strictFocusAodStyle = json.optBoolean("strictFocusAodStyle", false),
                focusGuardLevel = FocusGuardLevel.entries.firstOrNull { it.name == json.optString("focusGuardLevel") }
                    ?: if (isStrict) FocusGuardLevel.HIGH else FocusGuardLevel.NORMAL
            )
        }.getOrNull()
    }

    private fun encodeFocusGuardSettings(settings: FocusGuardSettings): String {
        return JSONObject().apply {
            put("defaultLevel", settings.defaultLevel.name)
            put("defaultFocusMinutes", settings.defaultFocusMinutes)
            put("defaultActivityAllowanceMinutes", settings.defaultActivityAllowanceMinutes)
            put("defaultBreakMinutes", settings.defaultBreakMinutes)
            put("strictnessLevel", settings.strictnessLevel.name)
            put("dndIntegrationEnabled", settings.dndIntegrationEnabled)
            put("notificationSoundEnabled", settings.notificationSoundEnabled)
            put("vibrationEnabled", settings.vibrationEnabled)
            put("activityTrackingEnabled", settings.activityTrackingEnabled)
            put("autoReturnToStudyReminder", settings.autoReturnToStudyReminder)
            put("emergencyExceptionsAlwaysOn", settings.emergencyExceptionsAlwaysOn)
            put("optionalAppPinningEnabled", settings.optionalAppPinningEnabled)
            put("distractionWarningsEnabled", settings.distractionWarningsEnabled)
            put("distractionCooldownSeconds", settings.distractionCooldownSeconds)
            put("customExtensionMinutes", settings.customExtensionMinutes)
            put("pauseStudyDuringTempActivity", settings.pauseStudyDuringTempActivity)
            put("liveUpdatesEnabled", settings.liveUpdatesEnabled)
            put("allowedStudyAppsCsv", settings.allowedStudyAppsCsv)
            put("optionalEntertainmentAppsCsv", settings.optionalEntertainmentAppsCsv)
            put("pdfDefaultMinutes", settings.allowances.pdfDefaultMinutes)
            put("lectureDefaultMinutes", settings.allowances.lectureDefaultMinutes)
            put("readingDefaultMinutes", settings.allowances.readingDefaultMinutes)
            put("notesDefaultMinutes", settings.allowances.notesDefaultMinutes)
            put("breakDefaultMinutes", settings.allowances.breakDefaultMinutes)
            put("gamingDefaultMinutes", settings.allowances.gamingDefaultMinutes)
            put("socialDefaultMinutes", settings.allowances.socialDefaultMinutes)
            put("dailyEntertainmentLimitMinutes", settings.allowances.dailyEntertainmentLimitMinutes)
        }.toString()
    }

    private fun decodeFocusGuardSettings(raw: String?): FocusGuardSettings {
        if (raw.isNullOrBlank()) return FocusGuardSettings()
        return runCatching {
            val json = JSONObject(raw)
            FocusGuardSettings(
                defaultLevel = FocusGuardLevel.entries.firstOrNull { it.name == json.optString("defaultLevel") }
                    ?: FocusGuardLevel.HIGH,
                defaultFocusMinutes = json.optInt("defaultFocusMinutes", 45).coerceIn(5, 180),
                defaultActivityAllowanceMinutes = json.optInt("defaultActivityAllowanceMinutes", 15).coerceIn(5, 90),
                defaultBreakMinutes = json.optInt("defaultBreakMinutes", 10).coerceIn(2, 60),
                strictnessLevel = StrictnessLevel.entries.firstOrNull { it.name == json.optString("strictnessLevel") }
                    ?: StrictnessLevel.COMMITTED,
                dndIntegrationEnabled = json.optBoolean("dndIntegrationEnabled", false),
                notificationSoundEnabled = json.optBoolean("notificationSoundEnabled", true),
                vibrationEnabled = json.optBoolean("vibrationEnabled", true),
                activityTrackingEnabled = json.optBoolean("activityTrackingEnabled", true),
                autoReturnToStudyReminder = json.optBoolean("autoReturnToStudyReminder", true),
                emergencyExceptionsAlwaysOn = json.optBoolean("emergencyExceptionsAlwaysOn", true),
                optionalAppPinningEnabled = json.optBoolean("optionalAppPinningEnabled", false),
                distractionWarningsEnabled = json.optBoolean("distractionWarningsEnabled", true),
                distractionCooldownSeconds = json.optInt("distractionCooldownSeconds", 90).coerceIn(30, 300),
                customExtensionMinutes = json.optInt("customExtensionMinutes", 5).coerceIn(3, 30),
                pauseStudyDuringTempActivity = json.optBoolean("pauseStudyDuringTempActivity", true),
                liveUpdatesEnabled = json.optBoolean("liveUpdatesEnabled", true),
                allowedStudyAppsCsv = json.optString("allowedStudyAppsCsv", "PDF Reader, Notes, Calculator, YouTube"),
                optionalEntertainmentAppsCsv = json.optString("optionalEntertainmentAppsCsv", "Instagram, TikTok, Games"),
                allowances = ActivityAllowanceConfig(
                    pdfDefaultMinutes = json.optInt("pdfDefaultMinutes", 25).coerceIn(5, 120),
                    lectureDefaultMinutes = json.optInt("lectureDefaultMinutes", 25).coerceIn(5, 120),
                    readingDefaultMinutes = json.optInt("readingDefaultMinutes", 20).coerceIn(5, 120),
                    notesDefaultMinutes = json.optInt("notesDefaultMinutes", 25).coerceIn(5, 120),
                    breakDefaultMinutes = json.optInt("breakDefaultMinutes", 10).coerceIn(2, 60),
                    gamingDefaultMinutes = json.optInt("gamingDefaultMinutes", 15).coerceIn(5, 90),
                    socialDefaultMinutes = json.optInt("socialDefaultMinutes", 10).coerceIn(5, 90),
                    dailyEntertainmentLimitMinutes = json.optInt("dailyEntertainmentLimitMinutes", 45).coerceIn(5, 240)
                )
            )
        }.getOrDefault(FocusGuardSettings())
    }
}
