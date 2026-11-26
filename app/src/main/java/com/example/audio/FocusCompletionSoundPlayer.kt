package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.RawRes
import com.example.R

/**
 * Abstraction over Android's [MediaPlayer] playback lifecycle so [FocusCompletionSoundCoordinator]
 * can be deterministically unit-tested on the JVM while production code uses the real
 * [AndroidMediaPlayerCompletionBackend].
 */
interface CompletionSoundBackend {
    fun play(
        @RawRes rawResId: Int,
        volume: Float,
        onFinished: () -> Unit
    ): Boolean

    fun stopAndRelease()
}

/**
 * Production [MediaPlayer]-backed audio engine for BRAIN BUFFER's calm session-completion chime.
 *
 * Audio & lifecycle guarantees:
 * - Loads the bundled raw WAV chime (`R.raw.focus_complete_chime`) via [MediaPlayer.setDataSource]
 *   and prepares asynchronously ([MediaPlayer.prepareAsync]) so the main thread never blocks.
 * - Requests transient ducking audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) so any
 *   playing music or ambient soundscape dips gently instead of cutting out abruptly.
 * - Applies a calm, subtle default volume so the alert never startles the user.
 * - Automatically releases the [MediaPlayer] and abandons audio focus on playback completion,
 *   playback error, or explicit [stopAndRelease].
 */
class AndroidMediaPlayerCompletionBackend(
    context: Context
) : CompletionSoundBackend {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var activePlayer: MediaPlayer? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun play(
        @RawRes rawResId: Int,
        volume: Float,
        onFinished: () -> Unit
    ): Boolean {
        val clampedVolume = volume.coerceIn(0f, 1f)
        synchronized(lock) {
            releaseLocked()
            val player = MediaPlayer()
            return try {
                val opened = appContext.resources.openRawResourceFd(rawResId)?.use { afd ->
                    player.setAudioAttributes(audioAttributes)
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    true
                } ?: false

                if (!opened) {
                    runCatching { player.release() }
                    false
                } else {
                    player.isLooping = false
                    player.setVolume(clampedVolume, clampedVolume)
                    player.setOnPreparedListener { prepared ->
                        synchronized(lock) {
                            if (activePlayer !== prepared) {
                                runCatching { prepared.release() }
                                return@setOnPreparedListener
                            }
                            requestTransientDuckFocusLocked()
                            runCatching { prepared.start() }.onFailure {
                                releaseLocked()
                                onFinished()
                            }
                        }
                    }
                    player.setOnCompletionListener { completed ->
                        synchronized(lock) {
                            if (activePlayer === completed) {
                                releaseLocked()
                            } else {
                                runCatching { completed.release() }
                            }
                        }
                        onFinished()
                    }
                    player.setOnErrorListener { errored, _, _ ->
                        synchronized(lock) {
                            if (activePlayer === errored) {
                                releaseLocked()
                            } else {
                                runCatching { errored.release() }
                            }
                        }
                        onFinished()
                        true
                    }
                    activePlayer = player
                    player.prepareAsync()
                    true
                }
            } catch (_: Exception) {
                runCatching { player.release() }
                releaseLocked()
                false
            }
        }
    }

    override fun stopAndRelease() {
        synchronized(lock) {
            releaseLocked()
        }
    }

    private fun requestTransientDuckFocusLocked() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(false)
                .build()
            activeFocusRequest = request
            runCatching { audioManager.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonAudioFocusLocked() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { audioManager.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(null) }
        }
    }

    private fun releaseLocked() {
        activePlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        activePlayer = null
        abandonAudioFocusLocked()
    }
}

/**
 * Thread-safe coordinator that ensures the calm focus-completion sound plays:
 * - Only when sound alerts are enabled.
 * - Strictly once per completed `sessionId` (preventing double playback when both
 *   `FocusTimerService` and `BrainBufferViewModel` observe natural completion).
 * - With bounded session-history tracking and explicit preview support.
 */
class FocusCompletionSoundCoordinator(
    private val backend: CompletionSoundBackend,
    @RawRes private val chimeResId: Int = R.raw.focus_complete_chime,
    private val defaultVolume: Float = DEFAULT_CALM_VOLUME
) {
    private val lock = Any()
    private val playedSessionIds = LinkedHashSet<String>()

    fun playForSessionCompletion(
        sessionId: String?,
        enabled: Boolean,
        volume: Float = defaultVolume
    ): Boolean {
        if (!enabled) return false
        val normalizedId = sessionId?.trim().orEmpty()
        synchronized(lock) {
            if (normalizedId.isNotEmpty() && !playedSessionIds.add(normalizedId)) {
                return false
            }
            if (playedSessionIds.size > MAX_TRACKED_SESSION_IDS) {
                val oldest = playedSessionIds.firstOrNull()
                if (oldest != null) {
                    playedSessionIds.remove(oldest)
                }
            }
        }
        val started = backend.play(
            rawResId = chimeResId,
            volume = volume.coerceIn(0f, 1f),
            onFinished = {}
        )
        if (!started && normalizedId.isNotEmpty()) {
            synchronized(lock) {
                playedSessionIds.remove(normalizedId)
            }
        }
        return started
    }

    fun playPreview(volume: Float = defaultVolume): Boolean {
        return backend.play(
            rawResId = chimeResId,
            volume = volume.coerceIn(0f, 1f),
            onFinished = {}
        )
    }

    fun release() {
        backend.stopAndRelease()
    }

    fun hasPlayedForSession(sessionId: String): Boolean = synchronized(lock) {
        playedSessionIds.contains(sessionId.trim())
    }

    companion object {
        const val DEFAULT_CALM_VOLUME: Float = 0.55f
        private const val MAX_TRACKED_SESSION_IDS = 32
    }
}

/**
 * Application-scoped entry point for playing BRAIN BUFFER's calm completion chime
 * via Android's [MediaPlayer] API across ViewModel, Service, and Settings previews.
 */
object FocusCompletionSoundPlayer {

    private val instanceLock = Any()

    @Volatile
    private var coordinator: FocusCompletionSoundCoordinator? = null

    private fun getOrCreate(context: Context): FocusCompletionSoundCoordinator {
        return coordinator ?: synchronized(instanceLock) {
            coordinator ?: FocusCompletionSoundCoordinator(
                backend = AndroidMediaPlayerCompletionBackend(context.applicationContext)
            ).also { coordinator = it }
        }
    }

    /**
     * Triggers the calm completion chime once per completed [sessionId] when [enabled] is true.
     */
    fun playCompletionChime(
        context: Context,
        sessionId: String?,
        enabled: Boolean,
        volume: Float = FocusCompletionSoundCoordinator.DEFAULT_CALM_VOLUME
    ): Boolean {
        if (!enabled) return false
        return getOrCreate(context).playForSessionCompletion(
            sessionId = sessionId,
            enabled = true,
            volume = volume
        )
    }

    /**
     * Plays a single subtle preview of the completion chime (e.g., when enabling alert sounds in Settings).
     */
    fun playPreview(
        context: Context,
        volume: Float = FocusCompletionSoundCoordinator.DEFAULT_CALM_VOLUME
    ): Boolean {
        return getOrCreate(context).playPreview(volume = volume)
    }

    /**
     * Immediately stops any active playback and releases native [MediaPlayer] resources.
     */
    fun release() {
        coordinator?.release()
    }
}
