package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.BeatgridEntity

@Dao
interface BeatgridDao {

    @Query("SELECT * FROM beatgrids WHERE trackId = :trackId ORDER BY offsetMs")
    suspend fun getByTrack(trackId: Long): List<BeatgridEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(beatgrids: List<BeatgridEntity>)

    @Query("DELETE FROM beatgrids WHERE trackId = :trackId")
    suspend fun deleteByTrack(trackId: Long)

    @Query("DELETE FROM beatgrids")
    suspend fun deleteAll()
}
