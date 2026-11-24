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
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.HEART
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

const val DETAIL_PLAY_ID = "detail-play"
const val DETAIL_FAVORITE_ID = "detail-fav"
const val DETAIL_VERSIONS_ID = "detail-versions"

/**
 * A movie's page: poster, title line (year, age rating, the portal's IMDb rating), description, cast and a Play
 * button that holds focus. Everything shown is the portal's own metadata; a missing field is simply left out.
 */
@Composable
fun MovieDetailScreen(
    item: VodItem?,
    portalUrl: String,
    memory: FocusMemory,
    onPlay: () -> Unit,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    /** Set when the title has other versions: shows a Versions button that calls this. */
    onShowVersions: (() -> Unit)? = null,
    versionCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        Text("This title is not available.", style = Tokens.BodyLarge, color = Tokens.StatusError,
            modifier = modifier.padding(Spacing.SafeHorizontal, Spacing.S3))
        return
    }
    Row(
        modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        Poster(PosterUrl.resolve(item.posterUri, portalUrl), Modifier.width(400.dp).height(600.dp), targetWidthPx = 600)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            Text(PortalText.decodeEntities(item.name), style = Tokens.TitleLarge, color = Tokens.TextPrimary, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                item.year?.let { Text(it, style = Tokens.BodyLarge, color = Tokens.TextSecondary) }
                item.age?.let { Text(PortalText.decodeEntities(it), style = Tokens.BodyLarge, color = Tokens.TextSecondary) }
                item.shownRating?.let { Text("${it.source} %.1f".format(it.value), style = Tokens.BodyLarge, color = Tokens.StatusWarning) }
                item.genres?.let { Text(PortalText.decodeEntities(it), style = Tokens.BodyLarge, color = Tokens.TextSecondary, maxLines = 1) }
            }
            item.description?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 6) }
            item.director?.let { Text("Director: ${PortalText.decodeEntities(it)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2) }
            item.actors?.let { Text("Cast: ${PortalText.decodeEntities(it)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                OsFocusable(id = DETAIL_PLAY_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onPlay) { _ ->
                    Text("Play", style = Tokens.TitleMedium, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S2))
                }
                if (onShowVersions != null) OsFocusable(id = DETAIL_VERSIONS_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onShowVersions) { _ ->
                    Text("Versions ($versionCount)", style = Tokens.TitleMedium, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S2))
                }
                OsFocusable(id = DETAIL_FAVORITE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onToggleFavorite) { _ ->
                    Text(if (favorite) "$HEART  In favorites" else "Add to favorites", style = Tokens.TitleMedium, color = if (favorite) Tokens.StatusError else Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S2))
                }
            }
        }
    }
}
