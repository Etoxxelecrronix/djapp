package com.djapp.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object WaveformGenerator {

    fun computeWaveform(samples: FloatArray, blocks: Int = 200): List<Float> {
        if (samples.isEmpty() || blocks <= 0) return emptyList()

        val blockSize = max(1, samples.size / blocks)
        val result = mutableListOf<Float>()

        for (i in 0 until blocks) {
            val offset = i * blockSize
            val end = min(offset + blockSize, samples.size)
            if (offset >= samples.size) {
                result.add(0f)
                continue
            }

            var sumSq = 0.0
            var peak = 0.0
            for (j in offset until end) {
                val v = samples[j].toDouble()
                sumSq += v * v
                val absV = abs(v)
                if (absV > peak) peak = absV
            }

            val rms = sqrt(sumSq / (end - offset)).toFloat()
            result.add(rms)
        }

        // Normalize to 0-1
        val maxVal = result.maxOrNull() ?: 1f
        if (maxVal > 0f) {
            return result.map { it / maxVal }
        }
        return result
    }

    fun compressedWaveform(bytes: ByteArray, startOffset: Int, blocks: Int = 200): List<Float> {
        if (bytes.isEmpty() || blocks <= 0) return emptyList()

        // Read 16-bit PCM samples from byte array (little-endian)
        val bytesPerSample = 2
        val totalSamples = (bytes.size - startOffset) / bytesPerSample
        if (totalSamples <= 0) return emptyList()

        val blockSize = max(1, totalSamples / blocks)
        val result = mutableListOf<Float>()

        for (b in 0 until blocks) {
            val sampleOffset = startOffset + b * blockSize * bytesPerSample
            val sampleEnd = min(sampleOffset + blockSize * bytesPerSample, bytes.size)
            val actualSamples = (sampleEnd - sampleOffset) / bytesPerSample

            if (actualSamples <= 0) {
                result.add(0f)
                continue
            }

            var sumSq = 0.0
            var peak = 0.0
            var pos = sampleOffset

            while (pos + 1 < sampleEnd && pos + 1 < bytes.size) {
                val lo = bytes[pos].toInt() and 0xFF
                val hi = bytes[pos + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toFloat() / 32768f
                val absV = abs(sample.toDouble())
                sumSq += sample.toDouble() * sample
                if (absV > peak) peak = absV
                pos += bytesPerSample
            }

            val rms = sqrt(sumSq / actualSamples).toFloat()
            result.add(rms)
        }

        val maxVal = result.maxOrNull() ?: 1f
        if (maxVal > 0f) {
            return result.map { it / maxVal }
        }
        return result
    }
}
