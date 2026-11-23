package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.AppUsageEntry
import com.example.model.BlockedAppRule
import com.example.model.CategoryUsageEntry
import com.example.model.DailyScreenTimePoint
import com.example.model.PhoneUsageReport
import com.example.model.UsagePeriodMode
import com.example.system.AppUsageAwarenessHelper
import com.example.system.PhoneUsageAnalyticsEngine
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassTone
import com.example.ui.theme.BrandSuccess
import com.example.ui.theme.BrandWarmAmber
import com.example.viewmodel.BrainBufferUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Phone Usage Analytics Screen — replaces Study Garden as destination #4.
 * Powered strictly by Android UsageStatsManager / UsageEvents (zero fabricated metrics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    uiState: BrainBufferUiState,
    bottomNavPadding: Dp,
    onRefreshUsage: () -> Unit,
    onSelectPeriodMode: (UsagePeriodMode) -> Unit,
    onSelectCustomRange: (startEpochDay: Long, endEpochDay: Long, mode: UsagePeriodMode) -> Unit,
    onToggleBlockApp: (AppUsageEntry, Boolean) -> Unit,
    onOpenFocusGuardSettings: () -> Unit,
    showHeader: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val report = uiState.phoneUsageReport
    val blockedPackages = remember(uiState.blockedApps) {
        uiState.blockedApps.filter { it.isBlocked }.map { it.packageName }.toSet()
    }

    var inspectedApp by remember { mutableStateOf<AppUsageEntry?>(null) }
    var inspectedCategory by remember { mutableStateOf<CategoryUsageEntry?>(null) }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }
    var pendingStartDay by remember { mutableStateOf(report.startEpochDay) }
    var pickingSingleDay by remember { mutableStateOf(false) }

    // Automatically refresh usage stats when screen opens or returns from Android Settings
    LaunchedEffect(Unit) {
        onRefreshUsage()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshUsage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.testTag("usage_screen"),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = if (showHeader) 12.dp else 6.dp,
            bottom = bottomNavPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optional standalone header (hidden when embedded in unified Insights tab)
        if (showHeader) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Screen Time",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (report.permissionGranted) {
                                "${PhoneUsageAnalyticsEngine.formatEpochDay(report.startEpochDay)} – ${PhoneUsageAnalyticsEngine.formatEpochDay(report.endEpochDay)}"
                            } else {
                                "On-device Android UsageStats analytics"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenFocusGuardSettings,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .testTag("usage_open_focus_guard")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Open App Blocking",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onRefreshUsage,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .testTag("usage_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh usage analytics",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Permission Gate (Honest — NEVER fabricates data when Usage Access is denied)
        if (!report.permissionGranted) {
            item {
                UsagePermissionExplainerCard(
                    onGrantPermission = {
                        AppUsageAwarenessHelper.openUsageAccessSettings(context)
                    },
                    onRefresh = onRefreshUsage
                )
            }
            return@LazyColumn
        }

        // Distinct Error State when UsageStatsManager throws or cannot be queried
        if (!report.errorMessage.isNullOrBlank()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("usage_error_card"),
                    tone = LiquidGlassTone.SUBTLE,
                    borderAccent = MaterialTheme.colorScheme.error,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Usage stats error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Could not read device screen time",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = report.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRefreshUsage,
                        modifier = Modifier.testTag("usage_error_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // Period selector chips
        item {
            val todayEpoch = uiState.todayEpochDay
            val isTodaySelected = report.periodMode == UsagePeriodMode.SINGLE_DAY &&
                report.startEpochDay == todayEpoch &&
                report.endEpochDay == todayEpoch
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = isTodaySelected,
                            onClick = {
                                onSelectCustomRange(todayEpoch, todayEpoch, UsagePeriodMode.SINGLE_DAY)
                            },
                            label = {
                                Text(
                                    text = "Today",
                                    fontWeight = if (isTodaySelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("usage_period_today")
                        )
                    }
                    items(UsagePeriodMode.entries) { mode ->
                        val selected = when (mode) {
                            UsagePeriodMode.SINGLE_DAY ->
                                report.periodMode == UsagePeriodMode.SINGLE_DAY && !isTodaySelected
                            else -> report.periodMode == mode
                        }
                        val label = when (mode) {
                            UsagePeriodMode.SINGLE_DAY -> "Pick Day"
                            else -> mode.label
                        }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                when (mode) {
                                    UsagePeriodMode.SINGLE_DAY -> pickingSingleDay = true
                                    UsagePeriodMode.CUSTOM_RANGE -> {
                                        pendingStartDay = report.startEpochDay
                                        pickingStartDate = true
                                    }
                                    else -> onSelectPeriodMode(mode)
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            leadingIcon = if (mode == UsagePeriodMode.CUSTOM_RANGE || mode == UsagePeriodMode.SINGLE_DAY) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("usage_period_${mode.name.lowercase()}")
                        )
                    }
                }

                AnimatedVisibility(visible = report.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Reading Android UsageStats…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Hero Summary Card: Total Screen Time, Daily Avg, Comparison Delta, Distracting vs Study
        item {
            UsageOverviewHeroCard(report = report)
        }

        // Daily Trends Interactive Bar Chart
        if (report.dailyTrend.isNotEmpty()) {
            item {
                DailyTrendChartCard(points = report.dailyTrend)
            }
        }

        // Real Computed Insights
        if (report.insights.isNotEmpty()) {
            item {
                UsageSectionHeader(
                    title = "Digital Wellbeing Insights",
                    subtitle = "Computed locally from your real Android usage history"
                )
            }
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        report.insights.forEach { insight ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp)
                                )
                                Text(
                                    text = insight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category Breakdown
        if (report.categoryUsages.isNotEmpty()) {
            item {
                UsageSectionHeader(
                    title = "Category Breakdown",
                    subtitle = "Tap any category to inspect apps"
                )
            }
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        report.categoryUsages.forEach { entry ->
                            CategoryUsageRow(
                                entry = entry,
                                onClick = { inspectedCategory = entry }
                            )
                        }
                    }
                }
            }
        }

        // Most Distracting Apps Quick-Guard
        if (report.mostDistractingApps.isNotEmpty()) {
            item {
                UsageSectionHeader(
                    title = "Most Distracting Apps",
                    subtitle = "Tap the shield to add or remove from Focus Guard"
                )
            }
            items(report.mostDistractingApps, key = { "dist_${it.packageName}" }) { app ->
                val isBlocked = app.packageName in blockedPackages
                AppUsageRowCard(
                    entry = app,
                    isBlockedInFocusGuard = isBlocked,
                    onClick = { inspectedApp = app },
                    onToggleBlock = { onToggleBlockApp(app, !isBlocked) }
                )
            }
        }

        // All Top Used Apps
        if (report.appUsages.isNotEmpty()) {
            item {
                UsageSectionHeader(
                    title = "Most Used Apps",
                    subtitle = "${report.appUsages.size} apps · ${report.totalLaunches} total opens"
                )
            }
            items(report.appUsages.take(20), key = { "top_${it.packageName}" }) { app ->
                val isBlocked = app.packageName in blockedPackages
                AppUsageRowCard(
                    entry = app,
                    isBlockedInFocusGuard = isBlocked,
                    onClick = { inspectedApp = app },
                    onToggleBlock = { onToggleBlockApp(app, !isBlocked) }
                )
            }
        } else {
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No usage recorded for this period",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Android only reports usage stats for days with recorded device activity. Try selecting Last 7 Days or check back later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Single Day DatePicker
    if (pickingSingleDay) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(report.endEpochDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingSingleDay = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = state.selectedDateMillis
                        if (ms != null) {
                            val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                            onSelectCustomRange(day, day, UsagePeriodMode.SINGLE_DAY)
                        }
                        pickingSingleDay = false
                    }
                ) { Text("Inspect Day") }
            },
            dismissButton = {
                TextButton(onClick = { pickingSingleDay = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state, title = { Text("Select a specific day", modifier = Modifier.padding(16.dp)) })
        }
    }

    // Custom Range: Step 1 Start Date -> Step 2 End Date
    if (pickingStartDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(report.startEpochDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = state.selectedDateMillis
                        if (ms != null) {
                            pendingStartDay = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        pickingStartDate = false
                        pickingEndDate = true
                    }
                ) { Text("Next: End Date") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state, title = { Text("Select start date", modifier = Modifier.padding(16.dp)) })
        }
    }

    if (pickingEndDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(report.endEpochDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = state.selectedDateMillis
                        if (ms != null) {
                            val endDay = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                            onSelectCustomRange(pendingStartDay, endDay, UsagePeriodMode.CUSTOM_RANGE)
                        }
                        pickingEndDate = false
                    }
                ) { Text("Apply Range") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state, title = { Text("Select end date", modifier = Modifier.padding(16.dp)) })
        }
    }

    // Drill-down sheet for a specific app
    inspectedApp?.let { app ->
        val isBlocked = app.packageName in blockedPackages
        ModalBottomSheet(
            onDismissRequest = { inspectedApp = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(app.category.accentHex).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = Color(app.category.accentHex)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${app.category.label} · ${app.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricPillTile(
                        label = "Screen Time",
                        value = PhoneUsageAnalyticsEngine.formatDurationCompact(app.totalTimeMs),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPillTile(
                        label = "App Opens",
                        value = "${app.launchCount}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricPillTile(
                        label = "Share",
                        value = "${(app.sharePercent * 100f).toInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        onToggleBlockApp(app, !isBlocked)
                        inspectedApp = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBlocked) "Remove from Focus Guard Blocklist" else "Add to Focus Guard Blocklist")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Drill-down sheet for a specific category
    inspectedCategory?.let { catEntry ->
        val appsInCat = remember(report.appUsages, catEntry) {
            report.appUsages.filter { it.category == catEntry.category }
        }
        ModalBottomSheet(
            onDismissRequest = { inspectedCategory = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = catEntry.category.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${PhoneUsageAnalyticsEngine.formatDurationCompact(catEntry.totalTimeMs)} total · ${(catEntry.sharePercent * 100f).toInt()}% of screen time · ${catEntry.appCount} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                appsInCat.take(10).forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${app.launchCount} opens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = PhoneUsageAnalyticsEngine.formatDurationCompact(app.totalTimeMs),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(catEntry.category.accentHex)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UsageSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UsagePermissionExplainerCard(
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usage_permission_card"),
        tone = LiquidGlassTone.ACCENT,
        tintOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhonelinkLock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Usage Access",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real on-device analytics · Never fabricated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Brain Buffer uses Android's Usage Access permission to show your real daily screen time, app-wise trends, distracting app totals, and Focus Guard nudges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = BrandSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Raw usage logs stay strictly on your phone. Nothing is read inside your apps or uploaded without your opt-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("usage_grant_permission_button")
                ) {
                    Text("Open Usage Access Settings")
                }
                OutlinedButton(onClick = onRefresh) {
                    Text("Recheck")
                }
            }
        }
    }
}

@Composable
private fun UsageOverviewHeroCard(report: PhoneUsageReport) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tone = LiquidGlassTone.ELEVATED,
        tintOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TOTAL SCREEN TIME",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PhoneUsageAnalyticsEngine.formatDurationCompact(report.totalScreenTimeMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                report.deltaPercentVsPrevious?.let { delta ->
                    val isDown = delta <= 0
                    val tint = if (isDown) BrandSuccess else BrandWarmAmber
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tint.copy(alpha = 0.14f))
                            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isDown) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${if (delta > 0) "+" else ""}$delta% vs prev",
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPillTile(
                    label = "Daily Avg",
                    value = PhoneUsageAnalyticsEngine.formatDurationCompact(report.dailyAverageMs),
                    modifier = Modifier.weight(1f)
                )
                MetricPillTile(
                    label = "Distracting",
                    value = "${report.distractingMinutes}m",
                    valueColor = BrandWarmAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricPillTile(
                    label = "Study Apps",
                    value = "${report.studyMinutes}m",
                    valueColor = BrandSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricPillTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DailyTrendChartCard(points: List<DailyScreenTimePoint>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val distractingColor = BrandWarmAmber
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val visiblePoints = remember(points) { points.takeLast(14) }
    val maxMs = remember(visiblePoints) {
        (visiblePoints.maxOfOrNull { it.totalTimeMs } ?: 60_000L).coerceAtLeast(60_000L)
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.AutoGraph,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Daily Screen Time Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Peak ${PhoneUsageAnalyticsEngine.formatDurationCompact(maxMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                val count = visiblePoints.size.coerceAtLeast(1)
                val gap = 8.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(6.dp.toPx())
                val chartHeight = size.height

                // Reference baseline
                drawLine(
                    color = trackColor,
                    start = Offset(0f, chartHeight - 1f),
                    end = Offset(size.width, chartHeight - 1f),
                    strokeWidth = 1.dp.toPx()
                )

                visiblePoints.forEachIndexed { index, point ->
                    val x = index * (barWidth + gap)
                    val totalRatio = (point.totalTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    val totalBarH = (chartHeight * totalRatio).coerceAtLeast(if (point.totalTimeMs > 0L) 6.dp.toPx() else 2.dp.toPx())
                    val distRatio = if (point.totalTimeMs > 0L) {
                        (point.distractingTimeMs.toFloat() / point.totalTimeMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val distBarH = totalBarH * distRatio

                    // Full bar (total screen time)
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.78f),
                        topLeft = Offset(x, chartHeight - totalBarH),
                        size = Size(barWidth, totalBarH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    // Distracting overlay segment at bottom
                    if (distBarH > 2.dp.toPx()) {
                        drawRoundRect(
                            color = distractingColor.copy(alpha = 0.9f),
                            topLeft = Offset(x, chartHeight - distBarH),
                            size = Size(barWidth, distBarH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelIndices = if (visiblePoints.size <= 7) {
                    visiblePoints.indices.toList()
                } else {
                    listOf(0, visiblePoints.size / 2, visiblePoints.lastIndex).distinct()
                }
                labelIndices.forEach { idx ->
                    Text(
                        text = visiblePoints[idx].label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = primaryColor, label = "Total usage")
                LegendDot(color = distractingColor, label = "Distracting apps")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryUsageRow(
    entry: CategoryUsageEntry,
    onClick: () -> Unit
) {
    val accent = Color(entry.category.accentHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
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
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = entry.category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "(${entry.appCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${PhoneUsageAnalyticsEngine.formatDurationCompact(entry.totalTimeMs)} · ${(entry.sharePercent * 100f).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { entry.sharePercent.coerceIn(0.02f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun AppUsageRowCard(
    entry: AppUsageEntry,
    isBlockedInFocusGuard: Boolean,
    onClick: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val accent = Color(entry.category.accentHex)
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.category.isDistracting) Icons.Rounded.WarningAmber else Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.category.label} · ${entry.launchCount} opens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PhoneUsageAnalyticsEngine.formatDurationCompact(entry.totalTimeMs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(entry.sharePercent * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onToggleBlock,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBlockedInFocusGuard) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            ) {
                Icon(
                    imageVector = if (isBlockedInFocusGuard) Icons.Rounded.Block else Icons.Outlined.Block,
                    contentDescription = if (isBlockedInFocusGuard) "Blocked in Focus Guard" else "Add to Focus Guard blocklist",
                    tint = if (isBlockedInFocusGuard) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
