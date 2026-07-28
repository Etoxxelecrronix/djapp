package com.djapp.engine

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
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

    private fun hasEngineDb(path: String): Boolean {
        return try {
            File(path, ENGINE_DB_RELATIVE).exists()
        } catch (_: Exception) { false }
    }

    private fun getTrackCount(context: Context, path: String): Int {
        return try {
            if (hasEngineDb(path)) EngineDJDatabase.trackCount(context, path) else 0
        } catch (_: Exception) { 0 }
    }

    private fun buildVolume(context: Context, path: String, label: String, type: VolumeType): EngineVolume? {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return null
        val canRead = try { dir.canRead() } catch (_: Exception) { false }
        if (!canRead) return null

        return EngineVolume(
            path = path,
            label = label,
            type = type,
            hasEngineLibrary = hasEngineDb(path),
            trackCount = getTrackCount(context, path),
        )
    }

    @Suppress("DEPRECATION")
    private fun detectViaStorageManager(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return found

        for (vol: StorageVolume in sm.storageVolumes) {
            val dir = vol.directory ?: continue
            val path = dir.absolutePath

            if (path == "/storage/emulated/0") continue

            val label = vol.getDescription(context) ?: dir.name
            val type = if (vol.isRemovable) VolumeType.USB else VolumeType.INTERNAL

            buildVolume(context, path, label, type)?.let { found.add(it) }
        }

        return found
    }

    private fun detectViaStorageDir(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()
        val storageDir = File("/storage")
        if (!storageDir.exists() || !storageDir.isDirectory) return found

        val knownInternal = "/storage/emulated/0"
        val knownSkip = setOf("emulated", "self", "sdcard0", "sdcard1")

        for (dir in storageDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()) {
            if (dir.name in knownSkip) continue
            val path = dir.absolutePath
            if (path == knownInternal) continue

            val canRead = try { dir.canRead() } catch (_: Exception) { false }
            if (!canRead) continue

            val label = dir.name
            val type = VolumeType.USB
            buildVolume(context, path, label, type)?.let { found.add(it) }
        }

        return found
    }

    private fun detectViaMnt(): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()
        val mntDir = File("/mnt")
        if (!mntDir.exists() || !mntDir.isDirectory) return found

        for (dir in mntDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            val path = dir.absolutePath
            val name = dir.name.lowercase()
            if (!name.contains("usb") && !name.contains("udisk") && !name.contains("media_rw")) continue

            val canRead = try { dir.canRead() } catch (_: Exception) { false }
            if (!canRead) continue

            buildVolume(null as Context?, path, dir.name, VolumeType.USB)?.let { found.add(it) }
        }

        return found
    }

    private fun detectInternal(context: Context): EngineVolume? {
        val internalPath = "/storage/emulated/0"
        return buildVolume(context, internalPath, "Interner Speicher", VolumeType.INTERNAL)
    }

    suspend fun detectUsbVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        found.addAll(detectViaStorageManager(context))
        found.addAll(detectViaStorageDir(context))
        found.addAll(detectViaMnt())

        return found.distinctBy { it.path }
    }

    suspend fun detectAllVolumes(context: Context): List<EngineVolume> {
        val found = mutableListOf<EngineVolume>()

        found.addAll(detectViaStorageManager(context))
        found.addAll(detectViaStorageDir(context))
        found.addAll(detectViaMnt())

        detectInternal(context)?.let { found.add(it) }

        return found.distinctBy { it.path }
    }
}
