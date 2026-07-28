package com.djapp.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Start")
    object FolderBrowser : Screen("folder_browser", "Ordner")
    object PlaylistManager : Screen("playlist_manager", "Playlists")
    object AnalysisProgress : Screen("analysis_progress/{folderPath}", "Analyse") {
        fun createRoute(folderPath: String) =
            "analysis_progress/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    object UsbStick : Screen("usb_stick", "USB-Stick")
    object Library : Screen("library", "Bibliothek")
    object SyncSettings : Screen("sync_settings", "Synchronisierung")
}
