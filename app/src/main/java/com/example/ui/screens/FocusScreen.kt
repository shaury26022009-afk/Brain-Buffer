package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.SessionCelebration
import com.example.model.Subject
import com.example.audio.LocalAmbientSoundscapeController
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.FocusSoundscapePill
import com.example.ui.components.FocusSoundscapeSheet
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassDialogCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubjectBadge
import com.example.ui.components.SubjectEditorSheetContent
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.platform.LocalContext
import com.example.model.AppUsageSuggestion
import com.example.notifications.BrainBufferNotificationManager
import com.example.ui.components.iconForActivity
import androidx.compose.ui.unit.Dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    uiState: BrainBufferUiState,
    onSelectMode: (FocusMode) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSelectSubject: (Long?) -> Unit,
    onAddSubject: (Subject) -> Unit,
    onUpdateSessionGoal: (String) -> Unit,
    onToggleZenMode: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onCompleteNow: () -> Unit,
    onDismissCelebration: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenMindReset: () -> Unit,
    onAddDistractionNote: (String) -> Unit = {},
    onOpenStrictFocus: () -> Unit = {},
    onOpenActivitySwitcher: () -> Unit = {},
    onReturnToStudy: () -> Unit = {},
    onExtendTimer: (Int) -> Unit = {},
    onAcceptAppSuggestion: (AppUsageSuggestion) -> Unit = {},
    onDismissAppSuggestion: () -> Unit = {},
    onOpenControlledPhoneAccess: () -> Unit = onOpenActivitySwitcher,
    onDismissUnapprovedWarning: () -> Unit = {},
    onStartBreakFromCelebration: () -> Unit = { onSelectMode(FocusMode.SHORT_BREAK) }
) {
    val timer = uiState.timer
    val context = LocalContext.current
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val adaptive = LocalAdaptiveMetrics.current
    val useSplitFocusLayout = adaptive.useTwoColumnLayout || adaptive.useCompactLandscapeSplit
    val timerRingSize = when {
        adaptive.isCompactHeight -> 208.dp
        adaptive.useTwoColumnLayout -> 292.dp
        else -> 264.dp
    }
    val currentMinutes = (timer.totalSeconds / 60).coerceAtLeast(1)
    val selectedSubject = uiState.subjects.firstOrNull { it.id == timer.selectedSubjectId }
    val soundscapeController = LocalAmbientSoundscapeController.current
    val soundscapeState by soundscapeController.state.collectAsState()
    var showAddSubjectSheet by remember { mutableStateOf(false) }
    var showSoundscapeSheet by remember { mutableStateOf(false) }
    var distractionDraft by remember { mutableStateOf("") }
    var requestedNotificationPermOnce by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Timer works reliably even if notification permission is declined
        onStartOrResume()
    }

    val startOrResumeWithNotificationCheck = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !requestedNotificationPermOnce &&
            uiState.preferences.notificationsEnabled &&
            !BrainBufferNotificationManager.hasNotificationPermission(context)
        ) {
            requestedNotificationPermOnce = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartOrResume()
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
                    .testTag("focus_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 14.dp,
                    bottom = adaptive.bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Focus Mode",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (timer.isZenMode) {
                                    "Distraction-free calm"
                                } else {
                                    uiState.activePlant?.let { "Growing ${it.nickname} · ${it.stage.label}" }
                                        ?: "Ready to start your study session"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onOpenMindReset,
                                modifier = Modifier
                                    .testTag("focus_mind_reset_button")
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Spa,
                                    contentDescription = stringResource(R.string.cd_mind_reset),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            FocusSoundscapePill(
                                state = soundscapeState,
                                onClick = { showSoundscapeSheet = true }
                            )
                            IconButton(
                                onClick = onToggleZenMode,
                                modifier = Modifier
                                    .testTag("focus_zen_toggle")
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (timer.isZenMode) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = stringResource(R.string.cd_toggle_zen),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Next-Gen Study Companion Quick Strip: Strict Focus + Switch Activity + +5m Extend
                if (!timer.isZenMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassSecondaryButton(
                                onClick = onOpenStrictFocus,
                                accentBorder = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.primary,
                                cornerRadius = 14.dp,
                                height = 44.dp,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("focus_strict_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Focus Guard", style = MaterialTheme.typography.labelLarge)
                            }

                            LiquidGlassSecondaryButton(
                                onClick = onOpenControlledPhoneAccess,
                                accentBorder = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                cornerRadius = 14.dp,
                                height = 44.dp,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("focus_switch_activity_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Need Phone", style = MaterialTheme.typography.labelLarge)
                            }

                            if (timer.hasStarted) {
                                LiquidGlassSecondaryButton(
                                    onClick = { onExtendTimer(5) },
                                    cornerRadius = 14.dp,
                                    height = 44.dp,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.testTag("focus_extend_5m_button")
                                ) {
                                    Text("+5m", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    // Active switched activity banner with one-tap Return to Study
                    if (timer.isSwitchedActivity) {
                        item {
                            val parent = timer.snapshot.pausedParentSession
                            SanctuaryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("focus_return_to_study_banner"),
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = iconForActivity(timer.activeActivityType),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Active: ${timer.activeActivityLabel}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = parent?.let {
                                                    val remMin = ((it.totalSeconds - (it.accumulatedFocusedMs / 1000L).toInt()).coerceAtLeast(0) + 59) / 60
                                                    "${it.activityLabel} paused · ~${remMin}m left"
                                                } ?: "Study session paused",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = onReturnToStudy,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("focus_return_study_cta")
                                    ) {
                                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Return", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Optional local App-Usage Awareness suggestion chip (strictly opt-in)
                    uiState.appUsageSuggestion?.let { suggestion ->
                        item {
                            SanctuaryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("focus_app_usage_suggestion_card"),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Recently used ${suggestion.appDisplayName}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Switch timer to ${suggestion.suggestedActivity.label} (${suggestion.suggestedDurationMinutes}m)?",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { onAcceptAppSuggestion(suggestion) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Switch", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(onClick = onDismissAppSuggestion) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Dismiss suggestion",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Gentle Unapproved App Mismatch Warning (Part 6 — optional Usage Access only)
                    uiState.unapprovedAppWarning?.let { warning ->
                        item {
                            SanctuaryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("focus_unapproved_app_warning_card"),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Opened ${warning.appDisplayName} during focus",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = warning.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = onOpenControlledPhoneAccess,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Log", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(onClick = onDismissUnapprovedWarning) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Dismiss warning",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (useSplitFocusLayout && !timer.isZenMode) {
                    // Tablet & Landscape Side-by-Side Focus Workspace
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("focus_split_layout"),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Pane: Large Animated Timer + Custom Presets + Primary Controls
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FocusCircularTimer(
                                    formattedTime = timer.formattedTime,
                                    progress = timer.progress,
                                    mode = timer.mode,
                                    isRunning = timer.isRunning,
                                    subjectName = if (timer.mode == FocusMode.FOCUS) {
                                        selectedSubject?.name ?: "General Study"
                                    } else {
                                        "Recharge calmly"
                                    },
                                    accentColor = selectedSubject?.accentColor ?: MaterialTheme.colorScheme.primary,
                                    reducedMotion = reducedMotion,
                                    ringSize = timerRingSize
                                )

                                AnimatedVisibility(
                                    visible = !timer.isRunning && !timer.hasStarted,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    FocusDurationPresetBar(
                                        mode = timer.mode,
                                        currentMinutes = currentMinutes,
                                        onSetDuration = onSetDuration
                                    )
                                }

                                FocusTimerControlsRow(
                                    timer = timer,
                                    reducedMotion = reducedMotion,
                                    onReset = onReset,
                                    onStartOrResume = startOrResumeWithNotificationCheck,
                                    onPause = onPause,
                                    onCompleteNow = onCompleteNow
                                )
                            }

                            // Right Pane: Mode Switcher + Subject & Goal + Distraction Parking Lot + Stats & History
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                FocusModeSegmentedBar(
                                    activeMode = timer.mode,
                                    onSelectMode = onSelectMode
                                )

                                if (timer.mode == FocusMode.FOCUS) {
                                    FocusSubjectAndGoalSelector(
                                        subjects = uiState.subjects,
                                        selectedSubjectId = timer.selectedSubjectId,
                                        sessionGoal = timer.sessionGoal,
                                        onSelectSubject = onSelectSubject,
                                        onAddSubjectClick = { showAddSubjectSheet = true },
                                        onUpdateSessionGoal = onUpdateSessionGoal
                                    )
                                }

                                // Distraction Parking Lot for deep focus sessions
                                SanctuaryCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("focus_distraction_parking_lot"),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Text(
                                        text = "Distraction Parking Lot",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = distractionDraft,
                                            onValueChange = { distractionDraft = it },
                                            placeholder = { Text("Park a stray thought for later…") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                if (distractionDraft.isNotBlank()) {
                                                    onAddDistractionNote(distractionDraft.trim())
                                                    distractionDraft = ""
                                                }
                                            },
                                            enabled = distractionDraft.isNotBlank(),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Park")
                                        }
                                    }
                                }

                                SanctuaryCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        FocusMiniStat("Today", formatMinutesCompact(uiState.todayFocusMinutes))
                                        FocusMiniStat("Sessions", "${uiState.todaySessionsCount}")
                                        FocusMiniStat("Target", "${(uiState.todayTargetProgress * 100).toInt()}%")
                                    }
                                }

                                if (uiState.focusOnlySessions.isNotEmpty()) {
                                    SectionHeader(
                                        title = "Recent Sessions",
                                        subtitle = "${uiState.focusOnlySessions.size} saved"
                                    )
                                    uiState.focusOnlySessions.take(4).forEach { record ->
                                        FocusSessionHistoryCard(record = record)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Phone Portrait & Zen Mode Layout
                    item {
                        FocusModeSegmentedBar(
                            activeMode = timer.mode,
                            onSelectMode = onSelectMode
                        )
                    }

                    if (!timer.isZenMode && timer.mode == FocusMode.FOCUS) {
                        item {
                            FocusSubjectAndGoalSelector(
                                subjects = uiState.subjects,
                                selectedSubjectId = timer.selectedSubjectId,
                                sessionGoal = timer.sessionGoal,
                                onSelectSubject = onSelectSubject,
                                onAddSubjectClick = { showAddSubjectSheet = true },
                                onUpdateSessionGoal = onUpdateSessionGoal
                            )
                        }
                    }

                    item {
                        FocusCircularTimer(
                            formattedTime = timer.formattedTime,
                            progress = timer.progress,
                            mode = timer.mode,
                            isRunning = timer.isRunning,
                            subjectName = if (timer.mode == FocusMode.FOCUS) {
                                selectedSubject?.name ?: "General Study"
                            } else {
                                "Recharge calmly"
                            },
                            accentColor = selectedSubject?.accentColor ?: MaterialTheme.colorScheme.primary,
                            reducedMotion = reducedMotion,
                            ringSize = timerRingSize
                        )
                    }

                    item {
                        AnimatedVisibility(
                            visible = !timer.isRunning && !timer.hasStarted,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            FocusDurationPresetBar(
                                mode = timer.mode,
                                currentMinutes = currentMinutes,
                                onSetDuration = onSetDuration
                            )
                        }
                    }

                    item {
                        FocusTimerControlsRow(
                            timer = timer,
                            reducedMotion = reducedMotion,
                            onReset = onReset,
                            onStartOrResume = startOrResumeWithNotificationCheck,
                            onPause = onPause,
                            onCompleteNow = onCompleteNow
                        )
                    }

                    if (!timer.isZenMode) {
                        item {
                            SanctuaryCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    FocusMiniStat("Today", formatMinutesCompact(uiState.todayFocusMinutes))
                                    FocusMiniStat("Sessions", "${uiState.todaySessionsCount}")
                                    FocusMiniStat("Daily Target", "${(uiState.todayTargetProgress * 100).toInt()}%")
                                }
                            }
                        }

                        item {
                            SectionHeader(
                                title = "Session History",
                                subtitle = if (uiState.focusOnlySessions.isEmpty()) {
                                    "Your completed focus sessions appear here"
                                } else {
                                    "${uiState.focusOnlySessions.size} saved focus sessions"
                                }
                            )
                        }

                        if (uiState.focusOnlySessions.isEmpty()) {
                            item {
                                EmptySanctuaryCard(
                                    icon = Icons.Rounded.Timer,
                                    title = "No focus sessions yet",
                                    message = "Start a session above — your focus time saves automatically."
                                )
                            }
                        } else {
                            items(uiState.focusOnlySessions.take(8), key = { it.id }) { record ->
                                FocusSessionHistoryCard(record = record)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubjectSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSubjectSheet = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            SubjectEditorSheetContent(
                initialSubject = null,
                onDismiss = { showAddSubjectSheet = false },
                onSave = { subject ->
                    onAddSubject(subject)
                    showAddSubjectSheet = false
                }
            )
        }
    }

    if (showSoundscapeSheet) {
        FocusSoundscapeSheet(
            state = soundscapeState,
            onSelectSoundscape = { soundscape ->
                soundscapeController.selectSoundscape(
                    soundscape = soundscape,
                    autoPlay = !soundscapeState.syncWithTimer || timer.isRunning || true
                )
            },
            onTogglePlayPause = soundscapeController::togglePlayPause,
            onVolumeChange = soundscapeController::setVolume,
            onSyncWithTimerChange = soundscapeController::setSyncWithTimer,
            onDismiss = { showSoundscapeSheet = false }
        )
    }

    uiState.pendingCelebration?.let { celebration ->
        FocusCelebrationDialog(
            celebration = celebration,
            studentName = uiState.preferences.displayName,
            onStartBreak = {
                onDismissCelebration()
                onStartBreakFromCelebration()
            },
            onReturnToStudy = {
                onDismissCelebration()
                onReturnToStudy()
            },
            onExtendFiveMin = {
                onDismissCelebration()
                onExtendTimer(5)
            },
            onFinishSession = {
                onDismissCelebration()
                onReset()
            },
            onVisitGarden = {
                onDismissCelebration()
                onOpenGarden()
            },
            onDismiss = onDismissCelebration
        )
    }
}

@Composable
private fun FocusMiniStat(label: String, value: String) {
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
private fun FocusModeSegmentedBar(
    activeMode: FocusMode,
    onSelectMode: (FocusMode) -> Unit
) {
    LiquidGlassSegmentedControl(
        items = FocusMode.entries,
        selectedItem = activeMode,
        onSelect = onSelectMode,
        labelProvider = { it.label },
        testTagProvider = { "focus_mode_${it.name.lowercase()}" }
    )
}

@Composable
private fun FocusSubjectAndGoalSelector(
    subjects: List<Subject>,
    selectedSubjectId: Long?,
    sessionGoal: String,
    onSelectSubject: (Long?) -> Unit,
    onAddSubjectClick: () -> Unit,
    onUpdateSessionGoal: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                LiquidGlassChip(
                    label = "General Study",
                    selected = selectedSubjectId == null,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSelectSubject(null) }
                )
            }

            items(subjects, key = { it.id }) { subject ->
                LiquidGlassChip(
                    label = subject.name,
                    selected = selectedSubjectId == subject.id,
                    accentColor = subject.accentColor,
                    leadingDotColor = subject.accentColor,
                    onClick = { onSelectSubject(subject.id) },
                    modifier = Modifier.testTag("focus_subject_${subject.id}")
                )
            }

            item {
                LiquidGlassChip(
                    label = "Add Subject",
                    selected = false,
                    leadingIcon = Icons.Rounded.Add,
                    onClick = onAddSubjectClick
                )
            }
        }

        OutlinedTextField(
            value = sessionGoal,
            onValueChange = onUpdateSessionGoal,
            label = { Text("Session goal (optional)") },
            placeholder = { Text("e.g., Finish 10 practice problems") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("focus_session_goal_input")
        )
    }
}

@Composable
private fun FocusDurationPresetBar(
    mode: FocusMode,
    currentMinutes: Int,
    onSetDuration: (Int) -> Unit
) {
    val presets = when (mode) {
        FocusMode.FOCUS -> listOf(15, 25, 45, 60)
        FocusMode.SHORT_BREAK -> listOf(5, 10, 15)
        FocusMode.LONG_BREAK -> listOf(15, 20, 30)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onSetDuration((currentMinutes - 5).coerceAtLeast(1)) },
            modifier = Modifier.testTag("timer_minus_5")
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease duration")
        }
        presets.forEach { preset ->
            LiquidGlassChip(
                label = "${preset}m",
                selected = currentMinutes == preset,
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { onSetDuration(preset) },
                modifier = Modifier.testTag("timer_preset_$preset")
            )
        }
        IconButton(
            onClick = { onSetDuration((currentMinutes + 5).coerceAtMost(120)) },
            modifier = Modifier.testTag("timer_plus_5")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase duration")
        }
    }
}

@Composable
private fun FocusTimerControlsRow(
    timer: com.example.viewmodel.FocusTimerUiState,
    reducedMotion: Boolean,
    onReset: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onCompleteNow: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassIconContainer(
            size = 52.dp,
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            onClick = onReset,
            modifier = Modifier.testTag("timer_reset_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.action_reset),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val ctaColor = if (timer.mode == FocusMode.FOCUS) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        }
        LiquidGlassButton(
            onClick = { if (timer.isRunning) onPause() else onStartOrResume() },
            accentStart = ctaColor,
            accentEnd = ctaColor.copy(alpha = 0.84f),
            cornerRadius = 28.dp,
            height = 56.dp,
            modifier = Modifier
                .widthIn(min = 176.dp)
                .testTag("timer_primary_action_button")
        ) {
            val stateKey = when {
                timer.isRunning -> "pause"
                timer.hasStarted -> "resume"
                else -> "start"
            }
            AnimatedContent(
                targetState = stateKey,
                transitionSpec = {
                    fadeIn(tween(if (reducedMotion) 0 else 160)) togetherWith
                        fadeOut(tween(if (reducedMotion) 0 else 120))
                },
                label = "focusPrimaryActionLabel"
            ) { key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (key == "pause") Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (key) {
                            "pause" -> stringResource(R.string.action_pause)
                            "resume" -> stringResource(R.string.action_resume)
                            else -> stringResource(R.string.action_start_focus)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        LiquidGlassIconContainer(
            size = 52.dp,
            accentColor = MaterialTheme.colorScheme.secondary,
            shape = CircleShape,
            onClick = onCompleteNow,
            modifier = Modifier.testTag("timer_complete_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.action_complete_now),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun FocusCircularTimer(
    formattedTime: String,
    progress: Float,
    mode: FocusMode,
    isRunning: Boolean,
    subjectName: String,
    accentColor: Color,
    reducedMotion: Boolean,
    ringSize: Dp = 264.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (reducedMotion) Spring.StiffnessHigh else Spring.StiffnessLow
        ),
        label = "timerRingProgress"
    )

    val renderQuality = BrainBufferThemeTokens.renderQuality
    val isAppInForeground = BrainBufferThemeTokens.isAppInForeground
    val animatePulse = isRunning && !reducedMotion && renderQuality.enableContinuousCanvasMotion && isAppInForeground

    val pulseScale = if (animatePulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseVal"
        )
        anim
    } else {
        1f
    }

    val ringPrimary = if (mode == FocusMode.FOCUS) accentColor else MaterialTheme.colorScheme.secondary
    val glass = BrainBufferThemeTokens.glass
    val trackColor = if (glass.isDark) Color.White.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant
    val innerTop = glass.elevatedTop
    val innerBottom = glass.surfaceBottom

    Box(
        modifier = Modifier
            .size(ringSize)
            .testTag("focus_timer_ring"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val strokePx = 12.dp.toPx()
            val radius = (minOf(w, h) / 2f) - 20.dp.toPx()
            val innerDiscRadius = radius - strokePx * 0.68f

            // Ambient calm aura behind the glass dial
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringPrimary.copy(alpha = if (isRunning) 0.18f else 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (minOf(w, h) / 2f) * pulseScale
                ),
                radius = (minOf(w, h) / 2f) * pulseScale,
                center = center
            )

            // Translucent Liquid Glass inner dial disc
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(innerTop, innerBottom),
                    startY = center.y - innerDiscRadius,
                    endY = center.y + innerDiscRadius
                ),
                radius = innerDiscRadius,
                center = center
            )

            // Top specular glass reflection crescent inside timer disc
            drawCircle(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = if (glass.isDark) 0.10f else 0.60f),
                        0.38f to Color.Transparent
                    ),
                    startY = center.y - innerDiscRadius,
                    endY = center.y + innerDiscRadius
                ),
                radius = innerDiscRadius,
                center = center
            )

            // Crisp hairline specular glass rim around the inner dial
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(glass.borderTop, glass.borderMid, glass.borderBottom),
                    startY = center.y - innerDiscRadius,
                    endY = center.y + innerDiscRadius
                ),
                radius = innerDiscRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Delicate precision clock dial ticks inside the timer ring
            val tickOuter = radius - strokePx * 1.15f
            for (i in 0 until 24) {
                val angle = (i * 15.0 - 90.0) * PI / 180.0
                val isMajor = i % 6 == 0
                val tickLen = if (isMajor) 5.5.dp.toPx() else 3.dp.toPx()
                val sx = center.x + (tickOuter - tickLen) * cos(angle).toFloat()
                val sy = center.y + (tickOuter - tickLen) * sin(angle).toFloat()
                val ex = center.x + tickOuter * cos(angle).toFloat()
                val ey = center.y + tickOuter * sin(angle).toFloat()
                drawLine(
                    color = trackColor.copy(alpha = if (isMajor) 0.9f else 0.55f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            val sweep = 360f * animatedProgress.coerceAtLeast(0.01f)
            drawArc(
                color = ringPrimary,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            val angleRad = ((-90f + sweep) * PI / 180.0)
            val tipX = center.x + radius * cos(angleRad).toFloat()
            val tipY = center.y + radius * sin(angleRad).toFloat()
            drawCircle(color = Color.White, radius = strokePx * 0.42f, center = Offset(tipX, tipY))
            drawCircle(color = ringPrimary, radius = strokePx * 0.22f, center = Offset(tipX, tipY))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isRunning) "FOCUSING" else mode.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ringPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subjectName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusSessionHistoryCard(record: FocusSessionRecord) {
    val timeText = remember(record.completedAtMs) {
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(record.completedAtMs))
    }

    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectBadge(
                        name = record.subjectName,
                        accentColor = Color(record.subjectColorHex),
                        compact = true
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.sessionGoal.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = record.sessionGoal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${record.durationMinutes} min",
                    style = MaterialTheme.typography.labelMedium.copy(
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

@Composable
private fun FocusCelebrationDialog(
    celebration: SessionCelebration,
    studentName: String = "Student",
    onStartBreak: () -> Unit,
    onReturnToStudy: () -> Unit = {},
    onExtendFiveMin: () -> Unit = {},
    onFinishSession: () -> Unit = {},
    onVisitGarden: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanName = studentName.trim().ifBlank { "Student" }
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassDialogCard(
            modifier = Modifier.testTag("session_celebration_dialog"),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LiquidGlassIconContainer(
                    size = 64.dp,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (celebration.seedsEarned > 0) {
                        "Well done, $cleanName!"
                    } else {
                        "${celebration.activityLabel} time is up"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (celebration.seedsEarned > 0) {
                        "You focused for ${celebration.durationMinutes} min on ${celebration.subjectName}."
                    } else {
                        "$cleanName, ${celebration.durationMinutes} min logged under ${celebration.stageLabel} (tracked separately from focused study). Ready to return?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (celebration.seedsEarned > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SanctuaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (celebration.leveledUp) {
                                    "${celebration.plantName} grew to ${celebration.stageLabel}!"
                                } else {
                                    "${celebration.plantName} grew stronger"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "+${celebration.seedsEarned} Seed earned for My Study Garden",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            celebration.newlyUnlockedDecoration?.let { deco ->
                                Text(
                                    text = "Unlocked decoration: ${deco.title}!",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                if (celebration.canReturnToStudy) {
                    LiquidGlassButton(
                        onClick = onReturnToStudy,
                        cornerRadius = 15.dp,
                        height = 48.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("celebration_return_to_study_button")
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Return to Study", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassSecondaryButton(
                            onClick = onExtendFiveMin,
                            cornerRadius = 14.dp,
                            height = 46.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("celebration_extend_button")
                        ) {
                            Text("Extend +5m", style = MaterialTheme.typography.labelLarge)
                        }
                        LiquidGlassSecondaryButton(
                            onClick = onStartBreak,
                            cornerRadius = 14.dp,
                            height = 46.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("celebration_take_break_button")
                        ) {
                            Text("Take Break", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onFinishSession,
                        modifier = Modifier.testTag("celebration_finish_button")
                    ) {
                        Text("Finish Session")
                    }
                } else {
                    LiquidGlassButton(
                        onClick = onVisitGarden,
                        cornerRadius = 15.dp,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.LocalFlorist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Visit My Study Garden", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LiquidGlassSecondaryButton(
                        onClick = onStartBreak,
                        cornerRadius = 14.dp,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take a Short Break", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
