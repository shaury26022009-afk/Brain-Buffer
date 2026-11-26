package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ActivityAllowanceConfig
import com.example.model.ActivityCategory
import com.example.model.FamilyControlSettings
import com.example.model.FocusGuardLevel
import com.example.model.FocusGuardSettings
import com.example.model.FocusMode
import com.example.model.ManualBlockingSession
import com.example.model.NotificationPreferences
import com.example.model.PausedParentSession
import com.example.model.PersistedTimerSnapshot
import com.example.model.PreferredStudyTime
import com.example.model.StrictnessLevel
import com.example.model.StudentAvatar
import com.example.model.StudyActivityType
import com.example.model.StudyLevel
import com.example.model.BreakPlanMode
import com.example.model.SessionPurposeCategory
import com.example.model.SupabaseAuthSession
import com.example.model.TemporaryAppAccessSession
import com.example.model.TemporaryAppAllowance
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.model.UserProfileRole
import com.example.cloud.SecureTokenCipher
import com.example.performance.PerformanceProfileOverride
import org.json.JSONArray
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
        val ONBOARDING_CURRENT_STEP = intPreferencesKey("onboarding_current_step")
        val USER_PROFILE_ROLE = stringPreferencesKey("user_profile_role")
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
        val SOUNDSCAPE_SELECTED = stringPreferencesKey("soundscape_selected")
        val SOUNDSCAPE_VOLUME = floatPreferencesKey("soundscape_volume")
        val SOUNDSCAPE_SYNC_WITH_TIMER = booleanPreferencesKey("soundscape_sync_with_timer")
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
        val TIMER_LINKED_TASK_ID = longPreferencesKey("timer_linked_task_id")
        val TIMER_LAST_COMPLETED_SESSION_ID = stringPreferencesKey("timer_last_completed_session_id")
        val TIMER_PURPOSE_CATEGORY = stringPreferencesKey("timer_purpose_category")
        val TIMER_ALLOWED_PACKAGES = stringSetPreferencesKey("timer_allowed_packages")
        val TIMER_ALLOWED_APP_LABELS_JSON = stringPreferencesKey("timer_allowed_app_labels_json")
        val TIMER_BREAK_PLAN_MODE = stringPreferencesKey("timer_break_plan_mode")
        val TIMER_BREAK_DURATION_MIN = intPreferencesKey("timer_break_duration_min")
        val TIMER_BREAK_INTERVAL_MIN = intPreferencesKey("timer_break_interval_min")
        val TIMER_BREAKS_TAKEN_COUNT = intPreferencesKey("timer_breaks_taken_count")
        val TIMER_ACCUM_BREAK_SEC = intPreferencesKey("timer_accum_break_sec")
        val TIMER_ACCUM_TEMP_ACCESS_SEC = intPreferencesKey("timer_accum_temp_access_sec")
        val TIMER_ACCUM_DISTRACTION_SEC = intPreferencesKey("timer_accum_distraction_sec")
        val TIMER_INTERVENTION_COUNT = intPreferencesKey("timer_intervention_count")
        val TIMER_SESSION_STARTED_MS = longPreferencesKey("timer_session_started_ms")
        val ACTIVE_TEMP_ACCESS_JSON = stringPreferencesKey("active_temp_access_json")
        val MANUAL_BLOCKING_SESSION_JSON = stringPreferencesKey("manual_blocking_session_json")
        val TEMP_APP_ALLOWANCES_JSON = stringPreferencesKey("temp_app_allowances_json")

        // Focus Guard 3.0 Settings & distraction counters (JSON encoded cleanly so defaults evolve safely)
        val FOCUS_GUARD_SETTINGS_JSON = stringPreferencesKey("focus_guard_settings_json")
        val NOTIF_DISTRACTION_WARNINGS = booleanPreferencesKey("notif_distraction_warnings")
        val NOTIF_TEMP_ACTIVITY_TIMER = booleanPreferencesKey("notif_temp_activity_timer")
        val DISTRACTION_WARNINGS_TODAY = intPreferencesKey("distraction_warnings_today")
        val DISTRACTION_WARNINGS_EPOCH_DAY = longPreferencesKey("distraction_warnings_epoch_day")
        val DISTRACTION_WARNINGS_WEEK = intPreferencesKey("distraction_warnings_week")
        val DISTRACTION_WARNINGS_WEEK_START = longPreferencesKey("distraction_warnings_week_start")

        // Extended notification exceptions, analytics privacy, Family Mode & Supabase Auth/Sync
        val NOTIF_ALLOW_IMPORTANT_CALLS = booleanPreferencesKey("notif_allow_important_calls")
        val NOTIF_ALLOW_ALARMS = booleanPreferencesKey("notif_allow_alarms")
        val NOTIF_MUTE_DISTRACTING = booleanPreferencesKey("notif_mute_distracting")
        val CLOUD_USAGE_SUMMARY_SYNC = booleanPreferencesKey("cloud_usage_summary_sync")
        val FAMILY_CONTROL_JSON = stringPreferencesKey("family_control_json")
        val ACTIVE_ACCOUNT_OWNER_ID = stringPreferencesKey("active_account_owner_id")
        val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val AUTH_EMAIL = stringPreferencesKey("auth_email")
        val AUTH_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        val AUTH_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        val AUTH_EXPIRES_AT = longPreferencesKey("auth_expires_at")
        val AUTH_PROVIDERS = stringPreferencesKey("auth_providers")
        val AUTH_DISPLAY_NAME_HINT = stringPreferencesKey("auth_display_name_hint")
        val LAST_SYNCED_AT_MS = longPreferencesKey("last_synced_at_ms")
        val PROFILE_HYDRATED_USER_ID = stringPreferencesKey("profile_hydrated_user_id")
        val FOCUS_SETTINGS_HYDRATED_USER_ID = stringPreferencesKey("focus_settings_hydrated_user_id")
        val PROFILE_UPDATED_AT_MS = longPreferencesKey("profile_updated_at_ms")
        val FOCUS_SETTINGS_UPDATED_AT_MS = longPreferencesKey("focus_settings_updated_at_ms")
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
                onboardingCurrentStep = (prefs[Keys.ONBOARDING_CURRENT_STEP] ?: 0).coerceIn(0, 7),
                userRole = prefs[Keys.USER_PROFILE_ROLE]?.let { name ->
                    UserProfileRole.entries.firstOrNull { it.name == name }
                } ?: UserProfileRole.STUDENT,
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
                    quietEndHour = (prefs[Keys.NOTIF_QUIET_END_HOUR] ?: 7).coerceIn(0, 23),
                    allowImportantCallsDuringFocus = prefs[Keys.NOTIF_ALLOW_IMPORTANT_CALLS] ?: true,
                    allowAlarmsDuringFocus = prefs[Keys.NOTIF_ALLOW_ALARMS] ?: true,
                    muteDistractingAppsDuringFocus = prefs[Keys.NOTIF_MUTE_DISTRACTING] ?: true
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
                },
                cloudUsageSummarySyncEnabled = prefs[Keys.CLOUD_USAGE_SUMMARY_SYNC] ?: false,
                familyControlSettings = decodeFamilyControlSettings(prefs[Keys.FAMILY_CONTROL_JSON]),
                activeAccountOwnerId = prefs[Keys.ACTIVE_ACCOUNT_OWNER_ID] ?: "",
                selectedSoundscapeKey = prefs[Keys.SOUNDSCAPE_SELECTED] ?: "OFF",
                soundscapeVolume = (prefs[Keys.SOUNDSCAPE_VOLUME] ?: 0.72f).coerceIn(0.05f, 1.0f),
                soundscapeSyncWithTimer = prefs[Keys.SOUNDSCAPE_SYNC_WITH_TIMER] ?: true
            )
        }

    val authSessionFlow: Flow<SupabaseAuthSession> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            SupabaseAuthSession(
                userId = prefs[Keys.AUTH_USER_ID] ?: "",
                email = prefs[Keys.AUTH_EMAIL] ?: "",
                accessToken = SecureTokenCipher.decrypt(prefs[Keys.AUTH_ACCESS_TOKEN]),
                refreshToken = SecureTokenCipher.decrypt(prefs[Keys.AUTH_REFRESH_TOKEN]),
                expiresAtEpochSec = prefs[Keys.AUTH_EXPIRES_AT] ?: 0L,
                providers = (prefs[Keys.AUTH_PROVIDERS] ?: "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                displayNameHint = prefs[Keys.AUTH_DISPLAY_NAME_HINT] ?: ""
            )
        }

    val lastSyncedAtMsFlow: Flow<Long> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs[Keys.LAST_SYNCED_AT_MS] ?: 0L }

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
                approvedDocumentUri = prefs[Keys.TIMER_APPROVED_DOC_URI] ?: "",
                linkedTaskId = (prefs[Keys.TIMER_LINKED_TASK_ID] ?: -1L).takeIf { it > 0L },
                lastCompletedSessionId = prefs[Keys.TIMER_LAST_COMPLETED_SESSION_ID] ?: "",
                purposeCategory = SessionPurposeCategory.fromKey(prefs[Keys.TIMER_PURPOSE_CATEGORY]),
                allowedPackages = prefs[Keys.TIMER_ALLOWED_PACKAGES] ?: emptySet(),
                allowedAppLabels = decodeStringList(prefs[Keys.TIMER_ALLOWED_APP_LABELS_JSON]),
                breakPlanMode = BreakPlanMode.fromKey(prefs[Keys.TIMER_BREAK_PLAN_MODE]),
                breakDurationMinutes = (prefs[Keys.TIMER_BREAK_DURATION_MIN] ?: 5).coerceIn(1, 60),
                breakIntervalMinutes = (prefs[Keys.TIMER_BREAK_INTERVAL_MIN] ?: 25).coerceIn(5, 180),
                breaksTakenCount = (prefs[Keys.TIMER_BREAKS_TAKEN_COUNT] ?: 0).coerceAtLeast(0),
                accumulatedBreakSeconds = (prefs[Keys.TIMER_ACCUM_BREAK_SEC] ?: 0).coerceAtLeast(0),
                accumulatedTempAccessSeconds = (prefs[Keys.TIMER_ACCUM_TEMP_ACCESS_SEC] ?: 0).coerceAtLeast(0),
                accumulatedDistractionSeconds = (prefs[Keys.TIMER_ACCUM_DISTRACTION_SEC] ?: 0).coerceAtLeast(0),
                interventionCount = (prefs[Keys.TIMER_INTERVENTION_COUNT] ?: 0).coerceAtLeast(0),
                sessionStartedAtEpochMs = (prefs[Keys.TIMER_SESSION_STARTED_MS] ?: -1L).takeIf { it > 0L }
            )
        }

    val activeTempAccessFlow: Flow<TemporaryAppAccessSession?> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decodeActiveTempAccess(prefs[Keys.ACTIVE_TEMP_ACCESS_JSON]) }

    val manualBlockingSessionFlow: Flow<ManualBlockingSession> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decodeManualBlockingSession(prefs[Keys.MANUAL_BLOCKING_SESSION_JSON]) }

    val temporaryAllowancesFlow: Flow<List<TemporaryAppAllowance>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decodeTemporaryAllowances(prefs[Keys.TEMP_APP_ALLOWANCES_JSON]) }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesFlow.first()
    suspend fun getTimerSnapshotOnce(): PersistedTimerSnapshot = timerSnapshotFlow.first()
    suspend fun getActiveTempAccessOnce(): TemporaryAppAccessSession? = activeTempAccessFlow.first()
    suspend fun getManualBlockingSessionOnce(): ManualBlockingSession = manualBlockingSessionFlow.first()
    suspend fun getTemporaryAllowancesOnce(): List<TemporaryAppAllowance> = temporaryAllowancesFlow.first()

    suspend fun saveActiveTempAccess(session: TemporaryAppAccessSession?) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_TEMP_ACCESS_JSON] = encodeActiveTempAccess(session)
        }
    }

    suspend fun saveManualBlockingSession(session: ManualBlockingSession) {
        dataStore.edit { prefs ->
            prefs[Keys.MANUAL_BLOCKING_SESSION_JSON] = encodeManualBlockingSession(session)
        }
    }

    suspend fun saveTemporaryAllowances(allowances: List<TemporaryAppAllowance>) {
        dataStore.edit { prefs ->
            prefs[Keys.TEMP_APP_ALLOWANCES_JSON] = encodeTemporaryAllowances(allowances)
        }
    }

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
            prefs[Keys.ONBOARDING_CURRENT_STEP] = 0
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
            prefs[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
            if ((prefs[Keys.TIMER_HAS_STARTED] ?: false).not()) {
                prefs[Keys.TIMER_TOTAL_SECONDS] = preferredFocusMinutes.coerceIn(5, 120) * 60
            }
        }
    }

    suspend fun saveOnboardingCurrentStep(step: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_CURRENT_STEP] = step.coerceIn(0, 7)
        }
    }

    suspend fun saveOnboardingDraft(
        step: Int,
        userRole: UserProfileRole,
        studentName: String,
        selectedPurposes: Set<SessionPurposeCategory>,
        dailyTargetMinutes: Int,
        preferredFocusMinutes: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_CURRENT_STEP] = step.coerceIn(0, 7)
            prefs[Keys.USER_PROFILE_ROLE] = userRole.name
            prefs[Keys.STUDENT_NAME] = studentName.trim().take(36)
            if (selectedPurposes.isNotEmpty()) {
                prefs[Keys.STUDY_GOALS] = selectedPurposes.map { it.label }.toSet()
                val existingGuard = decodeFocusGuardSettings(prefs[Keys.FOCUS_GUARD_SETTINGS_JSON])
                prefs[Keys.FOCUS_GUARD_SETTINGS_JSON] = encodeFocusGuardSettings(
                    existingGuard.copy(defaultPurposeCategory = selectedPurposes.first())
                )
            }
            prefs[Keys.DAILY_TARGET_MIN] = dailyTargetMinutes.coerceIn(15, 480)
            prefs[Keys.PREFERRED_FOCUS_MIN] = preferredFocusMinutes.coerceIn(5, 120)
        }
    }

    suspend fun saveFullOnboardingPreferences(
        userRole: UserProfileRole,
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int,
        focusGuardSettings: FocusGuardSettings,
        notificationPrefs: NotificationPreferences,
        appUsageAwarenessEnabled: Boolean
    ) {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
            prefs[Keys.ONBOARDING_CURRENT_STEP] = 0
            prefs[Keys.USER_PROFILE_ROLE] = userRole.name
            prefs[Keys.STUDENT_NAME] = studentName.trim()
            prefs[Keys.AVATAR_KEY] = avatarKey
            prefs[Keys.STUDY_LEVEL] = studyLevel.name
            prefs[Keys.COURSE_OR_EXAM] = courseOrExamName.trim()
            prefs[Keys.STUDY_GOALS] = studyGoals
            prefs[Keys.CUSTOM_GOAL] = customGoal.trim()
            prefs[Keys.PREFERRED_FOCUS_MIN] = preferredFocusMinutes.coerceIn(5, 120)
            prefs[Keys.SHORT_BREAK_MIN] = shortBreakMinutes.coerceIn(2, 30)
            prefs[Keys.LONG_BREAK_MIN] = longBreakMinutes.coerceIn(5, 60)
            prefs[Keys.PREFERRED_STUDY_TIME] = preferredStudyTime.name
            prefs[Keys.WEEKLY_STUDY_DAYS] = weeklyStudyDaysTarget.coerceIn(1, 7)
            prefs[Keys.BIGGEST_DISTRACTIONS] = biggestDistractions
            prefs[Keys.OTHER_DISTRACTION] = otherDistraction.trim()
            prefs[Keys.DAILY_TARGET_MIN] = dailyTargetMinutes.coerceIn(15, 480)
            prefs[Keys.FOCUS_GUARD_SETTINGS_JSON] = encodeFocusGuardSettings(focusGuardSettings)
            prefs[Keys.SOUND_ENABLED] = focusGuardSettings.notificationSoundEnabled
            prefs[Keys.HAPTICS_ENABLED] = focusGuardSettings.vibrationEnabled
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
            prefs[Keys.NOTIF_ALLOW_IMPORTANT_CALLS] = notificationPrefs.allowImportantCallsDuringFocus
            prefs[Keys.NOTIF_ALLOW_ALARMS] = notificationPrefs.allowAlarmsDuringFocus
            prefs[Keys.NOTIF_MUTE_DISTRACTING] = notificationPrefs.muteDistractingAppsDuringFocus
            prefs[Keys.APP_USAGE_AWARENESS_ENABLED] = appUsageAwarenessEnabled
            prefs[Keys.PROFILE_UPDATED_AT_MS] = now
            prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = now
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
            prefs[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
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
            prefs[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit {
            it[Keys.THEME_MODE] = mode.name
            it[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun updatePerformanceOverride(override: PerformanceProfileOverride) {
        dataStore.edit {
            it[Keys.PERFORMANCE_OVERRIDE] = override.name
            it[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
        }
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
            prefs[Keys.NOTIF_ALLOW_IMPORTANT_CALLS] = notificationPrefs.allowImportantCallsDuringFocus
            prefs[Keys.NOTIF_ALLOW_ALARMS] = notificationPrefs.allowAlarmsDuringFocus
            prefs[Keys.NOTIF_MUTE_DISTRACTING] = notificationPrefs.muteDistractingAppsDuringFocus
            prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun updateCloudUsageSummarySyncEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.CLOUD_USAGE_SUMMARY_SYNC] = enabled
            it[Keys.PROFILE_UPDATED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun updateFamilyControlSettings(settings: FamilyControlSettings) {
        dataStore.edit {
            it[Keys.FAMILY_CONTROL_JSON] = encodeFamilyControlSettings(settings)
            it[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun saveAuthSession(session: SupabaseAuthSession) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTH_USER_ID] = session.userId
            prefs[Keys.AUTH_EMAIL] = session.email
            prefs[Keys.AUTH_ACCESS_TOKEN] = SecureTokenCipher.encrypt(session.accessToken)
            prefs[Keys.AUTH_REFRESH_TOKEN] = SecureTokenCipher.encrypt(session.refreshToken)
            prefs[Keys.AUTH_EXPIRES_AT] = session.expiresAtEpochSec
            prefs[Keys.AUTH_PROVIDERS] = session.providers.distinct().joinToString(",")
            prefs[Keys.AUTH_DISPLAY_NAME_HINT] = session.displayNameHint
        }
    }

    suspend fun clearAuthSession() {
        dataStore.edit { prefs ->
            prefs[Keys.AUTH_USER_ID] = ""
            prefs[Keys.AUTH_EMAIL] = ""
            prefs[Keys.AUTH_ACCESS_TOKEN] = ""
            prefs[Keys.AUTH_REFRESH_TOKEN] = ""
            prefs[Keys.AUTH_EXPIRES_AT] = 0L
            prefs[Keys.AUTH_PROVIDERS] = ""
            prefs[Keys.AUTH_DISPLAY_NAME_HINT] = ""
        }
    }

    suspend fun updateActiveAccountOwnerId(ownerId: String) {
        dataStore.edit { it[Keys.ACTIVE_ACCOUNT_OWNER_ID] = ownerId }
    }

    suspend fun updateLastSyncedAtMs(ms: Long) {
        dataStore.edit { it[Keys.LAST_SYNCED_AT_MS] = ms }
    }

    suspend fun getLastSyncedAtMsOnce(): Long = lastSyncedAtMsFlow.first()

    suspend fun getAuthSessionOnce(): SupabaseAuthSession = authSessionFlow.first()

    suspend fun isProfileHydratedForUser(userId: String): Boolean {
        if (userId.isBlank()) return false
        return dataStore.data.first()[Keys.PROFILE_HYDRATED_USER_ID] == userId
    }

    suspend fun markProfileHydratedForUser(userId: String, syncedAtMs: Long = System.currentTimeMillis()) {
        dataStore.edit {
            it[Keys.PROFILE_HYDRATED_USER_ID] = userId
            if ((it[Keys.PROFILE_UPDATED_AT_MS] ?: 0L) == 0L) {
                it[Keys.PROFILE_UPDATED_AT_MS] = syncedAtMs
            }
        }
    }

    suspend fun getProfileUpdatedAtMsOnce(): Long =
        dataStore.data.first()[Keys.PROFILE_UPDATED_AT_MS] ?: 0L

    suspend fun isFocusSettingsHydratedForUser(userId: String): Boolean {
        if (userId.isBlank()) return false
        return dataStore.data.first()[Keys.FOCUS_SETTINGS_HYDRATED_USER_ID] == userId
    }

    suspend fun markFocusSettingsHydratedForUser(userId: String, syncedAtMs: Long = System.currentTimeMillis()) {
        dataStore.edit {
            it[Keys.FOCUS_SETTINGS_HYDRATED_USER_ID] = userId
            if ((it[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] ?: 0L) == 0L) {
                it[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = syncedAtMs
            }
        }
    }

    suspend fun getFocusSettingsUpdatedAtMsOnce(): Long =
        dataStore.data.first()[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] ?: 0L

    /**
     * Merges a fetched remote Supabase profile into DataStore.
     * On first login / new device (`preferRemote = true`), non-blank cloud values always hydrate local fields.
     * On subsequent syncs (`preferRemote = false`), remote values fill blank local fields without losing valid data.
     */
    suspend fun applyRemoteProfile(
        displayName: String,
        avatarKey: String,
        studyLevelName: String,
        courseOrExamName: String,
        onboardingCompleted: Boolean,
        preferencesJson: JSONObject?,
        remoteUpdatedAtMs: Long,
        preferRemote: Boolean
    ) {
        dataStore.edit { prefs ->
            val localName = prefs[Keys.STUDENT_NAME].orEmpty()
            if (displayName.isNotBlank() && (preferRemote || localName.isBlank())) {
                prefs[Keys.STUDENT_NAME] = displayName.trim()
            }
            if (avatarKey.isNotBlank() && (preferRemote || prefs[Keys.AVATAR_KEY].isNullOrBlank())) {
                prefs[Keys.AVATAR_KEY] = avatarKey
            }
            if (studyLevelName.isNotBlank() && StudyLevel.entries.any { it.name == studyLevelName } &&
                (preferRemote || prefs[Keys.STUDY_LEVEL].isNullOrBlank())
            ) {
                prefs[Keys.STUDY_LEVEL] = studyLevelName
            }
            val localCourse = prefs[Keys.COURSE_OR_EXAM].orEmpty()
            if (courseOrExamName.isNotBlank() && (preferRemote || localCourse.isBlank())) {
                prefs[Keys.COURSE_OR_EXAM] = courseOrExamName.trim()
            }
            if (onboardingCompleted) {
                prefs[Keys.ONBOARDING_COMPLETED] = true
            }
            if (preferencesJson != null && preferRemote) {
                if (preferencesJson.has("preferredFocusMinutes")) {
                    prefs[Keys.PREFERRED_FOCUS_MIN] = preferencesJson.optInt("preferredFocusMinutes", 25).coerceIn(5, 120)
                }
                if (preferencesJson.has("shortBreakMinutes")) {
                    prefs[Keys.SHORT_BREAK_MIN] = preferencesJson.optInt("shortBreakMinutes", 5).coerceIn(2, 30)
                }
                if (preferencesJson.has("longBreakMinutes")) {
                    prefs[Keys.LONG_BREAK_MIN] = preferencesJson.optInt("longBreakMinutes", 15).coerceIn(5, 60)
                }
                if (preferencesJson.has("dailyTargetMinutes")) {
                    prefs[Keys.DAILY_TARGET_MIN] = preferencesJson.optInt("dailyTargetMinutes", 60).coerceIn(15, 480)
                }
                val remoteTheme = preferencesJson.optString("themeMode", "")
                if (ThemeMode.entries.any { it.name == remoteTheme }) {
                    prefs[Keys.THEME_MODE] = remoteTheme
                }
                val remotePerf = preferencesJson.optString("performanceOverride", "")
                if (PerformanceProfileOverride.entries.any { it.name == remotePerf }) {
                    prefs[Keys.PERFORMANCE_OVERRIDE] = remotePerf
                }
                if (preferencesJson.has("cloudUsageSummarySyncEnabled")) {
                    prefs[Keys.CLOUD_USAGE_SUMMARY_SYNC] = preferencesJson.optBoolean("cloudUsageSummarySyncEnabled", false)
                }
            }
            if (remoteUpdatedAtMs > (prefs[Keys.PROFILE_UPDATED_AT_MS] ?: 0L)) {
                prefs[Keys.PROFILE_UPDATED_AT_MS] = remoteUpdatedAtMs
            }
        }
    }

    /**
     * Hydrates remote `focus_settings` (Focus Guard, Notification Prefs, Family Controls) into DataStore.
     */
    suspend fun applyRemoteFocusSettings(
        settingsJson: JSONObject?,
        notificationPrefsJson: JSONObject?,
        familyControlsJson: JSONObject?,
        remoteUpdatedAtMs: Long
    ) {
        val current = getPreferencesOnce()
        dataStore.edit { prefs ->
            if (settingsJson != null && settingsJson.length() > 0) {
                val decoded = decodeFocusGuardSettings(settingsJson.toString())
                prefs[Keys.FOCUS_GUARD_SETTINGS_JSON] = encodeFocusGuardSettings(decoded)
                prefs[Keys.SOUND_ENABLED] = decoded.notificationSoundEnabled
                prefs[Keys.HAPTICS_ENABLED] = decoded.vibrationEnabled
                prefs[Keys.NOTIF_DISTRACTION_WARNINGS] = decoded.distractionWarningsEnabled
            }
            if (notificationPrefsJson != null && notificationPrefsJson.length() > 0) {
                val np = decodeNotificationPreferences(notificationPrefsJson, current.notificationPrefs)
                prefs[Keys.NOTIF_ACTIVE_FOCUS] = np.activeFocusEnabled
                prefs[Keys.BREAK_REMINDERS_ENABLED] = np.breakRemindersEnabled
                prefs[Keys.NOTIF_ACTIVITY_REMINDERS] = np.activityRemindersEnabled
                prefs[Keys.NOTIF_TEMP_ACTIVITY_TIMER] = np.tempActivityTimerEnabled
                prefs[Keys.NOTIF_DISTRACTION_WARNINGS] = np.distractionWarningsEnabled
                prefs[Keys.NOTIF_UPCOMING_TASKS] = np.upcomingTasksEnabled
                prefs[Keys.NOTIF_EXAM_COUNTDOWNS] = np.examCountdownsEnabled
                prefs[Keys.NOTIF_STUDY_REMINDERS] = np.studyRemindersEnabled
                prefs[Keys.NOTIFICATIONS_ENABLED] = np.studyRemindersEnabled
                prefs[Keys.NOTIF_COMPLETED_SESSIONS] = np.completedSessionsEnabled
                prefs[Keys.NOTIF_HIDE_PRIVATE_LOCKSCREEN] = np.hidePrivateNotesOnLockScreen
                prefs[Keys.NOTIF_QUIET_HOURS_ENABLED] = np.quietHoursEnabled
                prefs[Keys.NOTIF_QUIET_START_HOUR] = np.quietStartHour.coerceIn(0, 23)
                prefs[Keys.NOTIF_QUIET_END_HOUR] = np.quietEndHour.coerceIn(0, 23)
                prefs[Keys.NOTIF_ALLOW_IMPORTANT_CALLS] = np.allowImportantCallsDuringFocus
                prefs[Keys.NOTIF_ALLOW_ALARMS] = np.allowAlarmsDuringFocus
                prefs[Keys.NOTIF_MUTE_DISTRACTING] = np.muteDistractingAppsDuringFocus
            }
            if (familyControlsJson != null && familyControlsJson.length() > 0) {
                val decodedFamily = decodeFamilyControlSettings(familyControlsJson.toString())
                prefs[Keys.FAMILY_CONTROL_JSON] = encodeFamilyControlSettings(decodedFamily)
            }
            if (remoteUpdatedAtMs > (prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] ?: 0L)) {
                prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = remoteUpdatedAtMs
            }
        }
    }

    /**
     * Resets account-scoped profile, family, and sync hydration fields on sign-out or account switch
     * so Account A's personal details never leak into Account B on the same device.
     */
    suspend fun resetAccountScopedPreferencesForSwitch() {
        dataStore.edit { prefs ->
            prefs[Keys.STUDENT_NAME] = ""
            prefs[Keys.USER_PROFILE_ROLE] = UserProfileRole.STUDENT.name
            prefs[Keys.COURSE_OR_EXAM] = ""
            prefs[Keys.AVATAR_KEY] = StudentAvatar.SPARK.key
            prefs[Keys.STUDY_LEVEL] = StudyLevel.UNIVERSITY.name
            prefs[Keys.STUDY_GOALS] = emptySet()
            prefs[Keys.CUSTOM_GOAL] = ""
            prefs[Keys.BIGGEST_DISTRACTIONS] = emptySet()
            prefs[Keys.OTHER_DISTRACTION] = ""
            prefs[Keys.CLOUD_USAGE_SUMMARY_SYNC] = false
            prefs[Keys.FAMILY_CONTROL_JSON] = ""
            prefs[Keys.FOCUS_GUARD_SETTINGS_JSON] = ""
            prefs[Keys.LAST_SYNCED_AT_MS] = 0L
            prefs[Keys.PROFILE_HYDRATED_USER_ID] = ""
            prefs[Keys.FOCUS_SETTINGS_HYDRATED_USER_ID] = ""
            prefs[Keys.PROFILE_UPDATED_AT_MS] = 0L
            prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = 0L
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
            prefs[Keys.FOCUS_SETTINGS_UPDATED_AT_MS] = System.currentTimeMillis()
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

    suspend fun updateSoundscapeSettings(
        selectedKey: String,
        volume: Float,
        syncWithTimer: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SOUNDSCAPE_SELECTED] = selectedKey
            prefs[Keys.SOUNDSCAPE_VOLUME] = volume.coerceIn(0.05f, 1.0f)
            prefs[Keys.SOUNDSCAPE_SYNC_WITH_TIMER] = syncWithTimer
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
            prefs[Keys.TIMER_LINKED_TASK_ID] = snapshot.linkedTaskId ?: -1L
            prefs[Keys.TIMER_LAST_COMPLETED_SESSION_ID] = snapshot.lastCompletedSessionId
            prefs[Keys.TIMER_PURPOSE_CATEGORY] = snapshot.purposeCategory.name
            prefs[Keys.TIMER_ALLOWED_PACKAGES] = snapshot.allowedPackages
            prefs[Keys.TIMER_ALLOWED_APP_LABELS_JSON] = encodeStringList(snapshot.allowedAppLabels)
            prefs[Keys.TIMER_BREAK_PLAN_MODE] = snapshot.breakPlanMode.name
            prefs[Keys.TIMER_BREAK_DURATION_MIN] = snapshot.breakDurationMinutes
            prefs[Keys.TIMER_BREAK_INTERVAL_MIN] = snapshot.breakIntervalMinutes
            prefs[Keys.TIMER_BREAKS_TAKEN_COUNT] = snapshot.breaksTakenCount
            prefs[Keys.TIMER_ACCUM_BREAK_SEC] = snapshot.accumulatedBreakSeconds
            prefs[Keys.TIMER_ACCUM_TEMP_ACCESS_SEC] = snapshot.accumulatedTempAccessSeconds
            prefs[Keys.TIMER_ACCUM_DISTRACTION_SEC] = snapshot.accumulatedDistractionSeconds
            prefs[Keys.TIMER_INTERVENTION_COUNT] = snapshot.interventionCount
            prefs[Keys.TIMER_SESSION_STARTED_MS] = snapshot.sessionStartedAtEpochMs ?: -1L
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
        dataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = completed
            if (!completed) {
                it[Keys.ONBOARDING_CURRENT_STEP] = 0
            }
        }
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
            put("purposeCategory", parent.purposeCategory.name)
            put("allowedPackages", JSONArray(parent.allowedPackages.toList()))
            put("allowedAppLabels", JSONArray(parent.allowedAppLabels))
            put("breakPlanMode", parent.breakPlanMode.name)
            put("breakDurationMinutes", parent.breakDurationMinutes)
            put("breakIntervalMinutes", parent.breakIntervalMinutes)
            put("breaksTakenCount", parent.breaksTakenCount)
            put("accumulatedBreakSeconds", parent.accumulatedBreakSeconds)
            put("accumulatedTempAccessSeconds", parent.accumulatedTempAccessSeconds)
            put("accumulatedDistractionSeconds", parent.accumulatedDistractionSeconds)
            put("interventionCount", parent.interventionCount)
            put("linkedTaskId", parent.linkedTaskId ?: -1L)
            put("sessionStartedAtEpochMs", parent.sessionStartedAtEpochMs ?: -1L)
        }.toString()
    }

    private fun decodePausedParent(raw: String?): PausedParentSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val subj = json.optLong("subjectId", -1L)
            val isStrict = json.optBoolean("isStrictFocus", false)
            val pkgArr = json.optJSONArray("allowedPackages")
            val pkgs = buildSet {
                if (pkgArr != null) {
                    for (i in 0 until pkgArr.length()) {
                        val s = pkgArr.optString(i, "").trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }
            val labelArr = json.optJSONArray("allowedAppLabels")
            val labels = buildList {
                if (labelArr != null) {
                    for (i in 0 until labelArr.length()) {
                        val s = labelArr.optString(i, "").trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }
            val linked = json.optLong("linkedTaskId", -1L).takeIf { it > 0L }
            val startedMs = json.optLong("sessionStartedAtEpochMs", -1L).takeIf { it > 0L }
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
                    ?: if (isStrict) FocusGuardLevel.HIGH else FocusGuardLevel.NORMAL,
                purposeCategory = SessionPurposeCategory.fromKey(json.optString("purposeCategory", "")),
                allowedPackages = pkgs,
                allowedAppLabels = labels,
                breakPlanMode = BreakPlanMode.fromKey(json.optString("breakPlanMode", "")),
                breakDurationMinutes = json.optInt("breakDurationMinutes", 5).coerceIn(1, 60),
                breakIntervalMinutes = json.optInt("breakIntervalMinutes", 25).coerceIn(5, 180),
                breaksTakenCount = json.optInt("breaksTakenCount", 0).coerceAtLeast(0),
                accumulatedBreakSeconds = json.optInt("accumulatedBreakSeconds", 0).coerceAtLeast(0),
                accumulatedTempAccessSeconds = json.optInt("accumulatedTempAccessSeconds", 0).coerceAtLeast(0),
                accumulatedDistractionSeconds = json.optInt("accumulatedDistractionSeconds", 0).coerceAtLeast(0),
                interventionCount = json.optInt("interventionCount", 0).coerceAtLeast(0),
                linkedTaskId = linked,
                sessionStartedAtEpochMs = startedMs
            )
        }.getOrNull()
    }

    private fun encodeStringList(items: List<String>): String {
        if (items.isEmpty()) return ""
        return JSONArray(items).toString()
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeActiveTempAccess(session: TemporaryAppAccessSession?): String {
        if (session == null) return ""
        return JSONObject().apply {
            put("id", session.id)
            put("parentSessionId", session.parentSessionId)
            put("packageName", session.packageName)
            put("appLabel", session.appLabel)
            put("purposeLabel", session.purposeLabel)
            put("originalAllowanceMinutes", session.originalAllowanceMinutes)
            put("extensionMinutesUsed", session.extensionMinutesUsed)
            put("hasUsedExtension", session.hasUsedExtension)
            put("startedAtEpochMs", session.startedAtEpochMs)
            put("expiresAtEpochMs", session.expiresAtEpochMs)
            put("endedAtEpochMs", session.endedAtEpochMs ?: -1L)
        }.toString()
    }

    private fun decodeActiveTempAccess(raw: String?): TemporaryAppAccessSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val id = json.optString("id", "")
            val pkg = json.optString("packageName", "")
            if (id.isBlank() || pkg.isBlank()) return null
            val ended = json.optLong("endedAtEpochMs", -1L).takeIf { it > 0L }
            TemporaryAppAccessSession(
                id = id,
                parentSessionId = json.optString("parentSessionId", ""),
                packageName = pkg,
                appLabel = json.optString("appLabel", pkg.substringAfterLast('.')),
                purposeLabel = json.optString("purposeLabel", ""),
                originalAllowanceMinutes = json.optInt("originalAllowanceMinutes", 5).coerceIn(1, 60),
                extensionMinutesUsed = json.optInt("extensionMinutesUsed", 0).coerceIn(0, TemporaryAppAccessSession.MAX_EXTENSION_MINUTES),
                hasUsedExtension = json.optBoolean("hasUsedExtension", false),
                startedAtEpochMs = json.optLong("startedAtEpochMs", 0L),
                expiresAtEpochMs = json.optLong("expiresAtEpochMs", 0L),
                endedAtEpochMs = ended
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
            put("scheduledBlockingEnabled", settings.scheduledBlockingEnabled)
            put("scheduleStartHour", settings.scheduleStartHour)
            put("scheduleEndHour", settings.scheduleEndHour)
            put("scheduleDaysCsv", settings.scheduleDaysCsv)
            put("blockDuringActiveFocus", settings.blockDuringActiveFocus)
            put("allowTemporaryBreaks", settings.allowTemporaryBreaks)
            put("essentialAppsCsv", settings.essentialAppsCsv)
            put("gracePeriodSeconds", settings.gracePeriodSeconds)
            put("unlockInterventionEnabled", settings.unlockInterventionEnabled)
            put("defaultPurposeCategory", settings.defaultPurposeCategory.name)
            put("defaultBreakPlanMode", settings.defaultBreakPlanMode.name)
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
                customExtensionMinutes = json.optInt("customExtensionMinutes", 5).coerceIn(1, 5),
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
                ),
                scheduledBlockingEnabled = json.optBoolean("scheduledBlockingEnabled", false),
                scheduleStartHour = json.optInt("scheduleStartHour", 9).coerceIn(0, 23),
                scheduleEndHour = json.optInt("scheduleEndHour", 12).coerceIn(0, 23),
                scheduleDaysCsv = json.optString("scheduleDaysCsv", "MON,TUE,WED,THU,FRI"),
                blockDuringActiveFocus = json.optBoolean("blockDuringActiveFocus", true),
                allowTemporaryBreaks = json.optBoolean("allowTemporaryBreaks", true),
                essentialAppsCsv = json.optString("essentialAppsCsv", "Phone, Messages, Clock, Settings"),
                gracePeriodSeconds = json.optInt("gracePeriodSeconds", 15).coerceIn(0, 120),
                unlockInterventionEnabled = json.optBoolean("unlockInterventionEnabled", false),
                defaultPurposeCategory = SessionPurposeCategory.fromKey(json.optString("defaultPurposeCategory", "")),
                defaultBreakPlanMode = BreakPlanMode.fromKey(json.optString("defaultBreakPlanMode", ""))
            )
        }.getOrDefault(FocusGuardSettings())
    }

    private fun encodeFamilyControlSettings(settings: FamilyControlSettings): String {
        return JSONObject().apply {
            put("isEnabled", settings.isEnabled)
            put("guardianName", settings.guardianName)
            put("pinHash", settings.pinHash)
            put("dailyScreenBudgetMinutes", settings.dailyScreenBudgetMinutes)
            put("dailyDistractingBudgetMinutes", settings.dailyDistractingBudgetMinutes)
            put("studyGoalBeforeEntertainmentMinutes", settings.studyGoalBeforeEntertainmentMinutes)
            put("lockFocusGuardRules", settings.lockFocusGuardRules)
            put("shareWeeklySummaryWithParent", settings.shareWeeklySummaryWithParent)
        }.toString()
    }

    private fun decodeFamilyControlSettings(raw: String?): FamilyControlSettings {
        if (raw.isNullOrBlank()) return FamilyControlSettings()
        return runCatching {
            val json = JSONObject(raw)
            FamilyControlSettings(
                isEnabled = json.optBoolean("isEnabled", false),
                guardianName = json.optString("guardianName", ""),
                pinHash = json.optString("pinHash", ""),
                dailyScreenBudgetMinutes = json.optInt("dailyScreenBudgetMinutes", 180).coerceIn(30, 720),
                dailyDistractingBudgetMinutes = json.optInt("dailyDistractingBudgetMinutes", 45).coerceIn(5, 240),
                studyGoalBeforeEntertainmentMinutes = json.optInt("studyGoalBeforeEntertainmentMinutes", 60).coerceIn(15, 360),
                lockFocusGuardRules = json.optBoolean("lockFocusGuardRules", false),
                shareWeeklySummaryWithParent = json.optBoolean("shareWeeklySummaryWithParent", false)
            )
        }.getOrDefault(FamilyControlSettings())
    }

    fun exportFocusGuardSettingsJson(settings: FocusGuardSettings): JSONObject =
        JSONObject(encodeFocusGuardSettings(settings))

    fun exportFamilyControlSettingsJson(settings: FamilyControlSettings): JSONObject =
        JSONObject(encodeFamilyControlSettings(settings))

    fun exportNotificationPrefsJson(prefs: NotificationPreferences): JSONObject = JSONObject().apply {
        put("activeFocusEnabled", prefs.activeFocusEnabled)
        put("breakRemindersEnabled", prefs.breakRemindersEnabled)
        put("activityRemindersEnabled", prefs.activityRemindersEnabled)
        put("tempActivityTimerEnabled", prefs.tempActivityTimerEnabled)
        put("distractionWarningsEnabled", prefs.distractionWarningsEnabled)
        put("upcomingTasksEnabled", prefs.upcomingTasksEnabled)
        put("examCountdownsEnabled", prefs.examCountdownsEnabled)
        put("studyRemindersEnabled", prefs.studyRemindersEnabled)
        put("completedSessionsEnabled", prefs.completedSessionsEnabled)
        put("hidePrivateNotesOnLockScreen", prefs.hidePrivateNotesOnLockScreen)
        put("quietHoursEnabled", prefs.quietHoursEnabled)
        put("quietStartHour", prefs.quietStartHour)
        put("quietEndHour", prefs.quietEndHour)
        put("allowImportantCallsDuringFocus", prefs.allowImportantCallsDuringFocus)
        put("allowAlarmsDuringFocus", prefs.allowAlarmsDuringFocus)
        put("muteDistractingAppsDuringFocus", prefs.muteDistractingAppsDuringFocus)
    }

    private fun decodeNotificationPreferences(
        json: JSONObject,
        fallback: NotificationPreferences
    ): NotificationPreferences = NotificationPreferences(
        activeFocusEnabled = json.optBoolean("activeFocusEnabled", fallback.activeFocusEnabled),
        breakRemindersEnabled = json.optBoolean("breakRemindersEnabled", fallback.breakRemindersEnabled),
        activityRemindersEnabled = json.optBoolean("activityRemindersEnabled", fallback.activityRemindersEnabled),
        tempActivityTimerEnabled = json.optBoolean("tempActivityTimerEnabled", fallback.tempActivityTimerEnabled),
        distractionWarningsEnabled = json.optBoolean("distractionWarningsEnabled", fallback.distractionWarningsEnabled),
        upcomingTasksEnabled = json.optBoolean("upcomingTasksEnabled", fallback.upcomingTasksEnabled),
        examCountdownsEnabled = json.optBoolean("examCountdownsEnabled", fallback.examCountdownsEnabled),
        studyRemindersEnabled = json.optBoolean("studyRemindersEnabled", fallback.studyRemindersEnabled),
        completedSessionsEnabled = json.optBoolean("completedSessionsEnabled", fallback.completedSessionsEnabled),
        hidePrivateNotesOnLockScreen = json.optBoolean("hidePrivateNotesOnLockScreen", fallback.hidePrivateNotesOnLockScreen),
        quietHoursEnabled = json.optBoolean("quietHoursEnabled", fallback.quietHoursEnabled),
        quietStartHour = json.optInt("quietStartHour", fallback.quietStartHour).coerceIn(0, 23),
        quietEndHour = json.optInt("quietEndHour", fallback.quietEndHour).coerceIn(0, 23),
        allowImportantCallsDuringFocus = json.optBoolean("allowImportantCallsDuringFocus", fallback.allowImportantCallsDuringFocus),
        allowAlarmsDuringFocus = json.optBoolean("allowAlarmsDuringFocus", fallback.allowAlarmsDuringFocus),
        muteDistractingAppsDuringFocus = json.optBoolean("muteDistractingAppsDuringFocus", fallback.muteDistractingAppsDuringFocus)
    )

    private fun encodeManualBlockingSession(session: ManualBlockingSession): String {
        return JSONObject().apply {
            put("isActive", session.isActive)
            put("isPaused", session.isPaused)
            put("startedAtEpochMs", session.startedAtEpochMs)
            put("endsAtEpochMs", session.endsAtEpochMs)
            put("remainingOnPauseMs", session.remainingOnPauseMs)
        }.toString()
    }

    private fun decodeManualBlockingSession(raw: String?): ManualBlockingSession {
        if (raw.isNullOrBlank()) return ManualBlockingSession()
        return runCatching {
            val json = JSONObject(raw)
            ManualBlockingSession(
                isActive = json.optBoolean("isActive", false),
                isPaused = json.optBoolean("isPaused", false),
                startedAtEpochMs = json.optLong("startedAtEpochMs", 0L),
                endsAtEpochMs = json.optLong("endsAtEpochMs", 0L),
                remainingOnPauseMs = json.optLong("remainingOnPauseMs", 0L)
            )
        }.getOrDefault(ManualBlockingSession())
    }

    private fun encodeTemporaryAllowances(list: List<TemporaryAppAllowance>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("packageName", item.packageName)
                    put("appLabel", item.appLabel)
                    put("reason", item.reason)
                    put("grantedAtEpochMs", item.grantedAtEpochMs)
                    put("expiresAtEpochMs", item.expiresAtEpochMs)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeTemporaryAllowances(raw: String?): List<TemporaryAppAllowance> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pkg = obj.optString("packageName", "").trim()
                    if (pkg.isBlank()) continue
                    add(
                        TemporaryAppAllowance(
                            packageName = pkg,
                            appLabel = obj.optString("appLabel", pkg),
                            reason = obj.optString("reason", "Temporary access"),
                            grantedAtEpochMs = obj.optLong("grantedAtEpochMs", 0L),
                            expiresAtEpochMs = obj.optLong("expiresAtEpochMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

