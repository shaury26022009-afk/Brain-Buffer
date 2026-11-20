package com.example.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.AppUsageSuggestion
import com.example.model.CustomActivityOption
import com.example.model.DailyActivityStat
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.GardenPlant
import com.example.model.MindNote
import com.example.model.MindNoteType
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.PlantSpecies
import com.example.model.PlantStage
import com.example.model.PreferredStudyTime
import com.example.model.SessionCelebration
import com.example.model.StrictFocusPreset
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.StudyLevel
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.SubjectStudySummary
import com.example.model.TaskPriority
import com.example.model.ThemeMode
import com.example.model.UpcomingDeadlineItem
import com.example.model.UserPreferences
import com.example.notifications.BrainBufferNotificationManager
import com.example.performance.DevicePerformanceSignals
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileEngine
import com.example.performance.PerformanceProfileOverride
import com.example.service.FocusTimerService
import com.example.system.AndroidDndBackend
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndFocusController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

data class FocusTimerUiState(
    val snapshot: PersistedTimerSnapshot = PersistedTimerSnapshot(),
    val remainingSeconds: Int = 25 * 60,
    val progress: Float = 0f,
    val isZenMode: Boolean = false,
    val sessionState: com.example.model.FocusSessionState = com.example.model.FocusSessionState.IDLE
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

    val formattedTime: String
        get() {
            val mins = (remainingSeconds / 60).coerceAtLeast(0)
            val secs = (remainingSeconds % 60).coerceAtLeast(0)
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
    val wasRecentlyCancelled: Boolean = false
)

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
    val resolvedPerformanceProfile: PerformanceProfile = PerformanceProfile.BALANCED
) {
    val todayEpochDay: Long get() = LocalDate.now().toEpochDay()

    val motivationalMessage: String
        get() = BrainBufferRepository.motivationalNotes[quoteIndex % BrainBufferRepository.motivationalNotes.size]

    val distractionTip: String
        get() = BrainBufferRepository.distractionTipFor(preferences.biggestDistractions)

    val focusOnlySessions: List<FocusSessionRecord>
        get() = sessions.filter { it.mode == FocusMode.FOCUS }

    val todayFocusMinutes: Int
        get() = focusOnlySessions
            .filter { it.dateEpochDay == todayEpochDay }
            .sumOf { it.durationSeconds } / 60

    val todaySessionsCount: Int
        get() = focusOnlySessions.count { it.dateEpochDay == todayEpochDay }

    val todayTargetProgress: Float
        get() = (todayFocusMinutes.toFloat() / preferences.dailyTargetMinutes.coerceAtLeast(15)).coerceIn(0f, 1f)

    val totalFocusMinutesAllTime: Int
        get() = focusOnlySessions.sumOf { it.durationSeconds } / 60

    val weeklyActivity: List<DailyActivityStat>
        get() = BrainBufferRepository.buildWeeklyActivity(sessions, tasks)

    val weeklyFocusMinutes: Int
        get() = weeklyActivity.sumOf { it.focusMinutes }

    val activeDaysThisWeek: Int
        get() = weeklyActivity.count { it.focusMinutes > 0 }

    val currentStreakDays: Int
        get() = BrainBufferRepository.computeCurrentStreakDays(sessions)

    val bestStreakDays: Int
        get() = BrainBufferRepository.computeBestStreakDays(sessions)

    val bestDayMinutes: Int
        get() = focusOnlySessions
            .groupBy { it.dateEpochDay }
            .maxOfOrNull { (_, list) -> list.sumOf { it.durationSeconds } / 60 } ?: 0

    val longestSessionMinutes: Int
        get() = (focusOnlySessions.maxOfOrNull { it.durationSeconds } ?: 0) / 60

    val completedTasksCount: Int
        get() = tasks.count { it.isCompleted }

    val todayTasks: List<StudyTask>
        get() = tasks.filter { it.dueDateEpochDay == todayEpochDay }

    val activePlant: GardenPlant?
        get() = plants.firstOrNull { it.isActive } ?: plants.firstOrNull()

    /**
     * Today's activity breakdown strictly separating Focused Study from Lectures,
     * Reading & Notes, Breaks, Gaming & Entertainment, and Other activities.
     */
    val todayActivityLogs: List<ActivityLogRecord>
        get() = activityLogs.filter { it.dateEpochDay == todayEpochDay }

    val todayCategoryMinutes: Map<ActivityCategory, Int>
        get() {
            val result = linkedMapOf<ActivityCategory, Int>()
            ActivityCategory.entries.forEach { cat ->
                val fromLogs = todayActivityLogs.filter { it.category == cat }.sumOf { it.actualSeconds } / 60
                result[cat] = if (cat == ActivityCategory.STUDY) {
                    maxOf(fromLogs, todayFocusMinutes)
                } else {
                    fromLogs
                }
            }
            return result
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

    val weeklyCategoryMinutes: Map<ActivityCategory, Int>
        get() {
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
            return result
        }

    /**
     * Real, locally computed insights derived strictly from recorded user sessions & activity logs.
     * Never invents data or displays mock percentages.
     */
    val smartLocalInsights: List<String>
        get() {
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

            return insights.take(4)
        }

    val todayEntertainmentMinutes: Int
        get() = todayActivityLogs
            .filter { it.category == ActivityCategory.ENTERTAINMENT }
            .sumOf { it.actualSeconds } / 60

    val dailyEntertainmentLimitMinutes: Int
        get() = preferences.focusGuardSettings.allowances.dailyEntertainmentLimitMinutes

    val isDailyEntertainmentLimitReached: Boolean
        get() = todayEntertainmentMinutes >= dailyEntertainmentLimitMinutes

    val subjectSummaries: List<SubjectStudySummary>
        get() {
            val weekStart = todayEpochDay - 6
            return subjects.map { subject ->
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

    val upcomingDeadlines: List<UpcomingDeadlineItem>
        get() {
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

            return (taskDeadlines + examDeadlines).sortedBy { it.dateEpochDay }
        }
}

class BrainBufferViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = BrainBufferRepository(
        database = BrainBufferDatabase.getInstance(appContext),
        preferencesStore = UserPreferencesStore(appContext)
    )

    private val deviceSignals: DevicePerformanceSignals = PerformanceProfileEngine.inspectDevice(appContext)
    private val ephemeral = MutableStateFlow(EphemeralUiState())
    private var tickerJob: Job? = null
    private var isCompletingSession = false

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
        val timerSnapshot: PersistedTimerSnapshot
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
        repository.timerSnapshot
    ) { mindNotes, activityLogs, presets, customActs, timerSnapshot ->
        ExtendedDataBundle(mindNotes, activityLogs, presets, customActs, timerSnapshot)
    }

    val uiState: StateFlow<BrainBufferUiState> = combine(
        coreFlow,
        extendedFlow,
        repository.appCategoryMappings,
        ephemeral
    ) { core, ext, appMappings, local ->
        val nowEpoch = local.nowEpochMs
        val nowElapsed = local.nowElapsedMs
        val remaining = FocusTimerEngine.remainingSeconds(ext.timerSnapshot, nowEpoch, nowElapsed)
        val progress = FocusTimerEngine.progress(ext.timerSnapshot, nowEpoch, nowElapsed)
        val sessionState = com.example.domain.FocusSessionStateMachine.deriveState(
            snapshot = ext.timerSnapshot,
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
            appCategoryMappings = appMappings,
            timer = FocusTimerUiState(
                snapshot = ext.timerSnapshot,
                remainingSeconds = remaining,
                progress = progress,
                isZenMode = local.isZenMode,
                sessionState = sessionState
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
            deviceSignals = deviceSignals,
            resolvedPerformanceProfile = PerformanceProfileEngine.resolveProfile(deviceSignals)
        )
    )

    init {
        startMonotonicClockTicker()
    }

    /**
     * Refreshes monotonic + wall-clock time every 500ms while running, and detects completion
     * even if the timer finished while the app was minimized, locked, or recreated.
     */
    private fun startMonotonicClockTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val nowEpoch = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()
                val snap = uiState.value.timer.snapshot
                if (snap.runningSinceEpochMs != null) {
                    ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
                    if (FocusTimerEngine.isCompleted(snap, nowEpoch, nowElapsed) && !isCompletingSession) {
                        completeCurrentSession(autoCompleted = true)
                    }
                    delay(500L)
                } else {
                    delay(1000L)
                }
            }
        }
    }

    /**
     * Called when Activity resumes so timer catches up immediately after screen-off / backgrounding.
     */
    fun syncTimerOnResume() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
        val snap = uiState.value.timer.snapshot
        if (snap.runningSinceEpochMs != null && FocusTimerEngine.isCompleted(snap, nowEpoch, nowElapsed) && !isCompletingSession) {
            completeCurrentSession(autoCompleted = true)
        } else if (snap.hasStarted) {
            FocusTimerService.syncWithSnapshot(appContext, snap)
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
            val snap = uiState.value.timer.snapshot
            if (!snap.hasStarted && snap.runningSinceEpochMs == null) {
                val mins = when (snap.mode) {
                    FocusMode.FOCUS -> preferredFocusMinutes
                    FocusMode.SHORT_BREAK -> shortBreakMinutes
                    FocusMode.LONG_BREAK -> longBreakMinutes
                }
                repository.saveTimerSnapshot(FocusTimerEngine.configureMode(snap, snap.mode, mins))
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

    fun refreshAppUsageSuggestion() {
        val state = uiState.value
        val prefs = state.preferences
        if (!prefs.appUsageAwarenessEnabled) {
            ephemeral.update { it.copy(appUsageSuggestion = null, unapprovedAppWarning = null) }
            return
        }
        val mappings = state.appCategoryMappings
        val suggestion = AppUsageAwarenessHelper.detectRecentAppSuggestion(
            context = appContext,
            enabledByUser = true,
            userMappings = mappings
        )
        val warning = AppUsageAwarenessHelper.detectUnapprovedAppMismatch(
            context = appContext,
            enabledByUser = true,
            snapshot = state.timer.snapshot,
            userMappings = mappings
        )
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
            val snap = uiState.value.timer.snapshot
            if (snap.selectedSubjectId == subjectId) {
                repository.saveTimerSnapshot(snap.copy(selectedSubjectId = null))
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
        viewModelScope.launch {
            val next = FocusTimerEngine.configureMode(uiState.value.timer.snapshot, mode, minutes)
            repository.saveTimerSnapshot(next)
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
            ephemeral.update {
                it.copy(nowEpochMs = System.currentTimeMillis(), nowElapsedMs = SystemClock.elapsedRealtime())
            }
        }
    }

    fun setCustomTimerDuration(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        val snap = uiState.value.timer.snapshot
        viewModelScope.launch {
            val next = FocusTimerEngine.configureMode(snap, snap.mode, clamped)
            repository.saveTimerSnapshot(next)
            FocusTimerService.stopService(appContext)
            ephemeral.update {
                it.copy(nowEpochMs = System.currentTimeMillis(), nowElapsedMs = SystemClock.elapsedRealtime())
            }
        }
    }

    fun selectTimerSubject(subjectId: Long?) {
        val snap = uiState.value.timer.snapshot
        viewModelScope.launch {
            val updated = snap.copy(selectedSubjectId = subjectId)
            repository.saveTimerSnapshot(updated)
            if (updated.hasStarted) FocusTimerService.syncWithSnapshot(appContext, updated)
        }
    }

    fun updateSessionGoal(goal: String) {
        val snap = uiState.value.timer.snapshot
        viewModelScope.launch {
            repository.saveTimerSnapshot(snap.copy(sessionGoal = goal.take(80)))
        }
    }

    fun toggleZenMode() {
        ephemeral.update { it.copy(isZenMode = !it.isZenMode) }
    }

    fun startOrResumeTimer() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val snap = uiState.value.timer.snapshot
        val started = FocusTimerEngine.startOrResume(snap, nowEpoch, nowElapsed)
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed, wasRecentlyCancelled = false) }
        viewModelScope.launch {
            repository.saveTimerSnapshot(started)
            FocusTimerService.syncWithSnapshot(appContext, started)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun startQuickFocus(subjectId: Long? = null) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val prefs = uiState.value.preferences
        val currentSnap = uiState.value.timer.snapshot
        val base = if (currentSnap.mode != FocusMode.FOCUS || currentSnap.isSwitchedActivity) {
            FocusTimerEngine.configureMode(currentSnap, FocusMode.FOCUS, prefs.preferredFocusMinutes)
        } else {
            currentSnap
        }.let { snap ->
            if (subjectId != null) snap.copy(selectedSubjectId = subjectId) else snap
        }
        val running = FocusTimerEngine.startOrResume(base, nowEpoch, nowElapsed)
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed, wasRecentlyCancelled = false) }
        viewModelScope.launch {
            repository.saveTimerSnapshot(running)
            FocusTimerService.syncWithSnapshot(appContext, running)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun pauseTimer() {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val paused = FocusTimerEngine.pause(uiState.value.timer.snapshot, nowEpoch, nowElapsed)
        ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
        viewModelScope.launch {
            repository.saveTimerSnapshot(paused)
            FocusTimerService.syncWithSnapshot(appContext, paused)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun resetTimer() {
        val prevSessionId = uiState.value.timer.snapshot.sessionId
        val reset = FocusTimerEngine.reset(uiState.value.timer.snapshot)
        BrainBufferNotificationManager.cancelDistractionWarningNotification(appContext)
        com.example.system.DistractionAlertCoordinator.clearSession(prevSessionId)
        ephemeral.update {
            it.copy(
                nowEpochMs = System.currentTimeMillis(),
                nowElapsedMs = SystemClock.elapsedRealtime(),
                wasRecentlyCancelled = true,
                unapprovedAppWarning = null
            )
        }
        viewModelScope.launch {
            repository.saveTimerSnapshot(reset)
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

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
        val currentSnap = currentState.timer.snapshot

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

            val switched = FocusTimerEngine.switchActivity(
                current = currentSnap,
                activityType = activityType,
                activityLabel = activityLabel,
                category = category,
                durationMinutes = durationMinutes,
                subjectId = subjectId,
                wasPlanned = wasPlanned,
                nowEpochMs = nowEpoch,
                nowElapsedRealtimeMs = nowElapsed,
                approvedPackageName = approvedPackageName,
                approvedAppLabel = approvedAppLabel,
                approvedDocumentUri = approvedDocumentUri
            )
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
            repository.saveTimerSnapshot(switched)
            FocusTimerService.syncWithSnapshot(appContext, switched)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun startBreakFromCurrent(breakMinutes: Int = uiState.value.preferences.focusGuardSettings.defaultBreakMinutes) {
        switchActivity(
            activityType = StudyActivityType.SHORT_BREAK,
            activityLabel = "Taking a short break",
            category = ActivityCategory.BREAK,
            durationMinutes = breakMinutes.coerceIn(2, 60),
            subjectId = uiState.value.timer.selectedSubjectId,
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
        val snap = currentState.timer.snapshot
        if (snap.pausedParentSession == null) return

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

            val resumed = FocusTimerEngine.returnToPausedStudySession(snap, nowEpoch, nowElapsed)
                ?: FocusTimerEngine.reset(snap)
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
            repository.saveTimerSnapshot(resumed)
            FocusTimerService.syncWithSnapshot(appContext, resumed)
        }
    }

    /**
     * Extends the active study, break, lecture, or gaming timer without guilt.
     */
    fun extendActiveTimer(extraMinutes: Int) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val snap = uiState.value.timer.snapshot
        val extended = FocusTimerEngine.extendActiveTimer(snap, extraMinutes, nowEpoch, nowElapsed)
        BrainBufferNotificationManager.cancelCompletionNotification(appContext)
        ephemeral.update {
            it.copy(
                nowEpochMs = nowEpoch,
                nowElapsedMs = nowElapsed,
                pendingCelebration = null
            )
        }
        viewModelScope.launch {
            repository.saveTimerSnapshot(extended)
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
                    savePreviousFilter = { prior ->
                        repository.updateSavedDndInterruptionFilter(prior)
                    }
                )
            }
            val strictSnap = FocusTimerEngine.startStrictFocus(
                subjectId = subjectId,
                sessionGoal = sessionGoal,
                activityType = activityType,
                durationMinutes = durationMinutes,
                strictness = strictness,
                enableAodStyle = enableAodStyle,
                dndActive = dndActivated,
                nowEpochMs = nowEpoch,
                nowElapsedRealtimeMs = nowElapsed,
                guardLevel = guardLevel
            )
            ephemeral.update {
                it.copy(
                    nowEpochMs = nowEpoch,
                    nowElapsedMs = nowElapsed,
                    wasRecentlyCancelled = false,
                    unapprovedAppWarning = null
                )
            }
            repository.saveTimerSnapshot(strictSnap)
            FocusTimerService.syncWithSnapshot(appContext, strictSnap)
            com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun toggleStrictFocusAod() {
        val snap = uiState.value.timer.snapshot
        viewModelScope.launch {
            repository.saveTimerSnapshot(snap.copy(strictFocusAodStyle = !snap.strictFocusAodStyle))
        }
    }

    /**
     * Voluntary, reversible exit from Strict Focus Mode — always restores DND if active.
     */
    fun exitStrictFocus(keepTimerRunning: Boolean = false) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val snap = uiState.value.timer.snapshot
        viewModelScope.launch {
            restoreDndIfNeeded()
            val updated = if (keepTimerRunning) {
                snap.copy(
                    isStrictFocus = false,
                    strictFocusAodStyle = false,
                    strictFocusDndActive = false
                )
            } else {
                FocusTimerEngine.reset(snap)
            }
            ephemeral.update { it.copy(nowEpochMs = nowEpoch, nowElapsedMs = nowElapsed) }
            repository.saveTimerSnapshot(updated)
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
     * Completes the current session using real elapsed focused seconds (never paused time).
     * Strictly separates Focused Study (which grows the Garden & updates study streak) from
     * Lectures, Reading, Breaks, and Gaming/Entertainment (which log cleanly into activity_logs).
     * Deduplicated via `sessionKey` so FocusTimerService + ViewModel never double-record.
     */
    fun completeCurrentSession(autoCompleted: Boolean = false) {
        if (isCompletingSession) return
        isCompletingSession = true
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = uiState.value
        val snap = currentState.timer.snapshot

        viewModelScope.launch {
            try {
                val actualSeconds = if (autoCompleted) {
                    snap.totalSeconds
                } else {
                    val elapsed = FocusTimerEngine.actualFocusedSeconds(snap, nowEpoch, nowElapsed)
                    if (elapsed >= 60) elapsed else snap.totalSeconds
                }
                val selectedSubject = currentState.subjects.firstOrNull { it.id == snap.selectedSubjectId }
                val subjectName = selectedSubject?.name ?: "General Study"
                val sessionKey = snap.sessionId.ifBlank { FocusTimerEngine.generateSessionId() }
                val todayEpoch = LocalDate.now().toEpochDay()

                // Always record into activity_logs (deduplicated by sessionKey)
                repository.recordActivityLog(
                    ActivityLogRecord(
                        sessionKey = sessionKey,
                        activityType = snap.activeActivityType,
                        activityLabel = snap.activeActivityLabel,
                        category = snap.activeActivityCategory,
                        subjectId = selectedSubject?.id,
                        subjectName = subjectName,
                        subjectColorHex = selectedSubject?.colorHex ?: snap.activeActivityType.accentHex,
                        plannedSeconds = snap.totalSeconds,
                        actualSeconds = actualSeconds,
                        startedAtMs = snap.activityStartedAtEpochMs ?: (nowEpoch - actualSeconds * 1000L),
                        endedAtMs = nowEpoch,
                        dateEpochDay = todayEpoch,
                        wasPlanned = snap.activityWasPlanned,
                        notes = snap.sessionGoal.trim()
                    )
                )

                val isFocusedStudy = snap.activeActivityCategory.countsAsFocusedStudy && snap.mode == FocusMode.FOCUS
                if (isFocusedStudy) {
                    val record = FocusSessionRecord(
                        subjectId = selectedSubject?.id,
                        subjectName = subjectName,
                        subjectColorHex = selectedSubject?.colorHex ?: 0xFF7B5CFA,
                        durationSeconds = actualSeconds,
                        plannedSeconds = snap.totalSeconds,
                        sessionGoal = snap.sessionGoal.trim(),
                        mode = FocusMode.FOCUS,
                        completedAtMs = nowEpoch,
                        dateEpochDay = todayEpoch
                    )

                    val previousStage = currentState.activePlant?.stage
                    val (updatedPlant, unlockedDeco) = repository.recordFocusSessionAndGrowGarden(
                        session = record,
                        dailyTargetMinutes = currentState.preferences.dailyTargetMinutes,
                        allExistingSessions = currentState.sessions,
                        existingDecorations = currentState.preferences.unlockedDecorations,
                        sessionKey = sessionKey
                    )
                    val newStage = PlantStage.fromSessions(updatedPlant.sessionsLogged)

                    val resetSnap = FocusTimerEngine.reset(snap).copy(sessionGoal = "")
                    repository.saveTimerSnapshot(resetSnap)
                    restoreDndIfNeeded()
                    FocusTimerService.stopService(appContext)

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
                                plantName = updatedPlant.nickname,
                                stageLabel = newStage.label,
                                leveledUp = previousStage != null && newStage != previousStage,
                                seedsEarned = 1,
                                newlyUnlockedDecoration = unlockedDeco,
                                activityLabel = snap.activeActivityLabel,
                                activityCategory = snap.activeActivityCategory,
                                canReturnToStudy = false
                            )
                        )
                    }
                } else {
                    // Switched activity or break finished: never mixes into focused study stats!
                    val hasParent = snap.pausedParentSession != null
                    if (hasParent) {
                        val frozenSnap = snap.copy(
                            accumulatedFocusedMs = snap.totalSeconds * 1000L,
                            runningSinceEpochMs = null,
                            runningSinceElapsedRealtimeMs = null,
                            hasStarted = false
                        )
                        repository.saveTimerSnapshot(frozenSnap)
                    } else {
                        val focusReady = FocusTimerEngine.configureMode(
                            snap,
                            FocusMode.FOCUS,
                            currentState.preferences.preferredFocusMinutes
                        )
                        repository.saveTimerSnapshot(focusReady)
                        restoreDndIfNeeded()
                    }
                    FocusTimerService.stopService(appContext)
                    com.example.widget.BrainBufferFocusWidgetProvider.updateAllWidgets(appContext)

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
                                plantName = currentState.activePlant?.nickname ?: "Study Garden",
                                stageLabel = snap.activeActivityCategory.label,
                                leveledUp = false,
                                seedsEarned = 0,
                                newlyUnlockedDecoration = null,
                                activityLabel = snap.activeActivityLabel,
                                activityCategory = snap.activeActivityCategory,
                                canReturnToStudy = hasParent
                            )
                        )
                    }
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

    fun clearAllStudyData() {
        viewModelScope.launch {
            restoreDndIfNeeded()
            FocusTimerService.stopService(appContext)
            repository.clearAllStudyData()
        }
    }

    // endregion

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }
}
