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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity): Long

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
}
