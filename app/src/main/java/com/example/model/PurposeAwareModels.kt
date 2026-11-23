package com.example.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Core user-declared phone-use purposes for the BRAIN BUFFER Purpose-Aware Focus System.
 */
enum class SessionPurposeCategory(
    val label: String,
    val shortLabel: String,
    val description: String,
    val defaultSessionPlaceholder: String,
    val suggestedSessionNames: List<String>,
    val accentHex: Long,
    val defaultActivityType: StudyActivityType
) {
    STUDY(
        label = "Study",
        shortLabel = "Study",
        description = "Lectures, reading, assignments, exam prep",
        defaultSessionPlaceholder = "Study Physics",
        suggestedSessionNames = listOf(
            "Study Physics",
            "Complete assignment",
            "Exam revision",
            "Read textbook chapter"
        ),
        accentHex = 0xFF7B5CFA,
        defaultActivityType = StudyActivityType.STUDYING
    ),
    WORK(
        label = "Work",
        shortLabel = "Work",
        description = "Deep work, office tasks, presentations, coding",
        defaultSessionPlaceholder = "Prepare presentation",
        suggestedSessionNames = listOf(
            "Office work",
            "Prepare presentation",
            "Write project report",
            "Code review"
        ),
        accentHex = 0xFF3B82F6,
        defaultActivityType = StudyActivityType.SOLVING_QUESTIONS
    ),
    COMMUNICATION(
        label = "Communication",
        shortLabel = "Comms",
        description = "Intentional replies, emails, calls, team chats",
        defaultSessionPlaceholder = "Reply to messages & email",
        suggestedSessionNames = listOf(
            "Check emails",
            "Reply to team messages",
            "Family call"
        ),
        accentHex = 0xFF10B981,
        defaultActivityType = StudyActivityType.OTHER
    ),
    ENTERTAINMENT(
        label = "Entertainment",
        shortLabel = "Leisure",
        description = "Intentional time-boxed media, videos, or games",
        defaultSessionPlaceholder = "Planned break video",
        suggestedSessionNames = listOf(
            "Watch one episode",
            "Time-boxed gaming",
            "Music & unwind"
        ),
        accentHex = 0xFFF59E0B,
        defaultActivityType = StudyActivityType.ENTERTAINMENT
    ),
    PERSONAL_TASK(
        label = "Personal / Necessary",
        shortLabel = "Personal",
        description = "Banking, errands, reading, planning, admin",
        defaultSessionPlaceholder = "Read a book",
        suggestedSessionNames = listOf(
            "Read a book",
            "Pay bills & banking",
            "Plan the week"
        ),
        accentHex = 0xFF06B6D4,
        defaultActivityType = StudyActivityType.READING
    ),
    CUSTOM(
        label = "Custom purpose",
        shortLabel = "Custom",
        description = "Define any custom intention and its allowed apps",
        defaultSessionPlaceholder = "Focused session",
        suggestedSessionNames = listOf(
            "Creative writing",
            "Language practice",
            "Research"
        ),
        accentHex = 0xFFEC4899,
        defaultActivityType = StudyActivityType.OTHER
    );

    companion object {
        fun fromKey(raw: String?): SessionPurposeCategory {
            if (raw.isNullOrBlank()) return STUDY
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: STUDY
        }
    }
}

/**
 * Optional break schedule selected during Focus session setup.
 */
enum class BreakPlanMode(val label: String, val subtitle: String) {
    NONE("No break", "Continuous focused session"),
    SINGLE("One break", "Pause once mid-session"),
    REPEATING("Repeating", "Scheduled breaks at regular intervals");

    companion object {
        fun fromKey(raw: String?): BreakPlanMode {
            if (raw.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: NONE
        }
    }
}

@Immutable
data class BreakPlanConfig(
    val mode: BreakPlanMode = BreakPlanMode.NONE,
    val breakDurationMinutes: Int = 5,
    val intervalMinutes: Int = 25
)

@Immutable
data class SelectedSessionApp(
    val packageName: String,
    val appLabel: String
)

/**
 * Single active temporary app access session created via App Switch or Distraction Intervention.
 *
 * Business-logic invariants enforced by [com.example.domain.TemporaryAccessPolicy]:
 * - Only ONE temporary access can be active at a time (prevents multi-allowance bypass).
 * - Extension is strictly capped at [MAX_EXTENSION_MINUTES] (5 minutes) AND allowed at most ONCE.
 * - Time spent in temporary access is NEVER counted as focused time.
 */
@Immutable
data class TemporaryAppAccessSession(
    val id: String,
    val parentSessionId: String,
    val packageName: String,
    val appLabel: String,
    val purposeLabel: String,
    val originalAllowanceMinutes: Int,
    val extensionMinutesUsed: Int = 0,
    val hasUsedExtension: Boolean = false,
    val startedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val endedAtEpochMs: Long? = null
) {
    val totalAllowedSeconds: Int
        get() = ((originalAllowanceMinutes + extensionMinutesUsed) * 60).coerceAtLeast(60)

    fun remainingSeconds(nowEpochMs: Long): Int {
        if (endedAtEpochMs != null) return 0
        return ((expiresAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)
    }

    fun isExpired(nowEpochMs: Long): Boolean {
        return endedAtEpochMs != null || nowEpochMs >= expiresAtEpochMs
    }

    fun actualDurationSeconds(nowEpochMs: Long): Int {
        val end = endedAtEpochMs ?: nowEpochMs.coerceAtMost(expiresAtEpochMs)
        return ((end - startedAtEpochMs) / 1000L).toInt().coerceAtLeast(0)
    }

    val canRequestExtension: Boolean
        get() = !hasUsedExtension && extensionMinutesUsed < MAX_EXTENSION_MINUTES && endedAtEpochMs == null

    companion object {
        const val MAX_EXTENSION_MINUTES = 5
    }
}

/**
 * Durable Room-backed record of a Purpose-Aware Focus Session (Intent vs Reality).
 * Keeps Focus, Break, Temporary Access, and Distraction durations strictly separate.
 */
@Immutable
data class PurposeSessionRecord(
    val id: Long = 0L,
    val sessionKey: String,
    val purposeCategory: SessionPurposeCategory = SessionPurposeCategory.STUDY,
    val sessionName: String,
    val allowedPackages: List<String> = emptyList(),
    val allowedAppLabels: List<String> = emptyList(),
    val plannedSeconds: Int,
    val actualFocusedSeconds: Int,
    val breakSeconds: Int = 0,
    val temporaryAccessSeconds: Int = 0,
    val distractionSeconds: Int = 0,
    val interventionCount: Int = 0,
    val status: String = STATUS_COMPLETED, // COMPLETED | ENDED_EARLY | CANCELLED
    val breakPlanMode: BreakPlanMode = BreakPlanMode.NONE,
    val linkedTaskId: Long? = null,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val dateEpochDay: Long = LocalDate.now().toEpochDay()
) {
    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_ENDED_EARLY = "ENDED_EARLY"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}

/**
 * Lightweight, privacy-preserving intervention & temporary-access event record.
 * Never stores messages, passwords, URLs, or screen content — only package-level events.
 */
enum class InterventionEventType {
    POTENTIAL_DISTRACTION,
    APP_SWITCH,
    TEMP_ACCESS_EXPIRED,
    TEMP_ACCESS_EXTENDED,
    PURPOSE_SWITCHED,
    BREAK_TAKEN;

    companion object {
        fun fromKey(raw: String?): InterventionEventType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: POTENTIAL_DISTRACTION
        }
    }
}

@Immutable
data class InterventionEventRecord(
    val id: Long = 0L,
    val sessionKey: String,
    val eventType: InterventionEventType,
    val packageName: String = "",
    val appLabel: String = "",
    val declaredPurpose: String = "",
    val actionTaken: String = "",
    val plannedSeconds: Int = 0,
    val extensionSeconds: Int = 0,
    val actualSeconds: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val dateEpochDay: Long = LocalDate.now().toEpochDay()
)

enum class InsightsRangeFilter(val label: String, val days: Int) {
    TODAY("Today", 1),
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30)
}
