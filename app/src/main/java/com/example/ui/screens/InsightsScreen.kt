package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.FocusRhythmInsights
import com.example.domain.StudyCoachEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.DailyActivityStat
import com.example.model.FocusSessionRecord
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.SubjectBadge
import com.example.ui.components.iconForActivity
import com.example.ui.components.springClickable
import com.example.ui.components.subjectIconVector
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.BrainBufferUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SubjectDistributionSlice(
    val key: String,
    val name: String,
    val color: Color,
    val iconKey: String?,
    val minutes: Int,
    val weeklyTargetMinutes: Int?
)

@Composable
fun InsightsStandaloneScreen(
    uiState: BrainBufferUiState,
    onBack: () -> Unit
) {
    SanctuaryBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = "Study Insights",
                subtitle = "100% from your real saved sessions",
                onBack = onBack
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .testTag("insights_standalone_list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        InsightsContent(uiState = uiState)
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsContent(
    uiState: BrainBufferUiState,
    modifier: Modifier = Modifier
) {
    val extended = BrainBufferThemeTokens.extended
    val focusSessions = uiState.focusOnlySessions
    val weeklyActivity = uiState.weeklyActivity
    var selectedDayIndex by remember(weeklyActivity.size) {
        mutableIntStateOf((weeklyActivity.size - 1).coerceAtLeast(0))
    }

    val distributionSlices = remember(uiState.subjects, focusSessions) {
        buildSubjectDistribution(uiState)
    }
    val rhythmInsights = remember(focusSessions, uiState.preferences.dailyTargetMinutes, uiState.todayEpochDay) {
        StudyCoachEngine.computeRhythmInsights(
            sessions = focusSessions,
            dailyTargetMinutes = uiState.preferences.dailyTargetMinutes,
            todayEpochDay = uiState.todayEpochDay
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Primary 2x2 real stats overview
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatTile(
                    icon = Icons.Rounded.Timer,
                    label = "Total Focus",
                    value = formatInsightDuration(uiState.totalFocusMinutesAllTime),
                    caption = "${uiState.todayFocusMinutes}m today",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_total_focus")
                )
                InsightStatTile(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "Last 7 Days",
                    value = formatInsightDuration(uiState.weeklyFocusMinutes),
                    caption = "${uiState.activeDaysThisWeek}/${uiState.preferences.weeklyStudyDaysTarget} active days",
                    tint = extended.sageAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_weekly_focus")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatTile(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Sessions Done",
                    value = "${focusSessions.size}",
                    caption = "${uiState.todaySessionsCount} completed today",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_sessions_count")
                )
                InsightStatTile(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Tasks Finished",
                    value = "${uiState.completedTasksCount}/${uiState.tasks.size}",
                    caption = if (uiState.tasks.isEmpty()) "No tasks yet" else "${((uiState.completedTasksCount * 100f) / uiState.tasks.size).toInt()}% complete",
                    tint = extended.peachAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_tasks_completed")
                )
            }
        }

        // 1b. Smart Local Insights (computed strictly from real saved sessions & activity logs)
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("insights_smart_local_card"),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ) {
            SectionHeader(
                title = "Smart Local Insights",
                subtitle = "Computed privately on your device"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.smartLocalInsights.forEach { takeaway ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = takeaway,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 1c. Activity Category Separation (Today / 7 Days: Focused Study, Lectures, PDF & Reading, Breaks, Gaming, Social)
        ActivityCategoryBreakdownCard(uiState = uiState)

        // 1c-2. Focus Protection & Planned vs Actual Time
        FocusProtectionAndAdherenceCard(uiState = uiState)

        // 1d. Daily Entertainment Limit (gentle, non-punitive budget tracking)
        DailyEntertainmentLimitCard(
            usedMinutes = uiState.todayEntertainmentMinutes,
            limitMinutes = uiState.dailyEntertainmentLimitMinutes
        )

        // 1e. Daily Activity Timeline (today's chronological activity blocks)
        DailyActivityTimelineCard(todayLogs = uiState.todayActivityLogs)

        // 1f. Weekly Review Summary
        WeeklyReviewSummaryCard(uiState = uiState)

        // 2. Study Consistency & Personal Bests
        ConsistencyAndBestsCard(uiState = uiState)

        // 2b. 28-Day Study Momentum Heatmap & Peak Focus Window
        StudyMomentumHeatmapCard(insights = rhythmInsights)

        // 3. Weekly Progress Bar Chart
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("insights_weekly_chart_card")
        ) {
            SectionHeader(
                title = "Weekly Focus Rhythm",
                subtitle = "Target: ${uiState.preferences.dailyTargetMinutes} min / day"
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (weeklyActivity.isNotEmpty()) {
                val safeIndex = selectedDayIndex.coerceIn(0, weeklyActivity.lastIndex)
                val selectedDay = weeklyActivity[safeIndex]

                WeeklyBarChart(
                    days = weeklyActivity,
                    dailyTargetMinutes = uiState.preferences.dailyTargetMinutes,
                    selectedIndex = safeIndex,
                    onSelectIndex = { selectedDayIndex = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, extended.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedDay.isToday) "${selectedDay.dayFull} (Today)" else selectedDay.dayFull,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedDay.sessionsCount} focus session${if (selectedDay.sessionsCount == 1) "" else "s"} · ${selectedDay.tasksCompleted} task${if (selectedDay.tasksCompleted == 1) "" else "s"} done",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatInsightDuration(selectedDay.focusMinutes),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (uiState.weeklyFocusMinutes == 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your chart updates automatically as soon as you finish your first focus block.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Subject-Wise Study Distribution
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("insights_subject_distribution_card")
        ) {
            SectionHeader(
                title = "Subject Distribution",
                subtitle = "Time invested across your subjects"
            )
            Spacer(modifier = Modifier.height(14.dp))

            val totalSliceMinutes = distributionSlices.sumOf { it.minutes }
            if (totalSliceMinutes == 0) {
                Text(
                    text = "No focus minutes logged by subject yet. Pick a subject in Focus Space and complete a session to see your breakdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Proportional bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    distributionSlices.filter { it.minutes > 0 }.forEach { slice ->
                        val weight = (slice.minutes.toFloat() / totalSliceMinutes.toFloat()).coerceAtLeast(0.03f)
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(slice.color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    distributionSlices.forEach { slice ->
                        val sharePct = ((slice.minutes * 100f) / totalSliceMinutes).toInt()
                        SubjectDistributionRow(
                            slice = slice,
                            sharePct = sharePct
                        )
                    }
                }
            }
        }

        // 5. Recent Study History
        SectionHeader(
            title = "Study History",
            subtitle = if (focusSessions.isEmpty()) "Waiting for your first session" else "Most recent completed blocks"
        )

        if (focusSessions.isEmpty()) {
            EmptySanctuaryCard(
                icon = Icons.Rounded.BarChart,
                title = "No study history yet",
                message = "Everything here reflects your genuine study sessions. Finish a Focus block and your progress, streaks, and subject totals will appear automatically."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                focusSessions.take(10).forEach { session ->
                    StudyHistoryRow(session = session)
                }
            }
        }

        // 6. Planned vs Actual Activity Log History
        if (uiState.activityLogs.isNotEmpty()) {
            SectionHeader(
                title = "Activity History (Planned vs Actual)",
                subtitle = "All study, lecture, break & entertainment logs"
            )
            Column(
                modifier = Modifier.testTag("insights_activity_history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.activityLogs.take(12).forEach { log ->
                    ActivityLogHistoryRow(log = log)
                }
            }
        }
    }
}

@Composable
private fun ActivityCategoryBreakdownCard(
    uiState: BrainBufferUiState
) {
    var selectedRange by remember { mutableIntStateOf(1) } // 0 = Today, 1 = 7 Days
    val isToday = selectedRange == 0
    val categoryMinutes = if (isToday) uiState.todayCategoryMinutes else uiState.weeklyCategoryMinutes
    val gamingMinutes = if (isToday) uiState.todayGamingMinutes else uiState.weeklyGamingMinutes
    val socialMinutes = if (isToday) uiState.todaySocialMinutes else uiState.weeklySocialMinutes

    val rows = listOf(
        Triple("Actual Focused Study", categoryMinutes[ActivityCategory.STUDY] ?: 0, MaterialTheme.colorScheme.primary),
        Triple("Study Lectures", categoryMinutes[ActivityCategory.LECTURE] ?: 0, Color(0xFF3B82F6)),
        Triple("PDF, Reading & Notes", categoryMinutes[ActivityCategory.READING] ?: 0, Color(0xFF2FA687)),
        Triple("Planned Breaks", categoryMinutes[ActivityCategory.BREAK] ?: 0, Color(0xFF6CE0BE)),
        Triple("Gaming", gamingMinutes, Color(0xFFF59E0B)),
        Triple("Social Media & Chill", socialMinutes, Color(0xFFEC4899))
    )
    val totalMins = rows.sumOf { it.second }

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_activity_breakdown_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isToday) "Activity Breakdown (Today)" else "Activity Breakdown (7 Days)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Focused study is never mixed with breaks, gaming, or social",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Today", "7 Days").forEachIndexed { idx, label ->
                    val chosen = selectedRange == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else BrainBufferThemeTokens.extended.cardBorder
                        ),
                        onClick = { selectedRange = idx },
                        modifier = Modifier.testTag("insights_range_${label.lowercase().replace(" ", "_")}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, mins, tint) ->
                val ratio = if (totalMins > 0) (mins.toFloat() / totalMins.toFloat()).coerceIn(0f, 1f) else 0f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatInsightDuration(mins),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = tint
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = tint,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusProtectionAndAdherenceCard(
    uiState: BrainBufferUiState
) {
    val plannedMins = uiState.weeklyPlannedMinutes
    val actualMins = uiState.weeklyActualMinutes
    val adherencePct = if (plannedMins > 0) ((actualMins * 100f) / plannedMins).toInt().coerceIn(0, 150) else 0
    val warningsToday = uiState.preferences.distractionWarningsToday
    val warningsWeek = uiState.preferences.distractionWarningsWeek

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_focus_protection_card")
    ) {
        SectionHeader(
            title = "Planned vs Actual & Focus Protection",
            subtitle = "Real 7-day adherence · calm, non-judgmental tracking"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Planned vs Actual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (plannedMins > 0) "$adherencePct%" else "—",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${formatInsightDuration(actualMins)} of ${formatInsightDuration(plannedMins)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Distraction Nudges",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$warningsWeek",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (warningsWeek == 0) BrainBufferThemeTokens.extended.sageAccent else Color(0xFFF59E0B)
                    )
                    Text(
                        text = "$warningsToday today · last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyEntertainmentLimitCard(
    usedMinutes: Int,
    limitMinutes: Int
) {
    val safeLimit = limitMinutes.coerceAtLeast(10)
    val progress = (usedMinutes.toFloat() / safeLimit.toFloat()).coerceIn(0f, 1f)
    val reached = usedMinutes >= safeLimit
    val accent = if (reached) MaterialTheme.colorScheme.error else Color(0xFFF59E0B)

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_entertainment_limit_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Entertainment Limit",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (reached) {
                        "Today's gentle limit reached — tracked separately from study"
                    } else {
                        "${(safeLimit - usedMinutes).coerceAtLeast(0)}m remaining in today's intentional budget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${usedMinutes}m / ${safeLimit}m",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun DailyActivityTimelineCard(
    todayLogs: List<ActivityLogRecord>
) {
    val chronological = remember(todayLogs) { todayLogs.sortedBy { it.startedAtMs } }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_daily_timeline_card")
    ) {
        SectionHeader(
            title = "Daily Activity Timeline",
            subtitle = if (chronological.isEmpty()) {
                "Your chronological blocks appear here today"
            } else {
                "${chronological.size} recorded ${if (chronological.size == 1) "block" else "blocks"} today"
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (chronological.isEmpty()) {
            Text(
                text = "Start a Focus Guard session or log an activity to build today's timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chronological.forEach { log ->
                    val accent = Color(log.activityType.accentHex)
                    val window = "${fmt.format(Date(log.startedAtMs))} – ${fmt.format(Date(log.endedAtMs))}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.activityLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$window · ${log.category.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${log.actualMinutes}m",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyReviewSummaryCard(uiState: BrainBufferUiState) {
    val weekCategories = uiState.weeklyCategoryMinutes
    val studyMins = weekCategories[ActivityCategory.STUDY] ?: 0
    val lectureAndReadMins = (weekCategories[ActivityCategory.LECTURE] ?: 0) +
        (weekCategories[ActivityCategory.READING] ?: 0)
    val entertainmentMins = weekCategories[ActivityCategory.ENTERTAINMENT] ?: 0
    val targetDays = uiState.preferences.weeklyStudyDaysTarget.coerceIn(1, 7)

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_weekly_review_card"),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
    ) {
        SectionHeader(
            title = "Weekly Review",
            subtitle = "Honest 7-day summary · offline & private"
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = formatInsightDuration(studyMins),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Focused Study",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatInsightDuration(lectureAndReadMins),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF3B82F6)
                )
                Text(
                    text = "Lectures & PDFs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatInsightDuration(entertainmentMins),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFF59E0B)
                )
                Text(
                    text = "Entertainment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Active ${uiState.activeDaysThisWeek} of $targetDays target study days this week · ${uiState.completedTasksCount} tasks completed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityLogHistoryRow(log: ActivityLogRecord) {
    val timeWindow = remember(log.startedAtMs, log.endedAtMs) {
        val fmt = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
        fmt.format(Date(log.endedAtMs))
    }
    val accent = Color(log.activityType.accentHex)

    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForActivity(log.activityType),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = log.activityLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.category.label} · ${log.subjectName} · ${if (log.wasPlanned) "Planned" else "Unplanned"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeWindow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${log.actualMinutes}m actual",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                Text(
                    text = "${log.plannedMinutes}m planned",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InsightStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    caption: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    SanctuaryCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConsistencyAndBestsCard(uiState: BrainBufferUiState) {
    val extended = BrainBufferThemeTokens.extended
    val targetDays = uiState.preferences.weeklyStudyDaysTarget.coerceIn(1, 7)
    val consistencyProgress = (uiState.activeDaysThisWeek.toFloat() / targetDays.toFloat()).coerceIn(0f, 1f)

    SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consistency & Personal Bests",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiState.activeDaysThisWeek} of $targetDays weekly study days reached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(consistencyProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { consistencyProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonalBestChip(
                icon = Icons.Rounded.LocalFireDepartment,
                label = "Streak",
                value = "${uiState.currentStreakDays}d",
                tint = extended.peachAccent,
                modifier = Modifier.weight(1f)
            )
            PersonalBestChip(
                icon = Icons.Rounded.EmojiEvents,
                label = "Best Streak",
                value = "${uiState.bestStreakDays}d",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            PersonalBestChip(
                icon = Icons.Rounded.Timer,
                label = "Best Day",
                value = formatInsightDuration(uiState.bestDayMinutes),
                tint = extended.sageAccent,
                modifier = Modifier.weight(1f)
            )
            PersonalBestChip(
                icon = Icons.Rounded.AutoAwesome,
                label = "Longest",
                value = "${uiState.longestSessionMinutes}m",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PersonalBestChip(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeeklyBarChart(
    days: List<DailyActivityStat>,
    dailyTargetMinutes: Int,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val maxVal = maxOf(
        days.maxOfOrNull { it.focusMinutes } ?: 0,
        dailyTargetMinutes.coerceAtLeast(30)
    ).toFloat()
    val reducedMotion = BrainBufferThemeTokens.reducedMotion

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val selected = index == selectedIndex
            val rawFraction = if (day.focusMinutes == 0) 0.04f else (day.focusMinutes / maxVal).coerceIn(0.10f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = rawFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = if (reducedMotion) Spring.StiffnessHigh else Spring.StiffnessLow
                ),
                label = "barFraction_$index"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .springClickable { onSelectIndex(index) }
                    .minimumInteractiveComponentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = if (day.focusMinutes > 0) "${day.focusMinutes}m" else "—",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.58f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedFraction)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(
                                when {
                                    day.focusMinutes == 0 -> SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                    selected -> Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            BrainBufferThemeTokens.extended.softPurpleAccent
                                        )
                                    )
                                    day.isToday -> Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        )
                                    )
                                    else -> Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        )
                                    )
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = day.dayShort,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected || day.isToday) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected || day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SubjectDistributionRow(
    slice: SubjectDistributionSlice,
    sharePct: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(slice.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (slice.iconKey != null) {
                Icon(
                    imageVector = subjectIconVector(slice.iconKey),
                    contentDescription = null,
                    tint = slice.color,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slice.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatInsightDuration(slice.minutes)} · $sharePct%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (sharePct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = slice.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StudyHistoryRow(session: FocusSessionRecord) {
    val timeLabel = remember(session.completedAtMs) {
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(session.completedAtMs))
    }

    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubjectBadge(
                    name = session.subjectName,
                    accentColor = Color(session.subjectColorHex),
                    compact = true
                )
                if (session.sessionGoal.isNotBlank()) {
                    Text(
                        text = session.sessionGoal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${session.durationMinutes} min",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun buildSubjectDistribution(uiState: BrainBufferUiState): List<SubjectDistributionSlice> {
    val sessions = uiState.focusOnlySessions
    if (sessions.isEmpty()) return emptyList()

    val subjectById = uiState.subjects.associateBy { it.id }
    val grouped = linkedMapOf<String, SubjectDistributionSlice>()

    sessions.forEach { session ->
        val matchedSubject = session.subjectId?.let { subjectById[it] }
        val key = matchedSubject?.let { "id_${it.id}" } ?: "name_${session.subjectName.lowercase()}"
        val existing = grouped[key]
        val addedMinutes = (session.durationSeconds / 60).coerceAtLeast(1)
        if (existing == null) {
            grouped[key] = SubjectDistributionSlice(
                key = key,
                name = matchedSubject?.name ?: session.subjectName,
                color = matchedSubject?.accentColor ?: Color(session.subjectColorHex),
                iconKey = matchedSubject?.iconKey,
                minutes = addedMinutes,
                weeklyTargetMinutes = matchedSubject?.let { (it.targetHoursPerWeek * 60f).toInt() }
            )
        } else {
            grouped[key] = existing.copy(minutes = existing.minutes + addedMinutes)
        }
    }

    return grouped.values.sortedByDescending { it.minutes }
}

private fun formatInsightDuration(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

@Composable
private fun StudyMomentumHeatmapCard(
    insights: FocusRhythmInsights
) {
    val glass = BrainBufferThemeTokens.glass
    val primary = MaterialTheme.colorScheme.primary
    val rows = remember(insights.heatmapCells) { insights.heatmapCells.chunked(7) }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_momentum_heatmap_card"),
        tone = LiquidGlassTone.STANDARD,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(18.dp)
    ) {
        SectionHeader(
            title = "28-Day Study Momentum",
            subtitle = "${insights.activeDaysLast28}/28 active days · Best day ${formatInsightDuration(insights.bestDayMinutesLast28)}"
        )
        Spacer(modifier = Modifier.height(14.dp))

        // 4 weeks x 7 days Liquid Glass heatmap grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { cell ->
                        val cellColor = when (cell.intensityLevel) {
                            0 -> if (glass.isDark) Color.White.copy(alpha = 0.07f) else primary.copy(alpha = 0.08f)
                            1 -> primary.copy(alpha = if (glass.isDark) 0.28f else 0.24f)
                            2 -> primary.copy(alpha = if (glass.isDark) 0.50f else 0.44f)
                            3 -> primary.copy(alpha = if (glass.isDark) 0.76f else 0.68f)
                            else -> primary
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = cellColor,
                            border = if (cell.isToday) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = cell.shortDayLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (cell.intensityLevel >= 3) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Peak focus window + avg session + focus quality pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassCard(
                modifier = Modifier
                    .weight(1.35f)
                    .testTag("insights_peak_window_badge"),
                tone = LiquidGlassTone.SUBTLE,
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = insights.bestWindowLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insights.bestWindowDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LiquidGlassCard(
                modifier = Modifier.weight(0.95f),
                tone = LiquidGlassTone.SUBTLE,
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (insights.averageSessionMinutes > 0) "${insights.averageSessionMinutes}m avg" else "—",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (insights.focusQualityPercent > 0) {
                        "${insights.focusQualityPercent}% completion"
                    } else {
                        "Completion rate"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

