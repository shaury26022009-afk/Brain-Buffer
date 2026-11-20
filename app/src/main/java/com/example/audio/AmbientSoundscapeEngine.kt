package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Built-in, zero-asset procedural focus soundscapes synthesized locally on-device.
 * Keeps APK tiny, works 100% offline, and smoothly masks study distractions.
 */
enum class AmbientSoundscape(
    val label: String,
    val subtitle: String,
    val accentHex: Long
) {
    OFF("Off", "Silent sanctuary", 0xFF8E8E98),
    SOFT_RAIN("Soft Rain", "Gentle steady rainfall", 0xFF60A5FA),
    BROWN_NOISE("Brown Noise", "Warm deep concentration rumble", 0xFFA78BFA),
    PINK_FOCUS("Pink Study", "Balanced acoustic masking", 0xFF34D399),
    NIGHT_BREEZE("Night Breeze", "Slow organic woodland wind", 0xFF2DD4BF),
    DEEP_DRONE("Calm Drone", "Soft harmonic study hum", 0xFFF472B6)
}

@Immutable
data class SoundscapeState(
    val selected: AmbientSoundscape = AmbientSoundscape.OFF,
    val isPlaying: Boolean = false,
    val volume: Float = 0.48f,
    val syncWithTimer: Boolean = true
) {
    val isActive: Boolean
        get() = selected != AmbientSoundscape.OFF && isPlaying
}

/**
 * Pure Kotlin procedural DSP PCM generator for all [AmbientSoundscape] profiles.
 * Operates at 22,050 Hz mono 16-bit PCM with smooth gain ramping so transitions never click.
 */
class AmbientPcmSynthesizer(
    private val sampleRate: Int = SAMPLE_RATE_HZ,
    seed: Int = 42
) {
    private val rng = Random(seed)

    // Filter state (brown / pink / low-pass / LFO phase / raindrop envelope)
    private var brownState = 0.0
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0
    private var lowPassState = 0.0
    private var phasePrimary = 0.0
    private var phaseFifth = 0.0
    private var phaseOctave = 0.0
    private var lfoPhase = 0.0
    private var raindropEnv = 0.0
    private var raindropPhase = 0.0
    private var raindropFreq = 900.0
    private var currentGain = 0.0

    fun reset() {
        brownState = 0.0
        b0 = 0.0; b1 = 0.0; b2 = 0.0; b3 = 0.0; b4 = 0.0; b5 = 0.0; b6 = 0.0
        lowPassState = 0.0
        phasePrimary = 0.0
        phaseFifth = 0.0
        phaseOctave = 0.0
        lfoPhase = 0.0
        raindropEnv = 0.0
        raindropPhase = 0.0
        currentGain = 0.0
    }

    /**
     * Fills [output] with click-free 16-bit signed PCM samples for [soundscape] at [targetVolume].
     */
    fun generateSamples(
        soundscape: AmbientSoundscape,
        targetVolume: Float,
        output: ShortArray
    ) {
        val clampedVol = if (soundscape == AmbientSoundscape.OFF) 0.0 else targetVolume.coerceIn(0f, 1f).toDouble()
        val gainStep = 1.0 / (sampleRate * 0.08) // ~80ms smooth anti-click ramp

        for (i in output.indices) {
            currentGain = when {
                currentGain < clampedVol -> (currentGain + gainStep).coerceAtMost(clampedVol)
                currentGain > clampedVol -> (currentGain - gainStep).coerceAtLeast(clampedVol)
                else -> currentGain
            }

            val rawSample = when (soundscape) {
                AmbientSoundscape.OFF -> 0.0
                AmbientSoundscape.BROWN_NOISE -> nextBrownNoise()
                AmbientSoundscape.PINK_FOCUS -> nextPinkNoise()
                AmbientSoundscape.SOFT_RAIN -> nextSoftRain()
                AmbientSoundscape.NIGHT_BREEZE -> nextNightBreeze()
                AmbientSoundscape.DEEP_DRONE -> nextDeepDrone()
            }

            // Soft limiter keeps output comfortably warm and prevents harsh clipping
            val scaled = (rawSample * currentGain * 0.58).coerceIn(-0.95, 0.95)
            output[i] = (scaled * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun nextWhite(): Double = rng.nextDouble(-1.0, 1.0)

    private fun nextBrownNoise(): Double {
        val white = nextWhite()
        brownState = (brownState + 0.028 * white) / 1.028
        return (brownState * 3.1).coerceIn(-1.0, 1.0)
    }

    private fun nextPinkNoise(): Double {
        val white = nextWhite()
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return (pink * 0.11).coerceIn(-1.0, 1.0)
    }

    private fun nextSoftRain(): Double {
        val pink = nextPinkNoise()
        lowPassState += 0.18 * (pink - lowPassState)
        // Occasional delicate soft droplet plink
        if (raindropEnv < 0.002 && rng.nextDouble() < 0.00045) {
            raindropEnv = 0.14 + rng.nextDouble() * 0.12
            raindropFreq = 680.0 + rng.nextDouble() * 520.0
            raindropPhase = 0.0
        }
        val droplet = if (raindropEnv > 0.001) {
            raindropPhase += 2.0 * PI * raindropFreq / sampleRate
            raindropEnv *= 0.994
            sin(raindropPhase) * raindropEnv
        } else {
            0.0
        }
        return (lowPassState * 0.85 + pink * 0.22 + droplet).coerceIn(-1.0, 1.0)
    }

    private fun nextNightBreeze(): Double {
        lfoPhase += 2.0 * PI * 0.11 / sampleRate
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        val swell = 0.55 + 0.45 * sin(lfoPhase)
        val pink = nextPinkNoise()
        val cutoff = 0.03 + 0.085 * swell
        lowPassState += cutoff * (pink - lowPassState)
        return (lowPassState * (1.2 + 0.5 * swell)).coerceIn(-1.0, 1.0)
    }

    private fun nextDeepDrone(): Double {
        // Warm 144Hz + 216Hz (fifth) + 288Hz (octave) ambient chord with gentle breathing LFO
        phasePrimary += 2.0 * PI * 144.0 / sampleRate
        phaseFifth += 2.0 * PI * 216.0 / sampleRate
        phaseOctave += 2.0 * PI * 288.0 / sampleRate
        lfoPhase += 2.0 * PI * 0.075 / sampleRate
        if (phasePrimary > 2.0 * PI) phasePrimary -= 2.0 * PI
        if (phaseFifth > 2.0 * PI) phaseFifth -= 2.0 * PI
        if (phaseOctave > 2.0 * PI) phaseOctave -= 2.0 * PI
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

        val breath = 0.78 + 0.22 * sin(lfoPhase)
        val harmonic = sin(phasePrimary) * 0.52 + sin(phaseFifth) * 0.26 + sin(phaseOctave) * 0.14
        val air = nextBrownNoise() * 0.10
        return (harmonic * breath + air).coerceIn(-1.0, 1.0)
    }

    companion object {
        const val SAMPLE_RATE_HZ = 22_050
    }
}

/**
 * Lifecycle-safe ambient soundscape playback controller backed by Android [AudioTrack].
 * Degrades cleanly to silent state-only mode on headless test runners where audio hardware is absent.
 */
@Stable
class AmbientSoundscapeController(
    private val enableHardwareAudio: Boolean = true
) {
    private val _state = MutableStateFlow(SoundscapeState())
    val state: StateFlow<SoundscapeState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    @Volatile
    private var workerThread: Thread? = null

    fun selectSoundscape(soundscape: AmbientSoundscape, autoPlay: Boolean = true) {
        if (soundscape == AmbientSoundscape.OFF) {
            stopPlaybackInternal(clearSelection = true)
            return
        }
        val shouldPlay = autoPlay
        _state.value = _state.value.copy(selected = soundscape, isPlaying = shouldPlay)
        if (shouldPlay) {
            ensureWorkerRunning()
        } else {
            stopWorkerThread()
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.selected == AmbientSoundscape.OFF) {
            selectSoundscape(AmbientSoundscape.SOFT_RAIN, autoPlay = true)
            return
        }
        val nextPlaying = !current.isPlaying
        _state.value = current.copy(isPlaying = nextPlaying)
        if (nextPlaying) {
            ensureWorkerRunning()
        } else {
            stopWorkerThread()
        }
    }

    fun setVolume(volume: Float) {
        _state.value = _state.value.copy(volume = volume.coerceIn(0.05f, 1.0f))
    }

    fun setSyncWithTimer(sync: Boolean) {
        _state.value = _state.value.copy(syncWithTimer = sync)
    }

    /**
     * Synchronizes ambient playback with the Focus Timer state when [SoundscapeState.syncWithTimer] is enabled.
     */
    fun onTimerRunningChanged(isTimerRunning: Boolean) {
        val current = _state.value
        if (!current.syncWithTimer || current.selected == AmbientSoundscape.OFF) return
        if (isTimerRunning && !current.isPlaying) {
            _state.value = current.copy(isPlaying = true)
            ensureWorkerRunning()
        } else if (!isTimerRunning && current.isPlaying) {
            _state.value = current.copy(isPlaying = false)
            stopWorkerThread()
        }
    }

    fun release() {
        stopPlaybackInternal(clearSelection = false)
    }

    private fun stopPlaybackInternal(clearSelection: Boolean) {
        _state.value = _state.value.copy(
            selected = if (clearSelection) AmbientSoundscape.OFF else _state.value.selected,
            isPlaying = false
        )
        stopWorkerThread()
    }

    @Synchronized
    private fun ensureWorkerRunning() {
        if (!enableHardwareAudio) return
        if (running.get() && workerThread?.isAlive == true) return
        running.set(true)
        val thread = Thread({
            runAudioLoop()
        }, "BrainBuffer-AmbientAudio").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
        workerThread = thread
        thread.start()
    }

    @Synchronized
    private fun stopWorkerThread() {
        running.set(false)
        workerThread = null
    }

    private fun runAudioLoop() {
        val sampleRate = AmbientPcmSynthesizer.SAMPLE_RATE_HZ
        val minBufBytes = runCatching {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        }.getOrDefault(0)
        if (minBufBytes <= 0) {
            running.set(false)
            return
        }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes((minBufBytes * 2).coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()

        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track?.release() }
            running.set(false)
            return
        }

        val synth = AmbientPcmSynthesizer(sampleRate = sampleRate)
        val chunk = ShortArray(1024)
        try {
            track.play()
            while (running.get()) {
                val snapshot = _state.value
                if (!snapshot.isPlaying || snapshot.selected == AmbientSoundscape.OFF) {
                    break
                }
                synth.generateSamples(snapshot.selected, snapshot.volume, chunk)
                val written = track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
            // Short fade-out chunk for clean silence
            synth.generateSamples(AmbientSoundscape.OFF, 0f, chunk)
            runCatching { track.write(chunk, 0, chunk.size) }
        } catch (_: Throwable) {
            // Audio device unavailable or interrupted; fail silently without disrupting study flow
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.release()
            }
            running.set(false)
        }
    }
}

val LocalAmbientSoundscapeController = staticCompositionLocalOf { AmbientSoundscapeController() }
