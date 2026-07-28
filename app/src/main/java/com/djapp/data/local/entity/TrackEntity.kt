package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index("path"), Index("artist"), Index("bpm")]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val filename: String,
    val folder: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: Int? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    val bpm: Double? = null,
    @ColumnInfo(name = "bpm_analyzed") val bpmAnalyzed: Double? = null,
    @ColumnInfo(name = "key_camelot") val keyCamelot: String? = null,
    @ColumnInfo(name = "key_open") val keyOpen: String? = null,
    @ColumnInfo(name = "key_musical") val keyMusical: String? = null,
    val lufs: Double? = null,
    @ColumnInfo(name = "rms_db") val rmsDb: Double? = null,
    @ColumnInfo(name = "peak_db") val peakDb: Double? = null,
    val bitrate: Int? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    @ColumnInfo(name = "file_type") val fileType: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val label: String = "",
    @ColumnInfo(name = "color_r") val colorR: Int = 0,
    @ColumnInfo(name = "color_g") val colorG: Int = 0,
    @ColumnInfo(name = "color_b") val colorB: Int = 0,
    @ColumnInfo(name = "is_analyzed") val isAnalyzed: Boolean = false,
    @ColumnInfo(name = "date_added") val dateAdded: String = "",
    @ColumnInfo(name = "date_modified") val dateModified: String? = null,
    @ColumnInfo(name = "engine_id") val engineId: Long? = null,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String? = null,
)
