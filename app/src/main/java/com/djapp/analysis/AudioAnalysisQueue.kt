package com.djapp.analysis

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val filename: String,
    val status: TrackStatus = TrackStatus.QUEUED,
    val progress: Float = 0f,
    val result: AnalysisResult? = null,
    val error: String? = null
)

enum class TrackStatus {
    QUEUED,
    ANALYZING,
    DONE,
    ERROR
}

object AnalysisQueue {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(3)

    private val _queue = MutableStateFlow<Map<String, QueueItem>>(emptyMap())
    val queue: StateFlow<Map<String, QueueItem>> = _queue.asStateFlow()

    private val _results = MutableStateFlow<Map<String, AnalysisResult>>(emptyMap())
    val results: StateFlow<Map<String, AnalysisResult>> = _results.asStateFlow()

    private val queueItems = ConcurrentHashMap<String, QueueItem>()
    private val resultsMap = ConcurrentHashMap<String, AnalysisResult>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun enqueue(tracks: List<Pair<Uri, String>>, context: Context) {
        for ((uri, filename) in tracks) {
            val id = UUID.randomUUID().toString()
            val item = QueueItem(id = id, uri = uri, filename = filename)
            queueItems[id] = item
            _queue.value = HashMap(queueItems)

            val job = scope.launch {
                semaphore.withPermit {
                    processItem(id, context)
                }
            }
            activeJobs[id] = job
        }
    }

    private suspend fun processItem(id: String, context: Context) {
        val item = queueItems[id] ?: return

        queueItems[id] = item.copy(status = TrackStatus.ANALYZING, progress = 0f)
        _queue.value = HashMap(queueItems)

        try {
            val result = AudioAnalyzer.analyzeAudioFile(
                context = context,
                uri = item.uri,
                filename = item.filename,
                onProgress = { progress ->
                    val current = queueItems[id] ?: return@analyzeAudioFile
                    queueItems[id] = current.copy(progress = progress)
                    _queue.value = HashMap(queueItems)
                }
            )

            queueItems[id] = item.copy(
                status = TrackStatus.DONE,
                progress = 1f,
                result = result
            )
            _queue.value = HashMap(queueItems)

            resultsMap[item.uri.toString()] = result
            _results.value = HashMap(resultsMap)

            persistResults()
        } catch (e: Exception) {
            queueItems[id] = item.copy(
                status = TrackStatus.ERROR,
                error = e.message ?: "Unknown error"
            )
            _queue.value = HashMap(queueItems)
        }
    }

    fun clearQueue() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        queueItems.clear()
        _queue.value = emptyMap()
    }

    fun clearResults() {
        resultsMap.clear()
        _results.value = emptyMap()
    }

    fun getResult(uri: Uri): AnalysisResult? {
        return resultsMap[uri.toString()]
    }

    fun getAllResults(): Map<String, AnalysisResult> {
        return HashMap(resultsMap)
    }

    fun isAnalyzed(uri: Uri): Boolean {
        return resultsMap.containsKey(uri.toString())
    }

    private fun persistResults() {
        // Persistence via DataStore would be implemented here
        // For now, results are held in memory
    }
}
