package com.djapp.analysis.parsers

import com.djapp.analysis.AudioMeta
import com.djapp.analysis.MP3Info
import com.djapp.analysis.parsers.ParserUtils.readUint32BE

object Mp3Parser {

    private val MPEG_SAMPLE_RATES = arrayOf(
        intArrayOf(11025, 12000, 8000),   // MPEG 2.5
        intArrayOf(0, 0, 0),               // reserved
        intArrayOf(22050, 24000, 16000),   // MPEG 2
        intArrayOf(44100, 48000, 32000)    // MPEG 1
    )

    private val MPEG_BITRATES = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),  // reserved
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0),  // MPEG 1 Layer 3
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),       // MPEG 2 Layer 3
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)        // MPEG 2.5 Layer 3
    )

    fun parseMP3Frames(bytes: ByteArray): MP3Info {
        var sampleRate = 44100
        var channels = 2
        var dataOffset = 0
        var totalDuration = 0.0

        val headerOffset = skipID3v2(bytes)
        dataOffset = headerOffset

        var pos = headerOffset
        var frameCount = 0

        while (pos + 4 <= bytes.size) {
            if (findFrameSync(bytes, pos)) {
                val header = readUint32BE(bytes, pos)
                val info = decodeFrameHeader(header)
                if (info != null) {
                    sampleRate = info.sampleRate
                    channels = info.channels

                    val frameSize = computeFrameSize(info, header)
                    if (frameSize <= 0) break

                    totalDuration += info.frameDuration
                    frameCount++
                    pos += frameSize
                } else {
                    pos++
                }
            } else {
                pos++
            }
        }

        return MP3Info(
            sampleRate = sampleRate,
            channels = channels,
            duration = totalDuration,
            dataOffset = dataOffset
        )
    }

    fun parseID3(bytes: ByteArray): AudioMeta {
        var bpm: Double? = null
        var key: String? = null

        if (bytes.size < 10) return AudioMeta(bpm, key)
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return AudioMeta(bpm, key)
        }

        val version = bytes[3].toInt()
        val size = if (version >= 4) {
            syncsafeInt(bytes, 6)
        } else {
            ((bytes[6].toInt() and 0xFF) shl 24) or
                    ((bytes[7].toInt() and 0xFF) shl 16) or
                    ((bytes[8].toInt() and 0xFF) shl 8) or
                    (bytes[9].toInt() and 0xFF)
        }

        var pos = 10
        val end = minOf(10 + size, bytes.size)

        while (pos + 10 <= end) {
            val frameId = String(bytes, pos, 4, Charsets.US_ASCII)
            val frameSize = if (version >= 4) {
                syncsafeInt(bytes, pos + 4)
            } else {
                ((bytes[pos + 4].toInt() and 0xFF) shl 24) or
                        ((bytes[pos + 5].toInt() and 0xFF) shl 16) or
                        ((bytes[pos + 6].toInt() and 0xFF) shl 8) or
                        (bytes[pos + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || pos + 10 + frameSize > end) break

            val frameData = bytes.copyOfRange(pos + 10, pos + 10 + frameSize)
            val text = decodeTextFrame(frameData)

            when (frameId) {
                "TBPM" -> {
                    bpm = text?.trim()?.toDoubleOrNull()
                }
                "TKEY" -> {
                    key = text?.trim()
                }
            }

            pos += 10 + frameSize
        }

        return AudioMeta(bpm = bpm, key = key)
    }

    private fun skipID3v2(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val size = syncsafeInt(bytes, 6)
        return 10 + size
    }

    private fun syncsafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun findFrameSync(bytes: ByteArray, start: Int): Boolean {
        if (start + 1 >= bytes.size) return false
        return bytes[start].toInt() == 0xFF && (bytes[start + 1].toInt() and 0xE0) == 0xE0
    }

    private data class FrameInfo(
        val version: Int,    // 0=2.5, 2=2, 3=1
        val layer: Int,      // 1=III, 2=II, 3=I
        val bitrateIndex: Int,
        val sampleRateIndex: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitrate: Int,
        val frameDuration: Double
    )

    private fun decodeFrameHeader(header: Int): FrameInfo? {
        if ((header and 0xFFE00000.toInt()) != 0xFFE00000.toInt()) return null

        val versionBits = (header shr 19) and 0x03
        val layerBits = (header shr 17) and 0x03
        val bitrateIndex = (header shr 12) and 0x0F
        val sampleRateIndex = (header shr 10) and 0x03
        val padding = (header shr 9) and 0x01

        if (versionBits == 1 || layerBits == 0) return null
        if (bitrateIndex == 0 || bitrateIndex == 15) return null
        if (sampleRateIndex == 3) return null

        val version = versionBits
        val layer = 4 - layerBits

        if (layer != 3) return null // Only Layer 3

        val srArrayIdx = versionBits
        val sampleRate = MPEG_SAMPLE_RATES[srArrayIdx][sampleRateIndex]
        if (sampleRate == 0) return null

        val brArrayIdx = when {
            version == 3 && layer == 3 -> 1
            version == 2 && layer == 3 -> 2
            version == 0 && layer == 3 -> 3
            else -> 1
        }
        val bitrate = MPEG_BITRATES[brArrayIdx][bitrateIndex]
        if (bitrate == 0) return null

        val channels = if ((header shr 6) and 0x03 == 3) 1 else 2
        val frameSamples = when (version) {
            3 -> 1152
            2 -> 576
            0 -> 576
            else -> 1152
        }

        val frameDuration = frameSamples.toDouble() / sampleRate

        return FrameInfo(version, layer, bitrateIndex, sampleRateIndex, channels, sampleRate, bitrate, frameDuration)
    }

    private fun computeFrameSize(info: FrameInfo, header: Int): Int {
        val padding = (header shr 9) and 0x01
        val frameSize = (144 * info.bitrate * 1000 / info.sampleRate) + padding
        return if (frameSize > 0) frameSize else 0
    }

    private fun decodeTextFrame(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt()
        val textBytes = data.copyOfRange(1, data.size)
        return when (encoding) {
            0 -> String(textBytes, Charsets.ISO_8859_1)
            1 -> String(textBytes, Charsets.UTF_16)
            2 -> String(textBytes, Charsets.UTF_16BE)
            3 -> String(textBytes, Charsets.UTF_8)
            else -> String(textBytes, Charsets.UTF_8)
        }
    }
}
