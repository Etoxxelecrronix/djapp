package com.djapp.navigation

import com.djapp.i18n.Strings

sealed class Screen(val route: String, val titleKey: String) {
    val title: String get() = Strings.t(titleKey)

    object Home : Screen("home", "nav.home")
    object FolderBrowser : Screen("folder_browser", "nav.folders")
    object PlaylistManager : Screen("playlist_manager", "nav.playlists")
    object AnalysisProgress : Screen("analysis_progress/{folderPath}", "analysis.title") {
        fun createRoute(folderPath: String) =
            "analysis_progress/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    object TrackDetail : Screen("track_detail/{trackId}", "track.title") {
        fun createRoute(trackId: Long) = "track_detail/$trackId"
    }
    object UsbStick : Screen("usb_stick", "nav.usb")
    object Library : Screen("library", "nav.library")
    object SyncSettings : Screen("sync_settings", "nav.sync")
}
