package com.djapp.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.djapp.engine.EngineVolume
import com.djapp.engine.EngineVolumeDetector
import com.djapp.engine.VolumeType
import com.djapp.i18n.Strings
import com.djapp.ui.components.EmptyState
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.theme.CardBackground
import com.djapp.ui.theme.OnSurface
import com.djapp.ui.theme.OnSurfaceVariant
import com.djapp.ui.theme.Primary
import com.djapp.util.PrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolves a SAF tree URI (from ACTION_OPEN_DOCUMENT_TREE) to a real file path. */
private fun resolveSafTreeUri(uri: android.net.Uri): String? {
    if (uri.scheme != "content") return uri.path
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        val storageType = split[0]
        val relativePath = if (split.size > 1) split[1] else ""
        val resolved = if (storageType.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
        } else {
            "/storage/$storageType/$relativePath"
        }
        return resolved.trimEnd('/')
    } catch (_: Exception) {
        return uri.path?.trimEnd('/')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbStickPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }

    var volumes by remember { mutableStateOf(emptyList<EngineVolume>()) }
    var selectedPath by remember { mutableStateOf(prefs.getString(PrefsKeys.SELECTED_PATH, null) ?: "") }
    var isScanning by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val realPath = resolveSafTreeUri(uri)
                if (realPath != null) {
                    selectedPath = realPath
                    prefs.edit().putString(PrefsKeys.SELECTED_PATH, realPath).apply()
                }
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
        prefs.edit().putString(PrefsKeys.SELECTED_PATH, volume.path).apply()
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
