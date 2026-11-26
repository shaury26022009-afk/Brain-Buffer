package com.example.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
import com.example.performance.PerformanceProfileEngine
import com.example.performance.PerformanceProfileOverride
import com.example.performance.RenderQualityTokens
import com.example.ui.components.LiquidGlassTokens
import com.example.ui.components.LocalLiquidGlassTokens

// ============================================================================
// High-Contrast AMOLED Dark (#000000) & Translucent Liquid Glass Tokens
// ============================================================================

private val AmoledGlassCanvas = Color(0xFF000000)             // True pixel-off AMOLED #000000
private val AmoledGlassSurfaceLowest = Color(0xFF000000)      // Pure AMOLED floor
private val AmoledGlassSurfaceLow = Color(0xB80E0E14)         // 72% translucent recessed glass
private val AmoledGlassSurface = Color(0xCC13131B)            // 80% translucent primary glass surface
private val AmoledGlassContainer = Color(0xD6171721)          // 84% translucent standard glass card
private val AmoledGlassContainerHigh = Color(0xE01F1F2C)      // 88% translucent elevated glass panel
private val AmoledGlassContainerHighest = Color(0xEB282838)   // 92% translucent floating capsule tier
private val AmoledGlassSurfaceBright = Color(0xF0303042)      // 94% translucent specular highlight tier
private val AmoledGlassVariant = Color(0xA61D1D28)            // 65% translucent tracks, pills & wells

private val AmoledHighContrastInk = Color(0xFFFFFFFF)         // 21:1 contrast on #000000
private val AmoledSecondaryInk = Color(0xFFD6D6E4)            // 13.8:1 contrast on #000000
private val AmoledGlassOutline = Color(0x4DFFFFFF)            // 30% translucent specular glass rim
private val AmoledGlassOutlineSubtle = Color(0x2BFFFFFF)      // 17% translucent hairline glass divider

private val AmoledPrimaryLuminous = Color(0xFFC8B6FF)         // 11.2:1 on #000000
private val AmoledOnPrimaryInk = Color(0xFF12072D)
private val AmoledPrimaryGlassContainer = Color(0xA82E2254)   // 66% translucent violet glass container
private val AmoledOnPrimaryGlassInk = Color(0xFFF7F3FF)

private val AmoledSecondaryMint = Color(0xFF66ECC2)           // 13.4:1 on #000000
private val AmoledOnSecondaryInk = Color(0xFF02291D)
private val AmoledSecondaryGlassContainer = Color(0xA310382D) // 64% translucent mint glass container
private val AmoledOnSecondaryGlassInk = Color(0xFFDCFCF2)

private val AmoledTertiaryOrchid = Color(0xFFE2C9FF)          // 13.1:1 on #000000
private val AmoledOnTertiaryInk = Color(0xFF260C4A)
private val AmoledTertiaryGlassContainer = Color(0xA3351D54)  // 64% translucent orchid glass container
private val AmoledOnTertiaryGlassInk = Color(0xFFF8F0FF)

private val AmoledErrorLuminous = Color(0xFFFF859D)           // 8.9:1 on #000000
private val AmoledOnErrorInk = Color(0xFF470514)
private val AmoledErrorGlassContainer = Color(0xB34A1220)     // 70% translucent rose glass container
private val AmoledOnErrorGlassInk = Color(0xFFFFE3EA)

private val AmoledGlassScrim = Color(0xCC000000)

// Subtle translucent frosted light glass mappings for Light Mode parity
private val LightGlassSurface = Color(0xEBFFFFFF)
private val LightGlassSurfaceLow = Color(0xCCF6F3FC)
private val LightGlassContainer = Color(0xD9F1ECFA)
private val LightGlassContainerHigh = Color(0xE6EBE4F7)
private val LightGlassContainerHighest = Color(0xF2E3DAF3)
private val LightGlassVariant = Color(0xBFD9D0EC)
private val LightGlassOutline = Color(0x476B4CE6)
private val LightGlassOutlineSubtle = Color(0x2B6B4CE6)

// ============================================================================
// Material 3 ColorSchemes — Translucent Liquid Glass Light & AMOLED Dark
// ============================================================================

val LightLiquidGlassColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer.copy(alpha = 0.86f),
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer.copy(alpha = 0.86f),
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer.copy(alpha = 0.86f),
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightGlassSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightGlassVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = LightPrimary,
    surfaceDim = Color(0xFFEAE4F6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightGlassSurfaceLow,
    surfaceContainer = LightGlassContainer,
    surfaceContainerHigh = LightGlassContainerHigh,
    surfaceContainerHighest = LightGlassContainerHighest,
    outline = LightGlassOutline,
    outlineVariant = LightGlassOutlineSubtle,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer.copy(alpha = 0.90f),
    onErrorContainer = LightOnErrorContainer,
    scrim = LightScrim
)

val AmoledLiquidGlassColorScheme: ColorScheme = darkColorScheme(
    primary = AmoledPrimaryLuminous,
    onPrimary = AmoledOnPrimaryInk,
    primaryContainer = AmoledPrimaryGlassContainer,
    onPrimaryContainer = AmoledOnPrimaryGlassInk,
    secondary = AmoledSecondaryMint,
    onSecondary = AmoledOnSecondaryInk,
    secondaryContainer = AmoledSecondaryGlassContainer,
    onSecondaryContainer = AmoledOnSecondaryGlassInk,
    tertiary = AmoledTertiaryOrchid,
    onTertiary = AmoledOnTertiaryInk,
    tertiaryContainer = AmoledTertiaryGlassContainer,
    onTertiaryContainer = AmoledOnTertiaryGlassInk,
    background = AmoledGlassCanvas,
    onBackground = AmoledHighContrastInk,
    surface = AmoledGlassSurface,
    onSurface = AmoledHighContrastInk,
    surfaceVariant = AmoledGlassVariant,
    onSurfaceVariant = AmoledSecondaryInk,
    surfaceTint = AmoledPrimaryLuminous,
    surfaceDim = AmoledGlassCanvas,
    surfaceBright = AmoledGlassSurfaceBright,
    surfaceContainerLowest = AmoledGlassSurfaceLowest,
    surfaceContainerLow = AmoledGlassSurfaceLow,
    surfaceContainer = AmoledGlassContainer,
    surfaceContainerHigh = AmoledGlassContainerHigh,
    surfaceContainerHighest = AmoledGlassContainerHighest,
    outline = AmoledGlassOutline,
    outlineVariant = AmoledGlassOutlineSubtle,
    inverseSurface = Color(0xFFF8F8FC),
    inverseOnSurface = Color(0xFF0A0A0D),
    inversePrimary = VioletCore,
    error = AmoledErrorLuminous,
    onError = AmoledOnErrorInk,
    errorContainer = AmoledErrorGlassContainer,
    onErrorContainer = AmoledOnErrorGlassInk,
    scrim = AmoledGlassScrim
)

// Compatibility aliases used across existing screens and tests
val BrainBufferLightColorScheme: ColorScheme = LightLiquidGlassColorScheme
val BrainBufferDarkColorScheme: ColorScheme = AmoledLiquidGlassColorScheme

// ============================================================================
// Semantic Liquid Glass Surface Palette (Bridges M3 ColorScheme & Glass Layers)
// ============================================================================

@Immutable
data class LiquidGlassSurfaceColors(
    val canvas: Color,
    val glassBase: Color,
    val glassSubtle: Color,
    val glassElevated: Color,
    val glassAccent: Color,
    val specularBorderTop: Color,
    val specularBorderBottom: Color,
    val hairlineDivider: Color,
    val innerSpecularGlow: Color,
    val highContrastInk: Color,
    val secondaryInk: Color,
    val scrim: Color,
    val isAmoledDark: Boolean
)

val LightLiquidGlassSurfaceColors = LiquidGlassSurfaceColors(
    canvas = LightBackground,
    glassBase = LightGlassSurface,
    glassSubtle = LightGlassSurfaceLow,
    glassElevated = Color(0xF5FFFFFF),
    glassAccent = Color(0xD9EDE7FF),
    specularBorderTop = Color(0xFAFFFFFF),
    specularBorderBottom = Color(0x406B4CE6),
    hairlineDivider = LightGlassOutlineSubtle,
    innerSpecularGlow = Color(0x8AFFFFFF),
    highContrastInk = LightTextPrimary,
    secondaryInk = LightTextSecondary,
    scrim = LightScrim,
    isAmoledDark = false
)

val AmoledLiquidGlassSurfaceColors = LiquidGlassSurfaceColors(
    canvas = AmoledGlassCanvas,
    glassBase = AmoledGlassSurface,
    glassSubtle = AmoledGlassSurfaceLow,
    glassElevated = AmoledGlassContainerHigh,
    glassAccent = AmoledPrimaryGlassContainer,
    specularBorderTop = Color(0x47FFFFFF),
    specularBorderBottom = Color(0x28FFFFFF),
    hairlineDivider = AmoledGlassOutlineSubtle,
    innerSpecularGlow = Color(0x1EFFFFFF),
    highContrastInk = AmoledHighContrastInk,
    secondaryInk = AmoledSecondaryInk,
    scrim = AmoledGlassScrim,
    isAmoledDark = true
)

private val HighContrastDarkExtendedColors: BrainBufferExtendedColors = DarkExtendedColors.copy(
    secondaryBackground = AmoledGlassSurfaceLow,
    cardBorder = AmoledGlassOutline,
    softGlow = Color(0x26C8B6FF),
    navBarSurface = Color(0xD90E0E14),
    navBarBorder = AmoledGlassOutline,
    softPurpleAccent = AmoledPrimaryLuminous,
    sageAccent = AmoledSecondaryMint
)

/**
 * Refined, moderately rounded geometry tuned for layered Liquid Glass surfaces.
 */
val BrainBufferShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ============================================================================
// CompositionLocals & Centralized Theme Token Accessor
// ============================================================================

val LocalBrainBufferColors = staticCompositionLocalOf { LightExtendedColors }
val LocalLiquidGlassSurfaceColors = staticCompositionLocalOf { LightLiquidGlassSurfaceColors }
val LocalBrainBufferTextStyles = staticCompositionLocalOf { DefaultBrainBufferTextStyles }
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalPerformanceQuality = staticCompositionLocalOf { RenderQualityTokens() }
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

    val surfaceColors: LiquidGlassSurfaceColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidGlassSurfaceColors.current

    val textStyles: BrainBufferTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalBrainBufferTextStyles.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current

    val renderQuality: RenderQualityTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPerformanceQuality.current

    val isAppInForeground: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalAppInForeground.current

    val glass: LiquidGlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidGlassTokens.current

    val blurController: AdaptiveBlurController
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveBlurController.current

    val blurState: AdaptiveBlurState
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveBlurState.current
}

// ============================================================================
// Root Theme Composables
// ============================================================================

@Composable
fun BrainBufferTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotionOverride: Boolean = false,
    performanceOverride: PerformanceProfileOverride = PerformanceProfileOverride.AUTO,
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
    performanceOverride: PerformanceProfileOverride = PerformanceProfileOverride.AUTO,
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
        PerformanceProfileEngine.inspectDevice(context)
    }
    val resolvedProfile = remember(deviceSignals, performanceOverride) {
        PerformanceProfileEngine.resolveProfile(deviceSignals, performanceOverride)
    }
    val renderQuality = remember(resolvedProfile, reducedMotion) {
        RenderQualityTokens.forProfile(
            profile = resolvedProfile,
            reducedMotion = reducedMotion
        )
    }
    val targetScheme = if (darkTheme) AmoledLiquidGlassColorScheme else LightLiquidGlassColorScheme
    val targetExtended = if (darkTheme) HighContrastDarkExtendedColors else LightExtendedColors
    val targetGlassSurface = if (darkTheme) {
        AmoledLiquidGlassSurfaceColors
    } else {
        LightLiquidGlassSurfaceColors
    }

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
        LiquidGlassTokens.resolve(
            isDark = darkTheme,
            renderQuality = renderQuality,
            reducedMotion = reducedMotion,
            blurState = blurState
        )
    }

    CompositionLocalProvider(
        LocalBrainBufferColors provides targetExtended,
        LocalLiquidGlassSurfaceColors provides targetGlassSurface,
        LocalBrainBufferTextStyles provides DefaultBrainBufferTextStyles,
        LocalReducedMotion provides reducedMotion,
        LocalPerformanceQuality provides renderQuality,
        LocalAppInForeground provides isAppInForeground,
        LocalAdaptiveBlurController provides blurController,
        LocalAdaptiveBlurState provides blurState,
        LocalLiquidGlassTokens provides liquidGlassTokens,
        LocalContentColor provides targetScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = targetScheme,
            typography = BrainBufferTypography,
            shapes = BrainBufferShapes,
            content = content
        )
    }
}
