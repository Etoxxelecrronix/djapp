package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djapp.data.local.entity.SavedLoopEntity

@Dao
interface LoopDao {

    @Query("SELECT * FROM saved_loops WHERE trackId = :trackId ORDER BY startMs")
    suspend fun getByTrack(trackId: Long): List<SavedLoopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loop: SavedLoopEntity): Long

    @Query("DELETE FROM saved_loops WHERE id = :id")
    suspend fun delete(id: Long)
}
