package com.djapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 2,
    exportSchema = false,
)
abstract class DJLibraryDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: DJLibraryDatabase? = null

        fun getInstance(context: Context): DJLibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DJLibraryDatabase::class.java,
                    "dj_library_v1.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    // ── Track convenience methods ──────────────────────────────────────────

    suspend fun upsertTrack(track: TrackEntity): Long {
        return trackDao().upsert(track)
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

        val totalTracks = trackDao.getCount()
        val allTracks = trackDao.getAll()
        val analyzedTracks = allTracks.count { it.isAnalyzed }
        val totalPlaylists = playlistDao.getPlaylistCount()
        val avgBpm = allTracks.mapNotNull { it.bpm }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val avgLufs = allTracks.mapNotNull { it.lufs }.takeIf { it.isNotEmpty() }?.average()

        return LibraryStats(
            totalTracks = totalTracks,
            analyzedTracks = analyzedTracks,
            totalPlaylists = totalPlaylists,
            avgBpm = avgBpm,
            avgLufs = avgLufs,
        )
    }

    // ── Import folder as playlist ──────────────────────────────────────────

    suspend fun importFolderAsPlaylist(
        folderName: String,
        tracks: List<Triple<String, String, String>>, // path, filename, extension
    ): Long {
        val plId = createPlaylist(folderName)
        tracks.forEachIndexed { i, (path, filename, _) ->
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
            addTrackToPlaylist(plId, trackId, i)
        }
        return plId
    }
}
