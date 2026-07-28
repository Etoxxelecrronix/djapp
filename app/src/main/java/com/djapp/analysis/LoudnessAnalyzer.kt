package com.djapp.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class LoudnessResult(
    val lufs: Double,
    val rms: Double,
    val peak: Double
)

object LoudnessAnalyzer {

    private const val BLOCK_SIZE = 400 // ~400ms blocks at 1kHz effective
    private const val OVERLAP_RATIO = 0.75
    private const val ABSOLUTE_GATE_LUFS = -70.0
    private const val RELATIVE_GATE_DB = -10.0

    fun computeLoudness(samples: FloatArray, sampleRate: Int): LoudnessResult {
        if (samples.isEmpty()) {
            return LoudnessResult(-70.0, -70.0, -70.0)
        }

        // K-weighting pre-filter (high-pass approximation of ITU-R BS.1770)
        val weighted = applyKWeighting(samples, sampleRate)

        // Compute block size in samples
        val blockSize = max((sampleRate * 0.4).toInt(), BLOCK_SIZE)
        val hopSize = (blockSize * (1.0 - OVERLAP_RATIO)).toInt().coerceAtLeast(1)
        val numBlocks = max(1, (weighted.size - blockSize) / hopSize + 1)

        // Measure LUFS per block
        val blockLoudness = DoubleArray(numBlocks)
        var blockCount = 0

        for (b in 0 until numBlocks) {
            val offset = b * hopSize
            var sumSq = 0.0
            val end = minOf(offset + blockSize, weighted.size)
            val samplesInBlock = end - offset

            for (i in offset until end) {
                sumSq += weighted[i].toDouble() * weighted[i]
            }
            val meanSquare = sumSq / samplesInBlock
            // LUFS = -0.691 + 10 * log10(meanSquare), with reference
            val lufs = if (meanSquare > 0) -0.691 + 10.0 * log10(meanSquare) else -70.0
            blockLoudness[b] = lufs
            if (lufs > ABSOLUTE_GATE_LUFS) {
                blockCount++
            }
        }

        // Absolute gate: only consider blocks above -70 LUFS
        val gatedBlocks = blockLoudness.filter { it > ABSOLUTE_GATE_LUFS }
        if (gatedBlocks.isEmpty()) {
            return LoudnessResult(-70.0, computeRMS(samples), computePeak(samples))
        }

        // Relative gate: compute mean, then gate at -10dB below mean
        val meanLufs = gatedBlocks.average()
        val relativeThreshold = meanLufs + RELATIVE_GATE_DB
        val relativeGatedBlocks = gatedBlocks.filter { it > relativeThreshold }

        val finalLufs = if (relativeGatedBlocks.isNotEmpty()) {
            val avgMeanSquare = relativeGatedBlocks.sumOf {
                10.0.pow((it + 0.691) / 10.0)
            } / relativeGatedBlocks.size
            -0.691 + 10.0 * log10(avgMeanSquare)
        } else {
            meanLufs
        }

        return LoudnessResult(
            lufs = finalLufs,
            rms = computeRMS(samples),
            peak = computePeak(samples)
        )
    }

    private fun applyKWeighting(samples: FloatArray, sampleRate: Int): FloatArray {
        // Simplified K-weighting: 2nd-order high-pass filter + shelving filter
        // Pre-filter: high-pass at ~38Hz (2nd order Butterworth approximation)
        val hpB = doubleArrayOf(0.9689, -1.9378, 0.9689)
        val hpA = doubleArrayOf(1.0, -1.9339, 0.9418)

        val filtered = FloatArray(samples.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        for (i in samples.indices) {
            val x0 = samples[i].toDouble()
            val y0 = hpB[0] * x0 + hpB[1] * x1 + hpB[2] * x2 - hpA[1] * y1 - hpA[2] * y2
            filtered[i] = y0.toFloat()
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }

        // Shelving filter boost above ~2.5kHz (+3dB approx)
        val shB = doubleArrayOf(1.535, -2.606, 1.071)
        val shA = doubleArrayOf(1.0, -1.519, 0.595)

        val result = FloatArray(samples.size)
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0

        for (i in filtered.indices) {
            val x0 = filtered[i].toDouble()
            val y0 = shB[0] * x0 + shB[1] * x1 + shB[2] * x2 - shA[1] * y1 - shA[2] * y2
            result[i] = y0.toFloat()
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }

        return result
    }

    private fun computeRMS(samples: FloatArray): Double {
        if (samples.isEmpty()) return -70.0
        var sumSq = 0.0
        for (s in samples) {
            sumSq += s.toDouble() * s
        }
        val rms = sqrt(sumSq / samples.size)
        return if (rms > 0) 20.0 * log10(rms) else -70.0
    }

    private fun computePeak(samples: FloatArray): Double {
        if (samples.isEmpty()) return -70.0
        var peak = 0.0
        for (s in samples) {
            val absVal = abs(s.toDouble())
            if (absVal > peak) peak = absVal
        }
        return if (peak > 0) 20.0 * log10(peak) else -70.0
    }

    private fun log10(x: Double): Double = kotlin.math.log10(x)
}
