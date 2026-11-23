package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndFocusController

@Immutable
data class LivePermissionSnapshot(
    val usageAccessGranted: Boolean,
    val notificationsGranted: Boolean,
    val dndPolicyGranted: Boolean
) {
    val grantedCount: Int
        get() = listOf(usageAccessGranted, notificationsGranted, dndPolicyGranted).count { it }
}

fun queryLivePermissions(context: Context): LivePermissionSnapshot = LivePermissionSnapshot(
    usageAccessGranted = AppUsageAwarenessHelper.hasUsageStatsPermission(context),
    notificationsGranted = BrainBufferNotificationManager.hasNotificationPermission(context),
    dndPolicyGranted = DndFocusController.isAccessGranted(context)
)

@Composable
fun rememberLivePermissionSnapshot(
    onSnapshotRefreshed: (LivePermissionSnapshot) -> Unit = {}
): LivePermissionSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(queryLivePermissions(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val fresh = queryLivePermissions(context)
                snapshot = fresh
                onSnapshotRefreshed(fresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return snapshot
}

/**
 * Unified Android Permission Center used in both First-Launch Setup (Step G)
 * and Profile -> Settings -> Permissions.
 *
 * - Never requests all permissions at once.
 * - Explains what each permission enables, why it is needed, and what happens if denied.
 * - Refreshes real OS permission states automatically on ON_RESUME.
 * - Never fakes granted state.
 */
@Composable
fun PermissionCenterSection(
    modifier: Modifier = Modifier,
    onUsageAccessGranted: () -> Unit = {},
    onStatusChanged: (LivePermissionSnapshot) -> Unit = {},
    showBatteryReliabilityCard: Boolean = true
) {
    val context = LocalContext.current
    var notificationPromptAttempted by remember { mutableStateOf(false) }

    val snapshot = rememberLivePermissionSnapshot { refreshed ->
        onStatusChanged(refreshed)
        if (refreshed.usageAccessGranted) {
            onUsageAccessGranted()
        }
    }

    var liveNotifGranted by remember(snapshot.notificationsGranted) {
        mutableStateOf(snapshot.notificationsGranted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPromptAttempted = true
        liveNotifGranted = granted || BrainBufferNotificationManager.hasNotificationPermission(context)
        onStatusChanged(queryLivePermissions(context))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Usage Access
        PermissionStatusCard(
            icon = Icons.Outlined.QueryStats,
            title = "Usage Access",
            isGranted = snapshot.usageAccessGranted,
            enablesText = "Enables accurate Phone Usage analytics and Focus Guard detection when you open a blocked distracting app.",
            whyNeededText = "Android requires Usage Access so Brain Buffer can read screen time and active app switches locally on-device.",
            ifDeniedText = "If skipped: Focus Timer, Planner, and Study YouTube still work, while Phone Usage charts and automatic app-switch alerts stay inactive.",
            buttonLabel = if (snapshot.usageAccessGranted) "Manage Usage Access" else "Enable Usage Access",
            testTag = "permission_card_usage_access",
            onActionClick = { AppUsageAwarenessHelper.openUsageAccessSettings(context) }
        )

        // 2. Notification Permission
        PermissionStatusCard(
            icon = Icons.Outlined.Notifications,
            title = "Notifications",
            isGranted = liveNotifGranted,
            enablesText = "Enables live focus countdown notification, pause/resume controls, distraction reminders, and scheduled focus alerts.",
            whyNeededText = "Required on Android 13+ so your ongoing timer and Focus Guard alerts stay visible outside the app.",
            ifDeniedText = "If skipped: In-app timer still runs, but live notification bar controls and background reminders won't appear.",
            buttonLabel = if (liveNotifGranted) "Notification Settings" else "Enable Notifications",
            testTag = "permission_card_notifications",
            onActionClick = {
                if (liveNotifGranted || notificationPromptAttempted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    BrainBufferNotificationManager.openAppNotificationSettings(context)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        // 3. Do Not Disturb Access
        PermissionStatusCard(
            icon = Icons.Outlined.DoNotDisturbOn,
            title = "Do Not Disturb Access",
            isGranted = snapshot.dndPolicyGranted,
            enablesText = "Enables quiet notifications during High Focus sessions while keeping alarms and important calls reachable.",
            whyNeededText = "Android requires explicit DND Policy permission before an app can silence interruptions and restore your prior state automatically.",
            ifDeniedText = "If skipped: Focus sessions work normally, and you can toggle Do Not Disturb manually from Quick Settings.",
            buttonLabel = if (snapshot.dndPolicyGranted) "Manage DND Access" else "Enable Do Not Disturb",
            testTag = "permission_card_dnd",
            onActionClick = { DndFocusController.openDndAccessSettings(context) }
        )

        // 4. Transparent OEM Battery & Background Reliability Guide
        if (showBatteryReliabilityCard) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permission_card_battery_guide"),
                tone = LiquidGlassTone.SUBTLE,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassIconContainer(
                            size = 38.dp,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BatterySaver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background & Blocking Reliability",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Optional OEM tip · Play-Policy compliant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Brain Buffer stays lightweight and avoids intrusive Accessibility spying or permanent overlays. On some devices (Xiaomi, Samsung, OnePlus, Vivo), aggressive battery savers can delay background focus reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LiquidGlassSecondaryButton(
                        onClick = { openBatterySettingsSafely(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_open_battery_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Android Battery Settings", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    enablesText: String,
    whyNeededText: String,
    ifDeniedText: String,
    buttonLabel: String,
    testTag: String,
    onActionClick: () -> Unit
) {
    val accent = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        tone = if (isGranted) LiquidGlassTone.SUBTLE else LiquidGlassTone.STANDARD,
        cornerRadius = 20.dp,
        borderAccent = if (isGranted) accent.copy(alpha = 0.45f) else null,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassIconContainer(
                    size = 40.dp,
                    accentColor = accent,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isGranted) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Granted",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isGranted) "Granted" else "Not enabled",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = enablesText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = whyNeededText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ifDeniedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(2.dp))
            LiquidGlassSecondaryButton(
                onClick = onActionClick,
                accentBorder = accent,
                contentColor = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_button")
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun openBatterySettingsSafely(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallback = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}
