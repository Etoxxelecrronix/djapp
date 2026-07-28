package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.CuePointEntity

@Dao
interface CuePointDao {

    @Query("SELECT * FROM cue_points WHERE trackId = :trackId ORDER BY positionMs")
    suspend fun getByTrack(trackId: Long): List<CuePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cuePoint: CuePointEntity): Long

    @Query("DELETE FROM cue_points WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cue_points WHERE trackId = :trackId")
    suspend fun deleteAll(trackId: Long)
}
