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
    val fileType: String?,
    val isAnalyzed: Int,
    val isAvailable: Int,
    val fileBytes: Long?,
    val rating: Int,
)

data class EnginePlaylist(
    val id: Long,
    val title: String,
    val parentListId: Long?,
    val isPersisted: Int,
    val nextListId: Long?,
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

    private var lastSourcePath: String? = null

    fun invalidateTempDbCache() {
        lastSourcePath = null
    }

    private fun ensureTempDb(context: Context, volumePath: String) {
        val sourcePath = File(volumePath, ENGINE_DB_RELATIVE)
        val tempPath = File(getTempDbPath(context))

        if (lastSourcePath == volumePath && tempPath.exists()) return

        if (sourcePath.exists()) {
            FileInputStream(sourcePath).use { input ->
                FileOutputStream(tempPath).use { output ->
                    input.copyTo(output)
                }
            }
        }
        lastSourcePath = volumePath
    }

    fun openEngineDb(context: Context, volumePath: String): SQLiteDatabase? {
        return try {
            ensureTempDb(context, volumePath)
            return try {
                val db = SQLiteDatabase.openOrCreateDatabase(getTempDbPath(context), null)
                bootstrapEngineSchema(db)
                db
            } catch (e1: Exception) {
                Log.w("EngineDJDB", "openEngineDb failed (corrupted?), retrying with fresh db", e1)
                val tempFile = File(getTempDbPath(context))
                if (tempFile.exists()) tempFile.delete()
                val db = SQLiteDatabase.openOrCreateDatabase(getTempDbPath(context), null)
                bootstrapEngineSchema(db)
                db
            }
        } catch (e: Exception) {
            Log.e("EngineDJDB", "openEngineDb failed after retry", e)
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

    fun bootstrapEngineSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA journal_mode = WAL;")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Track (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playOrder INTEGER,
                length INTEGER,
                bpm INTEGER,
                year INTEGER,
                path TEXT,
                filename TEXT,
                bitrate INTEGER,
                bpmAnalyzed REAL,
                albumArtId INTEGER,
                fileBytes INTEGER,
                title TEXT,
                artist TEXT,
                album TEXT,
                genre TEXT,
                comment TEXT,
                label TEXT,
                composer TEXT,
                remixer TEXT,
                key INTEGER,
                rating INTEGER DEFAULT 0,
                albumArt TEXT,
                timeLastPlayed DATETIME,
                isPlayed INTEGER DEFAULT 0,
                fileType TEXT,
                isAnalyzed INTEGER DEFAULT 0,
                dateCreated DATETIME,
                dateAdded DATETIME,
                isAvailable INTEGER DEFAULT 1,
                isMetadataOfPackedTrackChanged INTEGER DEFAULT 0,
                lastEditTime DATETIME,
                originTrackId INTEGER,
                originDatabaseUuid TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Playlist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                parentListId INTEGER,
                isPersisted INTEGER DEFAULT 1,
                nextListId INTEGER,
                lastEditTime DATETIME,
                isExplicitlyExported INTEGER DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PlaylistEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listId INTEGER,
                trackId INTEGER,
                databaseUuid TEXT,
                nextEntityId INTEGER,
                membershipReference INTEGER DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PerformanceData (
                trackId INTEGER PRIMARY KEY,
                trackData BLOB,
                overviewWaveFormData BLOB,
                beatData BLOB,
                quickCues BLOB,
                loops BLOB,
                thirdPartySourceId INTEGER,
                activeOnLoadLoops INTEGER DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Information (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                schemaVersionMajor INTEGER DEFAULT 1,
                schemaVersionMinor INTEGER DEFAULT 0,
                schemaVersionPatch INTEGER DEFAULT 0,
                currentPlayedIndiciator INTEGER DEFAULT 0,
                lastRekordBoxLibraryImportReadCounter INTEGER DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Smartlist (
                title TEXT NOT NULL DEFAULT '',
                uuid TEXT,
                parentPlaylistPath TEXT,
                nextPlaylistPath TEXT,
                nextListUuid TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PreparelistEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trackId INTEGER,
                listId INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Pack (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                packId TEXT,
                changeLogDatabaseUuid TEXT,
                changeLogId INTEGER,
                lastPackTime DATETIME
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS AlbumArt (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hash TEXT,
                albumArt BLOB
            )
        """)
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS PlaylistPath AS
            WITH RECURSIVE Heirarchy AS (
                SELECT id AS child, parentListId AS parent, title AS name, 1 AS depth FROM Playlist
                UNION ALL
                SELECT child, parentListId AS parent, title AS name, h.depth + 1 AS depth FROM Playlist c
                JOIN Heirarchy h ON h.parent = c.id
            )
            SELECT child AS playlistId, group_concat(name, ' / ') AS path FROM Heirarchy GROUP BY child
        """)
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS PlaylistAllChildren AS
            WITH FindAllChild AS (
                SELECT id, id as childListId FROM Playlist
                UNION ALL
                SELECT recursiveCTE.id, Plist.id FROM Playlist Plist
                INNER JOIN FindAllChild recursiveCTE
                ON recursiveCTE.childListId = Plist.parentListId
            )
            SELECT * FROM FindAllChild WHERE id <> childListId
        """)
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS PlaylistAllParent AS
            WITH FindAllParent AS (
                SELECT id, parentListId FROM Playlist
                UNION ALL
                SELECT recursiveCTE.id, Plist.parentListId FROM Playlist Plist
                INNER JOIN FindAllParent recursiveCTE
                ON recursiveCTE.parentListId = Plist.id
            )
            SELECT * FROM FindAllParent
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Track_insert_performance_data
            AFTER INSERT ON Track
            BEGIN
                INSERT OR IGNORE INTO PerformanceData(trackId) VALUES(NEW.id);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_delete_List
            AFTER DELETE ON Playlist
            FOR EACH ROW BEGIN
                UPDATE Playlist SET nextListId = OLD.nextListId WHERE nextListId = OLD.id;
                DELETE FROM Playlist WHERE parentListId = OLD.id;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_before_insert_List
            BEFORE INSERT ON Playlist
            FOR EACH ROW BEGIN
                UPDATE Playlist SET nextListId = -(1 + nextListId)
                WHERE nextListId = NEW.nextListId AND parentListId = NEW.parentListId;
            END
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

    private fun nullableString(cursor: android.database.Cursor, column: String): String? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
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
                            fileType = nullableString(cursor, "fileType"),
                            isAnalyzed = cursor.getInt(cursor.getColumnIndexOrThrow("isAnalyzed")),
                            isAvailable = cursor.getInt(cursor.getColumnIndexOrThrow("isAvailable")),
                            fileBytes = nullableLong(cursor, "fileBytes"),
                            rating = cursor.getInt(cursor.getColumnIndexOrThrow("rating")),
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
                            parentListId = nullableLong(cursor, "parentListId"),
                            isPersisted = cursor.getInt(cursor.getColumnIndexOrThrow("isPersisted")),
                            nextListId = nullableLong(cursor, "nextListId"),
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
        val normAbs = File(absPath).normalize().absolutePath
        val normRoot = File(volumeRoot).normalize().absolutePath.trimEnd('/')
        if (normAbs.startsWith("$normRoot/")) {
            return normAbs.removePrefix("$normRoot/")
        }
        val mountPoints = listOf("/storage/", "/mnt/media_rw/", "/data/media/")
        for (mount in mountPoints) {
            if (normAbs.startsWith(mount)) {
                val after = normAbs.removePrefix(mount)
                val slash = after.indexOf('/')
                if (slash >= 0) return after.substring(slash + 1)
            }
        }
        return normAbs.split("/").filter { it.isNotEmpty() }.takeLast(2).joinToString("/")
    }
}
