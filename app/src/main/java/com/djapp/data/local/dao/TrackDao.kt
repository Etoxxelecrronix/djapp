package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.TrackEntity

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY artist ASC, title ASC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE path = :path")
    suspend fun getByPath(path: String): TrackEntity?

    @Query("""
        SELECT * FROM tracks 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
           OR path LIKE '%' || :query || '%'
        ORDER BY artist, title
    """)
    suspend fun search(query: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: TrackEntity): Long

    @Query("""
        UPDATE tracks SET path=:path, filename=:filename, folder=:folder,
            title=:title, artist=:artist, album=:album, genre=:genre,
            year=:year, duration_ms=:durationMs, bpm=:bpm, bpm_analyzed=:bpmAnalyzed,
            key_camelot=:keyCamelot, key_open=:keyOpen, key_musical=:keyMusical,
            lufs=:lufs, rms_db=:rmsDb, peak_db=:peakDb, bitrate=:bitrate,
            file_size=:fileSize, file_type=:fileType, rating=:rating,
            comment=:comment, label=:label, color_r=:colorR, color_g=:colorG,
            color_b=:colorB, is_analyzed=:isAnalyzed, date_added=:dateAdded,
            date_modified=datetime('now')
        WHERE id=:id
    """)
    suspend fun update(
        id: Long, path: String, filename: String, folder: String,
        title: String, artist: String, album: String, genre: String,
        year: Int?, durationMs: Long?, bpm: Double?, bpmAnalyzed: Double?,
        keyCamelot: String?, keyOpen: String?, keyMusical: String?,
        lufs: Double?, rmsDb: Double?, peakDb: Double?, bitrate: Int?,
        fileSize: Long?, fileType: String, rating: Int,
        comment: String, label: String, colorR: Int, colorG: Int, colorB: Int,
        isAnalyzed: Boolean, dateAdded: String,
    )

    @Query("""
        UPDATE tracks SET 
            bpm = :bpm,
            bpm_analyzed = :bpmAnalyzed,
            key_camelot = :keyCamelot,
            key_open = :keyOpen,
            key_musical = :keyMusical,
            lufs = :lufs,
            rms_db = :rmsDb,
            peak_db = :peakDb,
            is_analyzed = :isAnalyzed,
            date_modified = datetime('now')
        WHERE id = :id
    """)
    suspend fun updateAnalysis(
        id: Long,
        bpm: Double?,
        bpmAnalyzed: Double?,
        keyCamelot: String?,
        keyOpen: String?,
        keyMusical: String?,
        lufs: Double?,
        rmsDb: Double?,
        peakDb: Double?,
        isAnalyzed: Boolean,
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE is_analyzed = 1")
    suspend fun getAnalyzedCount(): Int

    @Query("SELECT AVG(bpm) FROM tracks WHERE bpm > 0")
    suspend fun getAvgBpm(): Double?

    @Query("SELECT AVG(lufs) FROM tracks WHERE lufs IS NOT NULL")
    suspend fun getAvgLufs(): Double?
}
