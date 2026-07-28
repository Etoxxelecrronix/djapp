package com.djapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

import com.djapp.data.local.DJLibraryDatabase
import com.djapp.i18n.Strings
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.theme.*
import com.djapp.util.PrefsKeys

@Composable
fun HomePage(
    onNavigateToUsbStick: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
) {
    var totalTracks by remember { mutableIntStateOf(0) }
    var analyzedTracks by remember { mutableIntStateOf(0) }
    var playlistCount by remember { mutableIntStateOf(0) }
    var hasUsbPath by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        val db = DJLibraryDatabase.getInstance(context)
        val stats = db.getLibraryStats()
        totalTracks = stats.totalTracks
        analyzedTracks = stats.analyzedTracks
        playlistCount = stats.totalPlaylists
        hasUsbPath = prefs.getString(PrefsKeys.SELECTED_PATH, null).isNullOrBlank().not()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Secondary, Surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = Strings.t("home.title"),
                style = MaterialTheme.typography.displayLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = Strings.t("home.subtitle"),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            GreenButton(
                text = Strings.t("home.select_usb"),
                onClick = onNavigateToUsbStick
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = Strings.t("home.browse_folders"),
                onClick = onNavigateToFolders
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = Strings.t("home.start_analysis"),
                onClick = { if (hasUsbPath) onNavigateToAnalysis() else onNavigateToUsbStick() },
                enabled = hasUsbPath
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = Strings.t("home.library"),
                onClick = onNavigateToLibrary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = Strings.t("home.library"),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = Strings.t("home.tracks"), value = "$totalTracks", icon = Icons.Default.MusicNote)
                        StatItem(label = Strings.t("home.analyzed"), value = "$analyzedTracks", icon = Icons.Default.Analytics)
                        StatItem(label = Strings.t("home.playlists_count"), value = "$playlistCount", icon = Icons.Default.PlaylistPlay)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}
