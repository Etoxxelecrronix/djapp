package com.djapp.engine

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

data class EngineVolume(
    val path: String,
    val label: String,
    val type: VolumeType,
    val hasEngineLibrary: Boolean,
    val trackCount: Int,
)

enum class VolumeType { SD, USB, INTERNAL }

object EngineVolumeDetector {

    private fun getVolumeLabel(dir: File): String {
        return dir.name.ifBlank { dir.absolutePath.split("/").lastOrNull() ?: "Unbekannt" }
    }

    private fun isUsbVolume(context: Context, path: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (sm != null) {
                    for (vol in sm.storageVolumes) {
                        val volDir = vol.directory
                        if (volDir != null && path.startsWith(volDir.absolutePath)) {
                            return vol.isRemovable
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val lowerPath = path.lowercase()
        return lowerPath.contains("usb") || lowerPath.contains("udisk") ||
                lowerPath.contains("usbdrive") || lowerPath.contains("media_rw")
    }

    private fun detectVolume(
        context: Context,
        path: String,
        label: String,
        type: VolumeType,
    ): EngineVolume? {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return null

        val canRead = try { dir.canRead() } catch (_: Exception) { false }
        if (!canRead) return null

        val dbFile = File(path, ENGINE_DB_RELATIVE)
        val hasEngineLibrary = dbFile.exists()

        var trackCount = 0
        if (hasEngineLibrary) {
            try {
                trackCount = EngineDJDatabase.trackCount(context, path)
            } catch (_: Exception) {}
        }

        return EngineVolume(
            path = path,
            label = label,
            type = type,
            hasEngineLibrary = hasEngineLibrary,
            trackCount = trackCount,
        )
    }

    suspend fun detectUsbVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            val skipDirs = setOf("emulated", "self", "sdcard0")
            for (dir in storageDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()) {
                if (dir.name in skipDirs) continue
                val path = dir.absolutePath
                val type = if (isUsbVolume(context, path)) VolumeType.USB else VolumeType.USB
                val label = getVolumeLabel(dir)
                detectVolume(context, path, label, type)?.let { found.add(it) }
            }
        }

        val mntDir = File("/mnt")
        if (mntDir.exists() && mntDir.isDirectory) {
            for (dir in mntDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
                val path = dir.absolutePath
                if (path.contains("usb") || path.contains("udisk") || path.contains("media_rw")) {
                    val label = getVolumeLabel(dir)
                    if (found.none { it.path == path }) {
                        detectVolume(context, path, label, VolumeType.USB)?.let { found.add(it) }
                    }
                }
            }
        }

        val legacyPaths = listOf(
            "/mnt/usb_storage", "/mnt/usb",
            "/mnt/media_rw/usb0", "/mnt/media_rw/usb1",
            "/mnt/media_rw/udisk0", "/mnt/media_rw/udisk1",
        )
        for (path in legacyPaths) {
            if (found.none { it.path == path }) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val label = getVolumeLabel(dir)
                    detectVolume(context, path, label, VolumeType.USB)?.let { found.add(it) }
                }
            }
        }

        return found.distinctBy { it.path }
    }

    suspend fun detectAllVolumes(context: Context): List<EngineVolume> {
        val found = detectUsbVolumes(context).toMutableList()

        val internalPath = "/storage/emulated/0"
        if (found.none { it.path == internalPath }) {
            detectVolume(context, internalPath, "Interner Speicher", VolumeType.INTERNAL)
                ?.let { found.add(it) }
        }

        return found.distinctBy { it.path }
    }
}
