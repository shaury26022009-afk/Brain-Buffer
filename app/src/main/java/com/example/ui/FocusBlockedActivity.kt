package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.data.BrainBufferRepository
import com.example.domain.FocusTimerEngine
import com.example.model.ThemeMode
import com.example.notifications.BrainBufferNotificationManager
import com.example.service.TimerNotificationActionReceiver
import com.example.system.BrainBufferHaptics
import com.example.system.HapticEvent
import com.example.ui.components.AppRoutes
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calm, Purpose-Aware Distraction Intervention screen.
 *
 * Displayed when Usage Access detects switching to an app outside the active session's
 * declared allowed list. Uses calm, non-judgmental copy ("You're in Study mode, but [App]
 * isn't in your allowed list.") and offers clear actions:
 * - Return to Focus
 * - Temporarily allow this app (opens App Switch / timed access tracked separately)
 * - Change purpose
 * - Pause / Resume or Finish Early (with confirmation)
 */
class FocusBlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val blockedAppName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME).orEmpty().ifBlank { "This app" }
        val blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val blockedCategory = intent?.getStringExtra(EXTRA_BLOCKED_CATEGORY).orEmpty()
        val reason = intent?.getStringExtra(EXTRA_BLOCK_REASON).orEmpty()
        val repository = BrainBufferRepository.getInstance(applicationContext)

        setContent {
            val prefs by repository.preferences.collectAsStateWithLifecycle(initialValue = com.example.model.UserPreferences())
            val snapshot by repository.timerSnapshot.collectAsStateWithLifecycle(initialValue = com.example.model.PersistedTimerSnapshot())

            LaunchedEffect(blockedPackage, blockedAppName) {
                val latestPrefs = repository.getPreferencesOnce()
                BrainBufferHaptics.perform(
                    context = this@FocusBlockedActivity,
                    event = HapticEvent.DISTRACTION_ALERT,
                    enabled = latestPrefs.hapticsEnabled && latestPrefs.focusGuardSettings.vibrationEnabled
                )
            }

            BrainBufferTheme(
                themeMode = prefs.themeMode,
                reducedMotionOverride = prefs.reducedMotionOverride,
                performanceOverride = prefs.performanceOverride,
                isAppInForeground = true
            ) {
                FocusBlockedScreenContent(
                    blockedAppName = blockedAppName,
                    blockedPackage = blockedPackage,
                    blockedCategory = blockedCategory,
                    reason = reason,
                    snapshot = snapshot,
                    onReturnToFocus = {
                        BrainBufferHaptics.perform(
                            context = this@FocusBlockedActivity,
                            event = HapticEvent.PAUSE_OR_RESUME,
                            enabled = prefs.hapticsEnabled
                        )
                        val returnIntent = Intent(this@FocusBlockedActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, AppRoutes.FOCUS)
                        }
                        startActivity(returnIntent)
                        finish()
                    },
                    onTemporarilyAllow = {
                        val openIntent = Intent(this@FocusBlockedActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, AppRoutes.FOCUS)
                            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_CONTROLLED_ACCESS, true)
                        }
                        startActivity(openIntent)
                        finish()
                    },
                    onChangePurpose = {
                        val openIntent = Intent(this@FocusBlockedActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, AppRoutes.FOCUS)
                            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_CHANGE_PURPOSE, true)
                        }
                        startActivity(openIntent)
                        finish()
                    },
                    onTogglePauseResume = { isCurrentlyRunning ->
                        val action = if (isCurrentlyRunning) {
                            TimerNotificationActionReceiver.ACTION_PAUSE
                        } else {
                            TimerNotificationActionReceiver.ACTION_RESUME
                        }
                        TimerNotificationActionReceiver.handleAction(applicationContext, action)
                    },
                    onConfirmFinishFocus = {
                        TimerNotificationActionReceiver.handleAction(
                            applicationContext,
                            TimerNotificationActionReceiver.ACTION_END
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_BLOCKED_APP_NAME = "bb_blocked_app_name"
        const val EXTRA_BLOCKED_PACKAGE = "bb_blocked_package"
        const val EXTRA_BLOCKED_CATEGORY = "bb_blocked_category"
        const val EXTRA_BLOCK_REASON = "bb_block_reason"

        fun createIntent(
            context: Context,
            appName: String,
            packageName: String,
            categoryLabel: String,
            reason: String
        ): Intent = Intent(context, FocusBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_BLOCKED_APP_NAME, appName)
            putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
            putExtra(EXTRA_BLOCKED_CATEGORY, categoryLabel)
            putExtra(EXTRA_BLOCK_REASON, reason)
        }
    }
}

@Composable
private fun FocusBlockedScreenContent(
    blockedAppName: String,
    blockedPackage: String,
    blockedCategory: String,
    reason: String,
    snapshot: com.example.model.PersistedTimerSnapshot,
    onReturnToFocus: () -> Unit,
    onTemporarilyAllow: () -> Unit,
    onChangePurpose: () -> Unit,
    onTogglePauseResume: suspend (Boolean) -> Unit,
    onConfirmFinishFocus: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showFinishDialog by remember { mutableStateOf(false) }
    val isRunning = snapshot.runningSinceEpochMs != null
    var remainingSec by remember(snapshot) {
        mutableIntStateOf(
            FocusTimerEngine.remainingSeconds(
                snapshot,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
            )
        )
    }

    LaunchedEffect(snapshot) {
        while (true) {
            remainingSec = FocusTimerEngine.remainingSeconds(
                snapshot,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
            )
            if (snapshot.runningSinceEpochMs == null) break
            delay(1_000L)
        }
    }

    val purposeName = snapshot.purposeCategory.label.ifBlank { blockedCategory.ifBlank { "Focus" } }
    val sessionTitle = snapshot.sessionGoal.ifBlank { purposeName }
    val formattedTime = BrainBufferNotificationManager.formatMinSec(remainingSec)
    val explanation = reason.ifBlank {
        "You're in $purposeName mode, but $blockedAppName isn't in your allowed list."
    }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LiquidGlassIconContainer(
                    size = 72.dp,
                    accentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Purpose check-in",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Stay aligned with $purposeName",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = LiquidGlassTone.ACCENT,
                    cornerRadius = 24.dp,
                    borderAccent = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isRunning) "INTENDED FOCUS · $purposeName".uppercase() else "FOCUS PAUSED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = sessionTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                LiquidGlassButton(
                    onClick = onReturnToFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_screen_return_focus_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Return to Focus", style = MaterialTheme.typography.labelLarge)
                }

                LiquidGlassSecondaryButton(
                    onClick = onTemporarilyAllow,
                    accentBorder = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_screen_need_phone_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Temporarily allow this app", style = MaterialTheme.typography.labelLarge)
                }

                LiquidGlassSecondaryButton(
                    onClick = onChangePurpose,
                    accentBorder = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_screen_change_purpose_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change purpose", style = MaterialTheme.typography.labelLarge)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassSecondaryButton(
                        onClick = {
                            scope.launch { onTogglePauseResume(isRunning) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("blocked_screen_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Pause" else "Resume",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    LiquidGlassSecondaryButton(
                        onClick = { showFinishDialog = true },
                        accentBorder = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("blocked_screen_finish_focus_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Finish Early", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish Focus Session Early?") },
            text = {
                Text("Only your actual elapsed focus time will be recorded as Ended Early, and Focus Guard protection will stop.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        scope.launch { onConfirmFinishFocus() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Finish Early")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep Focusing")
                }
            }
        )
    }
}
