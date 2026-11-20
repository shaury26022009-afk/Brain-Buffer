package com.example.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("AMOLED Dark"),
    SYSTEM("Follow System")
}

enum class StudyLevel(val label: String, val placeholder: String) {
    SCHOOL("School", "e.g., Grade 11 · Science Stream"),
    COLLEGE("College", "e.g., B.S. Computer Science"),
    UNIVERSITY("University", "e.g., M.Sc. Cognitive Science"),
    COMPETITIVE("Competitive Exams", "e.g., JEE / NEET / MCAT / GRE"),
    SELF_LEARNING("Self-learning", "e.g., Android & Kotlin Engineering"),
    OTHER("Other", "e.g., Architecture Certification")
}

enum class PreferredStudyTime(val label: String, val windowHint: String) {
    MORNING("Morning", "6 AM – 12 PM"),
    AFTERNOON("Afternoon", "12 PM – 5 PM"),
    EVENING("Evening", "5 PM – 9 PM"),
    NIGHT("Night", "9 PM – 2 AM")
}

enum class StudentAvatar(val key: String, val label: String, val tintHex: Long) {
    SPROUT("sprout", "Sprout", 0xFF2B9A7C),
    SPARK("spark", "Spark", 0xFF6B4CE6),
    BOOK("book", "Scholar", 0xFF3B82F6),
    MOON("moon", "Night Owl", 0xFF9377F8),
    COMPASS("compass", "Explorer", 0xFFF59E0B),
    LOTUS("lotus", "Calm Mind", 0xFFEC4899)
}

enum class SubjectIcon(val key: String, val label: String) {
    BOOK("book", "Book"),
    CALCULATE("calculate", "Math"),
    SCIENCE("science", "Science"),
    CODE("code", "Code"),
    DESIGN("design", "Design"),
    LANGUAGE("language", "Language"),
    HISTORY("history", "History"),
    LIGHTBULB("lightbulb", "Ideas")
}

data class Subject(
    val id: Long = 0L,
    val name: String,
    val colorHex: Long = 0xFF7B5CFA,
    val iconKey: String = SubjectIcon.BOOK.key,
    val targetHoursPerWeek: Float = 4f,
    val examDateEpochDay: Long? = null
) {
    val accentColor: Color get() = Color(colorHex)

    fun examDateFormatted(): String? {
        val day = examDateEpochDay ?: return null
        return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }

    fun daysUntilExam(todayEpochDay: Long = LocalDate.now().toEpochDay()): Long? {
        val day = examDateEpochDay ?: return null
        return day - todayEpochDay
    }
}

enum class TaskPriority(val label: String, val sortOrder: Int) {
    HIGH("High", 0),
    MEDIUM("Medium", 1),
    LOW("Gentle", 2)
}

data class StudyTask(
    val id: Long = 0L,
    val title: String,
    val subjectId: Long? = null,
    val dueDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val dueTimeLabel: String = "6:00 PM",
    val estimatedMinutes: Int = 30,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val completedAtMs: Long? = null,
    val notes: String = ""
)

enum class FocusMode(val label: String, val defaultMinutes: Int) {
    FOCUS("Focus", 25),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

data class FocusSessionRecord(
    val id: Long = 0L,
    val subjectId: Long? = null,
    val subjectName: String = "General Study",
    val subjectColorHex: Long = 0xFF7B5CFA,
    val durationSeconds: Int,
    val plannedSeconds: Int,
    val sessionGoal: String = "",
    val mode: FocusMode = FocusMode.FOCUS,
    val completedAtMs: Long,
    val dateEpochDay: Long
) {
    val durationMinutes: Int get() = (durationSeconds / 60).coerceAtLeast(1)
}

enum class PlantStage(
    val label: String,
    val minSessions: Int,
    val description: String
) {
    SEED("Dormant Seed", 0, "Resting peacefully until your next focus session"),
    SPROUT("Tender Sprout", 1, "First tiny leaves emerging from quiet effort"),
    BUDDING("Budding Stem", 3, "Growing stronger with each unhurried block"),
    BLOOMING("In Bloom", 5, "Petals opening in calm rhythm"),
    RADIANT("Radiant Canopy", 8, "Full sanctuary bloom — timeless & serene");

    fun nextStage(): PlantStage? = when (this) {
        SEED -> SPROUT
        SPROUT -> BUDDING
        BUDDING -> BLOOMING
        BLOOMING -> RADIANT
        RADIANT -> null
    }

    companion object {
        fun fromSessions(sessions: Int): PlantStage = when {
            sessions >= RADIANT.minSessions -> RADIANT
            sessions >= BLOOMING.minSessions -> BLOOMING
            sessions >= BUDDING.minSessions -> BUDDING
            sessions >= SPROUT.minSessions -> SPROUT
            else -> SEED
        }
    }
}

enum class PlantSpecies(
    val displayName: String,
    val botanicalTag: String,
    val petalColor: Color,
    val secondaryColor: Color,
    val foliageColor: Color
) {
    LAVENDER_SPIRE("Lavender Spire", "Lavandula serenis", Color(0xFF9678F8), Color(0xFFD5C5FF), Color(0xFF4CA88B)),
    IRIS_LOTUS("Iris Lotus", "Nelumbo violacea", Color(0xFFD987D5), Color(0xFFF6C7F3), Color(0xFF3E9C82)),
    SAGE_BLOSSOM("Sage Blossom", "Salvia aurora", Color(0xFFF2A47E), Color(0xFFFFD8C4), Color(0xFF49AD8E)),
    MOONLIT_FERN("Moonlit Fern", "Dryopteris luna", Color(0xFF6CE0BE), Color(0xFFC5F5E7), Color(0xFF328C74)),
    VIOLET_BONSAI("Violet Bonsai", "Ficus noctis", Color(0xFF7C5EF2), Color(0xFFC5B3FF), Color(0xFF3C8A78))
}

enum class GardenDecoration(
    val key: String,
    val title: String,
    val unlockHint: String
) {
    STEPPING_STONES("stepping_stones", "Stepping Stones", "Complete your first focus session"),
    STONE_LANTERN("stone_lantern", "Warm Lantern", "Reach your daily target once"),
    WOODEN_BENCH("wooden_bench", "Quiet Bench", "Study on 3 different days"),
    STARRY_ARCH("starry_arch", "Lavender Arch", "Grow any plant to In Bloom")
}

data class GardenPlant(
    val id: Long = 0L,
    val species: PlantSpecies,
    val nickname: String,
    val sessionsLogged: Int = 0,
    val totalMinutes: Int = 0,
    val plantedDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val isActive: Boolean = false
) {
    val stage: PlantStage get() = PlantStage.fromSessions(sessionsLogged)

    val progressToNextStage: Float
        get() {
            val next = stage.nextStage() ?: return 1f
            val span = (next.minSessions - stage.minSessions).coerceAtLeast(1)
            val current = (sessionsLogged - stage.minSessions).coerceIn(0, span)
            return current.toFloat() / span.toFloat()
        }

    val sessionsUntilNextStage: Int
        get() {
            val next = stage.nextStage() ?: return 0
            return (next.minSessions - sessionsLogged).coerceAtLeast(0)
        }
}

enum class MindNoteType {
    DISTRACTION_DUMP,
    SESSION_REFLECTION
}

data class MindNote(
    val id: Long = 0L,
    val type: MindNoteType,
    val content: String,
    val moodTag: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class BreathingPattern(
    val title: String,
    val subtitle: String,
    val inhaleSec: Int,
    val holdSec: Int,
    val exhaleSec: Int,
    val restSec: Int
) {
    CALM_4_4_6("4-4-6 Unwind", "Settles racing thoughts", 4, 4, 6, 2),
    BOX_4_4_4_4("4-4-4-4 Box", "Steadies attention", 4, 4, 4, 4),
    QUICK_3_3_4("3-2-4 Quick", "Short chapter pause", 3, 2, 4, 1)
}

data class DailyActivityStat(
    val dateEpochDay: Long,
    val dayShort: String,
    val dayFull: String,
    val focusMinutes: Int,
    val sessionsCount: Int,
    val tasksCompleted: Int,
    val isToday: Boolean
)

data class SubjectStudySummary(
    val subject: Subject,
    val loggedMinutesThisWeek: Int,
    val loggedMinutesTotal: Int,
    val completedTasks: Int,
    val totalTasks: Int
) {
    val targetMinutesPerWeek: Int get() = (subject.targetHoursPerWeek * 60f).toInt().coerceAtLeast(30)
    val weeklyProgress: Float get() = (loggedMinutesThisWeek.toFloat() / targetMinutesPerWeek).coerceIn(0f, 1f)
}

data class UpcomingDeadlineItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateEpochDay: Long,
    val accentColor: Color,
    val isExam: Boolean
)

enum class ActivityCategory(val label: String, val countsAsFocusedStudy: Boolean) {
    STUDY("Focused Study", true),
    LECTURE("Study Lecture", false),
    READING("Reading & Notes", false),
    BREAK("Breaks", false),
    ENTERTAINMENT("Gaming & Entertainment", false),
    OTHER("Other", false)
}

enum class StudyActivityType(
    val label: String,
    val shortLabel: String,
    val defaultMinutes: Int,
    val category: ActivityCategory,
    val accentHex: Long
) {
    STUDYING("Studying", "Study", 45, ActivityCategory.STUDY, 0xFF7B5CFA),
    STUDY_PDF("Study PDF / Documents", "PDF", 25, ActivityCategory.READING, 0xFF0EA5E9),
    STUDY_LECTURE("Watching a study lecture", "Lecture", 30, ActivityCategory.LECTURE, 0xFF3B82F6),
    READING("Reading", "Reading", 25, ActivityCategory.READING, 0xFF2FA687),
    WRITING_NOTES("Writing notes", "Notes", 30, ActivityCategory.READING, 0xFF10B981),
    SOLVING_QUESTIONS("Solving questions", "Practice", 40, ActivityCategory.STUDY, 0xFF9377F8),
    SHORT_BREAK("Taking a short break", "Break", 10, ActivityCategory.BREAK, 0xFF6CE0BE),
    PLAYING_GAME("Playing a game", "Gaming", 20, ActivityCategory.ENTERTAINMENT, 0xFFF59E0B),
    ENTERTAINMENT("Entertainment", "Chill", 15, ActivityCategory.ENTERTAINMENT, 0xFFEC4899),
    OTHER("Other activity", "Other", 20, ActivityCategory.OTHER, 0xFF8E8AA8)
}

/**
 * Focus Guard 2.0 protection levels offering progressive distraction reduction
 * without trapping the user or bypassing Android system controls.
 */
enum class FocusGuardLevel(
    val label: String,
    val badge: String,
    val summary: String
) {
    NORMAL(
        label = "Normal Focus",
        badge = "Flexible",
        summary = "Study timer, persistent live notification & activity tracking while using other apps normally"
    ),
    HIGH(
        label = "High Focus",
        badge = "Dedicated",
        summary = "Pure AMOLED black AOD-inspired focus view, controlled phone access, voluntary DND & minimal controls"
    ),
    CUSTOM(
        label = "Custom Focus",
        badge = "Tailored",
        summary = "Your own durations, allowed study apps, DND, break rules & distraction settings"
    )
}

/**
 * Reliable 10-state Focus Guard 3.0 study session state machine (Part 9).
 */
enum class FocusSessionState(val label: String) {
    IDLE("Idle"),
    FOCUSING("Studying"),
    ACTIVITY_REQUEST("Activity Request"),
    APPROVED_STUDY_ACTIVITY("Approved Study Activity"),
    APPROVED_ENTERTAINMENT_ACTIVITY("Approved Entertainment"),
    BREAK("Break"),
    PAUSED("Paused"),
    RETURNING_TO_FOCUS("Returning to Study"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

/**
 * The 8 clear, fast options presented on "What do you need your phone for?" (Parts 3 & 5).
 */
enum class ControlledAccessOption(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val activityType: StudyActivityType,
    val defaultPresets: List<Int>,
    val supportsAppPicker: Boolean,
    val supportsPdfPicker: Boolean = false
) {
    STUDY_LECTURE(
        title = "Watch a study lecture",
        shortTitle = "Lecture",
        subtitle = "YouTube, Coursera or recorded class",
        activityType = StudyActivityType.STUDY_LECTURE,
        defaultPresets = listOf(10, 15, 20, 30, 45),
        supportsAppPicker = true
    ),
    STUDY_PDF(
        title = "Study PDF / Documents",
        shortTitle = "PDF / Docs",
        subtitle = "Open notes, slides or a textbook PDF",
        activityType = StudyActivityType.STUDY_PDF,
        defaultPresets = listOf(10, 15, 20, 30, 45),
        supportsAppPicker = true,
        supportsPdfPicker = true
    ),
    READING_RESEARCH(
        title = "Reading / Research",
        shortTitle = "Research",
        subtitle = "Articles, docs or reference lookup",
        activityType = StudyActivityType.READING,
        defaultPresets = listOf(10, 15, 20, 30),
        supportsAppPicker = true
    ),
    NOTES_WRITING(
        title = "Notes / Writing",
        shortTitle = "Notes",
        subtitle = "Notion, Google Docs, Keep or Obsidian",
        activityType = StudyActivityType.WRITING_NOTES,
        defaultPresets = listOf(10, 15, 20, 30, 45),
        supportsAppPicker = true
    ),
    TAKE_BREAK(
        title = "Take a short break",
        shortTitle = "Break",
        subtitle = "Rest your eyes, stretch or hydrate",
        activityType = StudyActivityType.SHORT_BREAK,
        defaultPresets = listOf(5, 10, 15, 20),
        supportsAppPicker = false
    ),
    GAMES(
        title = "Gaming",
        shortTitle = "Games",
        subtitle = "Timed entertainment allowance",
        activityType = StudyActivityType.PLAYING_GAME,
        defaultPresets = listOf(5, 10, 15, 20, 30),
        supportsAppPicker = true
    ),
    SOCIAL_ENTERTAINMENT(
        title = "Social media / Entertainment",
        shortTitle = "Social",
        subtitle = "Short intentional allowance",
        activityType = StudyActivityType.ENTERTAINMENT,
        defaultPresets = listOf(5, 10, 15, 20, 30),
        supportsAppPicker = true
    ),
    OTHER(
        title = "Other",
        shortTitle = "Other",
        subtitle = "Describe briefly & set a timer",
        activityType = StudyActivityType.OTHER,
        defaultPresets = listOf(5, 10, 15, 20, 30),
        supportsAppPicker = true
    )
}

/**
 * Editable default allowances & separate daily entertainment budget (Parts 5 & 8).
 */
data class ActivityAllowanceConfig(
    val pdfDefaultMinutes: Int = 25,
    val lectureDefaultMinutes: Int = 25,
    val readingDefaultMinutes: Int = 20,
    val notesDefaultMinutes: Int = 25,
    val breakDefaultMinutes: Int = 10,
    val gamingDefaultMinutes: Int = 15,
    val socialDefaultMinutes: Int = 10,
    val dailyEntertainmentLimitMinutes: Int = 45
) {
    fun defaultMinutesFor(option: ControlledAccessOption): Int = when (option) {
        ControlledAccessOption.STUDY_PDF -> pdfDefaultMinutes
        ControlledAccessOption.STUDY_LECTURE -> lectureDefaultMinutes
        ControlledAccessOption.READING_RESEARCH -> readingDefaultMinutes
        ControlledAccessOption.NOTES_WRITING -> notesDefaultMinutes
        ControlledAccessOption.TAKE_BREAK -> breakDefaultMinutes
        ControlledAccessOption.GAMES -> gamingDefaultMinutes
        ControlledAccessOption.SOCIAL_ENTERTAINMENT -> socialDefaultMinutes
        ControlledAccessOption.OTHER -> 15
    }
}

/**
 * High Focus & Focus Guard 3.0 user settings (Parts 7, 8, 9, 10, 11).
 */
data class FocusGuardSettings(
    val defaultLevel: FocusGuardLevel = FocusGuardLevel.HIGH,
    val defaultFocusMinutes: Int = 45,
    val defaultActivityAllowanceMinutes: Int = 15,
    val defaultBreakMinutes: Int = 10,
    val strictnessLevel: StrictnessLevel = StrictnessLevel.COMMITTED,
    val dndIntegrationEnabled: Boolean = false,
    val notificationSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val activityTrackingEnabled: Boolean = true,
    val autoReturnToStudyReminder: Boolean = true,
    val emergencyExceptionsAlwaysOn: Boolean = true,
    val optionalAppPinningEnabled: Boolean = false,
    val distractionWarningsEnabled: Boolean = true,
    val distractionCooldownSeconds: Int = 90,
    val customExtensionMinutes: Int = 5,
    val pauseStudyDuringTempActivity: Boolean = true,
    val liveUpdatesEnabled: Boolean = true,
    val allowedStudyAppsCsv: String = "PDF Reader, Notes, Calculator, YouTube",
    val optionalEntertainmentAppsCsv: String = "Instagram, TikTok, Games",
    val allowances: ActivityAllowanceConfig = ActivityAllowanceConfig()
)

/**
 * User-configured association between an installed Android package and an activity category (Part 6).
 */
data class AppCategoryMapping(
    val packageName: String,
    val appLabel: String,
    val category: ActivityCategory,
    val activityType: StudyActivityType,
    val isAllowedDuringFocus: Boolean = category == ActivityCategory.STUDY ||
        category == ActivityCategory.READING ||
        category == ActivityCategory.LECTURE,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Non-intrusive gentle warning surfaced when optional Usage Access observes an app
 * outside the user's approved Focus Guard activity (Part 6).
 */
data class UnapprovedAppWarning(
    val packageName: String,
    val appDisplayName: String,
    val observedCategory: ActivityCategory,
    val message: String
)

data class CustomActivityOption(
    val id: Long = 0L,
    val label: String,
    val category: ActivityCategory = ActivityCategory.OTHER,
    val defaultDurationMinutes: Int = 20,
    val accentHex: Long = 0xFF7B5CFA
)

data class ActivityLogRecord(
    val id: Long = 0L,
    val sessionKey: String = "",
    val activityType: StudyActivityType = StudyActivityType.STUDYING,
    val activityLabel: String = "Studying",
    val category: ActivityCategory = ActivityCategory.STUDY,
    val subjectId: Long? = null,
    val subjectName: String = "General Study",
    val subjectColorHex: Long = 0xFF7B5CFA,
    val plannedSeconds: Int = 0,
    val actualSeconds: Int = 0,
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val wasPlanned: Boolean = true,
    val notes: String = ""
) {
    val actualMinutes: Int get() = (actualSeconds / 60).coerceAtLeast(if (actualSeconds > 0) 1 else 0)
    val plannedMinutes: Int get() = (plannedSeconds / 60).coerceAtLeast(1)
    val durationDeltaMinutes: Int get() = (actualSeconds - plannedSeconds) / 60
}

enum class StrictnessLevel(val label: String, val summary: String) {
    GENTLE("Gentle", "Minimal screen with flexible pause & quick switching"),
    COMMITTED("Committed", "Full-screen focus with optional DND & exit confirmation"),
    DEEP("Deep", "Pure distraction-free focus + AOD display + DND protection")
}

enum class BreakPolicy(val label: String) {
    REMIND_ONLY("Remind when finished"),
    AUTO_OFFER_BREAK("Suggest break after focus"),
    MANUAL("Manual control")
}

data class StrictFocusPreset(
    val id: Long = 0L,
    val name: String,
    val durationMinutes: Int = 45,
    val subjectId: Long? = null,
    val activityType: StudyActivityType = StudyActivityType.STUDYING,
    val dailyGoalMinutes: Int = 90,
    val breakPolicy: BreakPolicy = BreakPolicy.AUTO_OFFER_BREAK,
    val strictnessLevel: StrictnessLevel = StrictnessLevel.COMMITTED,
    val enableDnd: Boolean = false,
    val enableAodDisplay: Boolean = false,
    val allowedAppsCsv: String = "Notes, Calculator, PDF Reader",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val allowedAppsList: List<String>
        get() = allowedAppsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Optional starter preset templates shown as one-tap suggestions in Focus Guard Settings (never forced).
 */
object FocusGuardPresetTemplates {
    val templates = listOf(
        StrictFocusPreset(
            name = "Exam Preparation",
            durationMinutes = 60,
            activityType = StudyActivityType.STUDYING,
            dailyGoalMinutes = 120,
            breakPolicy = BreakPolicy.AUTO_OFFER_BREAK,
            strictnessLevel = StrictnessLevel.COMMITTED,
            enableDnd = true,
            enableAodDisplay = true,
            allowedAppsCsv = "PDF Reader, Study Lectures, Calculator"
        ),
        StrictFocusPreset(
            name = "Normal Study",
            durationMinutes = 45,
            activityType = StudyActivityType.STUDYING,
            dailyGoalMinutes = 90,
            breakPolicy = BreakPolicy.AUTO_OFFER_BREAK,
            strictnessLevel = StrictnessLevel.GENTLE,
            enableDnd = false,
            enableAodDisplay = false,
            allowedAppsCsv = "PDF Reader, Notes, Browser"
        ),
        StrictFocusPreset(
            name = "Deep Work",
            durationMinutes = 90,
            activityType = StudyActivityType.SOLVING_QUESTIONS,
            dailyGoalMinutes = 150,
            breakPolicy = BreakPolicy.MANUAL,
            strictnessLevel = StrictnessLevel.DEEP,
            enableDnd = true,
            enableAodDisplay = true,
            allowedAppsCsv = "Manual activity switching only"
        )
    )
}

data class NotificationPreferences(
    val activeFocusEnabled: Boolean = true,
    val breakRemindersEnabled: Boolean = true,
    val activityRemindersEnabled: Boolean = true,
    val tempActivityTimerEnabled: Boolean = true,
    val distractionWarningsEnabled: Boolean = true,
    val upcomingTasksEnabled: Boolean = true,
    val examCountdownsEnabled: Boolean = true,
    val studyRemindersEnabled: Boolean = true,
    val completedSessionsEnabled: Boolean = true,
    val hidePrivateNotesOnLockScreen: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7
)

data class PausedParentSession(
    val sessionId: String,
    val mode: FocusMode,
    val activityType: StudyActivityType,
    val activityLabel: String,
    val selectedSubjectId: Long?,
    val sessionGoal: String,
    val totalSeconds: Int,
    val accumulatedFocusedMs: Long,
    val isStrictFocus: Boolean,
    val strictFocusAodStyle: Boolean,
    val focusGuardLevel: FocusGuardLevel = if (isStrictFocus) FocusGuardLevel.HIGH else FocusGuardLevel.NORMAL
)

data class AppUsageSuggestion(
    val packageName: String,
    val appDisplayName: String,
    val suggestedActivity: StudyActivityType,
    val suggestedDurationMinutes: Int
)

data class UserPreferences(
    val isLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val studentName: String = "",
    val avatarKey: String = StudentAvatar.SPARK.key,
    val studyLevel: StudyLevel = StudyLevel.UNIVERSITY,
    val courseOrExamName: String = "",
    val studyGoals: Set<String> = emptySet(),
    val customGoal: String = "",
    val preferredFocusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val preferredStudyTime: PreferredStudyTime = PreferredStudyTime.EVENING,
    val weeklyStudyDaysTarget: Int = 5,
    val biggestDistractions: Set<String> = emptySet(),
    val otherDistraction: String = "",
    val dailyTargetMinutes: Int = 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val breakRemindersEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotionOverride: Boolean = false,
    val seedsOwned: Int = 1,
    val unlockedDecorations: Set<String> = emptySet(),
    val mindResetCount: Int = 0,
    val performanceOverride: com.example.performance.PerformanceProfileOverride =
        com.example.performance.PerformanceProfileOverride.AUTO,
    val notificationPrefs: NotificationPreferences = NotificationPreferences(),
    val appUsageAwarenessEnabled: Boolean = false,
    val aodFocusDisplayEnabled: Boolean = false,
    val savedDndInterruptionFilter: Int = -1,
    val focusGuardSettings: FocusGuardSettings = FocusGuardSettings(),
    val distractionWarningsToday: Int = 0,
    val distractionWarningsWeek: Int = 0
) {
    val displayName: String get() = studentName.trim().ifBlank { "Student" }
    val avatar: StudentAvatar get() = StudentAvatar.entries.firstOrNull { it.key == avatarKey } ?: StudentAvatar.SPARK
}

data class PersistedTimerSnapshot(
    val mode: FocusMode = FocusMode.FOCUS,
    val selectedSubjectId: Long? = null,
    val sessionGoal: String = "",
    val totalSeconds: Int = 25 * 60,
    val accumulatedFocusedMs: Long = 0L,
    val runningSinceEpochMs: Long? = null,
    val hasStarted: Boolean = false,
    val sessionId: String = "",
    val runningSinceElapsedRealtimeMs: Long? = null,
    val activeActivityType: StudyActivityType = StudyActivityType.STUDYING,
    val activeActivityLabel: String = StudyActivityType.STUDYING.label,
    val activeActivityCategory: ActivityCategory = ActivityCategory.STUDY,
    val activityStartedAtEpochMs: Long? = null,
    val activityWasPlanned: Boolean = true,
    val isStrictFocus: Boolean = false,
    val strictFocusAodStyle: Boolean = false,
    val strictFocusDndActive: Boolean = false,
    val strictFocusStrictness: StrictnessLevel = StrictnessLevel.COMMITTED,
    val pausedParentSession: PausedParentSession? = null,
    val focusGuardLevel: FocusGuardLevel = FocusGuardLevel.NORMAL,
    val approvedPackageName: String = "",
    val approvedAppLabel: String = "",
    val approvedDocumentUri: String = ""
) {
    val isSwitchedActivity: Boolean get() = pausedParentSession != null
}

data class SessionCelebration(
    val durationMinutes: Int,
    val subjectName: String,
    val plantName: String,
    val stageLabel: String,
    val leveledUp: Boolean,
    val seedsEarned: Int,
    val newlyUnlockedDecoration: GardenDecoration? = null,
    val activityLabel: String = StudyActivityType.STUDYING.label,
    val activityCategory: ActivityCategory = ActivityCategory.STUDY,
    val canReturnToStudy: Boolean = false
)

object OnboardingOptions {
    val suggestedSubjects = listOf(
        Triple("Mathematics", 0xFF7B5CFA, SubjectIcon.CALCULATE.key),
        Triple("Physics", 0xFF3B82F6, SubjectIcon.SCIENCE.key),
        Triple("Computer Science", 0xFF10B981, SubjectIcon.CODE.key),
        Triple("Programming", 0xFF06B6D4, SubjectIcon.CODE.key),
        Triple("English", 0xFFF59E0B, SubjectIcon.LANGUAGE.key),
        Triple("Biology", 0xFF2FA687, SubjectIcon.SCIENCE.key),
        Triple("History", 0xFFEF6C57, SubjectIcon.HISTORY.key),
        Triple("Design", 0xFFEC4899, SubjectIcon.DESIGN.key)
    )

    val goalOptions = listOf(
        "Prepare for exams",
        "Complete assignments",
        "Learn a new skill",
        "Improve consistency",
        "Build a daily study habit",
        "Stay calm under pressure"
    )

    val distractionOptions = listOf(
        "Social media",
        "Games",
        "Notifications",
        "Videos",
        "Procrastination",
        "Noise",
        "Other"
    )

    val focusDurationChoices = listOf(15, 25, 30, 45, 50, 60)
    val breakDurationChoices = listOf(5, 10, 15)
    val dailyTargetChoices = listOf(30, 60, 120, 180)
}
