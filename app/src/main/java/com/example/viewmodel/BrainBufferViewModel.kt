package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.CloudSyncScheduler
import com.example.cloud.SupabaseAuthClient
import com.example.cloud.SupabaseAuthResult
import com.example.cloud.SupabaseConfig
import com.example.data.BrainBufferRepository
import com.example.domain.FocusTimerEngine
import com.example.domain.FocusTimerTick
import com.example.domain.PurposeMatchingEngine
import com.example.domain.SystemTimerClock
import com.example.domain.TemporaryAccessPolicy
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.AppUsageSuggestion
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.BreakPlanConfig
import com.example.model.CloudSyncState
import com.example.model.CloudSyncStatus
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PurposeSessionRecord
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.model.TemporaryAppAccessSession
import com.example.model.CustomActivityOption
import com.example.model.DailyActivityStat
import com.example.model.FamilyControlSettings
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.GardenPlant
import com.example.model.MindNote
import com.example.model.MindNoteType
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.PhoneUsageReport
import com.example.model.PlantSpecies
import com.example.model.PlantStage
import com.example.model.PreferredStudyTime
import com.example.model.SessionCelebration
import com.example.model.StrictFocusPreset
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.StudyLevel
import com.example.model.StudyResource
import com.example.model.StudyResourceType
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.SubjectStudySummary
import com.example.model.SupabaseAuthSession
import com.example.model.TaskPriority
import com.example.model.ThemeMode
import com.example.model.UpcomingDeadlineItem
import com.example.model.UsagePeriodMode
import com.example.model.UserPreferences
import com.example.model.UserProfileRole
import com.example.notifications.BrainBufferNotificationManager
import com.example.performance.DevicePerformanceSignals
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileEngine
import com.example.performance.PerformanceProfileOverride
import com.example.service.FocusScheduleWorker
import com.example.service.FocusTimerService
import com.example.service.StudyReminderWorker
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.BrainBufferHaptics
import com.example.system.DndFocusController
import com.example.system.HapticEvent
import com.example.system.PhoneUsageAnalyticsEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class ThemeShellState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedMotionOverride: Boolean = false,
    val performanceOverride: PerformanceProfileOverride = PerformanceProfileOverride.AUTO,
    val isLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false
)

@Immutable
data class FocusTimerUiState(
    val snapshot: PersistedTimerSnapshot = PersistedTimerSnapshot(),
    val remainingSeconds: Int = 25 * 60,
    val progress: Float = 0f,
    val isZenMode: Boolean = false,
    val sessionState: com.example.model.FocusSessionState = com.example.model.FocusSessionState.IDLE,
    val elapsedFocusedSeconds: Int = (snapshot.totalSeconds - remainingSeconds).coerceIn(0, snapshot.totalSeconds)
) {
    val mode: FocusMode get() = snapshot.mode
    val isRunning: Boolean get() = snapshot.runningSinceEpochMs != null
    val hasStarted: Boolean get() = snapshot.hasStarted
    val totalSeconds: Int get() = snapshot.totalSeconds
    val selectedSubjectId: Long? get() = snapshot.selectedSubjectId
    val sessionGoal: String get() = snapshot.sessionGoal
    val activeActivityType: StudyActivityType get() = snapshot.activeActivityType
    val activeActivityLabel: String get() = snapshot.activeActivityLabel
    val activeActivityCategory: ActivityCategory get() = snapshot.activeActivityCategory
    val isSwitchedActivity: Boolean get() = snapshot.isSwitchedActivity
    val isStrictFocus: Boolean get() = snapshot.isStrictFocus
    val isAodStyle: Boolean get() = snapshot.strictFocusAodStyle
    val focusGuardLevel: com.example.model.FocusGuardLevel get() = snapshot.focusGuardLevel
    val approvedAppLabel: String get() = snapshot.approvedAppLabel
    val linkedTaskId: Long? get() = snapshot.linkedTaskId

    val formattedTime: String
        get() {
            val mins = (remainingSeconds / 60).coerceAtLeast(0)
            val secs = (remainingSeconds % 60).coerceAtLeast(0)
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }

    val formattedElapsedTime: String
        get() {
            val mins = (elapsedFocusedSeconds / 60).coerceAtLeast(0)
            val secs = (elapsedFocusedSeconds % 60).coerceAtLeast(0)
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }
}

private data class EphemeralUiState(
    val nowEpochMs: Long = System.currentTimeMillis(),
    val nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    val isZenMode: Boolean = false,
    val quoteIndex: Int = 0,
    val recentlyDeletedTask: StudyTask? = null,
    val pendingCelebration: SessionCelebration? = null,
    val gardenSparkleTrigger: Int = 0,
    val appUsageSuggestion: AppUsageSuggestion? = null,
    val dismissedSuggestionPkg: String? = null,
    val unapprovedAppWarning: com.example.model.UnapprovedAppWarning? = null,
    val isRequestingActivity: Boolean = false,
    val wasRecentlyCancelled: Boolean = false,
    val hasUsageAccessPermission: Boolean = false,
    val preselectedPurpose: SessionPurposeCategory? = null,
    val expiredTempAccessPrompt: TemporaryAppAccessSession? = null,
    val tempAccessNotice: String? = null
)

@Immutable
data class BrainBufferUiState(
    val preferences: UserPreferences = UserPreferences(),
    val subjects: List<Subject> = emptyList(),
    val tasks: List<StudyTask> = emptyList(),
    val sessions: List<FocusSessionRecord> = emptyList(),
    val plants: List<GardenPlant> = emptyList(),
    val mindNotes: List<MindNote> = emptyList(),
    val activityLogs: List<ActivityLogRecord> = emptyList(),
    val strictFocusPresets: List<StrictFocusPreset> = emptyList(),
    val customActivities: List<CustomActivityOption> = emptyList(),
    val appCategoryMappings: List<com.example.model.AppCategoryMapping> = emptyList(),
    val blockedApps: List<BlockedAppRule> = emptyList(),
    val studyResources: List<StudyResource> = emptyList(),
    val purposeSessions: List<PurposeSessionRecord> = emptyList(),
    val interventionEvents: List<InterventionEventRecord> = emptyList(),
    val activeTempAccess: TemporaryAppAccessSession? = null,
    val expiredTempAccessPrompt: TemporaryAppAccessSession? = null,
    val preselectedPurpose: SessionPurposeCategory? = null,
    val tempAccessNotice: String? = null,
    val manualBlockingSession: com.example.model.ManualBlockingSession = com.example.model.ManualBlockingSession(),
    val temporaryAllowances: List<com.example.model.TemporaryAppAllowance> = emptyList(),
    val hasUsageAccessPermission: Boolean = false,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    val phoneUsageReport: PhoneUsageReport = PhoneUsageReport(),
    val cloudSync: CloudSyncState = CloudSyncState(
        isConfigured = SupabaseConfig.isConfigured
    ),
    val timer: FocusTimerUiState = FocusTimerUiState(),
    val quoteIndex: Int = 0,
    val pendingCelebration: SessionCelebration? = null,
    val gardenSparkleTrigger: Int = 0,
    val appUsageSuggestion: AppUsageSuggestion? = null,
    val unapprovedAppWarning: com.example.model.UnapprovedAppWarning? = null,
    val deviceSignals: DevicePerformanceSignals = DevicePerformanceSignals(
        totalRamMb = 4096,
        isLowRamDevice = false,
        cpuCores = 4,
        sdkInt = android.os.Build.VERSION.SDK_INT,
        refreshRateHz = 60f,
        isPowerSaveMode = false
    ),
    val resolvedPerformanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val todayEpochDay: Long = LocalDate.now().toEpochDay()
) {
    val activeBlockedApps: List<BlockedAppRule>
        get() = blockedApps.filter { it.isBlocked }

    val activeTemporaryAllowances: List<com.example.model.TemporaryAppAllowance>
        get() = temporaryAllowances.filter { it.isActive(nowEpochMs) }

    val isManualBlockingEnforcing: Boolean
        get() = manualBlockingSession.isEnforcing(nowEpochMs)

    val isFocusBlockingEnforcing: Boolean
        get() = timer.hasStarted &&
            timer.isRunning &&
            preferences.focusGuardSettings.blockDuringActiveFocus &&
            blockedApps.any { it.isBlockedFor(timer.focusGuardLevel) }

    val isScheduledWindowNow: Boolean
        get() = FocusScheduleWorker.isWithinScheduledWindow(preferences.focusGuardSettings)

    /**
     * Honest status: NEVER returns true unless Android Usage Access is granted AND
     * there is at least one blocked app AND an active blocking session or Focus Guard session is enforcing.
     */
    val isBlockingTrulyActive: Boolean
        get() = hasUsageAccessPermission &&
            activeBlockedApps.isNotEmpty() &&
            (isManualBlockingEnforcing || isFocusBlockingEnforcing)

    val blockingStatusHeadline: String
        get() = when {
            !hasUsageAccessPermission -> "Permission needed"
            activeBlockedApps.isEmpty() -> "No apps selected"
            isManualBlockingEnforcing -> "Blocking active"
            isFocusBlockingEnforcing -> "Blocking during focus"
            manualBlockingSession.isActive && manualBlockingSession.isPaused -> "Blocking paused"
            isScheduledWindowNow -> "Scheduled window ready"
            else -> "Ready to protect"
        }
    val motivationalMessage: String
        get() = BrainBufferRepository.motivationalNotes[quoteIndex % BrainBufferRepository.motivationalNotes.size]

    val distractionTip: String
        get() = BrainBufferRepository.distractionTipFor(preferences.biggestDistractions)

    val focusOnlySessions: List<FocusSessionRecord> by lazy(LazyThreadSafetyMode.NONE) {
        sessions.filter { it.mode == FocusMode.FOCUS }
    }

    val todayFocusMinutes: Int by lazy(LazyThreadSafetyMode.NONE) {
        focusOnlySessions
            .filter { it.dateEpochDay == todayEpochDay }
            .sumOf { it.durationSeconds } / 60
    }

    val todaySessionsCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        focusOnlySessions.count { it.dateEpochDay == todayEpochDay }
    }

    val todayTargetProgress: Float
        get() = (todayFocusMinutes.toFloat() / preferences.dailyTargetMinutes.coerceAtLeast(15)).coerceIn(0f, 1f)

    val totalFocusMinutesAllTime: Int by lazy(LazyThreadSafetyMode.NONE) {
        focusOnlySessions.sumOf { it.durationSeconds } / 60
    }

    val weeklyActivity: List<DailyActivityStat> by lazy(LazyThreadSafetyMode.NONE) {
        BrainBufferRepository.buildWeeklyActivity(sessions, tasks, LocalDate.ofEpochDay(todayEpochDay))
    }

    val weeklyFocusMinutes: Int
        get() = weeklyActivity.sumOf { it.focusMinutes }

    val activeDaysThisWeek: Int
        get() = weeklyActivity.count { it.focusMinutes > 0 }

    val currentStreakDays: Int by lazy(LazyThreadSafetyMode.NONE) {
        BrainBufferRepository.computeCurrentStreakDays(sessions, LocalDate.ofEpochDay(todayEpochDay))
    }

    val bestStreakDays: Int by lazy(LazyThreadSafetyMode.NONE) {
        BrainBufferRepository.computeBestStreakDays(sessions)
    }

    val bestDayMinutes: Int by lazy(LazyThreadSafetyMode.NONE) {
        focusOnlySessions
            .groupBy { it.dateEpochDay }
            .maxOfOrNull { (_, list) -> list.sumOf { it.durationSeconds } / 60 } ?: 0
    }

    val longestSessionMinutes: Int by lazy(LazyThreadSafetyMode.NONE) {
        (focusOnlySessions.maxOfOrNull { it.durationSeconds } ?: 0) / 60
    }

    val completedTasksCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        tasks.count { it.isCompleted }
    }

    val todayTasks: List<StudyTask> by lazy(LazyThreadSafetyMode.NONE) {
        tasks.filter { it.dueDateEpochDay == todayEpochDay }
    }

    val activePlant: GardenPlant?
        get() = plants.firstOrNull { it.isActive } ?: plants.firstOrNull()

    /**
     * Today's activity breakdown strictly separating Focused Study from Lectures,
     * Reading & Notes, Breaks, Gaming & Entertainment, and Other activities.
     */
    val todayActivityLogs: List<ActivityLogRecord> by lazy(LazyThreadSafetyMode.NONE) {
        activityLogs.filter { it.dateEpochDay == todayEpochDay }
    }

    val todayCategoryMinutes: Map<ActivityCategory, Int> by lazy(LazyThreadSafetyMode.NONE) {
        val result = linkedMapOf<ActivityCategory, Int>()
        ActivityCategory.entries.forEach { cat ->
            val fromLogs = todayActivityLogs.filter { it.category == cat }.sumOf { it.actualSeconds } / 60
            result[cat] = if (cat == ActivityCategory.STUDY) {
                maxOf(fromLogs, todayFocusMinutes)
            } else {
                fromLogs
            }
        }
        result
    }

    val todayGamingMinutes: Int
        get() = todayActivityLogs
            .filter { it.activityType == StudyActivityType.PLAYING_GAME }
            .sumOf { it.actualSeconds } / 60

    val todaySocialMinutes: Int
        get() = todayActivityLogs
            .filter { it.activityType == StudyActivityType.ENTERTAINMENT }
            .sumOf { it.actualSeconds } / 60

    val weeklyGamingMinutes: Int
        get() {
            val weekStart = todayEpochDay - 6
            return activityLogs
                .filter { it.dateEpochDay >= weekStart && it.activityType == StudyActivityType.PLAYING_GAME }
                .sumOf { it.actualSeconds } / 60
        }

    val weeklySocialMinutes: Int
        get() {
            val weekStart = todayEpochDay - 6
            return activityLogs
                .filter { it.dateEpochDay >= weekStart && it.activityType == StudyActivityType.ENTERTAINMENT }
                .sumOf { it.actualSeconds } / 60
        }

    val weeklyPlannedMinutes: Int
        get() {
            val weekStart = todayEpochDay - 6
            return activityLogs
                .filter { it.dateEpochDay >= weekStart }
                .sumOf { it.plannedSeconds } / 60
        }

    val weeklyActualMinutes: Int
        get() {
            val weekStart = todayEpochDay - 6
            return activityLogs
                .filter { it.dateEpochDay >= weekStart }
                .sumOf { it.actualSeconds } / 60
        }

    val weeklyCategoryMinutes: Map<ActivityCategory, Int> by lazy(LazyThreadSafetyMode.NONE) {
        val weekStart = todayEpochDay - 6
        val recentLogs = activityLogs.filter { it.dateEpochDay >= weekStart }
        val result = linkedMapOf<ActivityCategory, Int>()
        ActivityCategory.entries.forEach { cat ->
            val fromLogs = recentLogs.filter { it.category == cat }.sumOf { it.actualSeconds } / 60
            // Ensure focused study reflects existing focus_sessions even if logged before v2 migration
            result[cat] = if (cat == ActivityCategory.STUDY) {
                maxOf(fromLogs, weeklyFocusMinutes)
            } else {
                fromLogs
            }
        }
        result
    }

    /**
     * Real, locally computed insights derived strictly from recorded user sessions & activity logs.
     * Never invents data or displays mock percentages.
     */
    val smartLocalInsights: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        val insights = mutableListOf<String>()
        val weekStart = todayEpochDay - 6
        val prevWeekStart = todayEpochDay - 13

        if (todaySessionsCount > 0) {
            insights += "You completed $todaySessionsCount focused study ${if (todaySessionsCount == 1) "block" else "blocks"} ($todayFocusMinutes min) today."
        } else {
            insights += "No focused study blocks logged yet today — even a calm 15-minute start builds momentum."
        }

        // Daily target comparison
        val remainingToTarget = (preferences.dailyTargetMinutes - todayFocusMinutes).coerceAtLeast(0)
        if (todayFocusMinutes >= preferences.dailyTargetMinutes) {
            insights += "You reached your ${preferences.dailyTargetMinutes} min daily study target today."
        } else if (todayFocusMinutes > 0) {
            insights += "$remainingToTarget min left to reach today's ${preferences.dailyTargetMinutes} min focus target."
        }

        // Subject week-over-week real comparison
        val topThisWeek = focusOnlySessions
            .filter { it.dateEpochDay >= weekStart }
            .groupBy { it.subjectName }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds } / 60 }
            .maxByOrNull { it.value }
        if (topThisWeek != null && topThisWeek.value > 0) {
            val prevMins = focusOnlySessions
                .filter {
                    it.dateEpochDay in prevWeekStart until weekStart &&
                        it.subjectName.equals(topThisWeek.key, ignoreCase = true)
                }
                .sumOf { it.durationSeconds } / 60
            if (prevMins > 0) {
                val delta = topThisWeek.value - prevMins
                if (delta > 0) {
                    insights += "${topThisWeek.key} is up +$delta min compared to last week."
                } else if (delta < 0) {
                    insights += "${topThisWeek.key} is ${-delta} min lighter than last week."
                } else {
                    insights += "${topThisWeek.key} is holding steady at ${topThisWeek.value} min this week."
                }
            } else {
                insights += "Most focused subject this week: ${topThisWeek.key} (${topThisWeek.value} min)."
            }
        }

        // Breaks & Entertainment separation note
        val todayBreaks = todayActivityLogs.filter { it.category == ActivityCategory.BREAK }.sumOf { it.actualMinutes }
        val todayEntertainment = todayEntertainmentMinutes
        if (todayBreaks > 0 || todayEntertainment > 0) {
            val parts = buildList {
                if (todayBreaks > 0) add("$todayBreaks min breaks")
                if (todayEntertainment > 0) add("$todayEntertainment min gaming/entertainment")
            }
            insights += "Tracked separately today: ${parts.joinToString(" · ")} (never mixed into focused study)."
        }

        insights.take(4)
    }

    val todayEntertainmentMinutes: Int
        get() = todayActivityLogs
            .filter { it.category == ActivityCategory.ENTERTAINMENT }
            .sumOf { it.actualSeconds } / 60

    val dailyEntertainmentLimitMinutes: Int
        get() = preferences.focusGuardSettings.allowances.dailyEntertainmentLimitMinutes

    val isDailyEntertainmentLimitReached: Boolean
        get() = todayEntertainmentMinutes >= dailyEntertainmentLimitMinutes

    val subjectSummaries: List<SubjectStudySummary> by lazy(LazyThreadSafetyMode.NONE) {
        val weekStart = todayEpochDay - 6
        subjects.map { subject ->
            val matchingSessions = focusOnlySessions.filter {
                it.subjectId == subject.id ||
                    (it.subjectId == null && it.subjectName.equals(subject.name, ignoreCase = true))
            }
            val weekMins = matchingSessions
                .filter { it.dateEpochDay >= weekStart }
                .sumOf { it.durationSeconds } / 60
            val totalMins = matchingSessions.sumOf { it.durationSeconds } / 60
            val subjectTasks = tasks.filter { it.subjectId == subject.id }
            SubjectStudySummary(
                subject = subject,
                loggedMinutesThisWeek = weekMins,
                loggedMinutesTotal = totalMins,
                completedTasks = subjectTasks.count { it.isCompleted },
                totalTasks = subjectTasks.size
            )
        }
    }

    val upcomingDeadlines: List<UpcomingDeadlineItem> by lazy(LazyThreadSafetyMode.NONE) {
        val taskDeadlines = tasks
            .filter { !it.isCompleted && it.dueDateEpochDay >= todayEpochDay }
            .map { task ->
                val subject = subjects.firstOrNull { it.id == task.subjectId }
                UpcomingDeadlineItem(
                    id = "task_${task.id}",
                    title = task.title,
                    subtitle = "${subject?.name ?: "Study Task"} · ${task.dueTimeLabel}",
                    dateEpochDay = task.dueDateEpochDay,
                    accentColor = subject?.accentColor ?: androidx.compose.ui.graphics.Color(0xFF7B5CFA),
                    isExam = false
                )
            }

        val examDeadlines = subjects
            .filter { it.examDateEpochDay != null && it.examDateEpochDay >= todayEpochDay }
            .map { subject ->
                UpcomingDeadlineItem(
                    id = "exam_${subject.id}",
                    title = "${subject.name} Exam",
                    subtitle = "Scheduled exam date",
                    dateEpochDay = subject.examDateEpochDay!!,
                    accentColor = subject.accentColor,
                    isExam = true
                )
            }

        (taskDeadlines + examDeadlines).sortedBy { it.dateEpochDay }
    }
}

class BrainBufferViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = BrainBufferRepository.getInstance(appContext)

    private val deviceSignals: DevicePerformanceSignals = PerformanceProfileEngine.inspectDevice(appContext)
    private val ephemeral = MutableStateFlow(
        EphemeralUiState(
            hasUsageAccessPermission = AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)
        )
    )
    private var tickerJob: Job? = null
    private var externalTimerSyncJob: Job? = null
    private var isCompletingSession = false
    private val completedSessionIdsInMemory = mutableSetOf<String>()
    private val snapshotPersistMutex = Mutex()
    private val inFlightSnapshotWrites = AtomicInteger(0)

    private val timerController: FocusTimerEngine = FocusTimerEngine(
        scope = viewModelScope,
        clock = SystemTimerClock,
        onNaturalCompletion = { completedSnap, _ ->
            completeCurrentSession(autoCompleted = true, overrideSnapshot = completedSnap)
        }
    )

    val soundscapeController: com.example.audio.AmbientSoundscapeController =
        com.example.audio.AmbientSoundscapeController(
            context = appContext,
            onPreferencesChanged = { selected, volume, syncWithTimer ->
                viewModelScope.launch {
                    repository.updateSoundscapeSettings(
                        selectedKey = selected.name,
                        volume = volume,
                        syncWithTimer = syncWithTimer
                    )
                }
            }
        )

    private data class CoreDataBundle(
        val prefs: UserPreferences,
        val subjects: List<Subject>,
        val tasks: List<StudyTask>,
        val sessions: List<FocusSessionRecord>,
        val plants: List<GardenPlant>
    )

    private data class ExtendedDataBundle(
        val mindNotes: List<MindNote>,
        val activityLogs: List<ActivityLogRecord>,
        val strictFocusPresets: List<StrictFocusPreset>,
        val customActivities: List<CustomActivityOption>,
        val timerTick: FocusTimerTick
    )

    private val coreFlow = combine(
        repository.preferences,
        repository.subjects,
        repository.tasks,
        repository.focusSessions,
        repository.gardenPlants
    ) { prefs, subjects, tasks, sessions, plants ->
        CoreDataBundle(prefs, subjects, tasks, sessions, plants)
    }

    private val extendedFlow = combine(
        repository.mindNotes,
        repository.activityLogs,
        repository.strictFocusPresets,
        repository.customActivities,
        timerController.tickState
    ) { mindNotes, activityLogs, presets, customActs, timerTick ->
        ExtendedDataBundle(mindNotes, activityLogs, presets, customActs, timerTick)
    }

    private val authClient = SupabaseAuthClient()
    private val phoneUsageReportFlow = MutableStateFlow(PhoneUsageReport())
    private val cloudMetaFlow = MutableStateFlow(
        Triple(false, false, Pair<String?, String?>(null, null))
    ) // Triple(isSyncing, isAuthBusy, Pair(lastError, authNotice))

    private data class FeatureDataBundle(
        val appMappings: List<com.example.model.AppCategoryMapping>,
        val blockedApps: List<BlockedAppRule>,
        val studyResources: List<StudyResource>,
        val authSession: SupabaseAuthSession,
        val syncMeta: Pair<Int, Long>
    )

    private data class PurposeDataBundle(
        val purposeSessions: List<PurposeSessionRecord>,
        val interventionEvents: List<InterventionEventRecord>,
        val activeTempAccess: TemporaryAppAccessSession?
    )

    private data class AnalyticsAndBlockingBundle(
        val usageReport: PhoneUsageReport,
        val cloudMeta: Triple<Boolean, Boolean, Pair<String?, String?>>,
        val manualBlocking: com.example.model.ManualBlockingSession,
        val tempAllowances: List<com.example.model.TemporaryAppAllowance>,
        val purposeData: PurposeDataBundle
    )

    private val purposeDataFlow = combine(
        repository.purposeSessions,
        repository.interventionEvents,
        repository.activeTempAccess
    ) { pSessions, iEvents, activeTemp ->
        PurposeDataBundle(pSessions, iEvents, activeTemp)
    }

    private val syncMetaFlow = combine(
        repository.pendingOutboxCount,
        repository.lastSyncedAtMs
    ) { pending, lastSynced -> pending to lastSynced }

    private val featureFlow = combine(
        repository.appCategoryMappings,
        repository.blockedApps,
        repository.studyResources,
        repository.authSession,
        syncMetaFlow
    ) { mappings, blocked, resources, authSession, syncMeta ->
        FeatureDataBundle(mappings, blocked, resources, authSession, syncMeta)
    }

    private val analyticsAndCloudFlow = combine(
        phoneUsageReportFlow,
        cloudMetaFlow,
        repository.manualBlockingSession,
        repository.temporaryAllowances,
        purposeDataFlow
    ) { report, meta, manualBlocking, allowances, purposeData ->
        AnalyticsAndBlockingBundle(report, meta, manualBlocking, allowances, purposeData)
    }

    val uiState: StateFlow<BrainBufferUiState> = combine(
        coreFlow,
        extendedFlow,
        featureFlow,
        analyticsAndCloudFlow,
        ephemeral
    ) { core, ext, feat, analyticsAndBlocking, local ->
        val usageReport = analyticsAndBlocking.usageReport
        val cloudMeta = analyticsAndBlocking.cloudMeta
        val (isSyncing, isAuthBusy, errAndNotice) = cloudMeta
        val (lastErr, authNotice) = errAndNotice
        val (pendingCount, lastSyncedMs) = feat.syncMeta

        val cloudStatus = when {
            !SupabaseConfig.isConfigured -> CloudSyncStatus.UNCONFIGURED
            !feat.authSession.isSignedIn -> CloudSyncStatus.OFFLINE_GUEST
            isSyncing -> CloudSyncStatus.SYNCING
            !lastErr.isNullOrBlank() -> CloudSyncStatus.ERROR
            pendingCount > 0 -> CloudSyncStatus.PENDING
            else -> CloudSyncStatus.IDLE
        }

        val tick = ext.timerTick
        val nowEpoch = maxOf(local.nowEpochMs, tick.nowEpochMs)
        val nowElapsed = maxOf(local.nowElapsedMs, tick.nowElapsedRealtimeMs)
        val remaining = tick.remainingSeconds
        val progress = tick.progress
        val sessionState = com.example.domain.FocusSessionStateMachine.deriveState(
            snapshot = tick.snapshot,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed,
            isRequestingActivity = local.isRequestingActivity,
            wasRecentlyCancelled = local.wasRecentlyCancelled
        )
        val resolvedProfile = PerformanceProfileEngine.resolveProfile(
            signals = deviceSignals,
            override = core.prefs.performanceOverride
        )

        BrainBufferUiState(
            preferences = core.prefs,
            subjects = core.subjects,
            tasks = core.tasks,
            sessions = core.sessions,
            plants = core.plants,
            mindNotes = ext.mindNotes,
            activityLogs = ext.activityLogs,
            strictFocusPresets = ext.strictFocusPresets,
            customActivities = ext.customActivities,
            appCategoryMappings = feat.appMappings,
            blockedApps = feat.blockedApps,
            studyResources = feat.studyResources,
            purposeSessions = analyticsAndBlocking.purposeData.purposeSessions,
            interventionEvents = analyticsAndBlocking.purposeData.interventionEvents,
            activeTempAccess = analyticsAndBlocking.purposeData.activeTempAccess,
            expiredTempAccessPrompt = local.expiredTempAccessPrompt,
            preselectedPurpose = local.preselectedPurpose,
            tempAccessNotice = local.tempAccessNotice,
            manualBlockingSession = analyticsAndBlocking.manualBlocking,
            temporaryAllowances = analyticsAndBlocking.tempAllowances,
            hasUsageAccessPermission = local.hasUsageAccessPermission,
            nowEpochMs = nowEpoch,
            nowElapsedMs = nowElapsed,
            phoneUsageReport = usageReport,
            cloudSync = CloudSyncState(
                isConfigured = SupabaseConfig.isConfigured,
                status = cloudStatus,
                session = feat.authSession,
                pendingOutboxCount = pendingCount,
                lastSyncedAtMs = lastSyncedMs,
                lastError = lastErr,
                isAuthBusy = isAuthBusy,
                authNotice = authNotice
            ),
            timer = FocusTimerUiState(
                snapshot = tick.snapshot,
                remainingSeconds = remaining,
                progress = progress,
                isZenMode = local.isZenMode,
                sessionState = sessionState,
                elapsedFocusedSeconds = tick.elapsedSeconds
            ),
            quoteIndex = local.quoteIndex,
            pendingCelebration = local.pendingCelebration,
            gardenSparkleTrigger = local.gardenSparkleTrigger,
            appUsageSuggestion = local.appUsageSuggestion,
            unapprovedAppWarning = local.unapprovedAppWarning,
            deviceSignals = deviceSignals,
            resolvedPerformanceProfile = resolvedProfile
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BrainBufferUiState(
            hasUsageAccessPermission = AppUsageAwarenessHelper.hasUsageAccessPermission(appContext),
            deviceSignals = deviceSignals,
            resolvedPerformanceProfile = PerformanceProfileEngine.resolveProfile(deviceSignals)
        )
    )

    val themeShellState: StateFlow<ThemeShellState> = repository.preferences
        .map { prefs ->
            ThemeShellState(
                themeMode = prefs.themeMode,
                reducedMotionOverride = prefs.reducedMotionOverride,
                performanceOverride = prefs.performanceOverride,
                isLoaded = prefs.isLoaded,
                onboardingCompleted = prefs.onboardingCompleted
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeShellState()
        )

    init {
        startExternalTimerReconciliation()
        startAuxiliaryCountdownTicker()
        viewModelScope.launch {
            val savedPrefs = repository.getPreferencesOnce()
            soundscapeController.hydratePreferences(
                selected = com.example.audio.AmbientSoundscape.fromStorageKey(savedPrefs.selectedSoundscapeKey),
                volume = savedPrefs.soundscapeVolume,
                syncWithTimer = savedPrefs.soundscapeSyncWithTimer
            )
            syncTimerOnResume()
            if (AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)) {
                refreshPhoneUsageAnalytics()
            }
            if (SupabaseConfig.isConfigured && repository.getAuthSessionOnce().isSignedIn) {
                syncCloudNow()
            }
        }
        viewModelScope.launch {
            var previouslyStarted = false
            timerController.tickState
                .map { tick -> tick.snapshot.hasStarted to tick.isRunning }
                .distinctUntilChanged()
                .collect { (hasStarted, isRunning) ->
                    if (previouslyStarted && !hasStarted) {
                        soundscapeController.onSessionEnded()
                    } else {
                        soundscapeController.onTimerRunningChanged(hasStarted && isRunning)
                    }
                    previouslyStarted = hasStarted
                }
        }
    }

    private fun persistTimerSnapshot(snapshot: PersistedTimerSnapshot) {
        inFlightSnapshotWrites.incrementAndGet()
        viewModelScope.launch {
            try {
                snapshotPersistMutex.withLock {
                    repository.saveTimerSnapshot(snapshot)
                }
            } finally {
                inFlightSnapshotWrites.decrementAndGet()
            }
        }
    }

    private suspend fun persistTimerSnapshotSuspend(snapshot: PersistedTimerSnapshot) {
        inFlightSnapshotWrites.incrementAndGet()
        try {
            snapshotPersistMutex.withLock {
                repository.saveTimerSnapshot(snapshot)
            }
        } finally {
            inFlightSnapshotWrites.decrementAndGet()
        }
    }

    /**
     * Reconciles persisted snapshots from DataStore (process recreation, Notification Shade
     * Pause/Resume/Extend actions, or background [FocusTimerService] completion) into the single
     * authoritative [timerController] without ever overwriting an in-flight local mutation.
     */
    private fun startExternalTimerReconciliation() {
        externalTimerSyncJob?.cancel()
        externalTimerSyncJob = viewModelScope.launch {
            repository.timerSnapshot.collect { persisted ->
                if (inFlightSnapshotWrites.get() == 0) {
                    timerController.reconcileExternalSnapshot(persisted)
                }
            }
        }
    }

    /**
     * Lightweight auxiliary clock ticker strictly for Temporary App Access and Manual Blocking
     * countdowns. The primary focus timer ticks independently inside [timerController] via
     * `SystemClock.elapsedRealtime()` so there are never competing loops and zero wakeups when idle.
     */
    private fun startAuxiliaryCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            combine(
                repository.manualBlockingSession,
                repository.activeTempAccess
            ) { manual, tempAccess ->
                manual to tempAccess
            }.collectLatest { (manual, activeTemp) ->
                val manualActive = manual.isActive && !manual.isPaused
                val tempOpen = activeTemp != null && activeTemp.endedAtEpochMs == null
                if (!manualActive && !tempOpen) return@collectLatest
                while (isActive) {
                    val nowEpoch = System.currentTimeMillis()
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val manualStillActive = manual.isEnforcing(nowEpoch)
                    if (manual.isActive && !manual.isPaused && manual.endsAtEpochMs > 0L && nowEpoch >= manual.endsAtEpochMs) {
                        repository.saveManualBlockingSession(com.example.model.ManualBlockingSession())
                    }
                    if (activeTemp != null && activeTemp.endedAtEpochMs == null && activeTemp.isExpired(nowEpoch)) {
                        if (ephemeral.value.expiredTempAccessPrompt?.id != activeTemp.id) {
                            val prefs = uiState.value.preferences
                            BrainBufferHaptics.perform(
                                context = appContext,
                                event = HapticEvent.DISTRACTION_ALERT,
                                enabled = prefs.hapticsEnabled && prefs.focusGuardSettings.vibrationEnabled
                            )
                            ephemeral.update { it.copy(expiredTempAccessPrompt = activeTemp) }
                        }
                    }
                    val tempStillRunning = activeTemp != null && activeTemp.endedAtEpochMs == null && !activeTemp.isExpired(nowEpoch)
                    ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
                    if (!manualStillActive && !tempStillRunning) break
                    delay(1000L)
                }
            }
        }
    }

    /**
     * Called when Activity resumes, returns from Android Settings, unlocks screen, or recreates process:
     * - Immediately resyncs [timerController] against `SystemClock.elapsedRealtime()`.
     * - Reconciles persisted DataStore state (e.g., notification shade actions or process death).
     * - Restores timer safely via [FocusTimerEngine.restoreAfterProcessRecreation] (never restarts a completed timer).
     * - Refreshes real Android Usage Access permission state and prunes expired temporary app allowances.
     */
    fun syncTimerOnResume() {
        val tick = timerController.syncWithClock()
        val nowEpoch = tick.nowEpochMs
        val nowElapsed = tick.nowElapsedRealtimeMs
        val usageGranted = AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                hasUsageAccessPermission = usageGranted
            )
        }
        viewModelScope.launch {
            val snap = repository.getTimerSnapshotOnce()
            if (inFlightSnapshotWrites.get() == 0) {
                timerController.reconcileExternalSnapshot(snap)
            }
            val authoritativeSnap = timerController.snapshot
            val manual = repository.getManualBlockingSessionOnce()
            val allowances = repository.getTemporaryAllowancesOnce()
            val activeAllowances = allowances.filter { it.isActive(nowEpoch) }
            if (activeAllowances.size != allowances.size) {
                repository.saveTemporaryAllowances(activeAllowances)
            }
            if (manual.isActive && !manual.isPaused && manual.endsAtEpochMs > 0L && nowEpoch >= manual.endsAtEpochMs) {
                repository.saveManualBlockingSession(com.example.model.ManualBlockingSession())
            }

            when (val restored = FocusTimerEngine.restoreAfterProcessRecreation(
                snapshot = authoritativeSnap,
                nowEpochMs = nowEpoch,
                nowElapsedRealtimeMs = nowElapsed,
                completedSessionIds = completedSessionIdsInMemory + timerController.snapshotCompletedSessionIds()
            )) {
                is FocusTimerEngine.ProcessRestorationResult.NaturalCompletionPending -> {
                    if (!isCompletingSession) {
                        completeCurrentSession(autoCompleted = true, overrideSnapshot = authoritativeSnap)
                    }
                }
                is FocusTimerEngine.ProcessRestorationResult.AlreadyCompleted -> {
                    if (authoritativeSnap.hasStarted || authoritativeSnap.runningSinceEpochMs != null) {
                        timerController.applyTransition { _, _, _ -> restored.snapshot }
                        persistTimerSnapshotSuspend(restored.snapshot)
                    }
                }
                is FocusTimerEngine.ProcessRestorationResult.Running,
                is FocusTimerEngine.ProcessRestorationResult.Paused -> {
                    FocusTimerService.syncWithSnapshot(appContext, authoritativeSnap)
                }
                is FocusTimerEngine.ProcessRestorationResult.Idle -> {
                    if (manual.isEnforcing(nowEpoch) && usageGranted) {
                        FocusTimerService.syncForManualBlocking(appContext, manual)
                    }
                }
            }
        }
    }

    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = uiState.value.preferences.displayName
        val prefix = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Quiet night"
        }
        return "$prefix, $name"
    }

    fun getFormattedTodayDate(): String {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }

    fun nextMotivationalMessage() {
        ephemeral.update {
            it.copy(quoteIndex = (it.quoteIndex + 1) % BrainBufferRepository.motivationalNotes.size)
        }
    }

    // region Onboarding, Profile, Performance & Notification Preferences

    fun completeOnboarding(
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        subjects: List<Subject>,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                studentName = studentName,
                avatarKey = avatarKey,
                studyLevel = studyLevel,
                courseOrExamName = courseOrExamName,
                initialSubjects = subjects,
                studyGoals = studyGoals,
                customGoal = customGoal,
                preferredFocusMinutes = preferredFocusMinutes,
                shortBreakMinutes = shortBreakMinutes,
                preferredStudyTime = preferredStudyTime,
                weeklyStudyDaysTarget = weeklyStudyDaysTarget,
                biggestDistractions = biggestDistractions,
                otherDistraction = otherDistraction,
                dailyTargetMinutes = dailyTargetMinutes
            )
            onDone()
        }
    }

    fun saveOnboardingCurrentStep(step: Int) {
        viewModelScope.launch {
            repository.saveOnboardingCurrentStep(step)
        }
    }

    fun saveOnboardingDraft(
        step: Int,
        userRole: UserProfileRole,
        studentName: String,
        selectedPurposes: Set<SessionPurposeCategory>,
        dailyTargetMinutes: Int,
        preferredFocusMinutes: Int
    ) {
        viewModelScope.launch {
            repository.saveOnboardingDraft(
                step = step,
                userRole = userRole,
                studentName = studentName,
                selectedPurposes = selectedPurposes,
                dailyTargetMinutes = dailyTargetMinutes,
                preferredFocusMinutes = preferredFocusMinutes
            )
        }
    }

    fun completeFullOnboarding(
        userRole: UserProfileRole,
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        subjects: List<Subject>,
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
        focusGuardSettings: com.example.model.FocusGuardSettings,
        notificationPrefs: NotificationPreferences,
        selectedBlockedApps: List<BlockedAppRule>,
        appUsageAwarenessEnabled: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.completeFullOnboarding(
                userRole = userRole,
                studentName = studentName,
                avatarKey = avatarKey,
                studyLevel = studyLevel,
                courseOrExamName = courseOrExamName,
                initialSubjects = subjects,
                studyGoals = studyGoals,
                customGoal = customGoal,
                preferredFocusMinutes = preferredFocusMinutes,
                shortBreakMinutes = shortBreakMinutes,
                longBreakMinutes = longBreakMinutes,
                preferredStudyTime = preferredStudyTime,
                weeklyStudyDaysTarget = weeklyStudyDaysTarget,
                biggestDistractions = biggestDistractions,
                otherDistraction = otherDistraction,
                dailyTargetMinutes = dailyTargetMinutes,
                focusGuardSettings = focusGuardSettings,
                notificationPrefs = notificationPrefs,
                selectedBlockedApps = selectedBlockedApps,
                appUsageAwarenessEnabled = appUsageAwarenessEnabled
            )
            FocusScheduleWorker.syncSchedule(appContext, focusGuardSettings)
            StudyReminderWorker.syncSchedule(appContext, true, notificationPrefs)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
            if (AppUsageAwarenessHelper.hasUsageStatsPermission(appContext)) {
                refreshPhoneUsageAnalytics()
            }
            onDone()
        }
    }

    fun updateProfile(
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
        viewModelScope.launch {
            repository.updateProfile(
                studentName = studentName,
                avatarKey = avatarKey,
                studyLevel = studyLevel,
                courseOrExamName = courseOrExamName,
                studyGoals = studyGoals,
                customGoal = customGoal,
                preferredStudyTime = preferredStudyTime,
                weeklyStudyDaysTarget = weeklyStudyDaysTarget,
                biggestDistractions = biggestDistractions,
                otherDistraction = otherDistraction
            )
        }
    }

    fun updateTimerAndTargetPrefs(
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyTargetMinutes: Int
    ) {
        viewModelScope.launch {
            repository.updateTimerAndTargetPrefs(
                preferredFocusMinutes = preferredFocusMinutes,
                shortBreakMinutes = shortBreakMinutes,
                longBreakMinutes = longBreakMinutes,
                dailyTargetMinutes = dailyTargetMinutes
            )
            val snap = timerController.snapshot
            if (!snap.hasStarted && snap.runningSinceEpochMs == null) {
                val mins = when (snap.mode) {
                    FocusMode.FOCUS -> preferredFocusMinutes
                    FocusMode.SHORT_BREAK -> shortBreakMinutes
                    FocusMode.LONG_BREAK -> longBreakMinutes
                }
                val updated = timerController.applyTransition { current, _, _ ->
                    if (!current.hasStarted && current.runningSinceEpochMs == null) {
                        FocusTimerEngine.configureMode(current, current.mode, mins)
                    } else {
                        current
                    }
                }
                persistTimerSnapshotSuspend(updated)
            }
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun updatePerformanceOverride(override: PerformanceProfileOverride) {
        viewModelScope.launch { repository.updatePerformanceOverride(override) }
    }

    fun updateNotificationPreferences(notificationPrefs: NotificationPreferences) {
        viewModelScope.launch {
            repository.updateNotificationPreferences(notificationPrefs)
            StudyReminderWorker.syncSchedule(
                appContext,
                uiState.value.preferences.notificationsEnabled,
                notificationPrefs
            )
            val snap = uiState.value.timer.snapshot
            if (snap.hasStarted) {
                if (notificationPrefs.activeFocusEnabled && uiState.value.preferences.notificationsEnabled) {
                    FocusTimerService.syncWithSnapshot(appContext, snap)
                } else {
                    FocusTimerService.stopService(appContext)
                }
            }
        }
    }

    fun updateAppUsageAwareness(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAppUsageAwareness(enabled)
            if (!enabled) {
                ephemeral.update { it.copy(appUsageSuggestion = null) }
            } else {
                refreshAppUsageSuggestion()
            }
        }
    }

    private var usageSuggestionJob: Job? = null

    fun refreshAppUsageSuggestion() {
        val state = uiState.value
        val prefs = state.preferences
        if (!prefs.appUsageAwarenessEnabled) {
            usageSuggestionJob?.cancel()
            ephemeral.update { it.copy(appUsageSuggestion = null, unapprovedAppWarning = null) }
            return
        }
        if (usageSuggestionJob?.isActive == true) return
        val mappings = state.appCategoryMappings
        val snap = state.timer.snapshot
        val activeTemp = state.activeTempAccess
        val hapticsAllowed = prefs.hapticsEnabled && prefs.focusGuardSettings.vibrationEnabled
        val graceSec = prefs.focusGuardSettings.gracePeriodSeconds
        val cooldownSec = prefs.focusGuardSettings.distractionCooldownSeconds

        usageSuggestionJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val suggestion = AppUsageAwarenessHelper.detectRecentAppSuggestion(
                context = appContext,
                enabledByUser = true,
                userMappings = mappings
            )
            val nowEpoch = System.currentTimeMillis()
            val warning = if (snap.hasStarted && snap.runningSinceEpochMs != null && !snap.isOnBreak) {
                val fgPkg = AppUsageAwarenessHelper.queryCurrentForegroundPackage(
                    context = appContext,
                    enabledByUser = true,
                    nowEpochMs = nowEpoch
                )
                if (fgPkg != null) {
                    val fgLabel = runCatching {
                        val pm = appContext.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(fgPkg, 0)).toString()
                    }.getOrDefault(fgPkg.substringAfterLast('.'))
                    when (
                        val match = PurposeMatchingEngine.evaluateForegroundApp(
                            observedPackage = fgPkg,
                            observedAppLabel = fgLabel,
                            snapshot = snap,
                            activeTempAccess = activeTemp,
                            nowEpochMs = nowEpoch,
                            ownPackageName = appContext.packageName,
                            gracePeriodSeconds = graceSec,
                            cooldownSeconds = cooldownSec,
                            enforceGracePeriod = false
                        )
                    ) {
                        is PurposeMatchingEngine.MatchDecision.PotentialDistraction -> {
                            val observedCat = AppUsageAwarenessHelper
                                .matchPackageToSuggestion(fgPkg, mappings)
                                ?.suggestedActivity
                                ?.category
                                ?: ActivityCategory.OTHER
                            com.example.model.UnapprovedAppWarning(
                                packageName = match.packageName,
                                appDisplayName = match.appLabel,
                                observedCategory = observedCat,
                                message = match.message
                            )
                        }
                        else -> null
                    }
                } else {
                    null
                }
            } else {
                null
            }
            val isNewWarning = warning != null &&
                warning.packageName != ephemeral.value.unapprovedAppWarning?.packageName
            if (isNewWarning) {
                BrainBufferHaptics.perform(
                    context = appContext,
                    event = HapticEvent.DISTRACTION_ALERT,
                    enabled = hapticsAllowed
                )
            }
            ephemeral.update { current ->
                val filteredSuggestion = if (suggestion != null && suggestion.packageName == current.dismissedSuggestionPkg) {
                    current.appUsageSuggestion
                } else {
                    suggestion
                }
                current.copy(
                    appUsageSuggestion = filteredSuggestion,
                    unapprovedAppWarning = warning
                )
            }
        }
    }

    fun dismissAppUsageSuggestion() {
        ephemeral.update { current ->
            current.copy(
                appUsageSuggestion = null,
                dismissedSuggestionPkg = current.appUsageSuggestion?.packageName ?: current.dismissedSuggestionPkg
            )
        }
    }

    fun dismissUnapprovedAppWarning() {
        ephemeral.update { it.copy(unapprovedAppWarning = null) }
    }

    fun updateFocusGuardSettings(settings: com.example.model.FocusGuardSettings) {
        viewModelScope.launch {
            repository.updateFocusGuardSettings(settings)
            FocusScheduleWorker.syncSchedule(appContext, settings)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun upsertAppCategoryMapping(mapping: com.example.model.AppCategoryMapping) {
        viewModelScope.launch {
            repository.upsertAppCategoryMapping(mapping)
        }
    }

    fun deleteAppCategoryMapping(packageName: String) {
        viewModelScope.launch {
            repository.deleteAppCategoryMapping(packageName)
        }
    }

    fun setActivityRequestSheetOpen(open: Boolean) {
        ephemeral.update { it.copy(isRequestingActivity = open) }
    }

    fun updateAppSettings(
        notificationsEnabled: Boolean = uiState.value.preferences.notificationsEnabled,
        breakRemindersEnabled: Boolean = uiState.value.preferences.breakRemindersEnabled,
        soundEnabled: Boolean = uiState.value.preferences.soundEnabled,
        hapticsEnabled: Boolean = uiState.value.preferences.hapticsEnabled,
        reducedMotionOverride: Boolean = uiState.value.preferences.reducedMotionOverride
    ) {
        viewModelScope.launch {
            repository.updateAppSettings(
                notificationsEnabled = notificationsEnabled,
                breakRemindersEnabled = breakRemindersEnabled,
                soundEnabled = soundEnabled,
                hapticsEnabled = hapticsEnabled,
                reducedMotionOverride = reducedMotionOverride
            )
            StudyReminderWorker.syncSchedule(
                appContext,
                notificationsEnabled,
                uiState.value.preferences.notificationPrefs
            )
            val snap = uiState.value.timer.snapshot
            if (snap.hasStarted) {
                if (notificationsEnabled && uiState.value.preferences.notificationPrefs.activeFocusEnabled) {
                    FocusTimerService.syncWithSnapshot(appContext, snap)
                } else {
                    FocusTimerService.stopService(appContext)
                }
            }
        }
    }

    fun restartOnboarding(onReady: () -> Unit) {
        viewModelScope.launch {
            repository.setOnboardingCompleted(false)
            onReady()
        }
    }

    // endregion

    // region Subjects & Tasks CRUD

    fun saveSubject(subject: Subject) {
        if (subject.name.isBlank()) return
        viewModelScope.launch {
            repository.upsertSubject(subject.copy(name = subject.name.trim()))
        }
    }

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch {
            repository.deleteSubject(subjectId)
            val snap = timerController.snapshot
            if (snap.selectedSubjectId == subjectId) {
                val updated = timerController.applyTransition { current, _, _ ->
                    if (current.selectedSubjectId == subjectId) current.copy(selectedSubjectId = null) else current
                }
                persistTimerSnapshotSuspend(updated)
            }
        }
    }

    fun saveTask(
        existingId: Long?,
        title: String,
        subjectId: Long?,
        dueDateEpochDay: Long,
        dueTimeLabel: String,
        estimatedMinutes: Int,
        priority: TaskPriority,
        notes: String
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val existing = existingId?.let { id -> uiState.value.tasks.firstOrNull { it.id == id } }
            val task = StudyTask(
                id = existingId ?: 0L,
                title = clean,
                subjectId = subjectId,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeLabel = dueTimeLabel.ifBlank { "6:00 PM" },
                estimatedMinutes = estimatedMinutes.coerceIn(5, 240),
                priority = priority,
                isCompleted = existing?.isCompleted ?: false,
                completedAtMs = existing?.completedAtMs,
                notes = notes.trim()
            )
            repository.upsertTask(task)
        }
    }

    fun toggleTaskCompletion(taskId: Long) {
        val target = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        val nowComplete = !target.isCompleted
        viewModelScope.launch {
            repository.upsertTask(
                target.copy(
                    isCompleted = nowComplete,
                    completedAtMs = if (nowComplete) System.currentTimeMillis() else null
                )
            )
        }
    }

    fun deleteTask(taskId: Long) {
        val target = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        ephemeral.update { it.copy(recentlyDeletedTask = target) }
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun undoDeleteTask() {
        val deleted = ephemeral.value.recentlyDeletedTask ?: return
        ephemeral.update { it.copy(recentlyDeletedTask = null) }
        viewModelScope.launch {
            repository.upsertTask(deleted.copy(id = 0L))
        }
    }

    // endregion

    // region Smart Persistent Timer, Activity Switching & Voluntary Strict Focus

    fun selectFocusMode(mode: FocusMode) {
        val prefs = uiState.value.preferences
        val minutes = when (mode) {
            FocusMode.FOCUS -> prefs.preferredFocusMinutes
            FocusMode.SHORT_BREAK -> prefs.shortBreakMinutes
            FocusMode.LONG_BREAK -> prefs.longBreakMinutes
        }
        val next = timerController.applyTransition { current, _, _ ->
            FocusTimerEngine.configureMode(current, mode, minutes)
        }
        ephemeral.update {
            it.copy(nowEpochMs = System.currentTimeMillis(), nowElapsedMs = SystemClock.elapsedRealtime())
        }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(next)
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
        }
    }

    fun setCustomTimerDuration(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        val next = timerController.applyTransition { current, _, _ ->
            FocusTimerEngine.configureMode(current, current.mode, clamped)
        }
        ephemeral.update {
            it.copy(nowEpochMs = System.currentTimeMillis(), nowElapsedMs = SystemClock.elapsedRealtime())
        }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(next)
            FocusTimerService.stopService(appContext)
        }
    }

    fun selectTimerSubject(subjectId: Long?) {
        val updated = timerController.applyTransition { current, _, _ ->
            current.copy(selectedSubjectId = subjectId)
        }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(updated)
            if (updated.hasStarted) FocusTimerService.syncWithSnapshot(appContext, updated)
        }
    }

    fun updateSessionGoal(goal: String) {
        val updated = timerController.applyTransition { current, _, _ ->
            current.copy(sessionGoal = goal.take(80))
        }
        persistTimerSnapshot(updated)
    }

    fun toggleZenMode() {
        ephemeral.update { it.copy(isZenMode = !it.isZenMode) }
    }

    private suspend fun syncStudySessionRecord(
        snapshot: PersistedTimerSnapshot,
        status: String,
        nowEpochMs: Long,
        nowElapsedMs: Long,
        endTimeMs: Long? = null
    ) {
        if (snapshot.sessionId.isBlank()) return
        val subjectName = uiState.value.subjects
            .firstOrNull { it.id == snapshot.selectedSubjectId }?.name ?: "General Study"
        val elapsedSec = FocusTimerEngine.actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedMs)
        repository.upsertStudySession(
            sessionKey = snapshot.sessionId,
            subjectId = snapshot.selectedSubjectId,
            subjectName = subjectName,
            startTimeMs = snapshot.activityStartedAtEpochMs ?: nowEpochMs,
            endTimeMs = endTimeMs,
            totalDurationSeconds = elapsedSec,
            plannedDurationSeconds = snapshot.totalSeconds,
            sessionStatus = status,
            focusGuardLevel = snapshot.focusGuardLevel.name,
            activityType = snapshot.activeActivityType.name
        )
    }

    fun togglePauseResume() {
        val currentlyRunning = timerController.snapshot.runningSinceEpochMs != null
        if (currentlyRunning) {
            pauseTimer()
        } else {
            startOrResumeTimer()
        }
    }

    fun startOrResumeTimer() {
        val hadStarted = timerController.snapshot.hasStarted
        val started = timerController.startOrResume()
        val nowEpoch = started.runningSinceEpochMs ?: System.currentTimeMillis()
        val nowElapsed = started.runningSinceElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        val hapticEvent = if (!hadStarted) HapticEvent.SESSION_START else HapticEvent.PAUSE_OR_RESUME
        BrainBufferHaptics.perform(
            context = appContext,
            event = hapticEvent,
            enabled = uiState.value.preferences.hapticsEnabled
        )
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed, wasRecentlyCancelled = false) }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(started)
            syncStudySessionRecord(started, "RUNNING", nowEpoch, nowElapsed)
            FocusTimerService.syncWithSnapshot(appContext, started)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun startQuickFocus(subjectId: Long? = null) {
        val prefs = uiState.value.preferences
        val running = timerController.applyTransition { currentSnap, nowEpoch, nowElapsed ->
            val base = if (currentSnap.mode != FocusMode.FOCUS || currentSnap.isSwitchedActivity) {
                FocusTimerEngine.configureMode(currentSnap, FocusMode.FOCUS, prefs.preferredFocusMinutes)
            } else {
                currentSnap
            }.let { snap ->
                if (subjectId != null) snap.copy(selectedSubjectId = subjectId) else snap
            }
            FocusTimerEngine.startOrResume(base, nowEpoch, nowElapsed)
        }
        val nowEpoch = running.runningSinceEpochMs ?: System.currentTimeMillis()
        val nowElapsed = running.runningSinceElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.SESSION_START,
            enabled = prefs.hapticsEnabled
        )
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed, wasRecentlyCancelled = false) }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(running)
            syncStudySessionRecord(running, "RUNNING", nowEpoch, nowElapsed)
            FocusTimerService.syncWithSnapshot(appContext, running)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    /**
     * Launches a Focus session pre-configured with a Planner task's subject, title, and estimated duration.
     */
    fun startFocusForTask(task: StudyTask) {
        val durationMin = task.estimatedMinutes.coerceIn(10, 120)
        val defaultGuardLevel = uiState.value.preferences.focusGuardSettings.defaultLevel
        val running = timerController.applyTransition { currentSnap, nowEpoch, nowElapsed ->
            val configured = FocusTimerEngine.configureMode(currentSnap, FocusMode.FOCUS, durationMin).copy(
                selectedSubjectId = task.subjectId,
                sessionGoal = task.title.take(80),
                linkedTaskId = task.id,
                focusGuardLevel = defaultGuardLevel
            )
            FocusTimerEngine.startOrResume(configured, nowEpoch, nowElapsed)
        }
        val nowEpoch = running.runningSinceEpochMs ?: System.currentTimeMillis()
        val nowElapsed = running.runningSinceElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.SESSION_START,
            enabled = uiState.value.preferences.hapticsEnabled
        )
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed, wasRecentlyCancelled = false) }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(running)
            syncStudySessionRecord(running, "RUNNING", nowEpoch, nowElapsed)
            FocusTimerService.syncWithSnapshot(appContext, running)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun selectLinkedTask(taskId: Long?) {
        val task = uiState.value.tasks.firstOrNull { it.id == taskId }
        val updated = timerController.applyTransition { snap, _, _ ->
            snap.copy(
                linkedTaskId = task?.id,
                selectedSubjectId = task?.subjectId ?: snap.selectedSubjectId,
                sessionGoal = if (task != null && snap.sessionGoal.isBlank()) task.title.take(80) else snap.sessionGoal
            )
        }
        persistTimerSnapshot(updated)
    }

    fun markLinkedTaskCompleted(taskId: Long) {
        val task = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (!task.isCompleted) {
            toggleTaskCompletion(task.id)
        }
    }

    fun pauseTimer() {
        val paused = timerController.pause()
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.PAUSE_OR_RESUME,
            enabled = uiState.value.preferences.hapticsEnabled
        )
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(paused)
            syncStudySessionRecord(paused, "PAUSED", nowEpoch, nowElapsed)
            FocusTimerService.syncWithSnapshot(appContext, paused)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun resetTimer() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val prevSnap = timerController.snapshot
        val prevSessionId = prevSnap.sessionId
        val reset = timerController.reset()
        BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
        com.example.system.DistractionAlertCoordinator.clearSession(prevSessionId)
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                wasRecentlyCancelled = true,
                unapprovedAppWarning = null
            )
        }
        viewModelScope.launch {
            if (prevSnap.hasStarted) {
                syncStudySessionRecord(prevSnap, "CANCELLED", nowEpoch, nowElapsed, endTimeMs = nowEpoch)
                repository.savePurposeSession(
                    PurposeSessionRecord(
                        sessionKey = prevSessionId.ifBlank { FocusTimerEngine.generateSessionId() },
                        purposeCategory = prevSnap.purposeCategory,
                        sessionName = prevSnap.sessionGoal.ifBlank { prevSnap.purposeCategory.label },
                        allowedPackages = prevSnap.allowedPackages.toList(),
                        allowedAppLabels = prevSnap.allowedAppLabels,
                        plannedSeconds = prevSnap.totalSeconds,
                        actualFocusedSeconds = 0,
                        breakSeconds = prevSnap.accumulatedBreakSeconds,
                        temporaryAccessSeconds = prevSnap.accumulatedTempAccessSeconds,
                        distractionSeconds = prevSnap.accumulatedDistractionSeconds,
                        interventionCount = prevSnap.interventionCount,
                        status = PurposeSessionRecord.STATUS_CANCELLED,
                        breakPlanMode = prevSnap.breakPlanMode,
                        linkedTaskId = prevSnap.linkedTaskId,
                        startedAtMs = prevSnap.sessionStartedAtEpochMs ?: nowEpoch,
                        endedAtMs = nowEpoch,
                        dateEpochDay = LocalDate.now().toEpochDay()
                    )
                )
            }
            repository.saveActiveTempAccess(null)
            persistTimerSnapshotSuspend(reset)
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    // region Purpose-Aware Focus Session Lifecycle

    fun preselectPurposeForSetup(category: SessionPurposeCategory?) {
        ephemeral.update { it.copy(preselectedPurpose = category) }
    }

    fun clearTempAccessNotice() {
        ephemeral.update { it.copy(tempAccessNotice = null) }
    }

    fun startPurposeFocusSession(
        purposeCategory: SessionPurposeCategory,
        sessionName: String,
        durationMinutes: Int,
        selectedApps: List<SelectedSessionApp>,
        breakPlan: BreakPlanConfig = BreakPlanConfig(),
        linkedTaskId: Long? = null,
        selectedSubjectId: Long? = null
    ): Boolean {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val started = FocusTimerEngine.startPurposeSession(
            purposeCategory = purposeCategory,
            sessionName = sessionName,
            durationMinutes = durationMinutes,
            selectedApps = selectedApps,
            breakPlan = breakPlan,
            linkedTaskId = linkedTaskId,
            selectedSubjectId = selectedSubjectId,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed
        ) ?: return false

        timerController.applyTransition { _, _, _ -> started }

        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.SESSION_START,
            enabled = uiState.value.preferences.hapticsEnabled
        )
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                wasRecentlyCancelled = false,
                pendingCelebration = null,
                unapprovedAppWarning = null,
                expiredTempAccessPrompt = null,
                tempAccessNotice = null,
                preselectedPurpose = null
            )
        }
        viewModelScope.launch {
            repository.saveActiveTempAccess(null)
            persistTimerSnapshotSuspend(started)
            syncStudySessionRecord(started, "RUNNING", nowEpoch, nowElapsed)
            FocusTimerService.syncWithSnapshot(appContext, started)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
        return true
    }

    /**
     * App Switch: pauses the parent focus session and grants a single timed temporary allowance
     * for ONE chosen installed app, tracking temporary access strictly separate from focus time.
     */
    fun startTemporaryAppSwitch(
        packageName: String,
        appLabel: String,
        durationMinutes: Int,
        launchAfterGrant: Boolean = true
    ): Boolean {
        val nowEpoch = System.currentTimeMillis()
        val state = uiState.value
        val snap = timerController.snapshot
        if (!snap.hasStarted) return false

        val result = TemporaryAccessPolicy.startTemporaryAccess(
            parentSessionId = snap.sessionId,
            packageName = packageName,
            appLabel = appLabel,
            purposeLabel = snap.sessionGoal.ifBlank { snap.purposeCategory.label },
            durationMinutes = durationMinutes,
            nowEpochMs = nowEpoch,
            existingActive = state.activeTempAccess,
            ownPackageName = appContext.packageName
        )
        return when (result) {
            is TemporaryAccessPolicy.StartResult.Rejected -> {
                ephemeral.update { it.copy(tempAccessNotice = result.reason) }
                false
            }
            is TemporaryAccessPolicy.StartResult.Started -> {
                val replacedSeconds = result.replacedPrevious?.actualDurationSeconds(nowEpoch) ?: 0
                val updatedParent = timerController.applyTransition { current, epoch, elapsed ->
                    val pausedParent = FocusTimerEngine.pauseForTemporaryAccess(current, epoch, elapsed)
                    if (replacedSeconds > 0) {
                        pausedParent.copy(
                            accumulatedTempAccessSeconds = pausedParent.accumulatedTempAccessSeconds + replacedSeconds
                        )
                    } else {
                        pausedParent
                    }
                }
                val nowElapsed = SystemClock.elapsedRealtime()
                BrainBufferHaptics.perform(
                    context = appContext,
                    event = HapticEvent.PAUSE_OR_RESUME,
                    enabled = state.preferences.hapticsEnabled
                )
                ephemeral.update {
                    it.copy(
                        nowEpochMs = nowEpoch,
                        nowElapsedMs = nowElapsed,
                        unapprovedAppWarning = null,
                        expiredTempAccessPrompt = null,
                        tempAccessNotice = null
                    )
                }
                viewModelScope.launch {
                    persistTimerSnapshotSuspend(updatedParent)
                    repository.saveActiveTempAccess(result.session)
                    repository.logInterventionEvent(
                        InterventionEventRecord(
                            sessionKey = snap.sessionId,
                            eventType = InterventionEventType.APP_SWITCH,
                            packageName = result.session.packageName,
                            appLabel = result.session.appLabel,
                            declaredPurpose = snap.purposeCategory.label,
                            actionTaken = "TEMP_ALLOW",
                            plannedSeconds = durationMinutes * 60,
                            timestampMs = nowEpoch,
                            dateEpochDay = LocalDate.now().toEpochDay()
                        )
                    )
                    FocusTimerService.syncWithSnapshot(appContext, updatedParent)
                    if (launchAfterGrant) {
                        com.example.system.AppLauncherHelper.launchApp(appContext, result.session.packageName)
                    }
                }
                true
            }
        }
    }

    /**
     * Requests a temporary access extension. Strictly enforces the 5-minute maximum AND single-use
     * limit inside [TemporaryAccessPolicy.requestExtension].
     */
    fun requestTemporaryAccessExtension(requestedMinutes: Int = TemporaryAccessPolicy.MAX_EXTENSION_MINUTES): Boolean {
        val nowEpoch = System.currentTimeMillis()
        val currentTemp = uiState.value.activeTempAccess ?: uiState.value.expiredTempAccessPrompt ?: return false
        return when (val result = TemporaryAccessPolicy.requestExtension(currentTemp, requestedMinutes, nowEpoch)) {
            is TemporaryAccessPolicy.ExtensionResult.Granted -> {
                ephemeral.update {
                    it.copy(
                        nowEpochMs = nowEpoch,
                        expiredTempAccessPrompt = null,
                        tempAccessNotice = "Extended ${result.updated.appLabel} by ${result.grantedMinutes} min (final extension)."
                    )
                }
                viewModelScope.launch {
                    repository.saveActiveTempAccess(result.updated)
                    repository.logInterventionEvent(
                        InterventionEventRecord(
                            sessionKey = result.updated.parentSessionId,
                            eventType = InterventionEventType.TEMP_ACCESS_EXTENDED,
                            packageName = result.updated.packageName,
                            appLabel = result.updated.appLabel,
                            declaredPurpose = result.updated.purposeLabel,
                            actionTaken = "EXTEND_5M",
                            plannedSeconds = result.updated.originalAllowanceMinutes * 60,
                            extensionSeconds = result.grantedMinutes * 60,
                            timestampMs = nowEpoch,
                            dateEpochDay = LocalDate.now().toEpochDay()
                        )
                    )
                }
                true
            }
            is TemporaryAccessPolicy.ExtensionResult.RejectedAlreadyExtended -> {
                ephemeral.update {
                    it.copy(tempAccessNotice = "The 5-minute extension has already been used. Return to your focus session.")
                }
                false
            }
            is TemporaryAccessPolicy.ExtensionResult.RejectedExceedsMaxLimit -> {
                ephemeral.update {
                    it.copy(tempAccessNotice = "Extensions are strictly limited to a maximum of ${result.maxAllowedMinutes} minutes.")
                }
                false
            }
            is TemporaryAccessPolicy.ExtensionResult.RejectedSessionClosed -> false
        }
    }

    /**
     * Closes the active temporary access, folds actual temporary access seconds separately (never into
     * focus time), and resumes the parent focus session with its exact remaining time.
     */
    fun returnToFocusFromTemporaryAccess() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val state = uiState.value
        val temp = state.activeTempAccess ?: state.expiredTempAccessPrompt
        val closed = temp?.let { TemporaryAccessPolicy.closeTemporaryAccess(it, nowEpoch) }
        val actualTempSec = closed?.actualDurationSeconds(nowEpoch) ?: 0
        val resumed = timerController.applyTransition { current, epoch, elapsed ->
            if (current.hasStarted) {
                FocusTimerEngine.resumeFromTemporaryAccess(current, actualTempSec, epoch, elapsed)
            } else {
                current
            }
        }
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.PAUSE_OR_RESUME,
            enabled = state.preferences.hapticsEnabled
        )
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                expiredTempAccessPrompt = null,
                unapprovedAppWarning = null,
                tempAccessNotice = null
            )
        }
        viewModelScope.launch {
            repository.saveActiveTempAccess(null)
            if (closed != null) {
                repository.logInterventionEvent(
                    InterventionEventRecord(
                        sessionKey = closed.parentSessionId,
                        eventType = InterventionEventType.TEMP_ACCESS_EXPIRED,
                        packageName = closed.packageName,
                        appLabel = closed.appLabel,
                        declaredPurpose = closed.purposeLabel,
                        actionTaken = "RETURN_TO_FOCUS",
                        plannedSeconds = closed.originalAllowanceMinutes * 60,
                        extensionSeconds = closed.extensionMinutesUsed * 60,
                        actualSeconds = actualTempSec,
                        timestampMs = nowEpoch,
                        dateEpochDay = LocalDate.now().toEpochDay()
                    )
                )
            }
            if (resumed.hasStarted) {
                persistTimerSnapshotSuspend(resumed)
                FocusTimerService.syncWithSnapshot(appContext, resumed)
            }
        }
    }

    /**
     * Switches declared purpose mid-session when the user's real intention changes.
     */
    fun switchActiveSessionPurpose(
        newPurpose: SessionPurposeCategory,
        newSessionName: String,
        newAllowedApps: List<SelectedSessionApp>
    ) {
        val nowEpoch = System.currentTimeMillis()
        val prevSnap = timerController.snapshot
        if (!prevSnap.hasStarted) return
        val updated = timerController.applyTransition { current, _, _ ->
            PurposeMatchingEngine.switchPurpose(
                current = current,
                newPurpose = newPurpose,
                newSessionName = newSessionName,
                newAllowedPackages = newAllowedApps.map { it.packageName }.toSet(),
                newAllowedLabels = newAllowedApps.map { it.appLabel }
            )
        }
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                unapprovedAppWarning = null,
                tempAccessNotice = "Switched purpose to ${newPurpose.label}."
            )
        }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(updated)
            repository.logInterventionEvent(
                InterventionEventRecord(
                    sessionKey = prevSnap.sessionId,
                    eventType = InterventionEventType.PURPOSE_SWITCHED,
                    declaredPurpose = "${prevSnap.purposeCategory.label} -> ${newPurpose.label}",
                    actionTaken = "CHANGE_PURPOSE",
                    timestampMs = nowEpoch,
                    dateEpochDay = LocalDate.now().toEpochDay()
                )
            )
            FocusTimerService.syncWithSnapshot(appContext, updated)
        }
    }

    fun recordDistractionMismatchAction(
        packageName: String,
        appLabel: String,
        actionTaken: String,
        estimatedDistractionSeconds: Int = 30
    ) {
        val nowEpoch = System.currentTimeMillis()
        val updatedSnap = timerController.applyTransition { current, _, _ ->
            if (current.hasStarted) {
                FocusTimerEngine.recordDistractionIntervention(current, estimatedDistractionSeconds)
            } else {
                current
            }
        }
        ephemeral.update { it.copy(unapprovedAppWarning = null) }
        viewModelScope.launch {
            if (updatedSnap.hasStarted) {
                persistTimerSnapshotSuspend(updatedSnap)
            }
            repository.logInterventionEvent(
                InterventionEventRecord(
                    sessionKey = updatedSnap.sessionId,
                    eventType = InterventionEventType.POTENTIAL_DISTRACTION,
                    packageName = packageName,
                    appLabel = appLabel,
                    declaredPurpose = updatedSnap.purposeCategory.label,
                    actionTaken = actionTaken,
                    actualSeconds = estimatedDistractionSeconds,
                    timestampMs = nowEpoch,
                    dateEpochDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }

    // endregion

    /**
     * Switches to any built-in, controlled-access, or custom activity (PDF, Lecture, Reading,
     * Short Break, Gaming, Social/Entertainment, Other), automatically pausing and preserving
     * the active study session and storing any approved app/PDF context.
     */
    fun switchActivity(
        activityType: StudyActivityType,
        activityLabel: String = activityType.label,
        category: ActivityCategory = activityType.category,
        durationMinutes: Int = activityType.defaultMinutes,
        subjectId: Long? = uiState.value.timer.selectedSubjectId,
        wasPlanned: Boolean = true,
        approvedPackageName: String = "",
        approvedAppLabel: String = "",
        approvedDocumentUri: String = ""
    ) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = uiState.value
        val currentSnap = timerController.snapshot

        val switched = timerController.applyTransition { latestSnap, epoch, elapsed ->
            FocusTimerEngine.switchActivity(
                current = latestSnap,
                activityType = activityType,
                activityLabel = activityLabel,
                category = category,
                durationMinutes = durationMinutes,
                subjectId = subjectId,
                wasPlanned = wasPlanned,
                nowEpochMs = epoch,
                nowElapsedRealtimeMs = elapsed,
                approvedPackageName = approvedPackageName,
                approvedAppLabel = approvedAppLabel,
                approvedDocumentUri = approvedDocumentUri
            )
        }
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                appUsageSuggestion = null,
                unapprovedAppWarning = null,
                isRequestingActivity = false,
                pendingCelebration = null,
                wasRecentlyCancelled = false
            )
        }

        viewModelScope.launch {
            // If we were already on another switched activity with >=30s elapsed, log it before switching again
            if (currentSnap.isSwitchedActivity && currentSnap.hasStarted) {
                val elapsedSec = FocusTimerEngine.actualFocusedSeconds(currentSnap, nowEpoch, nowElapsed)
                if (elapsedSec >= 30) {
                    val subject = currentState.subjects.firstOrNull { it.id == currentSnap.selectedSubjectId }
                    repository.recordActivityLog(
                        ActivityLogRecord(
                            sessionKey = currentSnap.sessionId.ifBlank { FocusTimerEngine.generateSessionId() },
                            activityType = currentSnap.activeActivityType,
                            activityLabel = currentSnap.activeActivityLabel,
                            category = currentSnap.activeActivityCategory,
                            subjectId = subject?.id,
                            subjectName = subject?.name ?: "General Study",
                            subjectColorHex = subject?.colorHex ?: currentSnap.activeActivityType.accentHex,
                            plannedSeconds = currentSnap.totalSeconds,
                            actualSeconds = elapsedSec,
                            startedAtMs = currentSnap.activityStartedAtEpochMs ?: (nowEpoch - elapsedSec * 1000L),
                            endedAtMs = nowEpoch,
                            dateEpochDay = LocalDate.now().toEpochDay(),
                            wasPlanned = currentSnap.activityWasPlanned
                        )
                    )
                }
            }
            persistTimerSnapshotSuspend(switched)
            FocusTimerService.syncWithSnapshot(appContext, switched)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun startBreakFromCurrent(breakMinutes: Int = uiState.value.preferences.focusGuardSettings.defaultBreakMinutes) {
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.PAUSE_OR_RESUME,
            enabled = uiState.value.preferences.hapticsEnabled
        )
        switchActivity(
            activityType = StudyActivityType.SHORT_BREAK,
            activityLabel = "Taking a short break",
            category = ActivityCategory.BREAK,
            durationMinutes = breakMinutes.coerceIn(2, 60),
            subjectId = timerController.snapshot.selectedSubjectId,
            wasPlanned = true
        )
    }

    /**
     * Logs the switched activity (if >=30s elapsed) and seamlessly resumes the paused parent study session.
     */
    fun returnToPausedStudySession() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = uiState.value
        val snap = timerController.snapshot
        if (snap.pausedParentSession == null) return
        BrainBufferHaptics.perform(
            context = appContext,
            event = HapticEvent.PAUSE_OR_RESUME,
            enabled = currentState.preferences.hapticsEnabled
        )

        val resumed = timerController.applyTransition { latestSnap, epoch, elapsed ->
            FocusTimerEngine.returnToPausedStudySession(latestSnap, epoch, elapsed)
                ?: FocusTimerEngine.reset(latestSnap)
        }
        BrainBufferNotificationManager.cancelCompletionNotification(appContext)
        BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                pendingCelebration = null,
                unapprovedAppWarning = null
            )
        }

        viewModelScope.launch {
            val elapsedSec = FocusTimerEngine.actualFocusedSeconds(snap, nowEpoch, nowElapsed)
            if (elapsedSec >= 30) {
                val subject = currentState.subjects.firstOrNull { it.id == snap.selectedSubjectId }
                repository.recordActivityLog(
                    ActivityLogRecord(
                        sessionKey = snap.sessionId.ifBlank { FocusTimerEngine.generateSessionId() },
                        activityType = snap.activeActivityType,
                        activityLabel = snap.activeActivityLabel,
                        category = snap.activeActivityCategory,
                        subjectId = subject?.id,
                        subjectName = subject?.name ?: "General Study",
                        subjectColorHex = subject?.colorHex ?: snap.activeActivityType.accentHex,
                        plannedSeconds = snap.totalSeconds,
                        actualSeconds = elapsedSec,
                        startedAtMs = snap.activityStartedAtEpochMs ?: (nowEpoch - elapsedSec * 1000L),
                        endedAtMs = nowEpoch,
                        dateEpochDay = LocalDate.now().toEpochDay(),
                        wasPlanned = snap.activityWasPlanned
                    )
                )
            }
            persistTimerSnapshotSuspend(resumed)
            FocusTimerService.syncWithSnapshot(appContext, resumed)
        }
    }

    /**
     * Extends the active study, break, lecture, or gaming timer without guilt.
     */
    fun extendActiveTimer(extraMinutes: Int) {
        val extended = timerController.applyTransition { current, nowEpoch, nowElapsed ->
            FocusTimerEngine.extendActiveTimer(current, extraMinutes, nowEpoch, nowElapsed)
        }
        val nowEpoch = extended.runningSinceEpochMs ?: System.currentTimeMillis()
        val nowElapsed = extended.runningSinceElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        BrainBufferNotificationManager.cancelCompletionNotification(appContext)
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                pendingCelebration = null
            )
        }
        viewModelScope.launch {
            persistTimerSnapshotSuspend(extended)
            FocusTimerService.syncWithSnapshot(appContext, extended)
        }
    }

    /**
     * Starts a voluntary Focus Guard session (Normal Focus, High Focus, or Custom Focus)
     * with optional user-granted DND & AOD-inspired pure AMOLED display.
     */
    fun startStrictFocus(
        subjectId: Long?,
        sessionGoal: String,
        activityType: StudyActivityType,
        durationMinutes: Int,
        strictness: StrictnessLevel,
        enableDnd: Boolean,
        enableAodStyle: Boolean,
        guardLevel: com.example.model.FocusGuardLevel = com.example.model.FocusGuardLevel.HIGH
    ) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            var dndActivated = false
            if (enableDnd) {
                val prefs = repository.getPreferencesOnce()
                dndActivated = DndFocusController.enableFocusDndIfGranted(
                    backend = AndroidDndBackend(appContext),
                    alreadySavedFilter = prefs.savedDndInterruptionFilter,
                    allowImportantCalls = prefs.notificationPrefs.allowImportantCallsDuringFocus,
                    allowAlarms = prefs.notificationPrefs.allowAlarmsDuringFocus,
                    savePreviousFilter = { prior ->
                        repository.updateSavedDndInterruptionFilter(prior)
                    }
                )
            }
            val strictSnap = timerController.applyTransition { _, epoch, elapsed ->
                FocusTimerEngine.startStrictFocus(
                    subjectId = subjectId,
                    sessionGoal = sessionGoal,
                    activityType = activityType,
                    durationMinutes = durationMinutes,
                    strictness = strictness,
                    enableAodStyle = enableAodStyle,
                    dndActive = dndActivated,
                    nowEpochMs = epoch,
                    nowElapsedRealtimeMs = elapsed,
                    guardLevel = guardLevel
                )
            }
            ephemeral.update {
                it.copy(
                    nowEpochMs = strictSnap.runningSinceEpochMs ?: nowEpoch,
                    nowElapsedMs = strictSnap.runningSinceElapsedRealtimeMs ?: nowElapsed,
                    wasRecentlyCancelled = false,
                    unapprovedAppWarning = null
                )
            }
            persistTimerSnapshotSuspend(strictSnap)
            FocusTimerService.syncWithSnapshot(appContext, strictSnap)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun toggleStrictFocusAod() {
        val updated = timerController.applyTransition { snap, _, _ ->
            snap.copy(strictFocusAodStyle = !snap.strictFocusAodStyle)
        }
        persistTimerSnapshot(updated)
    }

    /**
     * Voluntary, reversible exit from Strict Focus Mode — always restores DND if active.
     */
    fun exitStrictFocus(keepTimerRunning: Boolean = false) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val updated = timerController.applyTransition { snap, _, _ ->
            if (keepTimerRunning) {
                snap.copy(
                    isStrictFocus = false,
                    strictFocusAodStyle = false,
                    strictFocusDndActive = false
                )
            } else {
                FocusTimerEngine.reset(snap)
            }
        }
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
        viewModelScope.launch {
            restoreDndIfNeeded()
            persistTimerSnapshotSuspend(updated)
            if (updated.hasStarted) {
                FocusTimerService.syncWithSnapshot(appContext, updated)
            } else {
                FocusTimerService.stopService(appContext)
            }
        }
    }

    fun saveStrictFocusPreset(preset: StrictFocusPreset) {
        if (preset.name.isBlank()) return
        viewModelScope.launch {
            repository.saveStrictFocusPreset(preset.copy(name = preset.name.trim()))
        }
    }

    fun deleteStrictFocusPreset(presetId: Long) {
        viewModelScope.launch { repository.deleteStrictFocusPreset(presetId) }
    }

    fun addCustomActivity(label: String, category: ActivityCategory, defaultMinutes: Int) {
        val clean = label.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.addCustomActivity(
                CustomActivityOption(
                    label = clean.take(32),
                    category = category,
                    defaultDurationMinutes = defaultMinutes.coerceIn(5, 180)
                )
            )
        }
    }

    fun deleteCustomActivity(id: Long) {
        viewModelScope.launch { repository.deleteCustomActivity(id) }
    }

    private suspend fun restoreDndIfNeeded() {
        val prefs = repository.getPreferencesOnce()
        if (prefs.savedDndInterruptionFilter != DndFocusController.NO_SAVED_FILTER) {
            DndFocusController.restorePreviousDnd(
                backend = AndroidDndBackend(appContext),
                savedFilter = prefs.savedDndInterruptionFilter,
                clearSavedFilter = {
                    repository.updateSavedDndInterruptionFilter(DndFocusController.NO_SAVED_FILTER)
                }
            )
        }
    }

    /**
     * Strictly completes a session ONLY when the timer has genuinely expired (remainingSeconds <= 0)
     * and has never been completed before.
     *
     * Business-logic enforcement (CRITICAL):
     * - Rejects calls when session has not started.
     * - Rejects calls when remainingSeconds > 0 (eliminates instant-completion bypass bug).
     * - Rejects duplicate calls for the same sessionId.
     * - Persists exact completed duration (actual == planned), updates stats, and clears timer cleanly.
     */
    fun completeCurrentSession(
        autoCompleted: Boolean = true,
        overrideSnapshot: PersistedTimerSnapshot? = null
    ) {
        if (isCompletingSession) return
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = uiState.value
        val snap = overrideSnapshot ?: timerController.snapshot

        val decision = FocusTimerEngine.evaluateNaturalCompletion(
            snapshot = snap,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed,
            completedSessionIds = completedSessionIdsInMemory
        )
        if (decision !is FocusTimerEngine.SessionCompletionDecision.NaturalCompletion) {
            return
        }

        isCompletingSession = true
        completedSessionIdsInMemory.add(decision.sessionId)
        timerController.markSessionCompleted(decision.sessionId)

        viewModelScope.launch {
            try {
                val actualSeconds = decision.actualSeconds
                val selectedSubject = currentState.subjects.firstOrNull { it.id == snap.selectedSubjectId }
                val subjectName = selectedSubject?.name ?: "General Focus"
                val sessionKey = decision.sessionId
                val todayEpoch = LocalDate.now().toEpochDay()
                val linkedTask = decision.linkedTaskId?.let { id ->
                    currentState.tasks.firstOrNull { it.id == id }
                }

                // Deduplicate against concurrent FocusTimerService completion
                val isPurposeFocusSession = snap.mode == FocusMode.FOCUS && !snap.isOnBreak
                val latestPersistedSnap = repository.getTimerSnapshotOnce()
                if (latestPersistedSnap.lastCompletedSessionId == sessionKey ||
                    (isPurposeFocusSession && repository.hasPurposeSession(sessionKey))
                ) {
                    return@launch
                }

                val openTempSec = currentState.activeTempAccess
                    ?.takeIf { it.endedAtEpochMs == null }
                    ?.actualDurationSeconds(nowEpoch)
                    ?.coerceAtLeast(0)
                    ?: 0

                // Record durable study session lifecycle completion & activity log (deduplicated by sessionKey)
                repository.upsertStudySession(
                    sessionKey = sessionKey,
                    subjectId = selectedSubject?.id,
                    subjectName = subjectName,
                    startTimeMs = snap.activityStartedAtEpochMs ?: (nowEpoch - actualSeconds * 1000L),
                    endTimeMs = nowEpoch,
                    totalDurationSeconds = actualSeconds,
                    plannedDurationSeconds = decision.plannedSeconds,
                    sessionStatus = "COMPLETED",
                    focusGuardLevel = snap.focusGuardLevel.name,
                    activityType = snap.activeActivityType.name
                )
                repository.recordActivityLog(
                    ActivityLogRecord(
                        sessionKey = sessionKey,
                        activityType = snap.activeActivityType,
                        activityLabel = snap.activeActivityLabel,
                        category = snap.activeActivityCategory,
                        subjectId = selectedSubject?.id,
                        subjectName = subjectName,
                        subjectColorHex = selectedSubject?.colorHex ?: snap.activeActivityType.accentHex,
                        plannedSeconds = decision.plannedSeconds,
                        actualSeconds = actualSeconds,
                        startedAtMs = snap.activityStartedAtEpochMs ?: (nowEpoch - actualSeconds * 1000L),
                        endedAtMs = nowEpoch,
                        dateEpochDay = todayEpoch,
                        wasPlanned = snap.activityWasPlanned,
                        notes = snap.sessionGoal.trim()
                    )
                )

                val isFocusedStudy = snap.activeActivityCategory.countsAsFocusedStudy && snap.mode == FocusMode.FOCUS
                if (isPurposeFocusSession) {
                    repository.savePurposeSession(
                        PurposeSessionRecord(
                            sessionKey = sessionKey,
                            purposeCategory = snap.purposeCategory,
                            sessionName = snap.sessionGoal.ifBlank { subjectName },
                            allowedPackages = snap.allowedPackages.toList(),
                            allowedAppLabels = snap.allowedAppLabels,
                            plannedSeconds = decision.plannedSeconds,
                            actualFocusedSeconds = actualSeconds,
                            breakSeconds = snap.accumulatedBreakSeconds,
                            temporaryAccessSeconds = snap.accumulatedTempAccessSeconds + openTempSec,
                            distractionSeconds = snap.accumulatedDistractionSeconds,
                            interventionCount = snap.interventionCount,
                            status = PurposeSessionRecord.STATUS_COMPLETED,
                            breakPlanMode = snap.breakPlanMode,
                            linkedTaskId = decision.linkedTaskId,
                            startedAtMs = snap.sessionStartedAtEpochMs ?: (nowEpoch - actualSeconds * 1000L),
                            endedAtMs = nowEpoch,
                            dateEpochDay = todayEpoch
                        )
                    )
                    repository.saveActiveTempAccess(null)
                }
                if (isFocusedStudy || isPurposeFocusSession) {
                    val record = FocusSessionRecord(
                        subjectId = selectedSubject?.id,
                        subjectName = snap.sessionGoal.ifBlank { subjectName },
                        subjectColorHex = selectedSubject?.colorHex ?: snap.purposeCategory.accentHex,
                        durationSeconds = actualSeconds,
                        plannedSeconds = decision.plannedSeconds,
                        sessionGoal = snap.sessionGoal.trim(),
                        mode = FocusMode.FOCUS,
                        completedAtMs = nowEpoch,
                        dateEpochDay = todayEpoch
                    )

                    repository.recordFocusSessionAndGrowGarden(
                        session = record,
                        dailyTargetMinutes = currentState.preferences.dailyTargetMinutes,
                        allExistingSessions = currentState.sessions,
                        existingDecorations = currentState.preferences.unlockedDecorations,
                        sessionKey = sessionKey
                    )

                    val resetSnap = FocusTimerEngine.markCompletedAndReset(snap, sessionKey)
                    timerController.applyTransition { _, _, _ -> resetSnap }
                    persistTimerSnapshotSuspend(resetSnap)
                    restoreDndIfNeeded()
                    if (!currentState.manualBlockingSession.isEnforcing(nowEpoch)) {
                        FocusTimerService.stopService(appContext)
                    }

                    BrainBufferHaptics.perform(
                        context = appContext,
                        event = HapticEvent.SESSION_COMPLETE,
                        enabled = currentState.preferences.hapticsEnabled
                    )
                    com.example.audio.FocusCompletionSoundPlayer.playCompletionChime(
                        context = appContext,
                        sessionId = sessionKey,
                        enabled = currentState.preferences.soundEnabled
                    )
                    if (autoCompleted) {
                        BrainBufferNotificationManager.postCompletionNotification(
                            context = appContext,
                            snapshot = snap,
                            subjectName = subjectName,
                            prefs = currentState.preferences.notificationPrefs,
                            masterNotificationsEnabled = currentState.preferences.notificationsEnabled,
                            studentName = currentState.preferences.studentName,
                            soundEnabled = currentState.preferences.soundEnabled &&
                                currentState.preferences.focusGuardSettings.notificationSoundEnabled,
                            vibrationEnabled = currentState.preferences.hapticsEnabled &&
                                currentState.preferences.focusGuardSettings.vibrationEnabled,
                            extensionMinutes = currentState.preferences.focusGuardSettings.customExtensionMinutes
                        )
                    }
                    com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)

                    ephemeral.update {
                        it.copy(
                            nowEpochMs = nowEpoch,
                            nowElapsedMs = nowElapsed,
                            pendingCelebration = SessionCelebration(
                                durationMinutes = (actualSeconds / 60).coerceAtLeast(1),
                                subjectName = record.subjectName,
                                activityLabel = snap.activeActivityLabel,
                                activityCategory = snap.activeActivityCategory,
                                canReturnToStudy = false,
                                actualSeconds = actualSeconds,
                                plannedSeconds = decision.plannedSeconds,
                                endedEarly = false,
                                linkedTaskId = linkedTask?.takeIf { t -> !t.isCompleted }?.id,
                                linkedTaskTitle = linkedTask?.takeIf { t -> !t.isCompleted }?.title.orEmpty()
                            )
                        )
                    }
                } else {
                    val hasParent = snap.pausedParentSession != null
                    if (hasParent) {
                        val frozenSnap = snap.copy(
                            accumulatedFocusedMs = snap.totalSeconds * 1000L,
                            runningSinceEpochMs = null,
                            runningSinceElapsedRealtimeMs = null,
                            hasStarted = false,
                            lastCompletedSessionId = sessionKey
                        )
                        timerController.applyTransition { _, _, _ -> frozenSnap }
                        persistTimerSnapshotSuspend(frozenSnap)
                    } else {
                        val focusReady = FocusTimerEngine.configureMode(
                            snap,
                            FocusMode.FOCUS,
                            currentState.preferences.preferredFocusMinutes
                        ).copy(lastCompletedSessionId = sessionKey)
                        timerController.applyTransition { _, _, _ -> focusReady }
                        persistTimerSnapshotSuspend(focusReady)
                        restoreDndIfNeeded()
                    }
                    if (!currentState.manualBlockingSession.isEnforcing(nowEpoch)) {
                        FocusTimerService.stopService(appContext)
                    }
                    com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)

                    BrainBufferHaptics.perform(
                        context = appContext,
                        event = HapticEvent.SESSION_COMPLETE,
                        enabled = currentState.preferences.hapticsEnabled
                    )
                    com.example.audio.FocusCompletionSoundPlayer.playCompletionChime(
                        context = appContext,
                        sessionId = sessionKey,
                        enabled = currentState.preferences.soundEnabled
                    )
                    if (autoCompleted) {
                        BrainBufferNotificationManager.postCompletionNotification(
                            context = appContext,
                            snapshot = snap,
                            subjectName = subjectName,
                            prefs = currentState.preferences.notificationPrefs,
                            masterNotificationsEnabled = currentState.preferences.notificationsEnabled,
                            studentName = currentState.preferences.studentName,
                            soundEnabled = currentState.preferences.soundEnabled &&
                                currentState.preferences.focusGuardSettings.notificationSoundEnabled,
                            vibrationEnabled = currentState.preferences.hapticsEnabled &&
                                currentState.preferences.focusGuardSettings.vibrationEnabled,
                            extensionMinutes = currentState.preferences.focusGuardSettings.customExtensionMinutes
                        )
                    }

                    ephemeral.update {
                        it.copy(
                            nowEpochMs = nowEpoch,
                            nowElapsedMs = nowElapsed,
                            pendingCelebration = SessionCelebration(
                                durationMinutes = (actualSeconds / 60).coerceAtLeast(1),
                                subjectName = subjectName,
                                activityLabel = snap.activeActivityLabel,
                                activityCategory = snap.activeActivityCategory,
                                canReturnToStudy = hasParent,
                                actualSeconds = actualSeconds,
                                plannedSeconds = decision.plannedSeconds,
                                endedEarly = false
                            )
                        )
                    }
                }
            } finally {
                isCompletingSession = false
            }
        }
    }

    /**
     * Explicit, honest Early Termination flow:
     * - Records ONLY actual elapsed focused seconds (never full planned duration, never paused time).
     * - Marks study_sessions status as "ENDED_EARLY" (never "COMPLETED").
     * - Never falsely awards full planned duration or auto-completes linked tasks.
     */
    fun endSessionEarly() {
        if (isCompletingSession) return
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = uiState.value
        val rawSnap = timerController.snapshot
        // Unwind any active Break to the parent Focus session and fold any open Temporary Access time
        val effectiveSnap = FocusTimerEngine.resolveParentForEarlyExit(
            snapshot = rawSnap,
            activeTempAccess = currentState.activeTempAccess,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed
        )

        val decision = FocusTimerEngine.evaluateEarlyTermination(
            snapshot = effectiveSnap,
            nowEpochMs = nowEpoch,
            nowElapsedRealtimeMs = nowElapsed,
            completedSessionIds = completedSessionIdsInMemory + timerController.snapshotCompletedSessionIds()
        )
        isCompletingSession = true
        if (decision.shouldPersist) {
            completedSessionIdsInMemory.add(decision.sessionId)
            timerController.markSessionCompleted(decision.sessionId)
        }

        viewModelScope.launch {
            try {
                val selectedSubject = currentState.subjects.firstOrNull { it.id == effectiveSnap.selectedSubjectId }
                val subjectName = selectedSubject?.name ?: "General Focus"
                val todayEpoch = LocalDate.now().toEpochDay()
                val alreadySaved = decision.shouldPersist && repository.hasPurposeSession(decision.sessionId)

                if (decision.shouldPersist && !alreadySaved) {
                    repository.upsertStudySession(
                        sessionKey = decision.sessionId,
                        subjectId = selectedSubject?.id,
                        subjectName = subjectName,
                        startTimeMs = effectiveSnap.activityStartedAtEpochMs ?: (nowEpoch - decision.actualSeconds * 1000L),
                        endTimeMs = nowEpoch,
                        totalDurationSeconds = decision.actualSeconds,
                        plannedDurationSeconds = decision.plannedSeconds,
                        sessionStatus = decision.status, // "ENDED_EARLY"
                        focusGuardLevel = effectiveSnap.focusGuardLevel.name,
                        activityType = effectiveSnap.activeActivityType.name
                    )
                    repository.recordActivityLog(
                        ActivityLogRecord(
                            sessionKey = decision.sessionId,
                            activityType = effectiveSnap.activeActivityType,
                            activityLabel = "${effectiveSnap.activeActivityLabel} (Ended early)",
                            category = effectiveSnap.activeActivityCategory,
                            subjectId = selectedSubject?.id,
                            subjectName = subjectName,
                            subjectColorHex = selectedSubject?.colorHex ?: effectiveSnap.activeActivityType.accentHex,
                            plannedSeconds = decision.plannedSeconds,
                            actualSeconds = decision.actualSeconds,
                            startedAtMs = effectiveSnap.activityStartedAtEpochMs ?: (nowEpoch - decision.actualSeconds * 1000L),
                            endedAtMs = nowEpoch,
                            dateEpochDay = todayEpoch,
                            wasPlanned = false,
                            notes = effectiveSnap.sessionGoal.trim()
                        )
                    )
                    // Save honest PurposeSessionRecord with actual focused seconds only (ENDED_EARLY)
                    if (effectiveSnap.mode == FocusMode.FOCUS && !effectiveSnap.isOnBreak) {
                        repository.savePurposeSession(
                            PurposeSessionRecord(
                                sessionKey = decision.sessionId,
                                purposeCategory = effectiveSnap.purposeCategory,
                                sessionName = effectiveSnap.sessionGoal.ifBlank { subjectName },
                                allowedPackages = effectiveSnap.allowedPackages.toList(),
                                allowedAppLabels = effectiveSnap.allowedAppLabels,
                                plannedSeconds = decision.plannedSeconds,
                                actualFocusedSeconds = decision.actualSeconds,
                                breakSeconds = effectiveSnap.accumulatedBreakSeconds,
                                temporaryAccessSeconds = effectiveSnap.accumulatedTempAccessSeconds,
                                distractionSeconds = effectiveSnap.accumulatedDistractionSeconds,
                                interventionCount = effectiveSnap.interventionCount,
                                status = PurposeSessionRecord.STATUS_ENDED_EARLY,
                                breakPlanMode = effectiveSnap.breakPlanMode,
                                linkedTaskId = effectiveSnap.linkedTaskId,
                                startedAtMs = effectiveSnap.sessionStartedAtEpochMs ?: (nowEpoch - decision.actualSeconds * 1000L),
                                endedAtMs = nowEpoch,
                                dateEpochDay = todayEpoch
                            )
                        )
                    }
                    // Log actual focused minutes (>= 60s) honestly without crediting planned duration
                    if (effectiveSnap.mode == FocusMode.FOCUS && !effectiveSnap.isOnBreak && decision.actualSeconds >= 60) {
                        repository.recordFocusSessionAndGrowGarden(
                            session = FocusSessionRecord(
                                subjectId = selectedSubject?.id,
                                subjectName = effectiveSnap.sessionGoal.ifBlank { subjectName },
                                subjectColorHex = selectedSubject?.colorHex ?: effectiveSnap.purposeCategory.accentHex,
                                durationSeconds = decision.actualSeconds,
                                plannedSeconds = decision.plannedSeconds,
                                sessionGoal = effectiveSnap.sessionGoal.trim(),
                                mode = FocusMode.FOCUS,
                                completedAtMs = nowEpoch,
                                dateEpochDay = todayEpoch
                            ),
                            dailyTargetMinutes = currentState.preferences.dailyTargetMinutes,
                            allExistingSessions = currentState.sessions,
                            existingDecorations = currentState.preferences.unlockedDecorations,
                            sessionKey = decision.sessionId
                        )
                    }
                }

                BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
                com.example.system.DistractionAlertCoordinator.clearSession(rawSnap.sessionId)
                com.example.system.DistractionAlertCoordinator.clearSession(effectiveSnap.sessionId)
                repository.saveActiveTempAccess(null)
                val cleaned = FocusTimerEngine.markCompletedAndReset(effectiveSnap, decision.sessionId)
                timerController.applyTransition { _, _, _ -> cleaned }
                persistTimerSnapshotSuspend(cleaned)
                restoreDndIfNeeded()
                if (!currentState.manualBlockingSession.isEnforcing(nowEpoch)) {
                    FocusTimerService.stopService(appContext)
                }
                com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)

                ephemeral.update {
                    it.copy(
                        nowEpochMs = nowEpoch,
                        nowElapsedMs = nowElapsed,
                        wasRecentlyCancelled = true,
                        unapprovedAppWarning = null,
                        expiredTempAccessPrompt = null,
                        pendingCelebration = if (decision.shouldPersist && !alreadySaved) {
                            SessionCelebration(
                                durationMinutes = decision.actualSeconds / 60,
                                subjectName = effectiveSnap.sessionGoal.ifBlank { subjectName },
                                activityLabel = effectiveSnap.activeActivityLabel,
                                activityCategory = effectiveSnap.activeActivityCategory,
                                canReturnToStudy = false,
                                actualSeconds = decision.actualSeconds,
                                plannedSeconds = decision.plannedSeconds,
                                endedEarly = true
                            )
                        } else null
                    )
                }
            } finally {
                isCompletingSession = false
            }
        }
    }

    fun dismissCelebration() {
        ephemeral.update { it.copy(pendingCelebration = null) }
    }

    // endregion

    // region My Study Garden

    fun setActivePlant(plantId: Long) {
        viewModelScope.launch { repository.setActivePlant(plantId) }
    }

    fun nurtureActivePlantWithSeed(): Boolean {
        if (uiState.value.preferences.seedsOwned <= 0 || uiState.value.activePlant == null) return false
        viewModelScope.launch {
            repository.nurtureActivePlantWithSeed()
            ephemeral.update { it.copy(gardenSparkleTrigger = it.gardenSparkleTrigger + 1) }
        }
        return true
    }

    fun plantNewSeed(species: PlantSpecies, nickname: String): Boolean {
        val hasSeeds = uiState.value.preferences.seedsOwned > 0
        if (!hasSeeds && uiState.value.plants.isNotEmpty()) return false
        viewModelScope.launch {
            repository.plantNewSeed(
                species = species,
                nickname = nickname,
                spendSeed = uiState.value.plants.isNotEmpty()
            )
            ephemeral.update { it.copy(gardenSparkleTrigger = it.gardenSparkleTrigger + 1) }
        }
        return true
    }

    // endregion

    // region Mind Reset & Data Management

    fun recordMindResetBreathCycle() {
        viewModelScope.launch { repository.recordMindResetBreathCycle() }
    }

    fun addDistractionNote(text: String) {
        viewModelScope.launch {
            repository.addMindNote(MindNoteType.DISTRACTION_DUMP, text)
        }
    }

    fun addSessionReflection(text: String, moodTag: String) {
        viewModelScope.launch {
            repository.addMindNote(MindNoteType.SESSION_REFLECTION, text, moodTag)
        }
    }

    fun deleteMindNote(id: Long) {
        viewModelScope.launch { repository.deleteMindNote(id) }
    }

    fun loadExplicitDemoData() {
        viewModelScope.launch { repository.loadExplicitDemoPreviewData() }
    }

    fun buildStudyBackupJson(): String {
        val state = uiState.value
        val root = org.json.JSONObject().apply {
            put("app", "Brain Buffer")
            put("exportedAtEpochMs", System.currentTimeMillis())
            put(
                "profile",
                org.json.JSONObject().apply {
                    put("displayName", state.preferences.displayName)
                    put("studyLevel", state.preferences.studyLevel.label)
                    put("courseOrExam", state.preferences.courseOrExamName)
                    put("dailyTargetMinutes", state.preferences.dailyTargetMinutes)
                    put("weeklyStudyDaysTarget", state.preferences.weeklyStudyDaysTarget)
                    put("seedsOwned", state.preferences.seedsOwned)
                }
            )
            put(
                "summary",
                org.json.JSONObject().apply {
                    put("totalFocusMinutesAllTime", state.totalFocusMinutesAllTime)
                    put("weeklyFocusMinutes", state.weeklyFocusMinutes)
                    put("currentStreakDays", state.currentStreakDays)
                    put("completedTasks", state.completedTasksCount)
                    put("totalTasks", state.tasks.size)
                    put("activityLogsCount", state.activityLogs.size)
                }
            )
            put(
                "subjects",
                org.json.JSONArray().apply {
                    state.subjects.forEach { s ->
                        put(
                            org.json.JSONObject().apply {
                                put("name", s.name)
                                put("targetHoursPerWeek", s.targetHoursPerWeek)
                                s.examDateFormatted()?.let { put("examDate", it) }
                            }
                        )
                    }
                }
            )
            put(
                "tasks",
                org.json.JSONArray().apply {
                    state.tasks.forEach { t ->
                        put(
                            org.json.JSONObject().apply {
                                put("title", t.title)
                                put("dueTime", t.dueTimeLabel)
                                put("estimatedMinutes", t.estimatedMinutes)
                                put("priority", t.priority.label)
                                put("completed", t.isCompleted)
                            }
                        )
                    }
                }
            )
            put("focusSessionsCount", state.focusOnlySessions.size)
            put(
                "activityLogs",
                org.json.JSONArray().apply {
                    state.activityLogs.take(100).forEach { log ->
                        put(
                            org.json.JSONObject().apply {
                                put("activity", log.activityLabel)
                                put("category", log.category.label)
                                put("subject", log.subjectName)
                                put("plannedMinutes", log.plannedMinutes)
                                put("actualMinutes", log.actualMinutes)
                                put("wasPlanned", log.wasPlanned)
                            }
                        )
                    }
                }
            )
            put(
                "gardenPlants",
                org.json.JSONArray().apply {
                    state.plants.forEach { p ->
                        put(
                            org.json.JSONObject().apply {
                                put("nickname", p.nickname)
                                put("species", p.species.displayName)
                                put("stage", p.stage.label)
                                put("sessionsLogged", p.sessionsLogged)
                                put("totalMinutes", p.totalMinutes)
                            }
                        )
                    }
                }
            )
        }
        return root.toString(2)
    }

    fun importStudyBackupJson(json: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = repository.importStudyBackupJson(json)
            onResult(result)
        }
    }

    fun clearAllStudyData() {
        viewModelScope.launch {
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
            repository.clearAllStudyData()
        }
    }

    // region Phone Usage Analytics

    fun refreshPhoneUsageAnalytics(
        periodMode: UsagePeriodMode = phoneUsageReportFlow.value.periodMode,
        customStartEpochDay: Long? = phoneUsageReportFlow.value.startEpochDay,
        customEndEpochDay: Long? = phoneUsageReportFlow.value.endEpochDay
    ) {
        viewModelScope.launch {
            phoneUsageReportFlow.update {
                it.copy(
                    isLoading = true,
                    periodMode = periodMode,
                    startEpochDay = customStartEpochDay ?: it.startEpochDay,
                    endEpochDay = customEndEpochDay ?: it.endEpochDay
                )
            }
            val report = PhoneUsageAnalyticsEngine.computeReport(
                context = appContext,
                periodMode = periodMode,
                customStartEpochDay = customStartEpochDay,
                customEndEpochDay = customEndEpochDay
            )
            phoneUsageReportFlow.value = report
        }
    }

    fun setUsagePeriodMode(mode: UsagePeriodMode) {
        val today = LocalDate.now()
        val (start, end) = when (mode) {
            UsagePeriodMode.LAST_7_DAYS -> today.minusDays(6).toEpochDay() to today.toEpochDay()
            UsagePeriodMode.LAST_30_DAYS -> today.minusDays(29).toEpochDay() to today.toEpochDay()
            UsagePeriodMode.SINGLE_DAY -> today.toEpochDay() to today.toEpochDay()
            UsagePeriodMode.CUSTOM_RANGE -> phoneUsageReportFlow.value.startEpochDay to phoneUsageReportFlow.value.endEpochDay
        }
        refreshPhoneUsageAnalytics(mode, start, end)
    }

    fun setUsageCustomDateRange(startEpochDay: Long, endEpochDay: Long, mode: UsagePeriodMode = UsagePeriodMode.CUSTOM_RANGE) {
        refreshPhoneUsageAnalytics(mode, startEpochDay, endEpochDay)
    }

    // endregion

    // region Focus Guard Blocklists & Study-Only YouTube Resources

    fun upsertBlockedApp(rule: BlockedAppRule) {
        viewModelScope.launch {
            repository.upsertBlockedApp(rule)
        }
    }

    fun removeBlockedApp(packageName: String) {
        viewModelScope.launch {
            repository.deleteBlockedApp(packageName)
        }
    }

    fun toggleBlockedAppsForCategory(
        category: BlockedAppCategory,
        installedApps: List<com.example.system.InstalledAppChoice>,
        enable: Boolean
    ) {
        viewModelScope.launch {
            val matching = installedApps.filter {
                PhoneUsageAnalyticsEngine.suggestBlockedCategory(it.packageName, it.label) == category
            }
            matching.forEach { app ->
                if (enable) {
                    repository.upsertBlockedApp(
                        BlockedAppRule(
                            packageName = app.packageName,
                            appLabel = app.label,
                            category = category,
                            blockInNormal = false,
                            blockInHigh = true,
                            blockInCustom = true,
                            isBlocked = true
                        )
                    )
                } else {
                    repository.deleteBlockedApp(app.packageName)
                }
            }
        }
    }

    fun addStudyResource(
        title: String,
        url: String,
        type: StudyResourceType,
        subjectId: Long?,
        channelName: String
    ) {
        val cleanTitle = title.trim()
        val cleanUrl = url.trim()
        if (cleanTitle.isBlank() || !com.example.system.AppLauncherHelper.isValidStudyUrl(cleanUrl)) return
        viewModelScope.launch {
            repository.addStudyResource(
                StudyResource(
                    subjectId = subjectId,
                    title = cleanTitle.take(100),
                    url = cleanUrl,
                    resourceType = type,
                    channelName = channelName.trim().take(60)
                )
            )
        }
    }

    fun markStudyResourceOpened(id: Long) {
        viewModelScope.launch { repository.markStudyResourceOpened(id) }
    }

    fun deleteStudyResource(id: Long) {
        viewModelScope.launch { repository.deleteStudyResource(id) }
    }

    // region Standalone Blocking Sessions & Temporary App Allowances (Block Tab)

    fun startManualBlockingSession(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        val clampedMin = durationMinutes.coerceIn(5, 480)
        val session = com.example.model.ManualBlockingSession(
            isActive = true,
            isPaused = false,
            startedAtEpochMs = now,
            endsAtEpochMs = now + clampedMin * 60_000L,
            remainingOnPauseMs = 0L
        )
        ephemeral.update {
            it.copy(
                nowEpochMs = now,
                hasUsageAccessPermission = AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)
            )
        }
        viewModelScope.launch {
            repository.saveManualBlockingSession(session)
            if (AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)) {
                FocusTimerService.syncForManualBlocking(appContext, session)
            }
        }
    }

    fun pauseManualBlockingSession() {
        val now = System.currentTimeMillis()
        val current = uiState.value.manualBlockingSession
        if (!current.isActive || current.isPaused) return
        val remainingMs = (current.endsAtEpochMs - now).coerceAtLeast(0L)
        val paused = current.copy(isPaused = true, remainingOnPauseMs = remainingMs)
        viewModelScope.launch {
            repository.saveManualBlockingSession(paused)
            if (!uiState.value.timer.hasStarted) {
                FocusTimerService.stopService(appContext)
            }
        }
    }

    fun resumeManualBlockingSession() {
        val now = System.currentTimeMillis()
        val current = uiState.value.manualBlockingSession
        if (!current.isActive || !current.isPaused) return
        val resumed = current.copy(
            isPaused = false,
            endsAtEpochMs = if (current.remainingOnPauseMs > 0L) now + current.remainingOnPauseMs else 0L,
            remainingOnPauseMs = 0L
        )
        ephemeral.update { it.copy(nowEpochMs = now) }
        viewModelScope.launch {
            repository.saveManualBlockingSession(resumed)
            if (AppUsageAwarenessHelper.hasUsageAccessPermission(appContext)) {
                FocusTimerService.syncForManualBlocking(appContext, resumed)
            }
        }
    }

    fun endManualBlockingSession() {
        viewModelScope.launch {
            repository.saveManualBlockingSession(com.example.model.ManualBlockingSession())
            if (!uiState.value.timer.hasStarted) {
                FocusTimerService.stopService(appContext)
            }
        }
    }

    fun allowAppTemporarily(
        packageName: String,
        appLabel: String,
        reason: String,
        durationMinutes: Int
    ) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return
        val now = System.currentTimeMillis()
        val clampedMins = durationMinutes.coerceIn(1, 60)
        val newEntry = com.example.model.TemporaryAppAllowance(
            packageName = cleanPkg,
            appLabel = appLabel.trim().ifBlank { cleanPkg },
            reason = reason.trim().ifBlank { "Temporary exception" }.take(80),
            grantedAtEpochMs = now,
            expiresAtEpochMs = now + clampedMins * 60_000L
        )
        ephemeral.update { it.copy(nowEpochMs = now) }
        viewModelScope.launch {
            val current = repository.getTemporaryAllowancesOnce()
                .filter { it.isActive(now) && !it.packageName.equals(cleanPkg, ignoreCase = true) }
            repository.saveTemporaryAllowances(current + newEntry)
        }
    }

    fun revokeTemporaryAllowance(packageName: String) {
        val now = System.currentTimeMillis()
        ephemeral.update { it.copy(nowEpochMs = now) }
        viewModelScope.launch {
            val updated = repository.getTemporaryAllowancesOnce()
                .filter { it.isActive(now) && !it.packageName.equals(packageName, ignoreCase = true) }
            repository.saveTemporaryAllowances(updated)
        }
    }

    // endregion

    // endregion

    // region Analytics Privacy & Transparent Family / Parent Controls

    fun setCloudUsageSummarySyncEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateCloudUsageSummarySyncEnabled(enabled) }
    }

    fun updateFamilyControlSettings(settings: FamilyControlSettings) {
        viewModelScope.launch { repository.updateFamilyControlSettings(settings) }
    }

    // endregion

    // region Supabase Authentication, Offline-First Sync & Account Isolation

    fun clearAuthNotice() {
        cloudMetaFlow.update { Triple(it.first, it.second, Pair(null, null)) }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            when (val res = authClient.signUp(email, password, displayName)) {
                is SupabaseAuthResult.Success -> {
                    val session = res.data
                    if (session.isSignedIn) {
                        handleAuthenticatedSession(session)
                        cloudMetaFlow.update { Triple(false, false, Pair(null, res.notice ?: "Signed in and syncing your Brain Buffer data.")) }
                        onSuccess()
                    } else {
                        cloudMetaFlow.update { Triple(false, false, Pair(null, res.notice ?: "Check your email to confirm your account.")) }
                    }
                }
                is SupabaseAuthResult.Error -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(res.message, null)) }
                }
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            when (val res = authClient.signInWithPassword(email, password)) {
                is SupabaseAuthResult.Success -> {
                    handleAuthenticatedSession(res.data)
                    cloudMetaFlow.update { Triple(false, false, Pair(null, "Welcome back! Syncing your cloud workspace…")) }
                    onSuccess()
                }
                is SupabaseAuthResult.Error -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(res.message, null)) }
                }
            }
        }
    }

    fun resetPasswordEmail(email: String) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            when (val res = authClient.resetPassword(email)) {
                is SupabaseAuthResult.Success -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(null, res.notice)) }
                }
                is SupabaseAuthResult.Error -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(res.message, null)) }
                }
            }
        }
    }

    fun resendVerificationEmail(email: String) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            when (val res = authClient.resendVerificationEmail(email)) {
                is SupabaseAuthResult.Success -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(null, res.notice)) }
                }
                is SupabaseAuthResult.Error -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(res.message, null)) }
                }
            }
        }
    }

    /**
     * Handles incoming `com.aistudio.brainbuffer.vkrqmx://auth-callback` deep links for:
     * - Implicit fragment `#access_token=...&refresh_token=...` (email verification & password recovery)
     * - Auth error responses (`error_description` / `error`)
     */
    fun handleAuthCallbackUri(uri: Uri) {
        viewModelScope.launch {
            val queryError = uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("error")
            val fragmentParams = parseUrlEncodedParams(uri.fragment.orEmpty())
            val fragmentError = fragmentParams["error_description"] ?: fragmentParams["error"]
            val errorText = queryError ?: fragmentError
            if (!errorText.isNullOrBlank()) {
                cloudMetaFlow.update {
                    Triple(false, false, Pair("Sign-in did not complete: $errorText", null))
                }
                return@launch
            }

            val accessToken = fragmentParams["access_token"].orEmpty()
            val refreshToken = fragmentParams["refresh_token"].orEmpty()
            val expiresIn = fragmentParams["expires_in"]?.toLongOrNull() ?: 3600L
            val callbackType = fragmentParams["type"].orEmpty()

            if (accessToken.isNotBlank()) {
                cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
                val expiresAt = (System.currentTimeMillis() / 1000L) + expiresIn
                when (val userRes = authClient.fetchUser(accessToken, refreshToken, expiresAt)) {
                    is SupabaseAuthResult.Success -> {
                        handleAuthenticatedSession(userRes.data)
                        val notice = when (callbackType.lowercase()) {
                            "signup" -> "Email verified! Your Brain Buffer cloud workspace is now syncing."
                            "recovery" -> "Recovery link verified. You're signed back into your account."
                            else -> "Signed in and syncing your Brain Buffer workspace."
                        }
                        cloudMetaFlow.update { Triple(false, false, Pair(null, notice)) }
                    }
                    is SupabaseAuthResult.Error -> {
                        cloudMetaFlow.update { Triple(false, false, Pair(userRes.message, null)) }
                    }
                }
            }
        }
    }

    private fun parseUrlEncodedParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    fun signOutCloud(clearLocalDeviceData: Boolean = true) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            CloudSyncScheduler.cancelSync(appContext)
            val current = repository.getAuthSessionOnce()
            authClient.signOut(current.accessToken)
            repository.clearAuthSession()
            if (clearLocalDeviceData) {
                repository.clearLocalTablesForAccountSwitch()
                repository.updateActiveAccountOwnerId("")
            }
            cloudMetaFlow.update {
                Triple(false, false, Pair(null, "Signed out. Your cloud backup stays safe in Supabase."))
            }
        }
    }

    fun deleteCloudAccount(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            cloudMetaFlow.update { Triple(it.first, true, Pair(null, null)) }
            var session = repository.getAuthSessionOnce()
            if (session.isExpired() && session.refreshToken.isNotBlank()) {
                val refreshed = authClient.refreshSession(session.refreshToken)
                if (refreshed is SupabaseAuthResult.Success) {
                    session = refreshed.data
                    repository.saveAuthSession(session)
                }
            }
            when (val res = authClient.requestAccountDeletion(session.accessToken)) {
                is SupabaseAuthResult.Success -> {
                    CloudSyncScheduler.cancelSync(appContext)
                    repository.clearAuthSession()
                    repository.clearLocalTablesForAccountSwitch()
                    repository.updateActiveAccountOwnerId("")
                    val msg = res.notice ?: "Account deleted permanently."
                    cloudMetaFlow.update { Triple(false, false, Pair(null, msg)) }
                    onResult(true, msg)
                }
                is SupabaseAuthResult.Error -> {
                    cloudMetaFlow.update { Triple(false, false, Pair(res.message, null)) }
                    onResult(false, res.message)
                }
            }
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            val session = repository.getAuthSessionOnce()
            if (!SupabaseConfig.isConfigured || !session.isSignedIn) return@launch
            cloudMetaFlow.update { Triple(true, it.second, Pair(null, it.third.second)) }
            val outcome = repository.syncManager.syncAll(
                currentSession = session,
                usageReportForOptInSummary = phoneUsageReportFlow.value
            )
            outcome.refreshedSession?.let { repository.saveAuthSession(it) }
            when {
                outcome.alreadyRunning -> {
                    cloudMetaFlow.update { Triple(false, it.second, it.third) }
                }
                outcome.success -> {
                    repository.updateLastSyncedAtMs(System.currentTimeMillis())
                    cloudMetaFlow.update { Triple(false, false, Pair(null, null)) }
                }
                else -> {
                    if (!outcome.requiresReauth) {
                        CloudSyncScheduler.scheduleSync(appContext)
                    }
                    cloudMetaFlow.update { Triple(false, false, Pair(outcome.errorMessage, null)) }
                }
            }
        }
    }

    private suspend fun handleAuthenticatedSession(session: SupabaseAuthSession) {
        val previousOwner = repository.getPreferencesOnce().activeAccountOwnerId
        if (previousOwner.isNotBlank() && previousOwner != session.userId) {
            // Strict account isolation: never leak previous user's local records into a different account
            repository.clearLocalTablesForAccountSwitch()
        }
        val hydratedSession = if (session.providers.isEmpty() && session.accessToken.isNotBlank()) {
            when (val userRes = authClient.fetchUser(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAtEpochSec = session.expiresAtEpochSec
            )) {
                is SupabaseAuthResult.Success -> userRes.data
                is SupabaseAuthResult.Error -> session
            }
        } else {
            session
        }
        repository.saveAuthSession(hydratedSession)
        repository.updateActiveAccountOwnerId(hydratedSession.userId)
        // Trigger immediate background sync
        syncCloudNow()
    }

    // endregion

    override fun onCleared() {
        tickerJob?.cancel()
        externalTimerSyncJob?.cancel()
        timerController.close()
        soundscapeController.release()
        super.onCleared()
    }
}

