package com.djapp.engine

import android.content.Context
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

    private val USB_PATHS = listOf(
        "/storage/usb0", "/storage/usb1", "/storage/usbdisk",
        "/storage/UsbDriveA", "/storage/UsbDriveB", "/mnt/usb_storage",
        "/mnt/usb", "/mnt/media_rw/usb0", "/mnt/media_rw/usb1",
        "/mnt/media_rw/udisk0", "/mnt/media_rw/udisk1", "/storage/usb",
    )

    private val INTERNAL_PATHS = listOf(
        "/storage/emulated/0" to "",
    )

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
            } catch (_: Exception) {
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

    suspend fun detectUsbVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        for (path in USB_PATHS) {
            detectVolume(context, path, path.split("/").last(), VolumeType.USB)?.let { found.add(it) }
        }

        return found
    }

    suspend fun detectAllVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        for (path in USB_PATHS) {
            detectVolume(context, path, path.split("/").last(), VolumeType.USB)?.let { found.add(it) }
        }

        for ((path, _) in INTERNAL_PATHS) {
            detectVolume(context, path, Strings.t("volume.internal"), VolumeType.INTERNAL)?.let { found.add(it) }
        }

        return found
    }
}
