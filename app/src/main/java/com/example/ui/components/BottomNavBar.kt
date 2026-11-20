package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.performance.BlurSurfaceRole
import com.example.ui.theme.BrainBufferThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

object AppRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val FOCUS = "focus"
    const val PLANNER = "planner"
    const val GARDEN = "garden"
    const val PROFILE = "profile"
    const val INSIGHTS = "insights"
    const val MIND_RESET = "mind_reset"
    const val SETTINGS = "settings"
    const val STRICT_FOCUS = "strict_focus"
}

enum class BottomNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(AppRoutes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    FOCUS(AppRoutes.FOCUS, "Focus", Icons.Rounded.Timer, Icons.Outlined.Timer, "nav_focus"),
    PLANNER(AppRoutes.PLANNER, "Planner", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_planner"),
    GARDEN(AppRoutes.GARDEN, "Garden", Icons.Rounded.LocalFlorist, Icons.Outlined.LocalFlorist, "nav_garden"),
    PROFILE(AppRoutes.PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
}

private data class MeasuredSlot(
    val centerX: Float = 0f,
    val width: Float = 0f,
    val isMeasured: Boolean = false
)

/**
 * Single iOS 26-inspired Liquid Glass floating bottom navigation bar for BRAIN BUFFER.
 *
 * Uses one source of truth (`selectedIndex`), real measured item bounds (`onGloballyPositioned`),
 * and shared `Animatable` physics driving one continuous glass capsule:
 * - Tap: glides the glass lens smoothly across the bar via spring physics with elastic stretch
 *   and traveling specular light sheen.
 * - Long-press: expands ONLY the glass lens around the pressed item with curved meniscus depth,
 *   magnified icon, and soft highlight; releasing in place contracts smoothly with zero accidental navigation.
 * - Finger-following drag: tracks the user's finger continuously across all five destinations with
 *   velocity-coupled elastic stretching, optical icon magnification, detent haptics, safe outside-bar
 *   cancellation, and clean settling onto the chosen destination on release.
 */
@Composable
fun BrainBufferBottomBar(
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

    // Unified interaction state — one active gesture owns the single glass capsule
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

    // Shared physical Animatables for the single glass lens (read inside Draw/GraphicsLayer)
    val lensPositionAnim = remember { Animatable(selectedIndex.toFloat()) }
    val lensExpandAnim = remember { Animatable(0f) }
    val lensVelocityStretchAnim = remember { Animatable(0f) }
    val pressDipAnim = remember { Animatable(0f) }

    // Measured item slot geometry relative to the padded Row
    val measuredSlots = remember {
        mutableStateListOf<MeasuredSlot>().apply {
            repeat(itemCount) { add(MeasuredSlot()) }
        }
    }

    // Spring lens smoothly whenever resting destination or stationary long-press target changes
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
                        val impulse = (sign(distance) * (abs(distance) * 0.42f).coerceIn(0.22f, 0.95f))
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

    // Smooth spring expansion/contraction of the glass lens during long-press & drag
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

    // Subtle micro-compression while finger touches down before tap/long-press resolves
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

    // Reserve fixed vertical headroom around the 62.dp floating pill so the crystal magnifying lens
    // rises smoothly above and below the pill with zero clipping and zero layout shift.
    val pillHeight = 62.dp
    val verticalHeadroom = 13.dp
    val totalBarHeight = pillHeight + verticalHeadroom * 2
    val horizontalInset = 5.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .height(totalBarHeight)
                .testTag("brain_buffer_bottom_bar")
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val pillHeightPx = with(density) { pillHeight.toPx() }
            val headroomPx = with(density) { verticalHeadroom.toPx() }
            val horizontalInsetPx = with(density) { horizontalInset.toPx() }
            val verticalCancelThresholdPx = with(density) { 108.dp.toPx() }

            val innerWidthPx = (totalWidthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
            val fallbackSlotWidthPx = innerWidthPx / itemCount.toFloat()

            // Read actual measured center/width in bar coordinates, with exact fallback on frame 0
            fun slotCenterInBarPx(index: Int): Float {
                val safe = index.coerceIn(0, itemCount - 1)
                val slot = measuredSlots[safe]
                return if (slot.isMeasured && slot.width > 0f) {
                    horizontalInsetPx + slot.centerX
                } else {
                    horizontalInsetPx + (safe + 0.5f) * fallbackSlotWidthPx
                }
            }

            fun slotWidthAtPx(index: Int): Float {
                val safe = index.coerceIn(0, itemCount - 1)
                val slot = measuredSlots[safe]
                return if (slot.isMeasured && slot.width > 0f) slot.width else fallbackSlotWidthPx
            }

            fun interpolatedCenterInBarPx(fractionalIndex: Float): Float {
                val clamped = fractionalIndex.coerceIn(0f, (itemCount - 1).toFloat())
                val lo = floor(clamped).toInt().coerceIn(0, itemCount - 1)
                val hi = ceil(clamped).toInt().coerceIn(0, itemCount - 1)
                val t = clamped - lo
                return slotCenterInBarPx(lo) * (1f - t) + slotCenterInBarPx(hi) * t
            }

            fun interpolatedSlotWidthPx(fractionalIndex: Float): Float {
                val clamped = fractionalIndex.coerceIn(0f, (itemCount - 1).toFloat())
                val lo = floor(clamped).toInt().coerceIn(0, itemCount - 1)
                val hi = ceil(clamped).toInt().coerceIn(0, itemCount - 1)
                val t = clamped - lo
                return slotWidthAtPx(lo) * (1f - t) + slotWidthAtPx(hi) * t
            }

            fun rowXToFractionalIndex(rowLocalX: Float): Float {
                val barX = rowLocalX + horizontalInsetPx
                val firstCenter = slotCenterInBarPx(0)
                val lastCenter = slotCenterInBarPx(itemCount - 1)
                if (barX <= firstCenter) return 0f
                if (barX >= lastCenter) return (itemCount - 1).toFloat()

                for (i in 0 until itemCount - 1) {
                    val leftC = slotCenterInBarPx(i)
                    val rightC = slotCenterInBarPx(i + 1)
                    if (barX in leftC..rightC) {
                        val span = (rightC - leftC).coerceAtLeast(1f)
                        return i + ((barX - leftC) / span)
                    }
                }
                return ((rowLocalX / fallbackSlotWidthPx) - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
            }

            fun rowXToSlotIndex(rowLocalX: Float): Int {
                return rowXToFractionalIndex(rowLocalX).roundToInt().coerceIn(0, itemCount - 1)
            }

            // 1. ONE Continuous Translucent Smoked/Frosted Glass Track
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(pillHeight)
                    .shadow(
                        elevation = if (!renderQuality.enableAmbientShadows) 2.dp else if (isDark) 12.dp else 14.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = palette.trackShadow,
                        spotColor = palette.trackShadow,
                        clip = false
                    )
                    .clip(RoundedCornerShape(50))
            ) {
                LiquidGlassTrackFill(
                    palette = palette,
                    blurRole = BlurSurfaceRole.NAVIGATION_TRACK
                )
            }

            // 2. ONE Track Border + ONE Moving & Expanding Crystal Liquid Glass Capsule
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLiquidGlassTrackBorder(
                    palette = palette,
                    pillTopOffsetPx = headroomPx,
                    pillHeightPx = pillHeightPx
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

                val measuredSlotW = interpolatedSlotWidthPx(lensPos)
                val baseLensWidth = (measuredSlotW - 2.dp.toPx()).coerceAtLeast(48.dp.toPx())
                val stretchWidth = measuredSlotW * 0.20f * travelEnergy
                val expandWidth = 18.dp.toPx() * expand
                val dipWidth = -1.5.dp.toPx() * pressDip * (1f - expand)
                val lensWidth = (baseLensWidth + stretchWidth + expandWidth + dipWidth)
                    .coerceAtMost(size.width + 6.dp.toPx())

                val baseLensHeight = pillHeightPx - 10.dp.toPx()
                val stretchSquash = 2.5.dp.toPx() * travelEnergy * (1f - 0.6f * expand)
                // Slight organic lift even during tap glide, full crystal dome on drag/long-press
                val glideLift = 6.dp.toPx() * travelEnergy.coerceAtMost(0.8f) * (1f - expand)
                val expandHeight = 20.dp.toPx() * expand
                val dipHeight = -1.dp.toPx() * pressDip * (1f - expand)
                val lensHeight = (baseLensHeight - stretchSquash + glideLift + expandHeight + dipHeight)
                    .coerceIn(36.dp.toPx(), size.height - 2.dp.toPx())

                val rawCenterX = interpolatedCenterInBarPx(lensPos)
                val edgeMargin = 5.dp.toPx() * (1f - expand)
                val minCenterX = lensWidth / 2f + edgeMargin
                val maxCenterX = (size.width - lensWidth / 2f - edgeMargin).coerceAtLeast(minCenterX)
                val clampedCenterX = rawCenterX.coerceIn(minCenterX, maxCenterX)

                val lensLeft = clampedCenterX - lensWidth / 2f
                val lensTop = (size.height - lensHeight) / 2f

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
                    enableSpecularSheen = renderQuality.enableSpecularSheen,
                    enableAmbientShadow = renderQuality.enableAmbientShadows
                )
            }

            // 3. Unified Bar-Level Gesture Surface + Five Measured Destination Items
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(pillHeight)
                    .padding(horizontal = horizontalInset)
                    .pointerInput(totalWidthPx, itemCount) {
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressDelayMs = viewConfiguration.longPressTimeoutMillis.coerceIn(220L, 360L)
                        val barCenterY = pillHeightPx * 0.5f

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val downSlot = rowXToSlotIndex(startX)

                            pressedIndex = downSlot
                            isPointerCancelledOutside = false
                            var triggeredLongPress = false
                            var draggedAcrossBar = false
                            var externallyConsumed = false
                            var lastPointerX = startX
                            var lastPointerTimeMs = down.uptimeMillis
                            var lastSampledSlot = downSlot

                            val longPressJob = scope.launch {
                                delay(longPressDelayMs)
                                if (!draggedAcrossBar) {
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
                                        if (change != null && change.isConsumed && !triggeredLongPress && !draggedAcrossBar) {
                                            externallyConsumed = true
                                        }
                                        break
                                    }

                                    val currentX = change.position.x
                                    val currentY = change.position.y
                                    val currentTimeMs = change.uptimeMillis
                                    val movedX = abs(currentX - startX)
                                    val movedY = abs(currentY - down.position.y)
                                    val outsideVertically = abs(currentY - barCenterY) > verticalCancelThresholdPx

                                    if (!draggedAcrossBar && movedX > touchSlop && (triggeredLongPress || movedX >= movedY)) {
                                        longPressJob.cancel()
                                        draggedAcrossBar = true
                                        isDragging = true
                                        isInteractiveExpanded = true
                                        val frac = rowXToFractionalIndex(currentX)
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

                                    if (draggedAcrossBar) {
                                        change.consume()
                                        isPointerCancelledOutside = outsideVertically

                                        val dtMs = (currentTimeMs - lastPointerTimeMs).coerceAtLeast(1L)
                                        val dx = currentX - lastPointerX
                                        val velocitySlotsPerSec = (dx / fallbackSlotWidthPx.coerceAtLeast(1f)) / (dtMs / 1000f)
                                        val targetStretch = (velocitySlotsPerSec * 0.085f).coerceIn(-1.05f, 1.05f)

                                        val frac = rowXToFractionalIndex(currentX)
                                        val hovered = frac.roundToInt().coerceIn(0, itemCount - 1)
                                        lastSampledSlot = hovered

                                        if (!outsideVertically) {
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

                                    lastPointerX = currentX
                                    lastPointerTimeMs = currentTimeMs
                                }
                            } finally {
                                longPressJob.cancel()
                                val wasCancelledOutside = isPointerCancelledOutside
                                val finalDragSlot = lastSampledSlot.coerceIn(0, itemCount - 1)
                                val shouldNavigateFromDrag = draggedAcrossBar && !wasCancelledOutside
                                val shouldNavigateFromTap =
                                    !externallyConsumed && !wasCancelledOutside && !triggeredLongPress && !draggedAcrossBar

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
                                    // Stationary long-press or outside-bar release: returns smoothly with zero accidental navigation
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    val isSelected = selectedIndex == index
                    val isExpanded = isInteractiveExpanded && !isPointerCancelledOutside
                    val isThisExpandedTarget = isExpanded && (focusedPreviewIndex == index)
                    val isHighlighted = if (isExpanded) {
                        isThisExpandedTarget
                    } else {
                        isSelected
                    }
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
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                val width = coords.size.width.toFloat()
                                val centerX = coords.positionInParent().x + width * 0.5f
                                val existing = measuredSlots[index]
                                if (!existing.isMeasured ||
                                    abs(existing.centerX - centerX) > 0.5f ||
                                    abs(existing.width - width) > 0.5f
                                ) {
                                    measuredSlots[index] = MeasuredSlot(
                                        centerX = centerX,
                                        width = width,
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

@Composable
internal fun LiquidGlassNavItem(
    destination: BottomNavDestination,
    slotIndex: Int,
    palette: LiquidGlassPalette,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isExpandedTarget: Boolean,
    isLightlyPressed: Boolean,
    lensPositionProvider: () -> Float,
    lensExpandProvider: () -> Float,
    showActiveFocusDot: Boolean,
    reducedMotion: Boolean,
    onAccessibilityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorAnimDuration = if (reducedMotion) 0 else 150
    val iconTint by animateColorAsState(
        targetValue = if (isHighlighted) palette.selectedIcon else palette.unselectedIcon,
        animationSpec = tween(durationMillis = colorAnimDuration),
        label = "navIconTint"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isHighlighted) palette.selectedIcon else palette.unselectedLabel,
        animationSpec = tween(durationMillis = colorAnimDuration),
        label = "navLabelColor"
    )

    Column(
        modifier = modifier
            .testTag(destination.testTag)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = destination.label
                onClick(label = destination.label) {
                    onAccessibilityClick()
                    true
                }
            }
            .graphicsLayer {
                val proximity = (1f - abs(lensPositionProvider() - slotIndex.toFloat())).coerceIn(0f, 1f)
                val expand = lensExpandProvider()
                // Gently dim non-focused items while the optical lens is expanded
                alpha = 1f - (0.38f * expand * (1f - proximity))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val proximity = (1f - abs(lensPositionProvider() - slotIndex.toFloat())).coerceIn(0f, 1f)
                val expand = lensExpandProvider()
                val baseScale = 1.0f + 0.10f * proximity
                val opticalMagnify = 0.16f * expand * proximity
                val pressScale = if (isLightlyPressed && expand < 0.05f) -0.06f else 0f
                val finalScale = (baseScale + opticalMagnify + pressScale).coerceIn(0.90f, 1.28f)
                scaleX = finalScale
                scaleY = finalScale
                translationY = (-2.4f * expand * proximity).dp.toPx()
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isHighlighted || isExpandedTarget) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            if (showActiveFocusDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                fontWeight = if (isHighlighted || isExpandedTarget) FontWeight.Bold else FontWeight.Medium
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.graphicsLayer {
                val proximity = (1f - abs(lensPositionProvider() - slotIndex.toFloat())).coerceIn(0f, 1f)
                val expand = lensExpandProvider()
                val labelScale = 0.96f + 0.06f * proximity + 0.05f * expand * proximity
                scaleX = labelScale
                scaleY = labelScale
                alpha = (0.76f + 0.24f * proximity) * (1f - 0.30f * expand * (1f - proximity))
            }
        )
    }
}
