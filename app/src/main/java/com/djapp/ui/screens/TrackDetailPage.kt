package com.djapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.data.local.entity.TrackEntity
import com.djapp.engine.InternalEngineDB
import com.djapp.i18n.Strings
import com.djapp.ui.components.BpmBadge
import com.djapp.ui.components.KeyBadge
import com.djapp.ui.theme.CardBackground
import com.djapp.ui.theme.OnSurface
import com.djapp.ui.theme.OnSurfaceVariant
import com.djapp.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailPage(trackId: Long, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { DJLibraryDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var track by remember { mutableStateOf<TrackEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }
    var editBpm by remember { mutableStateOf("") }
    var editBpmAnalyzed by remember { mutableStateOf("") }
    var editKeyMusical by remember { mutableStateOf("") }
    var editKeyCamelot by remember { mutableStateOf("") }
    var editKeyOpen by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editGenre by remember { mutableStateOf("") }
    var editYear by remember { mutableStateOf("") }
    var editRating by remember { mutableStateOf("") }
    var editComment by remember { mutableStateOf("") }
    var editLabel by remember { mutableStateOf("") }
    var editColorR by remember { mutableStateOf("") }
    var editColorG by remember { mutableStateOf("") }
    var editColorB by remember { mutableStateOf("") }

    LaunchedEffect(trackId) {
        withContext(Dispatchers.IO) {
            track = db.trackDao().getById(trackId)
            isLoading = false
        }
    }

    fun startEditing(t: TrackEntity) {
        editTitle = t.title
        editArtist = t.artist
        editBpm = t.bpm?.let { "%.1f".format(it) } ?: ""
        editBpmAnalyzed = t.bpmAnalyzed?.let { "%.1f".format(it) } ?: ""
        editKeyMusical = t.keyMusical ?: ""
        editKeyCamelot = t.keyCamelot ?: ""
        editKeyOpen = t.keyOpen ?: ""
        editAlbum = t.album
        editGenre = t.genre
        editYear = t.year?.toString() ?: ""
        editRating = if (t.rating > 0) t.rating.toString() else ""
        editComment = t.comment
        editLabel = t.label
        editColorR = if (t.colorR > 0) t.colorR.toString() else ""
        editColorG = if (t.colorG > 0) t.colorG.toString() else ""
        editColorB = if (t.colorB > 0) t.colorB.toString() else ""
        isEditing = true
    }

    fun saveChanges() {
        val t = track ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                db.trackDao().update(
                    id = t.id,
                    path = t.path,
                    filename = t.filename,
                    folder = t.folder,
                    title = editTitle,
                    artist = editArtist,
                    album = editAlbum,
                    genre = editGenre,
                    year = editYear.toIntOrNull(),
                    durationMs = t.durationMs,
                    bpm = editBpm.toDoubleOrNull(),
                    bpmAnalyzed = editBpmAnalyzed.toDoubleOrNull(),
                    keyCamelot = editKeyCamelot.ifBlank { null },
                    keyOpen = editKeyOpen.ifBlank { null },
                    keyMusical = editKeyMusical.ifBlank { null },
                    lufs = t.lufs,
                    rmsDb = t.rmsDb,
                    peakDb = t.peakDb,
                    bitrate = t.bitrate,
                    fileSize = t.fileSize,
                    fileType = t.fileType,
                    rating = editRating.toIntOrNull() ?: 0,
                    comment = editComment,
                    label = editLabel,
                    colorR = editColorR.toIntOrNull() ?: 0,
                    colorG = editColorG.toIntOrNull() ?: 0,
                    colorB = editColorB.toIntOrNull() ?: 0,
                    isAnalyzed = t.isAnalyzed || editBpm.isNotBlank(),
                    dateAdded = t.dateAdded,
                )
                track = db.trackDao().getById(t.id)
                InternalEngineDB.syncFromRoom(context)
            }
            isEditing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (isEditing) Strings.t("common.edit") else Strings.t("track.title"),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (isEditing) isEditing = false else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Primary)
                }
            },
            actions = {
                if (track != null && !isEditing) {
                    IconButton(onClick = { startEditing(track!!) }) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Primary)
                    }
                } else if (isEditing) {
                    TextButton(onClick = { saveChanges() }) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Strings.t("common.save"), color = Primary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (track == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Strings.t("library.empty_tracks"), color = OnSurfaceVariant)
            }
        } else {
            val t = track!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCard(t, isEditing, editTitle, editArtist,
                    onTitleChange = { editTitle = it },
                    onArtistChange = { editArtist = it })
                AnalysisCard(t, isEditing, editBpm, editBpmAnalyzed,
                    editKeyMusical, editKeyCamelot, editKeyOpen,
                    onBpmChange = { editBpm = it },
                    onBpmAnalyzedChange = { editBpmAnalyzed = it },
                    onKeyMusicalChange = { editKeyMusical = it },
                    onKeyCamelotChange = { editKeyCamelot = it },
                    onKeyOpenChange = { editKeyOpen = it })
                FileInfoCard(t)
                MetadataCard(t, isEditing, editAlbum, editGenre, editYear, editRating, editComment, editLabel,
                    onAlbumChange = { editAlbum = it },
                    onGenreChange = { editGenre = it },
                    onYearChange = { editYear = it },
                    onRatingChange = { editRating = it },
                    onCommentChange = { editComment = it },
                    onLabelChange = { editLabel = it })
                EngineDjCard(t, isEditing, editColorR, editColorG, editColorB,
                    onColorRChange = { editColorR = it },
                    onColorGChange = { editColorG = it },
                    onColorBChange = { editColorB = it })
                TimestampsCard(t)
            }
        }
    }
}

@Composable
private fun HeaderCard(
    track: TrackEntity, isEditing: Boolean,
    editTitle: String, editArtist: String,
    onTitleChange: (String) -> Unit, onArtistChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (track.isAnalyzed) Primary else OnSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = onTitleChange,
                            label = { Text(Strings.t("track.title")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                cursorColor = Primary,
                                focusedTextColor = OnSurface,
                            ),
                        )
                    } else {
                        Text(
                            text = track.title.ifBlank { track.filename },
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isEditing) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = onArtistChange,
                            label = { Text(Strings.t("track.artist")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                cursorColor = Primary,
                                focusedTextColor = OnSurface,
                            ),
                        )
                    } else if (track.artist.isNotBlank()) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (track.bpm != null) BpmBadge(bpm = track.bpm)
                if (track.keyCamelot != null) KeyBadge(key = track.keyCamelot)
                if (track.isAnalyzed) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Primary,
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text(
                            text = Strings.t("track.analyzed"),
                            color = OnSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(
    track: TrackEntity, isEditing: Boolean,
    editBpm: String, editBpmAnalyzed: String,
    editKeyMusical: String, editKeyCamelot: String, editKeyOpen: String,
    onBpmChange: (String) -> Unit, onBpmAnalyzedChange: (String) -> Unit,
    onKeyMusicalChange: (String) -> Unit, onKeyCamelotChange: (String) -> Unit,
    onKeyOpenChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AudioFile, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Strings.t("track.analysis_section"),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                EditRow(Strings.t("home.tracks"), editBpm, onBpmChange, KeyboardType.Decimal)
                EditRow("BPM Analyzed", editBpmAnalyzed, onBpmAnalyzedChange, KeyboardType.Decimal)
                EditRow(Strings.t("track.key_musical"), editKeyMusical, onKeyMusicalChange)
                EditRow("Camelot", editKeyCamelot, onKeyCamelotChange)
                EditRow(Strings.t("track.key_open"), editKeyOpen, onKeyOpenChange)
            } else {
                DetailRow(Strings.t("home.tracks"), track.bpm?.let { "%.1f".format(it) } ?: "–")
                DetailRow("BPM Analyzed", track.bpmAnalyzed?.let { "%.1f".format(it) } ?: "–")
                DetailRow(Strings.t("track.key_musical"), track.keyMusical ?: "–")
                DetailRow("Camelot", track.keyCamelot ?: "–")
                DetailRow(Strings.t("track.key_open"), track.keyOpen ?: "–")
                HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                DetailRow(Strings.t("track.lufs"), track.lufs?.let { "%.1f LUFS".format(it) } ?: "–")
                DetailRow(Strings.t("track.rms"), track.rmsDb?.let { "%.1f dB".format(it) } ?: "–")
                DetailRow(Strings.t("track.peak"), track.peakDb?.let { "%.1f dB".format(it) } ?: "–")
            }
        }
    }
}

@Composable
private fun FileInfoCard(track: TrackEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Strings.t("track.file_section"),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(Strings.t("track.filename"), track.filename)
            DetailRow(Strings.t("track.file_type"), track.fileType.ifBlank { "–" })
            DetailRow(Strings.t("track.path"), track.path, mono = true)
            DetailRow(Strings.t("track.file_size"), track.fileSize?.let { formatFileSize(it) } ?: "–")
            DetailRow(Strings.t("track.bitrate"), track.bitrate?.let { "${it} kbps" } ?: "–")
            DetailRow(Strings.t("track.duration"), track.durationMs?.let { formatDuration(it) } ?: "–")
        }
    }
}

@Composable
private fun MetadataCard(
    track: TrackEntity, isEditing: Boolean,
    editAlbum: String, editGenre: String, editYear: String,
    editRating: String, editComment: String, editLabel: String,
    onAlbumChange: (String) -> Unit, onGenreChange: (String) -> Unit,
    onYearChange: (String) -> Unit, onRatingChange: (String) -> Unit,
    onCommentChange: (String) -> Unit, onLabelChange: (String) -> Unit
) {
    val hasMetadata = isEditing || track.album.isNotBlank() || track.genre.isNotBlank() ||
        track.year != null || track.rating > 0 || track.comment.isNotBlank() || track.label.isNotBlank()

    if (!hasMetadata) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Strings.t("track.metadata_section"),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                EditRow(Strings.t("track.album"), editAlbum, onAlbumChange)
                EditRow(Strings.t("track.genre"), editGenre, onGenreChange)
                EditRow(Strings.t("track.year"), editYear, onYearChange, KeyboardType.Number)
                EditRow(Strings.t("track.rating"), editRating, onRatingChange, KeyboardType.Number)
                EditRow(Strings.t("track.comment"), editComment, onCommentChange)
                EditRow(Strings.t("track.label"), editLabel, onLabelChange)
            } else {
                if (track.album.isNotBlank()) DetailRow(Strings.t("track.album"), track.album)
                if (track.genre.isNotBlank()) DetailRow(Strings.t("track.genre"), track.genre)
                if (track.year != null) DetailRow(Strings.t("track.year"), track.year.toString())
                if (track.rating > 0) DetailRow(Strings.t("track.rating"), "${track.rating}/100")
                if (track.comment.isNotBlank()) DetailRow(Strings.t("track.comment"), track.comment)
                if (track.label.isNotBlank()) DetailRow(Strings.t("track.label"), track.label)
            }
        }
    }
}

@Composable
private fun EngineDjCard(
    track: TrackEntity, isEditing: Boolean,
    editColorR: String, editColorG: String, editColorB: String,
    onColorRChange: (String) -> Unit, onColorGChange: (String) -> Unit,
    onColorBChange: (String) -> Unit
) {
    val hasEngineInfo = isEditing || track.engineId != null || track.colorR != 0 || track.colorG != 0 || track.colorB != 0

    if (!hasEngineInfo) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Circle, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Strings.t("track.engine_section"),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (track.engineId != null) DetailRow(Strings.t("track.engine_id"), track.engineId.toString())
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EditRow("R", editColorR, onColorRChange, KeyboardType.Number, Modifier.weight(1f))
                    EditRow("G", editColorG, onColorGChange, KeyboardType.Number, Modifier.weight(1f))
                    EditRow("B", editColorB, onColorBChange, KeyboardType.Number, Modifier.weight(1f))
                }
            } else if (track.colorR != 0 || track.colorG != 0 || track.colorB != 0) {
                DetailRow(Strings.t("track.color"), "RGB(${track.colorR}, ${track.colorG}, ${track.colorB})")
            }
        }
    }
}

@Composable
private fun TimestampsCard(track: TrackEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                if (track.dateAdded.isNotBlank()) {
                    Text(
                        text = "${Strings.t("track.date_added")}: ${track.dateAdded}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
                if (track.dateModified != null) {
                    Text(
                        text = "${Strings.t("track.date_modified")}: ${track.dateModified}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            ),
            color = OnSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun EditRow(
    label: String, value: String, onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.padding(vertical = 2.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            cursorColor = Primary,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
        ),
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
