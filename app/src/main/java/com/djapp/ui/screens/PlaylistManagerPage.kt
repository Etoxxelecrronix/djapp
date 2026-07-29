package com.djapp.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.InternalEngineDB
import com.djapp.i18n.Strings
import com.djapp.util.PrefsKeys
import com.djapp.ui.components.BpmBadge
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.components.PlaylistListItem
import com.djapp.ui.components.TrackListItem
import com.djapp.ui.theme.CardBackground
import com.djapp.ui.theme.ErrorRed
import com.djapp.ui.theme.OnSurface
import com.djapp.ui.theme.OnSurfaceVariant
import com.djapp.ui.theme.Primary
import com.djapp.ui.theme.Secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistManagerPage(onTrackClick: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DJLibraryDatabase.getInstance(context) }

    var playlists by remember { mutableStateOf<List<PlaylistWithCount>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var allTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showDeletePlaylistDialog by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var showUndoRedoDialog by remember { mutableStateOf(false) }
    var undoRedoMessage by remember { mutableStateOf<String?>(null) }
    var showPlaylistContextMenu by remember { mutableStateOf<PlaylistWithCount?>(null) }

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

    fun syncPlaylistToUsb(playlist: PlaylistWithCount, tracks: List<TrackEntity>) {
        scope.launch {
            val prefs = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val selectedPath = prefs.getString(PrefsKeys.SELECTED_PATH, null)
            if (selectedPath.isNullOrBlank()) return@launch

            val volume = withContext(Dispatchers.IO) {
                EngineVolumeDetector.detectVolumeAtPath(context, selectedPath)
            }
            if (volume == null) return@launch

            val playlistEntity = PlaylistEntity(
                id = playlist.id,
                title = playlist.title,
                parentId = playlist.parentId,
                isFolder = playlist.isFolder,
                createdAt = playlist.createdAt,
            )
            withContext(Dispatchers.IO) {
                EngineDJSync.syncToEngineDJ(
                    context = context,
                    volumePath = volume.path,
                    tracks = tracks,
                    playlists = listOf(playlistEntity to tracks),
                )
            }
        }
    }

    LaunchedEffect(Unit) { refreshPlaylists() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.t("playlists.title"),
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (com.djapp.util.ActionHistory.canUndo() || com.djapp.util.ActionHistory.canRedo()) {
                    FilledTonalButton(
                        onClick = { showUndoRedoDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Primary.copy(alpha = 0.15f),
                            contentColor = Primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Primary)
                        }
                        Text(
                            text = selectedPlaylist?.title ?: "",
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
                            onClick = {
                                val pl = selectedPlaylist
                                if (pl != null) {
                                    syncPlaylistToUsb(pl, selectedPlaylistTracks)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedPlaylistTracks.isEmpty()) {
                EmptyState(
                    message = Strings.t("playlists.empty"),
                    icon = Icons.AutoMirrored.Filled.QueueMusic
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
                            onClick = { onTrackClick(track.id) },
                            onLongClick = {
                                val pl = selectedPlaylist
                                if (pl != null) {
                                    scope.launch {
                                        com.djapp.util.ActionHistory.push(
                                            com.djapp.util.UndoAction.RemoveTrackFromPlaylist(
                                                pl.id, track.id, pl.title,
                                                track.title.ifBlank { track.filename }
                                            )
                                        )
                                        withContext(Dispatchers.IO) {
                                            db.playlistDao().removeTrack(pl.id, track.id)
                                            InternalEngineDB.syncFromRoom(context)
                                        }
                                        loadTracks(pl.id)
                                        refreshPlaylists()
                                    }
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
                    icon = Icons.AutoMirrored.Filled.QueueMusic
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
                                    showPlaylistContextMenu = playlist
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
                val newId = withContext(Dispatchers.IO) {
                    val id = db.createPlaylist(newTitle)
                    InternalEngineDB.syncFromRoom(context)
                    id
                }
                showCreateDialog = false
                refreshPlaylists()
                com.djapp.util.ActionHistory.push(
                    com.djapp.util.UndoAction.CreatePlaylist(newId, newTitle)
                )
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

    if (showPlaylistContextMenu != null) {
        val pl = showPlaylistContextMenu!!
        AlertDialog(
            onDismissRequest = { showPlaylistContextMenu = null },
            title = { Text(pl.title, color = OnSurface) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPlaylistContextMenu = null
                            editTitle = pl.title
                            editingPlaylistId = pl.id
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.t("common.save"), color = OnSurface)
                    }
                    TextButton(
                        onClick = {
                            showPlaylistContextMenu = null
                            showDeletePlaylistDialog = pl
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.t("playlists.delete"), color = ErrorRed)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistContextMenu = null }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showDeletePlaylistDialog != null) {
        val delPl = showDeletePlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = null },
            title = { Text(Strings.t("playlists.delete"), color = OnSurface) },
            text = {
                Text(Strings.t("playlists.delete_confirm", delPl.title), color = OnSurface)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val tracks = withContext(Dispatchers.IO) {
                                db.playlistDao().getTracks(delPl.id)
                            }
                            com.djapp.util.ActionHistory.push(
                                com.djapp.util.UndoAction.DeletePlaylist(
                                    playlistId = delPl.id,
                                    title = delPl.title,
                                    trackIds = tracks.map { it.id },
                                    parentId = delPl.parentId,
                                    isFolder = delPl.isFolder,
                                    createdAt = delPl.createdAt,
                                )
                            )
                            withContext(Dispatchers.IO) {
                                db.playlistDao().delete(delPl.id)
                                InternalEngineDB.syncFromRoom(context)
                            }
                            showDeletePlaylistDialog = null
                            refreshPlaylists()
                            if (selectedPlaylist?.id == delPl.id) {
                                selectedPlaylist = null
                                selectedPlaylistTracks = emptyList()
                            }
                            undoRedoMessage = Strings.t("playlists.delete_done")
                        }
                    }
                ) {
                    Text(Strings.t("playlists.delete"), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = null }) {
                    Text(Strings.t("common.cancel"), color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showUndoRedoDialog) {
        val undoDesc = com.djapp.util.ActionHistory.undoDescription()
        val redoDesc = com.djapp.util.ActionHistory.redoDescription()
        AlertDialog(
            onDismissRequest = { showUndoRedoDialog = false },
            title = { Text(Strings.t("common.undo_redo"), color = OnSurface) },
            text = {
                Column {
                    if (undoDesc != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    com.djapp.util.ActionHistory.undo(context)
                                    showUndoRedoDialog = false
                                    refreshPlaylists()
                                    if (selectedPlaylist != null) loadTracks(selectedPlaylist!!.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(Strings.t("common.undo"), color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text(undoDesc, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (redoDesc != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    com.djapp.util.ActionHistory.redo(context)
                                    showUndoRedoDialog = false
                                    refreshPlaylists()
                                    if (selectedPlaylist != null) loadTracks(selectedPlaylist!!.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = Primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(Strings.t("common.redo"), color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text(redoDesc, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (undoDesc == null && redoDesc == null) {
                        Text(
                            text = Strings.t("common.undo_empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUndoRedoDialog = false }) {
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
                                            val plId = selectedPlaylist?.id ?: return@clickable
                                            scope.launch {
                                                com.djapp.util.ActionHistory.push(
                                                    com.djapp.util.UndoAction.AddTrackToPlaylist(
                                                        plId, track.id,
                                                        selectedPlaylist!!.title,
                                                        track.title.ifBlank { track.filename }
                                                    )
                                                )
                                                withContext(Dispatchers.IO) {
                                                    db.addTrackToPlaylist(plId, track.id)
                                                    InternalEngineDB.syncFromRoom(context)
                                                }
                                                loadTracks(plId)
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
