package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.FocusTimerEngine
import com.example.model.FocusSessionState
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Formats a non-negative duration in seconds into tabular-friendly `MM:SS`
 * (or `H:MM:SS` for durations of one hour or more).
 */
fun formatTimerDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val secs = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

/**
 * Lifecycle-aware focus timer component wired directly to a [FocusTimerEngine] instance.
 *
 * Collects [FocusTimerEngine.tickState] via [collectAsStateWithLifecycle], renders the
 * circular progress dial and remaining/elapsed readout, and dispatches Start/Pause/Resume
 * (and optional Reset) actions directly to the engine unless custom callbacks are provided.
 */
@Composable
fun FocusTimerDisplay(
    engine: FocusTimerEngine,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    ringSize: Dp = 252.dp,
    statusOverride: String? = null,
    primaryControlEnabled: Boolean = true,
    showResetButton: Boolean = true,
    useSplitLayout: Boolean = false,
    onStartOrPause: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    countdownTestTag: String = "focus_timer_remaining_text",
    primaryControlTestTag: String = "focus_timer_start_pause_button",
    resetControlTestTag: String = "focus_timer_reset_button",
    supportingContent: (@Composable () -> Unit)? = null,
    trailingControls: (@Composable RowScope.() -> Unit)? = null
) {
    val tick by engine.tickState.collectAsStateWithLifecycle()

    FocusTimerDisplay(
        remainingSeconds = tick.remainingSeconds,
        elapsedSeconds = tick.elapsedSeconds,
        totalSeconds = tick.snapshot.totalSeconds,
        progress = tick.progress,
        isRunning = tick.isRunning,
        hasStarted = tick.snapshot.hasStarted,
        sessionState = tick.sessionState,
        onToggleStartPause = {
            if (onStartOrPause != null) {
                onStartOrPause()
            } else {
                engine.togglePauseResume()
            }
        },
        modifier = modifier,
        accentColor = accentColor,
        ringSize = ringSize,
        statusLabel = statusOverride,
        primaryControlEnabled = primaryControlEnabled,
        onReset = if (showResetButton) {
            onReset ?: {
                engine.reset()
                Unit
            }
        } else {
            null
        },
        useSplitLayout = useSplitLayout,
        countdownTestTag = countdownTestTag,
        primaryControlTestTag = primaryControlTestTag,
        resetControlTestTag = resetControlTestTag,
        supportingContent = supportingContent,
        trailingControls = trailingControls
    )
}

/**
 * Stateless, hoistable focus timer UI displaying remaining time, circular progress,
 * elapsed/planned metadata, and Start/Pause/Resume controls.
 *
 * Uses strictly semantic Material 3 color tokens so typography, borders, and controls
 * remain high-contrast in both Light and AMOLED Dark themes.
 */
@Composable
fun FocusTimerDisplay(
    remainingSeconds: Int,
    elapsedSeconds: Int,
    totalSeconds: Int,
    progress: Float,
    isRunning: Boolean,
    hasStarted: Boolean,
    sessionState: FocusSessionState,
    onToggleStartPause: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    ringSize: Dp = 252.dp,
    statusLabel: String? = null,
    primaryControlEnabled: Boolean = true,
    onReset: (() -> Unit)? = null,
    resetContentDescription: String? = null,
    useSplitLayout: Boolean = false,
    countdownTestTag: String = "focus_timer_remaining_text",
    primaryControlTestTag: String = "focus_timer_start_pause_button",
    resetControlTestTag: String = "focus_timer_reset_button",
    supportingContent: (@Composable () -> Unit)? = null,
    primaryControlOverride: (@Composable () -> Unit)? = null,
    trailingControls: (@Composable RowScope.() -> Unit)? = null
) {
    val resolvedStatus = statusLabel ?: when {
        sessionState == FocusSessionState.COMPLETED -> stringResource(R.string.timer_status_completed)
        sessionState == FocusSessionState.BREAK -> stringResource(R.string.timer_status_break_remaining)
        sessionState == FocusSessionState.APPROVED_ENTERTAINMENT_ACTIVITY -> stringResource(R.string.timer_status_focus_paused)
        isRunning -> stringResource(R.string.timer_status_remaining)
        hasStarted -> stringResource(R.string.timer_status_paused)
        else -> stringResource(R.string.timer_status_ready)
    }

    val dialContent: @Composable () -> Unit = {
        FocusTimerDial(
            remainingSeconds = remainingSeconds,
            elapsedSeconds = elapsedSeconds,
            totalSeconds = totalSeconds,
            progress = progress,
            statusLabel = resolvedStatus,
            accentColor = accentColor,
            ringSize = ringSize,
            countdownTestTag = countdownTestTag
        )
    }

    val controlsContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            supportingContent?.invoke()

            if (primaryControlOverride != null) {
                primaryControlOverride()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusTimerStartPauseButton(
                        isRunning = isRunning,
                        hasStarted = hasStarted,
                        enabled = primaryControlEnabled,
                        onClick = onToggleStartPause,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(54.dp)
                            .testTag(primaryControlTestTag)
                    )

                    if (onReset != null) {
                        val resetDescription = resetContentDescription
                            ?: stringResource(R.string.cd_timer_reset)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            IconButton(
                                onClick = onReset,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag(resetControlTestTag)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = resetDescription,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    trailingControls?.invoke(this)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("focus_timer_display")
    ) {
        if (useSplitLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    dialContent()
                }
                Box(modifier = Modifier.weight(1f)) {
                    controlsContent()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                dialContent()
                controlsContent()
            }
        }
    }
}

/**
 * Circular countdown dial showing smoothed progress, status badge, tabular remaining time,
 * and elapsed vs. planned duration metadata.
 */
@Composable
fun FocusTimerDial(
    remainingSeconds: Int,
    elapsedSeconds: Int,
    totalSeconds: Int,
    progress: Float,
    statusLabel: String,
    accentColor: Color,
    ringSize: Dp,
    countdownTestTag: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "focus_timer_dial_progress"
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    val glowCenterColor = accentColor.copy(alpha = 0.10f)
    val ringBrush = remember(accentColor) {
        Brush.sweepGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.65f),
                accentColor
            )
        )
    }

    val formattedRemaining = remember(remainingSeconds) { formatTimerDuration(remainingSeconds) }
    val formattedElapsed = remember(elapsedSeconds) { formatTimerDuration(elapsedSeconds) }
    val plannedMinutes = (totalSeconds / 60).coerceAtLeast(1)

    Box(
        modifier = modifier
            .size(ringSize)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Subtle interior depth glow (static gradient, zero infinite animation cost)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowCenterColor, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Recessed progress track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active progress arc
            val sweepAngle = (animatedProgress * 360f).coerceIn(0f, 360f)
            if (sweepAngle > 0.5f) {
                drawArc(
                    brush = ringBrush,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Crisp luminous indicator dot at the leading arc tip
                val angleRad = ((-90f + sweepAngle) * PI / 180.0)
                val tipX = center.x + radius * cos(angleRad).toFloat()
                val tipY = center.y + radius * sin(angleRad).toFloat()
                drawCircle(
                    color = accentColor,
                    radius = strokeWidth * 0.42f,
                    center = Offset(tipX, tipY)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .testTag("focus_timer_status_label")
                )
            }
            Text(
                text = formattedRemaining,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag(countdownTestTag)
            )
            Text(
                text = stringResource(
                    R.string.timer_elapsed_planned_summary,
                    formattedElapsed,
                    plannedMinutes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("focus_timer_elapsed_text")
            )
        }
    }
}

/**
 * Primary Start / Pause / Resume control button with smooth icon/label transition
 * and accessible content descriptions.
 */
@Composable
fun FocusTimerStartPauseButton(
    isRunning: Boolean,
    hasStarted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionLabel = when {
        isRunning -> stringResource(R.string.action_pause)
        hasStarted -> stringResource(R.string.action_resume)
        else -> stringResource(R.string.action_start)
    }
    val actionDescription = when {
        isRunning -> stringResource(R.string.cd_timer_pause)
        hasStarted -> stringResource(R.string.cd_timer_resume)
        else -> stringResource(R.string.cd_timer_start)
    }

    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = actionDescription }
    ) {
        AnimatedContent(
            targetState = isRunning to actionLabel,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
            label = "focus_timer_primary_control_label"
        ) { (running, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Preview(name = "Focus Timer Running - Light", showBackground = true)
@Composable
private fun FocusTimerDisplayRunningLightPreview() {
    BrainBufferTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FocusTimerDisplay(
                remainingSeconds = 24 * 60 + 54,
                elapsedSeconds = 6,
                totalSeconds = 25 * 60,
                progress = 6f / 1500f,
                isRunning = true,
                hasStarted = true,
                sessionState = FocusSessionState.FOCUSING,
                onToggleStartPause = {},
                onReset = {},
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Preview(
    name = "Focus Timer Paused - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FocusTimerDisplayPausedDarkPreview() {
    BrainBufferTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FocusTimerDisplay(
                remainingSeconds = 12 * 60 + 30,
                elapsedSeconds = 12 * 60 + 30,
                totalSeconds = 25 * 60,
                progress = 0.5f,
                isRunning = false,
                hasStarted = true,
                sessionState = FocusSessionState.PAUSED,
                onToggleStartPause = {},
                onReset = {},
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
