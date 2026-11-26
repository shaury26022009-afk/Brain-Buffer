package com.example.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("AMOLED Dark"),
    SYSTEM("Follow System")
}

enum class UserProfileRole(
    val label: String,
    val subtitle: String,
    val defaultDisplayName: String
) {
    STUDENT("Student", "School, college, university or exam prep", "Student"),
    PROFESSIONAL("Professional", "Deep work, engineering, writing or projects", "Friend"),
    PERSONAL("Personal", "Healthier phone habits & mindful screen time", "Friend")
}

enum class StudyLevel(val label: String, val placeholder: String) {
    SCHOOL("School", "e.g., Grade 11 · Science Stream"),
    COLLEGE("College", "e.g., B.S. Computer Science"),
    UNIVERSITY("University", "e.g., M.Sc. Cognitive Science"),
    COMPETITIVE("Competitive Exams", "e.g., JEE / NEET / MCAT / GRE"),
    SELF_LEARNING("Self-learning", "e.g., Android & Kotlin Engineering"),
    OTHER("Other", "e.g., Software Engineering / Design")
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
 * The 6 clear, fast choices presented on "Need Phone" during Focus Guard sessions.
 */
enum class ControlledAccessOption(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val activityType: StudyActivityType,
    val defaultPresets: List<Int>,
    val supportsAppPicker: Boolean,
    val supportsPdfPicker: Boolean = false,
    val supportsFilePicker: Boolean = false,
    val supportsStudyYouTube: Boolean = false,
    val supportsDialer: Boolean = false
) {
    STUDY_LECTURE(
        title = "Watch Study Lectures",
        shortTitle = "Lectures",
        subtitle = "Approved study YouTube links, playlists & channels",
        activityType = StudyActivityType.STUDY_LECTURE,
        defaultPresets = listOf(10, 15, 25, 30, 45),
        supportsAppPicker = true,
        supportsStudyYouTube = true
    ),
    SMS_AND_EMAILS(
        title = "SMS and Emails",
        shortTitle = "Messages",
        subtitle = "Check urgent texts or emails briefly (never read by Brain Buffer)",
        activityType = StudyActivityType.OTHER,
        defaultPresets = listOf(3, 5, 10, 15),
        supportsAppPicker = true
    ),
    FILES_AND_DOCUMENTS(
        title = "Files and Documents",
        shortTitle = "Files",
        subtitle = "Open notes, slides or local documents via Android picker",
        activityType = StudyActivityType.READING,
        defaultPresets = listOf(10, 15, 25, 30, 45),
        supportsAppPicker = true,
        supportsFilePicker = true
    ),
    CALLS(
        title = "Calls",
        shortTitle = "Calls",
        subtitle = "Emergency & important calls always remain accessible",
        activityType = StudyActivityType.OTHER,
        defaultPresets = listOf(5, 10, 15, 20),
        supportsAppPicker = false,
        supportsDialer = true
    ),
    PDFS_AND_DRIVE(
        title = "PDFs and Drive",
        shortTitle = "PDFs / Drive",
        subtitle = "Open textbooks, PDFs or Google Drive study folders",
        activityType = StudyActivityType.STUDY_PDF,
        defaultPresets = listOf(10, 15, 25, 30, 45),
        supportsAppPicker = true,
        supportsPdfPicker = true
    ),
    CUSTOM(
        title = "Custom",
        shortTitle = "Custom",
        subtitle = "Choose any app or activity & duration (tracked separately)",
        activityType = StudyActivityType.OTHER,
        defaultPresets = listOf(5, 10, 15, 20, 30),
        supportsAppPicker = true
    )
}

/**
 * Editable default allowances & separate daily entertainment budget.
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
        ControlledAccessOption.STUDY_LECTURE -> lectureDefaultMinutes
        ControlledAccessOption.SMS_AND_EMAILS -> 5
        ControlledAccessOption.FILES_AND_DOCUMENTS -> readingDefaultMinutes
        ControlledAccessOption.CALLS -> 10
        ControlledAccessOption.PDFS_AND_DRIVE -> pdfDefaultMinutes
        ControlledAccessOption.CUSTOM -> 15
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
    val allowances: ActivityAllowanceConfig = ActivityAllowanceConfig(),
    val scheduledBlockingEnabled: Boolean = false,
    val scheduleStartHour: Int = 9,
    val scheduleEndHour: Int = 12,
    val scheduleDaysCsv: String = "MON,TUE,WED,THU,FRI",
    val blockDuringActiveFocus: Boolean = true,
    val allowTemporaryBreaks: Boolean = true,
    val essentialAppsCsv: String = "Phone, Messages, Clock, Settings",
    val gracePeriodSeconds: Int = 15,
    val unlockInterventionEnabled: Boolean = false,
    val defaultPurposeCategory: SessionPurposeCategory = SessionPurposeCategory.STUDY,
    val defaultBreakPlanMode: BreakPlanMode = BreakPlanMode.NONE
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
    val quietEndHour: Int = 7,
    val allowImportantCallsDuringFocus: Boolean = true,
    val allowAlarmsDuringFocus: Boolean = true,
    val muteDistractingAppsDuringFocus: Boolean = true
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
    val focusGuardLevel: FocusGuardLevel = if (isStrictFocus) FocusGuardLevel.HIGH else FocusGuardLevel.NORMAL,
    val purposeCategory: SessionPurposeCategory = SessionPurposeCategory.STUDY,
    val allowedPackages: Set<String> = emptySet(),
    val allowedAppLabels: List<String> = emptyList(),
    val breakPlanMode: BreakPlanMode = BreakPlanMode.NONE,
    val breakDurationMinutes: Int = 5,
    val breakIntervalMinutes: Int = 25,
    val breaksTakenCount: Int = 0,
    val accumulatedBreakSeconds: Int = 0,
    val accumulatedTempAccessSeconds: Int = 0,
    val accumulatedDistractionSeconds: Int = 0,
    val interventionCount: Int = 0,
    val linkedTaskId: Long? = null,
    val sessionStartedAtEpochMs: Long? = null
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
    val onboardingCurrentStep: Int = 0,
    val userRole: UserProfileRole = UserProfileRole.STUDENT,
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
    val distractionWarningsWeek: Int = 0,
    val cloudUsageSummarySyncEnabled: Boolean = false,
    val familyControlSettings: FamilyControlSettings = FamilyControlSettings(),
    val activeAccountOwnerId: String = "",
    val selectedSoundscapeKey: String = "OFF",
    val soundscapeVolume: Float = 0.72f,
    val soundscapeSyncWithTimer: Boolean = true
) {
    val displayName: String get() = studentName.trim().ifBlank { userRole.defaultDisplayName }
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
    val approvedDocumentUri: String = "",
    val linkedTaskId: Long? = null,
    val lastCompletedSessionId: String = "",
    val purposeCategory: SessionPurposeCategory = SessionPurposeCategory.STUDY,
    val allowedPackages: Set<String> = emptySet(),
    val allowedAppLabels: List<String> = emptyList(),
    val breakPlanMode: BreakPlanMode = BreakPlanMode.NONE,
    val breakDurationMinutes: Int = 5,
    val breakIntervalMinutes: Int = 25,
    val breaksTakenCount: Int = 0,
    val accumulatedBreakSeconds: Int = 0,
    val accumulatedTempAccessSeconds: Int = 0,
    val accumulatedDistractionSeconds: Int = 0,
    val interventionCount: Int = 0,
    val sessionStartedAtEpochMs: Long? = null
) {
    val isSwitchedActivity: Boolean get() = pausedParentSession != null
    val isOnBreak: Boolean get() = activeActivityCategory == ActivityCategory.BREAK && hasStarted
}

data class SessionCelebration(
    val durationMinutes: Int,
    val subjectName: String,
    val plantName: String = "",
    val stageLabel: String = "",
    val leveledUp: Boolean = false,
    val seedsEarned: Int = 0,
    val newlyUnlockedDecoration: GardenDecoration? = null,
    val activityLabel: String = StudyActivityType.STUDYING.label,
    val activityCategory: ActivityCategory = ActivityCategory.STUDY,
    val canReturnToStudy: Boolean = false,
    val actualSeconds: Int = durationMinutes * 60,
    val plannedSeconds: Int = durationMinutes * 60,
    val endedEarly: Boolean = false,
    val linkedTaskId: Long? = null,
    val linkedTaskTitle: String = ""
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

// ============================================================================
// FOCUS GUARD BLOCKLISTS, STUDY-ONLY YOUTUBE, USAGE ANALYTICS & CLOUD MODELS
// ============================================================================

enum class BlockedAppCategory(val label: String, val accentHex: Long) {
    SOCIAL_MEDIA("Social Media", 0xFFEC4899),
    SHORT_VIDEO("Short Videos", 0xFFF43F5E),
    GAMES("Games", 0xFFF59E0B),
    ENTERTAINMENT("Entertainment", 0xFF06B6D4),
    SHOPPING("Shopping", 0xFFFB7185),
    OTT_STREAMING("Streaming & Video", 0xFF8B5CF6),
    OTHER("Other Distracting Apps", 0xFF94A3B8)
}

/**
 * Explicit, time-boxed exception allowing a blocked app temporarily with a user-selected reason.
 */
data class TemporaryAppAllowance(
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long
) {
    fun isActive(nowEpochMs: Long): Boolean = expiresAtEpochMs > nowEpochMs

    fun remainingSeconds(nowEpochMs: Long): Int =
        ((expiresAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)

    fun remainingMinutes(nowEpochMs: Long): Int =
        ((remainingSeconds(nowEpochMs) + 59) / 60).coerceAtLeast(0)
}

/**
 * Standalone Focus Guard blocking session controllable directly from the Block tab
 * (independent of or alongside the Focus timer).
 */
data class ManualBlockingSession(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val endsAtEpochMs: Long = 0L,
    val remainingOnPauseMs: Long = 0L
) {
    fun isEnforcing(nowEpochMs: Long): Boolean {
        if (!isActive || isPaused) return false
        return endsAtEpochMs <= 0L || nowEpochMs < endsAtEpochMs
    }

    fun remainingSeconds(nowEpochMs: Long): Int {
        if (!isActive) return 0
        val ms = if (isPaused) {
            remainingOnPauseMs
        } else if (endsAtEpochMs > 0L) {
            (endsAtEpochMs - nowEpochMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return (ms / 1000L).toInt()
    }
}

data class BlockedAppRule(
    val packageName: String,
    val appLabel: String,
    val category: BlockedAppCategory = BlockedAppCategory.OTHER,
    val blockInNormal: Boolean = false,
    val blockInHigh: Boolean = true,
    val blockInCustom: Boolean = true,
    val isBlocked: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun isBlockedFor(level: FocusGuardLevel): Boolean {
        if (!isBlocked) return false
        return when (level) {
            FocusGuardLevel.NORMAL -> blockInNormal
            FocusGuardLevel.HIGH -> blockInHigh
            FocusGuardLevel.CUSTOM -> blockInCustom
        }
    }
}

enum class StudyResourceType(val label: String) {
    VIDEO("Study Video"),
    PLAYLIST("Playlist"),
    CHANNEL("Study Channel")
}

data class StudyResource(
    val id: Long = 0L,
    val subjectId: Long? = null,
    val title: String,
    val url: String,
    val resourceType: StudyResourceType = StudyResourceType.VIDEO,
    val channelName: String = "",
    val lastOpenedAtMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class UsageAnalyticsCategory(
    val label: String,
    val accentHex: Long,
    val isDistracting: Boolean
) {
    SOCIAL_MEDIA("Social Media", 0xFFEC4899, true),
    SHORT_VIDEO("Short Videos", 0xFFF43F5E, true),
    GAMING("Games", 0xFFF59E0B, true),
    OTT_VIDEO("Streaming & Video", 0xFF8B5CF6, true),
    SHOPPING("Shopping", 0xFFFB7185, true),
    STUDY_PRODUCTIVITY("Study & Work", 0xFF2FA687, false),
    COMMUNICATION("Communication", 0xFF3B82F6, false),
    OTHER("Other", 0xFF94A3B8, false)
}

enum class UsagePeriodMode(val label: String) {
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    SINGLE_DAY("Single Day"),
    CUSTOM_RANGE("Custom")
}

data class AppUsageEntry(
    val packageName: String,
    val appName: String,
    val category: UsageAnalyticsCategory,
    val totalTimeMs: Long,
    val launchCount: Int,
    val sharePercent: Float
) {
    val totalMinutes: Int get() = (totalTimeMs / 60_000L).toInt()
}

data class CategoryUsageEntry(
    val category: UsageAnalyticsCategory,
    val totalTimeMs: Long,
    val sharePercent: Float,
    val appCount: Int
) {
    val totalMinutes: Int get() = (totalTimeMs / 60_000L).toInt()
}

data class DailyScreenTimePoint(
    val dateEpochDay: Long,
    val label: String,
    val totalTimeMs: Long,
    val distractingTimeMs: Long,
    val studyTimeMs: Long
) {
    val totalMinutes: Int get() = (totalTimeMs / 60_000L).toInt()
}

data class PhoneUsageReport(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val periodMode: UsagePeriodMode = UsagePeriodMode.LAST_7_DAYS,
    val startEpochDay: Long = LocalDate.now().minusDays(6).toEpochDay(),
    val endEpochDay: Long = LocalDate.now().toEpochDay(),
    val totalScreenTimeMs: Long = 0L,
    val dailyAverageMs: Long = 0L,
    val previousPeriodTotalMs: Long = 0L,
    val deltaPercentVsPrevious: Int? = null,
    val appUsages: List<AppUsageEntry> = emptyList(),
    val categoryUsages: List<CategoryUsageEntry> = emptyList(),
    val dailyTrend: List<DailyScreenTimePoint> = emptyList(),
    val mostDistractingApps: List<AppUsageEntry> = emptyList(),
    val totalLaunches: Int = 0,
    val insights: List<String> = emptyList(),
    val lastRefreshedMs: Long = 0L,
    val errorMessage: String? = null
) {
    val totalMinutes: Int get() = (totalScreenTimeMs / 60_000L).toInt()
    val dailyAverageMinutes: Int get() = (dailyAverageMs / 60_000L).toInt()
    val distractingMinutes: Int
        get() = categoryUsages.filter { it.category.isDistracting }.sumOf { it.totalMinutes }
    val studyMinutes: Int
        get() = categoryUsages.firstOrNull { it.category == UsageAnalyticsCategory.STUDY_PRODUCTIVITY }?.totalMinutes ?: 0
}

/**
 * Transparent, consent-based Family / Parent Mode configuration.
 * Never performs covert surveillance; always displays a visible indicator to the device user.
 */
data class FamilyControlSettings(
    val isEnabled: Boolean = false,
    val guardianName: String = "",
    val pinHash: String = "",
    val dailyScreenBudgetMinutes: Int = 180,
    val dailyDistractingBudgetMinutes: Int = 45,
    val studyGoalBeforeEntertainmentMinutes: Int = 60,
    val lockFocusGuardRules: Boolean = false,
    val shareWeeklySummaryWithParent: Boolean = false
)

data class SupabaseAuthSession(
    val userId: String = "",
    val email: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochSec: Long = 0L,
    val providers: List<String> = emptyList(),
    val displayNameHint: String = ""
) {
    val isSignedIn: Boolean get() = userId.isNotBlank() && accessToken.isNotBlank()
    val hasEmailProvider: Boolean get() = providers.any { it.equals("email", ignoreCase = true) }
    val providerLabels: List<String>
        get() = providers.mapNotNull { raw ->
            when (raw.lowercase()) {
                "email" -> "Email"
                else -> raw.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }
            }
        }.distinct()

    fun isExpired(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean =
        expiresAtEpochSec > 0L && nowEpochSec >= (expiresAtEpochSec - 60L)
}

enum class CloudSyncStatus(val label: String) {
    OFFLINE_GUEST("Local Only"),
    UNCONFIGURED("Cloud Setup Needed"),
    IDLE("Synced"),
    SYNCING("Syncing…"),
    PENDING("Pending Sync"),
    ERROR("Sync Retry Needed")
}

data class CloudSyncState(
    val isConfigured: Boolean = false,
    val status: CloudSyncStatus = CloudSyncStatus.OFFLINE_GUEST,
    val session: SupabaseAuthSession = SupabaseAuthSession(),
    val pendingOutboxCount: Int = 0,
    val lastSyncedAtMs: Long = 0L,
    val lastError: String? = null,
    val isAuthBusy: Boolean = false,
    val authNotice: String? = null
)


