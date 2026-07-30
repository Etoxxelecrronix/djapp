package com.djapp.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djapp.analysis.AnalysisQueue
import com.djapp.analysis.QueueItem
import com.djapp.analysis.TrackStatus
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.engine.EngineDJDatabase
import com.djapp.engine.EngineDJSync
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.VolumeType
import com.djapp.scanner.MusicScanner
import com.djapp.scanner.ScanResult
import com.djapp.i18n.Strings
import com.djapp.ui.components.BpmBadge
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.KeyBadge
import com.djapp.ui.theme.BpmBadge as BpmBadgeColor
import com.djapp.ui.theme.CardBackground
import com.djapp.ui.theme.ErrorRed
import com.djapp.ui.theme.OnSurface
import com.djapp.ui.theme.OnSurfaceVariant
import com.djapp.ui.theme.Primary
import com.djapp.ui.theme.SurfaceVariant
import com.djapp.util.PrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisProgressPage(folderPath: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DJLibraryDatabase.getInstance(context) }
    var isStarted by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showUsbWriteDialog by remember { mutableStateOf(false) }
    var usbWriteResult by remember { mutableStateOf<String?>(null) }
    var isWritingToUsb by remember { mutableStateOf(false) }
    var usbWriteHadError by remember { mutableStateOf(false) }

    val queueState by AnalysisQueue.queue.collectAsState()
    val items = queueState.values.sortedBy { it.status != TrackStatus.QUEUED }
    val totalCount = items.size
    val doneCount = items.count { it.status == TrackStatus.DONE }
    val errorCount = items.count { it.status == TrackStatus.ERROR }
    val analyzingCount = items.count { it.status == TrackStatus.ANALYZING }
    val overallProgress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val isRunning = analyzingCount > 0 || (totalCount == 0 && isStarted)

    val analyzedBpm = items.mapNotNull { it.result?.bpm }
    val analyzedKeys = items.mapNotNull { it.result?.camelotKey }

    var showDuplicateWarning by remember { mutableStateOf(false) }
    var pendingScanResult by remember { mutableStateOf<ScanResult?>(null) }

    fun enqueueScanResult(scanResult: ScanResult) {
        scanMessage = Strings.t("analysis.total") + ": ${scanResult.totalTracks} ${Strings.t("home.tracks")}"
        val trackPairs = scanResult.folders.flatMap { folder ->
            folder.tracks.map { scanTrack ->
                val file = File(scanTrack.path)
                Uri.fromFile(file) to scanTrack.name
            }
        }
        AnalysisQueue.enqueue(trackPairs, context)
    }

    fun startAnalysis() {
        if (folderPath.isBlank()) return
        isStarted = true
        scope.launch {
            scanMessage = Strings.t("folders.scan")
            val scanResult = withContext(Dispatchers.IO) {
                MusicScanner.scanMusicLibrary(context, folderPath) { }
            }
            if (scanResult.totalTracks == 0) {
                scanMessage = Strings.t("folders.empty")
                isStarted = false
                return@launch
            }

            val existingCount = withContext(Dispatchers.IO) {
                scanResult.folders.flatMap { f -> f.tracks.map { it.path } }
                    .count { path -> db.getTrackByPath(path) != null }
            }

            if (existingCount > 0) {
                pendingScanResult = scanResult
                showDuplicateWarning = true
                isStarted = false
                return@launch
            }

            enqueueScanResult(scanResult)
        }
    }

    fun stopAnalysis() {
        AnalysisQueue.clearQueue()
        isStarted = false
    }

    fun createPlaylistFromAnalyzedTracks(playlistName: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val plId = db.createPlaylist(playlistName)
                val doneItems = items.filter { it.status == TrackStatus.DONE && it.trackId != null }
                val seenTrackIds = mutableSetOf<Long>()
                val existingTrackIds = db.playlistDao().getTracks(plId).map { it.id }.toSet()
                var pos = existingTrackIds.size
                for (item in doneItems) {
                    val tid = item.trackId ?: continue
                    if (tid in seenTrackIds || tid in existingTrackIds) continue
                    seenTrackIds.add(tid)
                    db.addTrackToPlaylist(plId, tid, pos)
                    pos++
                }
                com.djapp.util.ActionHistory.push(
                    com.djapp.util.UndoAction.ImportFolder(plId, playlistName)
                )
            }
            showCreatePlaylistDialog = false
        }
    }

    fun writeAnalyzedTracksToUsb(playlistName: String) {
        scope.launch {
            isWritingToUsb = true
            usbWriteResult = null
            val (result, hadError) = withContext(Dispatchers.IO) {
                var volumes = EngineVolumeDetector.detectUsbVolumes(context)
                var usbVolume = volumes.firstOrNull { it.type != VolumeType.INTERNAL }
                if (usbVolume == null) usbVolume = volumes.firstOrNull()
                if (usbVolume == null) {
                    val prefs = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    val manualPath = prefs.getString(PrefsKeys.SELECTED_PATH, null)
                    if (!manualPath.isNullOrBlank()) {
                        val manualVol = EngineVolumeDetector.detectVolumeAtPath(context, manualPath)
                        if (manualVol != null) usbVolume = manualVol
                    }
                }
                if (usbVolume == null) {
                    return@withContext Pair(Strings.t("engine.usb_not_found"), true)
                }
                val doneItems = items.filter { it.status == TrackStatus.DONE }
                if (doneItems.isEmpty()) {
                    return@withContext Pair(Strings.t("engine.no_analyzed_tracks"), true)
                }
                val tracks = doneItems.map { item ->
                    val filePath = if (item.uri.scheme == "file") {
                        item.uri.path ?: ""
                    } else {
                        item.uri.toString()
                    }
                    Triple(filePath, item.filename, item.result)
                }
                EngineDJDatabase.invalidateTempDbCache()
                val syncResult = EngineDJSync.writeAnalysisResultsToUsb(
                    context, usbVolume.path, tracks, playlistName
                )
                if (syncResult.errors.isNotEmpty()) {
                    Pair(Strings.t("engine.write_error", syncResult.errors.joinToString(", ")), true)
                } else {
                    Pair(Strings.t("engine.write_success", syncResult.tracksWritten), false)
                }
            }
            usbWriteResult = result
            usbWriteHadError = hadError
            isWritingToUsb = false
            showUsbWriteDialog = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = Strings.t("analysis.title"),
            style = MaterialTheme.typography.titleLarge,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = folderPath.ifBlank { Strings.t("folders.title") },
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.size(72.dp),
                            color = Primary,
                            trackColor = SurfaceVariant,
                            strokeWidth = 5.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { if (totalCount > 0) 1f else 0f },
                            modifier = Modifier.size(80.dp),
                            color = if (doneCount == totalCount && totalCount > 0) Primary else SurfaceVariant,
                            trackColor = SurfaceVariant,
                            strokeWidth = 6.dp
                        )
                    }
                    Text(
                        text = if (totalCount > 0) "${(overallProgress * 100).toInt()}%" else "0%",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (totalCount > 0) {
                    Text(
                        text = "$doneCount / $totalCount ${Strings.t("home.tracks")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (errorCount > 0) {
                        Text(
                            text = "$errorCount ${Strings.t("analysis.error")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                } else {
                    Text(
                        text = scanMessage.ifBlank { Strings.t("analysis.title") },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isStarted || !isRunning) {
                        GreenButton(
                            text = Strings.t("home.start_analysis"),
                            onClick = { startAnalysis() },
                            enabled = folderPath.isNotBlank()
                        )
                    } else {
                        OutlinedButton(
                            onClick = { stopAnalysis() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Strings.t("analysis.stop"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (doneCount > 0 && !isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = Strings.t("analysis.complete"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${Strings.t("home.analyzed")}: $doneCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface
                    )
                    if (analyzedBpm.isNotEmpty()) {
                        Text(
                            text = Strings.t("analysis.bpm_range", analyzedBpm.minOrNull() ?: 0.0, analyzedBpm.maxOrNull() ?: 0.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface
                        )
                    }
                    if (analyzedKeys.isNotEmpty()) {
                        val keySummary = analyzedKeys.groupingBy { it }.eachCount().entries.joinToString { "${it.key} (${it.value})" }
                        Text(
                            text = Strings.t("analysis.keys", keySummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GreenButton(
                        text = Strings.t("playlists.sync"),
                        onClick = { showUsbWriteDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showCreatePlaylistDialog = true },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(Strings.t("common.save"), color = OnSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    }
                    if (usbWriteResult != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = usbWriteResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usbWriteHadError) ErrorRed else Primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (items.isEmpty()) {
            EmptyState(
                message = if (isStarted) Strings.t("common.loading") else Strings.t("analysis.queued"),
                icon = Icons.Default.QueueMusic
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (item.status) {
                                    TrackStatus.DONE -> Icons.Default.CheckCircle
                                    TrackStatus.ANALYZING -> Icons.Default.Sync
                                    TrackStatus.ERROR -> Icons.Default.Error
                                    TrackStatus.QUEUED -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = when (item.status) {
                                    TrackStatus.DONE -> Primary
                                    TrackStatus.ANALYZING -> BpmBadgeColor
                                    TrackStatus.ERROR -> ErrorRed
                                    TrackStatus.QUEUED -> OnSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
                if (item.status == TrackStatus.ANALYZING && item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Primary,
                        trackColor = SurfaceVariant
                    )
                }
                if (item.status == TrackStatus.ERROR && item.error != null) {
                    Text(
                        text = item.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }
            item.result?.bpm?.let { BpmBadge(bpm = it) }
            item.result?.camelotKey?.let { key ->
                Spacer(modifier = Modifier.width(4.dp))
                                KeyBadge(key = key)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(Strings.t("playlists.create"), color = OnSurface) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(Strings.t("playlists.create_prompt")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OnSurfaceVariant,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (playlistName.isNotBlank()) createPlaylistFromAnalyzedTracks(playlistName) }
                ) {
                    Text(Strings.t("playlists.save"), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showUsbWriteDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUsbWriteDialog = false },
            title = { Text(Strings.t("playlists.sync"), color = OnSurface) },
            text = {
                Column {
                    Text(
                        text = Strings.t("sync.compatibility"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                    label = { Text(Strings.t("playlists.create_prompt")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OnSurfaceVariant,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            cursorColor = Primary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (playlistName.isNotBlank()) writeAnalyzedTracksToUsb(playlistName) },
                    enabled = !isWritingToUsb
                ) {
                    if (isWritingToUsb) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(Strings.t("common.save"), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsbWriteDialog = false }, enabled = !isWritingToUsb) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showDuplicateWarning && pendingScanResult != null) {
        val sr = pendingScanResult!!
        var duplicateExistingCount = 0
        scope.launch {
            duplicateExistingCount = withContext(Dispatchers.IO) {
                sr.folders.flatMap { f -> f.tracks.map { it.path } }
                    .count { path -> db.getTrackByPath(path) != null }
            }
        }
        val total = sr.totalTracks
        AlertDialog(
            onDismissRequest = {
                showDuplicateWarning = false
                pendingScanResult = null
            },
            title = { Text(Strings.t("folders.duplicate_title"), color = OnSurface) },
            text = {
                Text(
                    text = Strings.t("folders.duplicate_warning", duplicateExistingCount, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDuplicateWarning = false
                        scope.launch {
                            val existingPaths = withContext(Dispatchers.IO) {
                                sr.folders.flatMap { f -> f.tracks.map { it.path } }
                                    .filter { path -> db.getTrackByPath(path) != null }
                                    .toSet()
                            }
                            val newTracks = sr.folders.flatMap { f ->
                                f.tracks.filter { it.path !in existingPaths }
                            }
                            scanMessage = "${Strings.t("analysis.total")}: ${newTracks.size} ${Strings.t("home.tracks")}"
                            val trackPairs = newTracks.map { scanTrack ->
                                android.net.Uri.fromFile(java.io.File(scanTrack.path)) to scanTrack.name
                            }
                            if (trackPairs.isNotEmpty()) {
                                com.djapp.analysis.AnalysisQueue.enqueue(trackPairs, context)
                                isStarted = true
                            }
                            pendingScanResult = null
                        }
                    }
                ) {
                    Text(Strings.t("folders.duplicate_confirm_add"), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    pendingScanResult = null
                }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }
}
