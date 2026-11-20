package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.StudentAvatar
import com.example.model.Subject
import com.example.model.SubjectIcon
import com.example.model.TaskPriority
import com.example.ui.theme.BrainBufferThemeTokens

fun subjectIconVector(key: String): ImageVector = when (key) {
    SubjectIcon.CALCULATE.key -> Icons.Rounded.Calculate
    SubjectIcon.SCIENCE.key -> Icons.Rounded.Science
    SubjectIcon.CODE.key -> Icons.Rounded.Code
    SubjectIcon.DESIGN.key -> Icons.Rounded.Brush
    SubjectIcon.LANGUAGE.key -> Icons.Rounded.Translate
    SubjectIcon.HISTORY.key -> Icons.Rounded.HistoryEdu
    SubjectIcon.LIGHTBULB.key -> Icons.Rounded.Lightbulb
    else -> Icons.AutoMirrored.Rounded.MenuBook
}

fun avatarIconVector(avatar: StudentAvatar): ImageVector = when (avatar) {
    StudentAvatar.SPROUT -> Icons.Rounded.Spa
    StudentAvatar.SPARK -> Icons.Rounded.AutoAwesome
    StudentAvatar.BOOK -> Icons.Rounded.School
    StudentAvatar.MOON -> Icons.Rounded.NightsStay
    StudentAvatar.COMPASS -> Icons.Rounded.Explore
    StudentAvatar.LOTUS -> Icons.Rounded.Lightbulb
}

@Composable
fun StudentAvatarBadge(
    avatar: StudentAvatar,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val tint = Color(avatar.tintHex)
    LiquidGlassIconContainer(
        modifier = modifier,
        size = size,
        accentColor = tint,
        shape = CircleShape
    ) {
        Icon(
            imageVector = avatarIconVector(avatar),
            contentDescription = avatar.label,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

fun Modifier.springClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "springPressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Clean atmospheric backdrop:
 * - Light mode: soft white with subtle lavender luminescence.
 * - Dark mode: genuine AMOLED pure #000000 black with zero blue/navy wash.
 */
@Composable
fun SanctuaryBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val extended = BrainBufferThemeTokens.extended
    val glass = BrainBufferThemeTokens.glass

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                if (!extended.isDark) {
                    // Soft sunlit frosted lavender ambient gradient in light mode
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(extended.softGlow, Color.Transparent),
                            center = Offset(size.width * 0.88f, size.height * 0.04f),
                            radius = size.width * 0.90f
                        ),
                        radius = size.width * 0.90f,
                        center = Offset(size.width * 0.88f, size.height * 0.04f)
                    )
                    if (glass.enableSpecularSheen) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x149377F8), Color.Transparent),
                                center = Offset(size.width * 0.10f, size.height * 0.42f),
                                radius = size.width * 0.65f
                            ),
                            radius = size.width * 0.65f,
                            center = Offset(size.width * 0.10f, size.height * 0.42f)
                        )
                    }
                } else if (glass.enableSpecularSheen) {
                    // Ultra-restrained top-edge lavender whisper over pure #000000 AMOLED black (zero blue tint)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x11B7A1F7), Color.Transparent),
                            center = Offset(size.width * 0.82f, size.height * 0.02f),
                            radius = size.width * 0.72f
                        ),
                        radius = size.width * 0.72f,
                        center = Offset(size.width * 0.82f, size.height * 0.02f)
                    )
                }
            },
        content = content
    )
}

@Composable
fun SanctuaryCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = BrainBufferThemeTokens.extended.cardBorder,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = if (BrainBufferThemeTokens.extended.isDark) 0.dp else 1.5.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val defaultSurface = MaterialTheme.colorScheme.surface
    val subtleSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val defaultBorder = BrainBufferThemeTokens.extended.cardBorder

    val tone = when {
        containerColor == subtleSurface -> LiquidGlassTone.SUBTLE
        containerColor == defaultSurface -> LiquidGlassTone.STANDARD
        else -> LiquidGlassTone.STANDARD
    }
    val tintOverlay = if (containerColor != defaultSurface && containerColor != subtleSurface) {
        containerColor.copy(alpha = (containerColor.alpha * 0.34f).coerceIn(0.08f, 0.36f))
    } else {
        null
    }
    val borderAccent = if (borderColor != defaultBorder) borderColor else null
    val cornerDp = if (shape == RoundedCornerShape(24.dp)) 24.dp else 20.dp

    LiquidGlassCard(
        modifier = modifier,
        tone = tone,
        cornerRadius = cornerDp,
        tintOverlay = tintOverlay,
        borderAccent = borderAccent,
        elevation = if (shadowElevation > 0.dp) shadowElevation else null,
        contentPadding = contentPadding,
        onClick = onClick,
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            LiquidGlassChip(
                label = actionLabel,
                selected = true,
                onClick = onActionClick
            )
        }
    }
}

@Composable
fun SubjectBadge(
    name: String,
    accentColor: Color,
    iconKey: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isDark = BrainBufferThemeTokens.extended.isDark
    val bgAlpha = if (isDark) 0.2f else 0.12f

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = bgAlpha))
            .padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 4.dp else 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (iconKey != null) {
            Icon(
                imageVector = subjectIconVector(iconKey),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SubjectTag(
    subject: Subject,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    SubjectBadge(
        name = subject.name,
        accentColor = subject.accentColor,
        iconKey = subject.iconKey,
        modifier = modifier,
        compact = compact
    )
}

@Composable
fun PriorityBadge(
    priority: TaskPriority,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) to MaterialTheme.colorScheme.primary
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) to MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = priority.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
fun CircularProgressBadge(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 66.dp,
    strokeWidth: Dp = 7.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    centerText: String
) {
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (reducedMotion) Spring.StiffnessHigh else Spring.StiffnessLow
        ),
        label = "ringProgress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailingAction?.invoke()
    }
}

@Composable
fun EmptySanctuaryCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        tone = LiquidGlassTone.SUBTLE,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiquidGlassIconContainer(
                size = 54.dp,
                accentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                LiquidGlassButton(
                    onClick = onAction,
                    height = 46.dp,
                    cornerRadius = 14.dp,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
