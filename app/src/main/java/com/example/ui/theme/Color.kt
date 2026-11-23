package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ============================================================================
// Core Brand Palette — Lavender, Violet & Soft Purple
// ============================================================================

val VioletCore = Color(0xFF6B4CE6)
val VioletDeep = Color(0xFF4F33C7)
val VioletMidnight = Color(0xFF23115E)
val SoftPurple = Color(0xFF9377F8)
val LavenderLuminous = Color(0xFFB7A1F7)
val LavenderSoft = Color(0xFFCBB8FA)
val LavenderMist = Color(0xFFEDE7FF)
val LavenderCloud = Color(0xFFF5F1FC)
val BrandSuccess = Color(0xFF2FA687)
val BrandWarmAmber = Color(0xFFF59E0B)

// ============================================================================
// Light Mode — Soft White Backgrounds & Very Light Lavender Surfaces
// ============================================================================

val LightBackground = Color(0xFFFAF9FD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceLowest = Color(0xFFFFFFFF)
val LightSurfaceLow = Color(0xFFF6F3FC)
val LightSurfaceContainer = Color(0xFFF1ECFA)
val LightSurfaceHigh = Color(0xFFEBE4F7)
val LightSurfaceHighest = Color(0xFFE3DAF3)
val LightSurfaceVariant = Color(0xFFF3EFFB)

val LightPrimary = VioletCore
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = LavenderMist
val LightOnPrimaryContainer = VioletMidnight

val LightSecondary = Color(0xFF2B9A7C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD9F5EC)
val LightOnSecondaryContainer = Color(0xFF0A382B)

val LightTertiary = Color(0xFF9466E6)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF2E8FF)
val LightOnTertiaryContainer = Color(0xFF331466)

val LightTextPrimary = Color(0xFF181426)
val LightTextSecondary = Color(0xFF635B78)
val LightOutline = Color(0xFFDFD8EE)
val LightOutlineVariant = Color(0xFFECE6F7)

val LightInverseSurface = Color(0xFF18181B)
val LightInverseOnSurface = Color(0xFFF4F4F5)
val LightInversePrimary = LavenderLuminous

val LightError = Color(0xFFD64564)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE5EB)
val LightOnErrorContainer = Color(0xFF5C1123)
val LightScrim = Color(0x52000000)

// ============================================================================
// Dark Mode — True AMOLED Black (#000000) with Pure Neutral Surfaces & Violet Accent
// Zero navy / dark blue tint anywhere.
// ============================================================================

val AmoledBlack = Color(0xFF000000)
val AmoledSecondaryBg = Color(0xFF090909)
val AmoledCard = Color(0xFF121215)
val AmoledElevated = Color(0xFF19191E)
val AmoledElevatedHigh = Color(0xFF212128)
val AmoledBorder = Color(0xFF26262C)
val AmoledBorderSubtle = Color(0xFF1C1C21)
val AmoledTextPrimary = Color(0xFFFFFFFF)
val AmoledTextSecondary = Color(0xFFD2D2DE)

val DarkBackground = AmoledBlack
val DarkSurface = AmoledCard
val DarkSurfaceLowest = AmoledBlack
val DarkSurfaceLow = AmoledSecondaryBg
val DarkSurfaceContainer = AmoledCard
val DarkSurfaceHigh = AmoledElevated
val DarkSurfaceHighest = AmoledElevatedHigh
val DarkSurfaceVariant = AmoledElevated

val DarkPrimary = LavenderLuminous
val DarkOnPrimary = Color(0xFF160B36)
val DarkPrimaryContainer = Color(0xFF211C30)
val DarkOnPrimaryContainer = Color(0xFFF3EEFF)

val DarkSecondary = Color(0xFF5EE0B6)
val DarkOnSecondary = Color(0xFF042E22)
val DarkSecondaryContainer = Color(0xFF112922)
val DarkOnSecondaryContainer = Color(0xFFD3F7EB)

val DarkTertiary = Color(0xFFD4B5FF)
val DarkOnTertiary = Color(0xFF2B1052)
val DarkTertiaryContainer = Color(0xFF28193D)
val DarkOnTertiaryContainer = Color(0xFFF4E9FF)

val DarkTextPrimary = AmoledTextPrimary
val DarkTextSecondary = AmoledTextSecondary
val DarkOutline = AmoledBorder
val DarkOutlineVariant = AmoledBorderSubtle

val DarkInverseSurface = Color(0xFFF4F4F5)
val DarkInverseOnSurface = Color(0xFF111111)
val DarkInversePrimary = VioletCore

val DarkError = Color(0xFFFF7A93)
val DarkOnError = Color(0xFF470817)
val DarkErrorContainer = Color(0xFF3A121C)
val DarkOnErrorContainer = Color(0xFFFFD6DF)
val DarkScrim = Color(0xB3000000)

// ============================================================================
// Curated Subject Color Swatches (Selectable by Students)
// ============================================================================

val SubjectSwatchPalette = listOf(
    0xFF7B5CFA, // Violet
    0xFF3B82F6, // Cobalt
    0xFF10B981, // Emerald
    0xFFEC4899, // Rose
    0xFFF59E0B, // Amber
    0xFFEF6C57, // Coral
    0xFF06B6D4, // Cyan
    0xFFA855F7  // Orchid
)

val SubjectViolet = Color(0xFF7B5CFA)
val SubjectMint = Color(0xFF2FA687)
val SubjectCoral = Color(0xFFE58268)
val SubjectSky = Color(0xFF5694E8)
val SubjectRose = Color(0xFFD46AB3)
val SubjectAmber = Color(0xFFDCA14A)

@Immutable
data class BrainBufferExtendedColors(
    val secondaryBackground: Color,
    val cardBorder: Color,
    val softGlow: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val gardenSkyTop: Color,
    val gardenSkyBottom: Color,
    val gardenHillFar: Color,
    val gardenHillNear: Color,
    val gardenSoil: Color,
    val fireflyGold: Color,
    val sageAccent: Color,
    val peachAccent: Color,
    val lavenderMist: Color,
    val softPurpleAccent: Color,
    val navBarSurface: Color,
    val navBarBorder: Color,
    val isDark: Boolean
)

val LightExtendedColors = BrainBufferExtendedColors(
    secondaryBackground = Color(0xFFF5F2FB),
    cardBorder = Color(0xFFE8E2F4),
    softGlow = Color(0x226B4CE6),
    heroGradientStart = Color(0xFF6B4CE6),
    heroGradientEnd = Color(0xFF9377F8),
    gardenSkyTop = Color(0xFFE9E0FF),
    gardenSkyBottom = Color(0xFFFAF7FF),
    gardenHillFar = Color(0xFFCDE9DF),
    gardenHillNear = Color(0xFFA7DAC9),
    gardenSoil = Color(0xFF7A6A8F),
    fireflyGold = Color(0xFFF5C25B),
    sageAccent = Color(0xFF2B9A7C),
    peachAccent = Color(0xFFE0875C),
    lavenderMist = LavenderCloud,
    softPurpleAccent = SoftPurple,
    navBarSurface = Color(0xFCFFFFFF),
    navBarBorder = Color(0xFFE7E1F3),
    isDark = false
)

val DarkExtendedColors = BrainBufferExtendedColors(
    secondaryBackground = AmoledSecondaryBg,
    cardBorder = AmoledBorder,
    softGlow = Color(0x1AB7A1F7),
    heroGradientStart = Color(0xFF111111),
    heroGradientEnd = Color(0xFF1B1726),
    gardenSkyTop = Color(0xFF040404),
    gardenSkyBottom = Color(0xFF111111),
    gardenHillFar = Color(0xFF14211D),
    gardenHillNear = Color(0xFF183028),
    gardenSoil = Color(0xFF202020),
    fireflyGold = Color(0xFFFDE08D),
    sageAccent = Color(0xFF5EE0B6),
    peachAccent = Color(0xFFF4A982),
    lavenderMist = Color(0xFF181818),
    softPurpleAccent = LavenderLuminous,
    navBarSurface = Color(0xFA0D0D0D),
    navBarBorder = AmoledBorder,
    isDark = true
)
