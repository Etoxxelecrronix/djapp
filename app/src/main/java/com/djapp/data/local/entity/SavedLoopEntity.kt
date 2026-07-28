package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_loops",
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
data class SavedLoopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trackId") val trackId: Long,
    @ColumnInfo(name = "startMs") val startMs: Double,
    @ColumnInfo(name = "endMs") val endMs: Double,
    val label: String = "",
    @ColumnInfo(name = "color_r") val colorR: Int = 0,
    @ColumnInfo(name = "color_g") val colorG: Int = 255,
    @ColumnInfo(name = "color_b") val colorB: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)
