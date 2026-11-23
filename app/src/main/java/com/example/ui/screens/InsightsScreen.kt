package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.domain.IntentRealitySummary
import com.example.domain.PurposeBreakdownItem
import com.example.model.AppUsageEntry
import com.example.model.InsightsRangeFilter
import com.example.model.InterventionEventRecord
import com.example.model.PurposeSessionRecord
import com.example.model.UsagePeriodMode
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.ui.theme.LocalReducedMotion
import com.example.viewmodel.BrainBufferUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Intent vs. Reality Insights Screen — Tab 3 of BRAIN BUFFER.
 *
 * Strictly separates:
 * - Tab 0 ("Intent vs Reality"): Planned vs Approved Focus, Potential Distractions,
 *   Breaks, Temporary App Access, Purpose Breakdown, and Session History.
 * - Tab 1 ("Device Screen Time"): General Android UsageStatsManager foreground usage.
 *
 * Zero fabricated numbers, zero demo data, and explicit alignment methodology disclaimer.
 */
@Composable
fun InsightsPrimaryScreen(
    uiState: BrainBufferUiState,
    onRefreshUsage: () -> Unit = {},
    onSelectPeriodMode: (UsagePeriodMode) -> Unit = {},
    onSelectCustomRange: (startEpochDay: Long, endEpochDay: Long, mode: UsagePeriodMode) -> Unit = { _, _, _ -> },
    onToggleBlockApp: (AppUsageEntry, Boolean) -> Unit = { _, _ -> },
    onOpenFocusGuardSettings: () -> Unit = {}
) {
    val adaptive = LocalAdaptiveMetrics.current
    var activeSection by remember { mutableIntStateOf(0) } // 0 = Intent vs Reality, 1 = Device Screen Time
    var selectedRange by remember { mutableStateOf(InsightsRangeFilter.TODAY) }

    val summary = remember(
        uiState.purposeSessions,
        uiState.sessions,
        uiState.interventionEvents,
        selectedRange,
        uiState.todayEpochDay
    ) {
        IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = uiState.purposeSessions,
            legacyFocusSessions = uiState.sessions,
            interventionEvents = uiState.interventionEvents,
            range = selectedRange,
            todayEpochDay = uiState.todayEpochDay
        )
    }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .testTag("insights_screen")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "INSIGHTS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (activeSection == 0) "Intent vs. Reality" else "Device Screen Time",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LiquidGlassSegmentedControl(
                        items = listOf(0, 1),
                        selectedItem = activeSection,
                        onSelect = { activeSection = it },
                        labelProvider = { idx -> if (idx == 0) "Intent vs Reality" else "Device Screen Time" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("insights_section_switcher")
                    )
                }

                if (activeSection == 0) {
                    IntentVsRealityContent(
                        summary = summary,
                        selectedRange = selectedRange,
                        onSelectRange = { selectedRange = it }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Device Screen Time is tracked separately from Focus Timer sessions and never inflates your focus alignment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        UsageScreen(
                            uiState = uiState,
                            bottomNavPadding = 112.dp,
                            onRefreshUsage = onRefreshUsage,
                            onSelectPeriodMode = onSelectPeriodMode,
                            onSelectCustomRange = onSelectCustomRange,
                            onToggleBlockApp = onToggleBlockApp,
                            onOpenFocusGuardSettings = onOpenFocusGuardSettings,
                            showHeader = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntentVsRealityContent(
    summary: IntentRealitySummary,
    selectedRange: InsightsRangeFilter,
    onSelectRange: (InsightsRangeFilter) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("intent_reality_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Range Switcher: Today | 7 Days | 30 Days
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightsRangeFilter.entries.forEach { range ->
                    LiquidGlassChip(
                        label = range.label,
                        selected = selectedRange == range,
                        onClick = { onSelectRange(range) },
                        modifier = Modifier.testTag("insights_range_${range.name.lowercase()}")
                    )
                }
            }
        }

        // Hero Alignment & Intent vs Reality Card
        item {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("insights_alignment_hero_card"),
                tone = LiquidGlassTone.ACCENT,
                cornerRadius = 24.dp,
                borderAccent = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FOCUS ALIGNMENT (${selectedRange.label.uppercase()})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = summary.alignmentPercent?.let { "$it%" } ?: "—",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("insights_alignment_percent_value")
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "${summary.completedSessionCount} completed",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${summary.endedEarlySessionCount} ended early",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!summary.hasAnyRecordedData) {
                        Text(
                            text = "No recorded focus sessions in this range yet. Start a purpose-aware focus session to see honest Intent vs. Reality metrics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("insights_empty_state_text")
                        )
                    } else {
                        val reducedMotion = LocalReducedMotion.current
                        val targetAlignFraction = (summary.alignmentPercent ?: 0) / 100f
                        val animatedAlignFraction by animateFloatAsState(
                            targetValue = targetAlignFraction,
                            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 420),
                            label = "insights_alignment_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedAlignFraction },
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )

                        // Proportional time-allocation bar (strictly from real recorded minutes; zero fabrication)
                        val totalAllocatedMin = summary.approvedFocusMinutes +
                            summary.potentialDistractionMinutes +
                            summary.breakMinutes +
                            summary.temporaryAccessMinutes
                        if (totalAllocatedMin > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Tracked time split",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .testTag("insights_allocation_bar"),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (summary.approvedFocusMinutes > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(summary.approvedFocusMinutes.toFloat())
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )
                                    }
                                    if (summary.breakMinutes > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(summary.breakMinutes.toFloat())
                                                .fillMaxSize()
                                                .background(Color(0xFF06B6D4))
                                        )
                                    }
                                    if (summary.temporaryAccessMinutes > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(summary.temporaryAccessMinutes.toFloat())
                                                .fillMaxSize()
                                                .background(Color(0xFFF59E0B))
                                        )
                                    }
                                    if (summary.potentialDistractionMinutes > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(summary.potentialDistractionMinutes.toFloat())
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.error)
                                        )
                                    }
                                }
                            }
                        }

                        // Strictly separated 5-line accounting: Intended, Approved Focus, Potential Distractions, Breaks, Temp Access
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountingRow(
                                icon = Icons.Rounded.Timer,
                                label = "Intended (Planned)",
                                value = "${summary.intendedPlannedMinutes} min",
                                accent = MaterialTheme.colorScheme.primary
                            )
                            AccountingRow(
                                icon = Icons.Rounded.Shield,
                                label = "Approved Focus",
                                value = "${summary.approvedFocusMinutes} min",
                                accent = MaterialTheme.colorScheme.secondary
                            )
                            AccountingRow(
                                icon = Icons.Rounded.WarningAmber,
                                label = "Potential Distractions",
                                value = "${summary.potentialDistractionMinutes} min (${summary.totalInterventionCount})",
                                accent = MaterialTheme.colorScheme.error
                            )
                            AccountingRow(
                                icon = Icons.Rounded.Coffee,
                                label = "Breaks (separate)",
                                value = "${summary.breakMinutes} min",
                                accent = Color(0xFF06B6D4)
                            )
                            AccountingRow(
                                icon = Icons.Rounded.SwapHoriz,
                                label = "Temporary Access (separate)",
                                value = "${summary.temporaryAccessMinutes} min",
                                accent = Color(0xFFF59E0B)
                            )
                        }
                    }

                    // Explicit Methodology & Honesty Disclaimer
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = IntentRealitySummary.METHODOLOGY_NOTE,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("insights_methodology_note")
                            )
                        }
                    }
                }
            }
        }

        // Purpose Breakdown
        if (summary.purposeBreakdown.isNotEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Purpose breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        summary.purposeBreakdown.forEach { item ->
                            PurposeBreakdownRow(item = item)
                        }
                    }
                }
            }
        }

        // Temporary Access Log
        if (summary.filteredTempAccessEvents.isNotEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Temporary access log",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        summary.filteredTempAccessEvents.take(10).forEach { event ->
                            TempAccessLogRow(event = event)
                        }
                    }
                }
            }
        }

        // Session History
        if (summary.filteredSessions.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Session history",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            items(summary.filteredSessions.take(25), key = { "${it.sessionKey}_${it.id}" }) { session ->
                SessionHistoryCard(session = session)
            }
        }
    }
}

@Composable
private fun AccountingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = accent
        )
    }
}

@Composable
private fun PurposeBreakdownRow(item: PurposeBreakdownItem) {
    val accent = Color(item.category.accentHex)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Text(
                        text = "${item.category.label} · ${item.sessionCount} session${if (item.sessionCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = item.alignmentPercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
            }
            Text(
                text = "Planned ${item.plannedMinutes}m · Focus ${item.approvedFocusMinutes}m · Distraction ${item.distractionMinutes}m · Breaks ${item.breakMinutes}m · Temp ${item.tempAccessMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TempAccessLogRow(event: InterventionEventRecord) {
    val timeFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconBadge(
            packageName = event.packageName,
            appLabel = event.appLabel.ifBlank { "App" },
            size = 32.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.appLabel.ifBlank { event.packageName.ifBlank { "Temporary access" } },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val extSuffix = if (event.extensionSeconds > 0) " (+${event.extensionSeconds / 60}m ext)" else ""
            Text(
                text = "${event.declaredPurpose} · ${event.actionTaken}$extSuffix · ${timeFormat.format(Date(event.timestampMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val mins = ((event.actualSeconds.takeIf { it > 0 } ?: event.plannedSeconds) + 30) / 60
        if (mins > 0) {
            Text(
                text = "${mins}m",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SessionHistoryCard(session: PurposeSessionRecord) {
    val timeFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    val accent = Color(session.purposeCategory.accentHex)
    val plannedMin = ((session.plannedSeconds + 30) / 60).coerceAtLeast(1)
    val actualMin = (session.actualFocusedSeconds + 30) / 60
    val isEarly = session.status == PurposeSessionRecord.STATUS_ENDED_EARLY

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = session.purposeCategory.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = session.sessionName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (isEarly) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = if (isEarly) "Ended Early" else "Completed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isEarly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Actual ${actualMin}m of ${plannedMin}m planned · ${timeFormat.format(Date(session.endedAtMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val extras = buildList {
                if (session.breakSeconds > 0) add("Breaks ${(session.breakSeconds + 30) / 60}m")
                if (session.temporaryAccessSeconds > 0) add("Temp access ${(session.temporaryAccessSeconds + 30) / 60}m")
                if (session.interventionCount > 0) add("${session.interventionCount} distraction check-ins")
            }
            if (extras.isNotEmpty()) {
                Text(
                    text = extras.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
