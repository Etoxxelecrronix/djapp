package com.djapp.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

const val ENGINE_DB_RELATIVE = "Engine Library/Database2/m.db"

data class EngineTrack(
    val id: Long,
    val playOrder: Int?,
    val length: Int?,
    val bpm: Int?,
    val year: Int?,
    val path: String,
    val filename: String,
    val bitrate: Int?,
    val bpmAnalyzed: Double?,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val comment: String,
    val label: String,
    val fileType: Int,
    val isAnalyzed: Int,
    val isAvailable: Int,
    val isRhythmAnalyzed: Int,
    val fileBytes: Long?,
    val rating: Int,
    val colorRed: Int?,
    val colorGreen: Int?,
    val colorBlue: Int?,
)

data class EnginePlaylist(
    val id: Long,
    val title: String,
    val parentId: Long?,
    val isPersisted: Int,
    val nextListId: Long?,
    val firstTrackId: Long?,
    val lastTrackId: Long?,
    val flags: Int,
)

data class EnginePlaylistTrack(
    val trackId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val bpm: Int?,
    val bpmAnalyzed: Double?,
    val position: Int,
)

object EngineDJDatabase {

    private fun getTempDbPath(context: Context): String {
        val dir = File(context.cacheDir, "engine_db_tmp")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "engine_m.db").absolutePath
    }

    fun openEngineDb(context: Context, volumePath: String): SQLiteDatabase? {
        return try {
            val sourcePath = File(volumePath, ENGINE_DB_RELATIVE)
            val tempPath = getTempDbPath(context)

            if (sourcePath.exists()) {
                FileInputStream(sourcePath).use { input ->
                    FileOutputStream(tempPath).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val db = SQLiteDatabase.openOrCreateDatabase(tempPath, null)
            bootstrapEngineSchema(db)
            db
        } catch (e: Exception) {
            Log.e("EngineDJDB", "openEngineDb failed", e)
            null
        }
    }

    fun flushEngineDb(context: Context, volumePath: String): Boolean {
        return try {
            val tempPath = getTempDbPath(context)
            val dbDir = File(volumePath, "Engine Library/Database2")
            if (!dbDir.exists()) dbDir.mkdirs()

            val destPath = File(dbDir, "m.db")
            FileInputStream(tempPath).use { input ->
                FileOutputStream(destPath).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("EngineDJDB", "flushEngineDb failed", e)
            false
        }
    }

    private fun bootstrapEngineSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA journal_mode = WAL;")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Track (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playOrder INTEGER,
                length INTEGER,
                lengthCalculated INTEGER,
                bpm INTEGER,
                year INTEGER,
                path TEXT NOT NULL DEFAULT '',
                filename TEXT NOT NULL DEFAULT '',
                bitrate INTEGER,
                bpmAnalyzed REAL,
                title TEXT NOT NULL DEFAULT '',
                artist TEXT NOT NULL DEFAULT '',
                album TEXT NOT NULL DEFAULT '',
                genre TEXT NOT NULL DEFAULT '',
                comment TEXT NOT NULL DEFAULT '',
                label TEXT NOT NULL DEFAULT '',
                fileType INTEGER NOT NULL DEFAULT 0,
                isAnalyzed INTEGER NOT NULL DEFAULT 0,
                dateAdded INTEGER,
                isAvailable INTEGER NOT NULL DEFAULT 1,
                isRhythmAnalyzed INTEGER NOT NULL DEFAULT 0,
                fileBytes INTEGER,
                rating INTEGER NOT NULL DEFAULT 0,
                musicBrainzId TEXT,
                uri TEXT,
                colorRed INTEGER,
                colorGreen INTEGER,
                colorBlue INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Playlist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                parentId INTEGER,
                isPersisted INTEGER NOT NULL DEFAULT 1,
                nextListId INTEGER,
                firstTrackId INTEGER,
                lastTrackId INTEGER,
                flags INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PlaylistEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listId INTEGER NOT NULL,
                trackId INTEGER NOT NULL,
                databaseUuid TEXT NOT NULL DEFAULT '',
                nextEntityId INTEGER,
                membershipReference INTEGER NOT NULL DEFAULT 0,
                dateAdded INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PerformanceData (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                isAnalyzed INTEGER NOT NULL DEFAULT 0,
                isRendered INTEGER NOT NULL DEFAULT 0,
                trackData BLOB,
                highResolutionWaveformData BLOB,
                overviewWaveformData BLOB,
                beatData BLOB,
                quickCues BLOB,
                loops BLOB,
                activeOnLoadCueNum INTEGER NOT NULL DEFAULT -1,
                lastEditTime TEXT
            )
        """)
    }

    private fun nullableLong(cursor: android.database.Cursor, column: String): Long? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
    }

    private fun nullableInt(cursor: android.database.Cursor, column: String): Int? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else null
    }

    private fun nullableDouble(cursor: android.database.Cursor, column: String): Double? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getDouble(idx) else null
    }

    fun readAllEngineTracks(context: Context, volumePath: String): List<EngineTrack> {
        val db = openEngineDb(context, volumePath) ?: return emptyList()
        val tracks = mutableListOf<EngineTrack>()
        try {
            db.rawQuery("SELECT * FROM Track ORDER BY artist, title", null).use { cursor ->
                while (cursor.moveToNext()) {
                    tracks.add(
                        EngineTrack(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            playOrder = nullableInt(cursor, "playOrder"),
                            length = nullableInt(cursor, "length"),
                            bpm = nullableInt(cursor, "bpm"),
                            year = nullableInt(cursor, "year"),
                            path = cursor.getString(cursor.getColumnIndexOrThrow("path")),
                            filename = cursor.getString(cursor.getColumnIndexOrThrow("filename")),
                            bitrate = nullableInt(cursor, "bitrate"),
                            bpmAnalyzed = nullableDouble(cursor, "bpmAnalyzed"),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            artist = cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                            album = cursor.getString(cursor.getColumnIndexOrThrow("album")),
                            genre = cursor.getString(cursor.getColumnIndexOrThrow("genre")),
                            comment = cursor.getString(cursor.getColumnIndexOrThrow("comment")),
                            label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                            fileType = cursor.getInt(cursor.getColumnIndexOrThrow("fileType")),
                            isAnalyzed = cursor.getInt(cursor.getColumnIndexOrThrow("isAnalyzed")),
                            isAvailable = cursor.getInt(cursor.getColumnIndexOrThrow("isAvailable")),
                            isRhythmAnalyzed = cursor.getInt(cursor.getColumnIndexOrThrow("isRhythmAnalyzed")),
                            fileBytes = nullableLong(cursor, "fileBytes"),
                            rating = cursor.getInt(cursor.getColumnIndexOrThrow("rating")),
                            colorRed = nullableInt(cursor, "colorRed"),
                            colorGreen = nullableInt(cursor, "colorGreen"),
                            colorBlue = nullableInt(cursor, "colorBlue"),
                        )
                    )
                }
            }
        } finally {
            db.close()
        }
        return tracks
    }

    fun readAllEnginePlaylists(context: Context, volumePath: String): List<EnginePlaylist> {
        val db = openEngineDb(context, volumePath) ?: return emptyList()
        val playlists = mutableListOf<EnginePlaylist>()
        try {
            db.rawQuery("SELECT * FROM Playlist ORDER BY title", null).use { cursor ->
                while (cursor.moveToNext()) {
                    playlists.add(
                        EnginePlaylist(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            parentId = nullableLong(cursor, "parentId"),
                            isPersisted = cursor.getInt(cursor.getColumnIndexOrThrow("isPersisted")),
                            nextListId = nullableLong(cursor, "nextListId"),
                            firstTrackId = nullableLong(cursor, "firstTrackId"),
                            lastTrackId = nullableLong(cursor, "lastTrackId"),
                            flags = cursor.getInt(cursor.getColumnIndexOrThrow("flags")),
                        )
                    )
                }
            }
        } finally {
            db.close()
        }
        return playlists
    }

    fun readEnginePlaylistTracks(context: Context, volumePath: String, playlistId: Long): List<EnginePlaylistTrack> {
        val db = openEngineDb(context, volumePath) ?: return emptyList()
        val tracks = mutableListOf<EnginePlaylistTrack>()
        try {
            db.rawQuery("""
                SELECT pe.trackId, t.path, t.title, t.artist, t.bpm, t.bpmAnalyzed,
                       pe.id as position
                FROM PlaylistEntity pe
                JOIN Track t ON t.id = pe.trackId
                WHERE pe.listId = ?
                ORDER BY pe.id
            """, arrayOf(playlistId.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    tracks.add(
                        EnginePlaylistTrack(
                            trackId = cursor.getLong(cursor.getColumnIndexOrThrow("trackId")),
                            path = cursor.getString(cursor.getColumnIndexOrThrow("path")),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            artist = cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                            bpm = nullableInt(cursor, "bpm"),
                            bpmAnalyzed = nullableDouble(cursor, "bpmAnalyzed"),
                            position = cursor.getInt(cursor.getColumnIndexOrThrow("position")),
                        )
                    )
                }
            }
        } finally {
            db.close()
        }
        return tracks
    }

    fun trackCount(context: Context, volumePath: String): Int {
        val db = openEngineDb(context, volumePath) ?: return 0
        return try {
            db.rawQuery("SELECT COUNT(*) FROM Track", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            db.close()
        }
    }

    fun engineRelativePath(absPath: String, volumeRoot: String): String {
        val root = volumeRoot.trimEnd('/')
        val abs = if (absPath.startsWith("/")) absPath else "/$absPath"
        return if (abs.startsWith("$root/")) {
            abs.removePrefix("$root/")
        } else {
            abs.split("/").filter { it.isNotEmpty() }.takeLast(2).joinToString("/")
        }
    }
}
