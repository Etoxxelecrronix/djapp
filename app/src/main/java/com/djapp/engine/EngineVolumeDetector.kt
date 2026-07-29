package com.djapp.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import com.djapp.i18n.Strings
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

    private fun detectVolume(
        context: Context,
        path: String,
        label: String,
        type: VolumeType,
    ): EngineVolume? {
        val dir = File(path)
        if (!dir.exists() || !dir.canRead()) return null

        val dbFile = File(path, ENGINE_DB_RELATIVE)
        val hasEngineLibrary = dbFile.exists()

        var trackCount = 0
        if (hasEngineLibrary) {
            try {
                trackCount = EngineDJDatabase.trackCount(context, path)
            } catch (e: Exception) {
                Log.w("EngineVolDetect", "trackCount failed for $path", e)
            }
        }

        return EngineVolume(
            path = path,
            label = label,
            type = type,
            hasEngineLibrary = hasEngineLibrary,
            trackCount = trackCount,
        )
    }

    suspend fun detectVolumeAtPath(context: Context, path: String): EngineVolume? {
        val label = when {
            path.startsWith("/storage/emulated") -> Strings.t("volume.internal")
            path.startsWith("/storage/") -> Strings.t("volume.usb_manual")
            path.startsWith("/mnt/") -> Strings.t("volume.usb_manual")
            else -> Strings.t("volume.folder")
        }
        val type = if (path.startsWith("/storage/emulated")) VolumeType.INTERNAL else VolumeType.USB
        return detectVolume(context, path, label, type)
    }

    /**
     * Dynamisch alle verfügbaren USB-/SD-Karten-Volumes erkennen.
     *
     * Ersetzt die alte hartcodierte Pfadliste durch:
     * 1. [StorageManager.getStorageVolumes] (API 24+) – zuverlässigste Quelle
     * 2. Scan von /storage/ – für OEM-spezifische Mounts
     * 3. Scan von /mnt/media_rw/ – für USB-OTG auf vielen Geräten
     * 4. /proc/mounts-Parsing als Fallback
     */
    suspend fun detectUsbVolumes(context: Context): List<EngineVolume> {
        val seen = mutableSetOf<String>()
        val found = mutableListOf<EngineVolume>()

        fun tryAdd(path: String, label: String, type: VolumeType) {
            val normalized = path.trimEnd('/')
            if (normalized in seen) return
            seen.add(normalized)
            detectVolume(context, normalized, label, type)?.let { found.add(it) }
        }

        // 1. StorageManager API (primäre Quelle)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null) {
            val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                storageManager.storageVolumes
            } else {
                @Suppress("DEPRECATION")
                storageManager.volumeList ?: emptyList()
            }
            for (vol in volumes) {
                val dir = vol.directory?.absolutePath ?: vol.path
                val isRemovable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    vol.isRemovable
                } else {
                    @Suppress("DEPRECATION")
                    vol.isRemovable
                }
                val label = getVolumeLabel(context, vol) ?: dir.split("/").last()
                val type = when {
                    !isRemovable -> VolumeType.INTERNAL
                    dir.contains("sdcard", ignoreCase = true) -> VolumeType.SD
                    else -> VolumeType.USB
                }
                tryAdd(dir, label, type)
            }
        }

        // 2. /storage/ scannen (USB-Mounts wie /storage/XXXX-XXXX)
        try {
            val storageDir = File("/storage")
            if (storageDir.exists()) {
                for (entry in storageDir.listFiles().orEmpty()) {
                    if (entry.name in setOf("emulated", "self", "legacy")) continue
                    if (!entry.isDirectory || !entry.canRead()) continue
                    val label = Strings.t("volume.usb_manual") + " (${entry.name})"
                    tryAdd(entry.absolutePath, label, VolumeType.USB)
                }
            }
        } catch (e: Exception) {
            Log.w("EngineVolDetect", "scan /storage failed", e)
        }

        // 3. /mnt/media_rw/ scannen (alternative USB-OTG-Mounts)
        try {
            val mntDir = File("/mnt/media_rw")
            if (mntDir.exists()) {
                for (entry in mntDir.listFiles().orEmpty()) {
                    if (!entry.isDirectory || !entry.canRead()) continue
                    val label = Strings.t("volume.usb_manual") + " (${entry.name})"
                    tryAdd(entry.absolutePath, label, VolumeType.USB)
                }
            }
        } catch (e: Exception) {
            Log.w("EngineVolDetect", "scan /mnt/media_rw failed", e)
        }

        // 4. /proc/mounts parsen als Fallback
        try {
            val mounts = File("/proc/mounts")
            if (mounts.canRead()) {
                val usbFsTypes = setOf("vfat", "exfat", "ntfs", "fuseblk", "ext4", "sdfat")
                for (line in mounts.readLines()) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 2) continue
                    val mntPoint = parts[1]
                    val fsType = parts.getOrNull(2) ?: ""
                    if (fsType !in usbFsTypes) continue
                    // Nur Pfade unter /storage/, /mnt/ oder /run/media/ interessieren
                    if (!mntPoint.startsWith("/storage/") &&
                        !mntPoint.startsWith("/mnt/") &&
                        !mntPoint.startsWith("/run/media/")) continue
                    val label = Strings.t("volume.usb_manual") + " (${mntPoint.split("/").last()})"
                    tryAdd(mntPoint, label, VolumeType.USB)
                }
            }
        } catch (e: Exception) {
            Log.w("EngineVolDetect", "parse /proc/mounts failed", e)
        }

        return found
    }

    suspend fun detectAllVolumes(context: Context): List<EngineVolume> {
        val found = detectUsbVolumes(context).toMutableList()

        // Internen Speicher immer ergänzen
        val internalPath = Environment.getExternalStorageDirectory().absolutePath
        val alreadyHasInternal = found.any { it.path == internalPath }
        if (!alreadyHasInternal) {
            detectVolume(context, internalPath, Strings.t("volume.internal"), VolumeType.INTERNAL)
                ?.let { found.add(it) }
        }

        return found
    }

    private fun getVolumeLabel(context: Context, vol: StorageVolume): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                (vol.description as? CharSequence)?.toString()
            } else {
                @Suppress("DEPRECATION")
                vol.getDescription(context)
            }
        } catch (_: Exception) {
            null
        }
    }
}
