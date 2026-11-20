package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.StudyCoachEngine
import com.example.domain.StudyRecommendation
import com.example.model.GardenPlant
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.SubjectStudySummary
import com.example.model.UpcomingDeadlineItem
import com.example.ui.components.BrainBufferLogoMark
import com.example.ui.components.CircularProgressBadge
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassProgressBar
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.components.SubjectTag
import com.example.ui.components.springClickable
import com.example.ui.components.subjectIconVector
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: BrainBufferUiState,
    greeting: String,
    formattedDate: String,
    onRefreshMotivation: () -> Unit,
    onStartFocus: (Long?) -> Unit,
    onOpenFocus: () -> Unit,
    onToggleTask: (Long) -> Unit,
    onQuickAddTask: (title: String, subjectId: Long?) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMindReset: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val extended = BrainBufferThemeTokens.extended
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = extended.isDark
    val taskPreviewLimit = if (adaptive.useTwoColumnLayout) 5 else 3
    val todayTasks = remember(uiState.todayTasks, taskPreviewLimit) {
        uiState.todayTasks.sortedBy { it.isCompleted }.take(taskPreviewLimit)
    }
    val totalTodayCount = uiState.todayTasks.size
    val nearestDeadline = uiState.upcomingDeadlines.firstOrNull()
    val tabletDeadlines = remember(uiState.upcomingDeadlines) { uiState.upcomingDeadlines.take(3) }
    val subjectSummaries = uiState.subjectSummaries
    val subjectMap = remember(uiState.subjects) { uiState.subjects.associateBy { it.id } }
    val studyCoachRec = remember(
        uiState.subjects,
        uiState.tasks,
        uiState.focusOnlySessions,
        uiState.todayFocusMinutes,
        uiState.preferences.dailyTargetMinutes,
        uiState.preferences.preferredFocusMinutes,
        uiState.todayEpochDay
    ) {
        StudyCoachEngine.computeRecommendation(uiState)
    }
    var showQuickAddSheet by remember { mutableStateOf(false) }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("home_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 12.dp,
                    bottom = adaptive.bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                // 1. Minimal Compact Header (Brand Mark + Greeting + Date + Profile Access)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                BrainBufferLogoMark(
                                    size = 20.dp,
                                    animatePulse = false,
                                    glowAlpha = 0f
                                )
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassIconContainer(
                                size = 40.dp,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                shape = CircleShape,
                                onClick = onOpenMindReset,
                                modifier = Modifier
                                    .testTag("home_mind_reset_pill")
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Spa,
                                    contentDescription = stringResource(R.string.cd_mind_reset),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            StudentAvatarBadge(
                                avatar = uiState.preferences.avatar,
                                size = 40.dp,
                                modifier = Modifier
                                    .testTag("home_profile_avatar")
                                    .clip(CircleShape)
                                    .springClickable(onClick = onOpenProfile)
                            )
                        }
                    }
                }

                if (adaptive.useTwoColumnLayout) {
                    // Balanced Tablet Two-Column Dashboard
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_tablet_two_column"),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Column: Daily Focus Hero + Weekly Overview + Daily Insight + Subjects
                            Column(
                                modifier = Modifier.weight(1.12f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                HomeDailyFocusHeroCard(
                                    uiState = uiState,
                                    isDark = isDark,
                                    onOpenFocus = onOpenFocus,
                                    onOpenInsights = onOpenInsights,
                                    onStartFocus = { onStartFocus(null) }
                                )

                                // Tablet Study Overview Statistics
                                SanctuaryCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    onClick = onOpenInsights
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        HomeStatCell("This Week", formatMinutesCompact(uiState.weeklyFocusMinutes))
                                        HomeStatCell("Active Days", "${uiState.activeDaysThisWeek}/${uiState.preferences.weeklyStudyDaysTarget}")
                                        HomeStatCell("Sessions", "${uiState.focusOnlySessions.size}")
                                        HomeStatCell("Tasks Done", "${uiState.completedTasksCount}")
                                    }
                                }

                                HomeSmartStudyCoachCard(
                                    recommendation = studyCoachRec,
                                    onStartAction = { onStartFocus(studyCoachRec.subjectId) }
                                )

                                HomeDailyInsightCard(
                                    message = uiState.motivationalMessage,
                                    onRefresh = onRefreshMotivation
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Subject Progress",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    TextButton(
                                        onClick = onOpenProfile,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Manage",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (subjectSummaries.isEmpty()) {
                                    SanctuaryCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                        onClick = onOpenProfile
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Add subjects to track weekly hours",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(subjectSummaries, key = { it.subject.id }) { summary ->
                                            CompactSubjectTile(
                                                summary = summary,
                                                todayEpochDay = uiState.todayEpochDay,
                                                onClick = { onStartFocus(summary.subject.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Right Column: Today's Tasks + Upcoming Deadlines + Garden Preview
                            Column(
                                modifier = Modifier.weight(0.92f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                HomeTodayTasksHeader(
                                    totalTodayCount = totalTodayCount,
                                    onQuickAdd = { showQuickAddSheet = true },
                                    onOpenPlanner = onOpenPlanner
                                )

                                if (todayTasks.isEmpty()) {
                                    HomeTasksEmptyRow(onQuickAdd = { showQuickAddSheet = true })
                                } else {
                                    todayTasks.forEach { task ->
                                        CompactHomeTaskRow(
                                            task = task,
                                            subject = task.subjectId?.let { subjectMap[it] },
                                            onToggle = { onToggleTask(task.id) },
                                            onOpenPlanner = onOpenPlanner
                                        )
                                    }
                                }

                                if (tabletDeadlines.isNotEmpty()) {
                                    Text(
                                        text = "Upcoming Deadlines",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    tabletDeadlines.forEach { deadline ->
                                        NearestDeadlineCompactCard(
                                            item = deadline,
                                            todayEpochDay = uiState.todayEpochDay,
                                            onSeeAll = onOpenPlanner
                                        )
                                    }
                                }

                                CompactGardenPreviewCard(
                                    plant = uiState.activePlant,
                                    seedsOwned = uiState.preferences.seedsOwned,
                                    onOpenGarden = onOpenGarden
                                )
                            }
                        }
                    }
                } else {
                    // Phone Portrait & Compact Single-Column Dashboard
                    item {
                        HomeDailyFocusHeroCard(
                            uiState = uiState,
                            isDark = isDark,
                            onOpenFocus = onOpenFocus,
                            onOpenInsights = onOpenInsights,
                            onStartFocus = { onStartFocus(null) }
                        )
                    }

                    item {
                        HomeSmartStudyCoachCard(
                            recommendation = studyCoachRec,
                            onStartAction = { onStartFocus(studyCoachRec.subjectId) }
                        )
                    }

                    item {
                        HomeDailyInsightCard(
                            message = uiState.motivationalMessage,
                            onRefresh = onRefreshMotivation
                        )
                    }

                    item {
                        HomeTodayTasksHeader(
                            totalTodayCount = totalTodayCount,
                            onQuickAdd = { showQuickAddSheet = true },
                            onOpenPlanner = onOpenPlanner
                        )
                    }

                    if (todayTasks.isEmpty()) {
                        item {
                            HomeTasksEmptyRow(onQuickAdd = { showQuickAddSheet = true })
                        }
                    } else {
                        items(todayTasks, key = { it.id }) { task ->
                            CompactHomeTaskRow(
                                task = task,
                                subject = task.subjectId?.let { subjectMap[it] },
                                onToggle = { onToggleTask(task.id) },
                                onOpenPlanner = onOpenPlanner
                            )
                        }
                    }

                    if (nearestDeadline != null) {
                        item {
                            NearestDeadlineCompactCard(
                                item = nearestDeadline,
                                todayEpochDay = uiState.todayEpochDay,
                                onSeeAll = onOpenPlanner
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subject Progress",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(
                                onClick = onOpenProfile,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (subjectSummaries.isEmpty()) {
                        item {
                            SanctuaryCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                onClick = onOpenProfile
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Add subjects to track weekly hours",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(subjectSummaries, key = { it.subject.id }) { summary ->
                                    CompactSubjectTile(
                                        summary = summary,
                                        todayEpochDay = uiState.todayEpochDay,
                                        onClick = { onStartFocus(summary.subject.id) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        CompactGardenPreviewCard(
                            plant = uiState.activePlant,
                            seedsOwned = uiState.preferences.seedsOwned,
                            onOpenGarden = onOpenGarden
                        )
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuickAddSheet = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground
        ) {
            QuickTaskCaptureSheet(
                subjects = uiState.subjects,
                onDismiss = { showQuickAddSheet = false },
                onAdd = { title, subjectId ->
                    onQuickAddTask(title, subjectId)
                    showQuickAddSheet = false
                }
            )
        }
    }
}

@Composable
private fun HomeDailyFocusHeroCard(
    uiState: BrainBufferUiState,
    isDark: Boolean,
    onOpenFocus: () -> Unit,
    onOpenInsights: () -> Unit,
    onStartFocus: () -> Unit
) {
    val extended = BrainBufferThemeTokens.extended
    val heroPrimaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
    val heroSubText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.86f)
    val hasActiveSession = uiState.timer.hasStarted
    val activeSubjectName = uiState.subjects
        .firstOrNull { it.id == uiState.timer.selectedSubjectId }
        ?.name ?: "General Study"

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_focus_hero_card"),
        tone = LiquidGlassTone.ELEVATED,
        cornerRadius = 24.dp,
        borderAccent = if (isDark) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
        contentPadding = PaddingValues(0.dp),
        onClick = onOpenFocus
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        if (isDark) {
                            listOf(
                                extended.heroGradientStart.copy(alpha = 0.86f),
                                extended.heroGradientEnd.copy(alpha = 0.92f)
                            )
                        } else {
                            listOf(extended.heroGradientStart, extended.heroGradientEnd)
                        }
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = if (isDark) 0.08f else 0.22f),
                            0.36f to Color.Transparent
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasActiveSession) {
                            uiState.timer.formattedTime
                        } else {
                            formatMinutesCompact(uiState.todayFocusMinutes)
                        },
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = heroPrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasActiveSession) {
                            val status = if (uiState.timer.isRunning) {
                                if (uiState.timer.isStrictFocus) "High Focus" else uiState.timer.activeActivityLabel
                            } else {
                                "${uiState.timer.activeActivityLabel} · Paused"
                            }
                            "$status · $activeSubjectName"
                        } else {
                            "of ${formatMinutesCompact(uiState.preferences.dailyTargetMinutes)} daily goal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = heroSubText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                CircularProgressBadge(
                    progress = if (hasActiveSession) uiState.timer.progress else uiState.todayTargetProgress,
                    size = 66.dp,
                    strokeWidth = 6.5.dp,
                    progressColor = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
                    trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.24f),
                    centerText = "${((if (hasActiveSession) uiState.timer.progress else uiState.todayTargetProgress) * 100).toInt()}%"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.20f),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.42f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .springClickable(onClick = onOpenInsights)
                        .testTag("home_insights_icon")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFFFFE29A),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${uiState.currentStreakDays}d streak",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = heroPrimaryText
                        )
                    }
                }

                LiquidGlassButton(
                    onClick = if (hasActiveSession) onOpenFocus else onStartFocus,
                    accentStart = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
                    accentEnd = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color(0xFFF3EEFF),
                    contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color(0xFF4F33C7),
                    cornerRadius = 15.dp,
                    height = 44.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("home_start_focus_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when {
                            hasActiveSession && uiState.timer.isStrictFocus -> "Open High Focus"
                            hasActiveSession -> "Return to Session"
                            else -> "Start Focus"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeDailyInsightCard(
    message: String,
    onRefresh: () -> Unit
) {
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_motivation_card"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_quote_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_quote),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeTodayTasksHeader(
    totalTodayCount: Int,
    onQuickAdd: () -> Unit,
    onOpenPlanner: () -> Unit
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
            Text(
                text = "Today's Tasks",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (totalTodayCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$totalTodayCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onQuickAdd,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_quick_add_task_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Quick add task",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = onOpenPlanner,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Planner",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HomeTasksEmptyRow(onQuickAdd: () -> Unit) {
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_tasks_empty"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onQuickAdd
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Clear schedule today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Quick Task",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactHomeTaskRow(
    task: StudyTask,
    subject: Subject?,
    onToggle: () -> Unit,
    onOpenPlanner: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (task.isCompleted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        label = "homeTaskBg"
    )
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_task_${task.id}"),
        containerColor = bgColor,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        onClick = onOpenPlanner
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .springClickable(onClick = onToggle)
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            width = 1.8.dp,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.primary else (subject?.accentColor ?: MaterialTheme.colorScheme.outline),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subject != null) {
                        SubjectTag(subject = subject, compact = true)
                    }
                    Text(
                        text = "${task.dueTimeLabel} · ${task.estimatedMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NearestDeadlineCompactCard(
    item: UpcomingDeadlineItem,
    todayEpochDay: Long,
    onSeeAll: () -> Unit
) {
    val diff = item.dateEpochDay - todayEpochDay
    val badgeText = when {
        diff <= 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff <= 14L -> "In ${diff}d"
        else -> LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ofPattern("MMM d"))
    }

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_nearest_deadline"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onSeeAll
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                LiquidGlassIconContainer(
                    size = 36.dp,
                    accentColor = item.accentColor,
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Event,
                        contentDescription = null,
                        tint = item.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Next deadline · ${item.subtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (item.isExam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isExam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactSubjectTile(
    summary: SubjectStudySummary,
    todayEpochDay: Long,
    onClick: () -> Unit
) {
    val examDays = summary.subject.daysUntilExam(todayEpochDay)?.takeIf { it >= 0L }

    SanctuaryCard(
        modifier = Modifier.width(156.dp),
        contentPadding = PaddingValues(13.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LiquidGlassIconContainer(
                size = 32.dp,
                accentColor = summary.subject.accentColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = subjectIconVector(summary.subject.iconKey),
                    contentDescription = null,
                    tint = summary.subject.accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (examDays != null) {
                Text(
                    text = "${examDays}d",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = summary.subject.accentColor
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = summary.subject.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "${formatMinutesCompact(summary.loggedMinutesThisWeek)} / ${summary.subject.targetHoursPerWeek.toInt()}h",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LiquidGlassProgressBar(
            progress = summary.weeklyProgress,
            color = summary.subject.accentColor,
            height = 6.dp
        )
    }
}

@Composable
private fun CompactGardenPreviewCard(
    plant: GardenPlant?,
    seedsOwned: Int,
    onOpenGarden: () -> Unit
) {
    val accent = plant?.species?.petalColor ?: BrainBufferThemeTokens.extended.sageAccent

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_garden_preview"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onOpenGarden
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(
                size = 42.dp,
                accentColor = accent,
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFlorist,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plant?.nickname ?: "My Study Garden",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = plant?.stage?.label ?: "$seedsOwned seed${if (seedsOwned == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LiquidGlassProgressBar(
                    progress = plant?.progressToNextStage ?: 0f,
                    color = accent,
                    height = 6.dp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open Garden",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun QuickTaskCaptureSheet(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onAdd: (title: String, subjectId: Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Quick Task for Today",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("What needs focus today?") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_task_title_input")
        )
        if (subjects.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjects, key = { it.id }) { subject ->
                    val selected = selectedSubjectId == subject.id
                    LiquidGlassChip(
                        label = subject.name,
                        selected = selected,
                        accentColor = subject.accentColor,
                        leadingDotColor = subject.accentColor,
                        onClick = {
                            selectedSubjectId = if (selected) null else subject.id
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            LiquidGlassButton(
                onClick = {
                    if (title.isNotBlank()) onAdd(title.trim(), selectedSubjectId)
                },
                enabled = title.isNotBlank(),
                cornerRadius = 14.dp,
                height = 46.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_task_save_button")
            ) {
                Text("Add Task", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun HomeSmartStudyCoachCard(
    recommendation: StudyRecommendation,
    onStartAction: () -> Unit
) {
    val accent = Color(recommendation.accentHex)
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_study_coach_card"),
        tone = LiquidGlassTone.ACCENT,
        cornerRadius = 20.dp,
        tintOverlay = accent.copy(alpha = 0.12f),
        borderAccent = accent.copy(alpha = 0.45f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(
                size = 42.dp,
                accentColor = accent,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = recommendation.badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    maxLines = 1
                )
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recommendation.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LiquidGlassButton(
            onClick = onStartAction,
            cornerRadius = 14.dp,
            height = 42.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_study_coach_action")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${recommendation.actionLabel} · ${recommendation.recommendedMinutes}m",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


