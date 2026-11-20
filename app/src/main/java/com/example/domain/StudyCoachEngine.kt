package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TaskPriority
import com.example.viewmodel.BrainBufferUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Calm, actionable next-step study recommendation derived strictly from the student's
 * real Room database records (exams, due tasks, subject weekly targets, and daily progress).
 */
@Immutable
data class StudyRecommendation(
    val badge: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val recommendedMinutes: Int,
    val subjectId: Long? = null,
    val accentHex: Long = 0xFF7B5CFA
)

/**
 * Single day cell in the 28-day Study Momentum Heatmap.
 */
@Immutable
data class HeatmapDayCell(
    val epochDay: Long,
    val minutes: Int,
    val intensityLevel: Int, // 0 = rest, 1 = light, 2 = steady, 3 = strong, 4 = peak
    val isToday: Boolean,
    val shortDayLabel: String
)

/**
 * Real-data rhythm & consistency insights for the Insights dashboard.
 */
@Immutable
data class FocusRhythmInsights(
    val bestWindowLabel: String,
    val bestWindowDetail: String,
    val averageSessionMinutes: Int,
    val focusQualityPercent: Int,
    val activeDaysLast28: Int,
    val bestDayMinutesLast28: Int,
    val heatmapCells: List<HeatmapDayCell>
)

/**
 * Deterministic on-device Study Coach & Consistency Analyzer.
 * Zero mock data — computes personalized guidance from saved subjects, tasks, and focus logs.
 */
object StudyCoachEngine {

    fun computeRecommendation(
        uiState: BrainBufferUiState,
        todayEpochDay: Long = uiState.todayEpochDay
    ): StudyRecommendation {
        val defaultDuration = uiState.preferences.preferredFocusMinutes.coerceIn(15, 60)
        val subjectMap = uiState.subjects.associateBy { it.id }

        // 1. Urgent upcoming exam within 7 days
        val urgentExamSubject = uiState.subjects
            .filter { it.examDateEpochDay != null && it.examDateEpochDay >= todayEpochDay }
            .minByOrNull { it.examDateEpochDay!! }
        if (urgentExamSubject != null) {
            val daysLeft = (urgentExamSubject.examDateEpochDay!! - todayEpochDay).toInt()
            if (daysLeft <= 7) {
                val dayPhrase = when (daysLeft) {
                    0 -> "Exam today"
                    1 -> "Exam tomorrow"
                    else -> "Exam in $daysLeft days"
                }
                return StudyRecommendation(
                    badge = dayPhrase,
                    title = "Focused revision for ${urgentExamSubject.name}",
                    subtitle = "One calm ${defaultDuration}m recall block now beats last-minute cramming.",
                    actionLabel = "Revise ${urgentExamSubject.name}",
                    recommendedMinutes = defaultDuration,
                    subjectId = urgentExamSubject.id,
                    accentHex = urgentExamSubject.colorHex
                )
            }
        }

        // 2. Overdue or High-priority task due today
        val urgentTask = uiState.tasks
            .filter { !it.isCompleted && it.dueDateEpochDay <= todayEpochDay }
            .sortedWith(
                compareBy<StudyTask> { it.dueDateEpochDay }
                    .thenBy { priorityRank(it.priority) }
            )
            .firstOrNull()
        if (urgentTask != null) {
            val taskSubject = urgentTask.subjectId?.let { subjectMap[it] }
            val isOverdue = urgentTask.dueDateEpochDay < todayEpochDay
            val blockMin = urgentTask.estimatedMinutes.coerceIn(15, 50)
            return StudyRecommendation(
                badge = if (isOverdue) "Catch-up priority" else "Due today",
                title = urgentTask.title,
                subtitle = buildString {
                    if (taskSubject != null) append("${taskSubject.name} · ")
                    append("Estimated ${blockMin}m — knock out the first stretch cleanly.")
                },
                actionLabel = "Focus ${blockMin}m",
                recommendedMinutes = blockMin,
                subjectId = urgentTask.subjectId,
                accentHex = taskSubject?.colorHex ?: 0xFF7B5CFA
            )
        }

        // 3. Subject furthest behind its weekly target hours
        val weekStart = todayEpochDay - 6L
        val minutesBySubjectThisWeek = uiState.focusOnlySessions
            .filter { it.dateEpochDay >= weekStart && it.subjectId != null }
            .groupBy { it.subjectId!! }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds } / 60 }

        val laggingSubject = uiState.subjects
            .filter { it.targetHoursPerWeek > 0f }
            .maxByOrNull { subject ->
                val targetMins = (subject.targetHoursPerWeek * 60f).coerceAtLeast(30f)
                val logged = minutesBySubjectThisWeek[subject.id] ?: 0
                (targetMins - logged) / targetMins
            }
        val remainingToday = (uiState.preferences.dailyTargetMinutes - uiState.todayFocusMinutes).coerceAtLeast(0)
        if (laggingSubject != null && remainingToday > 0) {
            val logged = minutesBySubjectThisWeek[laggingSubject.id] ?: 0
            val targetMins = (laggingSubject.targetHoursPerWeek * 60f).roundToInt()
            if (logged < targetMins) {
                val blockMin = remainingToday.coerceIn(15, defaultDuration)
                return StudyRecommendation(
                    badge = "Weekly balance",
                    title = "Give ${laggingSubject.name} a steady boost",
                    subtitle = "${logged}m / ${targetMins}m logged this week · ${remainingToday}m left for today's goal.",
                    actionLabel = "Study ${laggingSubject.name}",
                    recommendedMinutes = blockMin,
                    subjectId = laggingSubject.id,
                    accentHex = laggingSubject.colorHex
                )
            }
        }

        // 4. Daily target progress nudge
        if (remainingToday > 0) {
            val blockMin = remainingToday.coerceIn(15, defaultDuration)
            return StudyRecommendation(
                badge = if (uiState.todayFocusMinutes == 0) "Fresh start" else "Almost there",
                title = if (uiState.todayFocusMinutes == 0) {
                    "Start your first ${blockMin}m focus block"
                } else {
                    "Only ${remainingToday}m left to hit today's goal"
                },
                subtitle = if (uiState.currentStreakDays > 0) {
                    "Keep your ${uiState.currentStreakDays}-day study rhythm alive without pressure."
                } else {
                    "One quiet session plants momentum in your Study Garden."
                },
                actionLabel = "Start ${blockMin}m Focus",
                recommendedMinutes = blockMin,
                subjectId = uiState.subjects.firstOrNull()?.id,
                accentHex = 0xFF7B5CFA
            )
        }

        // 5. Daily goal already completed!
        return StudyRecommendation(
            badge = "Daily goal complete",
            title = "You hit ${uiState.todayFocusMinutes}m of deep focus today",
            subtitle = "Protect your energy — review light notes or add an optional 20m bonus block.",
            actionLabel = "Bonus 20m Session",
            recommendedMinutes = 20,
            subjectId = uiState.subjects.firstOrNull()?.id,
            accentHex = 0xFF34D399
        )
    }

    fun computeRhythmInsights(
        sessions: List<FocusSessionRecord>,
        dailyTargetMinutes: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FocusRhythmInsights {
        val focusSessions = sessions.filter { it.mode == FocusMode.FOCUS }
        val startEpochDay = todayEpochDay - 27L

        val minutesByDay = focusSessions
            .filter { it.dateEpochDay in startEpochDay..todayEpochDay }
            .groupBy { it.dateEpochDay }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds } / 60 }

        val target = dailyTargetMinutes.coerceAtLeast(25)
        val heatmap = (0..27).map { offset ->
            val epochDay = startEpochDay + offset
            val mins = minutesByDay[epochDay] ?: 0
            val level = when {
                mins <= 0 -> 0
                mins < (target * 0.35f) -> 1
                mins < (target * 0.70f) -> 2
                mins < target -> 3
                else -> 4
            }
            val date = LocalDate.ofEpochDay(epochDay)
            HeatmapDayCell(
                epochDay = epochDay,
                minutes = mins,
                intensityLevel = level,
                isToday = epochDay == todayEpochDay,
                shortDayLabel = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            )
        }

        val activeDaysLast28 = heatmap.count { it.minutes > 0 }
        val bestDayMinutesLast28 = heatmap.maxOfOrNull { it.minutes } ?: 0

        val avgSessionMinutes = if (focusSessions.isEmpty()) {
            0
        } else {
            (focusSessions.sumOf { it.durationSeconds } / 60f / focusSessions.size).roundToInt().coerceAtLeast(1)
        }

        val focusQualityPercent = if (focusSessions.isEmpty()) {
            0
        } else {
            val ratios = focusSessions.map { s ->
                val planned = s.plannedSeconds.coerceAtLeast(60).toFloat()
                (s.durationSeconds.toFloat() / planned).coerceIn(0.1f, 1.0f)
            }
            (ratios.average() * 100.0).roundToInt().coerceIn(0, 100)
        }

        // Best focus window from actual completedAtMs hours
        val (bestWindow, bestDetail) = if (focusSessions.isEmpty()) {
            "Building pattern" to "Complete a few sessions to discover your peak hour"
        } else {
            val buckets = IntArray(4) // 0=Morning 5..11, 1=Afternoon 12..16, 2=Evening 17..21, 3=Night 22..4
            focusSessions.forEach { s ->
                val hour = Instant.ofEpochMilli(s.completedAtMs).atZone(zoneId).hour
                val mins = (s.durationSeconds / 60).coerceAtLeast(1)
                val idx = when (hour) {
                    in 5..11 -> 0
                    in 12..16 -> 1
                    in 17..21 -> 2
                    else -> 3
                }
                buckets[idx] += mins
            }
            val bestIdx = buckets.indices.maxByOrNull { buckets[it] } ?: 0
            val bestMins = buckets[bestIdx]
            when (bestIdx) {
                0 -> "Morning Peak" to "${bestMins}m logged between 5 AM – 12 PM"
                1 -> "Afternoon Peak" to "${bestMins}m logged between 12 PM – 5 PM"
                2 -> "Evening Peak" to "${bestMins}m logged between 5 PM – 10 PM"
                else -> "Night Owl" to "${bestMins}m logged after 10 PM"
            }
        }

        return FocusRhythmInsights(
            bestWindowLabel = bestWindow,
            bestWindowDetail = bestDetail,
            averageSessionMinutes = avgSessionMinutes,
            focusQualityPercent = focusQualityPercent,
            activeDaysLast28 = activeDaysLast28,
            bestDayMinutesLast28 = bestDayMinutesLast28,
            heatmapCells = heatmap
        )
    }

    private fun priorityRank(priority: TaskPriority): Int = when (priority) {
        TaskPriority.HIGH -> 0
        TaskPriority.MEDIUM -> 1
        TaskPriority.LOW -> 2
    }
}
