package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.RouteEntry
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** The focus ID a screen takes when it appears and nothing was remembered (per the prototype's focus table). */
fun initialFocusId(route: Route): String = when (route) {
    Route.HOME -> continueFocusId(0)
    Route.SEARCH -> SEARCH_INPUT_ID
    Route.SAVED -> SAVED_NEW_ID
    Route.TV_GUIDE -> guideRowFocusId(0)
    Route.SPORTS -> sportsRailFocusId(0)
    Route.SETTINGS -> settingFocusId(0)
    Route.LIVE_TV -> genreFocusId(0)
    Route.LIVE_PLAYER, Route.VOD_PLAYER -> PLAYER_SURFACE_ID
    Route.MOVIES, Route.SHOWS -> categoryFocusId(0)
    Route.MOVIE_DETAIL -> DETAIL_PLAY_ID
    Route.SHOW_DETAIL -> SHOW_PLAY_ID
    else -> "card-0"
}

/**
 * Shown when a route has nothing to show: a show page whose title is no longer loaded (for example after the portal was
 * changed in Settings), or a route with no screen. One plain line; Back (handled by the shell) returns to the last screen.
 */
@Composable
fun UnavailableScreen(modifier: Modifier = Modifier, message: String = "This title is not available.") {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3),
        verticalArrangement = Arrangement.spacedBy(Spacing.S2),
    ) {
        Text(message, style = MaterialTheme.typography.headlineMedium, color = Tokens.TextPrimary)
        Text("Press Back to return.", style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary)
    }
}
