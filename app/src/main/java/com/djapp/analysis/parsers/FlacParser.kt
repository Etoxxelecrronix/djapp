package com.djapp.analysis.parsers

import com.djapp.analysis.FLACMeta

object FlacParser {

    fun parseFLAC(bytes: ByteArray): FLACMeta? {
        if (bytes.size < 42) return null
        if (bytes[0] != 'f'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'A'.code.toByte() || bytes[3] != 'C'.code.toByte()
        ) return null

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var totalSamples = 0L
        var bpm: Double? = null
        var key: String? = null

        // Parse STREAMINFO block
        val streamInfoBlock = bytes.copyOfRange(4, minOf(42, bytes.size))
        if (streamInfoBlock.size >= 34) {
            val minBlock = ((streamInfoBlock[2].toInt() and 0xFF) shl 8) or (streamInfoBlock[3].toInt() and 0xFF)
            val maxBlock = ((streamInfoBlock[4].toInt() and 0xFF) shl 8) or (streamInfoBlock[5].toInt() and 0xFF)
            sampleRate = ((streamInfoBlock[10].toInt() and 0xFF) shl 12) or
                    ((streamInfoBlock[11].toInt() and 0xFF) shl 4) or
                    ((streamInfoBlock[12].toInt() and 0xFF) shr 4)
            channels = ((streamInfoBlock[12].toInt() and 0xFF) shr 1) and 0x07
            channels += 1
            bitsPerSample = (((streamInfoBlock[12].toInt() and 0x01) shl 4) or
                    ((streamInfoBlock[13].toInt() and 0xFF) shr 4)) + 1

            totalSamples = ((streamInfoBlock[14].toLong() and 0xFF) shl 24) or
                    ((streamInfoBlock[15].toLong() and 0xFF) shl 16) or
                    ((streamInfoBlock[16].toLong() and 0xFF) shl 8) or
                    (streamInfoBlock[17].toLong() and 0xFF)
        }

        // Scan for VORBIS_COMMENT block
        var pos = 4 + 34 // skip STREAMINFO
        while (pos + 4 < bytes.size) {
            val headerByte = bytes[pos].toInt() and 0xFF
            val blockType = headerByte and 0x7F
            val isLast = (headerByte and 0x80) != 0

            val blockSize = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)

            pos += 4

            if (blockType == 4 && pos + blockSize <= bytes.size) {
                // VORBIS_COMMENT
                parseVorbisComment(bytes, pos, blockSize) { tagKey, tagValue ->
                    when (tagKey.uppercase()) {
                        "BPM", "TBPM" -> {
                            bpm = tagValue.trim().toDoubleOrNull()
                        }
                        "INITIALKEY", "KEY", "TKEY" -> {
                            key = tagValue.trim()
                        }
                    }
                }
            }

            pos += blockSize
            if (isLast) break
        }

        val duration = if (sampleRate > 0) totalSamples.toDouble() / sampleRate else 0.0

        return FLACMeta(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            duration = duration,
            totalSamples = totalSamples,
            bpm = bpm,
            key = key
        )
    }

    private fun parseVorbisComment(bytes: ByteArray, offset: Int, size: Int, onTag: (String, String) -> Unit) {
        if (offset + 4 > bytes.size) return

        val vendorLen = ((bytes[offset].toInt() and 0xFF)) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        var pos = offset + 4 + vendorLen
        if (pos + 4 > offset + size) return

        val numComments = ((bytes[pos].toInt() and 0xFF)) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4

        for (i in 0 until numComments) {
            if (pos + 4 > offset + size) break
            val commentLen = ((bytes[pos].toInt() and 0xFF)) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4

            if (pos + commentLen > offset + size) break
            val comment = String(bytes, pos, commentLen, Charsets.UTF_8)
            val eqIdx = comment.indexOf('=')
            if (eqIdx > 0) {
                val key = comment.substring(0, eqIdx)
                val value = comment.substring(eqIdx + 1)
                onTag(key, value)
            }
            pos += commentLen
        }
    }
}
