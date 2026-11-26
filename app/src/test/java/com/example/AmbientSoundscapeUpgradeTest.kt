package com.example

import com.example.audio.AmbientPcmSynthesizer
import com.example.audio.AmbientSoundscape
import com.example.audio.AmbientSoundscapeController
import com.example.audio.SoundscapeCategory
import com.example.audio.SoundscapeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure-JVM verification suite for the upgraded 12-sound offline Focus Soundscape engine,
 * acoustic distinctness, normalization/loudness without clipping, smooth crossfading,
 * preview/selection lifecycle, persistence, and timer auto-sync.
 */
class AmbientSoundscapeUpgradeTest {

    private data class AcousticProfile(
        val rms: Double,
        val peak: Int,
        val zeroCrossingRate: Double,
        val highFreqRatio: Double
    )

    private fun measureProfile(soundscape: AmbientSoundscape, volume: Float = 0.75f): AcousticProfile {
        val synth = AmbientPcmSynthesizer(sampleRate = AmbientPcmSynthesizer.SAMPLE_RATE_HZ, seed = 42)
        // Warm up 1.5 seconds so LFOs, envelopes, chimes, and phrases are well underway
        val warmup = ShortArray(AmbientPcmSynthesizer.SAMPLE_RATE_HZ * 3 / 2)
        synth.generateSamples(soundscape, volume, warmup)

        // Measure across 3 full seconds of audio
        val samples = ShortArray(AmbientPcmSynthesizer.SAMPLE_RATE_HZ * 3)
        synth.generateSamples(soundscape, volume, samples)

        var sumSq = 0.0
        var peak = 0
        var zeroCrossings = 0
        var diffSqSum = 0.0

        for (i in samples.indices) {
            val s = samples[i].toDouble() / Short.MAX_VALUE.toDouble()
            sumSq += s * s
            val absVal = abs(samples[i].toInt())
            if (absVal > peak) peak = absVal
            if (i > 0) {
                val prev = samples[i - 1].toDouble() / Short.MAX_VALUE.toDouble()
                if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                    zeroCrossings++
                }
                val diff = s - prev
                diffSqSum += diff * diff
            }
        }
        val rms = sqrt(sumSq / samples.size)
        val zcr = zeroCrossings.toDouble() / samples.size
        val highFreqRatio = if (sumSq > 1e-9) sqrt(diffSqSum / sumSq) else 0.0
        return AcousticProfile(rms = rms, peak = peak, zeroCrossingRate = zcr, highFreqRatio = highFreqRatio)
    }

    @Test
    fun `sound library contains all 12 required sounds split cleanly into 2 categories`() {
        val nature = AmbientSoundscape.byCategory(SoundscapeCategory.NATURE_ATMOSPHERE)
        val fantasy = AmbientSoundscape.byCategory(SoundscapeCategory.FANTASY_INSTRUMENTAL)

        assertEquals(9, nature.size)
        assertEquals(3, fantasy.size)
        assertEquals(12, AmbientSoundscape.entries.count { it != AmbientSoundscape.OFF })

        assertTrue(nature.containsAll(
            listOf(
                AmbientSoundscape.WIND_CHIMES,
                AmbientSoundscape.PASTORAL,
                AmbientSoundscape.BEACH,
                AmbientSoundscape.SUMMER_NIGHT,
                AmbientSoundscape.SOFT_RAIN,
                AmbientSoundscape.FOREST,
                AmbientSoundscape.BROWN_NOISE,
                AmbientSoundscape.PINK_FOCUS,
                AmbientSoundscape.WHITE_NOISE
            )
        ))
        assertTrue(fantasy.containsAll(
            listOf(
                AmbientSoundscape.PEACEFUL_FANTASY,
                AmbientSoundscape.ENERGETIC_FOCUS,
                AmbientSoundscape.DEEP_FOCUS
            )
        ))
    }

    @Test
    fun `all 12 soundscapes deliver healthy audible loudness without clipping`() {
        val activeSounds = AmbientSoundscape.entries.filter { it != AmbientSoundscape.OFF }
        activeSounds.forEach { sound ->
            val profile = measureProfile(sound, volume = SoundscapeState.DEFAULT_VOLUME)
            assertTrue(
                "Expected clear audible RMS (>= 0.04) for $sound, got ${profile.rms}",
                profile.rms >= 0.04
            )
            assertTrue(
                "Expected non-clipping peak (< 32500) for $sound, got ${profile.peak}",
                profile.peak in 2500..32500
            )
        }
    }

    @Test
    fun `noise colors and sound categories have clearly distinct spectral profiles`() {
        val brown = measureProfile(AmbientSoundscape.BROWN_NOISE)
        val pink = measureProfile(AmbientSoundscape.PINK_FOCUS)
        val white = measureProfile(AmbientSoundscape.WHITE_NOISE)
        val rain = measureProfile(AmbientSoundscape.SOFT_RAIN)
        val summerNight = measureProfile(AmbientSoundscape.SUMMER_NIGHT)
        val beach = measureProfile(AmbientSoundscape.BEACH)
        val peacefulFantasy = measureProfile(AmbientSoundscape.PEACEFUL_FANTASY)
        val energeticFocus = measureProfile(AmbientSoundscape.ENERGETIC_FOCUS)
        val deepFocus = measureProfile(AmbientSoundscape.DEEP_FOCUS)

        // Brown < Pink < White spectral brightness (zero-crossing rate & high-frequency energy)
        assertTrue(
            "Brown (${brown.zeroCrossingRate}) should be much darker than Pink (${pink.zeroCrossingRate})",
            brown.zeroCrossingRate < pink.zeroCrossingRate * 0.75
        )
        assertTrue(
            "Pink (${pink.zeroCrossingRate}) should be noticeably softer than White (${white.zeroCrossingRate})",
            pink.zeroCrossingRate < white.zeroCrossingRate * 0.85
        )

        // Nature sounds must not collapse into identical waveforms
        assertNotEquals(
            "Beach and Rain must have distinct spectral brightness",
            beach.zeroCrossingRate,
            rain.zeroCrossingRate,
            0.015
        )
        assertNotEquals(
            "Summer Night and Brown Noise must have distinct spectral profiles",
            summerNight.zeroCrossingRate,
            brown.zeroCrossingRate,
            0.03
        )

        // Fantasy & Instrumental tracks must differ in tonal brightness/energy
        assertTrue(
            "Deep Focus should have lower high-frequency energy than Energetic Focus",
            deepFocus.highFreqRatio < energeticFocus.highFreqRatio
        )
        assertTrue(
            "Peaceful Fantasy should be tonal (lower ZCR than White Noise)",
            peacefulFantasy.zeroCrossingRate < white.zeroCrossingRate * 0.5
        )
    }

    @Test
    fun `switching soundscapes crossfades smoothly without clicks or clipping`() {
        val synth = AmbientPcmSynthesizer()
        val buf1 = ShortArray(4096)
        val buf2 = ShortArray(8192)

        synth.generateSamples(AmbientSoundscape.BROWN_NOISE, 0.85f, buf1)
        synth.generateSamples(AmbientSoundscape.PEACEFUL_FANTASY, 0.85f, buf2)

        val boundaryJump = abs(buf2[0].toInt() - buf1.last().toInt())
        assertTrue(
            "Expected smooth crossfade across sound switch boundary without pop (jump=$boundaryJump)",
            boundaryJump < 9000
        )
        val maxPeak = buf2.maxOf { abs(it.toInt()) }
        assertTrue("Crossfade output should never clip (peak=$maxPeak)", maxPeak <= 32500)
    }

    @Test
    fun `ducking attenuates output volume smoothly`() {
        val synthNormal = AmbientPcmSynthesizer(seed = 99)
        val synthDucked = AmbientPcmSynthesizer(seed = 99)
        val warm = ShortArray(8192)
        val normalBuf = ShortArray(8192)
        val duckedBuf = ShortArray(8192)

        synthNormal.generateSamples(AmbientSoundscape.PINK_FOCUS, 0.8f, warm, ducked = false)
        synthNormal.generateSamples(AmbientSoundscape.PINK_FOCUS, 0.8f, normalBuf, ducked = false)

        synthDucked.generateSamples(AmbientSoundscape.PINK_FOCUS, 0.8f, warm, ducked = true)
        synthDucked.generateSamples(AmbientSoundscape.PINK_FOCUS, 0.8f, duckedBuf, ducked = true)

        val normalRms = sqrt(normalBuf.map { it.toDouble() * it.toDouble() }.average())
        val duckedRms = sqrt(duckedBuf.map { it.toDouble() * it.toDouble() }.average())
        assertTrue(
            "Ducked RMS ($duckedRms) should be noticeably quieter than normal RMS ($normalRms)",
            duckedRms < normalRms * 0.55
        )
    }

    @Test
    fun `storage key migration preserves legacy selections safely`() {
        assertEquals(AmbientSoundscape.FOREST, AmbientSoundscape.fromStorageKey("NIGHT_BREEZE"))
        assertEquals(AmbientSoundscape.DEEP_FOCUS, AmbientSoundscape.fromStorageKey("DEEP_DRONE"))
        assertEquals(AmbientSoundscape.PEACEFUL_FANTASY, AmbientSoundscape.fromStorageKey("PEACEFUL_FANTASY"))
        assertEquals(AmbientSoundscape.OFF, AmbientSoundscape.fromStorageKey("UNKNOWN_KEY"))
        assertEquals(AmbientSoundscape.OFF, AmbientSoundscape.fromStorageKey(null))
    }

    @Test
    fun `controller manages preview, selection persistence, hydration, and timer auto-sync cleanly`() {
        var persistedSelected: AmbientSoundscape? = null
        var persistedVolume: Float? = null
        var persistedSync: Boolean? = null

        val controller = AmbientSoundscapeController(
            enableHardwareAudio = false,
            onPreferencesChanged = { sel, vol, sync ->
                persistedSelected = sel
                persistedVolume = vol
                persistedSync = sync
            }
        )

        // Hydrate persisted user choice
        controller.hydratePreferences(
            selected = AmbientSoundscape.BEACH,
            volume = 0.80f,
            syncWithTimer = true
        )
        assertEquals(AmbientSoundscape.BEACH, controller.state.value.selected)
        assertEquals(0.80f, controller.state.value.volume, 0.001f)
        assertFalse(controller.state.value.isPlaying)

        // Auto-sync starts selected sound when timer starts
        controller.onTimerRunningChanged(isTimerRunning = true)
        assertTrue(controller.state.value.isPlaying)
        assertEquals(AmbientSoundscape.BEACH, controller.state.value.effectiveSoundscape)

        // Previewing another sound auditions without overwriting persisted selection
        controller.togglePreview(AmbientSoundscape.WIND_CHIMES)
        assertTrue(controller.state.value.isPreviewing)
        assertEquals(AmbientSoundscape.WIND_CHIMES, controller.state.value.effectiveSoundscape)
        assertEquals(AmbientSoundscape.BEACH, controller.state.value.selected)

        // Stopping preview restores the previously playing selected sound
        controller.stopPreview()
        assertFalse(controller.state.value.isPreviewing)
        assertNull(controller.state.value.previewing)
        assertTrue(controller.state.value.isPlaying)
        assertEquals(AmbientSoundscape.BEACH, controller.state.value.effectiveSoundscape)

        // Selecting a new sound clears preview and triggers persistence callback
        controller.selectSoundscape(AmbientSoundscape.ENERGETIC_FOCUS, autoPlay = true)
        assertEquals(AmbientSoundscape.ENERGETIC_FOCUS, persistedSelected)
        assertEquals(0.80f, persistedVolume ?: 0f, 0.001f)
        assertEquals(true, persistedSync)

        // Volume clamps safely and persists
        controller.setVolume(1.5f)
        assertEquals(1.0f, controller.state.value.volume, 0.001f)
        assertEquals(1.0f, persistedVolume ?: 0f, 0.001f)

        // Ending focus session stops audio when syncWithTimer is true
        controller.onSessionEnded()
        assertFalse(controller.state.value.isPlaying)
        assertEquals(AmbientSoundscape.ENERGETIC_FOCUS, controller.state.value.selected)

        controller.release()
    }
}
