package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.LocalAmbientSoundscapeController
import com.example.audio.SoundscapeState
import com.example.domain.DailyFocusGoalProgress
import com.example.domain.FocusTimerEngine
import com.example.model.BreakPlanConfig
import com.example.model.BreakPlanMode
import com.example.model.SelectedSessionApp
import com.example.model.SessionPurposeCategory
import com.example.model.TemporaryAppAccessSession
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AppLauncherHelper
import com.example.system.AppUsageAwarenessHelper
import com.example.system.InstalledAppChoice
import com.example.ui.components.FocusSoundscapePill
import com.example.ui.components.FocusSoundscapeSheet
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Purpose-Aware Focus Screen — the centre of BRAIN BUFFER.
 *
 * Setup state (!timer.hasStarted):
 *   "What are you using your phone for?" -> Purpose -> Session Name -> Duration ->
 *   Installed Allowed Apps (real app icons) -> Optional Break Plan -> Optional Sound ->
 *   Dominant Start Focus CTA.
 *
 * Active state (timer.hasStarted):
 *   Calm, distraction-free display with circular timer, honest protection status,
 *   allowed apps quick-launch, minimal Pause/Resume + Sound controls, and a
 *   three-dot menu containing EXACTLY 3 main actions:
 *   1. App Switch  2. Break  3. Exit
 */
@Composable
fun FocusScreen(
    uiState: BrainBufferUiState,
    onStartPurposeSession: (
        purposeCategory: SessionPurposeCategory,
        sessionName: String,
        durationMinutes: Int,
        selectedApps: List<SelectedSessionApp>,
        breakPlan: BreakPlanConfig,
        linkedTaskId: Long?
    ) -> Unit = { _, _, _, _, _, _ -> },
    onTogglePauseResume: () -> Unit = {},
    onStartTempAppSwitch: (packageName: String, appLabel: String, durationMinutes: Int) -> Unit = { _, _, _ -> },
    onRequestTempAccessExtension: (Int) -> Unit = {},
    onReturnToFocusFromTempAccess: () -> Unit = {},
    onStartBreak: (Int) -> Unit = {},
    onReturnFromBreak: () -> Unit = {},
    onFinishEarly: () -> Unit = {},
    onCancelSession: () -> Unit = {},
    onSwitchPurpose: (SessionPurposeCategory, String, List<SelectedSessionApp>) -> Unit = { _, _, _ -> },
    onDistractionAction: (packageName: String, appLabel: String, actionTaken: String) -> Unit = { _, _, _ -> },
    onDismissCelebration: () -> Unit = {},
    onClearTempAccessNotice: () -> Unit = {},
    openAppSwitchImmediately: Boolean = false,
    onAppSwitchRequestConsumed: () -> Unit = {},
    openChangePurposeImmediately: Boolean = false,
    onChangePurposeRequestConsumed: () -> Unit = {},
    onUpdateDailyTargetMinutes: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val adaptive = LocalAdaptiveMetrics.current
    val soundscapeController = LocalAmbientSoundscapeController.current
    val soundscapeState by soundscapeController.state.collectAsState()

    var installedApps by remember { mutableStateOf<List<InstalledAppChoice>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            AppLauncherHelper.queryLaunchableApps(context, limit = 100)
        }
    }

    var showSoundscapeSheet by remember { mutableStateOf(false) }
    var showAppSwitchSheet by remember { mutableStateOf(false) }
    var appSwitchPreselect by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showBreakSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showChangePurposeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openAppSwitchImmediately, uiState.timer.hasStarted) {
        if (openAppSwitchImmediately && uiState.timer.hasStarted) {
            showAppSwitchSheet = true
            onAppSwitchRequestConsumed()
        }
    }
    LaunchedEffect(openChangePurposeImmediately, uiState.timer.hasStarted) {
        if (openChangePurposeImmediately && uiState.timer.hasStarted) {
            showChangePurposeSheet = true
            onChangePurposeRequestConsumed()
        }
    }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = uiState.timer.hasStarted,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "focus_setup_vs_active"
            ) { isActiveSession ->
                if (!isActiveSession) {
                    FocusSetupContent(
                        uiState = uiState,
                        installedApps = installedApps,
                        soundscapeState = soundscapeState,
                        onOpenSoundscape = { showSoundscapeSheet = true },
                        onOpenUsageSettings = { AppUsageAwarenessHelper.openUsageAccessSettings(context) },
                        onStartPurposeSession = onStartPurposeSession,
                        onUpdateDailyTargetMinutes = onUpdateDailyTargetMinutes,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = adaptive.maxContentWidth)
                    )
                } else {
                    ActiveFocusSessionContent(
                        uiState = uiState,
                        soundscapeState = soundscapeState,
                        onOpenSoundscape = { showSoundscapeSheet = true },
                        onTogglePauseResume = onTogglePauseResume,
                        onOpenAppSwitch = { pkg, label ->
                            appSwitchPreselect = if (!pkg.isNullOrBlank()) pkg to label.orEmpty() else null
                            showAppSwitchSheet = true
                        },
                        onOpenBreak = { showBreakSheet = true },
                        onOpenExit = { showExitDialog = true },
                        onOpenChangePurpose = { showChangePurposeSheet = true },
                        onReturnFromBreak = onReturnFromBreak,
                        onReturnFromTempAccess = onReturnToFocusFromTempAccess,
                        onLaunchAllowedApp = { pkg -> AppLauncherHelper.launchApp(context, pkg) },
                        onOpenUsageSettings = { AppUsageAwarenessHelper.openUsageAccessSettings(context) },
                        onDistractionReturn = { pkg, label ->
                            onDistractionAction(pkg, label, "RETURN_TO_FOCUS")
                        },
                        onClearNotice = onClearTempAccessNotice,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = adaptive.maxContentWidth)
                    )
                }
            }
        }
    }

    if (showSoundscapeSheet) {
        FocusSoundscapeSheet(
            state = soundscapeState,
            onSelectSoundscape = { soundscapeController.selectSoundscape(it) },
            onTogglePlayPause = { soundscapeController.togglePlayPause() },
            onVolumeChange = { soundscapeController.setVolume(it) },
            onSyncWithTimerChange = { soundscapeController.setSyncWithTimer(it) },
            onDismiss = { showSoundscapeSheet = false }
        )
    }

    if (showAppSwitchSheet && uiState.timer.hasStarted) {
        AppSwitchSheet(
            installedApps = installedApps,
            preselectedPackage = appSwitchPreselect?.first,
            preselectedLabel = appSwitchPreselect?.second,
            onConfirmSwitch = { pkg, label, mins ->
                showAppSwitchSheet = false
                appSwitchPreselect = null
                onStartTempAppSwitch(pkg, label, mins)
            },
            onDismiss = {
                showAppSwitchSheet = false
                appSwitchPreselect = null
            }
        )
    }

    if (showBreakSheet && uiState.timer.hasStarted) {
        BreakPickerSheet(
            defaultBreakMinutes = uiState.preferences.focusGuardSettings.defaultBreakMinutes,
            onStartBreak = { mins ->
                showBreakSheet = false
                onStartBreak(mins)
            },
            onDismiss = { showBreakSheet = false }
        )
    }

    if (showExitDialog && uiState.timer.hasStarted) {
        val exitPreviewSnap = remember(
            uiState.timer.snapshot,
            uiState.activeTempAccess,
            uiState.nowEpochMs
        ) {
            FocusTimerEngine.resolveParentForEarlyExit(
                snapshot = uiState.timer.snapshot,
                activeTempAccess = uiState.activeTempAccess,
                nowEpochMs = uiState.nowEpochMs,
                nowElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
            )
        }
        val previewElapsedSec = FocusTimerEngine.actualFocusedSeconds(
            snapshot = exitPreviewSnap,
            nowEpochMs = uiState.nowEpochMs,
            nowElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
        )
        ExitSessionConfirmationDialog(
            sessionName = exitPreviewSnap.sessionGoal.ifBlank { exitPreviewSnap.purposeCategory.label },
            elapsedFormatted = BrainBufferNotificationManager.formatMinSec(previewElapsedSec),
            plannedMinutes = (exitPreviewSnap.totalSeconds / 60).coerceAtLeast(1),
            onFinishEarly = {
                showExitDialog = false
                onFinishEarly()
            },
            onCancelSession = {
                showExitDialog = false
                onCancelSession()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    if (showChangePurposeSheet && uiState.timer.hasStarted) {
        val currentAllowed = remember(uiState.timer.snapshot.allowedPackages, uiState.timer.snapshot.allowedAppLabels) {
            val pkgs = uiState.timer.snapshot.allowedPackages.toList()
            val labels = uiState.timer.snapshot.allowedAppLabels
            pkgs.mapIndexed { idx, pkg ->
                SelectedSessionApp(pkg, labels.getOrElse(idx) { pkg.substringAfterLast('.') })
            }
        }
        ChangePurposeSheet(
            currentPurpose = uiState.timer.snapshot.purposeCategory,
            currentSessionName = uiState.timer.sessionGoal,
            currentAllowedApps = currentAllowed,
            installedApps = installedApps,
            onConfirmChange = { newPurpose, newName, newApps ->
                showChangePurposeSheet = false
                onSwitchPurpose(newPurpose, newName, newApps)
            },
            onDismiss = { showChangePurposeSheet = false }
        )
    }

    // Expired temporary access dialog ("Time is up. Return to your focus session?")
    val expiredTemp = uiState.expiredTempAccessPrompt
        ?: uiState.activeTempAccess?.takeIf { it.isExpired(uiState.nowEpochMs) && it.endedAtEpochMs == null }
    if (expiredTemp != null) {
        TemporaryAccessExpiredDialog(
            session = expiredTemp,
            onSwitchToFocus = onReturnToFocusFromTempAccess,
            onRequestExtension = onRequestTempAccessExtension
        )
    }

    // Natural completion celebration (ONLY when timer naturally hits 00:00)
    val celebration = uiState.pendingCelebration
    if (celebration != null) {
        PurposeCompletionDialog(
            celebration = celebration,
            onDismiss = onDismissCelebration,
            onStartBreak = onStartBreak
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetupContent(
    uiState: BrainBufferUiState,
    installedApps: List<InstalledAppChoice>,
    soundscapeState: SoundscapeState,
    onOpenSoundscape: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onStartPurposeSession: (
        SessionPurposeCategory,
        String,
        Int,
        List<SelectedSessionApp>,
        BreakPlanConfig,
        Long?
    ) -> Unit,
    onUpdateDailyTargetMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultPurpose = uiState.preselectedPurpose ?: uiState.preferences.focusGuardSettings.defaultPurposeCategory
    val initialLinkedTaskId = uiState.timer.snapshot.linkedTaskId
    val initialLinkedTaskTitle = remember(initialLinkedTaskId, uiState.tasks) {
        initialLinkedTaskId?.let { id -> uiState.tasks.firstOrNull { it.id == id }?.title }
    }
    var selectedPurpose by remember(defaultPurpose) { mutableStateOf(defaultPurpose) }
    var sessionName by remember(defaultPurpose, initialLinkedTaskId) {
        mutableStateOf(initialLinkedTaskTitle?.takeIf { it.isNotBlank() } ?: defaultPurpose.defaultSessionPlaceholder)
    }
    var durationMinutes by remember(uiState.preferences.preferredFocusMinutes) {
        mutableIntStateOf(uiState.preferences.preferredFocusMinutes.coerceIn(5, 180))
    }
    var selectedApps by remember { mutableStateOf<List<SelectedSessionApp>>(emptyList()) }
    var breakMode by remember(uiState.preferences.focusGuardSettings.defaultBreakPlanMode) {
        mutableStateOf(uiState.preferences.focusGuardSettings.defaultBreakPlanMode)
    }
    var breakMinutes by remember(uiState.preferences.focusGuardSettings.defaultBreakMinutes) {
        mutableIntStateOf(uiState.preferences.focusGuardSettings.defaultBreakMinutes.coerceIn(3, 20))
    }
    var linkedTaskId by remember(initialLinkedTaskId) { mutableStateOf(initialLinkedTaskId) }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    val durationPresets = listOf(15, 25, 45, 60, 90)
    val openTasks = remember(uiState.tasks) { uiState.tasks.filter { !it.isCompleted }.take(6) }
    val isFormValid = sessionName.trim().isNotEmpty() && durationMinutes >= 1

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "INTENTIONAL FOCUS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "What are you using your phone for?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("focus_setup_headline")
                )
                Text(
                    text = "Declare your purpose and allowed apps so BRAIN BUFFER can gently help you stay aligned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Purpose Category Selector
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Purpose",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionPurposeCategory.entries.forEach { category ->
                            val isSelected = selectedPurpose == category
                            LiquidGlassChip(
                                label = category.label,
                                selected = isSelected,
                                onClick = {
                                    selectedPurpose = category
                                    if (sessionName.isBlank() || SessionPurposeCategory.entries.any {
                                            it.defaultSessionPlaceholder == sessionName
                                        }
                                    ) {
                                        sessionName = category.defaultSessionPlaceholder
                                    }
                                },
                                modifier = Modifier.testTag("purpose_chip_${category.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = selectedPurpose.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Session Name / Specific Intention
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "2. Session name",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = { sessionName = it },
                        placeholder = { Text(selectedPurpose.defaultSessionPlaceholder) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("focus_session_name_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedPurpose.suggestedSessionNames) { suggestion ->
                            LiquidGlassChip(
                                label = suggestion,
                                selected = sessionName == suggestion,
                                onClick = { sessionName = suggestion }
                            )
                        }
                    }
                }
            }
        }

        // 3. Duration
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Duration",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { durationMinutes = (durationMinutes - 5).coerceAtLeast(5) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("focus_duration_minus")
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease duration by 5 minutes")
                            }
                            Text(
                                text = "${durationMinutes}m",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { durationMinutes = (durationMinutes + 5).coerceAtMost(180) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("focus_duration_plus")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Increase duration by 5 minutes")
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durationPresets.forEach { preset ->
                            LiquidGlassChip(
                                label = "${preset}m",
                                selected = durationMinutes == preset,
                                onClick = { durationMinutes = preset },
                                modifier = Modifier.testTag("duration_preset_${preset}m")
                            )
                        }
                    }
                }
            }
        }

        // 4. Allowed Apps Picker (Real Installed Apps + Icons)
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "4. Allowed apps",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedApps.isEmpty()) {
                                    "Choose apps needed for ${selectedPurpose.label.lowercase()} (or leave empty for phone-free focus)."
                                } else {
                                    "${selectedApps.size} app${if (selectedApps.size == 1) "" else "s"} approved for this session."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showAppPickerSheet = true },
                            modifier = Modifier.testTag("open_allowed_apps_picker_button")
                        ) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (selectedApps.isEmpty()) "Select" else "Edit")
                        }
                    }

                    if (selectedApps.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(selectedApps, key = { "picked_${it.packageName}" }) { app ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable {
                                        selectedApps = selectedApps.filterNot { it.packageName == app.packageName }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppIconBadge(
                                            packageName = app.packageName,
                                            appLabel = app.appLabel,
                                            size = 24.dp
                                        )
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Honest Android protection notice
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (uiState.hasUsageAccessPermission) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.hasUsageAccessPermission) Icons.Rounded.Shield else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (uiState.hasUsageAccessPermission) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (uiState.hasUsageAccessPermission) {
                                    "Usage Access enabled · App-level matching active (BRAIN BUFFER never reads in-app content)."
                                } else {
                                    "Grant Usage Access so BRAIN BUFFER can gently nudge you if you switch to an unapproved app."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (!uiState.hasUsageAccessPermission) {
                                TextButton(onClick = onOpenUsageSettings) {
                                    Text("Enable")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Optional Break Plan & Ambient Sound
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Optional · Break plan & sound",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BreakPlanMode.entries.forEach { mode ->
                            LiquidGlassChip(
                                label = mode.label,
                                selected = breakMode == mode,
                                onClick = { breakMode = mode },
                                modifier = Modifier.testTag("break_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    if (breakMode != BreakPlanMode.NONE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Break length:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(5, 10, 15).forEach { mins ->
                                LiquidGlassChip(
                                    label = "${mins}m",
                                    selected = breakMinutes == mins,
                                    onClick = { breakMinutes = mins }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FocusSoundscapePill(
                            state = soundscapeState,
                            onClick = onOpenSoundscape
                        )
                        Text(
                            text = "Daily goal: ${DailyFocusGoalProgress.formatGoalDuration(uiState.preferences.dailyTargetMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (openTasks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Link a task (optional)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(openTasks, key = { "link_task_${it.id}" }) { task ->
                                    val selected = linkedTaskId == task.id
                                    LiquidGlassChip(
                                        label = task.title,
                                        selected = selected,
                                        onClick = {
                                            linkedTaskId = if (selected) null else task.id
                                            if (!selected && sessionName == selectedPurpose.defaultSessionPlaceholder) {
                                                sessionName = task.title
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dominant Start Focus CTA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidGlassButton(
                    onClick = {
                        onStartPurposeSession(
                            selectedPurpose,
                            sessionName.trim(),
                            durationMinutes,
                            selectedApps,
                            BreakPlanConfig(mode = breakMode, breakDurationMinutes = breakMinutes),
                            linkedTaskId
                        )
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_focus_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start ${selectedPurpose.shortLabel} Focus · ${durationMinutes}m",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                if (!isFormValid) {
                    Text(
                        text = "Enter a session name to start focusing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showAppPickerSheet) {
        InstalledAppMultiPickerSheet(
            installedApps = installedApps,
            initialSelected = selectedApps,
            onConfirm = { picked ->
                selectedApps = picked
                showAppPickerSheet = false
            },
            onDismiss = { showAppPickerSheet = false }
        )
    }
}

@Composable
private fun ActiveFocusSessionContent(
    uiState: BrainBufferUiState,
    soundscapeState: SoundscapeState,
    onOpenSoundscape: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onOpenAppSwitch: (String?, String?) -> Unit,
    onOpenBreak: () -> Unit,
    onOpenExit: () -> Unit,
    onOpenChangePurpose: () -> Unit,
    onReturnFromBreak: () -> Unit,
    onReturnFromTempAccess: () -> Unit,
    onLaunchAllowedApp: (String) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onDistractionReturn: (String, String) -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timer = uiState.timer
    val snapshot = timer.snapshot
    val isBreak = snapshot.isOnBreak
    val activeTemp: TemporaryAppAccessSession? = uiState.activeTempAccess?.takeIf {
        it.endedAtEpochMs == null && !it.isExpired(uiState.nowEpochMs)
    }
    var overflowExpanded by remember { mutableStateOf(false) }

    val purposeAccent = remember(snapshot.purposeCategory, isBreak) {
        if (isBreak) Color(0xFF10B981) else Color(snapshot.purposeCategory.accentHex)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = timer.progress.coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "active_timer_ring"
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header with Purpose Badge + EXACT 3-Action Overflow Menu
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = purposeAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (isBreak) "BREAK" else snapshot.purposeCategory.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = purposeAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (activeTemp != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "TEMP ACCESS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBreak) {
                            "Break time · Focus paused"
                        } else {
                            snapshot.sessionGoal.ifBlank { snapshot.purposeCategory.label }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("active_focus_session_title")
                    )
                }

                // Three-dot menu with EXACTLY 3 main actions: App Switch, Break, Exit
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.testTag("active_focus_overflow_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Session options"
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("App Switch") },
                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onOpenAppSwitch(null, null)
                            },
                            modifier = Modifier.testTag("overflow_action_app_switch")
                        )
                        DropdownMenuItem(
                            text = { Text("Break") },
                            leadingIcon = { Icon(Icons.Rounded.Coffee, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onOpenBreak()
                            },
                            modifier = Modifier.testTag("overflow_action_break")
                        )
                        DropdownMenuItem(
                            text = { Text("Exit") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.ExitToApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onOpenExit()
                            },
                            modifier = Modifier.testTag("overflow_action_exit")
                        )
                    }
                }
            }
        }

        // Notice Banner (e.g., extension limit reached or purpose updated)
        if (!uiState.tempAccessNotice.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.tempAccessNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearNotice) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // Active Temporary Access Card (Focus paused, tracked separately)
        if (activeTemp != null) {
            item {
                val tempRemSec = activeTemp.remainingSeconds(uiState.nowEpochMs)
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_temp_access_card"),
                    tone = LiquidGlassTone.ACCENT,
                    cornerRadius = 24.dp,
                    borderAccent = MaterialTheme.colorScheme.tertiary,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconBadge(
                                packageName = activeTemp.packageName,
                                appLabel = activeTemp.appLabel,
                                size = 42.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Temporary access · ${activeTemp.appLabel}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Focus timer is paused. Temporary time is never counted as focus.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = BrainBufferNotificationManager.formatMinSec(tempRemSec),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidGlassSecondaryButton(
                                onClick = { onLaunchAllowedApp(activeTemp.packageName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open ${activeTemp.appLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            LiquidGlassButton(
                                onClick = onReturnFromTempAccess,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("return_to_focus_from_temp_button")
                            ) {
                                Text("Switch to Focus")
                            }
                        }
                    }
                }
            }
        }

        // Calm Distraction Intervention Banner (if mismatch detected while app open)
        val warning = uiState.unapprovedAppWarning
        if (warning != null && !isBreak && activeTemp == null) {
            item {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("distraction_intervention_card"),
                    tone = LiquidGlassTone.ACCENT,
                    cornerRadius = 22.dp,
                    borderAccent = MaterialTheme.colorScheme.error,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "You're in ${snapshot.purposeCategory.label} mode, but ${warning.appDisplayName} isn't in your allowed list.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassButton(
                                onClick = { onDistractionReturn(warning.packageName, warning.appDisplayName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Return to Focus", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onOpenAppSwitch(warning.packageName, warning.appDisplayName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Temp allow", maxLines = 1)
                            }
                        }
                        TextButton(
                            onClick = onOpenChangePurpose,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Change purpose")
                        }
                    }
                }
            }
        }

        // Centerpiece Circular Countdown Ring + Adaptive Landscape/Tablet Side-by-Side Layout
        item {
            val adaptive = LocalAdaptiveMetrics.current
            val useSplitLayout = adaptive.useCompactLandscapeSplit || adaptive.useTwoColumnLayout
            val ringSize = if (adaptive.isCompactHeight) 210.dp else 258.dp
            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)

            val ringContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    purposeAccent,
                                    purposeAccent.copy(alpha = 0.65f),
                                    purposeAccent
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = (animatedProgress * 360f).coerceIn(0f, 360f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                isBreak -> "BREAK REMAINING"
                                activeTemp != null -> "FOCUS PAUSED"
                                timer.isRunning -> "REMAINING"
                                else -> "PAUSED"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = purposeAccent
                        )
                        Text(
                            text = timer.formattedTime,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("active_focus_countdown")
                        )
                        Text(
                            text = "Elapsed ${timer.formattedElapsedTime} · ${(timer.totalSeconds / 60).coerceAtLeast(1)}m planned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val statusAndControls: @Composable () -> Unit = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.hasUsageAccessPermission, onClick = onOpenUsageSettings)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.hasUsageAccessPermission) Icons.Rounded.Shield else Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = if (uiState.hasUsageAccessPermission) purposeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when {
                                    isBreak -> "Break active · Distraction alerts paused until you return."
                                    activeTemp != null -> "Temporary access active · Monitoring returns when you switch back."
                                    uiState.hasUsageAccessPermission -> "App-level matching active · Only allowed apps + essential system apps."
                                    else -> "Timer-only mode · Tap to grant Usage Access for app-level distraction nudges."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isBreak) {
                        LiquidGlassButton(
                            onClick = onReturnFromBreak,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("return_from_break_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Return to Focus Early", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassButton(
                                onClick = onTogglePauseResume,
                                enabled = activeTemp == null,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(54.dp)
                                    .testTag("active_focus_pause_resume_button")
                            ) {
                                Icon(
                                    imageVector = if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (timer.isRunning) "Pause" else "Resume",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            FocusSoundscapePill(
                                state = soundscapeState,
                                onClick = onOpenSoundscape
                            )
                        }
                    }
                }
            }

            if (useSplitLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ringContent()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        statusAndControls()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ringContent()
                    statusAndControls()
                }
            }
        }

        // Allowed Apps Quick-Launch Row
        if (!isBreak) {
            item {
                val allowedPackages = snapshot.allowedPackages.toList()
                val allowedLabels = snapshot.allowedAppLabels
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Allowed apps",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = onOpenChangePurpose,
                                modifier = Modifier.testTag("active_change_purpose_button")
                            ) {
                                Text("Change purpose")
                            }
                        }

                        if (allowedPackages.isEmpty()) {
                            Text(
                                text = "Phone-free focus — no extra apps selected. Use the ⋮ menu for App Switch if you need a timed app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(allowedPackages.indices.toList()) { idx ->
                                    val pkg = allowedPackages[idx]
                                    val label = allowedLabels.getOrElse(idx) { pkg.substringAfterLast('.') }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .clickable { onLaunchAllowedApp(pkg) }
                                            .testTag("allowed_app_launch_$pkg")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AppIconBadge(packageName = pkg, appLabel = label, size = 24.dp)
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
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
