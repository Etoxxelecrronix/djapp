package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.AnalysisResultEntity

@Dao
interface AnalysisDao {

    @Query("SELECT * FROM analysis_results WHERE trackId = :trackId")
    suspend fun getByTrack(trackId: Long): AnalysisResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: AnalysisResultEntity)

    @Query("DELETE FROM analysis_results WHERE trackId = :trackId")
    suspend fun deleteByTrack(trackId: Long)
}
