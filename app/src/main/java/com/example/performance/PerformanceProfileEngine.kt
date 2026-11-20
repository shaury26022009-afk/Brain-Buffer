package com.example.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.runtime.Immutable

/**
 * Automatic & user-overridable rendering profiles so BRAIN BUFFER feels crisp on
 * budget Android 10 phones and lush on flagship tablets and phones.
 */
enum class PerformanceProfile(val label: String, val summary: String) {
    PERFORMANCE(
        label = "Performance",
        summary = "Low tier · Zero GPU blur, crisp transitions, maximum battery & 60fps lock"
    ),
    BALANCED(
        label = "Balanced",
        summary = "Medium tier · Lightweight selective Liquid Glass blur & smooth 60fps motion"
    ),
    PREMIUM(
        label = "Premium",
        summary = "High tier · Full Liquid Glass frosted blur, spring depth & rich garden details"
    );

    val blurTier: BlurPerformanceTier
        get() = BlurPerformanceTier.fromProfile(this)
}

enum class PerformanceProfileOverride(val label: String) {
    AUTO("Automatic"),
    PERFORMANCE("Performance"),
    BALANCED("Balanced"),
    PREMIUM("Premium")
}

/**
 * Multi-signal hardware & runtime observation snapshot.
 * Deliberately evaluates CPU cores, low-RAM flag, power-saver state, refresh rate,
 * Android version, RAM tier, and live frame-jank observations — never RAM alone.
 */
@Immutable
data class DevicePerformanceSignals(
    val totalRamMb: Int,
    val isLowRamDevice: Boolean,
    val cpuCores: Int,
    val sdkInt: Int,
    val refreshRateHz: Float,
    val isPowerSaveMode: Boolean,
    val observedJankRatio: Float = 0f
)

/**
 * Resolved visual & animation quality tokens consumed by Liquid Glass, Garden Canvas,
 * Focus timer ring, and screen transitions.
 */
@Immutable
data class RenderQualityTokens(
    val profile: PerformanceProfile = PerformanceProfile.BALANCED,
    val enableGlassBlur: Boolean = true,
    val glassBlurRadiusDp: Float = AdaptiveBlurPolicy.baseRadiusDp(
        BlurPerformanceTier.MEDIUM,
        BlurSurfaceRole.NAVIGATION_TRACK
    ),
    val enableSpecularSheen: Boolean = true,
    val enableAmbientShadows: Boolean = true,
    val enableContinuousCanvasMotion: Boolean = true,
    val springStiffnessScale: Float = 1.0f,
    val gardenFireflyCount: Int = 3,
    val gardenStarCount: Int = 6,
    val transitionDurationMs: Int = 220
) {
    companion object {
        fun forProfile(
            profile: PerformanceProfile,
            sdkInt: Int = Build.VERSION.SDK_INT,
            reducedMotion: Boolean = false
        ): RenderQualityTokens {
            val supportsRenderEffectBlur = sdkInt >= Build.VERSION_CODES.S
            val baseBlurDp = AdaptiveBlurPolicy.baseRadiusDp(
                tier = BlurPerformanceTier.fromProfile(profile),
                role = BlurSurfaceRole.NAVIGATION_TRACK
            )
            val base = when (profile) {
                PerformanceProfile.PERFORMANCE -> RenderQualityTokens(
                    profile = PerformanceProfile.PERFORMANCE,
                    enableGlassBlur = false,
                    glassBlurRadiusDp = 0f,
                    enableSpecularSheen = false,
                    enableAmbientShadows = false,
                    enableContinuousCanvasMotion = false,
                    springStiffnessScale = 1.55f,
                    gardenFireflyCount = 0,
                    gardenStarCount = 3,
                    transitionDurationMs = 120
                )
                PerformanceProfile.BALANCED -> RenderQualityTokens(
                    profile = PerformanceProfile.BALANCED,
                    enableGlassBlur = supportsRenderEffectBlur && baseBlurDp > 0f,
                    glassBlurRadiusDp = if (supportsRenderEffectBlur) baseBlurDp else 0f,
                    enableSpecularSheen = true,
                    enableAmbientShadows = true,
                    enableContinuousCanvasMotion = true,
                    springStiffnessScale = 1.15f,
                    gardenFireflyCount = 2,
                    gardenStarCount = 4,
                    transitionDurationMs = 200
                )
                PerformanceProfile.PREMIUM -> RenderQualityTokens(
                    profile = PerformanceProfile.PREMIUM,
                    enableGlassBlur = supportsRenderEffectBlur && baseBlurDp > 0f,
                    glassBlurRadiusDp = if (supportsRenderEffectBlur) baseBlurDp else 0f,
                    enableSpecularSheen = true,
                    enableAmbientShadows = true,
                    enableContinuousCanvasMotion = true,
                    springStiffnessScale = 1.0f,
                    gardenFireflyCount = 4,
                    gardenStarCount = 6,
                    transitionDurationMs = 240
                )
            }
            return if (reducedMotion) {
                base.copy(
                    enableContinuousCanvasMotion = false,
                    transitionDurationMs = 0,
                    springStiffnessScale = 2.0f
                )
            } else {
                base
            }
        }
    }
}

object PerformanceProfileEngine {

    /**
     * Collects real device signals safely across Android 10 (API 29) through Android 16+.
     */
    fun inspectDevice(context: Context, observedJankRatio: Float = 0f): DevicePerformanceSignals {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val totalRamMb = runCatching {
            am?.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024L * 1024L)).toInt()
        }.getOrDefault(4096).coerceAtLeast(512)

        val isLowRam = runCatching { am?.isLowRamDevice ?: false }.getOrDefault(false)
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { pm?.isPowerSaveMode ?: false }.getOrDefault(false)

        val refreshRate = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.refreshRate ?: 60f
            }
        }.getOrDefault(60f)

        return DevicePerformanceSignals(
            totalRamMb = totalRamMb,
            isLowRamDevice = isLowRam,
            cpuCores = cpuCores,
            sdkInt = Build.VERSION.SDK_INT,
            refreshRateHz = refreshRate,
            isPowerSaveMode = isPowerSave,
            observedJankRatio = observedJankRatio.coerceIn(0f, 1f)
        )
    }

    /**
     * Resolves the effective [PerformanceProfile] from multi-factor signals and any user override.
     */
    fun resolveProfile(
        signals: DevicePerformanceSignals,
        override: PerformanceProfileOverride = PerformanceProfileOverride.AUTO
    ): PerformanceProfile {
        when (override) {
            PerformanceProfileOverride.PERFORMANCE -> return PerformanceProfile.PERFORMANCE
            PerformanceProfileOverride.BALANCED -> return PerformanceProfile.BALANCED
            PerformanceProfileOverride.PREMIUM -> return PerformanceProfile.PREMIUM
            PerformanceProfileOverride.AUTO -> Unit
        }

        // Hard safety downgrades for low-RAM devices, battery saver, or severe observed jank
        if (signals.isLowRamDevice || signals.isPowerSaveMode || signals.observedJankRatio >= 0.28f) {
            return PerformanceProfile.PERFORMANCE
        }

        var score = 0
        // CPU parallelism contribution
        score += when {
            signals.cpuCores >= 8 -> 3
            signals.cpuCores >= 6 -> 2
            signals.cpuCores >= 4 -> 1
            else -> 0
        }
        // Memory headroom contribution (only one factor among several)
        score += when {
            signals.totalRamMb >= 7500 -> 3
            signals.totalRamMb >= 4500 -> 2
            signals.totalRamMb >= 3000 -> 1
            else -> 0
        }
        // Display refresh capability
        score += when {
            signals.refreshRateHz >= 115f -> 2
            signals.refreshRateHz >= 85f -> 1
            else -> 0
        }
        // Android RenderEffect / modern Skia GPU pipeline support
        score += when {
            signals.sdkInt >= Build.VERSION_CODES.TIRAMISU -> 2
            signals.sdkInt >= Build.VERSION_CODES.S -> 1
            else -> 0
        }
        // Live runtime jank penalty
        if (signals.observedJankRatio >= 0.14f) {
            score -= 2
        }

        return when {
            score >= 8 -> PerformanceProfile.PREMIUM
            score >= 4 -> PerformanceProfile.BALANCED
            else -> PerformanceProfile.PERFORMANCE
        }
    }
}
