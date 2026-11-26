package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Clearly separated categories for the BRAIN BUFFER Focus Soundscape library.
 */
enum class SoundscapeCategory(
    val title: String,
    val subtitle: String
) {
    NATURE_ATMOSPHERE(
        title = "Nature & Atmosphere",
        subtitle = "Natural surroundings & calibrated distraction masking"
    ),
    FANTASY_INSTRUMENTAL(
        title = "Fantasy & Instrumental",
        subtitle = "Original vocal-free atmospheric & motivating study textures"
    )
}

/**
 * 100% offline synthesized Focus Soundscape library.
 * Every soundscape owns a completely isolated procedural DSP voice so each selection
 * produces an unmistakable, rich, normalized listening experience.
 */
enum class AmbientSoundscape(
    val label: String,
    val subtitle: String,
    val accentHex: Long,
    val category: SoundscapeCategory?
) {
    OFF(
        label = "Off",
        subtitle = "Silent focus",
        accentHex = 0xFF8E8E98,
        category = null
    ),

    // Nature & Atmosphere (9)
    WIND_CHIMES(
        label = "Wind Chimes",
        subtitle = "Delicate chimes in a gentle breeze",
        accentHex = 0xFF38BDF8,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    PASTORAL(
        label = "Pastoral",
        subtitle = "Countryside meadow & soft songbirds",
        accentHex = 0xFF84CC16,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    BEACH(
        label = "Beach",
        subtitle = "Rolling surf waves & warm sea breeze",
        accentHex = 0xFF0EA5E9,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    SUMMER_NIGHT(
        label = "Summer Night",
        subtitle = "Rhythmic crickets & cool evening air",
        accentHex = 0xFF818CF8,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    SOFT_RAIN(
        label = "Rain",
        subtitle = "Crisp steady rainfall & soft droplets",
        accentHex = 0xFF60A5FA,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    FOREST(
        label = "Forest",
        subtitle = "Rustling woodland pines & distant calls",
        accentHex = 0xFF22C55E,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    BROWN_NOISE(
        label = "Brown Noise",
        subtitle = "Deep, velvety low-frequency rumble",
        accentHex = 0xFFA78BFA,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    PINK_FOCUS(
        label = "Pink Noise",
        subtitle = "Softer, balanced acoustic masking",
        accentHex = 0xFF34D399,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),
    WHITE_NOISE(
        label = "White Noise",
        subtitle = "Consistent bright distraction masking",
        accentHex = 0xFFCBD5E1,
        category = SoundscapeCategory.NATURE_ATMOSPHERE
    ),

    // Fantasy & Instrumental (3)
    PEACEFUL_FANTASY(
        label = "Peaceful Fantasy",
        subtitle = "Original open-world strings, harp & airy pads",
        accentHex = 0xFFC084FC,
        category = SoundscapeCategory.FANTASY_INSTRUMENTAL
    ),
    ENERGETIC_FOCUS(
        label = "Energetic Focus",
        subtitle = "Original motivating marimba pulse & study groove",
        accentHex = 0xFFF59E0B,
        category = SoundscapeCategory.FANTASY_INSTRUMENTAL
    ),
    DEEP_FOCUS(
        label = "Deep Focus",
        subtitle = "Minimal vocal-free piano swells & warm bass",
        accentHex = 0xFFF472B6,
        category = SoundscapeCategory.FANTASY_INSTRUMENTAL
    );

    companion object {
        fun byCategory(category: SoundscapeCategory): List<AmbientSoundscape> =
            entries.filter { it.category == category }

        /**
         * Safely resolves a persisted enum key, transparently migrating legacy sound names.
         */
        fun fromStorageKey(key: String?): AmbientSoundscape {
            if (key.isNullOrBlank()) return OFF
            return when (key.trim().uppercase()) {
                "NIGHT_BREEZE" -> FOREST
                "DEEP_DRONE" -> DEEP_FOCUS
                else -> entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) } ?: OFF
            }
        }
    }
}

@Immutable
data class SoundscapeState(
    val selected: AmbientSoundscape = AmbientSoundscape.OFF,
    val isPlaying: Boolean = false,
    val volume: Float = DEFAULT_VOLUME,
    val syncWithTimer: Boolean = true,
    val previewing: AmbientSoundscape? = null,
    val isDucked: Boolean = false
) {
    val effectiveSoundscape: AmbientSoundscape
        get() = previewing ?: selected

    val isActive: Boolean
        get() = effectiveSoundscape != AmbientSoundscape.OFF && isPlaying

    val isPreviewing: Boolean
        get() = previewing != null && isPlaying

    companion object {
        const val DEFAULT_VOLUME: Float = 0.72f
    }
}

/**
 * Studio-calibrated procedural PCM synthesizer for all [AmbientSoundscape] profiles.
 *
 * Audio quality guarantees:
 * - Isolated per-soundscape voice bank (zero cross-contamination between sounds).
 * - Equal-power 280 ms cosine/sine crossfading when switching sounds (zero clicks or pops).
 * - Perceptual volume scaling + smooth warm `tanh` soft-limiter for full, clear loudness
 *   without clipping or unpleasant peaks.
 * - Smooth transient ducking gain support for Android AudioFocus cooperation.
 */
class AmbientPcmSynthesizer(
    private val sampleRate: Int = SAMPLE_RATE_HZ,
    seed: Int = 42
) {
    private val voices: Map<AmbientSoundscape, SoundscapeVoice> = mapOf(
        AmbientSoundscape.WIND_CHIMES to WindChimesVoice(seed + 11),
        AmbientSoundscape.PASTORAL to PastoralVoice(seed + 23),
        AmbientSoundscape.BEACH to BeachVoice(seed + 37),
        AmbientSoundscape.SUMMER_NIGHT to SummerNightVoice(seed + 53),
        AmbientSoundscape.SOFT_RAIN to RainVoice(seed + 71),
        AmbientSoundscape.FOREST to ForestVoice(seed + 89),
        AmbientSoundscape.BROWN_NOISE to BrownNoiseVoice(seed + 107),
        AmbientSoundscape.PINK_FOCUS to PinkNoiseVoice(seed + 127),
        AmbientSoundscape.WHITE_NOISE to WhiteNoiseVoice(seed + 149),
        AmbientSoundscape.PEACEFUL_FANTASY to PeacefulFantasyVoice(seed + 173),
        AmbientSoundscape.ENERGETIC_FOCUS to EnergeticFocusVoice(seed + 197),
        AmbientSoundscape.DEEP_FOCUS to DeepFocusVoice()
    )

    private var activeSoundscape: AmbientSoundscape = AmbientSoundscape.OFF
    private var previousSoundscape: AmbientSoundscape = AmbientSoundscape.OFF
    private var crossfadeRemainingSamples: Int = 0
    private var crossfadeTotalSamples: Int = (sampleRate * 0.28).toInt().coerceAtLeast(1)
    private var currentGain: Double = 0.0
    private var currentDuckGain: Double = 1.0

    fun reset() {
        voices.values.forEach { it.reset() }
        activeSoundscape = AmbientSoundscape.OFF
        previousSoundscape = AmbientSoundscape.OFF
        crossfadeRemainingSamples = 0
        currentGain = 0.0
        currentDuckGain = 1.0
    }

    /**
     * Fills [output] with normalized, click-free 16-bit signed PCM samples for [soundscape]
     * at [targetVolume], automatically crossfading whenever [soundscape] changes.
     */
    fun generateSamples(
        soundscape: AmbientSoundscape,
        targetVolume: Float,
        output: ShortArray,
        ducked: Boolean = false
    ) {
        if (soundscape != activeSoundscape) {
            previousSoundscape = activeSoundscape
            activeSoundscape = soundscape
            crossfadeTotalSamples = (sampleRate * 0.28).toInt().coerceAtLeast(1)
            crossfadeRemainingSamples = if (previousSoundscape == AmbientSoundscape.OFF) {
                0
            } else {
                crossfadeTotalSamples
            }
        }

        val sr = sampleRate.toDouble()
        val targetLinear = if (soundscape == AmbientSoundscape.OFF && crossfadeRemainingSamples == 0) {
            0.0
        } else {
            val v = targetVolume.coerceIn(0f, 1f).toDouble()
            // Perceptual loudness taper: strong, full clarity across the usable slider range
            v.pow(1.22)
        }
        val targetDuck = if (ducked) 0.25 else 1.0
        val gainStep = 1.0 / (sr * 0.065) // ~65ms click-free volume ramp
        val duckStep = 1.0 / (sr * 0.060)

        for (i in output.indices) {
            currentGain = when {
                currentGain < targetLinear -> (currentGain + gainStep).coerceAtMost(targetLinear)
                currentGain > targetLinear -> (currentGain - gainStep).coerceAtLeast(targetLinear)
                else -> currentGain
            }
            currentDuckGain = when {
                currentDuckGain < targetDuck -> (currentDuckGain + duckStep).coerceAtMost(targetDuck)
                currentDuckGain > targetDuck -> (currentDuckGain - duckStep).coerceAtLeast(targetDuck)
                else -> currentDuckGain
            }

            val rawSample = if (crossfadeRemainingSamples > 0) {
                val progress = 1.0 - (crossfadeRemainingSamples.toDouble() / crossfadeTotalSamples.toDouble())
                crossfadeRemainingSamples--
                val fadeOut = cos(progress * (PI / 2.0))
                val fadeIn = sin(progress * (PI / 2.0))
                val oldVal = sampleFor(previousSoundscape, sr)
                val newVal = sampleFor(activeSoundscape, sr)
                oldVal * fadeOut + newVal * fadeIn
            } else {
                sampleFor(activeSoundscape, sr)
            }

            // Warm soft-clipping limiter improves perceived loudness cleanly with zero harsh digital clipping
            val gained = rawSample * currentGain * currentDuckGain * MASTER_MAKEUP_GAIN
            val softLimited = tanh(gained * 1.18) * 0.94
            output[i] = (softLimited * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun sampleFor(soundscape: AmbientSoundscape, sampleRate: Double): Double {
        if (soundscape == AmbientSoundscape.OFF) return 0.0
        return voices[soundscape]?.nextSample(sampleRate) ?: 0.0
    }

    companion object {
        const val SAMPLE_RATE_HZ = 22_050
        private const val MASTER_MAKEUP_GAIN = 1.24
    }
}

/**
 * Lifecycle-safe, AudioFocus-aware ambient soundscape playback controller backed by Android [AudioTrack].
 *
 * Guarantees:
 * - At most ONE active audio worker thread (strictly prevents overlapping playback).
 * - Smooth in-place crossfading when switching sounds without tearing down the [AudioTrack].
 * - Full Android [AudioManager] audio-focus handling (pauses for other apps, ducks smoothly on transient alerts).
 * - Supports independent sound previews without losing the user's selected session sound.
 * - Persists user selections via [onPreferencesChanged] and hydrates cleanly on startup.
 */
@Stable
class AmbientSoundscapeController(
    context: Context? = null,
    private val enableHardwareAudio: Boolean = true,
    private val onPreferencesChanged: ((selected: AmbientSoundscape, volume: Float, syncWithTimer: Boolean) -> Unit)? = null
) {
    private val appContext: Context? = context?.applicationContext
    private val _state = MutableStateFlow(SoundscapeState())
    val state: StateFlow<SoundscapeState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    private val workerGeneration = AtomicLong(0L)
    private val stateLock = Any()

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var pausedByTransientFocusLoss: Boolean = false

    @Volatile
    private var hasHydratedPreferences: Boolean = false

    @Volatile
    private var wasPlayingBeforePreview: Boolean = false

    private var activeFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByTransientFocusLoss = false
                synchronized(stateLock) {
                    _state.value = _state.value.copy(isPlaying = false, previewing = null, isDucked = false)
                    stopWorkerThreadLocked()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                synchronized(stateLock) {
                    if (_state.value.isPlaying) {
                        pausedByTransientFocusLoss = true
                        _state.value = _state.value.copy(isPlaying = false, isDucked = false)
                        stopWorkerThreadLocked()
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(stateLock) {
                    _state.value = _state.value.copy(isDucked = true)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                synchronized(stateLock) {
                    val shouldResume = pausedByTransientFocusLoss &&
                        _state.value.effectiveSoundscape != AmbientSoundscape.OFF
                    pausedByTransientFocusLoss = false
                    if (shouldResume) {
                        _state.value = _state.value.copy(isPlaying = true, isDucked = false)
                        ensureWorkerRunningLocked()
                    } else {
                        _state.value = _state.value.copy(isDucked = false)
                    }
                }
            }
        }
    }

    /**
     * Restores persisted soundscape preferences on startup without interrupting active playback.
     */
    fun hydratePreferences(
        selected: AmbientSoundscape,
        volume: Float,
        syncWithTimer: Boolean
    ) {
        synchronized(stateLock) {
            if (hasHydratedPreferences) return
            hasHydratedPreferences = true
            val current = _state.value
            val clampedVol = volume.coerceIn(0.05f, 1.0f)
            if (current.isPlaying || current.previewing != null) {
                return
            }
            _state.value = current.copy(
                selected = selected,
                volume = clampedVol,
                syncWithTimer = syncWithTimer
            )
        }
    }

    fun selectSoundscape(soundscape: AmbientSoundscape, autoPlay: Boolean = true) {
        val toPersist: SoundscapeState
        synchronized(stateLock) {
            pausedByTransientFocusLoss = false
            wasPlayingBeforePreview = false
            if (soundscape == AmbientSoundscape.OFF) {
                _state.value = _state.value.copy(
                    selected = AmbientSoundscape.OFF,
                    isPlaying = false,
                    previewing = null,
                    isDucked = false
                )
                stopWorkerThreadLocked()
                toPersist = _state.value
            } else {
                val nextPlaying = autoPlay
                _state.value = _state.value.copy(
                    selected = soundscape,
                    isPlaying = nextPlaying,
                    previewing = null,
                    isDucked = false
                )
                if (nextPlaying) {
                    ensureWorkerRunningLocked()
                } else {
                    stopWorkerThreadLocked()
                }
                toPersist = _state.value
            }
        }
        persist(toPersist)
    }

    /**
     * Auditions [soundscape] immediately without altering the user's saved [SoundscapeState.selected] choice.
     * Tapping preview on the currently previewing sound stops the preview cleanly and resumes
     * the previously playing selected sound if one was active before auditioning.
     */
    fun togglePreview(soundscape: AmbientSoundscape) {
        if (soundscape == AmbientSoundscape.OFF) {
            stopPreview()
            return
        }
        synchronized(stateLock) {
            pausedByTransientFocusLoss = false
            val current = _state.value
            if (current.previewing == soundscape && current.isPlaying) {
                val resumeSelected = wasPlayingBeforePreview && current.selected != AmbientSoundscape.OFF
                wasPlayingBeforePreview = false
                _state.value = current.copy(
                    previewing = null,
                    isPlaying = resumeSelected,
                    isDucked = false
                )
                if (resumeSelected) {
                    ensureWorkerRunningLocked()
                } else {
                    stopWorkerThreadLocked()
                }
            } else {
                if (current.previewing == null) {
                    wasPlayingBeforePreview = current.isPlaying && current.selected != AmbientSoundscape.OFF
                }
                _state.value = current.copy(
                    previewing = soundscape,
                    isPlaying = true,
                    isDucked = false
                )
                ensureWorkerRunningLocked()
            }
        }
    }

    fun stopPreview() {
        synchronized(stateLock) {
            val current = _state.value
            if (current.previewing == null) return
            val resumeSelected = wasPlayingBeforePreview && current.selected != AmbientSoundscape.OFF
            wasPlayingBeforePreview = false
            _state.value = current.copy(
                previewing = null,
                isPlaying = resumeSelected,
                isDucked = false
            )
            if (resumeSelected) {
                ensureWorkerRunningLocked()
            } else {
                stopWorkerThreadLocked()
            }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.selected == AmbientSoundscape.OFF && current.previewing == null) {
            selectSoundscape(AmbientSoundscape.SOFT_RAIN, autoPlay = true)
            return
        }
        synchronized(stateLock) {
            pausedByTransientFocusLoss = false
            val snapshot = _state.value
            val nextPlaying = !snapshot.isPlaying
            _state.value = snapshot.copy(
                isPlaying = nextPlaying,
                previewing = if (!nextPlaying) null else snapshot.previewing,
                isDucked = false
            )
            if (nextPlaying) {
                ensureWorkerRunningLocked()
            } else {
                stopWorkerThreadLocked()
            }
        }
    }

    fun setVolume(volume: Float) {
        val toPersist: SoundscapeState
        synchronized(stateLock) {
            _state.value = _state.value.copy(volume = volume.coerceIn(0.05f, 1.0f))
            toPersist = _state.value
        }
        persist(toPersist)
    }

    fun setSyncWithTimer(sync: Boolean) {
        val toPersist: SoundscapeState
        synchronized(stateLock) {
            _state.value = _state.value.copy(syncWithTimer = sync)
            toPersist = _state.value
        }
        persist(toPersist)
    }

    /**
     * Synchronizes ambient playback with the Focus Timer state when [SoundscapeState.syncWithTimer] is enabled.
     */
    fun onTimerRunningChanged(isTimerRunning: Boolean) {
        synchronized(stateLock) {
            val current = _state.value
            if (!current.syncWithTimer || current.selected == AmbientSoundscape.OFF) return
            // Don't abruptly kill an explicit user sound preview while idle; start/pause selected sound cleanly.
            if (current.previewing != null && !isTimerRunning) return
            if (isTimerRunning && !current.isPlaying) {
                pausedByTransientFocusLoss = false
                _state.value = current.copy(isPlaying = true, previewing = null, isDucked = false)
                ensureWorkerRunningLocked()
            } else if (!isTimerRunning && current.isPlaying && current.previewing == null) {
                pausedByTransientFocusLoss = false
                _state.value = current.copy(isPlaying = false, isDucked = false)
                stopWorkerThreadLocked()
            }
        }
    }

    /**
     * Called when a focus session finishes or resets so ambient audio never keeps looping unexpectedly.
     */
    fun onSessionEnded() {
        synchronized(stateLock) {
            val current = _state.value
            if (current.syncWithTimer && current.isPlaying) {
                pausedByTransientFocusLoss = false
                _state.value = current.copy(isPlaying = false, previewing = null, isDucked = false)
                stopWorkerThreadLocked()
            }
        }
    }

    fun release() {
        synchronized(stateLock) {
            pausedByTransientFocusLoss = false
            _state.value = _state.value.copy(isPlaying = false, previewing = null, isDucked = false)
            stopWorkerThreadLocked()
        }
    }

    private fun persist(state: SoundscapeState) {
        onPreferencesChanged?.invoke(state.selected, state.volume, state.syncWithTimer)
    }

    private fun requestAudioFocus(): Boolean {
        val ctx = appContext ?: return true
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            activeFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(audioFocusChangeListener) }
        }
    }

    private fun ensureWorkerRunningLocked() {
        if (!enableHardwareAudio) return
        if (running.get() && workerThread?.isAlive == true) {
            // Existing audio thread will crossfade smoothly to the new effectiveSoundscape on the very next frame!
            return
        }
        val gen = workerGeneration.incrementAndGet()
        running.set(true)
        val thread = Thread({
            runAudioLoop(gen)
        }, "BrainBuffer-AmbientAudio-$gen").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
        workerThread = thread
        thread.start()
    }

    private fun stopWorkerThreadLocked() {
        workerGeneration.incrementAndGet()
        running.set(false)
        workerThread = null
        abandonAudioFocus()
    }

    private fun runAudioLoop(generation: Long) {
        if (!requestAudioFocus()) {
            synchronized(stateLock) {
                if (workerGeneration.get() == generation) {
                    _state.value = _state.value.copy(isPlaying = false, previewing = null)
                    running.set(false)
                }
            }
            return
        }

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
            abandonAudioFocus()
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
            abandonAudioFocus()
            return
        }

        val synth = AmbientPcmSynthesizer(sampleRate = sampleRate)
        val chunk = ShortArray(1024)
        try {
            track.play()
            while (running.get() && workerGeneration.get() == generation) {
                val snapshot = _state.value
                val targetSound = snapshot.effectiveSoundscape
                if (!snapshot.isPlaying || targetSound == AmbientSoundscape.OFF) {
                    break
                }
                synth.generateSamples(
                    soundscape = targetSound,
                    targetVolume = snapshot.volume,
                    output = chunk,
                    ducked = snapshot.isDucked
                )
                val written = track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
            // Click-free fade-out tail before releasing hardware track
            synth.generateSamples(AmbientSoundscape.OFF, 0f, chunk, ducked = false)
            runCatching { track.write(chunk, 0, chunk.size) }
        } catch (_: Throwable) {
            // Audio device unavailable or interrupted; degrade safely without disrupting focus flow
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.release()
            }
            if (workerGeneration.get() == generation) {
                running.set(false)
                abandonAudioFocus()
            }
        }
    }
}

val LocalAmbientSoundscapeController = staticCompositionLocalOf { AmbientSoundscapeController() }
