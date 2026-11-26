package com.example

import com.example.audio.CompletionSoundBackend
import com.example.audio.FocusCompletionSoundCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusCompletionSoundPlayerTest {

    private class FakeCompletionSoundBackend(
        var shouldSucceed: Boolean = true
    ) : CompletionSoundBackend {
        val playedResIds = mutableListOf<Int>()
        val playedVolumes = mutableListOf<Float>()
        var releaseCount = 0
        var lastOnFinished: (() -> Unit)? = null

        override fun play(rawResId: Int, volume: Float, onFinished: () -> Unit): Boolean {
            playedResIds.add(rawResId)
            playedVolumes.add(volume)
            lastOnFinished = onFinished
            return shouldSucceed
        }

        override fun stopAndRelease() {
            releaseCount++
        }
    }

    @Test
    fun `disabled sound setting never triggers MediaPlayer backend`() {
        val backend = FakeCompletionSoundBackend()
        val coordinator = FocusCompletionSoundCoordinator(backend = backend, chimeResId = 101)

        val played = coordinator.playForSessionCompletion(
            sessionId = "session-1",
            enabled = false
        )

        assertFalse(played)
        assertTrue(backend.playedResIds.isEmpty())
        assertFalse(coordinator.hasPlayedForSession("session-1"))
    }

    @Test
    fun `same session completion plays calm chime strictly once across concurrent callers`() {
        val backend = FakeCompletionSoundBackend()
        val coordinator = FocusCompletionSoundCoordinator(backend = backend, chimeResId = 101)

        assertTrue(coordinator.playForSessionCompletion(sessionId = "session-abc", enabled = true))
        assertFalse(coordinator.playForSessionCompletion(sessionId = "session-abc", enabled = true))
        assertFalse(coordinator.playForSessionCompletion(sessionId = " session-abc ", enabled = true))

        assertEquals(1, backend.playedResIds.size)
        assertEquals(101, backend.playedResIds.first())
        assertEquals(FocusCompletionSoundCoordinator.DEFAULT_CALM_VOLUME, backend.playedVolumes.first(), 0.0001f)
        assertTrue(coordinator.hasPlayedForSession("session-abc"))
    }

    @Test
    fun `distinct completed sessions each trigger playback and volume is clamped safely`() {
        val backend = FakeCompletionSoundBackend()
        val coordinator = FocusCompletionSoundCoordinator(backend = backend, chimeResId = 202)

        assertTrue(coordinator.playForSessionCompletion("s-1", enabled = true, volume = 1.8f))
        assertTrue(coordinator.playForSessionCompletion("s-2", enabled = true, volume = -0.3f))

        assertEquals(2, backend.playedResIds.size)
        assertEquals(1.0f, backend.playedVolumes[0], 0.0001f)
        assertEquals(0.0f, backend.playedVolumes[1], 0.0001f)
    }

    @Test
    fun `failed playback does not permanently block retry for the same session`() {
        val backend = FakeCompletionSoundBackend(shouldSucceed = false)
        val coordinator = FocusCompletionSoundCoordinator(backend = backend, chimeResId = 303)

        assertFalse(coordinator.playForSessionCompletion("s-retry", enabled = true))
        assertFalse(coordinator.hasPlayedForSession("s-retry"))

        backend.shouldSucceed = true
        assertTrue(coordinator.playForSessionCompletion("s-retry", enabled = true))
        assertTrue(coordinator.hasPlayedForSession("s-retry"))
    }

    @Test
    fun `preview playback plays independently of session deduplication and release delegates cleanly`() {
        val backend = FakeCompletionSoundBackend()
        val coordinator = FocusCompletionSoundCoordinator(backend = backend, chimeResId = 404)

        assertTrue(coordinator.playPreview())
        assertTrue(coordinator.playPreview(volume = 0.4f))
        coordinator.release()

        assertEquals(2, backend.playedResIds.size)
        assertEquals(FocusCompletionSoundCoordinator.DEFAULT_CALM_VOLUME, backend.playedVolumes[0], 0.0001f)
        assertEquals(0.4f, backend.playedVolumes[1], 0.0001f)
        assertEquals(1, backend.releaseCount)
    }
}
