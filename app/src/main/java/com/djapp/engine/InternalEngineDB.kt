package com.djapp.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.TrackEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object InternalEngineDB {

    private const val INTERNAL_DB_NAME = "Engine Library/Database2/m.db"
    private const val TAG = "InternalEngineDB"

    private fun getInternalDbFile(context: Context): File {
        return File(context.filesDir, INTERNAL_DB_NAME)
    }

    private fun getInternalDbDir(context: Context): File {
        return getInternalDbFile(context).parentFile!!
    }

    fun openInternalDb(context: Context, ensureParentDir: Boolean = true): SQLiteDatabase? {
        return try {
            val file = getInternalDbFile(context)
            if (ensureParentDir) file.parentFile?.mkdirs()
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            EngineDJDatabase.bootstrapEngineSchema(db)
            db
        } catch (e: Exception) {
            Log.e(TAG, "openInternalDb failed", e)
            try {
                getInternalDbFile(context).delete()
                val file2 = getInternalDbFile(context)
                file2.parentFile?.mkdirs()
                val db = SQLiteDatabase.openOrCreateDatabase(file2, null)
                EngineDJDatabase.bootstrapEngineSchema(db)
                db
            } catch (e2: Exception) {
                Log.e(TAG, "openInternalDb retry failed", e2)
                null
            }
        }
    }

    suspend fun syncRoomToInternal(context: Context, volumeRoot: String? = null): Boolean {
        val db = openInternalDb(context) ?: return false
        return try {
            val roomDb = DJLibraryDatabase.getInstance(context)
            val allTracks = roomDb.getAllTracks()
            val allPlaylists = roomDb.playlistDao().getAll()
            val root = volumeRoot ?: context.filesDir.absolutePath

            val trackIdMap = mutableMapOf<Long, Long>()

            db.beginTransaction()
            try {
                for (track in allTracks) {
                    val relPath = EngineDJDatabase.engineRelativePath(track.path, root)
                    val ext = track.filename.substringAfterLast('.', "")
                    val fileType = when (ext.lowercase()) {
                        "mp3" -> "mp3"; "aif", "aiff" -> "aiff"
                        "wav" -> "wav"; "flac" -> "flac"
                        "ogg" -> "ogg"; "m4a", "mp4" -> "m4a"
                        "alac" -> "alac"; else -> ext.lowercase()
                    }
                    val bpmInt = track.bpm?.let { (it * 100).toInt() }

                    val existing = db.rawQuery(
                        "SELECT id FROM Track WHERE path=?",
                        arrayOf(relPath)
                    )
                    val existingId = if (existing.moveToFirst()) existing.getLong(0) else null
                    existing.close()

                    val engineId: Long
                    if (existingId != null) {
                        db.execSQL(
                            """UPDATE Track SET filename=?,title=?,artist=?,album=?,
                               genre=?,comment=?,label=?,bpm=?,bpmAnalyzed=?,year=?,
                               fileType=?,isAnalyzed=?,rating=?
                               WHERE id=?""",
                            arrayOf(
                                track.filename, track.title, track.artist, track.album,
                                track.genre, track.comment, track.label, bpmInt,
                                track.bpmAnalyzed, track.year, fileType,
                                if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5), existingId
                            )
                        )
                        engineId = existingId
                    } else {
                        db.execSQL(
                            """INSERT INTO Track (path,filename,title,artist,album,genre,
                               comment,label,bpm,bpmAnalyzed,year,fileType,isAnalyzed,
                               isAvailable,rating,dateCreated,dateAdded)
                               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,datetime('now'),datetime('now'))""",
                            arrayOf(
                                relPath, track.filename, track.title, track.artist,
                                track.album, track.genre, track.comment, track.label,
                                bpmInt, track.bpmAnalyzed, track.year, fileType,
                                if (track.isAnalyzed) 1 else 0,
                                (track.rating / 20).coerceIn(0, 5)
                            )
                        )
                        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                        engineId = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                        cursor.close()
                    }
                    if (engineId != 0L) trackIdMap[track.id] = engineId
                }

                for (pl in allPlaylists) {
                    val existing = db.rawQuery(
                        "SELECT id FROM Playlist WHERE title=?",
                        arrayOf(pl.title)
                    )
                    val enginePlId: Long
                    if (existing.moveToFirst()) {
                        enginePlId = existing.getLong(0)
                        existing.close()
                        db.execSQL("DELETE FROM PlaylistEntity WHERE listId=?", arrayOf(enginePlId))
                    } else {
                        existing.close()
                        db.execSQL(
                            "INSERT INTO Playlist (title, isPersisted) VALUES (?,1)",
                            arrayOf(pl.title)
                        )
                        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
                        enginePlId = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                        cursor.close()
                    }

                    val plTracks = roomDb.getPlaylistTracks(pl.id)
                    for ((pos, t) in plTracks.withIndex()) {
                        val engineTrackId = trackIdMap[t.id] ?: continue
                        db.execSQL(
                            """INSERT INTO PlaylistEntity
                               (listId, trackId, databaseUuid, membershipReference)
                               VALUES (?,?,hex(randomblob(16)),0)""",
                            arrayOf(enginePlId, engineTrackId)
                        )
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncRoomToInternal failed", e)
            false
        } finally {
            db.close()
        }
    }

    fun internalDbExists(context: Context): Boolean {
        return getInternalDbFile(context).exists()
    }

    suspend fun syncFromRoom(context: Context, volumeRoot: String? = null): Boolean = syncRoomToInternal(context, volumeRoot)

    fun exportToUsb(context: Context, volumePath: String): Boolean = pushToUsb(context, volumePath)

    fun pushToUsb(context: Context, usbVolumePath: String): Boolean {
        return try {
            val internalFile = getInternalDbFile(context)
            if (!internalFile.exists()) return false

            val dbDir = File(usbVolumePath, "Engine Library/Database2")
            if (!dbDir.exists()) dbDir.mkdirs()

            FileInputStream(internalFile).use { input ->
                FileOutputStream(File(dbDir, "m.db")).use { output ->
                    input.copyTo(output)
                }
            }

            val playlistsDir = File(usbVolumePath, "Engine Library/Playlists")
            if (!playlistsDir.exists()) playlistsDir.mkdirs()

            val internalPlDir = File(getInternalDbDir(context), "Playlists")
            if (internalPlDir.exists()) {
                internalPlDir.listFiles()?.forEach { file ->
                    FileInputStream(file).use { input ->
                        FileOutputStream(File(playlistsDir, file.name)).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "pushToUsb failed", e)
            false
        }
    }

    suspend fun exportToM3U8(context: Context, volumePath: String) {
        val db = openInternalDb(context) ?: return
        try {
            val playlists = db.rawQuery(
                "SELECT id, title FROM Playlist WHERE isPersisted=1", null
            )
            while (playlists.moveToNext()) {
                val plId = playlists.getLong(0)
                val plTitle = playlists.getString(1)
                val safe = plTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val m3uFile = File(
                    File(volumePath, "Engine Library/Playlists"),
                    "$safe.m3u8"
                )
                m3uFile.parentFile?.mkdirs()

                java.io.FileWriter(m3uFile).use { w ->
                    w.write("#EXTM3U\n#PLAYLIST:$plTitle\n")
                    val tracks = db.rawQuery(
                        """SELECT t.path, t.title, t.artist, t.bpmAnalyzed
                           FROM Track t JOIN PlaylistEntity pe ON pe.trackId = t.id
                           WHERE pe.listId = ? ORDER BY pe.id""",
                        arrayOf(plId.toString())
                    )
                    while (tracks.moveToNext()) {
                        val path = tracks.getString(0)
                        val title = tracks.getString(1)
                        val artist = tracks.getString(2)
                        val bpm = tracks.getDouble(3)
                        val info = listOf(artist, title).filter { it.isNotBlank() }
                            .joinToString(" - ")
                        w.write("#EXTINF:-1,bpm=${"%.1f".format(bpm)},$info\n$path\n")
                    }
                    tracks.close()
                }
            }
            playlists.close()
        } catch (e: Exception) {
            Log.e(TAG, "exportToM3U8 failed", e)
        } finally {
            db.close()
        }
    }
}