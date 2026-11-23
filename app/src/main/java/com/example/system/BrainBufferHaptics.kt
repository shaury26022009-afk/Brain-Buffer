package com.example.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Semantic tactile feedback events across BRAIN BUFFER.
 */
enum class HapticEvent {
    /** Energizing double-step rising pulse when starting a focus session. */
    SESSION_START,
    /** Warm celebratory three-beat cadence when a focus session finishes naturally or daily goal is reached. */
    SESSION_COMPLETE,
    /** Calm yet distinct triple-pulse nudge when a distraction notification/intervention triggers. */
    DISTRACTION_ALERT,
    /** Subtle single tactile click for pause, resume, break, and app-switch transitions. */
    PAUSE_OR_RESUME,
    /** Crisp lightweight mechanical tick for steppers, presets, and goal adjustments. */
    STEP_TICK
}

/**
 * Deterministic waveform specification for unit verification and runtime playback.
 * [timingsMs] and [amplitudes] always have identical lengths; amplitudes are strictly in 0..255.
 */
data class HapticWaveformSpec(
    val event: HapticEvent,
    val timingsMs: LongArray,
    val amplitudes: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HapticWaveformSpec) return false
        return event == other.event &&
            timingsMs.contentEquals(other.timingsMs) &&
            amplitudes.contentEquals(other.amplitudes)
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + timingsMs.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }
}

/**
 * Centralized Android haptic feedback controller (API 29–36+).
 *
 * - Uses [VibratorManager] on Android 12+ (API 31+) and [Vibrator] on Android 10–11.
 * - Uses amplitude-aware [VibrationEffect.createWaveform] when hardware supports amplitude control,
 *   falling back cleanly to default-amplitude waveforms on older/basic haptic motors.
 * - Strictly respects the user's [enabled] preference and never crashes on devices without a vibrator.
 */
object BrainBufferHaptics {

    fun waveformFor(event: HapticEvent): HapticWaveformSpec = when (event) {
        HapticEvent.SESSION_START -> HapticWaveformSpec(
            event = event,
            timingsMs = longArrayOf(0L, 18L, 48L, 38L),
            amplitudes = intArrayOf(0, 135, 0, 215)
        )
        HapticEvent.SESSION_COMPLETE -> HapticWaveformSpec(
            event = event,
            timingsMs = longArrayOf(0L, 28L, 65L, 38L, 80L, 70L),
            amplitudes = intArrayOf(0, 155, 0, 200, 0, 255)
        )
        HapticEvent.DISTRACTION_ALERT -> HapticWaveformSpec(
            event = event,
            timingsMs = longArrayOf(0L, 42L, 75L, 55L, 75L, 42L),
            amplitudes = intArrayOf(0, 180, 0, 225, 0, 165)
        )
        HapticEvent.PAUSE_OR_RESUME -> HapticWaveformSpec(
            event = event,
            timingsMs = longArrayOf(0L, 20L),
            amplitudes = intArrayOf(0, 130)
        )
        HapticEvent.STEP_TICK -> HapticWaveformSpec(
            event = event,
            timingsMs = longArrayOf(0L, 10L),
            amplitudes = intArrayOf(0, 85)
        )
    }

    /**
     * Triggers a tactile haptic pattern if [enabled] is true and the device has a vibrator.
     * Returns true when a haptic effect was dispatched, false when suppressed or unavailable.
     */
    fun perform(
        context: Context,
        event: HapticEvent,
        enabled: Boolean = true
    ): Boolean {
        if (!enabled) return false
        return runCatching {
            val vibrator = resolveVibrator(context) ?: return false
            if (!vibrator.hasVibrator()) return false
            val spec = waveformFor(event)
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(spec.timingsMs, spec.amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(spec.timingsMs, -1)
            }
            vibrator.vibrate(effect)
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
