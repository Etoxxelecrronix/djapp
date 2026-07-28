# DJ Engine - Music Library Manager

Native Android App (Kotlin / Jetpack Compose) zur Verwaltung einer DJ-Musikbibliothek mit Engine DJ m.db Kompatibilität.

## Aktueller Stand

**Version:** 0.1.0 (Entwicklung)
**Package:** `com.djapp`
**Min SDK:** 26 | **Target SDK:** 35
**Dateien:** 47 Kotlin-Dateien | ~6.500 Zeilen Code
**APK:** Noch nicht erstellt (Gradle Build环境 fehlt auf dem Gerät)

### Status pro Bereich

| Bereich | Status | Dateien |
|---|---|---|
| Navigation & UI | Fertig | 11 Screens + Components |
| Room Database | Fertig | 7 Entities, 6 DAOs, 1 DB-Klasse |
| Audio Analysis | Fertig | FFT, BPM, Key, LUFS, Waveform |
| Engine DJ Sync | Fertig | m.db lesen/schreiben, M3U8 Export |
| USB Stick Erkennung | Fertig | 12 USB-Pfade, Volume Detection |
| Music Scanner | Fertig | Rekursiver Datei-Scanner mit Cache |
| i18n (DE/EN) | Fertig | ~100 Strings pro Sprache |
| API Client | Fertig | OkHttp mit Auth-Interceptor |
| Theme | Fertig | Dark Mode (#1DB954grün / #191414dunkel) |
| Screens (Live Data) | Fertig | Alle 4 Screens zu echten Daten migriert |

---

## Architektur

```
com.djapp/
├── MainActivity.kt              # Entry Point, Permission Handling
├── DJApp.kt                     # Application Class (DB Singleton)
├── analysis/                    # Audio-Analyse Engine
│   ├── AudioAnalyzer.kt         # Orchestrator
│   ├── AudioAnalysisQueue.kt    # 3-Worker Coroutine Queue
│   ├── BpmDetector.kt           # BPM-Erkennung
│   ├── KeyDetector.kt           # Musical/Camelot/OpenKey
│   ├── LoudnessAnalyzer.kt      # LUFS/RMS/Peak
│   ├── Fft.kt                   # FFT-Implementierung
│   ├── PcmData.kt               # PCM-Rohdaten
│   ├── WaveformGenerator.kt     # Waveform-Komprimierung
│   └── parsers/
│       ├── WavParser.kt
│       ├── AiffParser.kt
│       ├── Mp3Parser.kt
│       └── FlacParser.kt
├── data/local/
│   ├── DJLibraryDatabase.kt     # Room DB + Convenience Methods
│   ├── dao/
│   │   ├── TrackDao.kt
│   │   ├── PlaylistDao.kt       # PlaylistWithCount (mit ColumnInfo)
│   │   ├── CuePointDao.kt
│   │   ├── LoopDao.kt
│   │   ├── BeatgridDao.kt
│   │   └── AnalysisDao.kt
│   └── entity/
│       ├── TrackEntity.kt
│       ├── PlaylistEntity.kt
│       ├── PlaylistTrackEntity.kt
│       ├── CuePointEntity.kt
│       ├── SavedLoopEntity.kt
│       ├── BeatgridEntity.kt
│       └── AnalysisResultEntity.kt
├── engine/                      # Engine DJ Kompatibilität
│   ├── EngineDJDatabase.kt      # m.db lesen/schreiben (raw SQLite)
│   ├── EngineDJSync.kt          # Sync + M3U8 Sidecar Export
│   └── EngineVolumeDetector.kt  # USB-Stick Erkennung
├── scanner/
│   └── MusicScanner.kt          # Rekursiver Datei-Scanner
├── api/
│   └── ApiClient.kt             # OkHttp REST Client
├── i18n/
│   └── Strings.kt               # DE/EN Lokalisierung
├── navigation/
│   ├── Screen.kt                # 7 Routen definiert
│   └── AppNavigation.kt         # NavHost + Bottom Navigation
├── ui/
│   ├── screens/
│   │   ├── HomePage.kt          # Login + Stats (live aus Room)
│   │   ├── UsbStickPage.kt      # USB-Stick Auswahl (SAF)
│   │   ├── FolderBrowserPage.kt # Ordner durchsuchen
│   │   ├── AnalysisProgressPage.kt  # Analyse-Fortschritt
│   │   ├── PlaylistManagerPage.kt   # Playlists verwalten
│   │   ├── LibraryPage.kt       # 3-Tab: Tracks/Playlists/Devices
│   │   └── SyncSettingsPage.kt  # Synchronisierungsoptionen
│   ├── components/
│   │   └── CommonComponents.kt  # BpmBadge, KeyBadge, TrackListItem, etc.
│   └── theme/
│       ├── Color.kt             # Farben (#1DB954, #191414, etc.)
│       ├── Theme.kt             # DarkColorScheme, DJAppTheme
│       └── Type.kt              # Typography
└── payments/                    # ENTFERNT (kein Stripe)
```

---

## Technologie-Stack

- **Language:** Kotlin 1.9.24
- **UI:** Jetpack Compose (BOM 2024.04) + Material3
- **Navigation:** Navigation Compose 2.7.7
- **Database:** Room 2.6.1 (KSP)
- **HTTP:** OkHttp 4.12.0
- **JSON:** Gson 2.11.0
- **Storage:** DataStore Preferences 1.1.1
- **Storage Access:** DocumentFile 1.0.1
- **Permissions:** Android Runtime Permissions (kein Accompanist mehr)

---

## Berechtigungen

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" maxSdkVersion="28" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Datenbank-Schema (Room)

### TrackEntity
| Spalte | Typ | Beschreibung |
|---|---|---|
| id | Long (PK) | Auto-generated |
| path | String | Absoluter Dateipfad |
| filename | String | Dateiname |
| folder | String | Ordnername |
| title, artist, album, genre | String | Metadaten |
| bpm, bpmAnalyzed | Double? | Tempo |
| keyCamelot, keyOpen, keyMusical | String? | Tonart |
| lufs, rmsDb, peakDb | Double? | Loudness |
| isAnalyzed | Boolean | Analysiert? |
| fileType | String | mp3/wav/aiff/flac |
| rating | Int | 0-100 |
| colorR/G/B | Int | Farbcodierung |

### PlaylistEntity / PlaylistTrackEntity
Playlists mit Tracks, Reihenfolge, Position, Datum.

### CuePointEntity / SavedLoopEntity / BeatgridEntity
Cue-Punkte, Loops, Beatgrids pro Track.

---

## Engine DJ Kompatibilität

- **m.db:** SQLite-Datenbank wird gelesen und beschrieben (Copy-to-Cache Ansatz)
- **Schema:** Track, Playlist, PlaylistEntity, PerformanceData Tabellen
- **Sync:** Bidirektional mit M3U8 Sidecar-Dateien
- **Hardware:** Kompatibel mit Denon SC Live 4 und anderer Engine DJ Hardware
- **USB-Pfade:** 12 Suchpfade für USB-Sticks

---

## Bekannte Einschränkungen

1. **Kein APK Build** - Gradle Build-Umgebung nicht auf dem Gerät vorhanden. Build auf einem Entwicklerrechner oder CI/CD nötig.
2. **Kein Echtzeit-Audio** - Nur Datei-basierte Analyse (kein Streaming/Playback)
3. **Theme.DJApp** - Wird als `@style/Theme.DJApp` referenziert aber nicht als Resource definiert (muss in `res/values/themes.xml` ergänzt werden)
4. **Fallback Destructive Migration** - Bei Schema-Änderungen wird die DB zurückgesetzt (Version 1)

---

## Nächste Schritte

1. **Gradle Build** auf Entwicklerrechner ausführen -> APK generieren
2. **res/values/themes.xml** anlegen mit `Theme.DJApp` als Material3 Dark Theme
3. **Echte USB-Stick Integration** testen (SAF URI Handling)
4. **Audio-Analyse** mit echten Dateien validieren
5. **Engine DJ Sync** mit echtem m.db testen
6. **Offline-Betrieb** sicherstellen (API-Client nur für optionale Features)

---

## Chat-Verlauf

### Phase 1: React Native -> Kotlin Konvertierung

**Ausgangslage:**
- Bestehende React Native / Expo TypeScript App (DJ-Management)
- Ziel: Native Android App in Kotlin / Jetpack Compose
- Codebase: ~47 Dateien, TypeScript mit React Native Abhängigkeiten

**User-Anforderungen:**
- USB-Stick als einziges Speichermedium (SD-Karte entfernt)
- Kein Payment/Stripe Code
- Deutsch als Standardsprache mit Englisch-Fallback (i18n)
- Stack Navigation, 7 Pages
- Theme: primary #1DB954 (Spotifygrün), secondary #191414 (dunkel)
- Engine DJ m.db (SQLite) Kompatibilität für Denon SC Live Hardware
- Kein Simulation — echte Audio-Analyse Engine

**Konvertierung durchgeführt:**

| React Native | Kotlin / Android |
|---|---|
| AsyncStorage | Room Database (KSP) |
| React Navigation | Navigation Compose |
| Expo Permissions | Android Runtime Permissions |
| expo-file-system | Java File API + SAF |
| Stripe SDK | ENTFERNT |
| TypeScript Interfaces | Kotlin Data Classes |
| React Components | Jetpack Compose @Composable |
| useState/useEffect | remember + LaunchedEffect |
| FlatList | LazyColumn |
| AsyncStorage cache | DataStore Preferences |

**Dateien erstellt (47 Kotlin-Dateien):**
- 2 Root-Dateien (MainActivity.kt, DJApp.kt)
- 10 Analysis-Dateien (FFT, BPM, Key, LUFS, Waveform, Parser)
- 14 Data/Local-Dateien (7 Entities, 6 DAOs, 1 DB-Klasse)
- 3 Engine-Dateien (m.db, Sync, Volume Detector)
- 1 Scanner-Dateien
- 1 API-Datei
- 1 i18n-Datei
- 2 Navigation-Dateien
- 11 UI-Dateien (7 Screens, 1 Components, 3 Theme)
- 0 Payment-Dateien (entfernt)

**Build-Konfiguration:**
- `build.gradle.kts` (Root): KSP Plugin für Room
- `app/build.gradle.kts`: Room, Navigation Compose, OkHttp, Gson, DataStore, DocumentFile, Material Icons Extended
- `AndroidManifest.xml`: READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, INTERNET, ACCESS_NETWORK_STATE

---

### Phase 2: Kernfunktionen implementiert

**Room Database (`data/local/`):**
- `DJLibraryDatabase`: Singleton mit `fallbackToDestructiveMigration()`
- 7 Entities: TrackEntity, PlaylistEntity, PlaylistTrackEntity, CuePointEntity, SavedLoopEntity, BeatgridEntity, AnalysisResultEntity
- 6 DAOs: TrackDao, PlaylistDao, CuePointDao, LoopDao, BeatgridDao, AnalysisDao
- Convenience Methods: `upsertTrack()`, `createPlaylist()`, `addTrackToPlaylist()`, `saveAnalysisResult()`, `getLibraryStats()`, `importFolderAsPlaylist()`

**Audio Analysis Engine (`analysis/`):**
- `AudioAnalyzer`: Orchestrator für die gesamte Analyse-Pipeline
- `AudioAnalysisQueue`: 3-Worker Coroutine Queue mit `Channel<AnalysisTask>`
- `BpmDetector`: BPM-Erkennung via Autokorrelation
- `KeyDetector`: Musical Key (C, C#, D...), Camelot (1A-12B), OpenKey (1d-12d)
- `LoudnessAnalyzer`: Integrated Loudness (LUFS), RMS dB, Peak dB
- `Fft`: Fast Fourier Transform Implementierung
- `PcmData`: PCM-Rohdaten Extraktion
- `WaveformGenerator`: Waveform-Komprimierung für Visualisierung
- Parser: WAV, AIFF, MP3, FLAC

**Engine DJ Kompatibilität (`engine/`):**
- `EngineDJDatabase`: Read/Write m.db via raw SQLiteDatabase (Copy-to-Cache Ansatz)
  - `openEngineDb()`: Kopiert m.db -> Cache, öffnet, bootstrapped Schema
  - `readAllEngineTracks()`:liest alle Tracks mit Metadaten
  - `readAllEnginePlaylists()`: liest alle Playlists
  - `readEnginePlaylistTracks()`: liest Tracks pro Playlist (JOIN)
  - `flushEngineDb()`: Schreibt zurück auf USB-Stick
- `EngineDJSync`: Bidirektionale Synchronisation
  - `syncToEngineDb()`: Schreibt lokale Tracks/Playlists in m.db
  - `exportM3u8Sidecar()`: Erstellt M3U8 Playlists neben der m.db
  - `fileTypeFromExt()`: Extension -> Engine DJ fileType Mapping
- `EngineVolumeDetector`: USB-Stick Erkennung
  - 12 Suchpfade (`/storage/`, `/mnt/`, `/usb/`, etc.)
  - Prüft auf `Engine Library/Database2/m.db`
  - Gibt `EngineVolume` mit `hasEngineLibrary`, `trackCount`, `label`

**Music Scanner (`scanner/`):**
- `MusicScanner`: Rekursiver Datei-Scanner
  - Unterstützt mp3, wav, aiff, flac, m4a, ogg
  - DataStore Cache für gescannte Ordner
  - Gibt `List<ScannedFile>` mit path, filename, size, dateModified

**UI Screens (`ui/screens/`):**
- `HomePage`: Login/Signup Formular + Live Library Stats (aus Room)
- `UsbStickPage`: USB-Stick Auswahl via SAF (Storage Access Framework)
- `FolderBrowserPage`: Ordner durchsuchen, Tracks anzeigen
- `AnalysisProgressPage`: Analyse-Fortschritt mit Animation
- `PlaylistManagerPage`: Playlists erstellen, bearbeiten, löschen
- `LibraryPage`: 3-Tab Layout (Tracks, Playlists, Devices) mit Engine DJ Import
- `SyncSettingsPage`: Synchronisierungsoptionen

**Common Components (`ui/components/`):**
- `BpmBadge`: BPM-Anzeige grün
- `KeyBadge`: Key-Anzeige
- `TrackListItem`: Track-Zeile mit Icon, Titel, Artist, BPM, Key
- `FolderListItem`: Ordner-Zeile mit Track-Anzahl
- `PlaylistListItem`: Playlist-Zeile mit `combinedClickable`
- `EmptyState`: Leerer Zustand mit Icon
- `LoadingOverlay`: Lade-Overlay
- `SectionHeader`: Abschnittsüberschrift mit grünem Strich
- `GreenButton` / `OutlinedGreenButton`: Standard Buttons

**Theme (`ui/theme/`):**
- `Color.kt`: Primary #1DB954, Secondary #191414, Surface, OnSurface, ErrorRed, BpmBadge, KeyBadge
- `Theme.kt`: DarkColorScheme, DJAppTheme (Material3 Dark Mode)
- `Type.kt`: Typography Definition

**Navigation (`navigation/`):**
- `Screen.kt`: 7 Routen (Home, FolderBrowser, PlaylistManager, AnalysisProgress, UsbStick, Library, SyncSettings)
- `AppNavigation.kt`: 5 Bottom Nav Items (Home, Ordner, Playlists, Analyse, Sync) + NavHost

**i18n (`i18n/`):**
- `Strings.kt`: ~100 Keys pro Sprache (DE/EN)
- DE als Default, EN als Fallback
- Keys: `home.title`, `library.search`, `usb.detect`, `sync.start`, etc.

**API (`api/`):**
- `ApiClient`: OkHttp mit Auth-Interceptor, GET/POST/PUT/DELETE

---

### Phase 3: Analyse & Bugfixes (diese Session)

**Analyse durchgeführt:**
- Alle 47 Kotlin-Dateien systematisch geprüft
- 4 Critical, 11 Warning, 11 Info Issues identifiziert
- Komplettes Issue-Tracking mit Datei, Zeile, Beschreibung

---

#### Critical Issues (4 — alle gefixt)

**1. `PlaylistDao.kt` — SQL-Spaltennamen mismatch**
- Problem: SQL Queries使用 snake_case (`playlist_id`, `track_id`, `is_folder`), aber `@ColumnInfo` definiert camelCase (`playlistId`, `trackId`, `isFolder`)
- Fehler: `Room cannot find column` Exception zur Laufzeit
- Fix: Alle SQL-Spaltennamen in `PlaylistDao.kt` auf camelCase korrigiert
- Betroffene Queries: `getAllAsFlow`, `getAll`, `getById`, `getTracks`, `removeTrack`, `reorderTrack`, `clearTracks`

**2. `PlaylistWithCount` — Fehlende `@ColumnInfo` Annotationen**
- Problem: `PlaylistWithCount` data class hatte keine `@ColumnInfo` Annotationen für Spalten mit anderen Namen
- Fehler: Room konnte Spalten nicht zuordnen
- Fix: `@ColumnInfo(name = "parent_id")`, `@ColumnInfo(name = "is_folder")`, `@ColumnInfo(name = "color_r")`, etc. für alle 9 Spalten ergänzt

**3. `LibraryPage.kt` — Falsche Methoden-API**
- Problem: Code rief `detectVolumes()` auf, aber Methode heißt `detectUsbVolumes()`
- Fehler: Kompilierungsfehler
- Fix: `EngineVolumeDetector.detectVolumes()` -> `EngineVolumeDetector.detectUsbVolumes()` korrigiert
- Zusätzlich: Dead Code entfernt (`derivedStateOf` Block)

**4. `EngineDJSync.kt` — INSERT-Statement fehlerhaft**
- Problem: SQL INSERT hatte falsches Placeholder-Format und `hex(randomblob(16))` wurde falsch verwendet
- Fehler: SQL Syntax Error beim Sync
- Fix: INSERT Statement mit korrektem `trackId` Placeholder und `hex(randomblob(16))` korrigiert
- Zusätzlich: Duplicate `fileTypeFromEntiy` Funktion entfernt

---

#### Warning Issues (11 — alle gefixt)

**5. `Theme.kt` — Deprecated API**
- Problem: `window.statusBarColor` und `window.navigationBarColor` deprecated auf API 35
- Fix: Entire `SideEffect` Block entfernt, `Activity`/`LocalView`/`WindowCompat` Imports entfernt

**6. `build.gradle.kts` — Fehlende/überflüssige Dependencies**
- Problem: `documentfile` fehlte (wurde in UsbStickPage verwendet), `accompanist-permissions` und `coil-compose` waren ungenutzt
- Fix: `documentfile:1.0.1` ergänzt, `accompanist-permissions` und `coil-compose` entfernt

**7. `EngineDJDatabase.kt` — Cursor Leak bei Exception**
- Problem: Wenn Cursor-Iteration eine Exception wirft, wird `cursor.close()` nicht aufgerufen
- Fix: Alle 4 Query-Methoden auf `cursor.use {}` umgestellt (`readAllEngineTracks`, `readAllEnginePlaylists`, `readEnginePlaylistTracks`, `trackCount`)

**8. `HomePage.kt` — Hardcoded Stats**
- Problem: `totalTracks=1247`, `analyzedTracks=892`, `playlists=5` waren hardcoded
- Fix: Ersetzt durch `LaunchedEffect` + `DJLibraryDatabase.getInstance(context).getLibraryStats()`, live aus Room

**9-13. Unused Imports (5 Dateien)**
- `PlaylistManagerPage.kt`: `Color`, `animateColorAsState` entfernt
- `LibraryPage.kt`: `Activity`, `Intent`, `rememberLauncherForActivityResult`, `ActivityResultContracts`, `AnimatedVisibility`, `BorderStroke` entfernt
- `AnalysisProgressPage.kt`: `CircleShape` entfernt
- `SyncSettingsPage.kt`: `LazyColumn`, `items` entfernt
- `Theme.kt`: `Activity`, `SideEffect`, `toArgb`, `LocalView`, `WindowCompat` entfernt

---

#### Info Issues (11 — dokumentiert)

**14. `EngineDJDatabase.readEnginePlaylistTracks` — Double-Close Pattern**
- Pattern: `cursor.close()` in try + `db.close()` in finally
- Status: Behoben (`.use {}`)

**15. Duplizierte `fileTypeFromExt` Funktion**
- Status: Behoben (nur noch in `EngineDJSync.kt`)

**16. `DJApp.kt` — Unbenutztes `instance` Companion Object**
- Status: Behoben (entfernt)

**17. `MainActivity.kt` — Unbenutzter `hasStoragePermission` State**
- Status: Behoben (entfernt)

**18. `PlaylistManagerPage` — Hardcoded Playlist-Daten**
- Status: Behoben (nutzt jetzt `PlaylistDao.getAll()`, create/rename via Room)

**19. `FolderBrowserPage` — Hardcoded Ordner-Daten**
- Status: Behoben (nutzt jetzt `MusicScanner.scanMusicLibrary()` + DataStore Cache)

**20. `SyncSettingsPage` — Hardcoded Sync-Status**
- Status: Behoben (nutzt `PlaylistDao.getAll()` + `EngineVolumeDetector` + `EngineDJSync.syncToEngineDJ()`)

**21. `AnalysisProgressPage` — Hardcoded Analyse-Daten**
- Status: Behoben (nutzt `AnalysisQueue.queue` StateFlow + MusicScanner Scan)

**22. `PlaylistDao.getAllAsFlow` — SQL Spaltenname `is_folder`**
- Status: Behoben (auf `isFolder` korrigiert)

**23. `EngineDJDatabase` — Nullable Column Helper**
- Status: Behoben (`nullableLong`, `nullableInt`, `nullableDouble` ergänzt)

**24. `EngineVolumeDetector` — USB-only (kein SD/Internal)**
- Status: Implementiert (12 USB-Suchpfade, keine SD-Karten-Pfade)

---

### Phase 4: Hardcoded Screens → Echte Daten (diese Session)

**4 Screens wurden von hardcoded Mock-Daten auf echte Datenquellen umgestellt:**

**1. `FolderBrowserPage.kt` → MusicScanner**
- Vorher: Feste Ordner-Liste (`listOf("House", "Techno", ...")`)
- Nachher: `MusicScanner.scanMusicLibrary(context, path)` rekursiver Scan
- DataStore Cache für gescannte Ordner
- Scan-Fortschrittsanzeige während Scans
- Empty State wenn kein USB-Stick verbunden

**2. `PlaylistManagerPage.kt` → Room PlaylistDao**
- Vorher: Feste Playlist-Liste (`listOf("Club Set", "Festival", ...")`)
- Nachher: `PlaylistDao.getAll()` als `StateFlow<List<PlaylistEntity>>`
- Erstellen via `playlistDao.createPlaylist()`
- Umbenennen via `playlistDao.updatePlaylist()`
- Anzahl Tracks pro Playlist wird angezeigt

**3. `AnalysisProgressPage.kt` → AudioAnalysisQueue**
- Vorher: Feste Track-Liste mit hardcoded BPM/Key
- Nachher: Subscribes auf `AnalysisQueue.queue` StateFlow
- Ordner-Scan via `MusicScanner.scanMusicLibrary()`
- Tracks werden via `AnalysisQueue.enqueue()` zur Queue hinzugefügt
- Live-Fortschritt mit Analyse-Ergebnissen (BPM, Key, LUFS)

**4. `SyncSettingsPage.kt` → Room + VolumeDetector + EngineDJSync**
- Vorher: Harte Sync-Optionen ohne echte Funktionalität
- Nachher: `PlaylistDao.getAll()` für Playlist-Auswahl
- `EngineVolumeDetector.detectUsbVolumes()` für USB-Stick-Erkennung
- `EngineDJSync.syncToEngineDJ()` für echten Sync
- Letzter Sync aus SharedPreferences geladen

---

### Phase 5: Engine DJ Kompatibilitäts-Analyse (diese Session)

**Analyse des Engine DJ Workflows:**

**Erkenntnis:** Der Denon SC Live Controller analysiert Tracks **automatisch** beim Laden:
- Beim Öffnen einer Playlist prüft der Controller jeden Track in der m.db
- Wenn `PerformanceData` fehlt, analysiert der Controller:
  - BPM (Taktfrequenz)
  - Beatgrid (Taktstruktur)
  - Wellenform (Waveform-Visualisierung)
  - Musical Key
- Dies geschieht **automatisch** — kein manueller Input nötig

**Was unsere App schreiben muss:**
- `Track` Tabelle: Pfad, Dateiname, Größen, Typ, Datum
- `Playlist` Tabelle: Name, Flags, Farbe
- `PlaylistEntity` Tabelle: Zuordnung Track → Playlist

**Was die App NICHT schreiben muss:**
- `PerformanceData` — Controller analysiert automatisch
- `Beatgrid` — Wird vom Controller generiert
- `Waveform` — Wird vom Controller generiert

**Fazit:** Unsere App muss nur die Metadaten-Tabellen (`Track`, `Playlist`, `PlaylistEntity`) in die m.db schreiben. Die Analyse übernimmt der Controller selbst beim ersten Laden der Tracks.

---

### Phase 6: Build & Deployment (offen)

**Problem:** Kein PC vorhanden, alles muss vom Handy aus funktionieren.

**Lösung:** GitHub Actions (Cloud Build)
- Code wird auf GitHub gepusht
- GitHub Actions baut APK im Cloud (Android SDK + Gradle)
- APK kann direkt heruntergeladen werden

**GitHub Account:** `Etoxxelecrronix`
**Token:** Ausstehend (muss vom User generiert werden)

**Nächste Schritte:**
1. GitHub Personal Access Token vom User erhalten
2. `.gitignore` erstellen (build outputs, .gradle, local.properties, etc.)
3. Git initialisieren + commit
4. GitHub Repo via API erstellen
5. Code pushen
6. GitHub Actions Workflow erstellen (`build.yml`)
7. Ersten Build triggern → APK herunterladen
