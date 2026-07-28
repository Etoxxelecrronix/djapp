package com.djapp.analysis

data class PCMData(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int,
    val duration: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PCMData) return false
        return samples.contentEquals(other.samples) &&
                sampleRate == other.sampleRate &&
                channels == other.channels &&
                duration == other.duration
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + duration.hashCode()
        return result
    }
}

data class MP3Info(
    val sampleRate: Int,
    val channels: Int,
    val duration: Double,
    val dataOffset: Int
)

data class AudioMeta(
    val bpm: Double? = null,
    val key: String? = null
)

data class FLACMeta(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val duration: Double,
    val totalSamples: Long,
    val bpm: Double? = null,
    val key: String? = null
)
