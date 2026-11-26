package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.ravindren_sm.openstream.ui.focus.keepHorizontalFocus
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.IndexStatus
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

fun settingFocusId(i: Int) = "setting-$i"

/** What Settings shows and can do. The shell fills it in so this screen holds no storage code. */
class SettingsModel(
    val portalStatus: String,
    val index: IndexStatus,
    val defaultFilters: String?,
    val autoPlayNext: Boolean,
    val posterCacheMb: Int,
    val version: String,
    val onChangePortal: () -> Unit,
    val onBuildIndex: () -> Unit,
    val onPauseIndex: () -> Unit,
    val onClearIndex: () -> Unit,
    val onClearDefaultFilters: () -> Unit,
    val onClearRecentSearches: () -> Unit,
    val onClearHistory: () -> Unit,
    val onClearFavorites: () -> Unit,
    val onToggleAutoPlay: () -> Unit,
    val onClearPosterCache: () -> Unit,
    val contentPolicy: io.github.ravindren_sm.openstream.data.ContentPolicy,
    val onSetMinRating: (Double?) -> Unit,
    val onToggleHideUnrated: () -> Unit,
    val orderByStats: Boolean,
    val statsSummary: String,
    val onToggleOrderByStats: () -> Unit,
    val onResetStats: () -> Unit,
    val applyDefaultsEverywhere: Boolean,
    val defaultsSummary: String,
    val onToggleApplyDefaults: () -> Unit,
    val profileSummary: String,
    val onSwitchProfile: () -> Unit,
    val onManageProfiles: () -> Unit,
    val tagStatus: io.github.ravindren_sm.openstream.data.tmdb.TaggerStatus,
    val onSetTagKey: () -> Unit,
    val onToggleTagging: () -> Unit,
    val onClearTags: () -> Unit,
    val imdbWithId: Int,
    val imdbScored: Int,
    val imdbBusy: Boolean,
    val imdbMessage: String?,
    val onDownloadImdb: () -> Unit,
    val onClearImdb: () -> Unit,
)

private class Row_(val title: String, val value: String, val action: String, val confirm: String? = null, val run: () -> Unit)

/**
 * Settings: the portal, search, history, playback, cache and about. Each row shows its value; Select does its action, and
 * anything that deletes something asks first. Nothing here changes a device setting.
 */
@Composable
fun SettingsScreen(model: SettingsModel, memory: FocusMemory, modifier: Modifier = Modifier) {
    var confirming: Row_? by remember { mutableStateOf(null) }
    var ratingPicker by remember { mutableStateOf(false) }
    var confirmingId: String? by remember { mutableStateOf(null) }
    val indexValue = when {
        model.index.complete && model.index.enabled -> "Ready · %,d titles".format(model.index.titles)
        model.index.enabled -> "Building ${model.index.percent}%% · %,d titles".format(model.index.titles)
        model.index.titles > 0 -> "Paused at ${model.index.percent}%% · %,d titles".format(model.index.titles)
        else -> "Not built"
    }
    val sections: List<Pair<String, List<Row_>>> = listOf(
        "Profile" to listOf(
            Row_("Watching as", model.profileSummary, "Switch", run = model.onSwitchProfile),
            Row_("Profiles and household PIN", "Add, edit or delete profiles; set the PIN", "Manage", run = model.onManageProfiles),
        ),
        "Portal" to listOf(
            Row_("Portal", model.portalStatus, "Change portal…", run = model.onChangePortal),
        ),
        "Content filter · this profile" to listOf(
            Row_("Hide movies and shows rated below", model.contentPolicy.summary(), "Change") { ratingPicker = true },
            Row_("Titles with no rating", if (model.contentPolicy.hideUnrated) "Hidden" else "Shown", if (model.contentPolicy.hideUnrated) "Show" else "Hide", run = model.onToggleHideUnrated),
        ),
        "Your viewing · this profile" to listOf(
            Row_("Order search by what you watch", if (model.orderByStats) "On" else "Off", if (model.orderByStats) "Turn off" else "Turn on", run = model.onToggleOrderByStats),
            Row_("Most watched", model.statsSummary, "Reset", "Reset what this profile has watched? Search goes back to the portal's order.", model.onResetStats),
        ),
        "Search" to listOf(
            Row_("Use my search defaults everywhere", if (model.applyDefaultsEverywhere) "On · ${model.defaultsSummary}" else "Off", if (model.applyDefaultsEverywhere) "Turn off" else "Turn on", run = model.onToggleApplyDefaults),
            Row_("Title index (fast search and sorting)", indexValue, if (model.index.enabled) "Pause" else if (model.index.titles > 0) "Resume" else "Build",
                run = { if (model.index.enabled) model.onPauseIndex() else model.onBuildIndex() }),
            Row_("Title index data", if (model.index.titles > 0) "%,d titles on this device".format(model.index.titles) else "None", "Delete", "Delete the title index? It can be built again.", model.onClearIndex),
            Row_("Default search filters", model.defaultFilters ?: "None", "Clear", "Clear the default search filters?", model.onClearDefaultFilters),
            Row_("Recent searches", "Shown as suggestions", "Clear", "Clear recent searches?", model.onClearRecentSearches),
        ),
        "Watching" to listOf(
            Row_("Play the next episode automatically", if (model.autoPlayNext) "On" else "Off", if (model.autoPlayNext) "Turn off" else "Turn on", run = model.onToggleAutoPlay),
            Row_("Continue watching and watched", "Kept on this device", "Clear", "Clear Continue watching and the watched list?", model.onClearHistory),
            Row_("Favorites", "Channels, movies and shows", "Clear", "Remove all favorites?", model.onClearFavorites),
        ),
        "Online tags (optional)" to listOf(
            Row_("Tag service key (themoviedb.org)", if (model.tagStatus.hasKey) "Saved on this device" else "Not set", if (model.tagStatus.hasKey) "Replace…" else "Set key…", run = model.onSetTagKey),
            Row_("What is sent", "Title names and years, with your key. Nothing else.", "", run = {}),
            Row_("Checking titles", tagSummary(model.tagStatus), if (!model.tagStatus.hasKey) "" else if (model.tagStatus.running) "Pause" else "Start", run = if (model.tagStatus.hasKey) model.onToggleTagging else ({})),
            Row_("Tags on this device", "%,d checked · %,d matched · %,d with an age certification".format(model.tagStatus.counts.checked, model.tagStatus.counts.matched, model.tagStatus.counts.graded), "Remove key and tags", "Remove the key and every tag? Titles keep the portal's own labels.", model.onClearTags),
        ),
        "IMDb ratings (personal use, optional)" to listOf(
            Row_("IMDb ratings file", when {
                model.imdbBusy -> "Downloading…"
                model.imdbMessage != null -> model.imdbMessage
                model.imdbWithId == 0 -> "Needs the online tags first (no IMDb ids yet)"
                model.imdbScored > 0 -> "%,d titles rated".format(model.imdbScored)
                else -> "Not downloaded"
            }, if (model.imdbBusy || model.imdbWithId == 0) "" else if (model.imdbScored > 0) "Update" else "Download", run = if (model.imdbBusy || model.imdbWithId == 0) ({}) else model.onDownloadImdb),
            Row_("What is downloaded", "IMDb's free ratings file (about 8 MB) comes to this device. Nothing about you or your titles is sent.", "", run = {}),
            Row_("Credit", io.github.ravindren_sm.openstream.data.imdb.ImdbRatings.CREDIT, "", run = {}),
            Row_("Ratings on this device", "%,d of %,d titles with an IMDb id".format(model.imdbScored, model.imdbWithId), "Remove", "Remove the IMDb ratings from this device?", model.onClearImdb),
        ),
        "Memory" to listOf(
            Row_("Poster cache", "${model.posterCacheMb} MB in use", "Clear", run = model.onClearPosterCache),
        ),
    )
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().keepHorizontalFocus().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),   // Left/Right on a row stay put (they used to jump to the tab bar)
            verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        ) {
            Text("Settings", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
            var n = 0
            sections.forEach { (heading, rows) ->
                Text(heading, style = Tokens.TitleMedium, color = Tokens.AccentPrimary, modifier = Modifier.padding(top = Spacing.S2))
                rows.forEach { row ->
                    val id = settingFocusId(n++)
                    if (row.action.isEmpty() && row.confirm == null) {
                        // Information only (nothing to select): plain text, so the D-pad does not stop on it.
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.width(620.dp))
                            Text(row.value, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2, modifier = Modifier.weight(1f))
                        }
                        return@forEach
                    }
                    OsFocusable(
                        id = id, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(),
                        onSelect = { confirmingId = id; if (row.confirm != null) confirming = row else row.run() },
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.width(620.dp))
                            Text(row.value, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(row.action, style = Tokens.Body, color = Tokens.AccentPrimary, maxLines = 1)
                        }
                    }
                }
            }
            Text("About", style = Tokens.TitleMedium, color = Tokens.AccentPrimary, modifier = Modifier.padding(top = Spacing.S2))
            Text("Cindercast ${model.version} · MIT licence", style = Tokens.Body, color = Tokens.TextPrimary)
            Text("Your portal details, favorites and history stay on this device. Nothing is sent anywhere except to your portal and, only if you add a key above, title names and years to themoviedb.org.", style = Tokens.Body, color = Tokens.TextSecondary)
            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", style = Tokens.Body, color = Tokens.TextSecondary)
        }
        if (ratingPicker) {
            ChoicePicker(
                "Hide titles rated below", listOf(Choice("any", "Show everything")) + io.github.ravindren_sm.openstream.data.ContentPolicy.STEPS.map { Choice("$it", "${"%.0f".format(it)}") },
                setOf(model.contentPolicy.minRating?.toString() ?: "any"), multi = false,
                note = "Uses the best rating this device has: IMDb (the portal's, or the IMDb file below), else Kinopoisk, else the online tags' score. Applies to lists, search and Home rows for this profile.",
                memory = memory, returnFocusId = confirmingId, onToggle = { id -> model.onSetMinRating(id.toDoubleOrNull()) }, onClose = { ratingPicker = false },
            )
        }
        confirming?.let { row ->
            ChoicePicker(
                row.confirm ?: "", listOf(Choice("yes", "Yes, ${row.action.lowercase()}"), Choice("no", "Cancel")), emptySet(), multi = false, note = null,
                memory = memory, returnFocusId = confirmingId,
                onToggle = { id -> if (id == "yes") row.run() }, onClose = { confirming = null },
            )
        }
    }
}

private fun tagSummary(s: io.github.ravindren_sm.openstream.data.tmdb.TaggerStatus): String = when {
    !s.hasKey -> "Needs a key first"
    s.message != null -> s.message
    s.running -> "Running while the app is open and idle (%,d checked)".format(s.counts.checked)
    else -> "Not running"
}


