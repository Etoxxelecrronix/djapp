package com.djapp.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.data.local.dao.PlaylistWithCount
import com.djapp.engine.EngineDJSync
import com.djapp.engine.EngineVolume
import com.djapp.engine.EngineVolumeDetector
import com.djapp.ui.components.GreenButton
import com.djapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SyncSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DJLibraryDatabase.getInstance(context) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableFloatStateOf(0f) }
    var showFolders by remember { mutableStateOf(false) }
    var currentSyncStep by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    var playlists by remember { mutableStateOf<List<PlaylistWithCount>>(emptyList()) }
    var usbVolume by remember { mutableStateOf<EngineVolume?>(null) }
    var lastSyncTime by remember { mutableStateOf<String?>(null) }

    val prefs = remember { context.getSharedPreferences("dj_usb_selected", Context.MODE_PRIVATE) }
    val selectedPath = prefs.getString("usb_selected_path", null) ?: ""

    LaunchedEffect(Unit) {
        val playlistList = withContext(Dispatchers.IO) { db.playlistDao().getAll() }
        playlists = playlistList

        val volumes = withContext(Dispatchers.IO) { EngineVolumeDetector.detectUsbVolumes(context) }
        usbVolume = volumes.firstOrNull { it.hasEngineLibrary }

        if (usbVolume == null && selectedPath.isNotBlank()) {
            val manualVolume = withContext(Dispatchers.IO) {
                EngineVolumeDetector.detectVolumeAtPath(context, selectedPath)
            }
            if (manualVolume != null) usbVolume = manualVolume
        }

        lastSyncTime = prefs.getString("last_sync_time", null)
    }

    fun doSync() {
        if (usbVolume == null) {
            syncMessage = "Kein Speichermedium mit Engine DJ Library gefunden"
            return
        }
        isSyncing = true
        syncProgress = 0f
        syncMessage = null
        currentSyncStep = "Lade lokale Tracks..."

        scope.launch {
            try {
                val vol = usbVolume!!

                val allTracks = withContext(Dispatchers.IO) { db.trackDao().getAll() }
                syncProgress = 0.2f
                currentSyncStep = "Synchronisiere mit Engine DJ..."

                val localPlaylists = withContext(Dispatchers.IO) { db.playlistDao().getAll() }
                val playlistPairs = localPlaylists.mapNotNull { pl ->
                    val tracks = withContext(Dispatchers.IO) { db.playlistDao().getTracks(pl.id) }
                    if (tracks.isNotEmpty()) {
                        com.djapp.data.local.entity.PlaylistEntity(
                            id = pl.id,
                            title = pl.title,
                            parentId = pl.parentId,
                            isFolder = pl.isFolder,
                            createdAt = pl.createdAt,
                        ) to tracks
                    } else null
                }

                syncProgress = 0.4f

                val result = withContext(Dispatchers.IO) {
                    EngineDJSync.syncToEngineDJ(
                        context = context,
                        volumePath = vol.path,
                        tracks = allTracks,
                        playlists = playlistPairs,
                    )
                }

                syncProgress = 1f
                currentSyncStep = "Fertig! ${result.tracksWritten} Tracks, ${result.playlistsWritten} Playlists."
                lastSyncTime = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                prefs.edit().putString("last_sync_time", lastSyncTime).apply()

                if (result.errors.isNotEmpty()) {
                    syncMessage = result.errors.joinToString("\n")
                }
            } catch (e: Exception) {
                syncMessage = "Sync fehlgeschlagen: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Synchronisierung",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Speichermedium Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (usbVolume != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Engine DJ Library erkannt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Primary
                        )
                    }
                    Text(
                        text = "${usbVolume!!.label} (${usbVolume!!.path})",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                } else if (selectedPath.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kein Speichermedium ausgewählt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keine Engine DJ Library auf diesem Stick",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
                if (lastSyncTime != null) {
                    Text(
                        text = "Letzter Sync: $lastSyncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFolders = !showFolders }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlists (${playlists.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        if (showFolders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = OnSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = showFolders,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "Keine Playlists vorhanden",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            playlists.forEach { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = playlist.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${playlist.trackCount} Tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GreenButton(
            text = if (isSyncing) "Synchronisiere..." else "Sync starten",
            onClick = { doSync() },
            enabled = !isSyncing && usbVolume != null
        )

        if (syncMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = syncMessage!!, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
        }

        if (isSyncing) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { syncProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                        trackColor = SurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSyncStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kompatibilität",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Engine DJ nutzt SQLite für die m.db. Camelot Key-Notation wird unterstützt. Kompatibel mit SC Live 4 und Denon Hardware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
