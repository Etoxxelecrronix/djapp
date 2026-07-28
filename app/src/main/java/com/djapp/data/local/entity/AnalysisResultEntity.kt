package com.djapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analysis_results",
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
data class AnalysisResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trackId") val trackId: Long,
    val bpm: Double? = null,
    @ColumnInfo(name = "bpm_confidence") val bpmConfidence: Double? = null,
    @ColumnInfo(name = "tempo_stability") val tempoStability: Double? = null,
    @ColumnInfo(name = "key_camelot") val keyCamelot: String? = null,
    @ColumnInfo(name = "key_open") val keyOpen: String? = null,
    @ColumnInfo(name = "key_musical") val keyMusical: String? = null,
    @ColumnInfo(name = "key_confidence") val keyConfidence: Double? = null,
    val lufs: Double? = null,
    @ColumnInfo(name = "rms_db") val rmsDb: Double? = null,
    @ColumnInfo(name = "peak_db") val peakDb: Double? = null,
    @ColumnInfo(name = "waveform_json") val waveformJson: String? = null,
    @ColumnInfo(name = "beat_grid_json") val beatGridJson: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Double? = null,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: String = "",
    @ColumnInfo(name = "analyzer_ver") val analyzerVer: String = "1.0",
)
