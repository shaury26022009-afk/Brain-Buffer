package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DailyFocusGoalProgress
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.model.InsightsRangeFilter
import com.example.model.SessionPurposeCategory
import com.example.model.StudyTask
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AppUsageAwarenessHelper
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Streamlined Purpose-Aware Home Screen.
 *
 * Focuses strictly on the core BRAIN BUFFER loop:
 * 1. Greeting + Active Session or Today's Intent-vs-Reality Alignment Hero
 * 2. Quick Purpose Launcher ("What are you using your phone for?")
 * 3. Honest Protection Readiness (Usage Access & Notifications)
 * 4. Lightweight Tasks with one-tap Focus launch
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: BrainBufferUiState,
    onOpenFocus: () -> Unit = {},
    onSelectQuickPurpose: (SessionPurposeCategory) -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleTaskCompleted: (StudyTask) -> Unit = {},
    onQuickAddTask: (String) -> Unit = {},
    onFocusTask: (StudyTask) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adaptive = LocalAdaptiveMetrics.current
    var hasNotifications by remember {
        mutableStateOf(BrainBufferNotificationManager.hasNotificationPermission(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifications = BrainBufferNotificationManager.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todaySummary = remember(
        uiState.purposeSessions,
        uiState.sessions,
        uiState.interventionEvents,
        uiState.todayEpochDay
    ) {
        IntentRealityAnalyticsEngine.computeSummary(
            purposeSessions = uiState.purposeSessions,
            legacyFocusSessions = uiState.sessions,
            interventionEvents = uiState.interventionEvents,
            range = InsightsRangeFilter.TODAY,
            todayEpochDay = uiState.todayEpochDay
        )
    }
    val dailyGoal = remember(todaySummary.approvedFocusMinutes, uiState.preferences.dailyTargetMinutes) {
        IntentRealityAnalyticsEngine.computeDailyGoalProgress(
            focusedMinutesToday = todaySummary.approvedFocusMinutes,
            dailyTargetMinutes = uiState.preferences.dailyTargetMinutes
        )
    }
    val animatedGoalProgress by animateFloatAsState(
        targetValue = dailyGoal.progressFraction,
        animationSpec = tween(durationMillis = if (uiState.preferences.reducedMotionOverride) 0 else 450),
        label = "dailyGoalProgress"
    )

    val greeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }
    val userName = uiState.preferences.studentName.trim()
    val openTasks = remember(uiState.tasks) { uiState.tasks.filter { !it.isCompleted }.take(5) }
    var newTaskTitle by remember { mutableStateOf("") }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .testTag("home_screen"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dateLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (userName.isNotBlank()) "$greeting, $userName" else greeting,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("home_open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                // Active Session Banner OR Today's Intent vs Reality Hero
                item {
                    if (uiState.timer.hasStarted) {
                        val snap = uiState.timer.snapshot
                        val accent = Color(snap.purposeCategory.accentHex)
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_active_session_card"),
                            tone = LiquidGlassTone.ACCENT,
                            cornerRadius = 24.dp,
                            borderAccent = accent,
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (snap.isOnBreak) "BREAK IN PROGRESS" else "ACTIVE · ${snap.purposeCategory.label.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accent
                                        )
                                        Text(
                                            text = snap.sessionGoal.ifBlank { snap.purposeCategory.label },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = uiState.timer.formattedTime,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = accent
                                    )
                                }
                                LiquidGlassButton(
                                    onClick = onOpenFocus,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_resume_focus_button")
                                ) {
                                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Active Session")
                                }
                            }
                        }
                    } else {
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_today_alignment_card"),
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
                                            text = "TODAY'S ALIGNMENT",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = todaySummary.alignmentPercent?.let { "$it% aligned" } ?: "No sessions yet",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(onClick = onOpenInsights) {
                                        Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Insights")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HomeMetricTile(
                                        label = "Intended",
                                        value = "${todaySummary.intendedPlannedMinutes}m",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HomeMetricTile(
                                        label = "Approved",
                                        value = "${todaySummary.approvedFocusMinutes}m",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HomeMetricTile(
                                        label = "Goal",
                                        value = DailyFocusGoalProgress.formatGoalDuration(uiState.preferences.dailyTargetMinutes),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                LiquidGlassButton(
                                    onClick = onOpenFocus,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("home_start_focus_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start Purpose-Aware Focus",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Focus Goal Progress Card
                item {
                    val goalAccent = if (dailyGoal.isGoalReached) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_daily_goal_card"),
                        cornerRadius = 22.dp,
                        borderAccent = if (dailyGoal.isGoalReached) goalAccent else null,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = goalAccent.copy(alpha = 0.14f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (dailyGoal.isGoalReached) {
                                                    Icons.Rounded.EmojiEvents
                                                } else {
                                                    Icons.Rounded.Flag
                                                },
                                                contentDescription = "Daily focus goal",
                                                tint = goalAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "DAILY FOCUS GOAL",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = goalAccent
                                        )
                                        Text(
                                            text = "${dailyGoal.formattedFocused} / ${dailyGoal.formattedTarget}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = goalAccent.copy(alpha = 0.16f),
                                    modifier = Modifier.testTag("home_daily_goal_badge")
                                ) {
                                    Text(
                                        text = if (dailyGoal.isGoalReached) "Goal Reached" else "${dailyGoal.percentComplete}%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = goalAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { animatedGoalProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .testTag("home_daily_goal_progress_bar"),
                                color = goalAccent,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                strokeCap = StrokeCap.Round
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        dailyGoal.bonusMinutes > 0 ->
                                            "Daily goal reached · +${dailyGoal.formattedBonus} bonus focus today"
                                        dailyGoal.isGoalReached ->
                                            "Daily goal reached — strong consistency today"
                                        dailyGoal.focusedMinutes == 0 ->
                                            "${dailyGoal.formattedRemaining} to go — start a focus block to track progress"
                                        else ->
                                            "${dailyGoal.formattedRemaining} remaining to reach today's target"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("home_edit_daily_goal_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }

                // Quick Purpose Launcher
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "What are you using your phone for?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap a purpose to set up your allowed apps and start focusing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SessionPurposeCategory.entries.forEach { category ->
                                    LiquidGlassChip(
                                        label = category.label,
                                        selected = false,
                                        onClick = { onSelectQuickPurpose(category) },
                                        modifier = Modifier.testTag("home_quick_purpose_${category.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Honest Protection Readiness
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Monitoring readiness",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ReadinessRow(
                                icon = Icons.Rounded.Shield,
                                title = "Usage Access (Package matching)",
                                isReady = uiState.hasUsageAccessPermission,
                                readyText = "Active · Never reads in-app content",
                                actionLabel = "Grant",
                                onAction = { AppUsageAwarenessHelper.openUsageAccessSettings(context) }
                            )
                            ReadinessRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "Notifications (Live timer & nudges)",
                                isReady = hasNotifications,
                                readyText = "Enabled",
                                actionLabel = "Enable",
                                onAction = { BrainBufferNotificationManager.openAppNotificationSettings(context) }
                            )
                        }
                    }
                }

                // Lightweight Focus Tasks
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Focus tasks",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newTaskTitle,
                                    onValueChange = { newTaskTitle = it },
                                    placeholder = { Text("Add something to finish today") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_quick_task_input")
                                )
                                IconButton(
                                    onClick = {
                                        val clean = newTaskTitle.trim()
                                        if (clean.isNotEmpty()) {
                                            onQuickAddTask(clean)
                                            newTaskTitle = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("home_quick_task_add_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add task",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (openTasks.isEmpty()) {
                                Text(
                                    text = "No open tasks. Add one above or jump straight into Focus.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                openTasks.forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onToggleTaskCompleted(task) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (task.isCompleted) {
                                                    Icons.Rounded.CheckCircle
                                                } else {
                                                    Icons.Rounded.RadioButtonUnchecked
                                                },
                                                contentDescription = "Toggle task",
                                                tint = if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { onFocusTask(task) }) {
                                            Text("Focus")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ReadinessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isReady: Boolean,
    readyText: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isReady) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isReady) readyText else "Optional · Not yet granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isReady) {
            LiquidGlassSecondaryButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
