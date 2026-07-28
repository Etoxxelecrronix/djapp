package com.djapp.analysis.parsers

import com.djapp.analysis.PCMData

object WavParser {

    fun parseWAV(bytes: ByteArray): PCMData? {
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null

        var audioFormat = 0
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = 0
        var dataSize = 0
        var pos = 12

        while (pos + 8 <= bytes.size) {
            val chunkId = readUint32LE(bytes, pos)
            val chunkSize = readUint32LE(bytes, pos + 4)
            pos += 8

            when (chunkId) {
                0x666D7420 -> { // "fmt "
                    if (pos + 16 > bytes.size) return null
                    audioFormat = readUint16LE(bytes, pos).toInt()
                    numChannels = readUint16LE(bytes, pos + 2).toInt()
                    sampleRate = readUint32LE(bytes, pos + 4).toInt()
                    bitsPerSample = readUint16LE(bytes, pos + 14).toInt()
                    pos += chunkSize
                }
                0x64617461 -> { // "data"
                    dataOffset = pos
                    dataSize = chunkSize.toInt()
                    break
                }
                else -> {
                    pos += chunkSize.toInt()
                }
            }
        }

        if (audioFormat != 1 && audioFormat != 3) return null // PCM or IEEE float
        if (dataOffset == 0) return null

        val bytesPerSample = bitsPerSample / 8
        val totalSamplesPerChannel = dataSize / (bytesPerSample * numChannels)
        val samples = FloatArray(totalSamplesPerChannel)
        var maxVal = 1.0f

        for (i in 0 until totalSamplesPerChannel) {
            var sum = 0.0f
            for (ch in 0 until numChannels) {
                val offset = dataOffset + (i * numChannels + ch) * bytesPerSample
                if (offset + bytesPerSample > bytes.size) break

                val sample = when (bitsPerSample) {
                    8 -> {
                        (bytes[offset].toInt() and 0xFF).toShort().toFloat() / 128.0f - 1.0f
                    }
                    16 -> {
                        val lo = bytes[offset].toInt() and 0xFF
                        val hi = bytes[offset + 1].toInt()
                        ((hi shl 8) or lo).toShort().toFloat() / 32768.0f
                    }
                    24 -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt()
                        val raw = (b2 shl 16) or (b1 shl 8) or b0
                        val adjusted = if (raw >= 0x800000) raw - 0x1000000 else raw
                        adjusted.toFloat() / 8388608.0f
                    }
                    32 -> {
                        if (audioFormat == 3) {
                            // IEEE float
                            val raw = (bytes[offset].toInt() and 0xFF) or
                                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                            Float.fromBits(raw)
                        } else {
                            // 32-bit PCM
                            val lo = (bytes[offset].toInt() and 0xFF) or
                                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                    ((bytes[offset + 2].toInt() and 0xFF) shl 16)
                            val hi = bytes[offset + 3].toInt()
                            val raw = (hi shl 24) or lo
                            raw.toFloat() / 2147483648.0f
                        }
                    }
                    else -> 0.0f
                }
                sum += sample
            }
            val mono = sum / numChannels
            samples[i] = mono
            val absVal = kotlin.math.abs(mono)
            if (absVal > maxVal) maxVal = absVal
        }

        // Normalize if clipping
        if (maxVal > 1.0f) {
            for (i in samples.indices) {
                samples[i] /= maxVal
            }
        }

        val duration = totalSamplesPerChannel.toDouble() / sampleRate

        return PCMData(
            samples = samples,
            sampleRate = sampleRate,
            channels = numChannels,
            duration = duration
        )
    }

    private fun readUint16LE(bytes: ByteArray, offset: Int): Short {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset + 1].toInt() shl 8) or (bytes[offset].toInt() and 0xFF)).toShort()
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
