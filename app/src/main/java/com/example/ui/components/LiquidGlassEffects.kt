package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.performance.BlurSurfaceRole
import com.example.performance.adaptiveLiquidBlur
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LavenderLuminous
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.VioletCore
import kotlin.math.abs
import kotlin.math.sign

@Immutable
internal data class LiquidGlassPalette(
    val trackTop: Color,
    val trackBottom: Color,
    val trackInnerSheen: Color,
    val trackBorderTop: Color,
    val trackBorderMid: Color,
    val trackBorderBottom: Color,
    val trackShadow: Color,
    val restCapsuleFillTop: Color,
    val restCapsuleFillBottom: Color,
    val lensClearFillTop: Color,
    val lensClearFillBottom: Color,
    val capsuleLavenderTint: Color,
    val capsuleTopReflection: Color,
    val capsuleBorderTop: Color,
    val capsuleBorderMid: Color,
    val capsuleBorderBottom: Color,
    val capsuleLiftShadow: Color,
    val selectedIcon: Color,
    val unselectedIcon: Color,
    val unselectedLabel: Color
)

/**
 * Calibrated ReSukiSU / iOS 26 Liquid Glass palette:
 * - Pure smoked charcoal crystal glass in AMOLED Dark mode (zero navy blue, crystal-clear expanded lens).
 * - Frosted crystalline white glass in Light mode with soft lavender specular accents.
 */
internal fun liquidGlassPalette(isDark: Boolean): LiquidGlassPalette {
    return if (isDark) {
        LiquidGlassPalette(
            trackTop = Color(0xCC18181D),
            trackBottom = Color(0xDC0B0B0E),
            trackInnerSheen = Color(0x16FFFFFF),
            trackBorderTop = Color(0x46FFFFFF),
            trackBorderMid = Color(0x18FFFFFF),
            trackBorderBottom = Color(0x28FFFFFF),
            trackShadow = Color(0xCC000000),
            // Resting capsule: sleek translucent smoked glass with delicate violet shimmer (never chalky)
            restCapsuleFillTop = Color(0x24FFFFFF),
            restCapsuleFillBottom = Color(0x12FFFFFF),
            // Expanded/moving lens: crystal-clear refractive droplet so the magnified icon shines sharply through
            lensClearFillTop = Color(0x12FFFFFF),
            lensClearFillBottom = Color(0x08FFFFFF),
            capsuleLavenderTint = Color(0x1FB7A1F7),
            capsuleTopReflection = Color(0x20FFFFFF),
            capsuleBorderTop = Color(0xA8FFFFFF),
            capsuleBorderMid = Color(0x26FFFFFF),
            capsuleBorderBottom = Color(0x64FFFFFF),
            capsuleLiftShadow = Color(0xA8000000),
            selectedIcon = LavenderLuminous,
            unselectedIcon = Color(0xFFEBEBF0),
            unselectedLabel = Color(0xFFA5A5B2)
        )
    } else {
        LiquidGlassPalette(
            trackTop = Color(0xDCFAF8FE),
            trackBottom = Color(0xD4EFE9FA),
            trackInnerSheen = Color(0x80FFFFFF),
            trackBorderTop = Color(0xFFFFFFFF),
            trackBorderMid = Color(0x64C7B7FA),
            trackBorderBottom = Color(0x8CD3C6F8),
            trackShadow = Color(0x2023115E),
            restCapsuleFillTop = Color(0xECFFFFFF),
            restCapsuleFillBottom = Color(0xCCE9E0FF),
            lensClearFillTop = Color(0x52FFFFFF),
            lensClearFillBottom = Color(0x26E2D5FF),
            capsuleLavenderTint = Color(0x166B4CE6),
            capsuleTopReflection = Color(0x78FFFFFF),
            capsuleBorderTop = Color(0xFFFFFFFF),
            capsuleBorderMid = Color(0x74B7A1F7),
            capsuleBorderBottom = Color(0xD8FFFFFF),
            capsuleLiftShadow = Color(0x2423115E),
            selectedIcon = VioletCore,
            unselectedIcon = Color(0xFF474159),
            unselectedLabel = Color(0xFF665E7B)
        )
    }
}

/**
 * Single lightweight frosted diffusion modifier for Android 12+ (API 31+) with clean fallback
 * on Android 10-11 (API 29-30). Always applied strictly to an oversized full-bleed fill inside
 * a clipped pill so edges never feather inward or produce ghosted double rims.
 */
internal fun Modifier.compatTrackBlur(radius: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
}

/**
 * Renders ONE clean translucent smoked/frosted glass track with soft top inner light catch
 * and zero inner nested shapes. Governed by [com.example.performance.AdaptiveBlurController]
 * so High/Medium/Low performance tiers and live 60fps throttling adjust the blur radius
 * smoothly while compensating fill opacity when blur is disabled.
 */
@Composable
internal fun LiquidGlassTrackFill(
    palette: LiquidGlassPalette,
    modifier: Modifier = Modifier,
    blurRole: BlurSurfaceRole = BlurSurfaceRole.NAVIGATION_TRACK,
    blurRadiusDp: Float? = null,
    enableBlur: Boolean = true
) {
    val blurState = BrainBufferThemeTokens.blurState
    val alphaBoost = blurState.alphaCompensationFor(blurRole)
    val trackTop = if (alphaBoost > 0f) {
        palette.trackTop.copy(alpha = (palette.trackTop.alpha + alphaBoost).coerceAtMost(0.96f))
    } else {
        palette.trackTop
    }
    val trackBottom = if (alphaBoost > 0f) {
        palette.trackBottom.copy(alpha = (palette.trackBottom.alpha + alphaBoost).coerceAtMost(0.97f))
    } else {
        palette.trackBottom
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .adaptiveLiquidBlur(
                role = blurRole,
                enabled = enableBlur,
                explicitRadiusDp = blurRadiusDp
            )
    ) {
        val bleed = 20.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(trackTop, trackBottom),
                startY = 0f,
                endY = size.height
            ),
            topLeft = Offset(-bleed, -bleed),
            size = Size(size.width + bleed * 2f, size.height + bleed * 2f)
        )
        // Subtle top-edge glass light catch
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to palette.trackInnerSheen,
                    0.35f to Color.Transparent
                ),
                startY = 0f,
                endY = size.height
            ),
            topLeft = Offset(-bleed, 0f),
            size = Size(size.width + bleed * 2f, size.height)
        )
    }
}

/**
 * Draws ONE fine top-lit specular glass border around the floating track pill.
 */
internal fun DrawScope.drawLiquidGlassTrackBorder(
    palette: LiquidGlassPalette,
    pillTopOffsetPx: Float,
    pillHeightPx: Float
) {
    val strokePx = 0.95.dp.toPx()
    val inset = strokePx * 0.5f
    val corner = CornerRadius(pillHeightPx / 2f, pillHeightPx / 2f)

    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to palette.trackBorderTop,
                0.32f to palette.trackBorderMid,
                0.68f to palette.trackBorderMid,
                1.0f to palette.trackBorderBottom
            ),
            start = Offset(0f, pillTopOffsetPx),
            end = Offset(size.width * 0.25f, pillTopOffsetPx + pillHeightPx)
        ),
        topLeft = Offset(inset, pillTopOffsetPx + inset),
        size = Size(
            (size.width - strokePx).coerceAtLeast(1f),
            (pillHeightPx - strokePx).coerceAtLeast(1f)
        ),
        cornerRadius = corner,
        style = Stroke(width = strokePx)
    )
}

/**
 * Draws ONE fine top-lit specular glass border around the vertical floating rail pill.
 */
internal fun DrawScope.drawLiquidGlassRailBorder(
    palette: LiquidGlassPalette,
    railLeftOffsetPx: Float,
    railWidthPx: Float,
    cornerRadiusPx: Float
) {
    val strokePx = 0.95.dp.toPx()
    val inset = strokePx * 0.5f
    val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)

    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to palette.trackBorderTop,
                0.32f to palette.trackBorderMid,
                0.68f to palette.trackBorderMid,
                1.0f to palette.trackBorderBottom
            ),
            start = Offset(railLeftOffsetPx, 0f),
            end = Offset(railLeftOffsetPx + railWidthPx, size.height * 0.35f)
        ),
        topLeft = Offset(railLeftOffsetPx + inset, inset),
        size = Size(
            (railWidthPx - strokePx).coerceAtLeast(1f),
            (size.height - strokePx).coerceAtLeast(1f)
        ),
        cornerRadius = corner,
        style = Stroke(width = strokePx)
    )
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

/**
 * Draws the ONE moving, expanding ReSukiSU / iOS 26-inspired Liquid Glass selection lens
 * (shared by both the phone bottom navigation pill and the tablet/landscape left navigation rail).
 *
 * Visual physics:
 * - At rest (`expandProgress == 0f`): sits sleekly inside the track with soft smoked-crystal
 *   translucency, subtle lavender reflection, and crisp top-lit rim.
 * - During motion / drag / long-press (`expandProgress -> 1f` or `travelEnergy > 0`):
 *   - Interior **clarifies** into crystal-clear refractive glass (NO chalky/milky white wash!)
 *     so the magnified icon and label stay razor-sharp beneath the droplet.
 *   - Outer rim brightens into realistic crescent specular reflections at top-left & bottom-right
 *     with a delicate lavender chromatic glint along the glass meniscus edge.
 *   - Soft dark elevation drop-shadow lifts the droplet cleanly above the track.
 * - Strictly ONE capsule path, ONE rim stroke — zero duplicate selection backgrounds or outlines.
 */
internal fun DrawScope.drawLiquidGlassCapsule(
    palette: LiquidGlassPalette,
    isDark: Boolean,
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float,
    expandProgress: Float,
    travelEnergy: Float,
    velocityStretchSigned: Float,
    isVertical: Boolean = false,
    customCornerRadiusPx: Float? = null,
    enableSpecularSheen: Boolean = true,
    enableAmbientShadow: Boolean = true
) {
    val clampedWidth = widthPx.coerceAtLeast(1f)
    val clampedHeight = heightPx.coerceAtLeast(1f)
    val radius = (customCornerRadiusPx ?: (minOf(clampedWidth, clampedHeight) / 2f))
        .coerceAtMost(minOf(clampedWidth, clampedHeight) / 2f)
    val corner = CornerRadius(radius, radius)
    val topLeft = Offset(leftPx, topPx)
    val capsuleSize = Size(clampedWidth, clampedHeight)
    val motionEnergy = maxOf(travelEnergy, abs(velocityStretchSigned)).coerceIn(0f, 1.2f)
    val motionDir = sign(velocityStretchSigned)
    // Optical clarity increases smoothly as the droplet lifts or glides (like real clear glass)
    val lensClarity = maxOf(expandProgress, (motionEnergy * 0.65f).coerceAtMost(0.85f)).coerceIn(0f, 1f)

    // 1. Soft elevation depth shadow ONLY while lifted during drag / long-press
    if (enableAmbientShadow && expandProgress > 0.03f) {
        val shadowSpread = 5.dp.toPx() * expandProgress
        val shadowOffsetY = 3.dp.toPx() * expandProgress
        drawRoundRect(
            color = palette.capsuleLiftShadow.copy(
                alpha = palette.capsuleLiftShadow.alpha * expandProgress * 0.62f
            ),
            topLeft = Offset(leftPx - shadowSpread * 0.45f, topPx + shadowOffsetY),
            size = Size(clampedWidth + shadowSpread * 0.9f, clampedHeight + shadowSpread * 0.35f),
            cornerRadius = CornerRadius(radius + shadowSpread * 0.3f, radius + shadowSpread * 0.3f)
        )
    }

    val capsulePath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(topLeft, capsuleSize),
                cornerRadius = corner
            )
        )
    }

    clipPath(capsulePath) {
        // 2. Refractive glass body: sleek smoked tint at rest -> crystal-clear glass droplet when expanded
        val fillTop = lerpColor(palette.restCapsuleFillTop, palette.lensClearFillTop, lensClarity)
        val fillBottom = lerpColor(palette.restCapsuleFillBottom, palette.lensClearFillBottom, lensClarity)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(fillTop, fillBottom),
                startY = topPx,
                endY = topPx + clampedHeight
            ),
            topLeft = topLeft,
            size = capsuleSize
        )

        // 3. Restrained lavender refraction whisper (fades slightly in center when crystal-clear)
        val tintScale = (1f - 0.35f * lensClarity)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.capsuleLavenderTint.copy(alpha = palette.capsuleLavenderTint.alpha * tintScale),
                    palette.capsuleLavenderTint.copy(alpha = palette.capsuleLavenderTint.alpha * 0.4f * tintScale)
                ),
                startY = topPx,
                endY = topPx + clampedHeight
            ),
            topLeft = topLeft,
            size = capsuleSize
        )

        // 4. Crisp top meniscus reflection (tight band along top edge, never fills the middle with white fog)
        val topRefAlpha = palette.capsuleTopReflection.alpha * (0.90f + 0.35f * lensClarity)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = topRefAlpha.coerceAtMost(if (isDark) 0.28f else 0.78f)),
                    0.22f to Color.White.copy(alpha = topRefAlpha * 0.18f),
                    0.50f to Color.Transparent,
                    0.82f to Color.Transparent,
                    // Subtle bottom-edge caustic bounce light inside the glass lens
                    1.0f to (if (isDark) LavenderLuminous else Color.White)
                        .copy(alpha = (0.08f + 0.14f * lensClarity).coerceAtMost(0.24f))
                ),
                startY = topPx,
                endY = topPx + clampedHeight
            ),
            topLeft = topLeft,
            size = capsuleSize
        )

        // 5. Directional liquid specular sheen while sliding or dragging across destinations
        if (enableSpecularSheen && motionEnergy > 0.04f) {
            val sheenAlpha = (if (isDark) 0.20f else 0.42f) * motionEnergy.coerceAtMost(1f)
            if (isVertical) {
                val sheenCenterY = topPx + clampedHeight * (0.5f + 0.26f * motionDir)
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = sheenAlpha),
                            1.0f to Color.Transparent
                        ),
                        startY = sheenCenterY - clampedHeight * 0.42f,
                        endY = sheenCenterY + clampedHeight * 0.42f
                    ),
                    topLeft = topLeft,
                    size = capsuleSize
                )
            } else {
                val sheenCenterX = leftPx + clampedWidth * (0.5f + 0.26f * motionDir)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = sheenAlpha),
                            1.0f to Color.Transparent
                        ),
                        startX = sheenCenterX - clampedWidth * 0.42f,
                        endX = sheenCenterX + clampedWidth * 0.42f
                    ),
                    topLeft = topLeft,
                    size = capsuleSize
                )
            }
        }
    }

    // 6. ONE crisp crescent-lit refractive glass rim (bright top-left + subtle lavender mid + luminous bottom-right)
    val strokePx = (1.05.dp + 0.45.dp * lensClarity).toPx()
    val borderInset = strokePx * 0.5f
    val topRimAlpha = (palette.capsuleBorderTop.alpha * (0.68f + 0.32f * lensClarity)).coerceAtMost(0.98f)
    val midRimColor = if (isDark) {
        lerpColor(palette.capsuleBorderMid, LavenderLuminous.copy(alpha = 0.34f), lensClarity * 0.7f)
    } else {
        lerpColor(palette.capsuleBorderMid, SoftPurple.copy(alpha = 0.40f), lensClarity * 0.6f)
    }
    val botRimAlpha = (palette.capsuleBorderBottom.alpha * (0.68f + 0.32f * lensClarity)).coerceAtMost(0.92f)

    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to palette.capsuleBorderTop.copy(alpha = topRimAlpha),
                0.28f to midRimColor,
                0.72f to midRimColor,
                1.0f to palette.capsuleBorderBottom.copy(alpha = botRimAlpha)
            ),
            start = Offset(leftPx + clampedWidth * 0.12f, topPx),
            end = Offset(leftPx + clampedWidth * 0.88f, topPx + clampedHeight)
        ),
        topLeft = Offset(leftPx + borderInset, topPx + borderInset),
        size = Size(
            (clampedWidth - strokePx).coerceAtLeast(1f),
            (clampedHeight - strokePx).coerceAtLeast(1f)
        ),
        cornerRadius = CornerRadius((radius - borderInset).coerceAtLeast(1f), (radius - borderInset).coerceAtLeast(1f)),
        style = Stroke(width = strokePx)
    )
}
