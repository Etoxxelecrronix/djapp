package com.djapp.engine

import android.content.Context
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.TrackEntity
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class EngineSyncResult(
    val tracksWritten: Int,
    val playlistsWritten: Int,
    val errors: List<String>,
)

object EngineDJSync {

    fun fileTypeFromExt(ext: String): Int = when (ext.lowercase()) {
        "mp3" -> 1
        "aif", "aiff" -> 2
        "wav" -> 3
        "flac" -> 4
        "ogg" -> 5
        "mp4", "m4a" -> 6
        "alac" -> 7
        else -> 0
    }

    suspend fun syncToEngineDJ(
        context: Context,
        volumePath: String,
        tracks: List<TrackEntity>,
        playlists: List<Pair<PlaylistEntity, List<TrackEntity>>>,
        overwrite: Boolean = false,
    ): EngineSyncResult {
        val db = EngineDJDatabase.openEngineDb(context, volumePath)
            ?: return EngineSyncResult(0, 0, listOf("Konnte m.db nicht öffnen"))

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
                               bpm=?,bpmAnalyzed=?,year=?,fileType=?,isAnalyzed=?,rating=?,
                               colorRed=?,colorGreen=?,colorBlue=?,lastEditTime=datetime('now')
                               WHERE id=?""",
                            arrayOf(
                                track.title, track.artist, track.album, track.genre,
                                track.comment, track.label, bpmInt, track.bpmAnalyzed,
                                track.year, fileType, if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5),
                                track.colorR, track.colorG, track.colorB, existingId,
                            )
                        )
                        trackIdMap[track.id] = existingId
                    } else {
                        db.execSQL(
                            """INSERT INTO Track
                               (path, filename, title, artist, album, genre, comment, label,
                                bpm, bpmAnalyzed, year, fileType, isAnalyzed, isAvailable,
                                rating, colorRed, colorGreen, colorBlue, dateAdded, lastEditTime)
                               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,datetime('now'),datetime('now'))""",
                            arrayOf(
                                relPath, track.filename, track.title, track.artist,
                                track.album, track.genre, track.comment, track.label,
                                bpmInt, track.bpmAnalyzed, track.year, fileType,
                                if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5),
                                track.colorR, track.colorG, track.colorB,
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
                            "INSERT INTO Playlist (title, isPersisted, flags) VALUES (?,1,0)",
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
                               (listId, trackId, databaseUuid, membershipReference, dateAdded)
                               VALUES (?,?,hex(randomblob(16)),0,strftime('%s','now'))""",
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
        if (!flushed) errors.add("Konnte m.db nicht auf das Medium schreiben")

        return EngineSyncResult(
            tracksWritten = trackIdMap.size,
            playlistsWritten = playlists.size,
            errors = errors,
        )
    }

    private fun writeM3U8Files(
        volumePath: String,
        playlists: List<Pair<PlaylistEntity, List<TrackEntity>>>,
    ) {
        val plDir = File(volumePath, "Engine Library/Playlists")
        if (!plDir.exists()) plDir.mkdirs()

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
                        w.write("$extInf\n")
                        w.write("${t.path}\n")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
