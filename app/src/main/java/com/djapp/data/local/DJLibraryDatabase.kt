package com.djapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.djapp.data.local.dao.AnalysisDao
import com.djapp.data.local.dao.PlaylistDao
import com.djapp.data.local.dao.TrackDao
import com.djapp.data.local.entity.AnalysisResultEntity
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.PlaylistTrackEntity
import com.djapp.data.local.entity.TrackEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LibraryStats(
    val totalTracks: Int,
    val analyzedTracks: Int,
    val totalPlaylists: Int,
    val avgBpm: Double?,
    val avgLufs: Double?,
)

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        AnalysisResultEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class DJLibraryDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: DJLibraryDatabase? = null

        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("DROP TABLE IF EXISTS loops")
            db.execSQL("DROP TABLE IF EXISTS cue_points")
            db.execSQL("DROP TABLE IF EXISTS beatgrids")
        }

        private val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("DELETE FROM tracks WHERE id NOT IN (SELECT MIN(id) FROM tracks GROUP BY path)")
            db.execSQL("DROP INDEX IF EXISTS index_tracks_path")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_path ON tracks(path)")
            db.execSQL("DELETE FROM playlists WHERE id NOT IN (SELECT MIN(id) FROM playlists GROUP BY title)")
            db.execSQL("DROP INDEX IF EXISTS index_playlists_title")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_title ON playlists(title)")
        }

        private val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("DELETE FROM playlist_tracks WHERE rowid NOT IN (SELECT MIN(rowid) FROM playlist_tracks GROUP BY playlistId, trackId)")
            db.execSQL("DROP INDEX IF EXISTS index_playlist_tracks_playlistId_position")
            db.execSQL("DROP INDEX IF EXISTS index_playlist_tracks_playlistId_trackId")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_tracks_playlistId_trackId ON playlist_tracks(playlistId, trackId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId_position ON playlist_tracks(playlistId, position)")
        }

        fun getInstance(context: Context): DJLibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DJLibraryDatabase::class.java,
                    "dj_library_v1.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    // ── Track convenience methods ──────────────────────────────────────────

    suspend fun upsertTrack(track: TrackEntity): Long {
        val existing = trackDao().getByPath(track.path)
        if (existing != null) {
            trackDao().update(
                id = existing.id, path = track.path, filename = track.filename,
                folder = track.folder, title = track.title, artist = track.artist,
                album = track.album, genre = track.genre, year = track.year,
                durationMs = track.durationMs, bpm = track.bpm,
                bpmAnalyzed = track.bpmAnalyzed, keyCamelot = track.keyCamelot,
                keyOpen = track.keyOpen, keyMusical = track.keyMusical,
                lufs = track.lufs, rmsDb = track.rmsDb, peakDb = track.peakDb,
                bitrate = track.bitrate, fileSize = track.fileSize,
                fileType = track.fileType, rating = track.rating,
                comment = track.comment, label = track.label,
                colorR = track.colorR, colorG = track.colorG, colorB = track.colorB,
                isAnalyzed = track.isAnalyzed, dateAdded = track.dateAdded,
            )
            return existing.id
        }
        return trackDao().insert(track)
    }

    suspend fun getTrackByPath(path: String): TrackEntity? {
        return trackDao().getByPath(path)
    }

    suspend fun getAllTracks(): List<TrackEntity> {
        return trackDao().getAll()
    }

    suspend fun searchTracks(query: String): List<TrackEntity> {
        return trackDao().search(query)
    }

    // ── Playlist convenience methods ───────────────────────────────────────

    suspend fun createPlaylist(title: String, parentId: Long? = null, isFolder: Boolean = false): Long {
        val existing = playlistDao().getByTitle(title)
        if (existing != null) return existing.id
        return playlistDao().insert(
            PlaylistEntity(
                title = title,
                parentId = parentId,
                isFolder = isFolder,
                createdAt = now(),
            )
        )
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int? = null) {
        val pos = position ?: run {
            val existing = playlistDao().getTracks(playlistId)
            existing.size
        }
        playlistDao().addTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = pos,
                dateAdded = now(),
            )
        )
    }

    suspend fun getPlaylistTracks(playlistId: Long): List<TrackEntity> {
        return playlistDao().getTracks(playlistId)
    }

    // ── Analysis convenience methods ───────────────────────────────────────

    suspend fun saveAnalysisResult(trackId: Long, result: AnalysisResultEntity) {
        analysisDao().upsert(result.copy(trackId = trackId, analyzedAt = now()))
        trackDao().updateAnalysis(
            id = trackId,
            bpm = result.bpm,
            bpmAnalyzed = result.bpm,
            keyCamelot = result.keyCamelot,
            keyOpen = result.keyOpen,
            keyMusical = result.keyMusical,
            lufs = result.lufs,
            rmsDb = result.rmsDb,
            peakDb = result.peakDb,
            isAnalyzed = true,
        )
    }

    // ── Stats ──────────────────────────────────────────────────────────────

    suspend fun getLibraryStats(): LibraryStats {
        val trackDao = trackDao()
        val playlistDao = playlistDao()

        return LibraryStats(
            totalTracks = trackDao.getCount(),
            analyzedTracks = trackDao.getAnalyzedCount(),
            totalPlaylists = playlistDao.getPlaylistCount(),
            avgBpm = trackDao.getAvgBpm(),
            avgLufs = trackDao.getAvgLufs(),
        )
    }

    // ── Import folder as playlist ──────────────────────────────────────────

    suspend fun importFolderAsPlaylist(
        folderName: String,
        tracks: List<Triple<String, String, String>>, // path, filename, extension
        onlyNew: Boolean = true,
    ): Long {
        val plId = createPlaylist(folderName)
        val existingPaths = if (onlyNew) {
            trackDao().getAll().map { it.path }.toSet()
        } else emptySet()
        var pos = playlistDao().getTracks(plId).size
        tracks.forEachIndexed { i, (path, filename, _) ->
            if (onlyNew && path in existingPaths) return@forEachIndexed
            val trackId = upsertTrack(
                TrackEntity(
                    path = path,
                    filename = filename,
                    folder = folderName,
                    title = filename.replace(Regex("\\.[^.]+$"), ""),
                    fileType = filename.substringAfterLast('.', ""),
                    dateAdded = now(),
                )
            )
            addTrackToPlaylist(plId, trackId, pos)
            pos++
        }
        return plId
    }
}
