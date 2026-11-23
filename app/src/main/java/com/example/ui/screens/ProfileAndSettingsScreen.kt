package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import com.example.audio.LocalAmbientSoundscapeController
import com.example.domain.DailyFocusGoalProgress
import com.example.domain.IntentRealityAnalyticsEngine
import com.example.model.BreakPlanMode
import com.example.model.FocusGuardSettings
import com.example.model.InsightsRangeFilter
import com.example.model.NotificationPreferences
import com.example.model.SessionPurposeCategory
import com.example.model.ThemeMode
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AppUsageAwarenessHelper
import com.example.system.BrainBufferHaptics
import com.example.system.DndFocusController
import com.example.system.HapticEvent
import com.example.ui.components.FocusSoundscapePill
import com.example.ui.components.FocusSoundscapeSheet
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState

/**
 * Top-level Settings & Profile Tab (Destination 4 of BRAIN BUFFER).
 *
 * Organized cleanly into the exact 8 essential sections:
 * 1. Appearance (Light / Dark AMOLED / System, Reduced motion)
 * 2. Focus defaults (Default purpose, duration, break plan, daily goal)
 * 3. Intervention preferences (Grace period, cooldown, distraction nudges, unlock check-in)
 * 4. Sound (Ambient soundscapes + sound/haptics toggles)
 * 5. Permissions (Live Android Usage Access, Notifications, Do Not Disturb)
 * 6. Notifications (Master, Live timer, Break reminders, Lock-screen privacy)
 * 7. Privacy and data (Package-level privacy guarantee, Export JSON, Clear data, Replay onboarding)
 * 8. Account (Guest mode or optional Supabase account, Sync, Sign Out, Delete Account)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    uiState: BrainBufferUiState,
    onUpdateTheme: (ThemeMode) -> Unit = {},
    onUpdateFocusGuardSettings: (FocusGuardSettings) -> Unit = {},
    onUpdateTimerAndTarget: (focusMin: Int, shortBreakMin: Int, longBreakMin: Int, dailyTargetMin: Int) -> Unit = { _, _, _, _ -> },
    onUpdateAppSettings: (
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit = {},
    onUpdateAppUsageAwareness: (Boolean) -> Unit = {},
    onExportStudyBackup: () -> String = { "" },
    onClearAllData: () -> Unit = {},
    onResetOnboarding: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSignOut: (Boolean) -> Unit = {},
    onDeleteAccount: ((Boolean, String) -> Unit) -> Unit = { cb -> cb(false, "Not connected") }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adaptive = LocalAdaptiveMetrics.current
    val soundscapeController = LocalAmbientSoundscapeController.current
    val soundscapeState by soundscapeController.state.collectAsState()

    val prefs = uiState.preferences
    val fg = prefs.focusGuardSettings
    val notif = prefs.notificationPrefs
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
    val todayGoalProgress = remember(todaySummary.approvedFocusMinutes, prefs.dailyTargetMinutes) {
        IntentRealityAnalyticsEngine.computeDailyGoalProgress(
            focusedMinutesToday = todaySummary.approvedFocusMinutes,
            dailyTargetMinutes = prefs.dailyTargetMinutes
        )
    }

    var hasUsageAccess by remember { mutableStateOf(AppUsageAwarenessHelper.hasUsageStatsPermission(context)) }
    var hasNotifications by remember { mutableStateOf(BrainBufferNotificationManager.hasNotificationPermission(context)) }
    var hasDndAccess by remember { mutableStateOf(DndFocusController.isAccessGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = AppUsageAwarenessHelper.hasUsageStatsPermission(context)
                hasNotifications = BrainBufferNotificationManager.hasNotificationPermission(context)
                hasDndAccess = DndFocusController.isAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSoundscapeSheet by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

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
                    .testTag("profile_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top-level Settings Header
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "SETTINGS & PROFILE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prefs.displayName.ifBlank { "BRAIN BUFFER Settings" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${prefs.userRole.label} · ${if (uiState.cloudSync.session.isSignedIn) uiState.cloudSync.session.email else "Guest (local-first)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!feedbackBanner.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = feedbackBanner!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { feedbackBanner = null }) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                // 1. Appearance
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Palette,
                        title = "1. Appearance",
                        tag = "settings_section_appearance"
                    ) {
                        val themeOptions = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
                        LiquidGlassSegmentedControl(
                            items = themeOptions,
                            selectedItem = prefs.themeMode,
                            onSelect = onUpdateTheme,
                            labelProvider = { mode ->
                                when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark AMOLED"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_theme_selector")
                        )
                        SettingsToggleRow(
                            title = "Reduce motion",
                            subtitle = "Minimize animations for calmer transitions",
                            checked = prefs.reducedMotionOverride,
                            onCheckedChange = { reduced ->
                                onUpdateAppSettings(
                                    prefs.notificationsEnabled,
                                    prefs.breakRemindersEnabled,
                                    prefs.soundEnabled,
                                    prefs.hapticsEnabled,
                                    reduced
                                )
                            }
                        )
                    }
                }

                // 2. Focus defaults
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Timer,
                        title = "2. Focus defaults",
                        tag = "settings_section_focus_defaults"
                    ) {
                        Text(
                            text = "Default purpose",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionPurposeCategory.entries.forEach { cat ->
                                LiquidGlassChip(
                                    label = cat.shortLabel,
                                    selected = fg.defaultPurposeCategory == cat,
                                    onClick = {
                                        onUpdateFocusGuardSettings(fg.copy(defaultPurposeCategory = cat))
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Default session duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 25, 45, 60).forEach { mins ->
                                LiquidGlassChip(
                                    label = "${mins}m",
                                    selected = prefs.preferredFocusMinutes == mins,
                                    onClick = {
                                        onUpdateTimerAndTarget(
                                            mins,
                                            prefs.shortBreakMinutes,
                                            prefs.longBreakMinutes,
                                            prefs.dailyTargetMinutes
                                        )
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Default break plan",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BreakPlanMode.entries.forEach { mode ->
                                LiquidGlassChip(
                                    label = mode.label,
                                    selected = fg.defaultBreakPlanMode == mode,
                                    onClick = {
                                        onUpdateFocusGuardSettings(fg.copy(defaultBreakPlanMode = mode))
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

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
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Daily focus goal",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tracked on your Home screen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.testTag("settings_daily_goal_value")
                            ) {
                                Text(
                                    text = todayGoalProgress.formattedTarget,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Today's real focus progress preview against the selected target
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_daily_goal_preview")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Today: ${todayGoalProgress.formattedFocused} / ${todayGoalProgress.formattedTarget}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (todayGoalProgress.isGoalReached) "Goal Reached" else "${todayGoalProgress.percentComplete}%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (todayGoalProgress.isGoalReached) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { todayGoalProgress.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = if (todayGoalProgress.isGoalReached) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30, 60, 90, 120, 180).forEach { goal ->
                                LiquidGlassChip(
                                    label = DailyFocusGoalProgress.formatGoalDuration(goal),
                                    selected = prefs.dailyTargetMinutes == goal,
                                    onClick = {
                                        BrainBufferHaptics.perform(
                                            context = context,
                                            event = HapticEvent.STEP_TICK,
                                            enabled = prefs.hapticsEnabled
                                        )
                                        onUpdateTimerAndTarget(
                                            prefs.preferredFocusMinutes,
                                            prefs.shortBreakMinutes,
                                            prefs.longBreakMinutes,
                                            goal
                                        )
                                    },
                                    modifier = Modifier.testTag("settings_daily_goal_preset_$goal")
                                )
                            }
                        }

                        // Precision custom target stepper (15m to 8h in 15m increments)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val decreased = (prefs.dailyTargetMinutes - DailyFocusGoalProgress.STEP_MINUTES)
                                        .coerceAtLeast(DailyFocusGoalProgress.MIN_GOAL_MINUTES)
                                    BrainBufferHaptics.perform(
                                        context = context,
                                        event = HapticEvent.STEP_TICK,
                                        enabled = prefs.hapticsEnabled
                                    )
                                    onUpdateTimerAndTarget(
                                        prefs.preferredFocusMinutes,
                                        prefs.shortBreakMinutes,
                                        prefs.longBreakMinutes,
                                        decreased
                                    )
                                },
                                enabled = prefs.dailyTargetMinutes > DailyFocusGoalProgress.MIN_GOAL_MINUTES,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_daily_goal_minus")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease daily focus goal by 15 minutes",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("15m")
                            }

                            OutlinedButton(
                                onClick = {
                                    val increased = (prefs.dailyTargetMinutes + DailyFocusGoalProgress.STEP_MINUTES)
                                        .coerceAtMost(DailyFocusGoalProgress.MAX_GOAL_MINUTES)
                                    BrainBufferHaptics.perform(
                                        context = context,
                                        event = HapticEvent.STEP_TICK,
                                        enabled = prefs.hapticsEnabled
                                    )
                                    onUpdateTimerAndTarget(
                                        prefs.preferredFocusMinutes,
                                        prefs.shortBreakMinutes,
                                        prefs.longBreakMinutes,
                                        increased
                                    )
                                },
                                enabled = prefs.dailyTargetMinutes < DailyFocusGoalProgress.MAX_GOAL_MINUTES,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_daily_goal_plus")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase daily focus goal by 15 minutes",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("15m")
                            }
                        }
                    }
                }

                // 3. Intervention preferences
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Tune,
                        title = "3. Intervention preferences",
                        tag = "settings_section_interventions"
                    ) {
                        SettingsToggleRow(
                            title = "Unapproved app nudges",
                            subtitle = "Gently alert when switching outside your allowed apps",
                            checked = fg.distractionWarningsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateFocusGuardSettings(fg.copy(distractionWarningsEnabled = enabled))
                                onUpdateAppUsageAwareness(enabled)
                            }
                        )
                        Text(
                            text = "Grace period before reminder",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10, 15, 30).forEach { sec ->
                                LiquidGlassChip(
                                    label = "${sec}s",
                                    selected = fg.gracePeriodSeconds == sec,
                                    onClick = {
                                        onUpdateFocusGuardSettings(fg.copy(gracePeriodSeconds = sec))
                                    }
                                )
                            }
                        }
                        Text(
                            text = "Cooldown between alerts for same app",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30, 60, 90).forEach { sec ->
                                LiquidGlassChip(
                                    label = "${sec}s",
                                    selected = fg.distractionCooldownSeconds == sec,
                                    onClick = {
                                        onUpdateFocusGuardSettings(fg.copy(distractionCooldownSeconds = sec))
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Sound
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = "4. Sound",
                        tag = "settings_section_sound"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ambient focus soundscape",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Rain, Brown noise, Pink noise, Gentle ambience, or Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FocusSoundscapePill(
                                state = soundscapeState,
                                onClick = { showSoundscapeSheet = true }
                            )
                        }
                        SettingsToggleRow(
                            title = "Alert sounds",
                            subtitle = "Play soft tones for session end and temporary access expiry",
                            checked = prefs.soundEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateAppSettings(
                                    prefs.notificationsEnabled,
                                    prefs.breakRemindersEnabled,
                                    enabled,
                                    prefs.hapticsEnabled,
                                    prefs.reducedMotionOverride
                                )
                            }
                        )
                        SettingsToggleRow(
                            title = "Haptic feedback",
                            subtitle = "Tactile pulses on session start, completion, and distraction alerts",
                            checked = prefs.hapticsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    BrainBufferHaptics.perform(
                                        context = context,
                                        event = HapticEvent.SESSION_START,
                                        enabled = true
                                    )
                                }
                                onUpdateAppSettings(
                                    prefs.notificationsEnabled,
                                    prefs.breakRemindersEnabled,
                                    prefs.soundEnabled,
                                    enabled,
                                    prefs.reducedMotionOverride
                                )
                            }
                        )
                    }
                }

                // 5. Permissions
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Shield,
                        title = "5. Permissions",
                        tag = "settings_section_permissions"
                    ) {
                        PermissionStatusRow(
                            title = "Usage Access",
                            subtitle = "App-level matching only. Never inspects in-app content.",
                            granted = hasUsageAccess,
                            onOpen = { AppUsageAwarenessHelper.openUsageAccessSettings(context) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        PermissionStatusRow(
                            title = "Notifications",
                            subtitle = "Live focus timer & temporary access expiry reminders.",
                            granted = hasNotifications,
                            onOpen = { BrainBufferNotificationManager.openAppNotificationSettings(context) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        PermissionStatusRow(
                            title = "Do Not Disturb (optional)",
                            subtitle = "Silence incoming notifications during focus sessions.",
                            granted = hasDndAccess,
                            onOpen = { DndFocusController.openDndAccessSettings(context) }
                        )
                    }
                }

                // 6. Notifications
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Notifications,
                        title = "6. Notifications",
                        tag = "settings_section_notifications"
                    ) {
                        SettingsToggleRow(
                            title = "Enable notifications",
                            subtitle = "Master switch for all BRAIN BUFFER alerts",
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateAppSettings(
                                    enabled,
                                    prefs.breakRemindersEnabled,
                                    prefs.soundEnabled,
                                    prefs.hapticsEnabled,
                                    prefs.reducedMotionOverride
                                )
                            }
                        )
                        SettingsToggleRow(
                            title = "Live focus countdown notification",
                            subtitle = "Show ongoing session progress in the status shade",
                            checked = notif.activeFocusEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateNotificationPrefs(notif.copy(activeFocusEnabled = enabled))
                            }
                        )
                        SettingsToggleRow(
                            title = "Break & completion reminders",
                            subtitle = "Notify when a break or focus session finishes",
                            checked = notif.breakRemindersEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateNotificationPrefs(
                                    notif.copy(
                                        breakRemindersEnabled = enabled,
                                        completedSessionsEnabled = enabled
                                    )
                                )
                            }
                        )
                        SettingsToggleRow(
                            title = "Hide session names on lock screen",
                            subtitle = "Keep personal session titles private when phone is locked",
                            checked = notif.hidePrivateNotesOnLockScreen,
                            onCheckedChange = { enabled ->
                                onUpdateNotificationPrefs(notif.copy(hidePrivateNotesOnLockScreen = enabled))
                            }
                        )
                    }
                }

                // 7. Privacy and data
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "7. Privacy and data",
                        tag = "settings_section_privacy"
                    ) {
                        Text(
                            text = "Privacy guarantee: BRAIN BUFFER only compares foreground app package names against your chosen allowed apps. It never reads messages, videos, websites, keystrokes, or screen content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidGlassSecondaryButton(
                                onClick = {
                                    val json = onExportStudyBackup()
                                    if (json.isNotBlank()) {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_SUBJECT, "BRAIN BUFFER Backup")
                                            putExtra(Intent.EXTRA_TEXT, json)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(share, "Export Backup"))
                                        }
                                        feedbackBanner = "Backup prepared for sharing."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export")
                            }
                            OutlinedButton(
                                onClick = onResetOnboarding,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Onboarding")
                            }
                        }
                        OutlinedButton(
                            onClick = { showClearDataDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear local focus data")
                        }
                    }
                }

                // 8. Account
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.AccountCircle,
                        title = "8. Account",
                        tag = "settings_section_account"
                    ) {
                        if (uiState.cloudSync.session.isSignedIn) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.cloudSync.session.email.ifBlank { "Connected account" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Cloud sync enabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LiquidGlassSecondaryButton(
                                    onClick = onSyncNow,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sync now")
                                }
                                OutlinedButton(
                                    onClick = { onSignOut(false) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign out")
                                }
                            }
                            TextButton(
                                onClick = { showDeleteAccountDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete cloud account")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Guest mode (local-first)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "No account required. Sign in optionally for backup.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (uiState.cloudSync.isConfigured) {
                                LiquidGlassButton(
                                    onClick = onOpenAuth,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Sign in or create account")
                                }
                            }
                        }
                    }
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

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear local focus data?") },
            text = { Text("This deletes your local session history, intervention logs, and tasks on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        onClearAllData()
                        feedbackBanner = "Local focus data cleared."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete cloud account?") },
            text = { Text("This permanently removes your cloud account and synced data.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount { _, msg -> feedbackBanner = msg }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    tag: String,
    content: @Composable () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            TextButton(onClick = onOpen) {
                Text("Grant")
            }
        }
    }
}
