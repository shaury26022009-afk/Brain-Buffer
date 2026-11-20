package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GardenDecoration
import com.example.model.GardenPlant
import com.example.model.PlantSpecies
import com.example.model.PlantStage
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassProgressBar
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    uiState: BrainBufferUiState,
    onSelectActivePlant: (Long) -> Unit,
    onNurtureWithSeed: () -> Boolean,
    onPlantNewSeed: (PlantSpecies, String) -> Boolean,
    onStartFocus: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val activePlant = uiState.activePlant
    val seeds = uiState.preferences.seedsOwned
    val unlockedDecorations = uiState.preferences.unlockedDecorations
    var showSeedSheet by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2500)
            feedbackMessage = null
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
                    .testTag("garden_screen"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 14.dp,
                    bottom = adaptive.bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                // Header + Earned Seeds Badge
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "My Study Garden",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${uiState.activeDaysThisWeek}/${uiState.preferences.weeklyStudyDaysTarget} study days this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LiquidGlassCard(
                            tone = LiquidGlassTone.ACCENT,
                            cornerRadius = 50.dp,
                            borderAccent = MaterialTheme.colorScheme.secondary,
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("garden_seeds_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Eco,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "$seeds Seeds",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (feedbackMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = feedbackMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                if (adaptive.useTwoColumnLayout || adaptive.useCompactLandscapeSplit) {
                    item {
                        val canvasHeight = if (adaptive.isCompactHeight) 224.dp else 310.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("garden_tablet_split"),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Column: Wide living garden canvas + botanical collection
                            Column(
                                modifier = Modifier.weight(1.15f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MyStudyGardenCanvas(
                                    plants = uiState.plants,
                                    unlockedDecorations = unlockedDecorations,
                                    sparkleTrigger = uiState.gardenSparkleTrigger,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(canvasHeight)
                                        .testTag("living_garden_canvas")
                                )

                                SectionHeader(
                                    title = "Your Plants",
                                    subtitle = "Tap any plant to grow it next",
                                    actionLabel = "Plant Seed",
                                    onActionClick = { showSeedSheet = true }
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    uiState.plants.forEach { plant ->
                                        GardenPlantCollectionCard(
                                            plant = plant,
                                            onSelectActivePlant = onSelectActivePlant
                                        )
                                    }
                                }
                            }

                            // Right Column: Active plant spotlight + garden decorations
                            Column(
                                modifier = Modifier.weight(0.95f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ActivePlantSpotlightSection(
                                    activePlant = activePlant,
                                    onPlantStarter = { showSeedSheet = true },
                                    onStartFocus = onStartFocus,
                                    onNurtureWithSeed = {
                                        val ok = onNurtureWithSeed()
                                        feedbackMessage = if (ok) {
                                            "Nurtured ${activePlant?.nickname ?: "plant"} with a seed!"
                                        } else {
                                            "Complete a focus session to earn more seeds."
                                        }
                                    }
                                )

                                SectionHeader(
                                    title = "Garden Decorations",
                                    subtitle = "${unlockedDecorations.size} of ${GardenDecoration.entries.size} unlocked"
                                )

                                GardenDecorationsGrid(unlockedDecorations = unlockedDecorations)
                            }
                        }
                    }
                } else {
                    // Phone Portrait: Single column serene flow
                    item {
                        MyStudyGardenCanvas(
                            plants = uiState.plants,
                            unlockedDecorations = unlockedDecorations,
                            sparkleTrigger = uiState.gardenSparkleTrigger,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(256.dp)
                                .testTag("living_garden_canvas")
                        )
                    }

                    item {
                        ActivePlantSpotlightSection(
                            activePlant = activePlant,
                            onPlantStarter = { showSeedSheet = true },
                            onStartFocus = onStartFocus,
                            onNurtureWithSeed = {
                                val ok = onNurtureWithSeed()
                                feedbackMessage = if (ok) {
                                    "Nurtured ${activePlant?.nickname ?: "plant"} with a seed!"
                                } else {
                                    "Complete a focus session to earn more seeds."
                                }
                            }
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Garden Decorations",
                            subtitle = "${unlockedDecorations.size} of ${GardenDecoration.entries.size} unlocked"
                        )
                    }

                    item {
                        GardenDecorationsGrid(unlockedDecorations = unlockedDecorations)
                    }

                    item {
                        SectionHeader(
                            title = "Your Plants",
                            subtitle = "Tap any plant to grow it next",
                            actionLabel = "Plant Seed",
                            onActionClick = { showSeedSheet = true }
                        )
                    }

                    items(uiState.plants, key = { it.id }) { plant ->
                        GardenPlantCollectionCard(
                            plant = plant,
                            onSelectActivePlant = onSelectActivePlant
                        )
                    }
                }
            }
        }
    }

    if (showSeedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSeedSheet = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            PlantSeedModalContent(
                seedsAvailable = seeds,
                isFirstPlantFree = uiState.plants.isEmpty(),
                onDismiss = { showSeedSheet = false },
                onPlant = { species, nickname ->
                    val planted = onPlantNewSeed(species, nickname)
                    if (!planted) {
                        feedbackMessage = "Complete a focus session to earn a seed first!"
                    }
                    showSeedSheet = false
                }
            )
        }
    }
}

@Composable
private fun ActivePlantSpotlightSection(
    activePlant: GardenPlant?,
    onPlantStarter: () -> Unit,
    onStartFocus: () -> Unit,
    onNurtureWithSeed: () -> Unit
) {
    if (activePlant == null) {
        EmptySanctuaryCard(
            icon = Icons.Rounded.LocalFlorist,
            title = "Plant your first study seed",
            message = "Your garden grows naturally every time you finish a focus session. Zero penalties for rest days.",
            actionLabel = "Plant Starter Seed",
            onAction = onPlantStarter
        )
    } else {
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_plant_card"),
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE PLANT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = activePlant.nickname,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${activePlant.species.displayName} · ${activePlant.stage.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LiquidGlassIconContainer(
                    size = 50.dp,
                    accentColor = activePlant.species.petalColor,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFlorist,
                        contentDescription = null,
                        tint = activePlant.species.petalColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val nextStage = activePlant.stage.nextStage()
            LiquidGlassProgressBar(
                progress = activePlant.progressToNextStage,
                color = activePlant.species.petalColor,
                height = 7.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (nextStage != null) {
                    "${activePlant.sessionsUntilNextStage} more focus session(s) to reach ${nextStage.label}"
                } else {
                    "Radiant Canopy reached! Plant a new seed whenever you'd like."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassButton(
                    onClick = onStartFocus,
                    cornerRadius = 15.dp,
                    height = 48.dp,
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("garden_start_focus_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Focus to Grow", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                }

                LiquidGlassSecondaryButton(
                    onClick = onNurtureWithSeed,
                    accentBorder = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    cornerRadius = 15.dp,
                    height = 48.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("garden_nurture_seed_button")
                ) {
                    Icon(Icons.Rounded.Eco, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Boost (-1)", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GardenDecorationsGrid(unlockedDecorations: Set<String>) {
    val extended = BrainBufferThemeTokens.extended
    val rows = remember { GardenDecoration.entries.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { deco ->
                    val isUnlocked = deco.key in unlockedDecorations
                    SanctuaryCard(
                        modifier = Modifier.weight(1f),
                        containerColor = if (isUnlocked) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        borderColor = if (isUnlocked) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                        } else {
                            extended.cardBorder
                        },
                        contentPadding = PaddingValues(13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isUnlocked) "Placed" else "Locked",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = deco.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = deco.unlockHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GardenPlantCollectionCard(
    plant: GardenPlant,
    onSelectActivePlant: (Long) -> Unit
) {
    val plantedText = remember(plant.plantedDateEpochDay) {
        LocalDate.ofEpochDay(plant.plantedDateEpochDay)
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
    SanctuaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("garden_plant_${plant.id}"),
        borderColor = if (plant.isActive) plant.species.petalColor else BrainBufferThemeTokens.extended.cardBorder,
        contentPadding = PaddingValues(14.dp),
        onClick = { onSelectActivePlant(plant.id) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(
                size = 44.dp,
                accentColor = plant.species.petalColor,
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFlorist,
                    contentDescription = null,
                    tint = plant.species.petalColor,
                    modifier = Modifier.size(23.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = plant.nickname,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (plant.isActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${plant.stage.label} · ${plant.sessionsLogged} sessions · ${plant.totalMinutes}m · $plantedText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MyStudyGardenCanvas(
    plants: List<GardenPlant>,
    unlockedDecorations: Set<String>,
    sparkleTrigger: Int,
    modifier: Modifier = Modifier
) {
    val extended = BrainBufferThemeTokens.extended
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val renderQuality = BrainBufferThemeTokens.renderQuality
    val isAppInForeground = BrainBufferThemeTokens.isAppInForeground
    val animateCanvas = !reducedMotion && renderQuality.enableContinuousCanvasMotion && isAppInForeground

    val swayPhase = if (animateCanvas) {
        val transition = rememberInfiniteTransition(label = "gardenSway")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "swayAngle"
        )
        value
    } else {
        0f
    }

    val fireflyGlow = if (animateCanvas) {
        val transition = rememberInfiniteTransition(label = "fireflyGlow")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "fireflyAlpha"
        )
        value
    } else {
        0.7f
    }

    var sparkleActive by remember { mutableStateOf(false) }
    LaunchedEffect(sparkleTrigger) {
        if (sparkleTrigger > 0) {
            sparkleActive = true
            delay(1300)
            sparkleActive = false
        }
    }

    val growthPulseScale by animateFloatAsState(
        targetValue = if (sparkleActive && !reducedMotion) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gardenGrowthPulse"
    )

    LiquidGlassCard(
        modifier = modifier,
        tone = LiquidGlassTone.ELEVATED,
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Pure AMOLED black night sky in dark mode / soft lavender daylight in light mode
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(extended.gardenSkyTop, extended.gardenSkyBottom)
                )
            )

            // Delicate twilight starlight dots in AMOLED dark mode (scaled by performance profile)
            if (extended.isDark && renderQuality.gardenStarCount > 0) {
                listOf(
                    Triple(0.14f, 0.16f, 1.4f),
                    Triple(0.32f, 0.11f, 1.1f),
                    Triple(0.49f, 0.19f, 1.5f),
                    Triple(0.64f, 0.12f, 1.2f),
                    Triple(0.24f, 0.28f, 1.0f),
                    Triple(0.72f, 0.25f, 1.3f)
                ).take(renderQuality.gardenStarCount).forEachIndexed { idx, (rx, ry, rDp) ->
                    val twinkle = (0.28f + 0.52f * ((fireflyGlow + idx * 0.17f) % 1f))
                    drawCircle(
                        color = Color(0xFFEDE8FF).copy(alpha = twinkle),
                        radius = rDp.dp.toPx(),
                        center = Offset(w * rx, h * ry)
                    )
                }
            }

            // Moon / Sun
            val orbCenter = Offset(w * 0.84f, h * 0.22f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (extended.isDark) 0.24f else 0.80f), Color.Transparent),
                    center = orbCenter,
                    radius = w * 0.18f
                ),
                radius = w * 0.18f,
                center = orbCenter
            )
            drawCircle(
                color = if (extended.isDark) Color(0xFFEDE8FF) else Color(0xFFFFFBF2),
                radius = w * 0.048f,
                center = orbCenter
            )

            // Optional unlocked Starry Arch behind hills
            if (GardenDecoration.STARRY_ARCH.key in unlockedDecorations) {
                drawArc(
                    color = Color(0xFFB79EFF).copy(alpha = 0.42f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.24f),
                    size = Size(w * 0.56f, h * 0.86f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Distant hill
            val farHill = Path().apply {
                moveTo(0f, h * 0.70f)
                quadraticTo(w * 0.30f, h * 0.56f, w * 0.60f, h * 0.68f)
                quadraticTo(w * 0.84f, h * 0.76f, w, h * 0.64f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(farHill, color = extended.gardenHillFar)

            // Foreground hill
            val nearHill = Path().apply {
                moveTo(0f, h * 0.79f)
                quadraticTo(w * 0.36f, h * 0.69f, w * 0.70f, h * 0.78f)
                quadraticTo(w * 0.88f, h * 0.83f, w, h * 0.75f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(nearHill, color = extended.gardenHillNear)

            // Unlocked Stepping Stones decoration
            if (GardenDecoration.STEPPING_STONES.key in unlockedDecorations) {
                val stoneColor = if (extended.isDark) Color(0xFF3A3A40) else Color(0xFFD6CEE8)
                listOf(0.28f to 0.91f, 0.38f to 0.89f, 0.48f to 0.92f).forEach { (rx, ry) ->
                    drawOval(
                        color = stoneColor,
                        topLeft = Offset(w * rx, h * ry),
                        size = Size(22.dp.toPx(), 7.dp.toPx())
                    )
                }
            }

            // Unlocked Warm Lantern decoration
            if (GardenDecoration.STONE_LANTERN.key in unlockedDecorations) {
                val lx = w * 0.14f
                val ly = h * 0.76f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(extended.fireflyGold.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(lx, ly - 18.dp.toPx()),
                        radius = 28.dp.toPx()
                    ),
                    radius = 28.dp.toPx(),
                    center = Offset(lx, ly - 18.dp.toPx())
                )
                drawRect(
                    color = Color(0xFF6E6E78),
                    topLeft = Offset(lx - 4.dp.toPx(), ly - 14.dp.toPx()),
                    size = Size(8.dp.toPx(), 16.dp.toPx())
                )
                drawCircle(
                    color = extended.fireflyGold,
                    radius = 5.dp.toPx(),
                    center = Offset(lx, ly - 18.dp.toPx())
                )
            }

            // Unlocked Quiet Bench decoration
            if (GardenDecoration.WOODEN_BENCH.key in unlockedDecorations) {
                val bx = w * 0.76f
                val by = h * 0.76f
                val wood = Color(0xFF8C6B54)
                drawRect(wood, Offset(bx, by), Size(32.dp.toPx(), 4.dp.toPx()))
                drawRect(wood, Offset(bx + 3.dp.toPx(), by + 4.dp.toPx()), Size(3.dp.toPx(), 9.dp.toPx()))
                drawRect(wood, Offset(bx + 26.dp.toPx(), by + 4.dp.toPx()), Size(3.dp.toPx(), 9.dp.toPx()))
            }

            // Plants
            val visiblePlants = plants.takeLast(5)
            val count = visiblePlants.size.coerceAtLeast(1)
            visiblePlants.forEachIndexed { index, plant ->
                val slotX = w * (0.18f + (0.64f * (index + 0.5f) / count))
                val baseY = h * (if (index % 2 == 0) 0.83f else 0.78f)
                val swayOffset = sin(swayPhase + index * 1.1f) * (if (reducedMotion) 0f else 5.5f)
                drawGardenPlant(
                    plant = plant,
                    base = Offset(slotX, baseY),
                    swayX = swayOffset,
                    scaleFactor = if (plant.isActive) 1.1f * growthPulseScale else 0.92f
                )
            }

            // Floating gentle fireflies (scaled by performance profile)
            if (renderQuality.gardenFireflyCount > 0) {
                listOf(
                    Offset(0.20f, 0.34f),
                    Offset(0.42f, 0.27f),
                    Offset(0.62f, 0.40f),
                    Offset(0.76f, 0.31f)
                ).take(renderQuality.gardenFireflyCount).forEachIndexed { i, rel ->
                    val dx = cos(swayPhase + i * 1.3f) * (if (!animateCanvas) 0f else 7f)
                    val dy = sin(swayPhase * 1.2f + i) * (if (!animateCanvas) 0f else 5f)
                    val pos = Offset(w * rel.x + dx, h * rel.y + dy)
                    val alpha = if (sparkleActive) 0.95f else (0.35f + 0.5f * fireflyGlow)
                    drawCircle(
                        color = extended.fireflyGold.copy(alpha = alpha * 0.35f),
                        radius = if (sparkleActive) 11.dp.toPx() else 6.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = extended.fireflyGold.copy(alpha = alpha),
                        radius = if (sparkleActive) 3.5.dp.toPx() else 2.2.dp.toPx(),
                        center = pos
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGardenPlant(
    plant: GardenPlant,
    base: Offset,
    swayX: Float,
    scaleFactor: Float
) {
    val stage = plant.stage
    val stemColor = plant.species.foliageColor
    val petalColor = plant.species.petalColor
    val accentPetal = plant.species.secondaryColor

    if (plant.isActive) {
        drawOval(
            color = petalColor.copy(alpha = 0.26f),
            topLeft = Offset(base.x - 24.dp.toPx(), base.y - 6.dp.toPx()),
            size = Size(48.dp.toPx(), 13.dp.toPx())
        )
    }
    drawOval(
        color = Color(0xFF3F384A).copy(alpha = 0.5f),
        topLeft = Offset(base.x - 14.dp.toPx(), base.y - 4.dp.toPx()),
        size = Size(28.dp.toPx(), 9.dp.toPx())
    )

    val unit = 11.5.dp.toPx() * scaleFactor
    when (stage) {
        PlantStage.SEED -> {
            drawOval(
                color = petalColor,
                topLeft = Offset(base.x - unit * 0.45f, base.y - unit * 0.6f),
                size = Size(unit * 0.9f, unit * 0.65f)
            )
        }
        PlantStage.SPROUT -> {
            val top = Offset(base.x + swayX * 0.4f, base.y - unit * 2.2f)
            drawLine(stemColor, base, top, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawOval(stemColor, Offset(top.x - unit * 0.95f, top.y - unit * 0.35f), Size(unit * 0.95f, unit * 0.55f))
            drawOval(accentPetal, Offset(top.x, top.y - unit * 0.45f), Size(unit * 0.95f, unit * 0.55f))
        }
        PlantStage.BUDDING -> {
            val top = Offset(base.x + swayX * 0.7f, base.y - unit * 4.2f)
            val stem = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x + swayX * 0.3f, base.y - unit * 2.1f, top.x, top.y)
            }
            drawPath(stem, stemColor, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            drawOval(stemColor, Offset(base.x - unit * 1.25f, base.y - unit * 2.2f), Size(unit * 1.15f, unit * 0.6f))
            drawOval(stemColor, Offset(base.x + unit * 0.1f, base.y - unit * 2.8f), Size(unit * 1.15f, unit * 0.6f))
            drawCircle(petalColor, unit * 0.7f, top)
            drawCircle(accentPetal, unit * 0.35f, Offset(top.x, top.y - unit * 0.15f))
        }
        PlantStage.BLOOMING -> {
            val top = Offset(base.x + swayX, base.y - unit * 5.6f)
            val stem = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x + swayX * 0.4f, base.y - unit * 2.8f, top.x, top.y)
            }
            drawPath(stem, stemColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawOval(stemColor, Offset(base.x - unit * 1.5f, base.y - unit * 2.5f), Size(unit * 1.4f, unit * 0.72f))
            drawOval(stemColor, Offset(base.x + unit * 0.15f, base.y - unit * 3.4f), Size(unit * 1.4f, unit * 0.72f))
            for (i in 0 until 5) {
                val angle = (i * 72.0) * PI / 180.0
                drawCircle(
                    petalColor,
                    unit * 0.6f,
                    Offset(top.x + cos(angle).toFloat() * unit * 0.75f, top.y + sin(angle).toFloat() * unit * 0.75f)
                )
            }
            drawCircle(accentPetal, unit * 0.45f, top)
            drawCircle(Color(0xFFFFE59E), unit * 0.22f, top)
        }
        PlantStage.RADIANT -> {
            val top = Offset(base.x + swayX, base.y - unit * 6.8f)
            val trunk = Path().apply {
                moveTo(base.x, base.y)
                cubicTo(base.x - unit * 0.6f, base.y - unit * 2.4f, base.x + unit * 0.6f + swayX * 0.4f, base.y - unit * 4.4f, top.x, top.y)
            }
            drawPath(trunk, stemColor, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(
                Brush.radialGradient(listOf(petalColor.copy(alpha = 0.32f), Color.Transparent), top, unit * 3.1f),
                unit * 3.1f,
                top
            )
            listOf(
                Triple(Offset(top.x - unit * 1.25f, top.y + unit * 0.7f), unit * 1.1f, stemColor),
                Triple(Offset(top.x + unit * 1.25f, top.y + unit * 0.55f), unit * 1.05f, stemColor),
                Triple(Offset(top.x - unit * 0.8f, top.y - unit * 0.2f), unit * 1.05f, petalColor),
                Triple(Offset(top.x + unit * 0.8f, top.y - unit * 0.25f), unit * 1.0f, petalColor),
                Triple(Offset(top.x, top.y - unit * 0.7f), unit * 1.15f, accentPetal)
            ).forEach { (c, r, col) -> drawCircle(col, r, c) }
            drawCircle(Color(0xFFFFE8A6), unit * 0.28f, Offset(top.x, top.y - unit * 0.5f))
        }
    }
}

@Composable
private fun PlantSeedModalContent(
    seedsAvailable: Int,
    isFirstPlantFree: Boolean,
    onDismiss: () -> Unit,
    onPlant: (PlantSpecies, String) -> Unit
) {
    var selectedSpecies by remember { mutableStateOf(PlantSpecies.LAVENDER_SPIRE) }
    var nickname by remember { mutableStateOf("") }
    val canPlant = isFirstPlantFree || seedsAvailable > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Plant a New Seed", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (isFirstPlantFree) {
                "Your starter seed is complimentary."
            } else {
                "Uses 1 earned seed ($seedsAvailable available)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlantSpecies.entries.forEach { species ->
                val selected = selectedSpecies == species
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .springClickable(onClick = { selectedSpecies = species }),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) species.petalColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (selected) species.petalColor else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(species.petalColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LocalFlorist, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        Column {
                            Text(species.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(species.botanicalTag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= 24) nickname = it },
            label = { Text("Nickname (optional)") },
            placeholder = { Text(selectedSpecies.displayName) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onPlant(selectedSpecies, nickname) },
                enabled = canPlant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Plant Seed")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
