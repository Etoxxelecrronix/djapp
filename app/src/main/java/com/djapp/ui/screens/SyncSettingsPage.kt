package com.djapp.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.djapp.i18n.Strings
import com.djapp.ui.components.GreenButton
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

    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    val selectedPath = prefs.getString(PrefsKeys.SELECTED_PATH, null) ?: ""

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

        lastSyncTime = prefs.getString(PrefsKeys.LAST_SYNC_TIME, null)
    }

    fun doSync() {
        if (usbVolume == null) {
            syncMessage = Strings.t("usb.no_engine")
            return
        }
        isSyncing = true
        syncProgress = 0f
        syncMessage = null
        currentSyncStep = Strings.t("common.loading")

        scope.launch {
            try {
                val vol = usbVolume ?: return@launch

                val allTracks = withContext(Dispatchers.IO) { db.trackDao().getAll() }
                syncProgress = 0.2f
                currentSyncStep = Strings.t("sync.progress")

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
                currentSyncStep = Strings.t("sync.done", result.tracksWritten, result.playlistsWritten)
                lastSyncTime = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                prefs.edit().putString(PrefsKeys.LAST_SYNC_TIME, lastSyncTime).apply()

                if (result.errors.isNotEmpty()) {
                    syncMessage = "${Strings.t("sync.complete")}. ${result.errors.joinToString("\n")}"
                }
            } catch (e: Exception) {
                syncMessage = "${Strings.t("sync.error")}: ${e.message}"
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
            text = Strings.t("sync.title"),
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
                    text = Strings.t("sync.target"),
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
                            text = Strings.t("library.engine_db_found"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Primary
                        )
                    }
                    Text(
                        text = usbVolume?.let { "${it.label} (${it.path})" } ?: "",
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
                            text = Strings.t("usb.no_devices"),
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
                            text = Strings.t("usb.no_engine"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
                if (lastSyncTime != null) {
                    Text(
                        text = Strings.t("sync.last_sync", lastSyncTime ?: ""),
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
                        text = "${Strings.t("nav.playlists")} (${playlists.size})",
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
                            text = Strings.t("playlists.empty"),
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
                                        text = Strings.t("library.track_count", playlist.trackCount),
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
            text = if (isSyncing) Strings.t("sync.progress") else Strings.t("sync.start"),
            onClick = { doSync() },
            enabled = !isSyncing && usbVolume != null
        )

        if (syncMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = syncMessage ?: "", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
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
                        text = Strings.t("sync.compatibility"),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.t("sync.format_info"),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
