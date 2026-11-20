package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LavenderLuminous
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.VioletCore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original BRAIN BUFFER geometric vector identity ("Focus Orbit & Neural Buffer"):
 * - An open precision focus ring with a luminous buffer node at top-right representing
 *   intentional mental space and steady progress.
 * - Paired minimal neural-crescent arcs inside the orbit symbolizing calm, balanced thought.
 * - Crisp at every scale from 24dp header icons to 120dp splash displays.
 */
@Composable
fun BrainBufferLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animatePulse: Boolean = false,
    ringSweepFraction: Float = 1f,
    glowAlpha: Float = 0.22f
) {
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val isDark = BrainBufferThemeTokens.extended.isDark
    val primary = MaterialTheme.colorScheme.primary

    val ringStartColor = if (isDark) Color(0xFFD8CCFF) else VioletCore
    val ringEndColor = if (isDark) SoftPurple else SoftPurple
    val coreColor = if (isDark) Color(0xFFF3EEFF) else VioletCore
    val nodeColor = if (isDark) LavenderLuminous else Color(0xFF7B5CFA)

    val pulse = if (animatePulse && !reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "logoGlowPulse")
        infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoPulseScale"
        ).value
    } else {
        1f
    }

    val clampedSweep = ringSweepFraction.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Brain Buffer Logo" }
    ) {
        val dim = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val strokeWidth = (dim * 0.085f).coerceAtLeast(1.8.dp.toPx())
        val innerStroke = (dim * 0.072f).coerceAtLeast(1.5.dp.toPx())

        // 1. Subtle ambient radial focus glow (soft, never harsh)
        if (glowAlpha > 0.01f) {
            val glowRadius = dim * 0.56f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )
        }

        // 2. Outer open Focus Orbit / Buffer Ring (292-degree precision arc leaving a clean gap at top-right)
        val orbitRadius = dim * 0.40f
        val orbitTopLeft = Offset(cx - orbitRadius, cy - orbitRadius)
        val orbitSize = Size(orbitRadius * 2f, orbitRadius * 2f)

        // Faint recessed track arc for subtle depth when animating in
        drawArc(
            color = primary.copy(alpha = if (isDark) 0.14f else 0.10f),
            startAngle = -24f,
            sweepAngle = 294f,
            useCenter = false,
            topLeft = orbitTopLeft,
            size = orbitSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (clampedSweep > 0.005f) {
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(ringStartColor, ringEndColor),
                    start = Offset(cx - orbitRadius, cy - orbitRadius),
                    end = Offset(cx + orbitRadius, cy + orbitRadius)
                ),
                startAngle = -24f,
                sweepAngle = 294f * clampedSweep,
                useCenter = false,
                topLeft = orbitTopLeft,
                size = orbitSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // 3. Luminous Buffer Node nestled cleanly inside the top-right orbital gap (-55 degrees)
        val nodeAngleRad = (-55.0 * PI / 180.0)
        val nodeX = cx + (orbitRadius * cos(nodeAngleRad)).toFloat()
        val nodeY = cy + (orbitRadius * sin(nodeAngleRad)).toFloat()
        val nodeRadius = (dim * 0.062f * (0.4f + 0.6f * clampedSweep)).coerceAtLeast(1.4.dp.toPx())
        drawCircle(
            color = nodeColor.copy(alpha = 0.28f * clampedSweep),
            radius = nodeRadius * 1.9f,
            center = Offset(nodeX, nodeY)
        )
        drawCircle(
            color = nodeColor.copy(alpha = clampedSweep),
            radius = nodeRadius,
            center = Offset(nodeX, nodeY)
        )

        // 4. Inner Minimal Neural Symmetry (two balanced geometric crescent hemispheres with calm negative space)
        val lobeRadius = dim * 0.165f
        val lobeOffsetX = dim * 0.078f

        // Left hemisphere arc (clean C-curve)
        val leftCenterX = cx - lobeOffsetX
        drawArc(
            color = coreColor,
            startAngle = 112f,
            sweepAngle = 136f,
            useCenter = false,
            topLeft = Offset(leftCenterX - lobeRadius, cy - lobeRadius),
            size = Size(lobeRadius * 2f, lobeRadius * 2f),
            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
        )

        // Right hemisphere arc (mirrored curve)
        val rightCenterX = cx + lobeOffsetX
        drawArc(
            color = coreColor.copy(alpha = if (isDark) 0.90f else 0.85f),
            startAngle = -68f,
            sweepAngle = 136f,
            useCenter = false,
            topLeft = Offset(rightCenterX - lobeRadius, cy - lobeRadius),
            size = Size(lobeRadius * 2f, lobeRadius * 2f),
            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
        )

        // Subtle central stillness bar / synaptic balance line
        val barHalfHeight = dim * 0.105f
        drawLine(
            color = nodeColor.copy(alpha = 0.85f),
            start = Offset(cx, cy - barHalfHeight),
            end = Offset(cx, cy + barHalfHeight),
            strokeWidth = (innerStroke * 0.72f).coerceAtLeast(1.2.dp.toPx()),
            cap = StrokeCap.Round
        )
    }
}
