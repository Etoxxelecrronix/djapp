package com.djapp.analysis

import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

data class KeyResult(
    val musicalKey: String,
    val camelotKey: String,
    val openKey: String,
    val keyConfidence: Double
)

object KeyDetector {

    val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    private val KK_MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val KK_MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    // Camelot wheel: index 0=C major -> 8B, 1=C# major -> 3B, etc.
    val CAMELOT_MAP = arrayOf(
        "8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B",
        "8A", "3A", "10A", "5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A"
    )

    // Open Key: index 0=C major -> 1d, 1=C# major -> 8d, etc.
    val OPEN_KEY_MAP = arrayOf(
        "1d", "8d", "3d", "10d", "5d", "12d", "7d", "2d", "9d", "4d", "11d", "6d",
        "1m", "8m", "3m", "10m", "5m", "12m", "7m", "2m", "9m", "4m", "11m", "6m"
    )

    private const val A4_FREQ = 440.0
    private const val FFT_SIZE = 16384

    fun computeChroma(samples: FloatArray, sampleRate: Int): FloatArray {
        val chroma = FloatArray(12)

        val fftSize = minOf(FFT_SIZE, nextPow2(samples.size))
        val numFrames = if (samples.size <= fftSize) 1 else (samples.size - fftSize) / (fftSize / 2) + 1

        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)

        for (frame in 0 until numFrames) {
            val offset = if (numFrames == 1) 0 else frame * (fftSize / 2)
            val len = minOf(fftSize, samples.size - offset)

            for (i in 0 until len) {
                re[i] = samples[offset + i].toDouble() * hanning(i, len)
            }
            for (i in len until fftSize) {
                re[i] = 0.0
                im[i] = 0.0
            }

            fft(re, im)

            val binCount = fftSize / 2
            for (k in 1 until binCount) {
                val freq = k.toDouble() * sampleRate / fftSize
                if (freq < 65.0 || freq > 5000.0) continue

                val magnitude = re[k] * re[k] + im[k] * im[k]
                val pitch = 12.0 * log2(freq / A4_FREQ) + 69.0
                val chromaIdx = ((floor(pitch) % 12.0 + 12.0) % 12.0).toInt()
                val frac = pitch - floor(pitch)

                chroma[chromaIdx] += (magnitude * (1.0 - frac)).toFloat()
                chroma[(chromaIdx + 1) % 12] += (magnitude * frac).toFloat()
            }
        }

        val maxVal = chroma.maxOrNull() ?: 1f
        if (maxVal > 0f) {
            for (i in chroma.indices) {
                chroma[i] /= maxVal
            }
        }

        return chroma
    }

    fun detectKey(chroma: FloatArray): KeyResult {
        var bestKey = 0
        var bestCorr = -1.0
        var isMajor = true

        for (shift in 0 until 12) {
            val corrMajor = correlation(chroma, KK_MAJOR, shift)
            val corrMinor = correlation(chroma, KK_MINOR, shift)

            if (corrMajor > bestCorr) {
                bestCorr = corrMajor
                bestKey = shift
                isMajor = true
            }
            if (corrMinor > bestCorr) {
                bestCorr = corrMinor
                bestKey = shift
                isMajor = false
            }
        }

        val keyName = NOTE_NAMES[bestKey] + if (isMajor) " Major" else " Minor"
        val camelotIdx = if (isMajor) bestKey else bestKey + 12
        val openKeyIdx = if (isMajor) bestKey else bestKey + 12
        val camelot = CAMELOT_MAP[camelotIdx]
        val openKey = OPEN_KEY_MAP[openKeyIdx]

        val confidence = (bestCorr / 6.5).coerceIn(0.0, 1.0)

        return KeyResult(
            musicalKey = keyName,
            camelotKey = camelot,
            openKey = openKey,
            keyConfidence = confidence
        )
    }

    private fun correlation(chroma: FloatArray, profile: DoubleArray, shift: Int): Double {
        var sum = 0.0
        for (i in 0 until 12) {
            sum += chroma[(i + shift) % 12] * profile[i]
        }
        return sum
    }

    private fun hanning(i: Int, n: Int): Double {
        return 0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / n))
    }

    private fun nextPow2(n: Int): Int {
        var v = 1
        while (v < n) {
            v = v shl 1
        }
        return v
    }
}
