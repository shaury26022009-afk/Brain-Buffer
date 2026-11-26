package com.example.performance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrainBufferThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Explicit three-tier blur budget classification aligned with [PerformanceProfile]:
 * - [HIGH]: Flagship / high-headroom devices (Premium profile) — rich frosted Liquid Glass.
 * - [MEDIUM]: Mid-range devices (Balanced profile) — lightweight selective blur on primary floating tracks.
 * - [LOW]: Budget / low-RAM / power-saver devices (Performance profile) — zero RenderEffect blur,
 *   using single-pass alpha-compensated translucent gradients for locked 60fps.
 */
enum class BlurPerformanceTier(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    companion object {
        fun fromProfile(profile: PerformanceProfile): BlurPerformanceTier = when (profile) {
            PerformanceProfile.PREMIUM -> HIGH
            PerformanceProfile.BALANCED -> MEDIUM
            PerformanceProfile.PERFORMANCE -> LOW
        }
    }
}

/**
 * Surface roles with strict GPU cost budgets so Liquid Glass never stacks expensive
 * RenderEffect blurs across scrolling list items.
 */
enum class BlurSurfaceRole {
    /** Primary floating bottom navigation pill & tablet navigation rail track. */
    NAVIGATION_TRACK,
    /** Dynamic Island-inspired floating live focus pill. */
    FLOATING_PILL,
    /** Floating elevated dialogs and modal sheets. */
    MODAL_SHEET_OR_DIALOG,
    /** Static top hero panel (blurred only on High tier with 60fps headroom). */
    HERO_CARD,
    /** Cards inside LazyColumn/LazyRow — ALWAYS 0.dp to guarantee 60fps scrolling. */
    SCROLLING_CARD
}

/**
 * Deterministic base blur radii (in dp) per [BlurPerformanceTier] and [BlurSurfaceRole].
 */
object AdaptiveBlurPolicy {
    const val MIN_EFFECTIVE_BLUR_DP = 1.5f
    const val TARGET_FRAME_TIME_MS = 16.67f
    const val SLOW_FRAME_THRESHOLD_MS = 18.5f
    const val EMA_DOWNGRADE_THRESHOLD_MS = 19.5f
    const val EMA_RECOVERY_THRESHOLD_MS = 16.8f
    const val WINDOW_FRAME_COUNT = 24
    const val RECOVERY_WINDOWS_REQUIRED = 3

    /** Discrete ladder prevents continuous RenderEffect recreation while adapting to frame load. */
    val DYNAMIC_SCALE_LADDER: FloatArray = floatArrayOf(1.0f, 0.72f, 0.45f, 0.0f)

    fun baseRadiusDp(tier: BlurPerformanceTier, role: BlurSurfaceRole): Float {
        if (role == BlurSurfaceRole.SCROLLING_CARD) return 0f
        return when (tier) {
            BlurPerformanceTier.HIGH -> when (role) {
                BlurSurfaceRole.NAVIGATION_TRACK -> 14f
                BlurSurfaceRole.FLOATING_PILL -> 10f
                BlurSurfaceRole.MODAL_SHEET_OR_DIALOG -> 8f
                BlurSurfaceRole.HERO_CARD -> 6f
                BlurSurfaceRole.SCROLLING_CARD -> 0f
            }
            BlurPerformanceTier.MEDIUM -> when (role) {
                BlurSurfaceRole.NAVIGATION_TRACK -> 8f
                BlurSurfaceRole.FLOATING_PILL -> 6f
                BlurSurfaceRole.MODAL_SHEET_OR_DIALOG -> 4f
                BlurSurfaceRole.HERO_CARD -> 0f
                BlurSurfaceRole.SCROLLING_CARD -> 0f
            }
            BlurPerformanceTier.LOW -> 0f
        }
    }
}

/**
 * Immutable snapshot of the current adaptive blur budget and 60fps frame-health telemetry.
 */
@Immutable
data class AdaptiveBlurState(
    val tier: BlurPerformanceTier = BlurPerformanceTier.MEDIUM,
    val profile: PerformanceProfile = PerformanceProfile.BALANCED,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val dynamicScale: Float = 1.0f,
    val isInteractiveMotionActive: Boolean = false,
    val emaFrameTimeMs: Float = AdaptiveBlurPolicy.TARGET_FRAME_TIME_MS,
    val estimatedFps: Int = 60,
    val isFrameBudgetGuarded: Boolean = false
) {
    /** Compose `Modifier.blur` uses GPU RenderEffect starting on Android 12 (API 31). */
    val supportsHardwareBlur: Boolean
        get() = sdkInt >= Build.VERSION_CODES.S

    val thermalScale: Float
        get() = when {
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 0f
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 0.65f
            else -> 1.0f
        }

    val isThermalThrottled: Boolean
        get() = thermalScale < 1.0f

    /**
     * Combined real-time scale factor factoring hardware capability, power saver,
     * thermal headroom, 60fps frame-budget watchdog, and active gesture motion.
     */
    val effectiveScale: Float
        get() {
            if (!supportsHardwareBlur || tier == BlurPerformanceTier.LOW || isPowerSaveMode) {
                return 0f
            }
            val motionFactor = if (isInteractiveMotionActive && tier == BlurPerformanceTier.MEDIUM) {
                0.85f
            } else {
                1.0f
            }
            return (dynamicScale * thermalScale * motionFactor).coerceIn(0f, 1f)
        }

    /**
     * Resolves the quantized blur radius (snapped to 0.5dp increments) for [role].
     * Returns `0.dp` whenever the effective radius drops below [AdaptiveBlurPolicy.MIN_EFFECTIVE_BLUR_DP]
     * so Compose skips attaching an unnecessary RenderNode blur layer.
     */
    fun radiusDpValueFor(role: BlurSurfaceRole): Float {
        val base = AdaptiveBlurPolicy.baseRadiusDp(tier, role)
        if (base <= 0f) return 0f
        val scaled = base * effectiveScale
        if (scaled < AdaptiveBlurPolicy.MIN_EFFECTIVE_BLUR_DP) return 0f
        return (scaled * 2f).roundToInt() / 2f
    }

    fun radiusFor(role: BlurSurfaceRole): Dp = radiusDpValueFor(role).dp

    fun isBlurActiveFor(role: BlurSurfaceRole): Boolean = radiusDpValueFor(role) >= AdaptiveBlurPolicy.MIN_EFFECTIVE_BLUR_DP

    /**
     * Subtle opacity compensation (+0.00f..+0.08f) when blur is unavailable or reduced,
     * keeping frosted glass tracks legible and rich on Android 10–11 and Low/Performance devices.
     */
    fun alphaCompensationFor(role: BlurSurfaceRole): Float {
        val base = AdaptiveBlurPolicy.baseRadiusDp(tier, role)
        if (base <= 0f) {
            return if (role == BlurSurfaceRole.NAVIGATION_TRACK || role == BlurSurfaceRole.FLOATING_PILL) 0.07f else 0f
        }
        val activeRatio = (radiusDpValueFor(role) / base).coerceIn(0f, 1f)
        return (1f - activeRatio) * 0.08f
    }

    val navBlurRadiusDp: Float
        get() = radiusDpValueFor(BlurSurfaceRole.NAVIGATION_TRACK)

    val statusSummary: String
        get() = when {
            tier == BlurPerformanceTier.LOW -> "Low tier · Zero-blur glass · 60fps locked"
            !supportsHardwareBlur -> "${tier.label} tier · Android 10–11 crisp glass fallback"
            isPowerSaveMode -> "${tier.label} tier · Blur paused (Battery Saver)"
            thermalScale == 0f -> "${tier.label} tier · Blur paused (Thermal guard)"
            isFrameBudgetGuarded && navBlurRadiusDp <= 0f -> "${tier.label} tier · 60fps guard (0dp)"
            isFrameBudgetGuarded -> "${tier.label} tier · 60fps guard (${navBlurRadiusDp.formatHalf()}dp)"
            else -> "${tier.label} tier · ${navBlurRadiusDp.formatHalf()}dp nav blur · ${estimatedFps}fps"
        }

    private fun Float.formatHalf(): String {
        return if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
    }
}

/**
 * Efficient, zero-allocation adaptive blur controller that dynamically tunes Liquid Glass
 * blur radii across High / Medium / Low performance tiers and guards the 60fps frame budget
 * using a rolling Choreographer frame-time watchdog with hysteresis.
 */
@Stable
class AdaptiveBlurController(
    initialProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    initialSdkInt: Int = Build.VERSION.SDK_INT,
    initialPowerSaveMode: Boolean = false,
    initialThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
) {
    private val _state = MutableStateFlow(
        AdaptiveBlurState(
            tier = BlurPerformanceTier.fromProfile(initialProfile),
            profile = initialProfile,
            sdkInt = initialSdkInt,
            isPowerSaveMode = initialPowerSaveMode,
            thermalStatus = initialThermalStatus
        )
    )
    val state: StateFlow<AdaptiveBlurState> = _state.asStateFlow()

    private var ladderIndex: Int = 0
    private var windowFrames: Int = 0
    private var windowSlowFrames: Int = 0
    private var consecutiveSmoothWindows: Int = 0
    private var emaFrameTimeMs: Float = AdaptiveBlurPolicy.TARGET_FRAME_TIME_MS

    /**
     * Updates the controller whenever the resolved performance profile, power-save state,
     * or thermal status changes.
     */
    fun updateEnvironment(
        profile: PerformanceProfile,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isPowerSaveMode: Boolean = false,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    ) {
        val nextTier = BlurPerformanceTier.fromProfile(profile)
        val current = _state.value
        val tierChanged = current.tier != nextTier
        if (tierChanged) {
            // Reset dynamic watchdog when user explicitly switches tiers
            ladderIndex = 0
            windowFrames = 0
            windowSlowFrames = 0
            consecutiveSmoothWindows = 0
            emaFrameTimeMs = AdaptiveBlurPolicy.TARGET_FRAME_TIME_MS
        }
        val nextScale = AdaptiveBlurPolicy.DYNAMIC_SCALE_LADDER[ladderIndex]
        val nextState = current.copy(
            tier = nextTier,
            profile = profile,
            sdkInt = sdkInt,
            isPowerSaveMode = isPowerSaveMode,
            thermalStatus = thermalStatus,
            dynamicScale = nextScale,
            emaFrameTimeMs = emaFrameTimeMs,
            estimatedFps = estimateFps(emaFrameTimeMs),
            isFrameBudgetGuarded = ladderIndex > 0
        )
        if (nextState != current) {
            _state.value = nextState
        }
    }

    /**
     * Hints that a continuous interactive gesture (such as dragging the Liquid Glass lens)
     * is currently active so mid-tier devices can keep frame times strictly under 16.67ms.
     */
    fun setInteractiveMotion(active: Boolean) {
        val current = _state.value
        if (current.isInteractiveMotionActive != active) {
            _state.value = current.copy(isInteractiveMotionActive = active)
        }
    }

    /**
     * Feeds a real or simulated frame duration (in milliseconds) into the 60fps watchdog.
     *
     * - Idle pauses (`> 120ms`) and invalid values (`<= 0ms`) are ignored so static screens
     *   never falsely downgrade blur quality.
     * - Evaluates in 24-frame rolling windows with hysteresis so radius never flickers.
     */
    fun recordFrameDurationMs(frameTimeMs: Float) {
        val current = _state.value
        if (!current.supportsHardwareBlur || current.tier == BlurPerformanceTier.LOW || current.isPowerSaveMode) {
            return
        }
        if (frameTimeMs <= 0f || frameTimeMs > 120f) {
            return
        }

        // Smooth EMA (alpha = 0.12) resists single-frame GC spikes while reacting within ~0.4s
        emaFrameTimeMs = emaFrameTimeMs * 0.88f + frameTimeMs * 0.12f
        windowFrames += 1
        if (frameTimeMs > AdaptiveBlurPolicy.SLOW_FRAME_THRESHOLD_MS) {
            windowSlowFrames += 1
        }

        if (windowFrames < AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) {
            return
        }

        val slowRatio = windowSlowFrames.toFloat() / windowFrames.toFloat()
        val isUnderPressure =
            slowRatio >= 0.25f || emaFrameTimeMs > AdaptiveBlurPolicy.EMA_DOWNGRADE_THRESHOLD_MS
        val isComfortablySmooth =
            slowRatio <= 0.05f && emaFrameTimeMs <= AdaptiveBlurPolicy.EMA_RECOVERY_THRESHOLD_MS

        var nextLadderIndex = ladderIndex
        if (isUnderPressure) {
            consecutiveSmoothWindows = 0
            if (nextLadderIndex < AdaptiveBlurPolicy.DYNAMIC_SCALE_LADDER.lastIndex) {
                nextLadderIndex += 1
            }
        } else if (isComfortablySmooth) {
            consecutiveSmoothWindows += 1
            if (consecutiveSmoothWindows >= AdaptiveBlurPolicy.RECOVERY_WINDOWS_REQUIRED && nextLadderIndex > 0) {
                nextLadderIndex -= 1
                consecutiveSmoothWindows = 0
            }
        } else {
            consecutiveSmoothWindows = 0
        }

        windowFrames = 0
        windowSlowFrames = 0
        ladderIndex = nextLadderIndex

        val nextScale = AdaptiveBlurPolicy.DYNAMIC_SCALE_LADDER[ladderIndex]
        val nextFps = estimateFps(emaFrameTimeMs)
        val updated = current.copy(
            dynamicScale = nextScale,
            emaFrameTimeMs = emaFrameTimeMs,
            estimatedFps = nextFps,
            isFrameBudgetGuarded = ladderIndex > 0
        )
        if (updated != current) {
            _state.value = updated
        }
    }

    fun resetFrameWatchdog() {
        ladderIndex = 0
        windowFrames = 0
        windowSlowFrames = 0
        consecutiveSmoothWindows = 0
        emaFrameTimeMs = AdaptiveBlurPolicy.TARGET_FRAME_TIME_MS
        val current = _state.value
        _state.value = current.copy(
            dynamicScale = 1.0f,
            emaFrameTimeMs = AdaptiveBlurPolicy.TARGET_FRAME_TIME_MS,
            estimatedFps = 60,
            isFrameBudgetGuarded = false
        )
    }

    private fun estimateFps(frameMs: Float): Int {
        if (frameMs <= 0f) return 60
        return (1000f / frameMs).roundToInt().coerceIn(15, 120)
    }

    companion object {
        /**
         * Safely reads Android 10+ (API 29+) thermal throttling status without requiring extra permissions.
         */
        fun inspectThermalStatus(context: Context): Int {
            return runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        }
    }
}

val LocalAdaptiveBlurController = staticCompositionLocalOf { AdaptiveBlurController() }
val LocalAdaptiveBlurState = staticCompositionLocalOf { AdaptiveBlurState() }

/**
 * Lifecycle-aware Choreographer frame-budget monitor.
 *
 * Automatically detaches whenever:
 * - The app moves to the background (`!isAppInForeground`)
 * - The device runs Android 10–11 (`!supportsHardwareBlur`)
 * - The active tier is [BlurPerformanceTier.LOW] or Battery Saver is active
 *
 * This ensures zero background CPU wakeups and zero overhead on budget devices.
 */
@Composable
fun AdaptiveBlurFrameMonitor(
    controller: AdaptiveBlurController,
    blurState: AdaptiveBlurState,
    isAppInForeground: Boolean
) {
    val shouldMonitor = isAppInForeground &&
        blurState.supportsHardwareBlur &&
        blurState.tier != BlurPerformanceTier.LOW &&
        !blurState.isPowerSaveMode

    DisposableEffect(controller, shouldMonitor, blurState.thermalStatus) {
        if (!shouldMonitor) {
            return@DisposableEffect onDispose { }
        }
        val choreographer = runCatching { Choreographer.getInstance() }.getOrNull()
            ?: return@DisposableEffect onDispose { }

        // Sample a bounded burst of frames upon foreground entry or thermal shift
        // rather than keeping Choreographer awake continuously at 60–120Hz while idle.
        val maxSampleFrames = AdaptiveBlurPolicy.WINDOW_FRAME_COUNT + 1
        var sampledFrames = 0
        var lastFrameTimeNanos = 0L
        var active = true

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (lastFrameTimeNanos > 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
                    controller.recordFrameDurationMs(deltaMs)
                }
                lastFrameTimeNanos = frameTimeNanos
                sampledFrames++
                if (active && sampledFrames < maxSampleFrames) {
                    choreographer.postFrameCallback(this)
                } else {
                    active = false
                }
            }
        }

        choreographer.postFrameCallback(callback)
        onDispose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }
}

/**
 * Efficient Liquid Glass blur modifier governed by [AdaptiveBlurController].
 *
 * - Smoothly interpolates discrete blur radius adjustments (zero abrupt popping).
 * - Completely omits `Modifier.blur(...)` when radius is `< 1.5.dp` or on Android 10–11,
 *   avoiding unnecessary RenderNode / RenderEffect GPU allocations.
 */
fun Modifier.adaptiveLiquidBlur(
    role: BlurSurfaceRole,
    enabled: Boolean = true,
    explicitRadiusDp: Float? = null
): Modifier = composed {
    val blurState = BrainBufferThemeTokens.blurState
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val targetDp: Dp = when {
        !enabled || !blurState.supportsHardwareBlur -> 0.dp
        explicitRadiusDp != null -> {
            if (explicitRadiusDp < AdaptiveBlurPolicy.MIN_EFFECTIVE_BLUR_DP) 0.dp
            else explicitRadiusDp.dp
        }
        else -> blurState.radiusFor(role)
    }

    val animatedRadius by animateDpAsState(
        targetValue = targetDp,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 160),
        label = "adaptiveLiquidBlurRadius"
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        animatedRadius >= AdaptiveBlurPolicy.MIN_EFFECTIVE_BLUR_DP.dp
    ) {
        this.blur(radius = animatedRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
}
