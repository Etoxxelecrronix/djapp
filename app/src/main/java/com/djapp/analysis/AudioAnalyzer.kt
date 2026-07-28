package com.djapp.analysis

import android.content.Context
import android.net.Uri
import com.djapp.analysis.parsers.AiffParser
import com.djapp.analysis.parsers.FlacParser
import com.djapp.analysis.parsers.Mp3Parser
import com.djapp.analysis.parsers.WavParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

enum class AnalysisSource {
    PCM_WAV,
    PCM_AIFF,
    METADATA_ONLY,
    ESTIMATED
}

data class AnalysisResult(
    val uri: String,
    val filename: String,
    val duration: Double,
    val sampleRate: Int,
    val channels: Int,
    val format: String,
    val bpm: Double?,
    val bpmConfidence: Double?,
    val tempoStability: Double?,
    val beatgrid: List<Double>,
    val downbeats: List<Double>,
    val musicalKey: String?,
    val camelotKey: String?,
    val openKey: String?,
    val keyConfidence: Double?,
    val lufs: Double?,
    val rms: Double?,
    val peak: Double?,
    val waveform: List<Float>,
    val analysisSource: AnalysisSource,
    val timestamp: Long = System.currentTimeMillis()
)

object AudioAnalyzer {

    private const val MAX_FILE_SIZE = 20 * 1024 * 1024 // 20MB

    suspend fun analyzeAudioFile(
        context: Context,
        uri: Uri,
        filename: String,
        onProgress: ((Float) -> Unit)? = null
    ): AnalysisResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(0.05f)

        val bytes = readBytesFromUri(context, uri)
        if (bytes == null || bytes.isEmpty()) {
            return@withContext createEmptyResult(uri, filename, "unknown")
        }

        val extension = filename.substringAfterLast('.', "").lowercase()
        onProgress?.invoke(0.15f)

        when (extension) {
            "wav" -> analyzeWav(uri, filename, bytes, onProgress)
            "aiff", "aif" -> analyzeAiff(uri, filename, bytes, onProgress)
            "mp3" -> analyzeMp3(uri, filename, bytes, onProgress)
            "flac" -> analyzeFlac(uri, filename, bytes, onProgress)
            else -> createEmptyResult(uri, filename, extension)
        }
    }

    private fun analyzeWav(uri: Uri, filename: String, bytes: ByteArray, onProgress: ((Float) -> Unit)?): AnalysisResult {
        val pcm = WavParser.parseWAV(bytes) ?: return createEmptyResult(uri, filename, "wav")
        return analyzePCM(uri, filename, pcm, "wav", onProgress)
    }

    private fun analyzeAiff(uri: Uri, filename: String, bytes: ByteArray, onProgress: ((Float) -> Unit)?): AnalysisResult {
        val pcm = AiffParser.parseAIFF(bytes) ?: return createEmptyResult(uri, filename, "aiff")
        return analyzePCM(uri, filename, pcm, "aiff", onProgress)
    }

    private fun analyzePCM(uri: Uri, filename: String, pcm: PCMData, format: String, onProgress: ((Float) -> Unit)?): AnalysisResult {
        onProgress?.invoke(0.3f)

        val bpmResult = BpmDetector.detectBPM(pcm.samples, pcm.sampleRate, pcm.duration)
        onProgress?.invoke(0.5f)

        val chroma = KeyDetector.computeChroma(pcm.samples, pcm.sampleRate)
        val keyResult = KeyDetector.detectKey(chroma)
        onProgress?.invoke(0.7f)

        val loudnessResult = LoudnessAnalyzer.computeLoudness(pcm.samples, pcm.sampleRate)
        onProgress?.invoke(0.85f)

        val waveform = WaveformGenerator.computeWaveform(pcm.samples, 200)
        onProgress?.invoke(1.0f)

        val source = when (format) {
            "wav" -> AnalysisSource.PCM_WAV
            "aiff", "aif" -> AnalysisSource.PCM_AIFF
            else -> AnalysisSource.ESTIMATED
        }

        return AnalysisResult(
            uri = uri.toString(),
            filename = filename,
            duration = pcm.duration,
            sampleRate = pcm.sampleRate,
            channels = pcm.channels,
            format = format,
            bpm = bpmResult.bpm,
            bpmConfidence = bpmResult.bpmConfidence,
            tempoStability = bpmResult.tempoStability,
            beatgrid = bpmResult.beatgrid,
            downbeats = bpmResult.downbeats,
            musicalKey = keyResult.musicalKey,
            camelotKey = keyResult.camelotKey,
            openKey = keyResult.openKey,
            keyConfidence = keyResult.keyConfidence,
            lufs = loudnessResult.lufs,
            rms = loudnessResult.rms,
            peak = loudnessResult.peak,
            waveform = waveform,
            analysisSource = source
        )
    }

    private fun analyzeMp3(uri: Uri, filename: String, bytes: ByteArray, onProgress: ((Float) -> Unit)?): AnalysisResult {
        val mp3Info = Mp3Parser.parseMP3Frames(bytes)
        val id3Meta = Mp3Parser.parseID3(bytes)
        onProgress?.invoke(0.3f)

        val waveform = WaveformGenerator.compressedWaveform(bytes, mp3Info.dataOffset, 200)
        onProgress?.invoke(1.0f)

        return AnalysisResult(
            uri = uri.toString(),
            filename = filename,
            duration = mp3Info.duration,
            sampleRate = mp3Info.sampleRate,
            channels = mp3Info.channels,
            format = "mp3",
            bpm = id3Meta.bpm,
            bpmConfidence = if (id3Meta.bpm != null) 0.8 else null,
            tempoStability = null,
            beatgrid = emptyList(),
            downbeats = emptyList(),
            musicalKey = id3Meta.key,
            camelotKey = null,
            openKey = null,
            keyConfidence = null,
            lufs = null,
            rms = null,
            peak = null,
            waveform = waveform,
            analysisSource = AnalysisSource.METADATA_ONLY
        )
    }

    private fun analyzeFlac(uri: Uri, filename: String, bytes: ByteArray, onProgress: ((Float) -> Unit)?): AnalysisResult {
        val flacMeta = FlacParser.parseFLAC(bytes) ?: return createEmptyResult(uri, filename, "flac")
        onProgress?.invoke(0.3f)

        val waveform = WaveformGenerator.computeWaveform(
            FloatArray(200) { ((it.toFloat() / 200) * 2 - 1) * 0.5f }, // placeholder for compressed
            200
        )
        onProgress?.invoke(1.0f)

        return AnalysisResult(
            uri = uri.toString(),
            filename = filename,
            duration = flacMeta.duration,
            sampleRate = flacMeta.sampleRate,
            channels = flacMeta.channels,
            format = "flac",
            bpm = flacMeta.bpm,
            bpmConfidence = if (flacMeta.bpm != null) 0.8 else null,
            tempoStability = null,
            beatgrid = emptyList(),
            downbeats = emptyList(),
            musicalKey = flacMeta.key,
            camelotKey = null,
            openKey = null,
            keyConfidence = null,
            lufs = null,
            rms = null,
            peak = null,
            waveform = waveform,
            analysisSource = AnalysisSource.METADATA_ONLY
        )
    }

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        // Handle file:// URIs directly via File API (contentResolver can't open these)
        if (uri.scheme == "file") {
            return try {
                val path = uri.path ?: return null
                val file = java.io.File(path)
                if (!file.exists() || file.length() > MAX_FILE_SIZE) return null
                file.inputStream().use { readLimited(it, MAX_FILE_SIZE) }
            } catch (e: Exception) {
                null
            }
        }
        // Handle content:// URIs via contentResolver
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readLimited(stream, MAX_FILE_SIZE)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readLimited(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        while (totalRead < maxBytes) {
            val read = stream.read(buffer, totalRead, maxBytes - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer.copyOf(totalRead)
    }

    private fun createEmptyResult(uri: Uri, filename: String, format: String): AnalysisResult {
        return AnalysisResult(
            uri = uri.toString(),
            filename = filename,
            duration = 0.0,
            sampleRate = 0,
            channels = 0,
            format = format,
            bpm = null,
            bpmConfidence = null,
            tempoStability = null,
            beatgrid = emptyList(),
            downbeats = emptyList(),
            musicalKey = null,
            camelotKey = null,
            openKey = null,
            keyConfidence = null,
            lufs = null,
            rms = null,
            peak = null,
            waveform = emptyList(),
            analysisSource = AnalysisSource.ESTIMATED
        )
    }
}
