package com.djapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
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
    var selectedPlaylistTracks by remember { mutableStateOf<List<com.djapp.data.local.entity.TrackEntity>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletingPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }

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
                text = "Playlists",
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
                Text("Neu", fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Primary)
                        }
                        Text(
                            text = selectedPlaylist!!.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GreenButton(
                        text = "Sync zu Speichermedium",
                        onClick = { /* sync via EngineDJSync */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedPlaylistTracks.isEmpty()) {
                EmptyState(
                    message = "Keine Tracks in dieser Playlist",
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
                        )
                    }
                }
            }
        } else {
            if (playlists.isEmpty()) {
                EmptyState(
                    message = "Keine Playlists vorhanden",
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
                                        Icon(Icons.Default.Check, contentDescription = "Speichern", tint = Primary)
                                    }
                                    IconButton(onClick = { editingPlaylistId = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Abbrechen", tint = ErrorRed)
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
            title = { Text("Neue Playlist", color = OnSurface) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Titel") },
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
                    Text("Erstellen", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Abbrechen", color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }
}
