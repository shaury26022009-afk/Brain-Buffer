package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.performance.BlurSurfaceRole
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LocalAdaptiveMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private data class MeasuredVerticalSlot(
    val centerY: Float = 0f,
    val height: Float = 0f,
    val isMeasured: Boolean = false
)

/**
 * Floating vertical Liquid Glass navigation rail for tablets and landscape windows.
 *
 * Shares the exact same single-track + single-moving-capsule glass language, spring physics,
 * long-press expansion, and finger-following drag gesture as [BrainBufferBottomBar].
 * Never rendered simultaneously with [BrainBufferBottomBar].
 */
@Composable
fun BrainBufferLiquidGlassRail(
    currentDestination: BottomNavDestination,
    onSelectDestination: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    isFocusTimerRunning: Boolean = false,
    hapticsEnabled: Boolean = true
) {
    val extended = BrainBufferThemeTokens.extended
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val renderQuality = BrainBufferThemeTokens.renderQuality
    val blurController = BrainBufferThemeTokens.blurController
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = extended.isDark
    val palette = remember(isDark) { liquidGlassPalette(isDark) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val destinations = BottomNavDestination.entries
    val itemCount = destinations.size
    val selectedIndex = remember(currentDestination) {
        destinations.indexOf(currentDestination).coerceIn(0, destinations.lastIndex)
    }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelectDestination)
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)

    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var focusedPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var isInteractiveExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isPointerCancelledOutside by remember { mutableStateOf(false) }
    var lastDetentIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(isDragging) {
        blurController.setInteractiveMotion(isDragging)
    }
    DisposableEffect(blurController) {
        onDispose { blurController.setInteractiveMotion(false) }
    }

    val lensPositionAnim = remember { Animatable(selectedIndex.toFloat()) }
    val lensExpandAnim = remember { Animatable(0f) }
    val lensVelocityStretchAnim = remember { Animatable(0f) }
    val pressDipAnim = remember { Animatable(0f) }

    val measuredSlots = remember {
        mutableStateListOf<MeasuredVerticalSlot>().apply {
            repeat(itemCount) { add(MeasuredVerticalSlot()) }
        }
    }

    val restingTargetIndex = if (isInteractiveExpanded && !isPointerCancelledOutside) {
        focusedPreviewIndex ?: selectedIndex
    } else {
        selectedIndex
    }

    LaunchedEffect(restingTargetIndex, isDragging, reducedMotion) {
        if (!isDragging) {
            val target = restingTargetIndex.toFloat()
            val distance = target - lensPositionAnim.value
            if (reducedMotion) {
                lensPositionAnim.snapTo(target)
                lensVelocityStretchAnim.snapTo(0f)
            } else {
                if (abs(distance) > 0.15f) {
                    launch {
                        val impulse = sign(distance) * (abs(distance) * 0.42f).coerceIn(0.20f, 0.90f)
                        lensVelocityStretchAnim.animateTo(
                            targetValue = impulse,
                            animationSpec = tween(durationMillis = 110)
                        )
                        lensVelocityStretchAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 240f)
                        )
                    }
                }
                lensPositionAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.70f,
                        stiffness = 275f * renderQuality.springStiffnessScale
                    )
                )
            }
        }
    }

    LaunchedEffect(isInteractiveExpanded, isPointerCancelledOutside, reducedMotion) {
        val targetExpand = if (isInteractiveExpanded && !isPointerCancelledOutside) 1f else 0f
        if (reducedMotion) {
            lensExpandAnim.snapTo(targetExpand)
        } else {
            lensExpandAnim.animateTo(
                targetValue = targetExpand,
                animationSpec = spring(
                    dampingRatio = if (targetExpand > 0.5f) 0.58f else 0.72f,
                    stiffness = if (targetExpand > 0.5f) 300f else 340f
                )
            )
        }
    }

    val isLightlyDepressed = pressedIndex != null && !isInteractiveExpanded
    LaunchedEffect(isLightlyDepressed, reducedMotion) {
        val targetDip = if (isLightlyDepressed) 1f else 0f
        if (reducedMotion) {
            pressDipAnim.snapTo(targetDip)
        } else {
            pressDipAnim.animateTo(
                targetValue = targetDip,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)
            )
        }
    }

    val railTrackWidth = 74.dp
    val horizontalHeadroom = 12.dp
    val totalRailWidth = railTrackWidth + horizontalHeadroom * 2
    val verticalInset = 6.dp
    val showBrandLogo = !adaptive.isCompactHeight

    Column(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(start = 10.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showBrandLogo) {
            BrainBufferLogoMark(
                size = 30.dp,
                animatePulse = false,
                glowAlpha = 0f
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        BoxWithConstraints(
            modifier = Modifier
                .width(totalRailWidth)
                .heightIn(min = 276.dp, max = 420.dp)
                .weight(1f, fill = false)
                .testTag("brain_buffer_nav_rail")
        ) {
            val totalHeightPx = with(density) { maxHeight.toPx() }
            val railWidthPx = with(density) { railTrackWidth.toPx() }
            val headroomPx = with(density) { horizontalHeadroom.toPx() }
            val verticalInsetPx = with(density) { verticalInset.toPx() }
            val horizontalCancelThresholdPx = with(density) { 108.dp.toPx() }
            val railCornerPx = railWidthPx * 0.48f

            val innerHeightPx = (totalHeightPx - verticalInsetPx * 2f).coerceAtLeast(1f)
            val fallbackSlotHeightPx = innerHeightPx / itemCount.toFloat()

            fun slotCenterInRailPx(index: Int): Float {
                val safe = index.coerceIn(0, itemCount - 1)
                val slot = measuredSlots[safe]
                return if (slot.isMeasured && slot.height > 0f) {
                    verticalInsetPx + slot.centerY
                } else {
                    verticalInsetPx + (safe + 0.5f) * fallbackSlotHeightPx
                }
            }

            fun slotHeightAtPx(index: Int): Float {
                val safe = index.coerceIn(0, itemCount - 1)
                val slot = measuredSlots[safe]
                return if (slot.isMeasured && slot.height > 0f) slot.height else fallbackSlotHeightPx
            }

            fun interpolatedCenterInRailPx(fractionalIndex: Float): Float {
                val clamped = fractionalIndex.coerceIn(0f, (itemCount - 1).toFloat())
                val lo = floor(clamped).toInt().coerceIn(0, itemCount - 1)
                val hi = ceil(clamped).toInt().coerceIn(0, itemCount - 1)
                val t = clamped - lo
                return slotCenterInRailPx(lo) * (1f - t) + slotCenterInRailPx(hi) * t
            }

            fun interpolatedSlotHeightPx(fractionalIndex: Float): Float {
                val clamped = fractionalIndex.coerceIn(0f, (itemCount - 1).toFloat())
                val lo = floor(clamped).toInt().coerceIn(0, itemCount - 1)
                val hi = ceil(clamped).toInt().coerceIn(0, itemCount - 1)
                val t = clamped - lo
                return slotHeightAtPx(lo) * (1f - t) + slotHeightAtPx(hi) * t
            }

            fun columnYToFractionalIndex(colLocalY: Float): Float {
                val railY = colLocalY + verticalInsetPx
                val firstCenter = slotCenterInRailPx(0)
                val lastCenter = slotCenterInRailPx(itemCount - 1)
                if (railY <= firstCenter) return 0f
                if (railY >= lastCenter) return (itemCount - 1).toFloat()

                for (i in 0 until itemCount - 1) {
                    val topC = slotCenterInRailPx(i)
                    val botC = slotCenterInRailPx(i + 1)
                    if (railY in topC..botC) {
                        val span = (botC - topC).coerceAtLeast(1f)
                        return i + ((railY - topC) / span)
                    }
                }
                return ((colLocalY / fallbackSlotHeightPx) - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
            }

            fun columnYToSlotIndex(colLocalY: Float): Int {
                return columnYToFractionalIndex(colLocalY).roundToInt().coerceIn(0, itemCount - 1)
            }

            // 1. ONE Continuous Floating Translucent Glass Rail Track
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(railTrackWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = if (!renderQuality.enableAmbientShadows) 2.dp else if (isDark) 12.dp else 14.dp,
                        shape = RoundedCornerShape(36.dp),
                        ambientColor = palette.trackShadow,
                        spotColor = palette.trackShadow,
                        clip = false
                    )
                    .clip(RoundedCornerShape(36.dp))
            ) {
                LiquidGlassTrackFill(
                    palette = palette,
                    blurRole = BlurSurfaceRole.NAVIGATION_TRACK
                )
            }

            // 2. ONE Track Border + ONE Moving & Expanding Crystal Liquid Glass Capsule
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLiquidGlassRailBorder(
                    palette = palette,
                    railLeftOffsetPx = headroomPx,
                    railWidthPx = railWidthPx,
                    cornerRadiusPx = railCornerPx
                )

                val lensPos = lensPositionAnim.value
                val expand = lensExpandAnim.value
                val velStretchSigned = if (currentReducedMotion) 0f else lensVelocityStretchAnim.value
                val velMag = abs(velStretchSigned).coerceIn(0f, 1.15f)
                val distEnergy = if (currentReducedMotion || isDragging) 0f else {
                    abs(restingTargetIndex.toFloat() - lensPos).coerceIn(0f, 1.15f)
                }
                val travelEnergy = maxOf(velMag, distEnergy)
                val pressDip = pressDipAnim.value

                val measuredSlotH = interpolatedSlotHeightPx(lensPos)
                val baseLensHeight = (measuredSlotH - 4.dp.toPx()).coerceAtLeast(48.dp.toPx())
                val stretchHeight = measuredSlotH * 0.20f * travelEnergy
                val expandHeight = 16.dp.toPx() * expand
                val dipHeight = -1.5.dp.toPx() * pressDip * (1f - expand)
                val lensHeight = (baseLensHeight + stretchHeight + expandHeight + dipHeight)
                    .coerceAtMost(size.height + 6.dp.toPx())

                val baseLensWidth = railWidthPx - 10.dp.toPx()
                val stretchSquash = 2.5.dp.toPx() * travelEnergy * (1f - 0.6f * expand)
                val glideLift = 5.dp.toPx() * travelEnergy.coerceAtMost(0.8f) * (1f - expand)
                val expandWidth = 18.dp.toPx() * expand
                val dipWidth = -1.dp.toPx() * pressDip * (1f - expand)
                val lensWidth = (baseLensWidth - stretchSquash + glideLift + expandWidth + dipWidth)
                    .coerceIn(48.dp.toPx(), size.width - 2.dp.toPx())

                val rawCenterY = interpolatedCenterInRailPx(lensPos)
                val edgeMargin = 5.dp.toPx() * (1f - expand)
                val minCenterY = lensHeight / 2f + edgeMargin
                val maxCenterY = (size.height - lensHeight / 2f - edgeMargin).coerceAtLeast(minCenterY)
                val clampedCenterY = rawCenterY.coerceIn(minCenterY, maxCenterY)

                val lensLeft = (size.width - lensWidth) / 2f
                val lensTop = clampedCenterY - lensHeight / 2f

                drawLiquidGlassCapsule(
                    palette = palette,
                    isDark = isDark,
                    leftPx = lensLeft,
                    topPx = lensTop,
                    widthPx = lensWidth,
                    heightPx = lensHeight,
                    expandProgress = expand,
                    travelEnergy = travelEnergy,
                    velocityStretchSigned = if (velMag > 0.02f) {
                        velStretchSigned
                    } else if (distEnergy > 0.02f) {
                        sign(restingTargetIndex.toFloat() - lensPos) * distEnergy
                    } else {
                        0f
                    },
                    isVertical = true,
                    customCornerRadiusPx = 24.dp.toPx() + 4.dp.toPx() * expand,
                    enableSpecularSheen = renderQuality.enableSpecularSheen,
                    enableAmbientShadow = renderQuality.enableAmbientShadows
                )
            }

            // 3. Unified Vertical Touch Controller + Five Measured Rail Destinations
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(railTrackWidth)
                    .fillMaxHeight()
                    .padding(vertical = verticalInset)
                    .pointerInput(totalHeightPx, itemCount) {
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressDelayMs = viewConfiguration.longPressTimeoutMillis.coerceIn(220L, 360L)
                        val railCenterX = railWidthPx * 0.5f

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startY = down.position.y
                            val downSlot = columnYToSlotIndex(startY)

                            pressedIndex = downSlot
                            isPointerCancelledOutside = false
                            var triggeredLongPress = false
                            var draggedAcrossRail = false
                            var externallyConsumed = false
                            var lastPointerY = startY
                            var lastPointerTimeMs = down.uptimeMillis
                            var lastSampledSlot = downSlot

                            val longPressJob = scope.launch {
                                delay(longPressDelayMs)
                                if (!draggedAcrossRail) {
                                    triggeredLongPress = true
                                    isInteractiveExpanded = true
                                    focusedPreviewIndex = downSlot
                                    lastDetentIndex = downSlot
                                    if (currentHapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        if (change != null && change.isConsumed && !triggeredLongPress && !draggedAcrossRail) {
                                            externallyConsumed = true
                                        }
                                        break
                                    }

                                    val currentX = change.position.x
                                    val currentY = change.position.y
                                    val currentTimeMs = change.uptimeMillis
                                    val movedX = abs(currentX - down.position.x)
                                    val movedY = abs(currentY - startY)
                                    val outsideHorizontally = abs(currentX - railCenterX) > horizontalCancelThresholdPx

                                    if (!draggedAcrossRail && movedY > touchSlop && (triggeredLongPress || movedY >= movedX)) {
                                        longPressJob.cancel()
                                        draggedAcrossRail = true
                                        isDragging = true
                                        isInteractiveExpanded = true
                                        val frac = columnYToFractionalIndex(currentY)
                                        val hovered = frac.roundToInt().coerceIn(0, itemCount - 1)
                                        lastDetentIndex = hovered
                                        lastSampledSlot = hovered
                                        focusedPreviewIndex = hovered
                                        if (!triggeredLongPress && currentHapticsEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        scope.launch {
                                            lensPositionAnim.snapTo(frac)
                                        }
                                    }

                                    if (draggedAcrossRail) {
                                        change.consume()
                                        isPointerCancelledOutside = outsideHorizontally

                                        val dtMs = (currentTimeMs - lastPointerTimeMs).coerceAtLeast(1L)
                                        val dy = currentY - lastPointerY
                                        val velocitySlotsPerSec = (dy / fallbackSlotHeightPx.coerceAtLeast(1f)) / (dtMs / 1000f)
                                        val targetStretch = (velocitySlotsPerSec * 0.085f).coerceIn(-1.05f, 1.05f)

                                        val frac = columnYToFractionalIndex(currentY)
                                        val hovered = frac.roundToInt().coerceIn(0, itemCount - 1)
                                        lastSampledSlot = hovered

                                        if (!outsideHorizontally) {
                                            scope.launch {
                                                lensPositionAnim.snapTo(frac)
                                                if (!currentReducedMotion) {
                                                    val blended = lensVelocityStretchAnim.value * 0.55f + targetStretch * 0.45f
                                                    lensVelocityStretchAnim.snapTo(blended)
                                                }
                                            }
                                            if (hovered != lastDetentIndex) {
                                                lastDetentIndex = hovered
                                                focusedPreviewIndex = hovered
                                                if (currentHapticsEnabled) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        } else {
                                            focusedPreviewIndex = currentSelectedIndex
                                            scope.launch {
                                                lensVelocityStretchAnim.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.72f, stiffness = 320f)
                                                )
                                            }
                                        }
                                    }

                                    lastPointerY = currentY
                                    lastPointerTimeMs = currentTimeMs
                                }
                            } finally {
                                longPressJob.cancel()
                                val wasCancelledOutside = isPointerCancelledOutside
                                val finalDragSlot = lastSampledSlot.coerceIn(0, itemCount - 1)
                                val shouldNavigateFromDrag = draggedAcrossRail && !wasCancelledOutside
                                val shouldNavigateFromTap =
                                    !externallyConsumed && !wasCancelledOutside && !triggeredLongPress && !draggedAcrossRail

                                pressedIndex = null
                                focusedPreviewIndex = null
                                isInteractiveExpanded = false
                                isDragging = false
                                isPointerCancelledOutside = false

                                scope.launch {
                                    lensVelocityStretchAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.60f, stiffness = 260f)
                                    )
                                }

                                when {
                                    shouldNavigateFromDrag -> {
                                        if (finalDragSlot != currentSelectedIndex) {
                                            if (currentHapticsEnabled) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            currentOnSelect(destinations[finalDragSlot])
                                        }
                                    }
                                    shouldNavigateFromTap -> {
                                        if (currentHapticsEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        currentOnSelect(destinations[downSlot])
                                    }
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                destinations.forEachIndexed { index, destination ->
                    val isSelected = selectedIndex == index
                    val isExpanded = isInteractiveExpanded && !isPointerCancelledOutside
                    val isThisExpandedTarget = isExpanded && (focusedPreviewIndex == index)
                    val isHighlighted = if (isExpanded) isThisExpandedTarget else isSelected
                    val showActiveFocusDot = destination == BottomNavDestination.FOCUS && isFocusTimerRunning

                    LiquidGlassNavItem(
                        destination = destination,
                        slotIndex = index,
                        palette = palette,
                        isSelected = isSelected,
                        isHighlighted = isHighlighted,
                        isExpandedTarget = isThisExpandedTarget,
                        isLightlyPressed = pressedIndex == index && !isExpanded,
                        lensPositionProvider = { lensPositionAnim.value },
                        lensExpandProvider = { lensExpandAnim.value },
                        showActiveFocusDot = showActiveFocusDot,
                        reducedMotion = reducedMotion,
                        onAccessibilityClick = {
                            if (currentHapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnSelect(destination)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                val height = coords.size.height.toFloat()
                                val centerY = coords.positionInParent().y + height * 0.5f
                                val existing = measuredSlots[index]
                                if (!existing.isMeasured ||
                                    abs(existing.centerY - centerY) > 0.5f ||
                                    abs(existing.height - height) > 0.5f
                                ) {
                                    measuredSlots[index] = MeasuredVerticalSlot(
                                        centerY = centerY,
                                        height = height,
                                        isMeasured = true
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}
