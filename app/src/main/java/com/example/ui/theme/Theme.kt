package com.example.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode
import com.example.performance.AdaptiveBlurController
import com.example.performance.AdaptiveBlurFrameMonitor
import com.example.performance.AdaptiveBlurState
import com.example.performance.LocalAdaptiveBlurController
import com.example.performance.LocalAdaptiveBlurState

val BrainBufferLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = LightScrim
)

val BrainBufferDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = DarkScrim
)

/**
 * Refined, moderately rounded geometry — crisp & modern without excessive bubble rounding.
 */
val BrainBufferShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val LocalBrainBufferColors = staticCompositionLocalOf { LightExtendedColors }
val LocalBrainBufferTextStyles = staticCompositionLocalOf { DefaultBrainBufferTextStyles }
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalPerformanceQuality = staticCompositionLocalOf { com.example.performance.RenderQualityTokens() }
val LocalAppInForeground = staticCompositionLocalOf { true }

object BrainBufferThemeTokens {
    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    val extended: BrainBufferExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrainBufferColors.current

    val textStyles: BrainBufferTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalBrainBufferTextStyles.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current

    val renderQuality: com.example.performance.RenderQualityTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPerformanceQuality.current

    val isAppInForeground: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalAppInForeground.current

    val glass: com.example.ui.components.LiquidGlassTokens
        @Composable
        @ReadOnlyComposable
        get() = com.example.ui.components.LocalLiquidGlassTokens.current

    val blurController: AdaptiveBlurController
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveBlurController.current

    val blurState: AdaptiveBlurState
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveBlurState.current
}

@Composable
private fun animateThemeColor(target: Color, durationMs: Int): Color {
    if (durationMs <= 0) return target
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = durationMs),
        label = "themeColor"
    )
    return color
}

@Composable
fun BrainBufferTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotionOverride: Boolean = false,
    performanceOverride: com.example.performance.PerformanceProfileOverride =
        com.example.performance.PerformanceProfileOverride.AUTO,
    isAppInForeground: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    BrainBufferTheme(
        darkTheme = useDark,
        reducedMotionOverride = reducedMotionOverride,
        performanceOverride = performanceOverride,
        isAppInForeground = isAppInForeground,
        content = content
    )
}

@Composable
fun BrainBufferTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotionOverride: Boolean = false,
    performanceOverride: com.example.performance.PerformanceProfileOverride =
        com.example.performance.PerformanceProfileOverride.AUTO,
    isAppInForeground: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemReducedMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
    val reducedMotion = reducedMotionOverride || systemReducedMotion
    val deviceSignals = remember(context) {
        com.example.performance.PerformanceProfileEngine.inspectDevice(context)
    }
    val resolvedProfile = remember(deviceSignals, performanceOverride) {
        com.example.performance.PerformanceProfileEngine.resolveProfile(deviceSignals, performanceOverride)
    }
    val renderQuality = remember(resolvedProfile, reducedMotion) {
        com.example.performance.RenderQualityTokens.forProfile(
            profile = resolvedProfile,
            reducedMotion = reducedMotion
        )
    }
    val animMs = renderQuality.transitionDurationMs

    val targetScheme = if (darkTheme) BrainBufferDarkColorScheme else BrainBufferLightColorScheme
    val animatedScheme = targetScheme.copy(
        background = animateThemeColor(targetScheme.background, animMs),
        surface = animateThemeColor(targetScheme.surface, animMs),
        surfaceVariant = animateThemeColor(targetScheme.surfaceVariant, animMs),
        surfaceContainerLow = animateThemeColor(targetScheme.surfaceContainerLow, animMs),
        surfaceContainer = animateThemeColor(targetScheme.surfaceContainer, animMs),
        surfaceContainerHigh = animateThemeColor(targetScheme.surfaceContainerHigh, animMs),
        onBackground = animateThemeColor(targetScheme.onBackground, animMs),
        onSurface = animateThemeColor(targetScheme.onSurface, animMs),
        onSurfaceVariant = animateThemeColor(targetScheme.onSurfaceVariant, animMs),
        primary = animateThemeColor(targetScheme.primary, animMs),
        primaryContainer = animateThemeColor(targetScheme.primaryContainer, animMs)
    )

    val targetExtended = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val animatedExtended = targetExtended.copy(
        secondaryBackground = animateThemeColor(targetExtended.secondaryBackground, animMs),
        cardBorder = animateThemeColor(targetExtended.cardBorder, animMs),
        navBarSurface = animateThemeColor(targetExtended.navBarSurface, animMs),
        navBarBorder = animateThemeColor(targetExtended.navBarBorder, animMs)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    val thermalStatus = remember(context, isAppInForeground) {
        AdaptiveBlurController.inspectThermalStatus(context)
    }
    val blurController = remember {
        AdaptiveBlurController(
            initialProfile = resolvedProfile,
            initialSdkInt = deviceSignals.sdkInt,
            initialPowerSaveMode = deviceSignals.isPowerSaveMode,
            initialThermalStatus = thermalStatus
        )
    }
    LaunchedEffect(resolvedProfile, deviceSignals.sdkInt, deviceSignals.isPowerSaveMode, thermalStatus) {
        blurController.updateEnvironment(
            profile = resolvedProfile,
            sdkInt = deviceSignals.sdkInt,
            isPowerSaveMode = deviceSignals.isPowerSaveMode,
            thermalStatus = thermalStatus
        )
    }
    val blurState by blurController.state.collectAsState()
    AdaptiveBlurFrameMonitor(
        controller = blurController,
        blurState = blurState,
        isAppInForeground = isAppInForeground
    )

    val liquidGlassTokens = remember(darkTheme, renderQuality, reducedMotion, blurState.navBlurRadiusDp) {
        com.example.ui.components.LiquidGlassTokens.resolve(
            isDark = darkTheme,
            renderQuality = renderQuality,
            reducedMotion = reducedMotion,
            blurState = blurState
        )
    }

    CompositionLocalProvider(
        LocalBrainBufferColors provides animatedExtended,
        LocalBrainBufferTextStyles provides DefaultBrainBufferTextStyles,
        LocalReducedMotion provides reducedMotion,
        LocalPerformanceQuality provides renderQuality,
        LocalAppInForeground provides isAppInForeground,
        LocalAdaptiveBlurController provides blurController,
        LocalAdaptiveBlurState provides blurState,
        com.example.ui.components.LocalLiquidGlassTokens provides liquidGlassTokens
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = BrainBufferTypography,
            shapes = BrainBufferShapes,
            content = content
        )
    }
}
