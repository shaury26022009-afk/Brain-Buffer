package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AmbientSoundscape
import com.example.audio.SoundscapeCategory
import com.example.audio.SoundscapeState
import com.example.performance.BlurSurfaceRole
import com.example.ui.theme.BrainBufferThemeTokens
import kotlin.math.roundToInt

fun iconForSoundscape(soundscape: AmbientSoundscape): ImageVector = when (soundscape) {
    AmbientSoundscape.OFF -> Icons.Rounded.MusicOff
    AmbientSoundscape.WIND_CHIMES -> Icons.Rounded.Air
    AmbientSoundscape.PASTORAL -> Icons.Rounded.WbSunny
    AmbientSoundscape.BEACH -> Icons.Rounded.Waves
    AmbientSoundscape.SUMMER_NIGHT -> Icons.Rounded.NightsStay
    AmbientSoundscape.SOFT_RAIN -> Icons.Rounded.WaterDrop
    AmbientSoundscape.FOREST -> Icons.Rounded.Park
    AmbientSoundscape.BROWN_NOISE -> Icons.Rounded.Equalizer
    AmbientSoundscape.PINK_FOCUS -> Icons.Rounded.GraphicEq
    AmbientSoundscape.WHITE_NOISE -> Icons.Rounded.BlurOn
    AmbientSoundscape.PEACEFUL_FANTASY -> Icons.Rounded.AutoAwesome
    AmbientSoundscape.ENERGETIC_FOCUS -> Icons.Rounded.Bolt
    AmbientSoundscape.DEEP_FOCUS -> Icons.Rounded.Spa
}

/**
 * Compact Liquid Glass pill displayed inside Setup & Settings to open or monitor ambient audio.
 */
@Composable
fun FocusSoundscapePill(
    state: SoundscapeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSound = state.effectiveSoundscape
    val activeAccent = Color(activeSound.accentHex)
    val accent by animateColorAsState(
        targetValue = if (state.isActive) activeAccent else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "soundscapePillAccent"
    )
    LiquidGlassSecondaryButton(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .minimumInteractiveComponentSize()
            .testTag("focus_soundscape_button"),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (state.isActive) iconForSoundscape(activeSound) else Icons.Rounded.Headphones,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                state.isActive -> activeSound.label
                state.selected != AmbientSoundscape.OFF -> "${state.selected.label} · Paused"
                else -> "Sounds"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (state.isActive) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sleek quick-access Soundscape control bar embedded inside dedicated full-screen Focus Mode.
 * Shows the current sound, provides instant Play/Pause without leaving Focus Mode, and opens
 * the categorized Soundscape sheet on tap.
 */
@Composable
fun FocusModeSoundControlBar(
    state: SoundscapeState,
    onOpenSoundscapeSheet: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedSound = state.effectiveSoundscape
    val accent = if (displayedSound != AmbientSoundscape.OFF) {
        Color(displayedSound.accentHex)
    } else {
        MaterialTheme.colorScheme.primary
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("focus_mode_sound_bar"),
        tone = if (state.isActive) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
        cornerRadius = 20.dp,
        borderAccent = if (state.isActive) accent.copy(alpha = 0.55f) else null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onOpenSoundscapeSheet
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(
                size = 40.dp,
                accentColor = accent,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (displayedSound != AmbientSoundscape.OFF) {
                        iconForSoundscape(displayedSound)
                    } else {
                        Icons.Rounded.Headphones
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (displayedSound == AmbientSoundscape.OFF) {
                        "Focus Soundscape"
                    } else {
                        displayedSound.label
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        state.isActive -> "Playing · ${(state.volume * 100f).roundToInt()}% · Tap to change"
                        state.selected != AmbientSoundscape.OFF -> "Paused · Tap to customize"
                        else -> "12 offline nature & instrumental sounds"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (state.selected != AmbientSoundscape.OFF || state.isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.testTag("focus_mode_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause soundscape" else "Play soundscape",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Choose soundscape",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
    onDismiss: () -> Unit,
    onTogglePreview: (AmbientSoundscape) -> Unit = {},
    onStopPreview: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DisposableEffect(Unit) {
        onDispose { onStopPreview() }
    }
    ModalBottomSheet(
        onDismissRequest = {
            onStopPreview()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground
    ) {
        FocusSoundscapeSheetContent(
            state = state,
            onSelectSoundscape = onSelectSoundscape,
            onTogglePlayPause = onTogglePlayPause,
            onVolumeChange = onVolumeChange,
            onSyncWithTimerChange = onSyncWithTimerChange,
            onDismiss = {
                onStopPreview()
                onDismiss()
            },
            onTogglePreview = onTogglePreview
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
    onDismiss: () -> Unit,
    onTogglePreview: (AmbientSoundscape) -> Unit = {}
) {
    val natureSounds = remember { AmbientSoundscape.byCategory(SoundscapeCategory.NATURE_ATMOSPHERE) }
    val instrumentalSounds = remember { AmbientSoundscape.byCategory(SoundscapeCategory.FANTASY_INSTRUMENTAL) }
    val activeSound = state.effectiveSoundscape
    val activeAccent = if (activeSound != AmbientSoundscape.OFF) {
        Color(activeSound.accentHex)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
            .navigationBarsPadding()
            .testTag("focus_soundscape_sheet")
    ) {
        // Fixed Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
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
                    text = when {
                        state.isPreviewing -> "Previewing ${state.previewing?.label}"
                        state.selected != AmbientSoundscape.OFF && state.isPlaying ->
                            "Playing ${state.selected.label} · 100% offline"
                        state.selected != AmbientSoundscape.OFF ->
                            "Selected: ${state.selected.label} (Paused)"
                        else -> "12 offline nature & instrumental soundscapes"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.selected != AmbientSoundscape.OFF) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        onClick = { onSelectSoundscape(AmbientSoundscape.OFF) },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("soundscape_option_off")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicOff,
                                contentDescription = "Turn soundscape off",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Off",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
        }

        // Scrollable Content: Controls + Categorized Sound Library
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Volume & Auto-Sync Control Card
            item(key = "controls_card") {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = LiquidGlassTone.SUBTLE,
                    cornerRadius = 20.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    tint = activeAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Soundscape Volume",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = activeAccent.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${(state.volume * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = activeAccent,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                        .testTag("soundscape_volume_percent")
                                )
                            }
                        }

                        Slider(
                            value = state.volume,
                            onValueChange = onVolumeChange,
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = activeAccent,
                                activeTrackColor = activeAccent,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Start when focus begins, pause cleanly on break or end",
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
                }
            }

            // Category 1: Nature & Atmosphere
            item(key = "cat_nature") {
                SoundscapeCategoryHeader(
                    category = SoundscapeCategory.NATURE_ATMOSPHERE,
                    count = natureSounds.size
                )
            }

            items(natureSounds, key = { it.name }) { soundscape ->
                SoundscapeOptionCard(
                    soundscape = soundscape,
                    isSelected = state.selected == soundscape,
                    isPlayingThis = state.isActive && state.effectiveSoundscape == soundscape,
                    isPreviewingThis = state.isPreviewing && state.previewing == soundscape,
                    onSelect = { onSelectSoundscape(soundscape) },
                    onTogglePreview = { onTogglePreview(soundscape) }
                )
            }

            // Category 2: Fantasy & Instrumental
            item(key = "cat_fantasy") {
                Spacer(modifier = Modifier.height(4.dp))
                SoundscapeCategoryHeader(
                    category = SoundscapeCategory.FANTASY_INSTRUMENTAL,
                    count = instrumentalSounds.size
                )
            }

            items(instrumentalSounds, key = { it.name }) { soundscape ->
                SoundscapeOptionCard(
                    soundscape = soundscape,
                    isSelected = state.selected == soundscape,
                    isPlayingThis = state.isActive && state.effectiveSoundscape == soundscape,
                    isPreviewingThis = state.isPreviewing && state.previewing == soundscape,
                    onSelect = { onSelectSoundscape(soundscape) },
                    onTogglePreview = { onTogglePreview(soundscape) }
                )
            }
        }

        // Fixed Done CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            LiquidGlassButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("soundscape_done_button")
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SoundscapeCategoryHeader(
    category: SoundscapeCategory,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SoundscapeOptionCard(
    soundscape: AmbientSoundscape,
    isSelected: Boolean,
    isPlayingThis: Boolean,
    isPreviewingThis: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    val tileAccent = Color(soundscape.accentHex)
    val cardBorder = when {
        isSelected -> tileAccent
        isPreviewingThis -> tileAccent.copy(alpha = 0.7f)
        else -> null
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("soundscape_option_${soundscape.name.lowercase()}"),
        tone = if (isSelected || isPreviewingThis) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
        cornerRadius = 18.dp,
        tintOverlay = if (isSelected || isPreviewingThis) tileAccent.copy(alpha = 0.13f) else null,
        borderAccent = cardBorder,
        blurRole = BlurSurfaceRole.SCROLLING_CARD,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(
                size = 42.dp,
                accentColor = if (isSelected || isPreviewingThis) tileAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = iconForSoundscape(soundscape),
                    contentDescription = null,
                    tint = if (isSelected || isPreviewingThis) tileAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = soundscape.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isSelected) tileAccent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = tileAccent.copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = tileAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isPlayingThis) "Playing" else "Selected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tileAccent
                                )
                            }
                        }
                    } else if (isPreviewingThis) {
                        Surface(
                            shape = CircleShape,
                            color = tileAccent.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tileAccent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = soundscape.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Independent Preview / Stop button
            Surface(
                shape = CircleShape,
                color = if (isPreviewingThis) {
                    tileAccent.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isPreviewingThis) tileAccent.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
            ) {
                IconButton(
                    onClick = onTogglePreview,
                    modifier = Modifier.testTag("soundscape_preview_${soundscape.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = if (isPreviewingThis) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPreviewingThis) {
                            "Stop previewing ${soundscape.label}"
                        } else {
                            "Preview ${soundscape.label}"
                        },
                        tint = if (isPreviewingThis) tileAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
