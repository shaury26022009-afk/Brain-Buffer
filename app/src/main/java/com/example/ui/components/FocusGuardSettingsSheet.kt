package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityCategory
import com.example.model.AppCategoryMapping
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.FocusGuardLevel
import com.example.model.FocusGuardPresetTemplates
import com.example.model.FocusGuardSettings
import com.example.model.StrictFocusPreset
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.system.AppLauncherHelper
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndFocusController
import com.example.system.InstalledAppChoice
import com.example.system.PhoneUsageAnalyticsEngine
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily

/**
 * Focus Guard 2.0 Settings & App Category Manager (Parts 6, 8, 9):
 * Clean, approachable control panel for default durations, per-activity allowances,
 * Daily Entertainment Limit, DND/sound/vibration/auto-return/optional Screen Pinning,
 * Emergency Exceptions guarantee, optional starter templates, and App-to-Category mapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusGuardSettingsSheet(
    settings: FocusGuardSettings,
    appUsageAwarenessEnabled: Boolean,
    appCategoryMappings: List<AppCategoryMapping>,
    blockedApps: List<BlockedAppRule> = emptyList(),
    onUpdateSettings: (FocusGuardSettings) -> Unit,
    onUpdateAppUsageAwareness: (Boolean) -> Unit,
    onUpsertAppMapping: (AppCategoryMapping) -> Unit,
    onDeleteAppMapping: (String) -> Unit,
    onUpsertBlockedApp: (BlockedAppRule) -> Unit = {},
    onDeleteBlockedApp: (String) -> Unit = {},
    onToggleBlockedCategory: (BlockedAppCategory, List<InstalledAppChoice>, Boolean) -> Unit = { _, _, _ -> },
    onSaveStarterPreset: (StrictFocusPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        FocusGuardSettingsContent(
            settings = settings,
            appUsageAwarenessEnabled = appUsageAwarenessEnabled,
            appCategoryMappings = appCategoryMappings,
            blockedApps = blockedApps,
            onUpdateSettings = onUpdateSettings,
            onUpdateAppUsageAwareness = onUpdateAppUsageAwareness,
            onUpsertAppMapping = onUpsertAppMapping,
            onDeleteAppMapping = onDeleteAppMapping,
            onUpsertBlockedApp = onUpsertBlockedApp,
            onDeleteBlockedApp = onDeleteBlockedApp,
            onToggleBlockedCategory = onToggleBlockedCategory,
            onSaveStarterPreset = onSaveStarterPreset,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusGuardSettingsContent(
    settings: FocusGuardSettings,
    appUsageAwarenessEnabled: Boolean,
    appCategoryMappings: List<AppCategoryMapping>,
    blockedApps: List<BlockedAppRule> = emptyList(),
    onUpdateSettings: (FocusGuardSettings) -> Unit,
    onUpdateAppUsageAwareness: (Boolean) -> Unit,
    onUpsertAppMapping: (AppCategoryMapping) -> Unit,
    onDeleteAppMapping: (String) -> Unit,
    onUpsertBlockedApp: (BlockedAppRule) -> Unit = {},
    onDeleteBlockedApp: (String) -> Unit = {},
    onToggleBlockedCategory: (BlockedAppCategory, List<InstalledAppChoice>, Boolean) -> Unit = { _, _, _ -> },
    onSaveStarterPreset: (StrictFocusPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dndGranted = remember { DndFocusController.isAccessGranted(context) }
    val usageGranted = remember { AppUsageAwarenessHelper.hasUsageStatsPermission(context) }
    val installedApps = remember(appCategoryMappings) {
        AppLauncherHelper.queryLaunchableApps(context, appCategoryMappings, limit = 40)
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var showBlocklistPicker by remember { mutableStateOf(false) }
    var selectedCategoryForNewApp by remember { mutableStateOf(StudyActivityType.STUDY_LECTURE) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("focus_guard_settings_sheet"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Focus Guard Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Simple, voluntary controls — you stay in charge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }

        // Optional Starter Templates (never forced)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Optional Starter Templates",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusGuardPresetTemplates.templates.forEach { template ->
                    OutlinedButton(
                        onClick = {
                            onUpdateSettings(
                                settings.copy(
                                    defaultFocusMinutes = template.durationMinutes,
                                    strictnessLevel = template.strictnessLevel,
                                    dndIntegrationEnabled = template.enableDnd && dndGranted
                                )
                            )
                            onSaveStarterPreset(template)
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("starter_template_${template.name.lowercase().replace(" ", "_")}")
                    ) {
                        Text("${template.name} · ${template.durationMinutes}m", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)

        // Default Focus Guard Level
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Default Focus Level",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FocusGuardLevel.entries.forEach { level ->
                    FilterChip(
                        selected = settings.defaultLevel == level,
                        onClick = { onUpdateSettings(settings.copy(defaultLevel = level)) },
                        label = { Text(level.label) }
                    )
                }
            }
        }

        // Default Session & Allowance Durations
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Default Durations & Allowances",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            AllowanceStepperRow(
                label = "Default focus session",
                minutes = settings.defaultFocusMinutes,
                minMinutes = 15,
                maxMinutes = 120,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(defaultFocusMinutes = it)) }
            )
            AllowanceStepperRow(
                label = "Study PDF / Documents",
                minutes = settings.allowances.pdfDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 90,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(pdfDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Study Lecture (e.g., YouTube)",
                minutes = settings.allowances.lectureDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 90,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(lectureDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Reading / Research",
                minutes = settings.allowances.readingDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 60,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(readingDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Notes / Writing",
                minutes = settings.allowances.notesDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 90,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(notesDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Short Break",
                minutes = settings.allowances.breakDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 30,
                step = 5,
                onChange = {
                    onUpdateSettings(
                        settings.copy(
                            defaultBreakMinutes = it,
                            allowances = settings.allowances.copy(breakDefaultMinutes = it)
                        )
                    )
                }
            )
            AllowanceStepperRow(
                label = "Gaming allowance",
                minutes = settings.allowances.gamingDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 60,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(gamingDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Social / Entertainment allowance",
                minutes = settings.allowances.socialDefaultMinutes,
                minMinutes = 5,
                maxMinutes = 45,
                step = 5,
                onChange = { onUpdateSettings(settings.copy(allowances = settings.allowances.copy(socialDefaultMinutes = it))) }
            )
            AllowanceStepperRow(
                label = "Daily Entertainment Limit",
                minutes = settings.allowances.dailyEntertainmentLimitMinutes,
                minMinutes = 15,
                maxMinutes = 180,
                step = 15,
                highlight = true,
                testTag = "daily_entertainment_limit_row",
                onChange = {
                    onUpdateSettings(
                        settings.copy(allowances = settings.allowances.copy(dailyEntertainmentLimitMinutes = it))
                    )
                }
            )
        }

        HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)

        // Focus Guard Toggles
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Focus Environment & Reminders",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            GuardSettingSwitchRow(
                title = "Do Not Disturb integration",
                subtitle = if (dndGranted) {
                    "Silences distractions while keeping alarms & emergency alerts active"
                } else {
                    "Tap to grant Android Do Not Disturb access"
                },
                checked = settings.dndIntegrationEnabled && dndGranted,
                onCheckedChange = { checked ->
                    if (checked && !dndGranted) {
                        DndFocusController.openDndAccessSettings(context)
                    } else {
                        onUpdateSettings(settings.copy(dndIntegrationEnabled = checked))
                    }
                }
            )

            GuardSettingSwitchRow(
                title = "Notification sound",
                subtitle = "Play a calm tone when an activity allowance ends",
                checked = settings.notificationSoundEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(notificationSoundEnabled = it)) }
            )

            GuardSettingSwitchRow(
                title = "Vibration & haptics",
                subtitle = "Gentle tactile pulse on reminders and focus actions",
                checked = settings.vibrationEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(vibrationEnabled = it)) }
            )

            GuardSettingSwitchRow(
                title = "Auto return-to-study reminder",
                subtitle = "Notify with one-tap Return to Study when allowances end",
                checked = settings.autoReturnToStudyReminder,
                onCheckedChange = { onUpdateSettings(settings.copy(autoReturnToStudyReminder = it)) }
            )

            GuardSettingSwitchRow(
                title = "Activity tracking",
                subtitle = "Log focused study, lectures, breaks & entertainment separately",
                checked = settings.activityTrackingEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(activityTrackingEnabled = it)) }
            )

            GuardSettingSwitchRow(
                title = "Optional Android Screen Pinning",
                subtitle = "Ask Android OS to pin High Focus screen. Always exitable via Android's standard unpin gesture.",
                checked = settings.optionalAppPinningEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(optionalAppPinningEnabled = it)) }
            )

            GuardSettingSwitchRow(
                title = "Distraction warning notifications",
                subtitle = "Send a calm study reminder notification when switching to an unapproved app during High Focus (requires Usage Access).",
                checked = settings.distractionWarningsEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(distractionWarningsEnabled = it)) }
            )

            GuardSettingSwitchRow(
                title = "Android 16 Live Updates",
                subtitle = "Promote live study timer on supported Android 16+ devices (falls back smoothly to live notification on Android 10–15).",
                checked = settings.liveUpdatesEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(liveUpdatesEnabled = it)) }
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Distraction reminder cooldown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30 to "30s", 60 to "1 min", 90 to "90s", 180 to "3 min").forEach { (secs, label) ->
                        FilterChip(
                            selected = settings.distractionCooldownSeconds == secs,
                            onClick = { onUpdateSettings(settings.copy(distractionCooldownSeconds = secs)) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("distraction_cooldown_${secs}s")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Default quick-extension button",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = settings.customExtensionMinutes == mins,
                            onClick = { onUpdateSettings(settings.copy(customExtensionMinutes = mins)) },
                            label = { Text("+${mins}m") },
                            modifier = Modifier.testTag("custom_extension_${mins}m")
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)

        // Part 6: App-Aware Tracking & App Category Manager
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "App-Aware Categories (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            GuardSettingSwitchRow(
                title = "App-usage awareness",
                subtitle = if (usageGranted) {
                    "Gently reminds you if an unapproved app opens during High Focus. Never reads screen contents."
                } else {
                    "Optional Android Usage Access permission required. Tap to enable in system settings."
                },
                checked = appUsageAwarenessEnabled && usageGranted,
                onCheckedChange = { checked ->
                    if (checked && !usageGranted) {
                        AppUsageAwarenessHelper.openUsageAccessSettings(context)
                    } else {
                        onUpdateAppUsageAwareness(checked)
                    }
                }
            )

            if (appCategoryMappings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    appCategoryMappings.forEach { mapping ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mapping.appLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${mapping.activityType.label} · ${mapping.category.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteAppMapping(mapping.packageName) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove ${mapping.appLabel} mapping",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showAppPicker = !showAppPicker },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("focus_guard_configure_apps_button")
            ) {
                Text(if (showAppPicker) "Hide Installed Apps" else "Categorize Installed Apps (e.g., YouTube -> Lecture)")
            }

            if (showAppPicker) {
                val categoryChoices = listOf(
                    StudyActivityType.STUDY_LECTURE,
                    StudyActivityType.STUDY_PDF,
                    StudyActivityType.READING,
                    StudyActivityType.WRITING_NOTES,
                    StudyActivityType.PLAYING_GAME,
                    StudyActivityType.ENTERTAINMENT
                )
                Text(
                    text = "1. Pick a category, then tap an app to assign it:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryChoices.forEach { act ->
                        FilterChip(
                            selected = selectedCategoryForNewApp == act,
                            onClick = { selectedCategoryForNewApp = act },
                            label = { Text(act.shortLabel) }
                        )
                    }
                }
                if (installedApps.isEmpty()) {
                    Text(
                        text = "No additional launchable apps detected on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        installedApps.forEach { app ->
                            FilterChip(
                                selected = appCategoryMappings.any { it.packageName == app.packageName },
                                onClick = {
                                    onUpsertAppMapping(
                                        AppCategoryMapping(
                                            packageName = app.packageName,
                                            appLabel = app.label,
                                            category = selectedCategoryForNewApp.category,
                                            activityType = selectedCategoryForNewApp
                                        )
                                    )
                                },
                                label = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)

        // Focus Guard Distracting App Blocklists (Categories + Per-App + Per-Mode Rules)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distracting App Blocklists (${blockedApps.count { it.isBlocked }})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Quick-guard categories or select individual installed apps per Focus mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showBlocklistPicker = !showBlocklistPicker }) {
                    Text(if (showBlocklistPicker) "Hide Apps" else "Pick Apps")
                }
            }

            // Suggested Category Quick Toggles
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BlockedAppCategory.entries.forEach { cat ->
                    val hasAnyInCat = blockedApps.any { it.category == cat && it.isBlocked }
                    FilterChip(
                        selected = hasAnyInCat,
                        onClick = { onToggleBlockedCategory(cat, installedApps, !hasAnyInCat) },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("blocklist_category_${cat.name.lowercase()}")
                    )
                }
            }

            // Individual Installed App Picker for Blocklist
            if (showBlocklistPicker && installedApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    installedApps.forEach { app ->
                        val existing = blockedApps.firstOrNull { it.packageName == app.packageName }
                        val isBlocked = existing?.isBlocked == true
                        FilterChip(
                            selected = isBlocked,
                            onClick = {
                                if (isBlocked) {
                                    onDeleteBlockedApp(app.packageName)
                                } else {
                                    onUpsertBlockedApp(
                                        BlockedAppRule(
                                            packageName = app.packageName,
                                            appLabel = app.label,
                                            category = PhoneUsageAnalyticsEngine.suggestBlockedCategory(app.packageName, app.label),
                                            blockInNormal = false,
                                            blockInHigh = true,
                                            blockInCustom = true,
                                            isBlocked = true
                                        )
                                    )
                                }
                            },
                            label = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            // Active Blocked Apps with Per-Mode Rule Chips (Normal / High / Custom)
            blockedApps.take(12).forEach { rule ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.appLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = rule.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteBlockedApp(rule.packageName) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove ${rule.appLabel} from blocklist"
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = rule.blockInNormal,
                                onClick = { onUpsertBlockedApp(rule.copy(blockInNormal = !rule.blockInNormal)) },
                                label = { Text("Normal") }
                            )
                            FilterChip(
                                selected = rule.blockInHigh,
                                onClick = { onUpsertBlockedApp(rule.copy(blockInHigh = !rule.blockInHigh)) },
                                label = { Text("High") }
                            )
                            FilterChip(
                                selected = rule.blockInCustom,
                                onClick = { onUpsertBlockedApp(rule.copy(blockInCustom = !rule.blockInCustom)) },
                                label = { Text("Custom") }
                            )
                        }
                    }
                }
            }
        }

        // Emergency Exceptions & Privacy Guarantee
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalHospital,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Emergency & Safety Guarantee",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Emergency calls, phone dialer, system alarms, and Android accessibility tools are never blocked. All data stays 100% offline on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LiquidGlassButton(
            onClick = onDismiss,
            cornerRadius = 16.dp,
            height = 50.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Focus Guard Settings", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun AllowanceStepperRow(
    label: String,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    step: Int,
    highlight: Boolean = false,
    testTag: String? = null,
    onChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onChange((minutes - step).coerceAtLeast(minMinutes)) },
                    enabled = minutes > minMinutes
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
                }
                Text(
                    text = "${minutes}m",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onChange((minutes + step).coerceAtMost(maxMinutes)) },
                    enabled = minutes < maxMinutes
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
                }
            }
        }
    }
}

@Composable
private fun GuardSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
