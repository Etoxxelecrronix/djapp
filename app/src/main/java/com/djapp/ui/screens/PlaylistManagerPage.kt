package com.djapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.djapp.data.local.dao.PlaylistWithCount
import com.djapp.data.local.entity.TrackEntity
import com.djapp.i18n.Strings
import com.djapp.ui.components.BpmBadge
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.components.PlaylistListItem
import com.djapp.ui.components.TrackListItem
import com.djapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistManagerPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DJLibraryDatabase.getInstance(context) }

    var playlists by remember { mutableStateOf<List<PlaylistWithCount>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletingPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var allTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var trackSearchQuery by remember { mutableStateOf("") }

    fun refreshPlaylists() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { db.playlistDao().getAll() }
            playlists = list
        }
    }

    fun loadTracks(playlistId: Long) {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { db.playlistDao().getTracks(playlistId) }
            selectedPlaylistTracks = tracks
        }
    }

    fun loadAllTracks() {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { db.getAllTracks() }
            allTracks = tracks
        }
    }

    LaunchedEffect(Unit) { refreshPlaylists() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.t("playlists.title"),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Primary,
                    contentColor = Secondary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Strings.t("playlists.create"), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedPlaylist != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            selectedPlaylist = null
                            selectedPlaylistTracks = emptyList()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Primary)
                        }
                        Text(
                            text = selectedPlaylist!!.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GreenButton(
                            text = Strings.t("library.add_to_playlist"),
                            onClick = {
                                loadAllTracks()
                                showAddTracksDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedGreenButton(
                            text = Strings.t("playlists.sync"),
                            onClick = { },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedPlaylistTracks.isEmpty()) {
                EmptyState(
                    message = Strings.t("playlists.empty"),
                    icon = Icons.Default.QueueMusic
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(selectedPlaylistTracks) { track ->
                        TrackListItem(
                            title = track.title.ifBlank { track.filename },
                            artist = track.artist,
                            bpm = track.bpm,
                            key = track.keyCamelot,
                            isAnalyzed = track.isAnalyzed,
                            onClick = {},
                            onLongClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.playlistDao().removeTrack(selectedPlaylist!!.id, track.id)
                                    }
                                    loadTracks(selectedPlaylist!!.id)
                                    refreshPlaylists()
                                }
                            }
                        )
                    }
                }
            }
        } else {
            if (playlists.isEmpty()) {
                EmptyState(
                    message = Strings.t("playlists.empty"),
                    icon = Icons.Default.QueueMusic
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(playlists) { playlist ->
                        if (editingPlaylistId == playlist.id) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedTextField(
                                        value = editTitle,
                                        onValueChange = { editTitle = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = OnSurfaceVariant,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface,
                                            cursorColor = Primary
                                        )
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.playlistDao().rename(playlist.id, editTitle)
                                            }
                                            editingPlaylistId = null
                                            refreshPlaylists()
                                        }
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = Strings.t("common.save"), tint = Primary)
                                    }
                                    IconButton(onClick = { editingPlaylistId = null }) {
                                        Icon(Icons.Default.Close, contentDescription = Strings.t("common.cancel"), tint = ErrorRed)
                                    }
                                }
                            }
                        } else {
                            PlaylistListItem(
                                title = playlist.title,
                                trackCount = playlist.trackCount,
                                syncedAt = playlist.syncedAt,
                                onClick = {
                                    selectedPlaylist = playlist
                                    loadTracks(playlist.id)
                                },
                                onLongClick = {
                                    editTitle = playlist.title
                                    editingPlaylistId = playlist.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(Strings.t("playlists.create"), color = OnSurface) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(Strings.t("playlists.create_prompt")) },
                    modifier = Modifier.fillMaxWidth(),
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
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    db.createPlaylist(newTitle)
                                }
                                showCreateDialog = false
                                refreshPlaylists()
                            }
                        }
                    }
                ) {
                    Text(Strings.t("playlists.save"), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showAddTracksDialog && selectedPlaylist != null) {
        val filteredTracks = if (trackSearchQuery.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(trackSearchQuery, ignoreCase = true) ||
                it.artist.contains(trackSearchQuery, ignoreCase = true) ||
                it.filename.contains(trackSearchQuery, ignoreCase = true)
        }
        val existingTrackIds = selectedPlaylistTracks.map { it.id }.toSet()

        AlertDialog(
            onDismissRequest = { showAddTracksDialog = false; trackSearchQuery = "" },
            title = { Text(Strings.t("library.add_to_playlist"), color = OnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = trackSearchQuery,
                        onValueChange = { trackSearchQuery = it },
                        label = { Text(Strings.t("library.search")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredTracks.isEmpty()) {
                        Text(
                            text = if (trackSearchQuery.isBlank()) Strings.t("library.empty_tracks")
                            else Strings.t("folders.empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredTracks) { track ->
                                val isAlreadyAdded = track.id in existingTrackIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAlreadyAdded) {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    db.addTrackToPlaylist(selectedPlaylist!!.id, track.id)
                                                }
                                                loadTracks(selectedPlaylist!!.id)
                                                refreshPlaylists()
                                            }
                                        }
                                        .background(
                                            if (isAlreadyAdded) Primary.copy(alpha = 0.05f)
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isAlreadyAdded) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                                        contentDescription = null,
                                        tint = if (isAlreadyAdded) Primary.copy(alpha = 0.3f) else Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { track.filename },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = OnSurface
                                        )
                                        if (track.artist.isNotBlank()) {
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OnSurfaceVariant
                                            )
                                        }
                                    }
                                    if (track.bpm != null) {
                                        BpmBadge(bpm = track.bpm)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTracksDialog = false; trackSearchQuery = "" }) {
                    Text(Strings.t("common.save"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }
}
