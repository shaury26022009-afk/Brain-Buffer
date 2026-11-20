package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.StudyActivityType
import com.example.performance.BlurSurfaceRole
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.viewmodel.FocusTimerUiState

fun iconForActivity(type: StudyActivityType): ImageVector = when (type) {
    StudyActivityType.STUDYING -> Icons.Rounded.School
    StudyActivityType.STUDY_PDF -> Icons.Rounded.AutoStories
    StudyActivityType.STUDY_LECTURE -> Icons.Rounded.PlayCircle
    StudyActivityType.READING -> Icons.Rounded.AutoStories
    StudyActivityType.WRITING_NOTES -> Icons.Rounded.EditNote
    StudyActivityType.SOLVING_QUESTIONS -> Icons.Rounded.Quiz
    StudyActivityType.SHORT_BREAK -> Icons.Rounded.Coffee
    StudyActivityType.PLAYING_GAME -> Icons.Rounded.SportsEsports
    StudyActivityType.ENTERTAINMENT -> Icons.Rounded.Movie
    StudyActivityType.OTHER -> Icons.Rounded.MoreHoriz
}

/**
 * BRAIN BUFFER LIVE — Dynamic Island-inspired floating translucent pill rendered in-app
 * whenever a study or activity timer is active while browsing other tabs.
 *
 * Compact state: activity icon, remaining countdown, and mini circular progress ring.
 * Expanded state: full activity title, subject, progress bar, Pause/Resume, Switch Activity,
 * Return to Study (when paused parent exists), and Open Focus / End controls.
 */
@Composable
fun BrainBufferLivePill(
    timer: FocusTimerUiState,
    subjectName: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenFocus: () -> Unit,
    onSwitchActivity: () -> Unit,
    onReturnToStudy: () -> Unit,
    onExtendFiveMin: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = BrainBufferThemeTokens.glass
    val renderQuality = BrainBufferThemeTokens.renderQuality
    var expanded by remember { mutableStateOf(false) }

    val accent = Color(timer.activeActivityType.accentHex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LiquidGlassCard(
            modifier = Modifier
                .widthIn(min = 196.dp, max = 420.dp)
                .then(
                    if (expanded) Modifier.fillMaxWidth() else Modifier
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium * renderQuality.springStiffnessScale
                    )
                )
                .testTag("brain_buffer_live_pill"),
            tone = LiquidGlassTone.ELEVATED,
            cornerRadius = if (expanded) 24.dp else 32.dp,
            tintOverlay = accent.copy(alpha = if (glass.isDark) 0.10f else 0.08f),
            borderAccent = accent.copy(alpha = if (glass.isDark) 0.44f else 0.34f),
            elevation = if (renderQuality.enableAmbientShadows) 12.dp else 2.dp,
            blurRole = BlurSurfaceRole.FLOATING_PILL,
            contentPadding = PaddingValues(
                horizontal = if (expanded) 16.dp else 14.dp,
                vertical = if (expanded) 14.dp else 9.dp
            )
        ) {
                // Compact top row (always visible — tapping toggles expanded controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .clickable { expanded = !expanded }
                        .testTag("brain_buffer_live_pill_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = expanded)
                    ) {
                        // Mini progress ring around activity icon
                        Box(
                            modifier = Modifier.size(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(30.dp)) {
                                val stroke = 2.6.dp.toPx()
                                drawArc(
                                    color = accent.copy(alpha = 0.20f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = accent,
                                    startAngle = -90f,
                                    sweepAngle = 360f * timer.progress.coerceIn(0f, 1f),
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Icon(
                                imageVector = iconForActivity(timer.activeActivityType),
                                contentDescription = timer.activeActivityLabel,
                                tint = accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (expanded) timer.activeActivityLabel else timer.activeActivityType.shortLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (expanded) {
                                Text(
                                    text = buildString {
                                        append(subjectName)
                                        if (timer.isSwitchedActivity) append(" · Study paused")
                                        else if (!timer.isRunning) append(" · Paused")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timer.formattedTime,
                            style = BrainBufferThemeTokens.textStyles.statCounter.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (timer.isRunning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("live_pill_time")
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Collapse live timer" else "Expand live timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expanded Dynamic Island controls
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassProgressBar(
                            progress = timer.progress.coerceIn(0f, 1f),
                            color = accent,
                            height = 6.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { if (timer.isRunning) onPause() else onResume() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("live_pill_play_pause"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (timer.isRunning) "Pause" else "Resume",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    expanded = false
                                    onSwitchActivity()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("live_pill_switch"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch", style = MaterialTheme.typography.labelLarge)
                            }

                            IconButton(
                                onClick = {
                                    expanded = false
                                    onOpenFocus()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .semantics { contentDescription = "Open full focus screen" }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (timer.isSwitchedActivity) {
                                FilledTonalButton(
                                    onClick = {
                                        expanded = false
                                        onReturnToStudy()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("live_pill_return_study"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Return to Study", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onExtendFiveMin,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text("+5 min", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    expanded = false
                                    onEndSession()
                                },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("live_pill_end"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("End", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
        }
    }
}
