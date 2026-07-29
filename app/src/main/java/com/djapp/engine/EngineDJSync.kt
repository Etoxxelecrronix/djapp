package com.djapp.engine

import android.content.Context
import android.util.Log
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.TrackEntity
import com.djapp.i18n.Strings
import java.io.File
import java.io.FileWriter

data class EngineSyncResult(
    val tracksWritten: Int,
    val playlistsWritten: Int,
    val errors: List<String>,
)

object EngineDJSync {

    private fun fileTypeFromExt(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "mp3"
        "aif", "aiff" -> "aiff"
        "wav" -> "wav"
        "flac" -> "flac"
        "ogg" -> "ogg"
        "mp4", "m4a" -> "m4a"
        "alac" -> "alac"
        else -> ext.lowercase()
    }

    suspend fun syncToEngineDJ(
        context: Context,
        volumePath: String,
        tracks: List<TrackEntity>,
        playlists: List<Pair<PlaylistEntity, List<TrackEntity>>>,
        overwrite: Boolean = false,
    ): EngineSyncResult {
        val db = EngineDJDatabase.openEngineDb(context, volumePath)
            ?: return EngineSyncResult(0, 0, listOf(Strings.t("engine.db_open_error")))

        val volumeRoot = volumePath.trimEnd('/')
        val trackIdMap = mutableMapOf<Long, Long>()
        val errors = mutableListOf<String>()

        try {
            for (track in tracks) {
                try {
                    val relPath = EngineDJDatabase.engineRelativePath(track.path, volumeRoot)
                    val ext = track.filename.substringAfterLast('.', "")
                    val fileType = fileTypeFromExt(ext)
                    val bpmInt = track.bpm?.let { (it * 100).toInt() }

                    val existing = db.rawQuery(
                        "SELECT id FROM Track WHERE path=? OR filename=?",
                        arrayOf(relPath, track.filename)
                    )
                    val existingId = if (existing.moveToFirst()) existing.getLong(0) else null
                    existing.close()

                    if (existingId != null && !overwrite) {
                        trackIdMap[track.id] = existingId
                        continue
                    }

                    if (existingId != null) {
                        db.execSQL(
                            """UPDATE Track SET title=?,artist=?,album=?,genre=?,comment=?,label=?,
                               bpm=?,bpmAnalyzed=?,year=?,fileType=?,isAnalyzed=?,rating=?
                               WHERE id=?""",
                            arrayOf(
                                track.title, track.artist, track.album, track.genre,
                                track.comment, track.label, bpmInt, track.bpmAnalyzed,
                                track.year, fileType, if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5), existingId,
                            )
                        )
                        trackIdMap[track.id] = existingId
                    } else {
                        db.execSQL(
                            """INSERT INTO Track
                               (path, filename, title, artist, album, genre, comment, label,
                                bpm, bpmAnalyzed, year, fileType, isAnalyzed, isAvailable,
                                rating, dateCreated, dateAdded)
                               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,datetime('now'),datetime('now'))""",
                            arrayOf(
                                relPath, track.filename, track.title, track.artist,
                                track.album, track.genre, track.comment, track.label,
                                bpmInt, track.bpmAnalyzed, track.year, fileType,
                                if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5),
                            )
                        )
                        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                        if (cursor.moveToFirst()) {
                            trackIdMap[track.id] = cursor.getLong(0)
                        }
                        cursor.close()
                    }
                } catch (e: Exception) {
                    errors.add("Track ${track.filename}: ${e.message}")
                }
            }

            for ((playlist, plTracks) in playlists) {
                try {
                    val existing = db.rawQuery(
                        "SELECT id FROM Playlist WHERE title=?",
                        arrayOf(playlist.title)
                    )
                    val enginePlaylistId = if (existing.moveToFirst()) {
                        val id = existing.getLong(0)
                        existing.close()
                        db.execSQL("DELETE FROM PlaylistEntity WHERE listId=?", arrayOf(id))
                        id
                    } else {
                        existing.close()
                        db.execSQL(
                            "INSERT INTO Playlist (title, isPersisted) VALUES (?,1)",
                            arrayOf(playlist.title)
                        )
                        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                        val id = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                        cursor.close()
                        id
                    }

                    for (track in plTracks) {
                        val engineTrackId = trackIdMap[track.id] ?: continue
                        db.execSQL(
                            """INSERT INTO PlaylistEntity
                               (listId, trackId, databaseUuid, membershipReference)
                               VALUES (?,?,hex(randomblob(16)),0)""",
                            arrayOf(enginePlaylistId, engineTrackId)
                        )
                    }
                } catch (e: Exception) {
                    errors.add("Playlist ${playlist.title}: ${e.message}")
                }
            }

            writeM3U8Files(volumePath, playlists)

        } finally {
            db.close()
        }

        val flushed = EngineDJDatabase.flushEngineDb(context, volumePath)
        if (!flushed) errors.add(Strings.t("engine.db_write_error"))

        return EngineSyncResult(
            tracksWritten = trackIdMap.size,
            playlistsWritten = playlists.size,
            errors = errors,
        )
    }

    fun writeAnalysisResultsToUsb(
        context: Context,
        volumePath: String,
        tracks: List<Triple<String, String, com.djapp.analysis.AnalysisResult?>>,
        playlistName: String?,
    ): EngineSyncResult {
        val db = EngineDJDatabase.openEngineDb(context, volumePath)
            ?: return EngineSyncResult(0, 0, listOf(Strings.t("engine.db_open_error")))

        val volumeRoot = volumePath.trimEnd('/')
        val errors = mutableListOf<String>()
        val engineTrackIds = mutableListOf<Long>()
        val writtenTracks = mutableListOf<Triple<String, String, com.djapp.analysis.AnalysisResult?>>()

        try {
            for ((filePath, filename, result) in tracks) {
                try {
                    val relPath = EngineDJDatabase.engineRelativePath(filePath, volumeRoot)
                    val ext = filename.substringAfterLast('.', "")
                    val fileType = fileTypeFromExt(ext)
                    val bpmInt = result?.bpm?.let { (it * 100).toInt() }

                    val existing = db.rawQuery(
                        "SELECT id FROM Track WHERE path=? OR filename=?",
                        arrayOf(relPath, filename)
                    )
                    val existingId = if (existing.moveToFirst()) existing.getLong(0) else null
                    existing.close()

                    if (existingId != null) {
                        db.execSQL(
                            """UPDATE Track SET title=?, bpm=?, bpmAnalyzed=?,
                               isAnalyzed=?, fileType=?
                               WHERE id=?""",
                            arrayOf(
                                filename.replace(Regex("\\.[^.]+$"), ""),
                                bpmInt, result?.bpm,
                                if (result != null) 1 else 0,
                                fileType, existingId,
                            )
                        )
                        engineTrackIds.add(existingId)
                    } else {
                        db.execSQL(
                            """INSERT INTO Track
                               (path, filename, title, bpm, bpmAnalyzed, fileType,
                                isAnalyzed, isAvailable, dateCreated, dateAdded)
                               VALUES (?,?,?,?,?,?,?,1,datetime('now'),datetime('now'))""",
                            arrayOf(
                                relPath, filename,
                                filename.replace(Regex("\\.[^.]+$"), ""),
                                bpmInt, result?.bpm, fileType,
                                if (result != null) 1 else 0,
                            )
                        )
                        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                        if (cursor.moveToFirst()) engineTrackIds.add(cursor.getLong(0))
                        cursor.close()
                    }
                    writtenTracks.add(Triple(filePath, filename, result))
                } catch (e: Exception) {
                    errors.add("Track $filename: ${e.message}")
                }
            }

            if (playlistName != null && engineTrackIds.isNotEmpty()) {
                try {
                    db.execSQL(
                        "INSERT INTO Playlist (title, isPersisted) VALUES (?,1)",
                        arrayOf(playlistName)
                    )
                    val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                    val playlistId = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    cursor.close()

                    for (trackId in engineTrackIds) {
                        db.execSQL(
                            """INSERT INTO PlaylistEntity
                               (listId, trackId, databaseUuid, membershipReference)
                               VALUES (?,?,hex(randomblob(16)),0)""",
                            arrayOf(playlistId, trackId)
                        )
                    }

                    writeM3U8ForAnalyzed(volumePath, playlistName, writtenTracks)
                } catch (e: Exception) {
                    errors.add("Playlist $playlistName: ${e.message}")
                }
            }

        } finally {
            db.close()
        }

        val flushed = EngineDJDatabase.flushEngineDb(context, volumePath)
        if (!flushed) errors.add(Strings.t("engine.db_write_error"))

        return EngineSyncResult(
            tracksWritten = engineTrackIds.size,
            playlistsWritten = if (playlistName != null) 1 else 0,
            errors = errors,
        )
    }

    private fun writeM3U8ForAnalyzed(
        volumePath: String,
        playlistName: String,
        tracks: List<Triple<String, String, com.djapp.analysis.AnalysisResult?>>,
    ) {
        val plDir = File(volumePath, "Engine Library/Playlists")
        if (!plDir.exists()) plDir.mkdirs()
        val volumeRoot = volumePath.trimEnd('/')

        try {
            val safe = playlistName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(plDir, "$safe.m3u8")
            FileWriter(file).use { w ->
                w.write("#EXTM3U\n")
                w.write("#PLAYLIST:$playlistName\n")
                for ((path, filename, result) in tracks) {
                    val info = filename.replace(Regex("\\.[^.]+$"), "")
                    val extInf = buildString {
                        append("#EXTINF:-1")
                        result?.bpm?.let { append(",bpm=${String.format("%.1f", it)}") }
                        result?.camelotKey?.let { append(",key=$it") }
                        append(",$info")
                    }
                    val relPath = EngineDJDatabase.engineRelativePath(path, volumeRoot)
                    w.write("$extInf\n")
                    w.write("$relPath\n")
                }
            }
        } catch (e: Exception) {
            Log.e("EngineDJSync", "M3U8 write failed for $playlistName", e)
        }
    }

    private fun writeM3U8Files(
        volumePath: String,
        playlists: List<Pair<PlaylistEntity, List<TrackEntity>>>,
    ) {
        val plDir = File(volumePath, "Engine Library/Playlists")
        if (!plDir.exists()) plDir.mkdirs()
        val volumeRoot = volumePath.trimEnd('/')

        for ((playlist, tracks) in playlists) {
            try {
                val safe = playlist.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = File(plDir, "$safe.m3u8")
                FileWriter(file).use { w ->
                    w.write("#EXTM3U\n")
                    w.write("#PLAYLIST:${playlist.title}\n")
                    for (t in tracks) {
                        val durSec = t.durationMs?.let { it / 1000 } ?: -1
                        val info = listOf(t.artist, t.title).filter { it.isNotBlank() }.joinToString(" - ")
                        val extInf = buildString {
                            append("#EXTINF:$durSec")
                            t.bpm?.let { append(",bpm=${String.format("%.1f", it)}") }
                            t.keyCamelot?.let { append(",key=$it") }
                            append(",$info")
                        }
                        val relPath = EngineDJDatabase.engineRelativePath(t.path, volumeRoot)
                        w.write("$extInf\n")
                        w.write("$relPath\n")
                    }
                }
            } catch (e: Exception) {
                Log.e("EngineDJSync", "M3U8 write failed for ${playlist.title}", e)
            }
        }
    }
}
