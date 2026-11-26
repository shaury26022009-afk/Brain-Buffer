package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Contract for an isolated, stateful procedural audio voice producing normalized samples
 * centered near target study loudness (~0.24–0.30 RMS) before master volume and soft limiting.
 */
internal interface SoundscapeVoice {
    fun reset()
    fun nextSample(sampleRate: Double): Double
}

private const val TWO_PI = 2.0 * PI

/**
 * 1. Wind Chimes — Delicate bamboo & metallic tubular chimes triggered organically by gentle wind gusts.
 */
internal class WindChimesVoice(seed: Int = 101) : SoundscapeVoice {
    private val rng = Random(seed)
    private val chimeFreqs = doubleArrayOf(1046.50, 1174.66, 1318.51, 1567.98, 1760.00, 2093.00)
    private val phases = DoubleArray(6)
    private val overtonePhases = DoubleArray(6)
    private val envs = DoubleArray(6)
    private var breezeLfo = 0.0
    private var windLp = 0.0
    private var windHp = 0.0
    private var prevWind = 0.0
    private var cooldownSamples = 0

    override fun reset() {
        phases.fill(0.0)
        overtonePhases.fill(0.0)
        envs.fill(0.0)
        breezeLfo = 0.0
        windLp = 0.0
        windHp = 0.0
        prevWind = 0.0
        cooldownSamples = 0
    }

    override fun nextSample(sampleRate: Double): Double {
        breezeLfo += TWO_PI * 0.14 / sampleRate
        if (breezeLfo > TWO_PI) breezeLfo -= TWO_PI
        val gust = 0.5 + 0.5 * sin(breezeLfo)

        // Subtle airy wind bed
        val w = rng.nextDouble(-1.0, 1.0)
        windLp += (0.045 + 0.05 * gust) * (w - windLp)
        windHp = 0.97 * (windHp + windLp - prevWind)
        prevWind = windLp
        val windBed = windHp * (0.22 + 0.18 * gust)

        // Trigger chimes in natural clusters during gusts
        if (cooldownSamples > 0) {
            cooldownSamples--
        } else if (rng.nextDouble() < (0.00018 + 0.00045 * gust)) {
            val idx = rng.nextInt(chimeFreqs.size)
            envs[idx] = 0.42 + rng.nextDouble() * 0.34
            cooldownSamples = (sampleRate * (0.14 + rng.nextDouble() * 0.32)).toInt()
        }

        var chimeSum = 0.0
        for (i in chimeFreqs.indices) {
            val env = envs[i]
            if (env > 0.0004) {
                val f = chimeFreqs[i]
                phases[i] += TWO_PI * f / sampleRate
                // Authentic metallic tubular bell partial ratio (~2.76x fundamental)
                overtonePhases[i] += TWO_PI * (f * 2.756) / sampleRate
                if (phases[i] > TWO_PI) phases[i] -= TWO_PI
                if (overtonePhases[i] > TWO_PI) overtonePhases[i] -= TWO_PI

                val body = sin(phases[i]) * 0.78
                val shimmer = sin(overtonePhases[i]) * 0.22 * env
                chimeSum += (body + shimmer) * env
                envs[i] = env * 0.99986
            }
        }

        return (windBed + chimeSum * 0.68).coerceIn(-1.4, 1.4)
    }
}

/**
 * 2. Pastoral — Peaceful countryside meadow air, warm sunlit breeze, and soft melodic songbird phrases.
 */
internal class PastoralVoice(seed: Int = 202) : SoundscapeVoice {
    private val rng = Random(seed)
    private var breezeLp = 0.0
    private var breezeLfo = 0.0
    private var meadowRustle = 0.0
    // Bird phrase synthesizer
    private var birdPhase = 0.0
    private var birdTime = 0.0
    private var birdDuration = 0.0
    private var birdBaseFreq = 2650.0
    private var birdSweepHz = 650.0
    private var birdNotesRemaining = 0
    private var birdGapSamples = 2200
    private var birdAmp = 0.0

    override fun reset() {
        breezeLp = 0.0
        breezeLfo = 0.0
        meadowRustle = 0.0
        birdPhase = 0.0
        birdTime = 0.0
        birdDuration = 0.0
        birdNotesRemaining = 0
        birdGapSamples = 2200
        birdAmp = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        breezeLfo += TWO_PI * 0.09 / sampleRate
        if (breezeLfo > TWO_PI) breezeLfo -= TWO_PI
        val swell = 0.55 + 0.45 * sin(breezeLfo)

        val w = rng.nextDouble(-1.0, 1.0)
        breezeLp += (0.032 + 0.03 * swell) * (w - breezeLp)
        meadowRustle += 0.14 * (w - meadowRustle)
        val meadowBed = breezeLp * (0.65 + 0.35 * swell) + (meadowRustle - breezeLp) * 0.16

        // Melodic multi-syllable songbird chirps
        var bird = 0.0
        if (birdDuration > 0.0) {
            val progress = (birdTime / birdDuration).coerceIn(0.0, 1.0)
            val env = sin(progress * PI) * sin(progress * PI)
            val freq = birdBaseFreq + birdSweepHz * sin(progress * PI * 1.15)
            birdPhase += TWO_PI * freq / sampleRate
            if (birdPhase > TWO_PI) birdPhase -= TWO_PI
            bird = (sin(birdPhase) * 0.82 + sin(birdPhase * 2.0) * 0.18) * env * birdAmp
            birdTime += 1.0 / sampleRate
            if (birdTime >= birdDuration) {
                birdDuration = 0.0
                birdGapSamples = if (birdNotesRemaining > 0) {
                    (sampleRate * (0.07 + rng.nextDouble() * 0.11)).toInt()
                } else {
                    (sampleRate * (1.1 + rng.nextDouble() * 2.2)).toInt()
                }
            }
        } else if (birdGapSamples > 0) {
            birdGapSamples--
        } else {
            if (birdNotesRemaining <= 0) {
                birdNotesRemaining = 2 + rng.nextInt(3)
                birdBaseFreq = 2250.0 + rng.nextDouble() * 1250.0
                birdAmp = 0.24 + rng.nextDouble() * 0.20
            }
            birdNotesRemaining--
            birdTime = 0.0
            birdDuration = 0.11 + rng.nextDouble() * 0.14
            birdSweepHz = (if (rng.nextBoolean()) 1.0 else -1.0) * (350.0 + rng.nextDouble() * 680.0)
            birdPhase = 0.0
        }

        return (meadowBed * 0.95 + bird * 0.92).coerceIn(-1.4, 1.4)
    }
}

/**
 * 3. Beach — Realistic rolling surf waves with crest buildup, frothy crash, receding foam, and coastal breeze.
 */
internal class BeachVoice(seed: Int = 303) : SoundscapeVoice {
    private val rng = Random(seed)
    private var wavePhase = 0.0
    private var subWavePhase = 1.7
    private var surfLp = 0.0
    private var foamLp = 0.0
    private var deepSwellLp = 0.0

    override fun reset() {
        wavePhase = 0.0
        subWavePhase = 1.7
        surfLp = 0.0
        foamLp = 0.0
        deepSwellLp = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        // ~8.2s primary wave cycle + ~13.4s secondary swell for non-repetitive realism
        wavePhase += TWO_PI * 0.122 / sampleRate
        subWavePhase += TWO_PI * 0.075 / sampleRate
        if (wavePhase > TWO_PI) wavePhase -= TWO_PI
        if (subWavePhase > TWO_PI) subWavePhase -= TWO_PI

        // Asymmetric wave shape: swift crest crash, longer receding shoreline wash
        val cyclePos = wavePhase / TWO_PI
        val waveEnv = if (cyclePos < 0.32) {
            val p = cyclePos / 0.32
            p * p * (3.0 - 2.0 * p)
        } else {
            val p = (cyclePos - 0.32) / 0.68
            exp(-p * 2.55)
        }
        val secondarySwell = 0.5 + 0.5 * sin(subWavePhase)

        val white = rng.nextDouble(-1.0, 1.0)
        // Deep ocean rumble bed
        deepSwellLp += 0.022 * (white - deepSwellLp)
        // Surf body whose brightness opens up sharply as the wave breaks
        val surfCutoff = 0.055 + 0.24 * waveEnv
        surfLp += surfCutoff * (white - surfLp)
        // High frothy receding shoreline hiss
        foamLp += 0.42 * (white - foamLp)
        val froth = (foamLp - surfLp) * (0.28 + 0.62 * waveEnv * waveEnv)

        val combined = deepSwellLp * (1.25 + 0.45 * secondarySwell) +
            surfLp * (0.48 + 0.92 * waveEnv) +
            froth * 0.58
        return (combined * 0.78).coerceIn(-1.4, 1.4)
    }
}

/**
 * 4. Summer Night — Authentic nocturnal cricket choruses (~4.0–4.6 kHz stridulation pulses) over cool night air.
 */
internal class SummerNightVoice(seed: Int = 404) : SoundscapeVoice {
    private val rng = Random(seed)
    private val carrierFreqs = doubleArrayOf(3960.0, 4290.0, 4580.0)
    private val pulseRates = doubleArrayOf(21.5, 24.0, 19.2)
    private val chirpRates = doubleArrayOf(1.18, 0.87, 0.64)
    private val carrierPhases = DoubleArray(3)
    private val pulsePhases = DoubleArray(3)
    private val chirpPhases = doubleArrayOf(0.0, 2.1, 4.3)
    private var nightAirLp = 0.0
    private var nightLfo = 0.0

    override fun reset() {
        carrierPhases.fill(0.0)
        pulsePhases.fill(0.0)
        chirpPhases[0] = 0.0
        chirpPhases[1] = 2.1
        chirpPhases[2] = 4.3
        nightAirLp = 0.0
        nightLfo = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        nightLfo += TWO_PI * 0.08 / sampleRate
        if (nightLfo > TWO_PI) nightLfo -= TWO_PI
        val airSwell = 0.6 + 0.4 * sin(nightLfo)

        val w = rng.nextDouble(-1.0, 1.0)
        nightAirLp += 0.036 * (w - nightAirLp)
        val nightAir = nightAirLp * (0.85 + 0.35 * airSwell)

        var crickets = 0.0
        val gains = doubleArrayOf(0.30, 0.24, 0.17)
        for (i in 0..2) {
            carrierPhases[i] += TWO_PI * carrierFreqs[i] / sampleRate
            pulsePhases[i] += TWO_PI * pulseRates[i] / sampleRate
            chirpPhases[i] += TWO_PI * chirpRates[i] / sampleRate
            if (carrierPhases[i] > TWO_PI) carrierPhases[i] -= TWO_PI
            if (pulsePhases[i] > TWO_PI) pulsePhases[i] -= TWO_PI
            if (chirpPhases[i] > TWO_PI) chirpPhases[i] -= TWO_PI

            // Rhythmic chirp window (active ~55% of each cycle) modulated by fast wing stridulation
            val chirpGate = (sin(chirpPhases[i]) - 0.12).coerceAtLeast(0.0) / 0.88
            val smoothGate = chirpGate * chirpGate
            val pulse = 0.5 + 0.5 * sin(pulsePhases[i])
            val stridulation = pulse * pulse
            crickets += sin(carrierPhases[i]) * smoothGate * stridulation * gains[i]
        }

        return (nightAir + crickets * 0.85).coerceIn(-1.4, 1.4)
    }
}

/**
 * 5. Rain — Distinct layered rainfall: steady crisp mid-high rain sheet + sparkling rooftop droplets.
 */
internal class RainVoice(seed: Int = 505) : SoundscapeVoice {
    private val rng = Random(seed)
    private var sheetLp = 0.0
    private var bodyLp = 0.0
    private var prevSheet = 0.0
    private var sheetHp = 0.0
    // 4 overlapping droplet slots for crisp natural patter
    private val dropPhases = DoubleArray(4)
    private val dropFreqs = DoubleArray(4)
    private val dropEnvs = DoubleArray(4)
    private var rainLfo = 0.0

    override fun reset() {
        sheetLp = 0.0
        bodyLp = 0.0
        prevSheet = 0.0
        sheetHp = 0.0
        dropPhases.fill(0.0)
        dropFreqs.fill(1000.0)
        dropEnvs.fill(0.0)
        rainLfo = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        rainLfo += TWO_PI * 0.065 / sampleRate
        if (rainLfo > TWO_PI) rainLfo -= TWO_PI
        val intensity = 0.88 + 0.12 * sin(rainLfo)

        val w = rng.nextDouble(-1.0, 1.0)
        // High-pass crisp rain hiss (distinctly brighter & more granular than Brown/Pink/Beach)
        sheetLp += 0.56 * (w - sheetLp)
        sheetHp = 0.86 * (sheetHp + sheetLp - prevSheet)
        prevSheet = sheetLp
        // Soft mid-band roof rumble
        bodyLp += 0.08 * (w - bodyLp)

        var droplets = 0.0
        for (i in dropEnvs.indices) {
            if (dropEnvs[i] <= 0.0015) {
                if (rng.nextDouble() < 0.0016) {
                    dropEnvs[i] = 0.18 + rng.nextDouble() * 0.24
                    dropFreqs[i] = 720.0 + rng.nextDouble() * 1450.0
                    dropPhases[i] = 0.0
                }
            } else {
                dropPhases[i] += TWO_PI * dropFreqs[i] / sampleRate
                if (dropPhases[i] > TWO_PI) dropPhases[i] -= TWO_PI
                droplets += sin(dropPhases[i]) * dropEnvs[i]
                dropEnvs[i] *= 0.991
            }
        }

        return ((sheetHp * 0.54 + bodyLp * 0.62 + droplets * 0.72) * intensity).coerceIn(-1.4, 1.4)
    }
}

/**
 * 6. Forest — Rustling woodland pine canopy wind with resonant swaying gusts & distant wood-dove calls.
 */
internal class ForestVoice(seed: Int = 606) : SoundscapeVoice {
    private val rng = Random(seed)
    private var lfoA = 0.0
    private var lfoB = 1.9
    private var canopyLp = 0.0
    private var canopyBp = 0.0
    private var leafLp = 0.0
    // Soft distant woodland dove / forest call (460–540 Hz two-tone coo)
    private var dovePhase = 0.0
    private var doveTime = 0.0
    private var doveActive = false
    private var doveCooldown = 6600
    private var dovePitch = 490.0

    override fun reset() {
        lfoA = 0.0
        lfoB = 1.9
        canopyLp = 0.0
        canopyBp = 0.0
        leafLp = 0.0
        dovePhase = 0.0
        doveTime = 0.0
        doveActive = false
        doveCooldown = 6600
    }

    override fun nextSample(sampleRate: Double): Double {
        lfoA += TWO_PI * 0.13 / sampleRate
        lfoB += TWO_PI * 0.055 / sampleRate
        if (lfoA > TWO_PI) lfoA -= TWO_PI
        if (lfoB > TWO_PI) lfoB -= TWO_PI
        val gust = 0.45 + 0.35 * sin(lfoA) + 0.20 * sin(lfoB)

        val w = rng.nextDouble(-1.0, 1.0)
        // Swaying pine-needle rustle band
        canopyLp += (0.04 + 0.09 * gust) * (w - canopyLp)
        leafLp += 0.32 * (w - leafLp)
        val rustle = (leafLp - canopyLp) * (0.28 + 0.36 * gust)
        canopyBp += 0.08 * (canopyLp - canopyBp)

        var dove = 0.0
        if (doveActive) {
            val dur = 0.68
            val p = (doveTime / dur).coerceIn(0.0, 1.0)
            val twoPulseEnv = sin(p * PI) * (0.65 + 0.35 * sin(p * TWO_PI * 1.5))
            val freq = dovePitch * (1.0 + 0.05 * sin(p * PI))
            dovePhase += TWO_PI * freq / sampleRate
            if (dovePhase > TWO_PI) dovePhase -= TWO_PI
            dove = sin(dovePhase) * twoPulseEnv.coerceAtLeast(0.0) * 0.24
            doveTime += 1.0 / sampleRate
            if (doveTime >= dur) {
                doveActive = false
                doveCooldown = (sampleRate * (2.6 + rng.nextDouble() * 3.8)).toInt()
            }
        } else if (doveCooldown > 0) {
            doveCooldown--
        } else {
            doveActive = true
            doveTime = 0.0
            dovePhase = 0.0
            dovePitch = 455.0 + rng.nextDouble() * 90.0
        }

        return (canopyBp * 1.15 + rustle * 0.85 + dove).coerceIn(-1.4, 1.4)
    }
}

/**
 * 7. Brown Noise — Deep, velvety 1/f² Brownian concentration rumble with DC-blocking stabilization.
 */
internal class BrownNoiseVoice(seed: Int = 707) : SoundscapeVoice {
    private val rng = Random(seed)
    private var brown = 0.0
    private var dcPrevIn = 0.0
    private var dcPrevOut = 0.0

    override fun reset() {
        brown = 0.0
        dcPrevIn = 0.0
        dcPrevOut = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        val white = rng.nextDouble(-1.0, 1.0)
        brown = (brown + 0.032 * white) / 1.032
        // High-pass DC blocker (~24 Hz) keeps low-end warm and full without offset drift
        val hp = 0.993 * (dcPrevOut + brown - dcPrevIn)
        dcPrevIn = brown
        dcPrevOut = hp
        return (hp * 4.4).coerceIn(-1.4, 1.4)
    }
}

/**
 * 8. Pink Noise — Balanced Paul Kellet 7-pole 1/f acoustic masking calibrated for full clarity.
 */
internal class PinkNoiseVoice(seed: Int = 808) : SoundscapeVoice {
    private val rng = Random(seed)
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    override fun reset() {
        b0 = 0.0; b1 = 0.0; b2 = 0.0; b3 = 0.0; b4 = 0.0; b5 = 0.0; b6 = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        val white = rng.nextDouble(-1.0, 1.0)
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return (pink * 0.24).coerceIn(-1.4, 1.4)
    }
}

/**
 * 9. White Noise — Consistent broadband masking with gentle upper-treble smoothing to avoid ear fatigue.
 */
internal class WhiteNoiseVoice(seed: Int = 909) : SoundscapeVoice {
    private val rng = Random(seed)
    private var lp = 0.0

    override fun reset() {
        lp = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        val white = rng.nextDouble(-1.0, 1.0)
        // Mild smoothing preserves unmistakable bright white-noise hiss while preventing harsh speaker fizz
        lp += 0.74 * (white - lp)
        return (lp * 0.62).coerceIn(-1.4, 1.4)
    }
}

/**
 * 10. Peaceful Fantasy — Original cinematic open-world exploration ambience:
 * lush warm orchestral string pad (Dmaj7 -> Aadd9 -> Bm7 -> Gmaj7),
 * delicate harp arpeggios, and gentle airy flute shimmer. 100% original procedural music.
 */
internal class PeacefulFantasyVoice(seed: Int = 1010) : SoundscapeVoice {
    private val rng = Random(seed)
    // Original 4-chord open-world harmonic palette (Hz)
    private val padChords = arrayOf(
        doubleArrayOf(146.83, 220.00, 277.18, 369.99), // Dmaj7
        doubleArrayOf(164.81, 220.00, 277.18, 329.63), // Aadd9/E
        doubleArrayOf(123.47, 185.00, 246.94, 293.66), // Bm7
        doubleArrayOf(196.00, 246.94, 293.66, 369.99)  // Gmaj7
    )
    private val harpArps = arrayOf(
        doubleArrayOf(587.33, 739.99, 880.00, 1108.73, 880.00, 739.99),
        doubleArrayOf(554.37, 659.25, 880.00, 987.77, 880.00, 659.25),
        doubleArrayOf(493.88, 587.33, 739.99, 880.00, 739.99, 587.33),
        doubleArrayOf(587.33, 739.99, 987.77, 1174.66, 987.77, 739.99)
    )
    private val padPhases = DoubleArray(4)
    private val detunePhases = DoubleArray(4)
    private val currentPadFreqs = padChords[0].copyOf()
    private var elapsedSec = 0.0
    private var harpPhase = 0.0
    private var harpEnv = 0.0
    private var harpFreq = 587.33
    private var lastArpStep = -1
    private var flutePhase = 0.0
    private var breathLp = 0.0

    override fun reset() {
        padPhases.fill(0.0)
        detunePhases.fill(0.0)
        for (i in currentPadFreqs.indices) currentPadFreqs[i] = padChords[0][i]
        elapsedSec = 0.0
        harpPhase = 0.0
        harpEnv = 0.0
        harpFreq = 587.33
        lastArpStep = -1
        flutePhase = 0.0
        breathLp = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        val chordDurationSec = 4.8
        val totalCycleSec = chordDurationSec * padChords.size
        val cyclePos = elapsedSec % totalCycleSec
        val chordIdx = (cyclePos / chordDurationSec).toInt().coerceIn(0, padChords.lastIndex)
        val targetChord = padChords[chordIdx]

        // Smooth orchestral string glide between chords
        var padSum = 0.0
        val swell = 0.80 + 0.20 * sin(TWO_PI * 0.18 * elapsedSec)
        for (i in 0..3) {
            currentPadFreqs[i] += (targetChord[i] - currentPadFreqs[i]) * (2.6 / sampleRate)
            val f = currentPadFreqs[i]
            padPhases[i] += TWO_PI * f / sampleRate
            detunePhases[i] += TWO_PI * (f * 1.0045) / sampleRate
            if (padPhases[i] > TWO_PI) padPhases[i] -= TWO_PI
            if (detunePhases[i] > TWO_PI) detunePhases[i] -= TWO_PI

            val voice = sin(padPhases[i]) * 0.56 +
                sin(detunePhases[i]) * 0.30 +
                sin(padPhases[i] * 2.0) * 0.14
            padSum += voice * (if (i == 0) 0.34 else 0.22)
        }

        // Delicate plucked harp arpeggio every 0.8s
        val arpStep = (elapsedSec / 0.80).toInt()
        if (arpStep != lastArpStep) {
            lastArpStep = arpStep
            val notes = harpArps[chordIdx]
            harpFreq = notes[arpStep % notes.size]
            harpEnv = 0.38
            harpPhase = 0.0
        }
        val harp = if (harpEnv > 0.0005) {
            harpPhase += TWO_PI * harpFreq / sampleRate
            if (harpPhase > TWO_PI) harpPhase -= TWO_PI
            val tone = (sin(harpPhase) * 0.78 + sin(harpPhase * 2.0) * 0.22 * harpEnv) * harpEnv
            harpEnv *= 0.99965
            tone
        } else 0.0

        // Gentle airy high flute shimmer
        val fluteFreq = harpArps[chordIdx][2] * 0.5
        flutePhase += TWO_PI * fluteFreq / sampleRate
        if (flutePhase > TWO_PI) flutePhase -= TWO_PI
        val fluteEnv = (0.5 + 0.5 * sin(TWO_PI * 0.11 * elapsedSec - 1.2))
        val air = rng.nextDouble(-1.0, 1.0)
        breathLp += 0.08 * (air - breathLp)
        val flute = (sin(flutePhase) * 0.14 + breathLp * 0.04) * fluteEnv * fluteEnv

        elapsedSec += 1.0 / sampleRate
        return (padSum * swell * 0.78 + harp * 0.72 + flute).coerceIn(-1.4, 1.4)
    }
}

/**
 * 11. Energetic Focus — Original motivating 104 BPM instrumental study groove:
 * crisp plucked marimba/kalimba motif, warm rhythmic bass pulse, light chord pad, and soft brushed shaker.
 */
internal class EnergeticFocusVoice(seed: Int = 1111) : SoundscapeVoice {
    private val rng = Random(seed)
    // 4-bar uplifting progression: Am7 -> Fmaj7 -> Cmaj7 -> G6
    private val bassRoots = doubleArrayOf(110.00, 87.31, 130.81, 98.00)
    private val motifs = arrayOf(
        doubleArrayOf(440.00, 523.25, 659.25, 523.25, 587.33, 659.25, 783.99, 659.25),
        doubleArrayOf(349.23, 440.00, 523.25, 659.25, 523.25, 440.00, 523.25, 440.00),
        doubleArrayOf(523.25, 659.25, 783.99, 659.25, 587.33, 523.25, 659.25, 587.33),
        doubleArrayOf(392.00, 493.88, 587.33, 659.25, 587.33, 493.88, 587.33, 523.25)
    )
    private var elapsedSec = 0.0
    private var lastStep = -1
    private var pluckPhase = 0.0
    private var pluckFreq = 440.0
    private var pluckEnv = 0.0
    private var bassPhase = 0.0
    private var bassFreq = 110.0
    private var bassEnv = 0.0
    private var padPhaseA = 0.0
    private var padPhaseB = 0.0
    private var shakerEnv = 0.0
    private var prevWhite = 0.0

    override fun reset() {
        elapsedSec = 0.0
        lastStep = -1
        pluckPhase = 0.0
        pluckEnv = 0.0
        bassPhase = 0.0
        bassEnv = 0.0
        padPhaseA = 0.0
        padPhaseB = 0.0
        shakerEnv = 0.0
        prevWhite = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        // 104 BPM -> 8th note duration = 60 / (104 * 2) = 0.28846s
        val stepDuration = 60.0 / (104.0 * 2.0)
        val step = (elapsedSec / stepDuration).toInt()
        val barIdx = (step / 8) % 4
        val stepInBar = step % 8

        if (step != lastStep) {
            lastStep = step
            pluckFreq = motifs[barIdx][stepInBar]
            pluckEnv = if (stepInBar % 2 == 0) 0.44 else 0.30
            if (stepInBar == 0 || stepInBar == 3 || stepInBar == 6) {
                bassFreq = bassRoots[barIdx]
                bassEnv = 0.48
            }
            shakerEnv = if (stepInBar % 2 == 1) 0.14 else 0.06
        }

        // Warm marimba/kalimba pluck
        pluckPhase += TWO_PI * pluckFreq / sampleRate
        if (pluckPhase > TWO_PI) pluckPhase -= TWO_PI
        val pluck = (sin(pluckPhase) * 0.80 + sin(pluckPhase * 3.0) * 0.20 * pluckEnv) * pluckEnv
        pluckEnv *= 0.99915

        // Warm focused bass pulse
        bassPhase += TWO_PI * bassFreq / sampleRate
        if (bassPhase > TWO_PI) bassPhase -= TWO_PI
        val bass = (sin(bassPhase) * 0.78 + sin(bassPhase * 2.0) * 0.22) * bassEnv
        bassEnv *= 0.99945

        // Subtle warm chord glue
        val padA = bassRoots[barIdx] * 2.0
        val padB = bassRoots[barIdx] * 3.0
        padPhaseA += TWO_PI * padA / sampleRate
        padPhaseB += TWO_PI * padB / sampleRate
        if (padPhaseA > TWO_PI) padPhaseA -= TWO_PI
        if (padPhaseB > TWO_PI) padPhaseB -= TWO_PI
        val pad = (sin(padPhaseA) + sin(padPhaseB) * 0.65) * 0.14

        // Crisp gentle brushed shaker pulse
        val w = rng.nextDouble(-1.0, 1.0)
        val hi = w - prevWhite
        prevWhite = w
        val shaker = hi * shakerEnv
        shakerEnv *= 0.995

        elapsedSec += 1.0 / sampleRate
        return (pluck * 0.82 + bass * 0.72 + pad + shaker * 0.55).coerceIn(-1.4, 1.4)
    }
}

/**
 * 12. Deep Focus — Minimal, calm instrumental textures: warm Rhodes-like electric piano swells,
 * velvety low harmonic drone, and sparse contemplative bell notes with zero vocals.
 */
internal class DeepFocusVoice : SoundscapeVoice {
    private val chords = arrayOf(
        doubleArrayOf(130.81, 196.00, 246.94, 293.66), // Cmaj9
        doubleArrayOf(110.00, 164.81, 220.00, 246.94), // Am9
        doubleArrayOf(174.61, 220.00, 261.63, 329.63), // Fmaj7
        doubleArrayOf(146.83, 196.00, 261.63, 293.66)  // Gsus4/D
    )
    private val phases = DoubleArray(4)
    private val currentFreqs = chords[0].copyOf()
    private var bellPhase = 0.0
    private var bellEnv = 0.0
    private var bellFreq = 523.25
    private var lastChordIdx = -1
    private var elapsedSec = 0.0

    override fun reset() {
        phases.fill(0.0)
        for (i in currentFreqs.indices) currentFreqs[i] = chords[0][i]
        bellPhase = 0.0
        bellEnv = 0.0
        lastChordIdx = -1
        elapsedSec = 0.0
    }

    override fun nextSample(sampleRate: Double): Double {
        val chordDuration = 6.4
        val cyclePos = elapsedSec % (chordDuration * chords.size)
        val chordIdx = (cyclePos / chordDuration).toInt().coerceIn(0, chords.lastIndex)
        val target = chords[chordIdx]

        if (chordIdx != lastChordIdx) {
            lastChordIdx = chordIdx
            bellFreq = target[3] * 2.0
            bellEnv = 0.34
            bellPhase = 0.0
        }

        val breath = 0.74 + 0.26 * sin(TWO_PI * 0.08 * elapsedSec)
        val tremolo = 0.90 + 0.10 * sin(TWO_PI * 2.6 * elapsedSec)
        var chordSum = 0.0
        for (i in 0..3) {
            currentFreqs[i] += (target[i] - currentFreqs[i]) * (1.8 / sampleRate)
            phases[i] += TWO_PI * currentFreqs[i] / sampleRate
            if (phases[i] > TWO_PI) phases[i] -= TWO_PI
            val tine = sin(phases[i]) * 0.84 + sin(phases[i] * 2.0) * 0.16
            chordSum += tine * (if (i == 0) 0.36 else 0.22)
        }

        val bell = if (bellEnv > 0.0004) {
            bellPhase += TWO_PI * bellFreq / sampleRate
            if (bellPhase > TWO_PI) bellPhase -= TWO_PI
            val out = (sin(bellPhase) * 0.85 + sin(bellPhase * 2.0) * 0.15) * bellEnv
            bellEnv *= 0.99982
            out
        } else 0.0

        elapsedSec += 1.0 / sampleRate
        return (chordSum * breath * tremolo * 0.88 + bell * 0.62).coerceIn(-1.4, 1.4)
    }
}
