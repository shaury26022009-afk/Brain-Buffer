package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AmbientSoundscape
import com.example.audio.SoundscapeState
import com.example.performance.BlurSurfaceRole
import com.example.ui.theme.BrainBufferThemeTokens
import kotlin.math.roundToInt

fun iconForSoundscape(soundscape: AmbientSoundscape): ImageVector = when (soundscape) {
    AmbientSoundscape.OFF -> Icons.Rounded.MusicOff
    AmbientSoundscape.SOFT_RAIN -> Icons.Rounded.WaterDrop
    AmbientSoundscape.BROWN_NOISE -> Icons.Rounded.Waves
    AmbientSoundscape.PINK_FOCUS -> Icons.Rounded.GraphicEq
    AmbientSoundscape.NIGHT_BREEZE -> Icons.Rounded.Air
    AmbientSoundscape.DEEP_DRONE -> Icons.Rounded.Spa
}

/**
 * Compact Liquid Glass pill displayed inside FocusScreen to open or monitor ambient audio.
 */
@Composable
fun FocusSoundscapePill(
    state: SoundscapeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = Color(state.selected.accentHex)
    val accent by animateColorAsState(
        targetValue = if (state.isActive) activeAccent else MaterialTheme.colorScheme.primary,
        animationSpec = tween(220),
        label = "soundscapePillAccent"
    )
    LiquidGlassSecondaryButton(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .testTag("focus_soundscape_button"),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (state.isActive) iconForSoundscape(state.selected) else Icons.Rounded.Headphones,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                state.isActive -> state.selected.label
                state.selected != AmbientSoundscape.OFF -> "${state.selected.label} (Paused)"
                else -> "Sounds"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (state.isActive) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSoundscapeSheet(
    state: SoundscapeState,
    onSelectSoundscape: (AmbientSoundscape) -> Unit,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSyncWithTimerChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground
    ) {
        FocusSoundscapeSheetContent(
            state = state,
            onSelectSoundscape = onSelectSoundscape,
            onTogglePlayPause = onTogglePlayPause,
            onVolumeChange = onVolumeChange,
            onSyncWithTimerChange = onSyncWithTimerChange,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun FocusSoundscapeSheetContent(
    state: SoundscapeState,
    onSelectSoundscape: (AmbientSoundscape) -> Unit,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSyncWithTimerChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val entries = AmbientSoundscape.entries
    val activeAccent = Color(state.selected.accentHex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .testTag("focus_soundscape_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Focus Soundscape",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "100% offline synthesized study acoustics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.selected != AmbientSoundscape.OFF) {
                LiquidGlassIconContainer(
                    size = 44.dp,
                    accentColor = activeAccent,
                    shape = CircleShape,
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("soundscape_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause soundscape" else "Play soundscape",
                        tint = activeAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2-column grid of 6 soundscape glass tiles
        entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { soundscape ->
                    val isSelected = state.selected == soundscape
                    val tileAccent = Color(soundscape.accentHex)
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("soundscape_option_${soundscape.name.lowercase()}"),
                        tone = if (isSelected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        cornerRadius = 18.dp,
                        tintOverlay = if (isSelected) tileAccent.copy(alpha = 0.14f) else null,
                        borderAccent = if (isSelected) tileAccent else null,
                        blurRole = if (isSelected) BlurSurfaceRole.MODAL_SHEET_OR_DIALOG else BlurSurfaceRole.SCROLLING_CARD,
                        contentPadding = PaddingValues(12.dp),
                        onClick = { onSelectSoundscape(soundscape) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidGlassIconContainer(
                                size = 34.dp,
                                accentColor = if (isSelected) tileAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = iconForSoundscape(soundscape),
                                    contentDescription = null,
                                    tint = if (isSelected) tileAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = soundscape.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) tileAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = soundscape.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Volume slider card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tone = LiquidGlassTone.SUBTLE,
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
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
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ambience Volume",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${(state.volume * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = state.volume,
                onValueChange = onVolumeChange,
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("soundscape_volume_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-sync with Focus Timer",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Play when focusing, pause smoothly on break",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = state.syncWithTimer,
                    onCheckedChange = onSyncWithTimerChange,
                    modifier = Modifier.testTag("soundscape_sync_switch")
                )
            }
        }

        LiquidGlassButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("soundscape_done_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
