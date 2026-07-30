package com.deineapp.enginehelper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.djapp.R;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_USB = 1001;
    private static final int REQUEST_CODE_FOLDER = 1002;

    private Uri usbRootUri = null;
    private Uri musicFolderUri = null;
    private EngineDbManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        dbManager = new EngineDbManager(this);

        Button btnSelectUsb = (Button) findViewById(R.id.btn_select_usb);
        Button btnSelectFolder = (Button) findViewById(R.id.btn_select_folder);
        Button btnRunSync = (Button) findViewById(R.id.btn_run_sync);

        btnSelectUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_CODE_USB);
            }
        });

        btnSelectFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_CODE_FOLDER);
            }
        });

        btnRunSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usbRootUri != null && musicFolderUri != null) {
                    Toast.makeText(MainActivity.this, "Verarbeitung läuft...", Toast.LENGTH_SHORT).show();
                    
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                dbManager.processFolderToPlaylist(usbRootUri, musicFolderUri);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Erfolgreich! Playlist wurde oben erstellt.", Toast.LENGTH_LONG).show();
                                    }
                                });
                            } catch (final Exception e) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                } else {
                    Toast.makeText(MainActivity.this, "Bitte erst USB und Ordner wählen!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
            
            if (requestCode == REQUEST_CODE_USB) {
                usbRootUri = uri;
                Toast.makeText(this, "USB-Stick registriert", Toast.LENGTH_SHORT).show();
            }
            if (requestCode == REQUEST_CODE_FOLDER) {
                musicFolderUri = uri;
                Toast.makeText(this, "Musik-Ordner registriert", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
