package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.FocusGuardSettings
import com.example.model.StudyResource
import com.example.model.TemporaryAppAllowance
import com.example.system.AppLauncherHelper
import com.example.system.AppUsageAwarenessHelper
import com.example.system.PhoneUsageAnalyticsEngine
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassDialogCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassStatusBanner
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState

private val TEMP_ALLOW_REASONS = listOf(
    "Work message",
    "Study link",
    "Quick reply",
    "OTP / Banking"
)

private val SCHEDULE_DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/**
 * Dedicated Block (Focus Guard) primary tab.
 *
 * Design principles:
 * - Strictly honest blocking status: never claims "Blocking active" unless Usage Access is granted,
 *   at least one app is selected, and a Focus or Manual Blocking session is actively enforcing.
 * - Full control over standalone blocking sessions (Start / Pause / Resume / End), distraction
 *   categories, individual installed apps, temporary timed allowances with explicit reason,
 *   scheduled blocking windows, and Study-Only YouTube resources.
 * - Emergency calls, Dialer, Clock, Settings, SystemUI, and Accessibility services are always exempt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockScreen(
    uiState: BrainBufferUiState,
    onRefreshPermissions: () -> Unit,
    onStartManualBlocking: (Int, String) -> Unit,
    onPauseManualBlocking: () -> Unit,
    onResumeManualBlocking: () -> Unit,
    onEndManualBlocking: () -> Unit,
    onUpsertBlockedApp: (BlockedAppRule) -> Unit,
    onRemoveBlockedApp: (String) -> Unit,
    onToggleBlockedCategory: (BlockedAppCategory, Boolean, List<Pair<String, String>>) -> Unit,
    onAllowAppTemporarily: (String, String, Int, String) -> Unit,
    onRevokeTemporaryAllowance: (String) -> Unit,
    onUpdateFocusGuardSettings: (FocusGuardSettings) -> Unit,
    onOpenStudyYouTube: () -> Unit,
    onLaunchStudyResource: (StudyResource) -> Unit,
    onOpenFocusTab: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adaptive = LocalAdaptiveMetrics.current

    // Refresh Usage Access permission status whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedApps = remember(context) {
        AppLauncherHelper.queryLaunchableApps(context, uiState.appCategoryMappings, limit = 120)
            .filterNot { AppUsageAwarenessHelper.isExemptSystemPackage(it.packageName, context.packageName) }
    }

    var selectedBlockDuration by remember { mutableIntStateOf(60) }
    var appSearchQuery by remember { mutableStateOf("") }
    var showAllInstalledPicker by remember { mutableStateOf(false) }
    var tempAllowTarget by remember { mutableStateOf<BlockedAppRule?>(null) }

    val blockedByPkg = remember(uiState.blockedApps) {
        uiState.blockedApps.associateBy { it.packageName }
    }
    val activeBlocked = uiState.activeBlockedApps
    val activeAllowances = uiState.activeTemporaryAllowances
    val manualSession = uiState.manualBlockingSession
    val guardSettings = uiState.preferences.focusGuardSettings

    val filteredInstalledApps = remember(installedApps, appSearchQuery) {
        val q = appSearchQuery.trim().lowercase()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
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
                    .testTag("block_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 14.dp,
                    bottom = adaptive.bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                // Header
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Block & Guard",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Control distracting apps, schedules, and temporary exceptions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Permission Banner if Usage Access is missing
                if (!uiState.hasUsageAccessPermission) {
                    item {
                        LiquidGlassStatusBanner(
                            icon = Icons.Rounded.WarningAmber,
                            title = "Usage Access permission required",
                            subtitle = "Android requires Usage Access to detect when a blocked app opens. Blocking stays inactive until you enable it.",
                            accentColor = MaterialTheme.colorScheme.error,
                            actionLabel = "Enable",
                            onActionClick = { AppUsageAwarenessHelper.openUsageAccessSettings(context) },
                            actionTestTag = "block_enable_usage_access_btn"
                        )
                    }
                }

                // Honest Blocking Status & Standalone Session Control Card
                item {
                    BlockingStatusHeroCard(
                        hasUsageAccess = uiState.hasUsageAccessPermission,
                        isBlockingTrulyActive = uiState.isBlockingTrulyActive,
                        isFocusEnforcing = uiState.isFocusBlockingEnforcing,
                        manualSession = manualSession,
                        nowEpochMs = uiState.nowEpochMs,
                        blockedCount = activeBlocked.size,
                        selectedDurationMinutes = selectedBlockDuration,
                        onSelectDuration = { selectedBlockDuration = it },
                        onStartManualBlocking = {
                            onStartManualBlocking(selectedBlockDuration, "Distraction Block")
                        },
                        onPauseManualBlocking = onPauseManualBlocking,
                        onResumeManualBlocking = onResumeManualBlocking,
                        onEndManualBlocking = onEndManualBlocking,
                        onEnableUsageAccess = { AppUsageAwarenessHelper.openUsageAccessSettings(context) },
                        onOpenFocusTab = onOpenFocusTab
                    )
                }

                // Active Temporary Allowances (if any)
                if (activeAllowances.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Temporarily Allowed (${activeAllowances.size})",
                            subtitle = "Automatically re-blocked when timer expires"
                        )
                    }
                    items(activeAllowances, key = { "allow_${it.packageName}" }) { allowance ->
                        TemporaryAllowanceRow(
                            allowance = allowance,
                            nowEpochMs = uiState.nowEpochMs,
                            onRevoke = { onRevokeTemporaryAllowance(allowance.packageName) }
                        )
                    }
                }

                // Distraction Categories
                item {
                    SectionHeader(
                        title = "Distraction Categories",
                        subtitle = "Block or unblock matching installed apps in one tap"
                    )
                }

                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlockedAppCategory.entries.forEach { category ->
                            val matchingInstalled = remember(installedApps, category) {
                                installedApps.filter { app ->
                                    PhoneUsageAnalyticsEngine.suggestBlockedCategory(app.packageName, app.label) == category
                                }
                            }
                            val blockedInCat = activeBlocked.count { it.category == category }
                            val isSelected = blockedInCat > 0
                            LiquidGlassChip(
                                label = if (blockedInCat > 0) "${category.label} ($blockedInCat)" else category.label,
                                selected = isSelected,
                                accentColor = Color(category.accentHex),
                                leadingDotColor = Color(category.accentHex),
                                onClick = {
                                    val pairs = matchingInstalled.map { it.packageName to it.label }
                                    onToggleBlockedCategory(category, !isSelected, pairs)
                                },
                                modifier = Modifier.testTag("block_category_${category.name.lowercase()}")
                            )
                        }
                    }
                }

                // Currently Blocked Apps List (with quick Temporarily Allow button)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blocked Apps (${activeBlocked.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Tap 'Allow' for a timed exception with reason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showAllInstalledPicker = !showAllInstalledPicker },
                            modifier = Modifier.testTag("block_toggle_app_picker_btn")
                        ) {
                            Icon(
                                imageVector = if (showAllInstalledPicker) Icons.Rounded.Close else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showAllInstalledPicker) "Done" else "Pick Apps")
                        }
                    }
                }

                if (activeBlocked.isEmpty()) {
                    item {
                        SanctuaryCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text(
                                text = "No distracting apps selected yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Select a distraction category above or tap 'Pick Apps' to choose specific apps to block during Focus or Blocking sessions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(activeBlocked, key = { "blocked_${it.packageName}" }) { rule ->
                        val activeTemp = activeAllowances.firstOrNull { it.packageName == rule.packageName }
                        BlockedAppManageRow(
                            rule = rule,
                            activeAllowance = activeTemp,
                            nowEpochMs = uiState.nowEpochMs,
                            onTemporarilyAllow = { tempAllowTarget = rule },
                            onRemove = { onRemoveBlockedApp(rule.packageName) }
                        )
                    }
                }

                // Expandable Installed App Picker
                if (showAllInstalledPicker) {
                    item {
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Search installed apps…") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("block_app_search_input")
                        )
                    }

                    if (filteredInstalledApps.isEmpty()) {
                        item {
                            Text(
                                text = "No matching launchable apps found on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredInstalledApps.take(40), key = { "pick_${it.packageName}" }) { app ->
                            val isBlocked = blockedByPkg[app.packageName]?.isBlocked == true
                            val suggestedCat = PhoneUsageAnalyticsEngine.suggestBlockedCategory(app.packageName, app.label)
                                ?: BlockedAppCategory.OTHER
                            SanctuaryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isBlocked) {
                                            onRemoveBlockedApp(app.packageName)
                                        } else {
                                            onUpsertBlockedApp(
                                                BlockedAppRule(
                                                    packageName = app.packageName,
                                                    appLabel = app.label,
                                                    category = suggestedCat,
                                                    blockInNormal = true,
                                                    blockInHigh = true,
                                                    blockInCustom = true,
                                                    isBlocked = true
                                                )
                                            )
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = suggestedCat.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isBlocked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                onUpsertBlockedApp(
                                                    BlockedAppRule(
                                                        packageName = app.packageName,
                                                        appLabel = app.label,
                                                        category = suggestedCat,
                                                        blockInNormal = true,
                                                        blockInHigh = true,
                                                        blockInCustom = true,
                                                        isBlocked = true
                                                    )
                                                )
                                            } else {
                                                onRemoveBlockedApp(app.packageName)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Scheduled Blocking Periods
                item {
                    ScheduledBlockingCard(
                        settings = guardSettings,
                        onUpdateSettings = onUpdateFocusGuardSettings
                    )
                }

                // Study-Only YouTube Protection Card
                item {
                    StudyYouTubeProtectionCard(
                        resources = uiState.studyResources,
                        onOpenLibrary = onOpenStudyYouTube,
                        onLaunchResource = { resource ->
                            onLaunchStudyResource(resource)
                            AppLauncherHelper.openStudyUrl(context, resource.url)
                        }
                    )
                }

                // Essential & Emergency Exceptions Guarantee
                item {
                    SanctuaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Essential & emergency apps stay accessible",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Phone, Emergency calls, Dialer, Clock/Alarms, Android Settings, and Accessibility tools are permanently exempt from blocking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    tempAllowTarget?.let { rule ->
        TemporaryAllowAppDialog(
            rule = rule,
            onApprove = { durationMin, reason ->
                onAllowAppTemporarily(rule.packageName, rule.appLabel, durationMin, reason)
                tempAllowTarget = null
            },
            onDismiss = { tempAllowTarget = null }
        )
    }
}

@Composable
private fun BlockingStatusHeroCard(
    hasUsageAccess: Boolean,
    isBlockingTrulyActive: Boolean,
    isFocusEnforcing: Boolean,
    manualSession: com.example.model.ManualBlockingSession?,
    nowEpochMs: Long,
    blockedCount: Int,
    selectedDurationMinutes: Int,
    onSelectDuration: (Int) -> Unit,
    onStartManualBlocking: () -> Unit,
    onPauseManualBlocking: () -> Unit,
    onResumeManualBlocking: () -> Unit,
    onEndManualBlocking: () -> Unit,
    onEnableUsageAccess: () -> Unit,
    onOpenFocusTab: () -> Unit
) {
    val isManualActive = manualSession != null && manualSession.isActive &&
        (manualSession.isPaused || manualSession.isEnforcing(nowEpochMs))
    val isManualPaused = manualSession != null && manualSession.isPaused
    val remainingSec = manualSession?.remainingSeconds(nowEpochMs) ?: 0
    val remainingLabel = remember(remainingSec) {
        val m = remainingSec / 60
        val s = remainingSec % 60
        String.format("%02d:%02d", m, s)
    }

    val statusColor = when {
        !hasUsageAccess -> MaterialTheme.colorScheme.error
        isBlockingTrulyActive -> MaterialTheme.colorScheme.secondary
        isManualPaused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusTitle = when {
        !hasUsageAccess -> "Blocking Inactive · Permission Needed"
        isBlockingTrulyActive && isFocusEnforcing -> "Blocking Active · Focus Session"
        isBlockingTrulyActive -> "Blocking Active · $remainingLabel left"
        isManualPaused -> "Blocking Paused · $remainingLabel left"
        blockedCount == 0 -> "Blocking Idle · No Apps Selected"
        else -> "Blocking Ready · $blockedCount apps selected"
    }

    val statusSubtitle = when {
        !hasUsageAccess -> "Grant Android Usage Access to enforce app blocking."
        isBlockingTrulyActive && isFocusEnforcing -> "Protecting your active Focus session across $blockedCount blocked apps."
        isBlockingTrulyActive -> "Actively shielding you from $blockedCount distracting apps."
        isManualPaused -> "Distracting apps are temporarily unblocked until you resume."
        blockedCount == 0 -> "Select distraction categories or apps below to start blocking."
        else -> "Start a standalone blocking session below or start a Focus session."
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("block_status_hero_card"),
        tone = LiquidGlassTone.ELEVATED,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassIconContainer(
                    size = 48.dp,
                    accentColor = statusColor,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isBlockingTrulyActive) Icons.Rounded.Shield else Icons.Rounded.Security,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                !hasUsageAccess -> {
                    LiquidGlassButton(
                        onClick = onEnableUsageAccess,
                        accentStart = MaterialTheme.colorScheme.error,
                        accentEnd = MaterialTheme.colorScheme.error.copy(alpha = 0.82f),
                        cornerRadius = 14.dp,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Usage Access Permission", fontWeight = FontWeight.Bold)
                    }
                }

                isManualActive || isManualPaused -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassButton(
                            onClick = { if (isManualPaused) onResumeManualBlocking() else onPauseManualBlocking() },
                            cornerRadius = 14.dp,
                            height = 48.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_pause_resume_btn")
                        ) {
                            Icon(
                                imageVector = if (isManualPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isManualPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                        }
                        LiquidGlassSecondaryButton(
                            onClick = onEndManualBlocking,
                            accentBorder = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.error,
                            cornerRadius = 14.dp,
                            height = 48.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_end_session_btn")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("End Block", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                isFocusEnforcing -> {
                    LiquidGlassSecondaryButton(
                        onClick = onOpenFocusTab,
                        cornerRadius = 14.dp,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Active Focus Session", fontWeight = FontWeight.SemiBold)
                    }
                }

                else -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(30, 60, 90, 120)) { mins ->
                            LiquidGlassChip(
                                label = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m",
                                selected = selectedDurationMinutes == mins,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { onSelectDuration(mins) }
                            )
                        }
                    }
                    LiquidGlassButton(
                        onClick = onStartManualBlocking,
                        enabled = blockedCount > 0,
                        cornerRadius = 14.dp,
                        height = 48.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("block_start_session_btn")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (blockedCount > 0) {
                                "Start Blocking ($selectedDurationMinutes min)"
                            } else {
                                "Select Apps Below to Start Blocking"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemporaryAllowanceRow(
    allowance: TemporaryAppAllowance,
    nowEpochMs: Long,
    onRevoke: () -> Unit
) {
    val remSec = allowance.remainingSeconds(nowEpochMs)
    val remText = String.format("%02d:%02d", remSec / 60, remSec % 60)

    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("temp_allowance_row_${allowance.packageName}"),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
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
                    imageVector = Icons.Rounded.LockClock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "${allowance.appLabel} · $remText left",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reason: ${allowance.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(
                onClick = onRevoke,
                modifier = Modifier.testTag("revoke_temp_allow_${allowance.packageName}")
            ) {
                Text("Revoke", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BlockedAppManageRow(
    rule: BlockedAppRule,
    activeAllowance: TemporaryAppAllowance?,
    nowEpochMs: Long,
    onTemporarilyAllow: () -> Unit,
    onRemove: () -> Unit
) {
    SanctuaryCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
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
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(rule.category.accentHex))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.appLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = if (activeAllowance != null) {
                        val mins = (activeAllowance.remainingSeconds(nowEpochMs) + 59) / 60
                        "${rule.category.label} · Temporarily allowed (~${mins}m)"
                    } else {
                        rule.category.label
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeAllowance != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onTemporarilyAllow,
                    modifier = Modifier.testTag("temp_allow_btn_${rule.packageName}")
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Allow", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Unblock ${rule.appLabel}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemporaryAllowAppDialog(
    rule: BlockedAppRule,
    onApprove: (durationMinutes: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDuration by remember { mutableIntStateOf(10) }
    var selectedReason by remember { mutableStateOf(TEMP_ALLOW_REASONS.first()) }
    var customReason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassDialogCard(
            modifier = Modifier.testTag("temp_allow_dialog"),
            contentPadding = PaddingValues(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Temporarily allow ${rule.appLabel}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose how long and why you need access. Blocking automatically resumes when time is up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = selectedDuration == mins,
                            onClick = { selectedDuration = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                Text(
                    text = "Reason",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TEMP_ALLOW_REASONS.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason && customReason.isBlank(),
                            onClick = {
                                selectedReason = reason
                                customReason = ""
                            },
                            label = { Text(reason) }
                        )
                    }
                }

                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it.take(60) },
                    label = { Text("Or custom reason") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassSecondaryButton(
                        onClick = onDismiss,
                        cornerRadius = 14.dp,
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    LiquidGlassButton(
                        onClick = {
                            val finalReason = customReason.trim().ifBlank { selectedReason }
                            onApprove(selectedDuration, finalReason)
                        },
                        cornerRadius = 14.dp,
                        height = 46.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_temp_allow_button")
                    ) {
                        Text("Allow ${selectedDuration}m", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduledBlockingCard(
    settings: FocusGuardSettings,
    onUpdateSettings: (FocusGuardSettings) -> Unit
) {
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("block_schedule_card"),
        contentPadding = PaddingValues(16.dp)
    ) {
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
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Scheduled Blocking",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.scheduledBlockingEnabled) {
                                "${formatHourLabel(settings.scheduleStartHour)} – ${formatHourLabel(settings.scheduleEndHour)}"
                            } else {
                                "Remind & guard during daily focus windows"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = settings.scheduledBlockingEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(settings.copy(scheduledBlockingEnabled = enabled))
                    },
                    modifier = Modifier.testTag("block_schedule_toggle")
                )
            }

            if (settings.scheduledBlockingEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassSecondaryButton(
                        onClick = {
                            val nextStart = (settings.scheduleStartHour + 1) % 24
                            onUpdateSettings(settings.copy(scheduleStartHour = nextStart))
                        },
                        cornerRadius = 12.dp,
                        height = 42.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start: ${formatHourLabel(settings.scheduleStartHour)}")
                    }
                    LiquidGlassSecondaryButton(
                        onClick = {
                            val nextEnd = (settings.scheduleEndHour + 1) % 24
                            onUpdateSettings(settings.copy(scheduleEndHour = nextEnd))
                        },
                        cornerRadius = 12.dp,
                        height = 42.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("End: ${formatHourLabel(settings.scheduleEndHour)}")
                    }
                }

                val currentDays = remember(settings.scheduleDaysCsv) {
                    settings.scheduleDaysCsv
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SCHEDULE_DAYS.forEach { day ->
                        val active = day in currentDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                val nextDays = if (active) {
                                    (currentDays - day).ifEmpty { setOf(day) }
                                } else {
                                    currentDays + day
                                }
                                onUpdateSettings(
                                    settings.copy(
                                        scheduleDaysCsv = SCHEDULE_DAYS.filter { it in nextDays }.joinToString(",")
                                    )
                                )
                            },
                            label = { Text(day) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyYouTubeProtectionCard(
    resources: List<StudyResource>,
    onOpenLibrary: () -> Unit,
    onLaunchResource: (StudyResource) -> Unit
) {
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("block_study_youtube_card"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        imageVector = Icons.Rounded.SmartDisplay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = "YouTube Study Protection",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${resources.size} approved videos, playlists & channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier.testTag("block_open_study_youtube_btn")
                ) {
                    Text("Manage")
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Android does not allow third-party apps to filter or inspect videos inside the official YouTube app. Launch your saved study links from here to skip the distracting home feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (resources.isNotEmpty()) {
                resources.take(3).forEach { resource ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onLaunchResource(resource) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resource.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${resource.resourceType.label}${if (resource.channelName.isNotBlank()) " · ${resource.channelName}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Open ${resource.title}",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                LiquidGlassSecondaryButton(
                    onClick = onOpenLibrary,
                    cornerRadius = 12.dp,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Approved Study Link")
                }
            }
        }
    }
}

private fun formatHourLabel(hour24: Int): String {
    val h = ((hour24 % 24) + 24) % 24
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:00 $suffix"
}
