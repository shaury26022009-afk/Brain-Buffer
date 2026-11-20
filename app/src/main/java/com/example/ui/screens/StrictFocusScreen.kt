package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BreakPolicy
import com.example.model.FocusGuardLevel
import com.example.model.StrictFocusPreset
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.UnapprovedAppWarning
import com.example.system.AppLauncherHelper
import com.example.system.DndFocusController
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LavenderLuminous
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Focus Guard 2.0 Hub & Active Distraction-Free Sanctuary:
 * - Three protection levels: Normal Focus, High Focus, Custom Focus.
 * - Pure `#000000` AMOLED AOD-inspired High Focus Display with minimal BRAIN BUFFER wordmark,
 *   date/time, burn-in-safe subtle pixel drift, razor-thin lavender ring, large white timer,
 *   subject & activity label, and essential controls (Need Phone, Pause/Resume, Exit).
 * - Voluntary, reversible, never traps the user, never forces permanent wake locks, and always
 *   preserves emergency calls, phone dialer, system alarms, and accessibility tools.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrictFocusScreen(
    uiState: BrainBufferUiState,
    onBack: () -> Unit,
    onStartStrictFocus: (
        subjectId: Long?,
        sessionGoal: String,
        activityType: StudyActivityType,
        durationMinutes: Int,
        strictness: StrictnessLevel,
        enableDnd: Boolean,
        enableAodStyle: Boolean
    ) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtendFiveMin: () -> Unit,
    onToggleAodStyle: () -> Unit,
    onOpenActivitySwitcher: () -> Unit,
    onExitStrictFocus: (keepTimerRunning: Boolean) -> Unit,
    onSavePreset: (StrictFocusPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onStartFocusGuardWithLevel: (
        subjectId: Long?,
        sessionGoal: String,
        activityType: StudyActivityType,
        durationMinutes: Int,
        strictness: StrictnessLevel,
        enableDnd: Boolean,
        enableAodStyle: Boolean,
        guardLevel: FocusGuardLevel
    ) -> Unit = { subId, goal, act, mins, strictness, dnd, aod, _ ->
        onStartStrictFocus(subId, goal, act, mins, strictness, dnd, aod)
    },
    onOpenFocusGuardSettings: () -> Unit = {},
    onOpenControlledPhoneAccess: () -> Unit = onOpenActivitySwitcher,
    onReturnToStudy: () -> Unit = {},
    onDismissUnapprovedWarning: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val timer = uiState.timer
    val guardSettings = uiState.preferences.focusGuardSettings
    var showExitConfirm by remember { mutableStateOf(false) }

    // Optional user-authorized Android Screen Pinning ONLY during active High Focus when opted in
    val shouldUseOptionalPinning = timer.isStrictFocus &&
        timer.hasStarted &&
        !timer.isSwitchedActivity &&
        timer.focusGuardLevel == FocusGuardLevel.HIGH &&
        guardSettings.optionalAppPinningEnabled

    LaunchedEffect(shouldUseOptionalPinning) {
        if (shouldUseOptionalPinning) {
            AppLauncherHelper.startOptionalAppPinning(activity)
        } else {
            AppLauncherHelper.stopOptionalAppPinning(activity)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AppLauncherHelper.stopOptionalAppPinning(activity)
        }
    }

    val handleExitAttempt = {
        if (timer.isStrictFocus && timer.hasStarted && timer.snapshot.strictFocusStrictness != StrictnessLevel.GENTLE) {
            showExitConfirm = true
        } else if (timer.isStrictFocus) {
            AppLauncherHelper.stopOptionalAppPinning(activity)
            onExitStrictFocus(false)
            onBack()
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleExitAttempt)

    if (timer.isStrictFocus && timer.hasStarted) {
        val subjectName = uiState.subjects.firstOrNull { it.id == timer.selectedSubjectId }?.name ?: "General Study"
        if (timer.isAodStyle) {
            AodInspiredFocusDisplay(
                formattedTime = timer.formattedTime,
                progress = timer.progress,
                activityLabel = timer.activeActivityLabel,
                subjectName = subjectName,
                isRunning = timer.isRunning,
                dndActive = timer.snapshot.strictFocusDndActive,
                guardLevelLabel = timer.focusGuardLevel.label,
                isSwitchedActivity = timer.isSwitchedActivity,
                unapprovedWarning = uiState.unapprovedAppWarning,
                onWakeToMinimal = onToggleAodStyle,
                onTogglePause = { if (timer.isRunning) onPause() else onResume() },
                onNeedPhone = {
                    AppLauncherHelper.stopOptionalAppPinning(activity)
                    onOpenControlledPhoneAccess()
                },
                onReturnToStudy = onReturnToStudy,
                onDismissWarning = onDismissUnapprovedWarning,
                onExit = handleExitAttempt
            )
        } else {
            ActiveStrictFocusView(
                uiState = uiState,
                subjectName = subjectName,
                isPinned = AppLauncherHelper.isAppPinningActive(context),
                onPause = onPause,
                onResume = onResume,
                onExtendFiveMin = onExtendFiveMin,
                onToggleAod = onToggleAodStyle,
                onNeedPhone = {
                    AppLauncherHelper.stopOptionalAppPinning(activity)
                    onOpenControlledPhoneAccess()
                },
                onReturnToStudy = onReturnToStudy,
                onDismissWarning = onDismissUnapprovedWarning,
                onExit = handleExitAttempt
            )
        }
    } else {
        StrictFocusSetupScreen(
            uiState = uiState,
            dndGranted = DndFocusController.isAccessGranted(context),
            onOpenDndSettings = { DndFocusController.openDndAccessSettings(context) },
            onOpenSettings = onOpenFocusGuardSettings,
            onBack = onBack,
            onStartFocusGuard = onStartFocusGuardWithLevel,
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Leave Focus Guard?") },
            text = {
                Text(
                    "Focus Guard is always voluntary. You can keep your timer running in normal Focus view, or end the session and restore Do Not Disturb right away."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        AppLauncherHelper.stopOptionalAppPinning(activity)
                        onExitStrictFocus(true)
                        onBack()
                    }
                ) {
                    Text("Keep Timer & Exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text("Stay Focused")
                    }
                    TextButton(
                        onClick = {
                            showExitConfirm = false
                            AppLauncherHelper.stopOptionalAppPinning(activity)
                            onExitStrictFocus(false)
                            onBack()
                        }
                    ) {
                        Text("End Session")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrictFocusSetupScreen(
    uiState: BrainBufferUiState,
    dndGranted: Boolean,
    onOpenDndSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onStartFocusGuard: (
        subjectId: Long?,
        sessionGoal: String,
        activityType: StudyActivityType,
        durationMinutes: Int,
        strictness: StrictnessLevel,
        enableDnd: Boolean,
        enableAodStyle: Boolean,
        guardLevel: FocusGuardLevel
    ) -> Unit,
    onSavePreset: (StrictFocusPreset) -> Unit,
    onDeletePreset: (Long) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val haptic = LocalHapticFeedback.current
    val guardSettings = uiState.preferences.focusGuardSettings

    var selectedLevel by remember { mutableStateOf(guardSettings.defaultLevel) }
    var durationMinutes by remember {
        mutableIntStateOf(guardSettings.defaultFocusMinutes.coerceIn(15, 120))
    }
    var selectedSubjectId by remember {
        mutableStateOf(uiState.timer.selectedSubjectId ?: uiState.subjects.firstOrNull()?.id)
    }
    var selectedActivity by remember { mutableStateOf(StudyActivityType.STUDYING) }
    var sessionGoal by remember { mutableStateOf(uiState.timer.sessionGoal) }
    var strictness by remember { mutableStateOf(guardSettings.strictnessLevel) }
    var breakPolicy by remember { mutableStateOf(BreakPolicy.AUTO_OFFER_BREAK) }
    var enableDnd by remember { mutableStateOf(dndGranted && guardSettings.dndIntegrationEnabled) }
    var enableAod by remember {
        mutableStateOf(selectedLevel == FocusGuardLevel.HIGH || uiState.preferences.aodFocusDisplayEnabled)
    }
    var allowedApps by remember { mutableStateOf(guardSettings.allowedStudyAppsCsv) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val focusFriendlyActivities = listOf(
        StudyActivityType.STUDYING,
        StudyActivityType.SOLVING_QUESTIONS,
        StudyActivityType.STUDY_PDF,
        StudyActivityType.READING,
        StudyActivityType.WRITING_NOTES,
        StudyActivityType.STUDY_LECTURE
    )

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
                    .testTag("strict_focus_setup_screen"),
                contentPadding = PaddingValues(
                    horizontal = adaptive.horizontalPadding,
                    vertical = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Column {
                                Text(
                                    text = "Focus Guard",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Calm distraction control · voluntary & reversible",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("open_focus_guard_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Focus Guard Settings",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showSavePresetDialog = true },
                                modifier = Modifier.testTag("save_strict_preset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BookmarkAdd,
                                    contentDescription = "Save preset",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Part 1: Three Focus Guard Levels (Normal Focus, High Focus, Custom Focus)
                item {
                    SanctuaryCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Choose Focus Level",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FocusGuardLevel.entries.forEach { level ->
                                val isChosen = selectedLevel == level
                                LiquidGlassCard(
                                    tone = if (isChosen) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                                    cornerRadius = 16.dp,
                                    borderAccent = if (isChosen) MaterialTheme.colorScheme.primary else null,
                                    contentPadding = PaddingValues(14.dp),
                                    onClick = {
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        selectedLevel = level
                                        when (level) {
                                            FocusGuardLevel.NORMAL -> {
                                                strictness = StrictnessLevel.GENTLE
                                                enableAod = false
                                                enableDnd = false
                                            }
                                            FocusGuardLevel.HIGH -> {
                                                strictness = StrictnessLevel.DEEP
                                                enableAod = true
                                                enableDnd = dndGranted
                                            }
                                            FocusGuardLevel.CUSTOM -> {
                                                strictness = guardSettings.strictnessLevel
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("focus_guard_level_${level.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = level.label,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = level.badge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = level.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isChosen) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Saved Presets
                if (uiState.strictFocusPresets.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Saved Presets",
                            subtitle = "Tap to load your favorite Focus Guard setup"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.strictFocusPresets.forEach { preset ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                                    onClick = {
                                        durationMinutes = preset.durationMinutes
                                        selectedSubjectId = preset.subjectId
                                        selectedActivity = preset.activityType
                                        strictness = preset.strictnessLevel
                                        breakPolicy = preset.breakPolicy
                                        enableDnd = preset.enableDnd && dndGranted
                                        enableAod = preset.enableAodDisplay
                                        allowedApps = preset.allowedAppsCsv
                                        selectedLevel = if (preset.enableAodDisplay) FocusGuardLevel.HIGH else FocusGuardLevel.CUSTOM
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${preset.name} · ${preset.durationMinutes}m",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { onDeletePreset(preset.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete preset ${preset.name}",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Duration, Activity & Subject Card
                item {
                    SanctuaryCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Session Duration", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(25, 30, 45, 50, 60, 90).forEach { mins ->
                                    FilterChip(
                                        selected = durationMinutes == mins,
                                        onClick = { durationMinutes = mins },
                                        label = { Text("${mins}m") }
                                    )
                                }
                            }

                            Text("Study Activity", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                focusFriendlyActivities.forEach { act ->
                                    FilterChip(
                                        selected = selectedActivity == act,
                                        onClick = { selectedActivity = act },
                                        label = { Text(act.shortLabel) }
                                    )
                                }
                            }

                            if (uiState.subjects.isNotEmpty()) {
                                Text("Subject", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.subjects.forEach { subject ->
                                        FilterChip(
                                            selected = selectedSubjectId == subject.id,
                                            onClick = { selectedSubjectId = subject.id },
                                            label = { Text(subject.name) }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = sessionGoal,
                                onValueChange = { sessionGoal = it.take(80) },
                                label = { Text("Single session intention (optional)") },
                                placeholder = { Text("e.g., Finish 10 integration problems") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Custom Focus Controls (shown for Custom Focus, compact summary otherwise)
                if (selectedLevel == FocusGuardLevel.CUSTOM) {
                    item {
                        SanctuaryCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Custom Focus Rules", style = MaterialTheme.typography.titleMedium)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StrictnessLevel.entries.forEach { level ->
                                        val isChosen = strictness == level
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isChosen) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            border = BorderStroke(
                                                if (isChosen) 1.5.dp else 1.dp,
                                                if (isChosen) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                                            ),
                                            onClick = {
                                                strictness = level
                                                if (level == StrictnessLevel.DEEP) enableAod = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = level.label,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = level.summary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isChosen) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Text("Break Policy", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BreakPolicy.entries.forEach { policy ->
                                        FilterChip(
                                            selected = breakPolicy == policy,
                                            onClick = { breakPolicy = policy },
                                            label = { Text(policy.label) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = allowedApps,
                                    onValueChange = { allowedApps = it.take(120) },
                                    label = { Text("Allowed study apps note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Voluntary DND & Pure Black AOD-Inspired Display Card
                item {
                    SanctuaryCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DoNotDisturbOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text("Do Not Disturb during focus", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (dndGranted) {
                                                "Emergency alerts, alarms & starred contacts stay allowed. Restores automatically."
                                            } else {
                                                "Optional Android permission required. Tap to grant access."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = enableDnd && dndGranted,
                                    onCheckedChange = { checked ->
                                        if (checked && !dndGranted) {
                                            onOpenDndSettings()
                                        } else {
                                            enableDnd = checked
                                        }
                                    },
                                    modifier = Modifier.testTag("strict_focus_dnd_switch")
                                )
                            }

                            if (!dndGranted) {
                                OutlinedButton(
                                    onClick = onOpenDndSettings,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Do Not Disturb Access in Settings")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DarkMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text("AOD-Inspired Pure Black Display", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Pure #000000 AMOLED focus screen with thin lavender ring & minimal controls.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = enableAod,
                                    onCheckedChange = { enableAod = it },
                                    modifier = Modifier.testTag("strict_focus_aod_switch")
                                )
                            }
                        }
                    }
                }

                // Start CTA
                item {
                    LiquidGlassButton(
                        onClick = {
                            if (uiState.preferences.hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onStartFocusGuard(
                                selectedSubjectId,
                                sessionGoal,
                                selectedActivity,
                                durationMinutes,
                                strictness,
                                enableDnd && dndGranted,
                                enableAod,
                                selectedLevel
                            )
                        },
                        cornerRadius = 18.dp,
                        height = 56.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_strict_focus_button")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start ${selectedLevel.label} · ${durationMinutes}m",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Focus Guard Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(28) },
                    label = { Text("Preset name (e.g., Exam Preparation)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSavePreset(
                                StrictFocusPreset(
                                    name = presetName.trim(),
                                    durationMinutes = durationMinutes,
                                    subjectId = selectedSubjectId,
                                    activityType = selectedActivity,
                                    dailyGoalMinutes = uiState.preferences.dailyTargetMinutes,
                                    breakPolicy = breakPolicy,
                                    strictnessLevel = strictness,
                                    enableDnd = enableDnd,
                                    enableAodDisplay = enableAod,
                                    allowedAppsCsv = allowedApps
                                )
                            )
                            presetName = ""
                            showSavePresetDialog = false
                        }
                    },
                    enabled = presetName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveStrictFocusView(
    uiState: BrainBufferUiState,
    subjectName: String,
    isPinned: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtendFiveMin: () -> Unit,
    onToggleAod: () -> Unit,
    onNeedPhone: () -> Unit,
    onReturnToStudy: () -> Unit,
    onDismissWarning: () -> Unit,
    onExit: () -> Unit
) {
    val timer = uiState.timer
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("active_strict_focus_view")
    ) {
        // Top minimal status & voluntary exit
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = buildString {
                            append(timer.focusGuardLevel.label)
                            if (timer.snapshot.strictFocusDndActive) append(" · DND")
                            if (isPinned) append(" · Pinned")
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onToggleAod,
                    modifier = Modifier.testTag("toggle_aod_display_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DarkMode,
                        contentDescription = "Switch to Pure Black AOD-inspired display",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onExit,
                    modifier = Modifier.testTag("exit_strict_focus_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit Focus Guard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Center distraction-free timer ring + optional warning banner
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.unapprovedAppWarning?.let { warning ->
                UnapprovedAppNoticePill(
                    warning = warning,
                    isPureBlack = false,
                    onOpenAccess = onNeedPhone,
                    onDismiss = onDismissWarning
                )
            }

            Text(
                text = "${timer.activeActivityLabel} · $subjectName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (timer.approvedAppLabel.isNotBlank()) {
                Text(
                    text = "Approved app: ${timer.approvedAppLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary
                )
            }

            Box(
                modifier = Modifier.size(256.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    drawArc(
                        color = primary.copy(alpha = 0.14f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = 360f * timer.progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timer.formattedTime,
                        style = BrainBufferThemeTokens.textStyles.timerDisplay,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("strict_focus_timer_text")
                    )
                    if (timer.sessionGoal.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = timer.sessionGoal,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 190.dp)
                        )
                    }
                }
            }
        }

        // Bottom minimal controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (timer.isSwitchedActivity) {
                LiquidGlassButton(
                    onClick = onReturnToStudy,
                    cornerRadius = 16.dp,
                    height = 50.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strict_return_to_study_button")
                ) {
                    Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Return to Study", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassButton(
                    onClick = { if (timer.isRunning) onPause() else onResume() },
                    cornerRadius = 16.dp,
                    height = 52.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (timer.isRunning) "Pause" else "Resume",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                LiquidGlassSecondaryButton(
                    onClick = onNeedPhone,
                    accentBorder = MaterialTheme.colorScheme.secondary,
                    cornerRadius = 16.dp,
                    height = 52.dp,
                    modifier = Modifier.testTag("strict_need_phone_button")
                ) {
                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Need Phone", style = MaterialTheme.typography.labelLarge)
                }

                LiquidGlassSecondaryButton(
                    onClick = onExtendFiveMin,
                    cornerRadius = 16.dp,
                    height = 52.dp
                ) {
                    Text("+5m", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Pure `#000000` AMOLED AOD-inspired High Focus Display (Part 2):
 * - Zero distractions, pure black background (`Color.Black`), minimal BRAIN BUFFER wordmark,
 *   small date/time, burn-in-safe subtle ±2dp drift per minute, razor-thin lavender ring,
 *   large high-legibility white timer, current subject & study activity, and essential controls
 *   (Need Phone, Pause/Resume, Exit).
 * - Never keeps the screen permanently awake (respects Android display timeout) and never
 *   attempts to replace the OS lock screen.
 */
@Composable
fun AodInspiredFocusDisplay(
    formattedTime: String,
    progress: Float,
    activityLabel: String,
    subjectName: String,
    isRunning: Boolean,
    dndActive: Boolean,
    onWakeToMinimal: () -> Unit,
    onTogglePause: () -> Unit,
    onExit: () -> Unit,
    guardLevelLabel: String = FocusGuardLevel.HIGH.label,
    isSwitchedActivity: Boolean = false,
    unapprovedWarning: UnapprovedAppWarning? = null,
    onNeedPhone: () -> Unit = onWakeToMinimal,
    onReturnToStudy: () -> Unit = {},
    onDismissWarning: () -> Unit = {}
) {
    val nowTime = remember(formattedTime) { LocalDateTime.now() }
    val nowStamp = remember(formattedTime) {
        nowTime.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault()))
    }
    // Gentle burn-in-safe pixel drift (±2dp shifting slowly each minute)
    val driftOffset = remember(nowTime.minute) {
        val pattern = listOf(0 to 0, 2 to -1, -2 to 1, 1 to 2, -1 to -2)
        val (dx, dy) = pattern[nowTime.minute % pattern.size]
        IntOffset(dx, dy)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("aod_inspired_focus_display")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = "Show focus controls") { onWakeToMinimal() }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp)
        ) {
        // Minimal BRAIN BUFFER logo/wordmark + Date & Time
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { driftOffset },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BRAIN BUFFER",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.6.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = LavenderLuminous.copy(alpha = 0.65f),
                modifier = Modifier.testTag("aod_brand_wordmark")
            )
            Text(
                text = "$nowStamp · $guardLevelLabel",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7B788A)
            )
        }

        // Pure black center with razor-thin lavender ring
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { driftOffset },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            unapprovedWarning?.let { warning ->
                UnapprovedAppNoticePill(
                    warning = warning,
                    isPureBlack = true,
                    onOpenAccess = onNeedPhone,
                    onDismiss = onDismissWarning
                )
            }

            Box(
                modifier = Modifier.size(252.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.75.dp.toPx()
                    drawArc(
                        color = LavenderLuminous.copy(alpha = 0.16f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = LavenderLuminous.copy(alpha = 0.90f),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        style = BrainBufferThemeTokens.textStyles.timerDisplay.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light
                        ),
                        color = Color(0xFFF5F4FA),
                        modifier = Modifier.testTag("aod_timer_text")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isRunning) activityLabel else "$activityLabel · Paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = LavenderLuminous.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (dndActive) "$subjectName · DND" else subjectName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7D7A8E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Low-contrast essential actions at bottom (Need Phone, Pause/Resume, Exit)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSwitchedActivity) {
                TextButton(
                    onClick = onReturnToStudy,
                    colors = ButtonDefaults.textButtonColors(contentColor = LavenderLuminous),
                    modifier = Modifier.testTag("aod_return_to_study_button")
                ) {
                    Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Return to Study")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onNeedPhone,
                    colors = ButtonDefaults.textButtonColors(contentColor = LavenderLuminous.copy(alpha = 0.86f)),
                    modifier = Modifier.testTag("aod_need_phone_button")
                ) {
                    Text("Need Phone")
                }
                TextButton(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF9C98B4))
                ) {
                    Text(if (isRunning) "Pause" else "Resume")
                }
                TextButton(
                    onClick = onExit,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8E8AA8))
                ) {
                    Text("Exit")
                }
            }
        }
        }
    }
}

@Composable
private fun UnapprovedAppNoticePill(
    warning: UnapprovedAppWarning,
    isPureBlack: Boolean,
    onOpenAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    LiquidGlassCard(
        tone = LiquidGlassTone.SUBTLE,
        cornerRadius = 16.dp,
        tintOverlay = if (isPureBlack) Color(0xFF14111E).copy(alpha = 0.40f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        borderAccent = LavenderLuminous.copy(alpha = 0.45f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .testTag("strict_unapproved_app_warning")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unapproved app: ${warning.appDisplayName}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPureBlack) Color(0xFFECEAF5) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPureBlack) Color(0xFF9C98B4) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenAccess) {
                    Text("Log", color = LavenderLuminous)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss app reminder",
                        tint = if (isPureBlack) Color(0xFF9C98B4) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
