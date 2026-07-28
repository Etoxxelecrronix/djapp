package com.djapp.analysis.parsers

import com.djapp.analysis.PCMData
import com.djapp.analysis.parsers.ParserUtils.readUint16BE
import com.djapp.analysis.parsers.ParserUtils.readUint32BE
import kotlin.math.pow

object AiffParser {

    fun parseAIFF(bytes: ByteArray): PCMData? {
        if (bytes.size < 12) return null
        if (bytes[0] != 'F'.code.toByte() || bytes[1] != 'O'.code.toByte() ||
            bytes[2] != 'R'.code.toByte() || bytes[3] != 'M'.code.toByte()
        ) return null

        val formType = readFourCC(bytes, 8)
        if (formType != "AIFF" && formType != "AIFC") return null

        var numChannels = 0
        var numFrames = 0
        var bitsPerSample = 0
        var sampleRate = 0.0
        var dataOffset = 0
        var dataSize = 0
        var pos = 12

        while (pos + 8 <= bytes.size) {
            val chunkId = readFourCC(bytes, pos)
            val chunkSize = readUint32BE(bytes, pos + 4)
            pos += 8

            when (chunkId) {
                "COMM" -> {
                    if (pos + 18 > bytes.size) return null
                    numChannels = readUint16BE(bytes, pos).toInt()
                    numFrames = readUint32BE(bytes, pos + 2).toInt()
                    bitsPerSample = readUint16BE(bytes, pos + 6).toInt()
                    sampleRate = readExtended80(bytes, pos + 8)
                    pos += chunkSize.toInt()
                }
                "SSND" -> {
                    if (pos + 8 > bytes.size) return null
                    readUint32BE(bytes, pos)
                    readUint32BE(bytes, pos + 4)
                    dataOffset = pos + 8
                    dataSize = chunkSize.toInt() - 8
                    break
                }
                else -> {
                    pos += chunkSize.toInt()
                }
            }
        }

        if (dataOffset == 0 || numChannels == 0 || bitsPerSample == 0 || sampleRate <= 0) return null

        val sampleRateInt = sampleRate.toInt()
        val bytesPerSample = bitsPerSample / 8
        val samples = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            var sum = 0.0f
            for (ch in 0 until numChannels) {
                val offset = dataOffset + (i * numChannels + ch) * bytesPerSample
                if (offset + bytesPerSample > bytes.size) break

                val sample = when (bitsPerSample) {
                    16 -> {
                        val raw = (bytes[offset].toInt() shl 8) or (bytes[offset + 1].toInt() and 0xFF)
                        raw.toShort().toFloat() / 32768.0f
                    }
                    24 -> {
                        val b0 = bytes[offset].toInt()
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt() and 0xFF
                        val raw = (b0 shl 16) or (b1 shl 8) or b2
                        val adjusted = if (raw >= 0x800000) raw - 0x1000000 else raw
                        adjusted.toFloat() / 8388608.0f
                    }
                    32 -> {
                        val raw = (bytes[offset].toInt() shl 24) or
                                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                                (bytes[offset + 3].toInt() and 0xFF)
                        raw.toFloat() / 2147483648.0f
                    }
                    else -> 0.0f
                }
                sum += sample
            }
            samples[i] = sum / numChannels
        }

        val duration = numFrames.toDouble() / sampleRateInt

        return PCMData(
            samples = samples,
            sampleRate = sampleRateInt,
            channels = numChannels,
            duration = duration
        )
    }

    private fun readFourCC(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    private fun readExtended80(bytes: ByteArray, offset: Int): Double {
        if (offset + 10 > bytes.size) return 0.0

        val sign = if ((bytes[offset].toInt() and 0x80) != 0) -1 else 1
        val exponent = ((bytes[offset].toInt() and 0x7F) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        val mantissa = (bytes[offset + 2].toLong() shl 48) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 40) or
                ((bytes[offset + 4].toLong() and 0xFF) shl 32) or
                ((bytes[offset + 5].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 6].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 7].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 8].toLong() and 0xFF) shl 4) or
                ((bytes[offset + 9].toLong() and 0xFF) shr 4)

        val value = mantissa.toDouble() / (1L shl 48)
        return sign * value * 2.0.pow(exponent - 16383)
    }
}
