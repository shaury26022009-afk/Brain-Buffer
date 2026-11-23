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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassProgressBar
import com.example.ui.components.LiquidGlassSearchBar
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassSegmentedControl
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
import java.util.Locale

private enum class PlannerTab(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    WEEKLY("Weekly")
}

private data class QuickDueDateOption(
    val epochDay: Long,
    val label: String
)

/**
 * Clean, practical Planner Screen with 4 focused views:
 * Today | Upcoming | Overdue | Weekly + collapsible Completed tasks section.
 * Removed cluttered 14-day/7-day calendar ribbons while keeping full CRUD,
 * search, subject filters, priority badges, and one-tap Start Focus linking.
 */
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
    onUndoDelete: () -> Unit,
    onStartFocusForTask: (StudyTask) -> Unit = {}
) {
    val todayEpoch = uiState.todayEpochDay
    val today = remember(todayEpoch) { LocalDate.ofEpochDay(todayEpoch) }
    val subjectMap = remember(uiState.subjects) { uiState.subjects.associateBy { it.id } }
    val adaptive = LocalAdaptiveMetrics.current

    var activeTab by remember { mutableStateOf(PlannerTab.TODAY) }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCompletedSection by remember { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<StudyTask?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dueDateOptions = remember(today) {
        val fmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        listOf(
            QuickDueDateOption(today.toEpochDay(), "Today"),
            QuickDueDateOption(today.plusDays(1).toEpochDay(), "Tomorrow"),
            QuickDueDateOption(today.plusDays(2).toEpochDay(), today.plusDays(2).format(fmt)),
            QuickDueDateOption(today.plusDays(3).toEpochDay(), today.plusDays(3).format(fmt)),
            QuickDueDateOption(today.plusDays(5).toEpochDay(), today.plusDays(5).format(fmt)),
            QuickDueDateOption(today.plusDays(7).toEpochDay(), "Next week"),
            QuickDueDateOption(today.plusDays(14).toEpochDay(), "In 2 weeks")
        )
    }

    val trimmedQuery = searchQuery.trim()
    val filteredAll = remember(uiState.tasks, selectedSubjectId, trimmedQuery, subjectMap) {
        uiState.tasks.filter { task ->
            val matchesSubject = selectedSubjectId == null || task.subjectId == selectedSubjectId
            val matchesSearch = if (trimmedQuery.isEmpty()) {
                true
            } else {
                val subjectName = task.subjectId?.let { subjectMap[it]?.name }.orEmpty()
                task.title.contains(trimmedQuery, ignoreCase = true) ||
                    task.notes.contains(trimmedQuery, ignoreCase = true) ||
                    subjectName.contains(trimmedQuery, ignoreCase = true)
            }
            matchesSubject && matchesSearch
        }
    }

    val overdueCount = remember(filteredAll, todayEpoch) {
        filteredAll.count { !it.isCompleted && it.dueDateEpochDay < todayEpoch }
    }

    val activeTasksInTab = remember(filteredAll, activeTab, todayEpoch, trimmedQuery) {
        filteredAll.filter { task ->
            if (trimmedQuery.isNotEmpty()) {
                !task.isCompleted
            } else {
                when (activeTab) {
                    PlannerTab.TODAY -> !task.isCompleted && task.dueDateEpochDay == todayEpoch
                    PlannerTab.UPCOMING -> !task.isCompleted && task.dueDateEpochDay > todayEpoch
                    PlannerTab.OVERDUE -> !task.isCompleted && task.dueDateEpochDay < todayEpoch
                    PlannerTab.WEEKLY -> !task.isCompleted && task.dueDateEpochDay in todayEpoch..(todayEpoch + 6)
                }
            }
        }.sortedWith(compareBy<StudyTask> { it.dueDateEpochDay }.thenBy { it.priority.sortOrder })
    }

    val completedTasks = remember(filteredAll, activeTab, todayEpoch, trimmedQuery) {
        filteredAll.filter { task ->
            if (!task.isCompleted) return@filter false
            if (trimmedQuery.isNotEmpty()) return@filter true
            when (activeTab) {
                PlannerTab.TODAY -> task.dueDateEpochDay == todayEpoch
                PlannerTab.WEEKLY -> task.dueDateEpochDay in todayEpoch..(todayEpoch + 6)
                else -> true
            }
        }.sortedByDescending { it.completedAtMs ?: 0L }
    }

    val todayDone = uiState.todayTasks.count { it.isCompleted }
    val todayTotal = uiState.todayTasks.size
    val todayProgress = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal.toFloat()

    val deleteWithUndo: (StudyTask) -> Unit = { task ->
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
                    .testTag("planner_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 14.dp,
                    bottom = adaptive.bottomContentClearance + 68.dp
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                // Header
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Planner",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val overdueSuffix = if (overdueCount > 0) " · $overdueCount overdue" else ""
                        Text(
                            text = "${uiState.tasks.count { !it.isCompleted }} active · ${uiState.completedTasksCount} completed$overdueSuffix",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 4-Mode View Switcher: Today | Upcoming | Overdue | Weekly
                item {
                    LiquidGlassSegmentedControl(
                        items = PlannerTab.entries,
                        selectedItem = activeTab,
                        onSelect = { activeTab = it },
                        labelProvider = { tab ->
                            if (tab == PlannerTab.OVERDUE && overdueCount > 0) {
                                "${tab.label} ($overdueCount)"
                            } else {
                                tab.label
                            }
                        },
                        testTagProvider = { "planner_tab_${it.name.lowercase()}" }
                    )
                }

                // Search bar
                item {
                    LiquidGlassSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search tasks, notes, or subjects…",
                        inputTestTag = "planner_search_input"
                    )
                }

                // Today's completion progress bar (shown on Today & Weekly when tasks exist)
                if ((activeTab == PlannerTab.TODAY || activeTab == PlannerTab.WEEKLY) && todayTotal > 0) {
                    item {
                        SanctuaryCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Today's progress · $todayDone of $todayTotal done",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${(todayProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LiquidGlassProgressBar(
                                progress = todayProgress,
                                color = MaterialTheme.colorScheme.primary,
                                height = 7.dp
                            )
                        }
                    }
                }

                // Subject / Category Filters
                if (uiState.subjects.isNotEmpty()) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                LiquidGlassChip(
                                    label = "All",
                                    selected = selectedSubjectId == null,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = { selectedSubjectId = null }
                                )
                            }
                            items(uiState.subjects, key = { it.id }) { subject ->
                                LiquidGlassChip(
                                    label = subject.name,
                                    selected = selectedSubjectId == subject.id,
                                    accentColor = subject.accentColor,
                                    leadingDotColor = subject.accentColor,
                                    onClick = {
                                        selectedSubjectId = if (selectedSubjectId == subject.id) null else subject.id
                                    }
                                )
                            }
                        }
                    }
                }

                // Active Tasks in Current View
                if (activeTasksInTab.isEmpty()) {
                    item {
                        PlannerEmptyState(
                            activeTab = activeTab,
                            searchQuery = trimmedQuery,
                            onClearSearch = { searchQuery = "" },
                            onAddTask = {
                                editingTask = null
                                showTaskSheet = true
                            }
                        )
                    }
                } else {
                    items(activeTasksInTab, key = { it.id }) { task ->
                        val subject = task.subjectId?.let { subjectMap[it] }
                        val dateLabel = formatRelativeDay(task.dueDateEpochDay, todayEpoch)
                        PlannerTaskItemCard(
                            task = task,
                            subject = subject,
                            dateLabel = dateLabel,
                            showDate = activeTab != PlannerTab.TODAY,
                            isOverdue = !task.isCompleted && task.dueDateEpochDay < todayEpoch,
                            onToggle = { onToggleTask(task.id) },
                            onStartFocus = { onStartFocusForTask(task) },
                            onEdit = {
                                editingTask = task
                                showTaskSheet = true
                            },
                            onDelete = { deleteWithUndo(task) }
                        )
                    }
                }

                // Collapsible Completed Tasks section
                if (completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Completed (${completedTasks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showCompletedSection = !showCompletedSection },
                                modifier = Modifier.testTag("planner_toggle_completed_btn")
                            ) {
                                Text(if (showCompletedSection) "Hide" else "Show")
                                Icon(
                                    imageVector = if (showCompletedSection) {
                                        Icons.Rounded.KeyboardArrowUp
                                    } else {
                                        Icons.Rounded.KeyboardArrowDown
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (showCompletedSection) {
                        items(completedTasks, key = { "done_${it.id}" }) { task ->
                            val subject = task.subjectId?.let { subjectMap[it] }
                            PlannerTaskItemCard(
                                task = task,
                                subject = subject,
                                dateLabel = formatRelativeDay(task.dueDateEpochDay, todayEpoch),
                                showDate = true,
                                isOverdue = false,
                                onToggle = { onToggleTask(task.id) },
                                onStartFocus = { onStartFocusForTask(task) },
                                onEdit = {
                                    editingTask = task
                                    showTaskSheet = true
                                },
                                onDelete = { deleteWithUndo(task) }
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
                    .padding(
                        bottom = adaptive.fabBottomPadding + 6.dp,
                        start = adaptive.horizontalPadding,
                        end = adaptive.horizontalPadding
                    )
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
                defaultDueEpochDay = if (activeTab == PlannerTab.UPCOMING) todayEpoch + 1 else todayEpoch,
                subjects = uiState.subjects,
                dueDateOptions = dueDateOptions,
                onDismiss = { showTaskSheet = false },
                onSave = { title, subjectId, dueEpoch, dueTime, mins, priority, notes ->
                    onSaveTask(editingTask?.id, title, subjectId, dueEpoch, dueTime, mins, priority, notes)
                    showTaskSheet = false
                }
            )
        }
    }
}

private fun formatRelativeDay(epochDay: Long, todayEpoch: Long): String {
    return when (epochDay - todayEpoch) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }
}

@Composable
private fun PlannerEmptyState(
    activeTab: PlannerTab,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onAddTask: () -> Unit
) {
    if (searchQuery.isNotEmpty()) {
        EmptySanctuaryCard(
            icon = Icons.Rounded.CalendarMonth,
            title = "No matching tasks",
            message = "No tasks or subjects matched \"$searchQuery\".",
            actionLabel = "Clear Search",
            onAction = onClearSearch
        )
        return
    }
    val (icon, title, msg) = when (activeTab) {
        PlannerTab.TODAY -> Triple(
            Icons.Rounded.CalendarMonth,
            "Nothing planned for today",
            "Add one clear task and start a Focus session directly from it."
        )
        PlannerTab.UPCOMING -> Triple(
            Icons.Rounded.Event,
            "No upcoming tasks",
            "Schedule upcoming assignments, work deliverables, or deadlines."
        )
        PlannerTab.OVERDUE -> Triple(
            Icons.Rounded.CheckCircle,
            "Zero overdue tasks",
            "You are completely caught up on past deadlines."
        )
        PlannerTab.WEEKLY -> Triple(
            Icons.Rounded.CalendarMonth,
            "Clear week ahead",
            "Add tasks for the next 7 days to stay ahead of deadlines."
        )
    }
    EmptySanctuaryCard(
        icon = icon,
        title = title,
        message = msg,
        actionLabel = if (activeTab != PlannerTab.OVERDUE) "Add Task" else null,
        onAction = if (activeTab != PlannerTab.OVERDUE) onAddTask else null
    )
}

@Composable
private fun PlannerTaskItemCard(
    task: StudyTask,
    subject: Subject?,
    dateLabel: String,
    showDate: Boolean,
    isOverdue: Boolean,
    onToggle: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1f else 0.92f,
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
                            color = when {
                                task.isCompleted -> MaterialTheme.colorScheme.primary
                                isOverdue -> MaterialTheme.colorScheme.error
                                else -> subject?.accentColor ?: MaterialTheme.colorScheme.outline
                            },
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
                            imageVector = if (isOverdue) Icons.Rounded.WarningAmber else Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildString {
                                if (showDate || isOverdue) append("$dateLabel · ")
                                append("${task.dueTimeLabel} · ${task.estimatedMinutes}m")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!task.isCompleted) {
                            IconButton(
                                onClick = onStartFocus,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("planner_task_focus_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Start focus on ${task.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
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
    dueDateOptions: List<QuickDueDateOption>,
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
            text = if (initialTask == null) "New Task" else "Edit Task",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task title") },
            placeholder = { Text("e.g., Finish quarterly report or Chapter 3 exercises") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task_editor_title_input")
        )

        if (subjects.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subject / Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidGlassChip(
                        label = "General",
                        selected = subjectId == null,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { subjectId = null }
                    )
                    subjects.forEach { subj ->
                        LiquidGlassChip(
                            label = subj.name,
                            selected = subjectId == subj.id,
                            accentColor = subj.accentColor,
                            leadingDotColor = subj.accentColor,
                            onClick = { subjectId = subj.id }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Due date", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dueDateOptions, key = { it.epochDay }) { slot ->
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
                            text = slot.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    listOf(25, 45, 60).forEach { m ->
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
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
