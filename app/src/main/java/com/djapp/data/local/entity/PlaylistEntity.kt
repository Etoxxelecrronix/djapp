package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists", indices = [Index(value = ["title"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "is_folder") val isFolder: Boolean = false,
    @ColumnInfo(name = "color_r") val colorR: Int = 0,
    @ColumnInfo(name = "color_g") val colorG: Int = 0,
    @ColumnInfo(name = "color_b") val colorB: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
    @ColumnInfo(name = "synced_at") val syncedAt: String? = null,
    @ColumnInfo(name = "engine_id") val engineId: Long? = null,
)
