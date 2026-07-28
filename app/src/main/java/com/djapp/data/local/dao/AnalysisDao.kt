package com.djapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.djapp.data.local.entity.AnalysisResultEntity

@Dao
interface AnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: AnalysisResultEntity)
}
