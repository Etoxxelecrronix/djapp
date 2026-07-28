package com.djapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.djapp.data.local.entity.TrackEntity
import com.djapp.data.local.dao.PlaylistWithCount
import com.djapp.engine.EngineDJDatabase
import com.djapp.engine.EngineVolume
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.VolumeType
import com.djapp.i18n.Strings
import com.djapp.ui.components.*
import com.djapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryPage() {
    val context = LocalContext.current
    val db = remember { DJLibraryDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    var trackList by remember { mutableStateOf(emptyList<TrackEntity>()) }
    var playlistList by remember { mutableStateOf(emptyList<PlaylistWithCount>()) }
    var deviceList by remember { mutableStateOf(emptyList<EngineVolume>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var selectedTrackForPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            trackList = db.getAllTracks()
            playlistList = db.playlistDao().getAll()
            isLoading = false
        }
    }

    fun refreshData() {
        scope.launch {
            withContext(Dispatchers.IO) {
                trackList = db.getAllTracks()
                playlistList = db.playlistDao().getAll()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(Strings.t("library.title"), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Primary,
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text(Strings.t("library.tab_tracks"), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text(Strings.t("library.tab_playlists"), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 2, onClick = {
                selectedTab = 2
                scope.launch {
                    withContext(Dispatchers.IO) {
                        deviceList = EngineVolumeDetector.detectUsbVolumes(context)
                    }
                }
            }) {
                Text(Strings.t("library.tab_devices"), modifier = Modifier.padding(12.dp))
            }
        }

        when (selectedTab) {
            0 -> TracksTab(
                tracks = trackList,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                isLoading = isLoading,
                onLongPressTrack = { track ->
                    selectedTrackForPlaylist = track
                    showAddToPlaylistDialog = true
                },
                onSwipeDelete = { track ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.trackDao().delete(track.id)
                            trackList = db.getAllTracks()
                        }
                    }
                },
            )
            1 -> PlaylistsTab(
                playlists = playlistList,
                isLoading = isLoading,
                onCreatePlaylist = { showNewPlaylistDialog = true },
                onRefresh = { refreshData() },
            )
            2 -> DevicesTab(
                devices = deviceList,
                isLoading = isLoading,
                onRefresh = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            deviceList = EngineVolumeDetector.detectUsbVolumes(context)
                        }
                    }
                },
                onImportDevice = { volume ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val engineTracks = EngineDJDatabase.readAllEngineTracks(context, volume.path)
                            val enginePlaylists = EngineDJDatabase.readAllEnginePlaylists(context, volume.path)
                            for (et in engineTracks) {
                                val absPath = if (et.path.startsWith("/")) et.path
                                else "${volume.path.trimEnd('/')}/${et.path}"
                                db.upsertTrack(
                                    TrackEntity(
                                        path = absPath,
                                        filename = et.filename,
                                        folder = absPath.split('/').dropLast(1).lastOrNull() ?: "",
                                        title = et.title.ifBlank { et.filename.replace(Regex("\\.[^.]+$"), "") },
                                        artist = et.artist,
                                        album = et.album,
                                        genre = et.genre,
                                        year = et.year,
                                        bpm = et.bpm?.let { it / 100.0 },
                                        bpmAnalyzed = et.bpmAnalyzed,
                                        fileType = et.filename.substringAfterLast('.', ""),
                                        rating = et.rating * 20,
                                        comment = et.comment,
                                        label = et.label,
                                        colorR = et.colorRed ?: 0,
                                        colorG = et.colorGreen ?: 0,
                                        colorB = et.colorBlue ?: 0,
                                        isAnalyzed = et.isAnalyzed == 1,
                                        dateAdded = java.time.Instant.now().toString(),
                                    )
                                )
                            }
                            for (ep in enginePlaylists) {
                                val plId = db.createPlaylist(ep.title)
                                val entities = EngineDJDatabase.readEnginePlaylistTracks(context, volume.path, ep.id)
                                for ((pos, ept) in entities.withIndex()) {
                                    val absPath = if (ept.path.startsWith("/")) ept.path
                                    else "${volume.path.trimEnd('/')}/${ept.path}"
                                    val localTrack = db.getTrackByPath(absPath)
                                    if (localTrack != null) {
                                        db.addTrackToPlaylist(plId, localTrack.id, pos)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                refreshData()
                            }
                        }
                    }
                },
            )
        }
    }

    if (showAddToPlaylistDialog && selectedTrackForPlaylist != null) {
        AddToPlaylistDialog(
            playlists = playlistList,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.addTrackToPlaylist(playlist.id, selectedTrackForPlaylist!!.id)
                    }
                    showAddToPlaylistDialog = false
                }
            },
            onCreateNew = {
                showAddToPlaylistDialog = false
                showNewPlaylistDialog = true
            },
        )
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onCreate = { title ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.createPlaylist(title)
                    }
                    refreshData()
                    showNewPlaylistDialog = false
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TracksTab(
    tracks: List<TrackEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isLoading: Boolean,
    onLongPressTrack: (TrackEntity) -> Unit,
    onSwipeDelete: (TrackEntity) -> Unit,
) {
    val filtered = if (searchQuery.isBlank()) tracks else tracks.filter {
        it.title.contains(searchQuery, true) ||
            it.artist.contains(searchQuery, true) ||
            it.album.contains(searchQuery, true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text(Strings.t("library.search")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OnSurfaceVariant,
                cursorColor = Primary,
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface,
            ),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filtered.isEmpty()) {
            EmptyState(
                message = Strings.t("library.empty_tracks"),
                icon = Icons.Default.MusicNote,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { track ->
                    TrackListItem(
                        title = track.title.ifBlank { track.filename },
                        artist = track.artist,
                        bpm = track.bpm,
                        key = track.keyCamelot,
                        isAnalyzed = track.isAnalyzed,
                        onClick = {},
                        onLongClick = { onLongPressTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistWithCount>,
    isLoading: Boolean,
    onCreatePlaylist: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Strings.t("library.playlist_count", playlists.size),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
            )
            GreenButton(
                text = Strings.t("library.new_playlist"),
                onClick = onCreatePlaylist,
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (playlists.isEmpty()) {
            EmptyState(
                message = Strings.t("library.empty_playlists"),
                icon = Icons.Default.PlaylistPlay,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistListItem(
                        title = playlist.title,
                        trackCount = playlist.trackCount,
                        syncedAt = playlist.syncedAt,
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicesTab(
    devices: List<EngineVolume>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onImportDevice: (EngineVolume) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedGreenButton(
                text = Strings.t("library.detect_devices"),
                onClick = onRefresh,
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (devices.isEmpty()) {
            EmptyState(
                message = Strings.t("library.empty_devices"),
                icon = Icons.Default.Usb,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices) { device ->
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
                            Icon(
                                imageVector = when (device.type) {
                                    VolumeType.USB -> Icons.Default.Usb
                                    VolumeType.SD -> Icons.Default.SdCard
                                    VolumeType.INTERNAL -> Icons.Default.PhoneAndroid
                                },
                                contentDescription = null,
                                tint = if (device.hasEngineLibrary) Primary else OnSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (device.hasEngineLibrary)
                                        Strings.t("library.engine_db_found") + " (${device.trackCount} ${Strings.t("library.track_count", device.trackCount)})"
                                    else
                                        Strings.t("library.engine_db_missing"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (device.hasEngineLibrary) Primary else OnSurfaceVariant,
                                )
                            }
                            if (device.hasEngineLibrary) {
                                GreenButton(
                                    text = Strings.t("library.import_from_device"),
                                    onClick = { onImportDevice(device) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistWithCount) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(Strings.t("library.add_to_playlist"), color = OnSurface) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateNew() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.t("library.new_playlist"), color = Primary)
                }
                HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.2f))
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = OnSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(playlist.title, color = OnSurface)
                            Text(
                                Strings.t("library.track_count", playlist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.t("common.cancel"), color = OnSurfaceVariant)
            }
        },
    )
}

@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(Strings.t("library.new_playlist"), color = OnSurface) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Strings.t("playlists.create_prompt")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OnSurfaceVariant,
                    cursorColor = Primary,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )
        },
        confirmButton = {
            GreenButton(
                text = Strings.t("common.save"),
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.t("common.cancel"), color = OnSurfaceVariant)
            }
        },
    )
}
