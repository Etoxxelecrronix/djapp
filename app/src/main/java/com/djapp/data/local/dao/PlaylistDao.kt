package com.djapp.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.PlaylistTrackEntity
import com.djapp.data.local.entity.TrackEntity

data class PlaylistWithCount(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "is_folder") val isFolder: Boolean,
    @ColumnInfo(name = "color_r") val colorR: Int,
    @ColumnInfo(name = "color_g") val colorG: Int,
    @ColumnInfo(name = "color_b") val colorB: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "synced_at") val syncedAt: String?,
    @ColumnInfo(name = "engine_id") val engineId: Long?,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {

    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) as trackCount
        FROM playlists p
        ORDER BY p.is_folder DESC, p.title ASC
    """)
    suspend fun getAll(): List<PlaylistWithCount>

    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) as trackCount
        FROM playlists p WHERE p.id = :id
    """)
    suspend fun getById(id: Long): PlaylistWithCount?

    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) as trackCount
        FROM playlists p WHERE p.title = :title LIMIT 1
    """)
    suspend fun getByTitle(title: String): PlaylistWithCount?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("""
        SELECT t.* FROM tracks t
        JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    suspend fun getTracks(playlistId: Long): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("SELECT COUNT(*) FROM playlists WHERE is_folder = 0")
    suspend fun getPlaylistCount(): Int
}
