package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TaskPriority
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassProgressBar
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.PriorityBadge
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SubjectTag
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class PlannerTab(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    DEADLINES("Deadlines"),
    COMPLETED("Completed")
}

private data class CalendarDayOption(
    val epochDay: Long,
    val dayShort: String,
    val dayNum: String,
    val fullLabel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    uiState: BrainBufferUiState,
    onToggleTask: (Long) -> Unit,
    onSaveTask: (
        existingId: Long?,
        title: String,
        subjectId: Long?,
        dueDateEpochDay: Long,
        dueTimeLabel: String,
        estimatedMinutes: Int,
        priority: TaskPriority,
        notes: String
    ) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onUndoDelete: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val todayEpoch = today.toEpochDay()
    val subjectMap = remember(uiState.subjects) { uiState.subjects.associateBy { it.id } }

    var activeTab by remember { mutableStateOf(PlannerTab.DAILY) }
    var selectedEpochDay by remember { mutableLongStateOf(todayEpoch) }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<StudyTask?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val adaptive = LocalAdaptiveMetrics.current
    val twoWeekDays = remember(today) {
        val fullFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        (0..13).map { offset ->
            val date = today.plusDays(offset.toLong())
            CalendarDayOption(
                epochDay = date.toEpochDay(),
                dayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNum = date.dayOfMonth.toString(),
                fullLabel = when (offset) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> date.format(fullFmt)
                }
            )
        }
    }
    val weekDays = remember(twoWeekDays) { twoWeekDays.take(7) }

    val displayedTasks = remember(uiState.tasks, activeTab, selectedEpochDay, selectedSubjectId, todayEpoch) {
        uiState.tasks.filter { task ->
            val matchesSubject = selectedSubjectId == null || task.subjectId == selectedSubjectId
            val matchesTab = when (activeTab) {
                PlannerTab.DAILY -> task.dueDateEpochDay == selectedEpochDay
                PlannerTab.WEEKLY -> task.dueDateEpochDay in todayEpoch..(todayEpoch + 6)
                PlannerTab.DEADLINES -> !task.isCompleted && task.dueDateEpochDay >= todayEpoch
                PlannerTab.COMPLETED -> task.isCompleted
            }
            matchesSubject && matchesTab
        }.sortedWith(
            when (activeTab) {
                PlannerTab.COMPLETED -> compareByDescending<StudyTask> { it.completedAtMs ?: 0L }
                else -> compareBy<StudyTask> { it.isCompleted }.thenBy { it.dueDateEpochDay }.thenBy { it.priority.sortOrder }
            }
        )
    }

    val doneInView = displayedTasks.count { it.isCompleted }
    val totalInView = displayedTasks.size
    val progressRatio = if (totalInView == 0) 0f else doneInView.toFloat() / totalInView.toFloat()
    val upcomingExams = remember(uiState.subjects, todayEpoch) {
        uiState.subjects
            .filter { it.examDateEpochDay != null && it.examDateEpochDay >= todayEpoch }
            .sortedBy { it.examDateEpochDay }
    }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (adaptive.useTwoColumnLayout) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = adaptive.maxContentWidth)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            start = adaptive.horizontalPadding,
                            end = adaptive.horizontalPadding,
                            top = 16.dp
                        )
                        .testTag("planner_screen"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Study Planner",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${uiState.tasks.count { !it.isCompleted }} active · ${uiState.completedTasksCount} completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PlannerModeSwitcher(
                            activeTab = activeTab,
                            onSelectTab = { activeTab = it },
                            modifier = Modifier.widthIn(max = 420.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("planner_tablet_split"),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Pane: 2-week calendar grid, progress, subject filters, upcoming exams
                        Column(
                            modifier = Modifier
                                .weight(0.92f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = adaptive.bottomContentClearance + 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SanctuaryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("planner_tablet_calendar_grid"),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Text(
                                    text = "NEXT 14 DAYS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    twoWeekDays.chunked(7).forEach { weekRow ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            weekRow.forEach { day ->
                                                val dayTasks = uiState.tasks.filter { it.dueDateEpochDay == day.epochDay }
                                                PlannerCalendarDayTile(
                                                    day = day,
                                                    isSelected = activeTab == PlannerTab.DAILY && selectedEpochDay == day.epochDay,
                                                    isToday = day.epochDay == todayEpoch,
                                                    hasTasks = dayTasks.isNotEmpty(),
                                                    allDone = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted },
                                                    onClick = {
                                                        selectedEpochDay = day.epochDay
                                                        activeTab = PlannerTab.DAILY
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if ((activeTab == PlannerTab.DAILY || activeTab == PlannerTab.WEEKLY) && totalInView > 0) {
                                PlannerProgressCard(
                                    doneInView = doneInView,
                                    totalInView = totalInView,
                                    progressRatio = progressRatio
                                )
                            }

                            if (uiState.subjects.isNotEmpty()) {
                                SanctuaryCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Text(
                                        text = "FILTER BY SUBJECT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PlannerFilterPill(
                                            label = "All",
                                            color = MaterialTheme.colorScheme.primary,
                                            selected = selectedSubjectId == null,
                                            onClick = { selectedSubjectId = null }
                                        )
                                        uiState.subjects.forEach { subject ->
                                            PlannerFilterPill(
                                                label = subject.name,
                                                color = subject.accentColor,
                                                selected = selectedSubjectId == subject.id,
                                                onClick = {
                                                    selectedSubjectId = if (selectedSubjectId == subject.id) null else subject.id
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (upcomingExams.isNotEmpty()) {
                                UpcomingExamsCard(
                                    upcomingExams = upcomingExams,
                                    todayEpoch = todayEpoch
                                )
                            }
                        }

                        // Right Pane: Scrollable Task List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1.18f)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = adaptive.bottomContentClearance + 72.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (displayedTasks.isEmpty()) {
                                item {
                                    PlannerEmptyState(
                                        activeTab = activeTab,
                                        onAddTask = {
                                            editingTask = null
                                            showTaskSheet = true
                                        }
                                    )
                                }
                            } else {
                                items(displayedTasks, key = { it.id }) { task ->
                                    val subject = task.subjectId?.let { subjectMap[it] }
                                    val dateLabel = remember(task.dueDateEpochDay, todayEpoch) {
                                        when (task.dueDateEpochDay - todayEpoch) {
                                            0L -> "Today"
                                            1L -> "Tomorrow"
                                            -1L -> "Yesterday"
                                            else -> LocalDate.ofEpochDay(task.dueDateEpochDay)
                                                .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                                        }
                                    }
                                    PlannerTaskItemCard(
                                        task = task,
                                        subject = subject,
                                        dateLabel = dateLabel,
                                        showDate = activeTab != PlannerTab.DAILY,
                                        onToggle = { onToggleTask(task.id) },
                                        onEdit = {
                                            editingTask = task
                                            showTaskSheet = true
                                        },
                                        onDelete = {
                                            onDeleteTask(task.id)
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Deleted \"${task.title}\"",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onUndoDelete()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("planner_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 14.dp,
                    bottom = adaptive.bottomContentClearance + 56.dp
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                // Header
                item {
                    Column {
                        Text(
                            text = "Study Planner",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.tasks.count { !it.isCompleted }} active · ${uiState.completedTasksCount} completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 4-Mode View Switcher: Daily | Weekly | Deadlines | Completed
                item {
                    PlannerModeSwitcher(
                        activeTab = activeTab,
                        onSelectTab = { activeTab = it }
                    )
                }

                // 7-Day Calendar Ribbon (shown in Daily & Weekly tabs)
                if (activeTab == PlannerTab.DAILY || activeTab == PlannerTab.WEEKLY) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(weekDays, key = { it.epochDay }) { day ->
                                val isSelected = activeTab == PlannerTab.DAILY && selectedEpochDay == day.epochDay
                                val dayTasks = uiState.tasks.filter { it.dueDateEpochDay == day.epochDay }
                                val allDone = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted }
                                val isToday = day.epochDay == todayEpoch

                                LiquidGlassCard(
                                    modifier = Modifier.width(56.dp),
                                    tone = if (isSelected) LiquidGlassTone.ACCENT else LiquidGlassTone.STANDARD,
                                    cornerRadius = 18.dp,
                                    tintOverlay = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else null,
                                    borderAccent = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                        else -> null
                                    },
                                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                                    onClick = {
                                        selectedEpochDay = day.epochDay
                                        activeTab = PlannerTab.DAILY
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = day.dayShort,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = day.dayNum,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        dayTasks.isEmpty() -> Color.Transparent
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        allDone -> MaterialTheme.colorScheme.secondary
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Progress bar (Daily / Weekly)
                if ((activeTab == PlannerTab.DAILY || activeTab == PlannerTab.WEEKLY) && totalInView > 0) {
                    item {
                        PlannerProgressCard(
                            doneInView = doneInView,
                            totalInView = totalInView,
                            progressRatio = progressRatio
                        )
                    }
                }

                // Subject Filters (user's real subjects)
                if (uiState.subjects.isNotEmpty()) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                PlannerFilterPill(
                                    label = "All",
                                    color = MaterialTheme.colorScheme.primary,
                                    selected = selectedSubjectId == null,
                                    onClick = { selectedSubjectId = null }
                                )
                            }
                            items(uiState.subjects, key = { it.id }) { subject ->
                                PlannerFilterPill(
                                    label = subject.name,
                                    color = subject.accentColor,
                                    selected = selectedSubjectId == subject.id,
                                    onClick = {
                                        selectedSubjectId = if (selectedSubjectId == subject.id) null else subject.id
                                    }
                                )
                            }
                        }
                    }
                }

                // Upcoming Subject Exams Banner in Deadlines tab
                if (activeTab == PlannerTab.DEADLINES) {
                    val upcomingExams = uiState.subjects
                        .filter { it.examDateEpochDay != null && it.examDateEpochDay >= todayEpoch }
                        .sortedBy { it.examDateEpochDay }
                    if (upcomingExams.isNotEmpty()) {
                        item {
                            SanctuaryCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentPadding = PaddingValues(15.dp)
                            ) {
                                Text(
                                    text = "UPCOMING EXAMS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                upcomingExams.forEachIndexed { i, subject ->
                                    val daysLeft = subject.daysUntilExam(todayEpoch) ?: 0L
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SubjectTag(subject = subject)
                                        Text(
                                            text = if (daysLeft == 0L) "Today" else "${subject.examDateFormatted()} (${daysLeft}d)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (i < upcomingExams.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // Task List or Helpful Empty State
                if (displayedTasks.isEmpty()) {
                    item {
                        val (icon, title, msg) = when (activeTab) {
                            PlannerTab.COMPLETED -> Triple(
                                Icons.Rounded.CheckCircle,
                                "No completed tasks yet",
                                "Finished tasks will appear here as you check them off."
                            )
                            PlannerTab.DEADLINES -> Triple(
                                Icons.Rounded.Event,
                                "No pending deadlines",
                                "You're caught up. Add an assignment or exam revision anytime."
                            )
                            else -> Triple(
                                Icons.Rounded.CalendarMonth,
                                "No tasks planned here",
                                "Break your study session into one small, clear step."
                            )
                        }
                        EmptySanctuaryCard(
                            icon = icon,
                            title = title,
                            message = msg,
                            actionLabel = if (activeTab != PlannerTab.COMPLETED) "Add Task" else null,
                            onAction = if (activeTab != PlannerTab.COMPLETED) {
                                {
                                    editingTask = null
                                    showTaskSheet = true
                                }
                            } else null
                        )
                    }
                } else {
                    items(displayedTasks, key = { it.id }) { task ->
                        val subject = task.subjectId?.let { subjectMap[it] }
                        val dateLabel = remember(task.dueDateEpochDay, todayEpoch) {
                            when (task.dueDateEpochDay - todayEpoch) {
                                0L -> "Today"
                                1L -> "Tomorrow"
                                -1L -> "Yesterday"
                                else -> LocalDate.ofEpochDay(task.dueDateEpochDay)
                                    .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                            }
                        }
                        PlannerTaskItemCard(
                            task = task,
                            subject = subject,
                            dateLabel = dateLabel,
                            showDate = activeTab != PlannerTab.DAILY,
                            onToggle = { onToggleTask(task.id) },
                            onEdit = {
                                editingTask = task
                                showTaskSheet = true
                            },
                            onDelete = {
                                onDeleteTask(task.id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted \"${task.title}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUndoDelete()
                                    }
                                }
                            }
                        )
                    }
                }
            }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = adaptive.fabBottomPadding + 6.dp, start = adaptive.horizontalPadding, end = adaptive.horizontalPadding)
            )

            ExtendedFloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskSheet = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_task)) },
                text = {
                    Text(
                        text = stringResource(R.string.action_add_task),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = adaptive.horizontalPadding, bottom = adaptive.fabBottomPadding)
                    .testTag("planner_add_task_fab")
            )
        }
    }

    if (showTaskSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTaskSheet = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            TaskEditorSheet(
                initialTask = editingTask,
                defaultDueEpochDay = selectedEpochDay,
                subjects = uiState.subjects,
                dayOptions = twoWeekDays,
                onDismiss = { showTaskSheet = false },
                onSave = { title, subjectId, dueEpoch, dueTime, mins, priority, notes ->
                    onSaveTask(editingTask?.id, title, subjectId, dueEpoch, dueTime, mins, priority, notes)
                    showTaskSheet = false
                }
            )
        }
    }
}

@Composable
private fun PlannerModeSwitcher(
    activeTab: PlannerTab,
    onSelectTab: (PlannerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassSegmentedControl(
        items = PlannerTab.entries,
        selectedItem = activeTab,
        onSelect = onSelectTab,
        labelProvider = { it.label },
        testTagProvider = { "planner_tab_${it.name.lowercase()}" },
        modifier = modifier
    )
}

@Composable
private fun PlannerCalendarDayTile(
    day: CalendarDayOption,
    isSelected: Boolean,
    isToday: Boolean,
    hasTasks: Boolean,
    allDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = if (isSelected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
        cornerRadius = 14.dp,
        tintOverlay = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else null,
        borderAccent = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else -> null
        },
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = day.dayShort.take(2),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = day.dayNum,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !hasTasks -> Color.Transparent
                            isSelected -> MaterialTheme.colorScheme.primary
                            allDone -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}

@Composable
private fun PlannerProgressCard(
    doneInView: Int,
    totalInView: Int,
    progressRatio: Float
) {
    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$doneInView of $totalInView tasks completed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(progressRatio * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LiquidGlassProgressBar(
            progress = progressRatio,
            color = MaterialTheme.colorScheme.primary,
            height = 7.dp
        )
    }
}

@Composable
private fun UpcomingExamsCard(
    upcomingExams: List<Subject>,
    todayEpoch: Long
) {
    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(15.dp)
    ) {
        Text(
            text = "UPCOMING EXAMS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        upcomingExams.forEachIndexed { i, subject ->
            val daysLeft = subject.daysUntilExam(todayEpoch) ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubjectTag(subject = subject)
                Text(
                    text = if (daysLeft == 0L) "Today" else "${subject.examDateFormatted()} (${daysLeft}d)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (i < upcomingExams.lastIndex) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlannerEmptyState(
    activeTab: PlannerTab,
    onAddTask: () -> Unit
) {
    val (icon, title, msg) = when (activeTab) {
        PlannerTab.COMPLETED -> Triple(
            Icons.Rounded.CheckCircle,
            "No completed tasks yet",
            "Finished tasks will appear here as you check them off."
        )
        PlannerTab.DEADLINES -> Triple(
            Icons.Rounded.Event,
            "No pending deadlines",
            "You're caught up. Add an assignment or exam revision anytime."
        )
        else -> Triple(
            Icons.Rounded.CalendarMonth,
            "No tasks planned here",
            "Break your study session into one small, clear step."
        )
    }
    EmptySanctuaryCard(
        icon = icon,
        title = title,
        message = msg,
        actionLabel = if (activeTab != PlannerTab.COMPLETED) "Add Task" else null,
        onAction = if (activeTab != PlannerTab.COMPLETED) onAddTask else null
    )
}

@Composable
private fun PlannerFilterPill(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassChip(
        label = label,
        selected = selected,
        accentColor = color,
        leadingDotColor = if (label != "All" && label != "General") color else null,
        onClick = onClick
    )
}

@Composable
private fun PlannerTaskItemCard(
    task: StudyTask,
    subject: Subject?,
    dateLabel: String,
    showDate: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (reducedMotion) Spring.StiffnessHigh else Spring.StiffnessMedium
        ),
        label = "taskCheckScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (task.isCompleted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        label = "taskBgColor"
    )

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("planner_task_${task.id}"),
        containerColor = bgColor,
        contentPadding = PaddingValues(14.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        .size(25.dp)
                        .scale(checkScale)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            width = 2.dp,
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
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (task.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subject != null) {
                        SubjectTag(subject = subject, compact = true)
                    }
                    PriorityBadge(priority = task.priority)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildString {
                                if (showDate) append("$dateLabel · ")
                                append("${task.dueTimeLabel} · ${task.estimatedMinutes}m")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.cd_edit_task),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_task),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorSheet(
    initialTask: StudyTask?,
    defaultDueEpochDay: Long,
    subjects: List<Subject>,
    dayOptions: List<CalendarDayOption>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, Long, String, Int, TaskPriority, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var notes by remember { mutableStateOf(initialTask?.notes ?: "") }
    var subjectId by remember { mutableStateOf(initialTask?.subjectId ?: subjects.firstOrNull()?.id) }
    var dueEpochDay by remember { mutableLongStateOf(initialTask?.dueDateEpochDay ?: defaultDueEpochDay) }
    var dueTime by remember { mutableStateOf(initialTask?.dueTimeLabel ?: "6:00 PM") }
    var estimatedMinutes by remember { mutableIntStateOf(initialTask?.estimatedMinutes ?: 30) }
    var priority by remember { mutableStateOf(initialTask?.priority ?: TaskPriority.MEDIUM) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (initialTask == null) "New Study Task" else "Edit Task",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task title") },
            placeholder = { Text("e.g., Solve Chapter 3 problem set") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task_editor_title_input")
        )

        if (subjects.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subject", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlannerFilterPill(
                        label = "General",
                        color = MaterialTheme.colorScheme.primary,
                        selected = subjectId == null,
                        onClick = { subjectId = null }
                    )
                    subjects.forEach { subj ->
                        PlannerFilterPill(
                            label = subj.name,
                            color = subj.accentColor,
                            selected = subjectId == subj.id,
                            onClick = { subjectId = subj.id }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Deadline day", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayOptions) { slot ->
                    val selected = dueEpochDay == slot.epochDay
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .springClickable(onClick = { dueEpochDay = slot.epochDay }),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Text(
                            text = slot.fullLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = dueTime,
                onValueChange = { dueTime = it },
                label = { Text("Due time") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Estimate: ${estimatedMinutes}m", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 30, 45).forEach { m ->
                        val sel = estimatedMinutes == m
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .springClickable(onClick = { estimatedMinutes = m }),
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${m}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { p ->
                    val sel = priority == p
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .springClickable(onClick = { priority = p }),
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassSecondaryButton(
                onClick = onDismiss,
                cornerRadius = 14.dp,
                height = 48.dp,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
            LiquidGlassButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, subjectId, dueEpochDay, dueTime, estimatedMinutes, priority, notes)
                    }
                },
                enabled = title.isNotBlank(),
                cornerRadius = 14.dp,
                height = 48.dp,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("task_editor_save_button")
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
