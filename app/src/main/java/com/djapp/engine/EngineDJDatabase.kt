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
        // v2: neues Schema (Engine-DJ-kompatibel); alter Cache wird bewusst ignoriert
        return File(dir, "engine_m_v2.db").absolutePath
    }

    private var lastSourcePath: String? = null

    fun invalidateTempDbCache() {
        lastSourcePath = null
    }

    private fun ensureTempDb(context: Context, volumePath: String): Boolean {
        val sourcePath = File(volumePath, ENGINE_DB_RELATIVE)
        val tempPath = File(getTempDbPath(context))

        if (lastSourcePath == volumePath && tempPath.exists()) {
            return sourcePath.exists()
        }

        // Immer frisch starten: veraltete Temp-DB von einem anderen Volume/Lauf entfernen,
        // damit bei fehlender Quell-DB eine leere, neue Engine-Library entsteht.
        deleteTempDbFiles(context)

        val sourceExisted = sourcePath.exists()
        if (sourceExisted) {
            copyDbFile(sourcePath, tempPath)
        }
        lastSourcePath = volumePath
        return sourceExisted
    }

    private fun copyDbFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        // Restliches WAL (nicht sauber unmountet) mitschleppen, sonst gehen Daten verloren.
        val walSource = File(source.absolutePath + "-wal")
        if (walSource.exists()) {
            FileInputStream(walSource).use { input ->
                FileOutputStream(File(dest.absolutePath + "-wal")).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun openEngineDb(context: Context, volumePath: String): SQLiteDatabase? {
        return try {
            val sourceExisted = ensureTempDb(context, volumePath)
            var db: SQLiteDatabase? = null
            try {
                db = SQLiteDatabase.openOrCreateDatabase(getTempDbPath(context), null)
                bootstrapEngineSchema(db)
            } catch (e: Exception) {
                db?.close()
                db = null
                Log.w("EngineDJDB", "openEngineDb open/bootstrap failed", e)
                // Quell-m.db existiert, ist aber unlesbar/korrupt (z.B. kaputte View,
                // inkompatibles Denon-SQLite-Format). Nie stillschweigend eine leere DB
                // über die echte Library legen – stattdessen die Korrupte als .bak sichern
                // und mit einer frischen, korrekten DB weiterarbeiten.
                if (sourceExisted) backupCorruptSourceDb(context, volumePath)
                deleteTempDbFiles(context)
                db = SQLiteDatabase.openOrCreateDatabase(getTempDbPath(context), null)
                bootstrapEngineSchema(db)
            }
            db
        } catch (e: Exception) {
            Log.e("EngineDJDB", "openEngineDb failed", e)
            null
        }
    }

    private fun backupCorruptSourceDb(context: Context, volumePath: String) {
        try {
            val sourcePath = File(volumePath, ENGINE_DB_RELATIVE)
            if (sourcePath.exists()) {
                val backupPath = File(sourcePath.absolutePath + ".bak")
                copyDbFile(sourcePath, backupPath)
                // Korrupte Quelldatei samt WAL/SHM entfernen, damit flushEngineDb
                // eine frische m.db an diese Stelle schreiben kann.
                sourcePath.delete()
                File(sourcePath.absolutePath + "-wal").delete()
                File(sourcePath.absolutePath + "-shm").delete()
                Log.w("EngineDJDB", "korrupte m.db nach ${backupPath.name} gesichert, frische DB wird erzeugt")
            }
        } catch (e: Exception) {
            Log.w("EngineDJDB", "backupCorruptSourceDb failed", e)
        }
    }

    private fun deleteTempDbFiles(context: Context) {
        val tempPath = getTempDbPath(context)
        for (p in listOf(File(tempPath), File(tempPath + "-wal"), File(tempPath + "-shm"))) {
            if (p.exists()) p.delete()
        }
    }

    fun flushEngineDb(context: Context, volumePath: String): Boolean {
        return try {
            val tempPath = getTempDbPath(context)
            val dbDir = File(volumePath, "Engine Library/Database2")
            if (!dbDir.exists()) dbDir.mkdirs()

            val destPath = File(dbDir, "m.db")
            // Veraltete WAL/SHM am Ziel entfernen, damit sie nicht über unsere frische Datei gemerged werden.
            File(destPath.absolutePath + "-wal").delete()
            File(destPath.absolutePath + "-shm").delete()

            copyDbFile(File(tempPath), destPath)

            // Temp-seitige WAL/SHM aufräumen
            File(tempPath + "-wal").delete()
            File(tempPath + "-shm").delete()
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
                rating INTEGER,
                albumArt TEXT,
                timeLastPlayed DATETIME,
                isPlayed BOOLEAN,
                fileType TEXT,
                isAnalyzed BOOLEAN,
                dateCreated DATETIME,
                dateAdded DATETIME,
                isAvailable BOOLEAN,
                isMetadataOfPackedTrackChanged BOOLEAN,
                isPerfomanceDataOfPackedTrackChanged BOOLEAN,
                playedIndicator INTEGER,
                isMetadataImported BOOLEAN,
                pdbImportKey INTEGER,
                streamingSource TEXT,
                uri TEXT,
                isBeatGridLocked BOOLEAN,
                originDatabaseUuid TEXT,
                originTrackId INTEGER,
                streamingFlags INTEGER,
                explicitLyrics BOOLEAN,
                lastEditTime DATETIME,
                albumArtSourceHash CHAR(40),
                CONSTRAINT C_originDatabaseUuid_originTrackId UNIQUE (originDatabaseUuid, originTrackId),
                CONSTRAINT C_path UNIQUE (path),
                FOREIGN KEY (albumArtId) REFERENCES AlbumArt (id) ON DELETE RESTRICT
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Playlist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                parentListId INTEGER,
                isPersisted BOOLEAN,
                nextListId INTEGER,
                lastEditTime DATETIME,
                isExplicitlyExported BOOLEAN,
                CONSTRAINT C_NAME_UNIQUE_FOR_PARENT UNIQUE (title, parentListId),
                CONSTRAINT C_NEXT_LIST_ID_UNIQUE_FOR_PARENT UNIQUE (parentListId, nextListId)
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PlaylistEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listId INTEGER,
                trackId INTEGER,
                databaseUuid TEXT,
                nextEntityId INTEGER,
                membershipReference INTEGER,
                CONSTRAINT C_NAME_UNIQUE_FOR_LIST UNIQUE (listId, databaseUuid, trackId),
                FOREIGN KEY (listId) REFERENCES Playlist (id) ON DELETE CASCADE
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
                activeOnLoadLoops INTEGER,
                FOREIGN KEY (trackId) REFERENCES Track (id) ON DELETE CASCADE ON UPDATE CASCADE
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Information (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                schemaVersionMajor INTEGER,
                schemaVersionMinor INTEGER,
                schemaVersionPatch INTEGER,
                currentPlayedIndiciator INTEGER,
                lastRekordBoxLibraryImportReadCounter INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Smartlist (
                listUuid TEXT NOT NULL PRIMARY KEY,
                title TEXT,
                parentPlaylistPath TEXT,
                nextPlaylistPath TEXT,
                nextListUuid TEXT,
                rules TEXT,
                lastEditTime DATETIME,
                CONSTRAINT C_NAME_UNIQUE_FOR_PARENT UNIQUE (title, parentPlaylistPath),
                CONSTRAINT C_NEXT_LIST_UNIQUE_FOR_PARENT UNIQUE (parentPlaylistPath, nextPlaylistPath, nextListUuid)
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PreparelistEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trackId INTEGER,
                trackNumber INTEGER,
                FOREIGN KEY (trackId) REFERENCES Track (id) ON DELETE CASCADE
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
            CREATE VIEW IF NOT EXISTS ChangeLog (id, trackId) AS SELECT 0, 0 WHERE FALSE
        """)
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS PlaylistPath AS
            WITH RECURSIVE Heirarchy AS (
                SELECT id AS child, parentListId AS parent, title AS name, 1 AS depth FROM Playlist
                UNION ALL
                SELECT child, parentListId AS parent, title AS name, h.depth + 1 AS depth FROM Playlist c
                JOIN Heirarchy h ON h.parent = c.id
            )
            SELECT child AS playlistId, REPLACE(group_concat(name), ',', ' / ') AS path
            FROM Heirarchy GROUP BY child
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
            CREATE INDEX IF NOT EXISTS index_Track_bpmAnalyzed ON Track(CAST(bpmAnalyzed + 0.5 AS int))
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_Track_artist ON Track (artist)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_Track_album ON Track (album)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_Track_key ON Track (key)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_Track_uri ON Track (uri)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_PlaylistEntity_nextEntityId_listId ON PlaylistEntity(nextEntityId, listId)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_PreparelistEntity_trackId ON PreparelistEntity (trackId)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_AlbumArt_hash ON AlbumArt (hash)
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Track_insert_performance_data
            AFTER INSERT ON Track
            BEGIN
                INSERT OR IGNORE INTO PerformanceData(trackId) VALUES(NEW.id);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Track_fix_origin
            AFTER INSERT ON Track
            WHEN IFNULL(NEW.originTrackId, 0) = 0
              OR IFNULL(NEW.originDatabaseUuid, '') = ''
            BEGIN
                UPDATE Track SET
                    originTrackId = NEW.id,
                    originDatabaseUuid = (SELECT uuid FROM Information)
                WHERE track.id = NEW.id;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_update_Track_fix_origin
            AFTER UPDATE ON Track
            WHEN IFNULL(NEW.originTrackId, 0) = 0
              OR IFNULL(NEW.originDatabaseUuid, '') = ''
            BEGIN
                UPDATE Track SET
                    originTrackId = NEW.id,
                    originDatabaseUuid = (SELECT uuid FROM Information)
                WHERE track.id = NEW.id;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_update_only_Track_timestamp
            AFTER UPDATE OF length, bpm, year, filename, bitrate, bpmAnalyzed, albumArtId,
                              title, artist, album, genre, comment, label, composer, remixer,
                              key, rating, albumArt, fileType, isAnalyzed, isBeatGridLocked,
                              explicitLyrics
            ON Track
            FOR EACH ROW
            BEGIN
                UPDATE Track SET lastEditTime = strftime('%s') WHERE ROWID=NEW.ROWID;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Track_check_id
            AFTER INSERT ON Track
            WHEN NEW.id <= (SELECT seq FROM sqlite_sequence WHERE name = 'Track')
            BEGIN
                SELECT RAISE(ABORT, 'Recycling deleted track id''s are not allowed');
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_update_Track_check_Id
            BEFORE UPDATE ON Track
            WHEN NEW.id <> OLD.id
            BEGIN
                SELECT RAISE(ABORT, 'Changing track id''s are not allowed');
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_PerformanceData_after_update_Track_timestamp
            AFTER UPDATE OF trackData, isAnalyzed, overviewWaveFormData, beatData, quickCues,
                              loops, activeOnLoadLoops
            ON PerformanceData
            FOR EACH ROW
            BEGIN
                UPDATE Track
                SET lastEditTime = strftime('%s')
                WHERE id = NEW.trackId;
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
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_List
            AFTER INSERT ON Playlist
            FOR EACH ROW BEGIN
                UPDATE Playlist SET nextListId = 0 WHERE id = NEW.id;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_isPersist
            AFTER INSERT ON Playlist
            WHEN new.isPersisted = 1
            BEGIN
                UPDATE Playlist SET
                    isPersisted = 1
                WHERE id IN (SELECT parentListId FROM PlaylistAllParent WHERE id=new.id);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_update_isPersistChild
            AFTER UPDATE ON Playlist
            WHEN old.isPersisted = 1
              AND new.isPersisted = 0
            BEGIN
                UPDATE Playlist SET
                    isPersisted = 0
                WHERE id IN (SELECT childListId FROM PlaylistAllChildren WHERE id=new.id);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_update_isPersistParent
            AFTER UPDATE ON Playlist
            WHEN (old.isPersisted = 0 AND new.isPersisted = 1)
              OR (old.parentListId != new.parentListId AND new.isPersisted = 1)
            BEGIN
                UPDATE Playlist SET
                    isPersisted = 1
                WHERE id IN (SELECT parentListId FROM PlaylistAllParent WHERE id=new.id);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_before_delete_PlaylistEntity
            BEFORE DELETE ON PlaylistEntity
            WHEN OLD.trackId > 0
            BEGIN
                UPDATE PlaylistEntity SET
                    nextEntityId = OLD.nextEntityId
                WHERE nextEntityId = OLD.id
                AND listId = OLD.listId;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Pack_changeLogId
            AFTER INSERT ON Pack
            FOR EACH ROW WHEN NEW.changeLogId = 0
            BEGIN
                UPDATE Pack SET changeLogId = 1 WHERE ROWID = NEW.ROWID;
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_after_insert_Pack_timestamp
            AFTER INSERT ON Pack
            FOR EACH ROW WHEN NEW.lastPackTime IS NULL
            BEGIN
                UPDATE Pack SET lastPackTime = strftime('%s') WHERE ROWID = NEW.ROWID;
            END
        """)
        db.execSQL("""
            INSERT INTO Information
                (uuid, schemaVersionMajor, schemaVersionMinor, schemaVersionPatch,
                 currentPlayedIndiciator, lastRekordBoxLibraryImportReadCounter)
            SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' ||
                         hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' ||
                         hex(randomblob(6))), 1, 0, 0, 0, 0
            WHERE NOT EXISTS (SELECT 1 FROM Information)
        """)
    }

    fun engineLibraryUuid(db: SQLiteDatabase): String {
        db.rawQuery(
            "SELECT uuid FROM Information WHERE uuid IS NOT NULL AND uuid != '' LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        val uuid = generateLibraryUuid()
        db.execSQL(
            """INSERT INTO Information
               (uuid, schemaVersionMajor, schemaVersionMinor, schemaVersionPatch,
                currentPlayedIndiciator, lastRekordBoxLibraryImportReadCounter)
               VALUES (?,1,0,0,0,0)""",
            arrayOf(uuid)
        )
        return uuid
    }

    private fun generateLibraryUuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        val h = bytes.joinToString("") { "%02x".format(it) }
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-" +
            "${h.substring(16, 20)}-${h.substring(20, 32)}"
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
