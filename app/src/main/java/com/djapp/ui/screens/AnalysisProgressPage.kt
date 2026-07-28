package com.djapp.ui.screens

import android.net.Uri
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.djapp.analysis.AnalysisQueue
import com.djapp.analysis.QueueItem
import com.djapp.analysis.TrackStatus
import com.djapp.scanner.MusicScanner
import com.djapp.ui.components.BpmBadge
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.KeyBadge
import com.djapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisProgressPage(folderPath: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPaused by remember { mutableStateOf(false) }
    var isStarted by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }

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

    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    fun startAnalysis() {
        if (folderPath.isBlank()) return
        isStarted = true
        scope.launch {
            scanMessage = "Scanne Ordner..."
            val scanResult = withContext(Dispatchers.IO) {
                MusicScanner.scanMusicLibrary(context, folderPath) { }
            }
            if (scanResult.totalTracks == 0) {
                scanMessage = "Keine Tracks gefunden"
                isStarted = false
                return@launch
            }
            scanMessage = "${scanResult.totalTracks} Tracks gefunden, starte Analyse..."
            val trackPairs = scanResult.folders.flatMap { folder ->
                folder.tracks.map { scanTrack ->
                    val file = File(scanTrack.path)
                    Uri.fromFile(file) to scanTrack.name
                }
            }
            AnalysisQueue.enqueue(trackPairs, context)
        }
    }

    fun stopAnalysis() {
        AnalysisQueue.clearQueue()
        isStarted = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Analyse",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = folderPath.ifBlank { "Alle Ordner" },
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .size(100.dp)
                                .rotate(if (isPaused) 0f else rotation),
                            color = Primary,
                            trackColor = SurfaceVariant,
                            strokeWidth = 8.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { if (totalCount > 0) 1f else 0f },
                            modifier = Modifier.size(100.dp),
                            color = if (doneCount == totalCount && totalCount > 0) Primary else SurfaceVariant,
                            trackColor = SurfaceVariant,
                            strokeWidth = 8.dp
                        )
                    }
                    Text(
                        text = if (totalCount > 0) "${(overallProgress * 100).toInt()}%" else "0%",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (totalCount > 0) {
                    Text(
                        text = "$doneCount / $totalCount Tracks analysiert",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (errorCount > 0) {
                        Text(
                            text = "$errorCount Fehler",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                } else {
                    Text(
                        text = scanMessage.ifBlank { "Bereit zur Analyse" },
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isStarted || !isRunning) {
                        GreenButton(
                            text = "Analyse starten",
                            onClick = { startAnalysis() },
                            enabled = folderPath.isNotBlank()
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { isPaused = !isPaused },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isPaused) Primary else BpmBadge,
                                contentColor = Secondary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPaused) "Fortsetzen" else "Pause")
                        }

                        OutlinedButton(
                            onClick = { stopAnalysis() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stopp")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (doneCount > 0 && !isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Analyse abgeschlossen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tracks analysiert: $doneCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface
                    )
                    if (analyzedBpm.isNotEmpty()) {
                        Text(
                            text = "BPM-Bereich: ${String.format("%.0f", analyzedBpm.min())} - ${String.format("%.0f", analyzedBpm.max())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface
                        )
                    }
                    if (analyzedKeys.isNotEmpty()) {
                        Text(
                            text = "Keys: ${analyzedKeys.groupingBy { it }.eachCount().entries.joinToString { "${it.key} (${it.value})" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (items.isEmpty()) {
            EmptyState(
                message = if (isStarted) "Lade Tracks..." else "Noch keine Tracks in der Warteschlange",
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                    TrackStatus.ANALYZING -> BpmBadge
                                    TrackStatus.ERROR -> ErrorRed
                                    TrackStatus.QUEUED -> OnSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface
                                )
                                if (item.status == TrackStatus.ANALYZING && item.progress > 0f) {
                                    LinearProgressIndicator(
                                        progress = { item.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        color = Primary,
                                        trackColor = SurfaceVariant
                                    )
                                }
                                if (item.status == TrackStatus.ERROR && item.error != null) {
                                    Text(
                                        text = item.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRed
                                    )
                                }
                            }
                            if (item.result?.bpm != null) BpmBadge(bpm = item.result.bpm!!)
                            if (item.result?.camelotKey != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                KeyBadge(key = item.result!!.camelotKey!!)
                            }
                        }
                    }
                }
            }
        }
    }
}
