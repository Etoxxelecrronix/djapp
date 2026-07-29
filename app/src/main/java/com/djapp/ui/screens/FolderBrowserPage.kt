package com.djapp.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.data.local.dao.PlaylistWithCount
import com.djapp.data.local.entity.PlaylistEntity
import com.djapp.data.local.entity.TrackEntity
import com.djapp.engine.EngineDJSync
import com.djapp.engine.EngineSyncResult
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.InternalEngineDB
import com.djapp.i18n.Strings
import com.djapp.scanner.FolderStat
import com.djapp.scanner.MusicScanner
import com.djapp.scanner.ScanPhase
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.FolderListItem
import com.djapp.ui.components.GreenButton
import com.djapp.ui.theme.CardBackground
import com.djapp.ui.theme.OnSurface
import com.djapp.ui.theme.OnSurfaceVariant
import com.djapp.ui.theme.Primary
import com.djapp.ui.theme.SurfaceVariant
import com.djapp.util.PrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserPage(
    onNavigateToAnalysis: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DJLibraryDatabase.getInstance(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<FolderStat?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<FolderStat>>(emptyList()) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var showDuplicateImportWarning by remember { mutableStateOf(false) }
    var pendingImportFolder by remember { mutableStateOf<FolderStat?>(null) }

    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    val selectedPath = prefs.getString(PrefsKeys.SELECTED_PATH, null) ?: ""

    fun doScan() {
        if (selectedPath.isBlank()) return
        isScanning = true
        scanProgress = Strings.t("usb.scanning")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MusicScanner.scanMusicLibrary(context, selectedPath) { progress ->
                    scanProgress = if (progress.phase == ScanPhase.DONE)
                        "${progress.found} ${Strings.t("home.tracks")}"
                    else
                        "${Strings.t("folders.scan")}: ${progress.currentPath}"
                }
            }
            folders = result.folders
            isScanning = false
            scanProgress = Strings.t("folders.tracks_count", result.totalTracks) + " in ${result.folders.size} ${Strings.t("nav.folders").lowercase()}"
        }
    }

    fun doImportFolder(folder: FolderStat, onlyNew: Boolean = true) {
        scope.launch {
            val plId = withContext(Dispatchers.IO) {
                val tracks = folder.tracks.map {
                    Triple(it.path, it.name, it.extension)
                }
                val id = db.importFolderAsPlaylist(folder.name, tracks, onlyNew = onlyNew)
                InternalEngineDB.syncFromRoom(context)
                id
            }
            com.djapp.util.ActionHistory.push(
                com.djapp.util.UndoAction.ImportFolder(plId, folder.name)
            )
            importMessage = "\"${folder.name}\" ${Strings.t("playlists.sync")}"
        }
    }

    fun importFolderAsPlaylist(folder: FolderStat, forceAll: Boolean = false) {
        scope.launch {
            if (!forceAll) {
                val existingCount = withContext(Dispatchers.IO) {
                    folder.tracks.count { track -> db.getTrackByPath(track.path) != null }
                }
                if (existingCount > 0 && existingCount < folder.trackCount) {
                    showDuplicateImportWarning = true
                    pendingImportFolder = folder
                    return@launch
                }
                if (existingCount >= folder.trackCount) {
                    importMessage = Strings.t("folders.duplicate_all_exist", folder.trackCount)
                    return@launch
                }
            }
            doImportFolder(folder, onlyNew = !forceAll)
        }
    }

    fun exportFolderToUsb(folder: FolderStat) {
        scope.launch {
            try {
                val tracks = folder.tracks.map { Triple(it.path, it.name, it.extension) }
                val plId = withContext(Dispatchers.IO) {
                    val id = db.importFolderAsPlaylist(folder.name, tracks)
                    InternalEngineDB.syncFromRoom(context)
                    id
                }

                val volume = withContext(Dispatchers.IO) {
                    EngineVolumeDetector.detectVolumeAtPath(context, selectedPath)
                }
                if (volume == null) {
                    importMessage = Strings.t("usb.no_devices")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    InternalEngineDB.exportToUsb(context, volume.path)
                    val allTracks = db.getAllTracks()
                    val pwc = db.playlistDao().getById(plId) ?: return@withContext EngineSyncResult(0, 0, listOf("playlist not found"))
                    val playlistTracks = db.playlistDao().getTracks(plId)
                    val pl = PlaylistEntity(
                        id = pwc.id, title = pwc.title,
                        parentId = pwc.parentId, isFolder = pwc.isFolder,
                        createdAt = pwc.createdAt,
                    )
                    EngineDJSync.syncToEngineDJ(
                        context = context, volumePath = volume.path,
                        tracks = allTracks, playlists = listOf(pl to playlistTracks),
                    )
                }

                importMessage = if (result.errors.isEmpty())
                    Strings.t("folders.export_done", folder.name)
                else
                    Strings.t("folders.export_error", result.errors.first())
            } catch (e: Exception) {
                importMessage = Strings.t("folders.export_error", e.message ?: "unknown")
            }
        }
    }

    LaunchedEffect(selectedPath) {
        if (selectedPath.isNotBlank()) doScan()
    }

    val filteredFolders = folders.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = Strings.t("folders.title"),
            style = MaterialTheme.typography.titleLarge,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )

        if (selectedPath.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            EmptyState(
                message = Strings.t("usb.no_devices"),
                icon = Icons.Default.UsbOff
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedPath,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(Strings.t("folders.search")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OnSurfaceVariant,
                    focusedLabelColor = Primary,
                    cursorColor = Primary,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GreenButton(
                    text = if (isScanning) scanProgress else Strings.t("folders.scan"),
                    onClick = { doScan() },
                    enabled = !isScanning
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (importMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(importMessage ?: "", color = Primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = SurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = scanProgress, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            if (filteredFolders.isEmpty() && !isScanning) {
                EmptyState(
                    message = if (searchQuery.isNotBlank()) Strings.t("folders.search") else Strings.t("folders.empty"),
                    icon = Icons.Default.FolderOff
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredFolders) { folder ->
                        FolderListItem(
                            name = folder.name,
                            trackCount = folder.trackCount,
                            analyzedCount = folder.newCount,
                            onClick = { onNavigateToAnalysis(folder.path) },
                            onLongClick = {
                                selectedFolder = folder
                                showContextMenu = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showContextMenu && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(selectedFolder?.name ?: "", color = OnSurface) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            selectedFolder?.let { onNavigateToAnalysis(it.path) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.t("folders.analyze"), color = OnSurface)
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            selectedFolder?.let { importFolderAsPlaylist(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.t("folders.create_playlist"), color = OnSurface)
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            selectedFolder?.let { exportFolderToUsb(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.t("folders.export_usb"), color = OnSurface)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showDuplicateImportWarning && pendingImportFolder != null) {
        val dupFolder = pendingImportFolder!!
        var existingCount by remember { mutableStateOf(0) }
        LaunchedEffect(dupFolder) {
            existingCount = withContext(Dispatchers.IO) {
                dupFolder.tracks.count { track -> db.getTrackByPath(track.path) != null }
            }
        }
        val newCount = dupFolder.trackCount - existingCount
        AlertDialog(
            onDismissRequest = {
                showDuplicateImportWarning = false
                pendingImportFolder = null
            },
            title = { Text(Strings.t("folders.duplicate_title"), color = OnSurface) },
            text = {
                Column {
                    Text(
                        text = Strings.t("folders.duplicate_warning_file", dupFolder.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$existingCount ${Strings.t("home.tracks")} vorhanden · $newCount neu",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDuplicateImportWarning = false
                        doImportFolder(dupFolder, onlyNew = true)
                    }
                ) {
                    Text(Strings.t("folders.duplicate_confirm_add"), color = Primary)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showDuplicateImportWarning = false
                        doImportFolder(dupFolder, onlyNew = false)
                    }) {
                        Text(Strings.t("folders.duplicate_add_all"), color = OnSurfaceVariant)
                    }
                    TextButton(onClick = {
                        showDuplicateImportWarning = false
                        pendingImportFolder = null
                    }) {
                        Text(Strings.t("common.cancel"), color = Primary)
                    }
                }
            },
            containerColor = CardBackground
        )
    }
}
