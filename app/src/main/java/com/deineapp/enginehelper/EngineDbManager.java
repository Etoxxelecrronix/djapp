package com.deineapp.enginehelper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EngineDbManager {

    private final Context context;

    public static class TrackMetadata {
        String filename;
        String relativePath;
        String title;
        String artist;
        String album;
        long fileLength;

        public TrackMetadata(String filename, String relativePath, String title, String artist, String album, long fileLength) {
            this.filename = filename;
            this.relativePath = relativePath;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.fileLength = fileLength;
        }
    }

    public EngineDbManager(Context context) {
        this.context = context;
    }

    public void processFolderToPlaylist(Uri usbRootUri, Uri targetFolderUri) throws Exception {
        DocumentFile rootDoc = DocumentFile.fromTreeUri(context, usbRootUri);
        DocumentFile targetFolderDoc = DocumentFile.fromTreeUri(context, targetFolderUri);
        
        if (rootDoc == null || targetFolderDoc == null) return;
        
        String playlistName = targetFolderDoc.getName();
        if (playlistName == null) playlistName = "Neue Playlist";
        
        DocumentFile engineLib = rootDoc.findFile("Engine Library");
        DocumentFile db2 = (engineLib != null) ? engineLib.findFile("Database2") : null;
        DocumentFile dbDoc = (db2 != null) ? db2.findFile("m.db") : null;
        
        if (dbDoc == null) {
            throw new Exception("Engine DJ Datenbank (m.db) nicht auf dem Stick gefunden!");
        }

        File tempDbFile = new File(context.getCacheDir(), "engine_temp.db");
        try (InputStream input = context.getContentResolver().openInputStream(dbDoc.getUri())) {
             byte[] buffer = new byte[4096];
             int length;
             java.io.FileOutputStream fos = new java.io.FileOutputStream(tempDbFile);
             while ((length = input.read(buffer)) > 0) {
                 fos.write(buffer, 0, length);
             }
             fos.close();
        }

        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null);
        
        try {
            db.beginTransaction();

            String playlistId = UUID.randomUUID().toString();
            int playlistOrder = getNextPlaylistOrder(db);
            
            db.execSQL(
                "INSERT INTO Playlist (id, title, parentListId, playOrder) VALUES (?, ?, NULL, ?)",
                new Object[]{playlistId, playlistName, playlistOrder}
            );

            List<TrackMetadata> tracks = new ArrayList<>();
            scanFolderForAudio(targetFolderDoc, usbRootUri, tracks);
            
            int trackOrder = 1;
            for (TrackMetadata track : tracks) {
                String trackId = getTrackIdByPath(db, track.relativePath);
                
                if (trackId == null) {
                    trackId = UUID.randomUUID().toString();
                    db.execSQL(
                        "INSERT INTO Track (id, path, filename, title, artist, album, fileLength, isAnalyzed, isMetadataImported) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1)",
                        new Object[]{trackId, track.relativePath, track.filename, track.title, track.artist, track.album, track.fileLength}
                    );
                }

                String playlistTrackId = UUID.randomUUID().toString();
                db.execSQL(
                    "INSERT INTO PlaylistTrack (id, playlistId, trackId, trackOrder) VALUES (?, ?, ?, ?)",
                    new Object[]{playlistTrackId, playlistId, trackId, trackOrder}
                );
                trackOrder++;
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
            
            try (InputStream input = new java.io.FileInputStream(tempDbFile);
                 OutputStream output = context.getContentResolver().openOutputStream(dbDoc.getUri(), "rwt")) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }
            tempDbFile.delete();
        }
    }

    private int getNextPlaylistOrder(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT MAX(playOrder) FROM Playlist WHERE parentListId IS NULL", null);
        int maxOrder = 0;
        if (cursor.moveToFirst()) {
            maxOrder = cursor.getInt(0);
        }
        cursor.close();
        return maxOrder + 1;
    }

    private String getTrackIdByPath(SQLiteDatabase db, String relativePath) {
        Cursor cursor = db.rawQuery("SELECT id FROM Track WHERE path = ?", new String[]{relativePath});
        String id = null;
        if (cursor.moveToFirst()) {
            id = cursor.getString(0);
        }
        cursor.close();
        return id;
    }

    private void scanFolderForAudio(DocumentFile folder, Uri usbRootUri, List<TrackMetadata> trackList) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        DocumentFile[] files = folder.listFiles();

        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                scanFolderForAudio(file, usbRootUri, trackList);
            } else {
                String name = file.getName();
                if (name == null) continue;
                int dotIndex = name.lastIndexOf(".");
                if (dotIndex == -1) continue;
                String ext = name.substring(dotIndex + 1).toLowerCase();
                
                if (ext.equals("mp3") || ext.equals("wav") || ext.equals("flac") || ext.equals("m4a")) {
                    try {
                        retriever.setDataSource(context, file.getUri());
                        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        
                        if (title == null) title = name.substring(0, dotIndex);
                        if (artist == null) artist = "Unbekannt";
                        if (album == null) album = "Unbekannt";
                        
                        String relPath = getRelativePathFromUri(file.getUri(), usbRootUri);

                        trackList.add(new TrackMetadata(name, relPath, title, artist, album, file.length()));
                    } catch (Exception e) {
                        String relPath = getRelativePathFromUri(file.getUri(), usbRootUri);
                        trackList.add(new TrackMetadata(name, relPath, name.substring(0, dotIndex), "Unbekannt", "Unbekannt", file.length()));
                    }
                }
            }
        }
        try { retriever.release(); } catch (Exception ignored) {}
    }

    private String getRelativePathFromUri(Uri fileUri, Uri rootUri) {
        String rootPath = rootUri.getPath();
        String filePath = fileUri.getPath();
        
        String relative = filePath.replace(rootPath, "");
        relative = Uri.decode(relative);
        
        if (relative.startsWith("/")) relative = relative.substring(1);
        if (relative.startsWith(":")) relative = relative.substring(1);
        
        return relative;
    }
}
