package com.djapp.engine

import android.content.Context
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
        "/storage/usb0" to "USB-Stick",
        "/storage/usb1" to "USB-Stick 1",
        "/storage/usbdisk" to "USB-Stick",
        "/storage/UsbDriveA" to "USB-Stick A",
        "/storage/UsbDriveB" to "USB-Stick B",
        "/mnt/usb_storage" to "USB-Speicher",
        "/mnt/usb" to "USB",
        "/mnt/media_rw/usb0" to "USB (usb0)",
        "/mnt/media_rw/usb1" to "USB (usb1)",
        "/mnt/media_rw/udisk0" to "USB-Disk 0",
        "/mnt/media_rw/udisk1" to "USB-Disk 1",
        "/storage/usb" to "USB-Stick",
    )

    private val INTERNAL_PATHS = listOf(
        "/storage/emulated/0" to "Interner Speicher",
        "/sdcard" to "SD-Karte / Interner Speicher",
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

    suspend fun detectUsbVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        for ((path, label) in USB_PATHS) {
            detectVolume(context, path, label, VolumeType.USB)?.let { found.add(it) }
        }

        for ((path, label) in INTERNAL_PATHS) {
            detectVolume(context, path, label, VolumeType.INTERNAL)?.let { found.add(it) }
        }

        return found
    }
}
