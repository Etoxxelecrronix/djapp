package com.djapp.util

import android.content.Context
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.data.local.entity.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class UndoAction {
    data class CreatePlaylist(val playlistId: Long, val title: String) : UndoAction()
    data class DeletePlaylist(val playlistId: Long, val title: String, val trackIds: List<Long>, val parentId: Long? = null, val isFolder: Boolean = false, val createdAt: String = "") : UndoAction()
    data class AddTrackToPlaylist(val playlistId: Long, val trackId: Long, val playlistTitle: String, val trackName: String) : UndoAction()
    data class RemoveTrackFromPlaylist(val playlistId: Long, val trackId: Long, val playlistTitle: String, val trackName: String) : UndoAction()
    data class ImportFolder(val playlistId: Long, val folderName: String) : UndoAction()
}

object ActionHistory {
    private const val MAX_HISTORY = 50
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()

    fun push(action: UndoAction) {
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > MAX_HISTORY) {
            undoStack.removeAt(0)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun peekUndo(): UndoAction? = undoStack.lastOrNull()
    fun peekRedo(): UndoAction? = redoStack.lastOrNull()

    suspend fun undo(context: Context): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        val db = DJLibraryDatabase.getInstance(context)
        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    is UndoAction.CreatePlaylist -> {
                        db.playlistDao().delete(action.playlistId)
                    }
                    is UndoAction.DeletePlaylist -> {
                        db.playlistDao().insert(
                            PlaylistEntity(
                                id = action.playlistId,
                                title = action.title,
                                parentId = action.parentId,
                                isFolder = action.isFolder,
                                createdAt = action.createdAt,
                            )
                        )
                        action.trackIds.forEachIndexed { i, trackId ->
                            db.playlistDao().addTrack(
                                com.djapp.data.local.entity.PlaylistTrackEntity(
                                    playlistId = action.playlistId,
                                    trackId = trackId,
                                    position = i,
                                )
                            )
                        }
                    }
                    is UndoAction.AddTrackToPlaylist -> {
                        db.playlistDao().removeTrack(action.playlistId, action.trackId)
                    }
                    is UndoAction.RemoveTrackFromPlaylist -> {
                        db.playlistDao().addTrack(
                            com.djapp.data.local.entity.PlaylistTrackEntity(
                                playlistId = action.playlistId,
                                trackId = action.trackId,
                            )
                        )
                    }
                    is UndoAction.ImportFolder -> {
                        db.playlistDao().delete(action.playlistId)
                    }
                }
                redoStack.add(action)
                true
            } catch (e: Exception) {
                undoStack.add(action)
                false
            }
        }
    }

    suspend fun redo(context: Context): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        val db = DJLibraryDatabase.getInstance(context)
        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    is UndoAction.CreatePlaylist -> {
                        db.playlistDao().insert(
                            com.djapp.data.local.entity.PlaylistEntity(
                                id = action.playlistId,
                                title = action.title,
                            )
                        )
                    }
                    is UndoAction.DeletePlaylist -> {
                        db.playlistDao().delete(action.playlistId)
                    }
                    is UndoAction.AddTrackToPlaylist -> {
                        db.playlistDao().addTrack(
                            com.djapp.data.local.entity.PlaylistTrackEntity(
                                playlistId = action.playlistId,
                                trackId = action.trackId,
                            )
                        )
                    }
                    is UndoAction.RemoveTrackFromPlaylist -> {
                        db.playlistDao().removeTrack(action.playlistId, action.trackId)
                    }
                    is UndoAction.ImportFolder -> {}
                }
                undoStack.add(action)
                true
            } catch (e: Exception) {
                redoStack.add(action)
                false
            }
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun undoDescription(): String? = when (val a = undoStack.lastOrNull()) {
        is UndoAction.CreatePlaylist -> "Playlist \"${a.title}\" löschen"
        is UndoAction.DeletePlaylist -> "Playlist \"${a.title}\" wiederherstellen"
        is UndoAction.AddTrackToPlaylist -> "\"${a.trackName}\" aus ${a.playlistTitle} entfernen"
        is UndoAction.RemoveTrackFromPlaylist -> "\"${a.trackName}\" zu ${a.playlistTitle} hinzufügen"
        is UndoAction.ImportFolder -> "Import \"${a.folderName}\" rückgängig"
        null -> null
    }

    fun redoDescription(): String? = when (val a = redoStack.lastOrNull()) {
        is UndoAction.CreatePlaylist -> "Playlist \"${a.title}\" wiederherstellen"
        is UndoAction.DeletePlaylist -> "Playlist \"${a.title}\" löschen"
        is UndoAction.AddTrackToPlaylist -> "\"${a.trackName}\" zu ${a.playlistTitle} hinzufügen"
        is UndoAction.RemoveTrackFromPlaylist -> "\"${a.trackName}\" aus ${a.playlistTitle} entfernen"
        is UndoAction.ImportFolder -> "Import \"${a.folderName}\" wiederholen"
        null -> null
    }
}
