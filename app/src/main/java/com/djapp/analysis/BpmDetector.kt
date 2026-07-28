package com.djapp.analysis

import kotlin.math.min
import kotlin.math.sqrt

data class BPMResult(
    val bpm: Double,
    val bpmConfidence: Double,
    val tempoStability: Double,
    val beatgrid: List<Double>,
    val downbeats: List<Double>
)

object BpmDetector {

    private const val DOWNSAMPLE_RATE = 4000
    private const val FRAME_SIZE = 64
    private const val HOP_SIZE = 32
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0

    fun detectBPM(samples: FloatArray, sampleRate: Int, totalDuration: Double): BPMResult {
        if (samples.isEmpty()) {
            return BPMResult(0.0, 0.0, 0.0, emptyList(), emptyList())
        }

        val mono = if (sampleRate > DOWNSAMPLE_RATE) {
            downsample(samples, sampleRate, DOWNSAMPLE_RATE)
        } else {
            samples.copyOf()
        }
        val effectiveRate = if (sampleRate > DOWNSAMPLE_RATE) DOWNSAMPLE_RATE else sampleRate

        val envelope = computeEnergyEnvelope(mono, effectiveRate)
        if (envelope.isEmpty()) {
            return BPMResult(0.0, 0.0, 0.0, emptyList(), emptyList())
        }

        val bpmResult = autocorrelateBPM(envelope, effectiveRate, totalDuration)
        val beatgrid = buildBeatgrid(bpmResult.bpm, totalDuration, bpmResult.onsets, envelope, effectiveRate)
        val downbeats = beatgrid.filterIndexed { index, _ -> index % 4 == 0 }

        return BPMResult(
            bpm = bpmResult.bpm,
            bpmConfidence = bpmResult.confidence,
            tempoStability = bpmResult.stability,
            beatgrid = beatgrid,
            downbeats = downbeats
        )
    }

    private fun downsample(input: FloatArray, originalRate: Int, targetRate: Int): FloatArray {
        val ratio = originalRate.toDouble() / targetRate
        val outputLen = (input.size / ratio).toInt()
        val output = FloatArray(outputLen)
        for (i in 0 until outputLen) {
            val srcPos = (i * ratio).toInt()
            output[i] = input[min(srcPos, input.size - 1)]
        }
        return output
    }

    private fun computeEnergyEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.size < FRAME_SIZE) return floatArrayOf()

        val numFrames = (samples.size - FRAME_SIZE) / HOP_SIZE + 1
        val envelope = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            val offset = i * HOP_SIZE
            var energy = 0.0
            for (j in 0 until FRAME_SIZE) {
                if (offset + j < samples.size) {
                    energy += samples[offset + j].toDouble() * samples[offset + j]
                }
            }
            envelope[i] = (energy / FRAME_SIZE).toFloat()
        }

        return envelope
    }

    private data class AutocorrResult(
        val bpm: Double,
        val confidence: Double,
        val stability: Double,
        val onsets: List<Int>
    )

    private fun autocorrelateBPM(
        envelope: FloatArray,
        sampleRate: Int,
        totalDuration: Double
    ): AutocorrResult {
        val framesPerSec = sampleRate.toDouble() / HOP_SIZE
        val minLag = (framesPerSec * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSec * 60.0 / MIN_BPM).toInt().coerceAtLeast(minLag + 1)

        val clampedMaxLag = min(maxLag, envelope.size / 2)

        val mean = envelope.average().toFloat()
        val variance = envelope.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
        if (variance == 0f) {
            return AutocorrResult(0.0, 0.0, 0.0, emptyList())
        }

        val ac = FloatArray(clampedMaxLag + 1)
        for (lag in 0..clampedMaxLag) {
            var sum = 0.0
            for (i in 0 until envelope.size - lag) {
                sum += (envelope[i] - mean) * (envelope[i + lag] - mean)
            }
            ac[lag] = (sum / variance).toFloat()
        }

        var bestLag = minLag
        var bestVal = ac[minLag]
        for (lag in minLag..clampedMaxLag) {
            if (ac[lag] > bestVal) {
                bestVal = ac[lag]
                bestLag = lag
            }
        }

        var detectedBpm = if (bestLag > 0) framesPerSec * 60.0 / bestLag else 0.0

        // Half/double tempo disambiguation
        detectedBpm = disambiguateTempo(detectedBpm, ac, framesPerSec, minLag, clampedMaxLag)

        val confidence = bestVal.toDouble().coerceIn(0.0, 1.0)

        // Compute stability: variance of intervals between detected beats
        val stability = computeTempoStability(detectedBpm, framesPerSec, envelope)

        // Detect onsets for beatgrid phase alignment
        val onsets = detectOnsets(envelope)

        return AutocorrResult(detectedBpm, confidence, stability, onsets)
    }

    private fun disambiguateTempo(
        bpm: Double,
        ac: FloatArray,
        framesPerSec: Double,
        minLag: Int,
        maxLag: Int
    ): Double {
        if (bpm <= 0) return bpm

        // Check quarter-tempo, half-tempo, double-tempo candidates
        val candidates = listOf(bpm / 2.0, bpm, bpm * 2.0)
        var bestBpm = bpm
        var bestScore = -1.0

        for (candidate in candidates) {
            if (candidate < MIN_BPM || candidate > MAX_BPM) continue
            val lag = (framesPerSec * 60.0 / candidate).toInt()
            if (lag < minLag || lag > maxLag) continue

            // Score: autocorrelation value with small harmonic support
            var score = ac[lag].toDouble()
            // Check for sub-harmonic support
            if (lag * 2 <= maxLag) {
                score += ac[lag * 2] * 0.3
            }
            if (lag / 2 >= minLag) {
                score += ac[lag / 2] * 0.3
            }

            if (score > bestScore) {
                bestScore = score
                bestBpm = candidate
            }
        }

        return bestBpm
    }

    private fun computeTempoStability(bpm: Double, framesPerSec: Double, envelope: FloatArray): Double {
        if (bpm <= 0 || envelope.isEmpty()) return 0.0

        val expectedLag = framesPerSec * 60.0 / bpm
        val onsets = detectOnsets(envelope)
        if (onsets.size < 4) return 0.0

        val intervals = mutableListOf<Double>()
        for (i in 1 until onsets.size) {
            intervals.add((onsets[i] - onsets[i - 1]).toDouble())
        }

        if (intervals.isEmpty()) return 0.0

        val meanInterval = intervals.average()
        val variance = intervals.sumOf { (it - meanInterval) * (it - meanInterval) } / intervals.size
        val stdDev = sqrt(variance)
        val stability = 1.0 - (stdDev / meanInterval).coerceIn(0.0, 1.0)
        return stability.coerceIn(0.0, 1.0)
    }

    private fun detectOnsets(envelope: FloatArray): List<Int> {
        if (envelope.size < 3) return emptyList()

        val onsets = mutableListOf<Int>()
        val threshold = computeOnsetThreshold(envelope)

        var prevDiff = 0.0
        for (i in 2 until envelope.size) {
            val diff = envelope[i].toDouble() - envelope[i - 1]
            if (diff > threshold && diff > prevDiff && envelope[i] > envelope.average() * 0.5) {
                // Local peak check
                if (i + 1 >= envelope.size || envelope[i] >= envelope[i - 1]) {
                    onsets.add(i)
                }
            }
            prevDiff = diff
        }

        return onsets
    }

    private fun computeOnsetThreshold(envelope: FloatArray): Double {
        val sorted = envelope.sortedArray()
        val medianIdx = sorted.size / 2
        val median = sorted[medianIdx].toDouble()
        return median * 0.3
    }

    private fun buildBeatgrid(
        bpm: Double,
        totalDuration: Double,
        onsets: List<Int>,
        envelope: FloatArray,
        sampleRate: Int
    ): List<Double> {
        if (bpm <= 0 || totalDuration <= 0) return emptyList()

        val framesPerSec = sampleRate.toDouble() / HOP_SIZE
        val beatInterval = 60.0 / bpm

        // Find best phase offset using onset alignment
        val phaseOffset = if (onsets.isNotEmpty()) {
            findBestPhase(onsets, framesPerSec, beatInterval)
        } else {
            0.0
        }

        val beats = mutableListOf<Double>()
        var t = phaseOffset
        while (t < totalDuration) {
            if (t >= 0.0) {
                beats.add(t)
            }
            t += beatInterval
        }

        return beats
    }

    private fun findBestPhase(onsets: List<Int>, framesPerSec: Double, beatInterval: Double): Double {
        if (onsets.isEmpty()) return 0.0

        var bestPhase = 0.0
        var bestScore = -1.0

        // Try phases aligned to each onset
        val maxPhases = min(onsets.size, 50)
        for (i in 0 until maxPhases) {
            val phase = onsets[i].toDouble() / framesPerSec
            var score = 0.0
            for (onset in onsets) {
                val onsetTime = onset.toDouble() / framesPerSec
                val offset = (onsetTime - phase) % beatInterval
                val dist = min(offset, beatInterval - offset)
                if (dist < beatInterval * 0.15) {
                    score += 1.0
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestPhase = phase % beatInterval
            }
        }

        return bestPhase
    }
}
