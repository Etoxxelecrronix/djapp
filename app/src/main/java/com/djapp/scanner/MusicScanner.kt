package com.djapp.scanner

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

val MUSIC_EXTENSIONS = setOf("mp3", "flac", "aif", "aiff", "wav", "ogg", "m4a", "alac", "aac")

data class ScanTrack(
    val name: String,
    val path: String,
    val folder: String,
    val folderPath: String,
    val extension: String,
    var isNew: Boolean = false,
)

data class FolderStat(
    val name: String,
    val path: String,
    var trackCount: Int = 0,
    var newCount: Int = 0,
    val tracks: MutableList<ScanTrack> = mutableListOf(),
)

enum class ScanPhase { SCANNING, DONE }

data class ScanProgress(
    val scanned: Int,
    val found: Int,
    val currentPath: String,
    val phase: ScanPhase,
)

data class ScanResult(
    val folders: List<FolderStat>,
    val totalTracks: Int,
    val newTrackCount: Int,
    val removedTrackCount: Int,
    val durationMs: Long,
)

private const val MAX_DEPTH = 10
private const val CACHE_DIR = "music_scanner_cache"
private const val MAX_CACHED_ROOTS = 5

data class CacheEntry(
    val rootPath: String,
    val tracks: List<ScanTrack>,
    val scannedAt: String,
)

object MusicScanner {

    private var lastYieldTime = 0L

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cacheFile(context: Context, rootPath: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(rootPath.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir(context), "$hash.json")
    }

    private suspend fun maybeYield() {
        val now = System.currentTimeMillis()
        if (now - lastYieldTime >= 14) {
            kotlinx.coroutines.yield()
            lastYieldTime = now
        }
    }

    suspend fun scanMusicLibrary(
        context: Context,
        rootPath: String,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        lastYieldTime = startMs

        val tracks = mutableListOf<ScanTrack>()
        val folderStats = mutableMapOf<String, FolderStat>()
        val scannedCount = intArrayOf(0)

        scanFileDir(
            dirPath = File(rootPath),
            depth = 0,
            tracks = tracks,
            folderStats = folderStats,
            scannedCount = scannedCount,
            onProgress = onProgress,
        )

        // Detect changes vs cache
        val cache = loadCache(context)
        val prior = cache.find { it.rootPath == rootPath }
        val cachedPaths = prior?.tracks?.map { it.path }?.toSet() ?: emptySet()
        val currentPaths = tracks.map { it.path }.toSet()

        var newTrackCount = 0
        for (track in tracks) {
            if (track.path !in cachedPaths) {
                track.isNew = true
                newTrackCount++
            }
        }
        val removedTrackCount = cachedPaths.count { it !in currentPaths }

        // Propagate isNew to folders
        for ((_, stat) in folderStats) {
            stat.newCount = stat.tracks.count { it.isNew }
            stat.trackCount = stat.tracks.size
        }

        // Persist cache
        saveCache(context, rootPath, tracks)

        onProgress(ScanProgress(scannedCount[0], tracks.size, "", ScanPhase.DONE))

        ScanResult(
            folders = folderStats.values.sortedByDescending { it.trackCount },
            totalTracks = tracks.size,
            newTrackCount = newTrackCount,
            removedTrackCount = removedTrackCount,
            durationMs = System.currentTimeMillis() - startMs,
        )
    }

    private suspend fun scanFileDir(
        dirPath: File,
        depth: Int,
        tracks: MutableList<ScanTrack>,
        folderStats: MutableMap<String, FolderStat>,
        scannedCount: IntArray,
        onProgress: (ScanProgress) -> Unit,
    ) {
        if (depth > MAX_DEPTH || !dirPath.exists() || !dirPath.canRead()) return

        coroutineContext.ensureActive()
        scannedCount[0]++

        val folderName = dirPath.name
        val musicFiles = mutableListOf<File>()
        val subDirs = mutableListOf<File>()

        dirPath.listFiles()?.forEach { entry ->
            if (entry.name.startsWith(".")) return@forEach
            if (entry.isFile) {
                val ext = entry.extension.lowercase()
                if (ext in MUSIC_EXTENSIONS) {
                    musicFiles.add(entry)
                }
            } else if (entry.isDirectory) {
                subDirs.add(entry)
            }
        }

        if (musicFiles.isNotEmpty()) {
            val stat = folderStats.getOrPut(dirPath.absolutePath) {
                FolderStat(name = folderName, path = dirPath.absolutePath)
            }
            for (file in musicFiles) {
                val track = ScanTrack(
                    name = file.name,
                    path = file.absolutePath,
                    folder = folderName,
                    folderPath = dirPath.absolutePath,
                    extension = file.extension.lowercase(),
                )
                tracks.add(track)
                stat.tracks.add(track)
            }
            stat.trackCount = stat.tracks.size
            onProgress(ScanProgress(scannedCount[0], tracks.size, folderName, ScanPhase.SCANNING))
            maybeYield()
        }

        for (sub in subDirs) {
            scanFileDir(sub, depth + 1, tracks, folderStats, scannedCount, onProgress)
        }
    }

    private fun loadCache(context: Context): List<CacheEntry> {
        val type = object : TypeToken<CacheEntry>() {}.type
        return cacheDir(context).listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    Gson().fromJson<CacheEntry>(file.readText(), type)
                } catch (_: Exception) {
                    file.delete()
                    null
                }
            }
            ?.sortedByDescending { it.scannedAt }
            .orEmpty()
    }

    private fun saveCache(context: Context, rootPath: String, tracks: List<ScanTrack>) {
        try {
            val entry = CacheEntry(
                rootPath = rootPath,
                tracks = tracks,
                scannedAt = java.time.Instant.now().toString(),
            )
            cacheFile(context, rootPath).writeText(Gson().toJson(entry))

            // Prune to keep only the last N root paths
            val allFiles = cacheDir(context).listFiles()
                ?.filter { it.extension == "json" }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            if (allFiles.size > MAX_CACHED_ROOTS) {
                allFiles.drop(MAX_CACHED_ROOTS).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w("MusicScanner", "cache prune failed", e)
        }
    }

    suspend fun clearScanCache(context: Context, rootPath: String? = null) {
        if (rootPath != null) {
            cacheFile(context, rootPath).delete()
        } else {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
