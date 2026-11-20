package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.performance.AdaptiveBlurState
import com.example.performance.BlurSurfaceRole
import com.example.performance.PerformanceProfile
import com.example.performance.RenderQualityTokens
import com.example.performance.adaptiveLiquidBlur
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LavenderLuminous
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.VioletCore
import com.example.ui.theme.VioletDeep

/**
 * Surface hierarchy levels for the unified BRAIN BUFFER Liquid Glass Design System.
 */
enum class LiquidGlassTone {
    /** Standard translucent glass card for primary content containers. */
    STANDARD,
    /** Quieter secondary glass layer for grouped items and nested tiles. */
    SUBTLE,
    /** Elevated floating glass panel for hero sections, sheets, and dialogs. */
    ELEVATED,
    /** Accent-tinted lavender/violet glass for active callouts and primary focus cards. */
    ACCENT
}

/**
 * Centralized Liquid Glass design tokens resolved dynamically from:
 * - Active theme (Pure AMOLED Dark `#000000` vs Frosted Light `#FAF9FD`)
 * - Adaptive performance profile (`PERFORMANCE`, `BALANCED`, `PREMIUM`)
 * - Accessibility reduced-motion setting
 *
 * All cards, buttons, chips, segmented controls, sheets, dialogs, and progress tracks
 * consume these tokens so the entire application shares one coherent glass material language
 * with zero duplicated ad-hoc implementations and zero expensive per-card blur stacking.
 */
@Immutable
data class LiquidGlassTokens(
    val isDark: Boolean,
    val profile: PerformanceProfile,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val subtleTop: Color,
    val subtleBottom: Color,
    val elevatedTop: Color,
    val elevatedBottom: Color,
    val accentTop: Color,
    val accentBottom: Color,
    val sheetBackground: Color,
    val borderTop: Color,
    val borderMid: Color,
    val borderBottom: Color,
    val innerReflectionTop: Color,
    val lavenderReflection: Color,
    val shadowColor: Color,
    val shadowElevation: Dp,
    val blurRadiusDp: Float,
    val enableBlur: Boolean,
    val enableSpecularSheen: Boolean,
    val enableAmbientShadows: Boolean,
    val cornerSmall: Dp,
    val cornerMedium: Dp,
    val cornerLarge: Dp,
    val cornerExtraLarge: Dp,
    val springDamping: Float,
    val springStiffness: Float,
    val animDurationMs: Int
) {
    companion object {
        fun resolve(
            isDark: Boolean,
            renderQuality: RenderQualityTokens,
            reducedMotion: Boolean,
            blurState: AdaptiveBlurState? = null
        ): LiquidGlassTokens {
            val isPerf = renderQuality.profile == PerformanceProfile.PERFORMANCE
            val resolvedBlurDp = blurState?.navBlurRadiusDp ?: renderQuality.glassBlurRadiusDp
            val resolvedEnableBlur = blurState?.isBlurActiveFor(BlurSurfaceRole.NAVIGATION_TRACK)
                ?: renderQuality.enableGlassBlur
            val reflectionScale = when (renderQuality.profile) {
                PerformanceProfile.PERFORMANCE -> 0.72f
                PerformanceProfile.BALANCED -> 0.90f
                PerformanceProfile.PREMIUM -> 1.0f
            }
            val springDamp = if (reducedMotion) 1.0f else 0.74f
            val springStiff = if (reducedMotion) 1200f else 340f * renderQuality.springStiffnessScale

            return if (isDark) {
                // Pure neutral charcoal glass over #000000 AMOLED base — zero navy/blue cast.
                LiquidGlassTokens(
                    isDark = true,
                    profile = renderQuality.profile,
                    surfaceTop = Color(0xE617171B),
                    surfaceBottom = Color(0xEC0E0E11),
                    subtleTop = Color(0xD8111114),
                    subtleBottom = Color(0xE40A0A0D),
                    elevatedTop = Color(0xEE1F1E25),
                    elevatedBottom = Color(0xF2121216),
                    accentTop = Color(0xE6221C34),
                    accentBottom = Color(0xEE141120),
                    sheetBackground = Color(0xF7121216),
                    borderTop = Color.White.copy(alpha = 0.22f * reflectionScale),
                    borderMid = Color.White.copy(alpha = 0.08f * reflectionScale),
                    borderBottom = Color.White.copy(alpha = 0.13f * reflectionScale),
                    innerReflectionTop = Color.White.copy(alpha = 0.085f * reflectionScale),
                    lavenderReflection = LavenderLuminous.copy(alpha = 0.14f * reflectionScale),
                    shadowColor = Color(0xAA000000),
                    shadowElevation = if (!renderQuality.enableAmbientShadows || isPerf) 0.dp else 6.dp,
                    blurRadiusDp = resolvedBlurDp,
                    enableBlur = resolvedEnableBlur,
                    enableSpecularSheen = renderQuality.enableSpecularSheen,
                    enableAmbientShadows = renderQuality.enableAmbientShadows,
                    cornerSmall = 12.dp,
                    cornerMedium = 16.dp,
                    cornerLarge = 22.dp,
                    cornerExtraLarge = 28.dp,
                    springDamping = springDamp,
                    springStiffness = springStiff,
                    animDurationMs = renderQuality.transitionDurationMs
                )
            } else {
                // Frosted crystalline white glass over soft #FAF9FD base with delicate lavender sheen.
                LiquidGlassTokens(
                    isDark = false,
                    profile = renderQuality.profile,
                    surfaceTop = Color(0xF5FFFFFF),
                    surfaceBottom = Color(0xEBF6F2FD),
                    subtleTop = Color(0xE8F9F6FE),
                    subtleBottom = Color(0xE2F0EAF9),
                    elevatedTop = Color(0xFAFFFFFF),
                    elevatedBottom = Color(0xF0F4EEFC),
                    accentTop = Color(0xF2EFE9FF),
                    accentBottom = Color(0xEAE2D6FF),
                    sheetBackground = Color(0xFAFAF8FE),
                    borderTop = Color.White.copy(alpha = 0.96f),
                    borderMid = Color(0xFFC9BAFA).copy(alpha = 0.36f * reflectionScale),
                    borderBottom = Color(0xFFD6CAF6).copy(alpha = 0.56f * reflectionScale),
                    innerReflectionTop = Color.White.copy(alpha = 0.72f * reflectionScale),
                    lavenderReflection = SoftPurple.copy(alpha = 0.10f * reflectionScale),
                    shadowColor = Color(0x1E23115E),
                    shadowElevation = if (!renderQuality.enableAmbientShadows || isPerf) 1.dp else 5.dp,
                    blurRadiusDp = resolvedBlurDp,
                    enableBlur = resolvedEnableBlur,
                    enableSpecularSheen = renderQuality.enableSpecularSheen,
                    enableAmbientShadows = renderQuality.enableAmbientShadows,
                    cornerSmall = 12.dp,
                    cornerMedium = 16.dp,
                    cornerLarge = 22.dp,
                    cornerExtraLarge = 28.dp,
                    springDamping = springDamp,
                    springStiffness = springStiff,
                    animDurationMs = renderQuality.transitionDurationMs
                )
            }
        }
    }
}

val LocalLiquidGlassTokens = staticCompositionLocalOf {
    LiquidGlassTokens.resolve(
        isDark = false,
        renderQuality = RenderQualityTokens(),
        reducedMotion = false
    )
}

/**
 * Lightweight, tactile Liquid Glass press interaction modifier:
 * applies spring scale compression, subtle highlight feedback, and optional haptics.
 */
fun Modifier.liquidGlassPress(
    enabled: Boolean = true,
    scaleDown: Float = 0.975f,
    hapticsEnabled: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val glass = BrainBufferThemeTokens.glass
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = glass.springStiffness.coerceAtLeast(380f)
        ),
        label = "liquidGlassPressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(
                color = if (glass.isDark) Color.White.copy(alpha = 0.18f) else VioletCore.copy(alpha = 0.14f)
            ),
            enabled = enabled,
            onClick = {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}

/**
 * Core single-pass GPU Liquid Glass card/panel surface.
 *
 * Uses one custom `drawBehind` + top-lit specular border pass (NO per-card `Modifier.blur`)
 * so LazyColumn scrolling stays 60/120fps across Android 10–16 devices while delivering
 * unmistakable translucent Liquid Glass depth, top-edge illumination, and gentle lavender tint.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    tone: LiquidGlassTone = LiquidGlassTone.STANDARD,
    cornerRadius: Dp = 20.dp,
    tintOverlay: Color? = null,
    borderAccent: Color? = null,
    elevation: Dp? = null,
    blurRole: BlurSurfaceRole = BlurSurfaceRole.SCROLLING_CARD,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = BrainBufferThemeTokens.glass
    val blurState = BrainBufferThemeTokens.blurState
    val shape = RoundedCornerShape(cornerRadius)
    val resolvedElevation = elevation ?: when (tone) {
        LiquidGlassTone.ELEVATED -> glass.shadowElevation
        LiquidGlassTone.ACCENT -> glass.shadowElevation
        LiquidGlassTone.STANDARD -> if (glass.isDark) 0.dp else (glass.shadowElevation * 0.55f)
        LiquidGlassTone.SUBTLE -> 0.dp
    }

    val alphaBoost = blurState.alphaCompensationFor(blurRole)
    val (rawTopFill, rawBottomFill) = when (tone) {
        LiquidGlassTone.STANDARD -> glass.surfaceTop to glass.surfaceBottom
        LiquidGlassTone.SUBTLE -> glass.subtleTop to glass.subtleBottom
        LiquidGlassTone.ELEVATED -> glass.elevatedTop to glass.elevatedBottom
        LiquidGlassTone.ACCENT -> glass.accentTop to glass.accentBottom
    }
    val topFill = if (alphaBoost > 0f) rawTopFill.copy(alpha = (rawTopFill.alpha + alphaBoost).coerceAtMost(0.98f)) else rawTopFill
    val bottomFill = if (alphaBoost > 0f) rawBottomFill.copy(alpha = (rawBottomFill.alpha + alphaBoost).coerceAtMost(0.98f)) else rawBottomFill
    val useSelectiveBackdropBlur = blurRole != BlurSurfaceRole.SCROLLING_CARD && blurState.isBlurActiveFor(blurRole)

    val borderTopColor = borderAccent?.copy(alpha = if (glass.isDark) 0.62f else 0.52f) ?: glass.borderTop
    val borderMidColor = borderAccent?.copy(alpha = if (glass.isDark) 0.24f else 0.24f) ?: glass.borderMid
    val borderBottomColor = borderAccent?.copy(alpha = if (glass.isDark) 0.34f else 0.32f) ?: glass.borderBottom

    Box(
        modifier = modifier
            .then(
                if (resolvedElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = resolvedElevation,
                        shape = shape,
                        ambientColor = glass.shadowColor,
                        spotColor = glass.shadowColor,
                        clip = false
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind {
                val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
                val corner = CornerRadius(radiusPx, radiusPx)
                val fullSize = size

                // 1. Base translucent charcoal / frosted-white glass vertical gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topFill, bottomFill),
                        startY = 0f,
                        endY = fullSize.height
                    ),
                    cornerRadius = corner
                )

                // 2. Optional subtle tint wash (accent cards, status cards, subject highlight)
                if (tintOverlay != null && tintOverlay.alpha > 0.005f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                tintOverlay.copy(alpha = (tintOverlay.alpha * 1.15f).coerceAtMost(0.45f)),
                                tintOverlay.copy(alpha = (tintOverlay.alpha * 0.55f).coerceAtMost(0.30f))
                            ),
                            startY = 0f,
                            endY = fullSize.height
                        ),
                        cornerRadius = corner
                    )
                }

                // 3. Soft top-edge glass inner highlight sheen (single gradient, zero seams)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to glass.innerReflectionTop,
                            0.24f to glass.innerReflectionTop.copy(alpha = glass.innerReflectionTop.alpha * 0.22f),
                            1.0f to Color.Transparent
                        ),
                        startY = 0f,
                        endY = fullSize.height
                    ),
                    cornerRadius = corner
                )

                // 4. Delicate lavender light bounce at top-left on elevated & accent surfaces
                if (glass.enableSpecularSheen && (tone == LiquidGlassTone.ELEVATED || tone == LiquidGlassTone.ACCENT)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                glass.lavenderReflection,
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(fullSize.width * 0.65f, fullSize.height * 0.55f)
                        ),
                        cornerRadius = corner
                    )
                }
            }
            .drawWithContent {
                drawContent()
                // 5. Crisp single top-lit specular glass rim border
                val strokePx = if (borderAccent != null) 1.15.dp.toPx() else 0.9.dp.toPx()
                val inset = strokePx * 0.5f
                val radiusPx = (cornerRadius.toPx() - inset).coerceAtLeast(1f)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to borderTopColor,
                            0.38f to borderMidColor,
                            0.74f to borderMidColor,
                            1.0f to borderBottomColor
                        ),
                        start = Offset(size.width * 0.08f, 0f),
                        end = Offset(size.width * 0.92f, size.height)
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - strokePx).coerceAtLeast(1f),
                        (size.height - strokePx).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.liquidGlassPress(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        if (useSelectiveBackdropBlur) {
            // Selective frosted diffusion layer for floating overlays only (never scrolling cards).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .adaptiveLiquidBlur(role = blurRole)
                    .drawBehind {
                        val bleed = 14.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    topFill.copy(alpha = (topFill.alpha * 0.42f).coerceAtMost(0.45f)),
                                    bottomFill.copy(alpha = (bottomFill.alpha * 0.35f).coerceAtMost(0.38f))
                                )
                            ),
                            topLeft = Offset(-bleed, -bleed),
                            size = Size(size.width + bleed * 2f, size.height + bleed * 2f)
                        )
                    }
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Luminous primary Liquid Glass CTA button with glossy top-lit specular bevel,
 * smooth spring compression, and crisp contrast in both Light and AMOLED Dark themes.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentStart: Color = MaterialTheme.colorScheme.primary,
    accentEnd: Color = if (BrainBufferThemeTokens.glass.isDark) SoftPurple else VioletDeep,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    cornerRadius: Dp = 16.dp,
    height: Dp = 50.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val glass = BrainBufferThemeTokens.glass
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveAlpha = if (enabled) 1f else 0.42f

    Box(
        modifier = modifier
            .height(height)
            .then(
                if (enabled && glass.enableAmbientShadows) {
                    Modifier.shadow(
                        elevation = if (glass.isDark) 8.dp else 6.dp,
                        shape = shape,
                        ambientColor = accentStart.copy(alpha = 0.35f),
                        spotColor = accentStart.copy(alpha = 0.42f),
                        clip = false
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind {
                val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
                val corner = CornerRadius(radiusPx, radiusPx)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentStart.copy(alpha = effectiveAlpha),
                            accentEnd.copy(alpha = effectiveAlpha)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = corner
                )
                // Glossy top specular glass sheen
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.30f * effectiveAlpha),
                            0.42f to Color.White.copy(alpha = 0.06f * effectiveAlpha),
                            1.0f to Color.Transparent
                        )
                    ),
                    cornerRadius = corner
                )
                // Crisp outer specular glass rim
                val strokePx = 1.dp.toPx()
                val inset = strokePx * 0.5f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.48f * effectiveAlpha),
                            Color.White.copy(alpha = 0.14f * effectiveAlpha)
                        )
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - strokePx).coerceAtLeast(1f), (size.height - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((radiusPx - inset).coerceAtLeast(1f), (radiusPx - inset).coerceAtLeast(1f)),
                    style = Stroke(width = strokePx)
                )
            }
            .liquidGlassPress(enabled = enabled, scaleDown = 0.965f, onClick = onClick)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor.copy(alpha = effectiveAlpha)
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Translucent secondary Liquid Glass button for Focus Guard, Need Phone, Boost, and secondary actions.
 */
@Composable
fun LiquidGlassSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentBorder: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 15.dp,
    height: Dp = 46.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val glass = BrainBufferThemeTokens.glass
    val shape = RoundedCornerShape(cornerRadius)
    val alpha = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .drawBehind {
                val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
                val corner = CornerRadius(radiusPx, radiusPx)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            glass.elevatedTop.copy(alpha = glass.elevatedTop.alpha * alpha),
                            glass.surfaceBottom.copy(alpha = glass.surfaceBottom.alpha * alpha)
                        )
                    ),
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to glass.innerReflectionTop,
                            0.45f to Color.Transparent
                        )
                    ),
                    cornerRadius = corner
                )
                val strokePx = 1.dp.toPx()
                val inset = strokePx * 0.5f
                val rimTop = accentBorder?.copy(alpha = 0.52f) ?: glass.borderTop
                val rimBot = accentBorder?.copy(alpha = 0.25f) ?: glass.borderBottom
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(rimTop, rimBot)),
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - strokePx).coerceAtLeast(1f), (size.height - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((radiusPx - inset).coerceAtLeast(1f), (radiusPx - inset).coerceAtLeast(1f)),
                    style = Stroke(width = strokePx)
                )
            }
            .liquidGlassPress(enabled = enabled, scaleDown = 0.97f, onClick = onClick)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor.copy(alpha = alpha)
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Translucent crystal icon housing with delicate top specular highlight and subtle accent glow.
 */
@Composable
fun LiquidGlassIconContainer(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = RoundedCornerShape(13.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = BrainBufferThemeTokens.glass
    val isCircle = shape == CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .drawBehind {
                val r = if (isCircle) minOf(this.size.width, this.size.height) / 2f else 13.dp.toPx()
                val corner = CornerRadius(r, r)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (glass.isDark) 0.24f else 0.16f),
                            accentColor.copy(alpha = if (glass.isDark) 0.11f else 0.08f)
                        )
                    ),
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = if (glass.isDark) 0.14f else 0.55f),
                            0.5f to Color.Transparent
                        )
                    ),
                    cornerRadius = corner
                )
                val strokePx = 0.9.dp.toPx()
                val inset = strokePx * 0.5f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (glass.isDark) 0.45f else 0.34f),
                            accentColor.copy(alpha = if (glass.isDark) 0.16f else 0.14f)
                        )
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size((this.size.width - strokePx).coerceAtLeast(1f), (this.size.height - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((r - inset).coerceAtLeast(1f), (r - inset).coerceAtLeast(1f)),
                    style = Stroke(width = strokePx)
                )
            }
            .then(if (onClick != null) Modifier.liquidGlassPress(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Reusable Liquid Glass Segmented Control with a single spring-sliding refractive glass capsule.
 * Used across Focus mode switcher, Planner view tabs, Profile sections, and Theme picker.
 */
@Composable
fun <T> LiquidGlassSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector)? = null,
    testTagProvider: ((T) -> String)? = null,
    height: Dp = 46.dp
) {
    if (items.isEmpty()) return
    val glass = BrainBufferThemeTokens.glass
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val density = LocalDensity.current
    val selectedIndex = items.indexOf(selectedItem).coerceIn(0, items.lastIndex)

    val slideAnim = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, reducedMotion) {
        if (reducedMotion) {
            slideAnim.snapTo(selectedIndex.toFloat())
        } else {
            slideAnim.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 340f
                )
            )
        }
    }

    val trackCorner = 16.dp
    val innerPad = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(trackCorner))
            .drawBehind {
                val r = trackCorner.toPx()
                val corner = CornerRadius(r, r)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(glass.subtleTop, glass.subtleBottom)
                    ),
                    cornerRadius = corner
                )
                val strokePx = 0.9.dp.toPx()
                val inset = strokePx * 0.5f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(glass.borderTop, glass.borderMid, glass.borderBottom)
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - strokePx).coerceAtLeast(1f), (size.height - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((r - inset).coerceAtLeast(1f), (r - inset).coerceAtLeast(1f)),
                    style = Stroke(width = strokePx)
                )
            }
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val padPx = with(density) { innerPad.toPx() }
        val slotW = ((totalWidthPx - padPx * 2f) / items.size.toFloat()).coerceAtLeast(1f)
        val capsuleH = (totalHeightPx - padPx * 2f).coerceAtLeast(1f)
        val capsuleR = with(density) { 12.dp.toPx() }

        // Single moving refractive glass selection capsule
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = padPx + slideAnim.value * slotW
            val top = padPx
            val corner = CornerRadius(capsuleR, capsuleR)
            val rect = Rect(Offset(left, top), Size(slotW, capsuleH))
            val path = Path().apply { addRoundRect(RoundRect(rect, corner)) }

            clipPath(path) {
                if (glass.isDark) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x40B7A1F7),
                                Color(0x266B4CE6)
                            ),
                            startY = top,
                            endY = top + capsuleH
                        ),
                        topLeft = Offset(left, top),
                        size = Size(slotW, capsuleH)
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.22f),
                                0.42f to Color.White.copy(alpha = 0.04f),
                                1.0f to Color.Transparent
                            ),
                            startY = top,
                            endY = top + capsuleH
                        ),
                        topLeft = Offset(left, top),
                        size = Size(slotW, capsuleH)
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xF0EFE8FF)
                            ),
                            startY = top,
                            endY = top + capsuleH
                        ),
                        topLeft = Offset(left, top),
                        size = Size(slotW, capsuleH)
                    )
                }
            }

            // Crisp specular capsule edge
            val strokePx = 1.dp.toPx()
            val inset = strokePx * 0.5f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = if (glass.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            LavenderLuminous.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.20f)
                        )
                    } else {
                        listOf(
                            Color.White,
                            VioletCore.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.85f)
                        )
                    },
                    start = Offset(left, top),
                    end = Offset(left + slotW, top + capsuleH)
                ),
                topLeft = Offset(left + inset, top + inset),
                size = Size((slotW - strokePx).coerceAtLeast(1f), (capsuleH - strokePx).coerceAtLeast(1f)),
                cornerRadius = CornerRadius((capsuleR - inset).coerceAtLeast(1f), (capsuleR - inset).coerceAtLeast(1f)),
                style = Stroke(width = strokePx)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (glass.isDark) Color.White else VioletCore
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(if (reducedMotion) 0 else 150),
                    label = "segTextColor"
                )

                val itemTag = testTagProvider?.invoke(item)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .let { if (itemTag != null) it.testTag(itemTag) else it }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(item) }
                        )
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        iconProvider?.invoke(item)?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = labelProvider(item),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Selectable translucent Liquid Glass chip for subjects, durations, and filters.
 */
@Composable
fun LiquidGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    leadingDotColor: Color? = null,
    leadingIcon: ImageVector? = null
) {
    val glass = BrainBufferThemeTokens.glass
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val r = size.height / 2f
                val corner = CornerRadius(r, r)
                if (selected) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (glass.isDark) 0.28f else 0.18f),
                                accentColor.copy(alpha = if (glass.isDark) 0.16f else 0.10f)
                            )
                        ),
                        cornerRadius = corner
                    )
                } else {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(glass.surfaceTop, glass.surfaceBottom)
                        ),
                        cornerRadius = corner
                    )
                }
                val strokePx = if (selected) 1.25.dp.toPx() else 0.9.dp.toPx()
                val inset = strokePx * 0.5f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (selected) {
                            listOf(
                                accentColor.copy(alpha = 0.85f),
                                accentColor.copy(alpha = 0.42f)
                            )
                        } else {
                            listOf(glass.borderTop, glass.borderBottom)
                        }
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - strokePx).coerceAtLeast(1f), (size.height - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((r - inset).coerceAtLeast(1f), (r - inset).coerceAtLeast(1f)),
                    style = Stroke(width = strokePx)
                )
            }
            .liquidGlassPress(scaleDown = 0.96f, onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        } else if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(leadingDotColor)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Translucent Liquid Glass linear progress indicator with smooth animated fill and top specular gloss.
 */
@Composable
fun LiquidGlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 7.dp
) {
    val glass = BrainBufferThemeTokens.glass
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (reducedMotion) Spring.StiffnessHigh else Spring.StiffnessLow
        ),
        label = "glassProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
    ) {
        val r = size.height / 2f
        val corner = CornerRadius(r, r)

        // Glass track trough
        drawRoundRect(
            color = if (glass.isDark) Color.White.copy(alpha = 0.08f) else color.copy(alpha = 0.14f),
            cornerRadius = corner
        )

        // Luminous progress fill
        val fillW = (size.width * animatedProgress).coerceAtLeast(if (animatedProgress > 0f) size.height else 0f)
        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(color.copy(alpha = 0.85f), color)
                ),
                size = Size(fillW, size.height),
                cornerRadius = corner
            )
            // Subtle top glass reflection stripe
            drawRoundRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(0f, 0f),
                size = Size(fillW, size.height * 0.42f),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Elevated floating Liquid Glass container for custom dialogs (Celebration, Confirmations).
 */
@Composable
fun LiquidGlassDialogCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        tone = LiquidGlassTone.ELEVATED,
        cornerRadius = 26.dp,
        blurRole = BlurSurfaceRole.MODAL_SHEET_OR_DIALOG,
        contentPadding = contentPadding,
        content = content
    )
}
