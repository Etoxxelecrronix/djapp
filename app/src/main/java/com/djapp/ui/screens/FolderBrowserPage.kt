package com.djapp.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djapp.scanner.FolderStat
import com.djapp.scanner.MusicScanner
import com.djapp.scanner.ScanPhase
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.FolderListItem
import com.djapp.ui.components.GreenButton
import com.djapp.ui.theme.*
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<FolderStat?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<FolderStat>>(emptyList()) }

    val prefs = remember { context.getSharedPreferences("dj_usb_selected", Context.MODE_PRIVATE) }
    val selectedPath = prefs.getString("usb_selected_path", null) ?: ""

    fun doScan() {
        if (selectedPath.isBlank()) return
        isScanning = true
        scanProgress = "Scanne..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MusicScanner.scanMusicLibrary(context, selectedPath) { progress ->
                    scanProgress = if (progress.phase == ScanPhase.DONE)
                        "${progress.found} Tracks gefunden"
                    else
                        "Scanne: ${progress.currentPath}"
                }
            }
            folders = result.folders
            isScanning = false
            scanProgress = "${result.totalTracks} Tracks in ${result.folders.size} Ordnern"
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
            .padding(16.dp)
    ) {
        Text(
            text = "Ordner durchsuchen",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )

        if (selectedPath.isBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            EmptyState(
                message = "Kein Speichermedium ausgewählt. Bitte unter Speichermedium eines auswählen.",
                icon = Icons.Default.UsbOff
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selectedPath,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Ordner suchen...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Löschen")
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
                    text = if (isScanning) scanProgress else "Neu scannen",
                    onClick = { doScan() },
                    enabled = !isScanning
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    message = if (searchQuery.isNotBlank()) "Keine Ordner gefunden" else "Keine Ordner vorhanden",
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
            title = { Text(selectedFolder!!.name, color = OnSurface) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onNavigateToAnalysis(selectedFolder!!.path)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ordner analysieren", color = OnSurface)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text("Abbrechen", color = Primary)
                }
            },
            containerColor = CardBackground
        )
    }
}
