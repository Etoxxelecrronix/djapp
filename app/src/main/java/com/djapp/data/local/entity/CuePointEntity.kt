package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cue_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId", "positionMs")]
)
data class CuePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trackId") val trackId: Long,
    val type: Int = 0,
    @ColumnInfo(name = "positionMs") val positionMs: Double,
    @ColumnInfo(name = "lengthMs") val lengthMs: Double = 0.0,
    val label: String = "",
    @ColumnInfo(name = "color_r") val colorR: Int = 255,
    @ColumnInfo(name = "color_g") val colorG: Int = 165,
    @ColumnInfo(name = "color_b") val colorB: Int = 0,
    @ColumnInfo(name = "is_hot_cue") val isHotCue: Boolean = false,
    @ColumnInfo(name = "hot_cue_number") val hotCueNumber: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)
