package org.jellycopter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavScreen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val isTopLevel: Boolean = true
) {
    HOME("home", "Home", Icons.Rounded.Home, true),
    CHANNELS("channels", "Shuffle Channels", Icons.Rounded.Tv, true),
    DISCOVER("discover", "Discover", Icons.Rounded.AutoAwesome, true),
    LIBRARY("library", "Library", Icons.Rounded.LocalMovies, true),
    TROPHY_ROOM("trophy_room", "Trophy Room", Icons.Rounded.EmojiEvents, true),
    POST_PLAYBACK("post_playback", "Session End", Icons.Rounded.Movie, false),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, false);

    companion object {
        fun topLevelScreens(isDiscoverEnabled: Boolean = true): List<NavScreen> {
            return entries.filter { it.isTopLevel && (it != DISCOVER || isDiscoverEnabled) }
        }

        val topLevelScreens: List<NavScreen>
            get() = topLevelScreens(isDiscoverEnabled = true)
    }
}
