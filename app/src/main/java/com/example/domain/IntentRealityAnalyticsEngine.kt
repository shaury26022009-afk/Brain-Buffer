package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.InsightsRangeFilter
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PurposeSessionRecord
import com.example.model.SessionPurposeCategory
import kotlin.math.roundToInt

@Immutable
data class PurposeBreakdownItem(
    val category: SessionPurposeCategory,
    val plannedMinutes: Int,
    val approvedFocusMinutes: Int,
    val distractionMinutes: Int,
    val breakMinutes: Int,
    val tempAccessMinutes: Int,
    val sessionCount: Int,
    val alignmentPercent: Int?
)

@Immutable
data class IntentRealitySummary(
    val range: InsightsRangeFilter,
    val intendedPlannedMinutes: Int,
    val approvedFocusMinutes: Int,
    val potentialDistractionMinutes: Int,
    val breakMinutes: Int,
    val temporaryAccessMinutes: Int,
    val completedSessionCount: Int,
    val endedEarlySessionCount: Int,
    val totalInterventionCount: Int,
    /**
     * Null when there is no recorded session data in the range (never fabricated).
     * Formula: round(approvedFocusSeconds * 100.0 / (approvedFocusSeconds + potentialDistractionSeconds)).
     */
    val alignmentPercent: Int?,
    val purposeBreakdown: List<PurposeBreakdownItem>,
    val filteredSessions: List<PurposeSessionRecord>,
    val filteredTempAccessEvents: List<InterventionEventRecord>,
    val filteredDistractionEvents: List<InterventionEventRecord>
) {
    val hasAnyRecordedData: Boolean
        get() = filteredSessions.isNotEmpty() ||
            filteredTempAccessEvents.isNotEmpty() ||
            filteredDistractionEvents.isNotEmpty()

    companion object {
        const val METHODOLOGY_NOTE =
            "Alignment = Approved Focus ÷ (Approved Focus + Potential Distractions). " +
                "Breaks, temporary app access, and general device screen time are tracked separately. " +
                "App-level matching estimates alignment only — BRAIN BUFFER never inspects in-app content or measures learning."
    }
}

@Immutable
data class DailyFocusGoalProgress(
    val focusedMinutes: Int,
    val targetMinutes: Int,
    val remainingMinutes: Int,
    val bonusMinutes: Int,
    val progressFraction: Float,
    val percentComplete: Int,
    val isGoalReached: Boolean
) {
    val formattedFocused: String get() = formatGoalDuration(focusedMinutes)
    val formattedTarget: String get() = formatGoalDuration(targetMinutes)
    val formattedRemaining: String get() = formatGoalDuration(remainingMinutes)
    val formattedBonus: String get() = formatGoalDuration(bonusMinutes)

    companion object {
        const val MIN_GOAL_MINUTES = 15
        const val MAX_GOAL_MINUTES = 480
        const val STEP_MINUTES = 15

        fun formatGoalDuration(totalMinutes: Int): String {
            val safe = totalMinutes.coerceAtLeast(0)
            val hours = safe / 60
            val mins = safe % 60
            return when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }
        }
    }
}

/**
 * Pure, deterministic Intent-vs-Reality analytics calculator.
 *
 * Guarantees:
 * - Strictly separates Focus Timer Duration, Break Duration, Temporary App Access,
 *   Potential Distraction Time, and General Device Screen Time.
 * - Uses ONE consistent formula for Focus Alignment percentage.
 * - Produces zero fake numbers, demo defaults, or invented comparisons when empty.
 */
object IntentRealityAnalyticsEngine {

    /**
     * Computes today's Daily Focus Goal progress strictly from real approved focus minutes
     * (never including breaks or temporary access).
     */
    fun computeDailyGoalProgress(
        focusedMinutesToday: Int,
        dailyTargetMinutes: Int
    ): DailyFocusGoalProgress {
        val safeFocused = focusedMinutesToday.coerceAtLeast(0)
        val safeTarget = dailyTargetMinutes.coerceIn(
            DailyFocusGoalProgress.MIN_GOAL_MINUTES,
            DailyFocusGoalProgress.MAX_GOAL_MINUTES
        )
        val remaining = (safeTarget - safeFocused).coerceAtLeast(0)
        val bonus = (safeFocused - safeTarget).coerceAtLeast(0)
        val fraction = (safeFocused.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
        val percent = ((safeFocused.toDouble() / safeTarget.toDouble()) * 100.0)
            .roundToInt()
            .coerceAtLeast(0)
        return DailyFocusGoalProgress(
            focusedMinutes = safeFocused,
            targetMinutes = safeTarget,
            remainingMinutes = remaining,
            bonusMinutes = bonus,
            progressFraction = fraction,
            percentComplete = percent,
            isGoalReached = safeFocused >= safeTarget
        )
    }

    /**
     * Consistent Focus Alignment formula:
     *   alignment = round(approvedFocusSeconds / (approvedFocusSeconds + distractionSeconds) * 100)
     *
     * Returns null when both numerator and denominator are 0 (no tracked focus window).
     * Never includes break time or general device screen time in the ratio.
     */
    fun calculateAlignmentPercent(
        approvedFocusSeconds: Int,
        potentialDistractionSeconds: Int
    ): Int? {
        val safeFocus = approvedFocusSeconds.coerceAtLeast(0)
        val safeDistraction = potentialDistractionSeconds.coerceAtLeast(0)
        val denominator = safeFocus + safeDistraction
        if (denominator <= 0) return null
        return ((safeFocus.toDouble() / denominator.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun computeSummary(
        purposeSessions: List<PurposeSessionRecord>,
        legacyFocusSessions: List<FocusSessionRecord> = emptyList(),
        interventionEvents: List<InterventionEventRecord> = emptyList(),
        range: InsightsRangeFilter,
        todayEpochDay: Long
    ): IntentRealitySummary {
        val startEpochDay = todayEpochDay - (range.days - 1).coerceAtLeast(0)

        // Include legacy FocusSessionRecords only if their sessionKey isn't already represented in purposeSessions
        // so existing user history is preserved seamlessly without double-counting.
        val existingKeys = purposeSessions.map { it.sessionKey }.filter { it.isNotBlank() }.toSet()
        val existingEndTimestamps = purposeSessions.map { it.endedAtMs }.toSet()
        val migratedLegacy = legacyFocusSessions
            .filter { it.mode == FocusMode.FOCUS }
            .filter { legacy ->
                "legacy_${legacy.id}" !in existingKeys &&
                    existingEndTimestamps.none { kotlin.math.abs(it - legacy.completedAtMs) < 5_000L }
            }
            .map { legacy ->
                val isEndedEarly = legacy.durationSeconds < legacy.plannedSeconds
                PurposeSessionRecord(
                    id = -legacy.id,
                    sessionKey = "legacy_${legacy.id}",
                    purposeCategory = SessionPurposeCategory.STUDY,
                    sessionName = legacy.sessionGoal.ifBlank { legacy.subjectName.ifBlank { "Focus Session" } },
                    plannedSeconds = legacy.plannedSeconds,
                    actualFocusedSeconds = legacy.durationSeconds,
                    breakSeconds = 0,
                    temporaryAccessSeconds = 0,
                    distractionSeconds = 0,
                    interventionCount = 0,
                    status = if (isEndedEarly) PurposeSessionRecord.STATUS_ENDED_EARLY else PurposeSessionRecord.STATUS_COMPLETED,
                    startedAtMs = (legacy.completedAtMs - legacy.durationSeconds * 1000L).coerceAtLeast(0L),
                    endedAtMs = legacy.completedAtMs,
                    dateEpochDay = legacy.dateEpochDay
                )
            }

        val combinedSessions = (purposeSessions + migratedLegacy)
            .filter { it.dateEpochDay in startEpochDay..todayEpochDay }
            .filter { it.status != PurposeSessionRecord.STATUS_CANCELLED }
            .sortedByDescending { it.endedAtMs }

        val filteredEvents = interventionEvents
            .filter { it.dateEpochDay in startEpochDay..todayEpochDay }
            .sortedByDescending { it.timestampMs }

        val tempAccessEvents = filteredEvents.filter {
            it.eventType == InterventionEventType.APP_SWITCH ||
                it.eventType == InterventionEventType.TEMP_ACCESS_EXTENDED ||
                it.eventType == InterventionEventType.TEMP_ACCESS_EXPIRED
        }
        val distractionEvents = filteredEvents.filter {
            it.eventType == InterventionEventType.POTENTIAL_DISTRACTION
        }

        val totalPlannedSec = combinedSessions.sumOf { it.plannedSeconds.coerceAtLeast(0) }
        val totalApprovedFocusSec = combinedSessions.sumOf { it.actualFocusedSeconds.coerceAtLeast(0) }
        val totalBreakSec = combinedSessions.sumOf { it.breakSeconds.coerceAtLeast(0) }
        val totalTempAccessSec = combinedSessions.sumOf { it.temporaryAccessSeconds.coerceAtLeast(0) }
        val totalDistractionSec = combinedSessions.sumOf { it.distractionSeconds.coerceAtLeast(0) }
        val totalInterventions = maxOf(
            combinedSessions.sumOf { it.interventionCount.coerceAtLeast(0) },
            distractionEvents.size
        )

        val breakdown = SessionPurposeCategory.entries.mapNotNull { category ->
            val group = combinedSessions.filter { it.purposeCategory == category }
            if (group.isEmpty()) return@mapNotNull null
            val gPlanned = group.sumOf { it.plannedSeconds.coerceAtLeast(0) }
            val gFocus = group.sumOf { it.actualFocusedSeconds.coerceAtLeast(0) }
            val gDistraction = group.sumOf { it.distractionSeconds.coerceAtLeast(0) }
            val gBreak = group.sumOf { it.breakSeconds.coerceAtLeast(0) }
            val gTemp = group.sumOf { it.temporaryAccessSeconds.coerceAtLeast(0) }
            PurposeBreakdownItem(
                category = category,
                plannedMinutes = toRoundedMinutes(gPlanned),
                approvedFocusMinutes = toRoundedMinutes(gFocus),
                distractionMinutes = toRoundedMinutes(gDistraction),
                breakMinutes = toRoundedMinutes(gBreak),
                tempAccessMinutes = toRoundedMinutes(gTemp),
                sessionCount = group.size,
                alignmentPercent = calculateAlignmentPercent(gFocus, gDistraction)
            )
        }.sortedByDescending { it.approvedFocusMinutes }

        return IntentRealitySummary(
            range = range,
            intendedPlannedMinutes = toRoundedMinutes(totalPlannedSec),
            approvedFocusMinutes = toRoundedMinutes(totalApprovedFocusSec),
            potentialDistractionMinutes = toRoundedMinutes(totalDistractionSec),
            breakMinutes = toRoundedMinutes(totalBreakSec),
            temporaryAccessMinutes = toRoundedMinutes(totalTempAccessSec),
            completedSessionCount = combinedSessions.count { it.status == PurposeSessionRecord.STATUS_COMPLETED },
            endedEarlySessionCount = combinedSessions.count { it.status == PurposeSessionRecord.STATUS_ENDED_EARLY },
            totalInterventionCount = totalInterventions,
            alignmentPercent = calculateAlignmentPercent(totalApprovedFocusSec, totalDistractionSec),
            purposeBreakdown = breakdown,
            filteredSessions = combinedSessions,
            filteredTempAccessEvents = tempAccessEvents,
            filteredDistractionEvents = distractionEvents
        )
    }

    private fun toRoundedMinutes(seconds: Int): Int {
        if (seconds <= 0) return 0
        return ((seconds + 30) / 60).coerceAtLeast(1)
    }
}
