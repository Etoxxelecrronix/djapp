package com.djapp.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import com.djapp.engine.EngineVolume
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.VolumeType
import com.djapp.i18n.Strings
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "dj_usb_selected"
private const val KEY_SELECTED_PATH = "usb_selected_path"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbStickPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    var volumes by remember { mutableStateOf(emptyList<EngineVolume>()) }
    var selectedPath by remember { mutableStateOf(prefs.getString(KEY_SELECTED_PATH, null) ?: "") }
    var isScanning by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val path = docFile?.uri?.path ?: uri.path ?: ""
                selectedPath = path
                prefs.edit().putString(KEY_SELECTED_PATH, path).apply()
            }
        }
    }

    fun scanVolumes() {
        isScanning = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                EngineVolumeDetector.detectUsbVolumes(context)
            }
            volumes = found
            isScanning = false
        }
    }

    fun selectVolume(volume: EngineVolume) {
        selectedPath = volume.path
        prefs.edit().putString(KEY_SELECTED_PATH, volume.path).apply()
    }

    LaunchedEffect(Unit) {
        scanVolumes()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = Strings.t("usb.title"),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = Strings.t("usb.subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GreenButton(
                text = Strings.t("usb.scan"),
                onClick = { scanVolumes() }
            )
            OutlinedGreenButton(
                text = Strings.t("usb.manual_folder"),
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    folderPickerLauncher.launch(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = Strings.t("usb.scanning"),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (selectedPath.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, Primary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(Strings.t("usb.selected"), style = MaterialTheme.typography.labelMedium, color = Primary)
                        Text(
                            selectedPath.split("/").takeLast(2).joinToString("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                        )
                    }
                }
            }
        }

        if (volumes.isEmpty() && !isScanning) {
            EmptyState(
                message = Strings.t("usb.no_devices"),
                icon = Icons.Default.Usb,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(volumes) { volume ->
                    val isSelected = selectedPath == volume.path

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectVolume(volume) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Primary.copy(alpha = 0.12f) else CardBackground,
                        ),
                        border = if (isSelected) BorderStroke(2.dp, Primary) else null,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when (volume.type) {
                                    VolumeType.USB -> Icons.Default.Usb
                                    VolumeType.SD -> Icons.Default.SdCard
                                    VolumeType.INTERNAL -> Icons.Default.PhoneAndroid
                                },
                                contentDescription = null,
                                tint = if (isSelected) Primary else OnSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = volume.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${volume.trackCount} ${Strings.t("usb.tracks")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant,
                                )
                                Text(
                                    text = volume.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            if (volume.hasEngineLibrary) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = Strings.t("usb.engine_found"),
                                    tint = Primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
