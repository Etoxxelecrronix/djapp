package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "beatgrids",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class BeatgridEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trackId") val trackId: Long,
    val bpm: Double,
    @ColumnInfo(name = "offsetMs") val offsetMs: Double = 0.0,
    @ColumnInfo(name = "is_downbeat") val isDownbeat: Boolean = false,
    @ColumnInfo(name = "beat_number") val beatNumber: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)
