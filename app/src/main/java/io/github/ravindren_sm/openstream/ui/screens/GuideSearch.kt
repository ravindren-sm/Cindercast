package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.ProgrammeSearch
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.OsTextField
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.focus.trapFocus
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.delay

const val GUIDE_SEARCH_INPUT_ID = "guide-search-input"
fun guideSearchResultFocusId(i: Int) = "guide-sr-$i"

/**
 * Programme search: a text box and the programmes whose title or description contain the typed words, from every guide the
 * loader already holds plus the favorites' channels (loaded one at a time while this is open; a status line counts them).
 * Results show the title, the channel and when it is on ("Live now", "Today 8:00 PM"); Select opens the channel. Back closes
 * (the Back that only closes the keyboard is ignored). It replaces the grid, so nothing beneath can take focus.
 */
@Composable
internal fun ProgrammeSearchLayer(
    state: GuideState,
    loader: GuideLoader,
    favoriteChannels: () -> List<LiveChannel>,
    nowSec: Long,
    memory: FocusMemory,
    onOpenChannel: (channels: List<LiveChannel>, index: Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler { if (!memory.justFinishedEditing()) onClose() }

    // The guides of the favorites' channels that are not cached yet, asked one at a time; each answer refreshes the results.
    var tick by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val todo = favoriteChannels().distinctBy { it.id }.filter { loader.peek(it.id) == null }
        total = todo.size
        todo.forEachIndexed { i, ch ->
            loader.loadQueued(ch)
            done = i + 1; tick++
        }
    }
    // The typed words take effect a moment after the last key, so each letter does not rescan the cache.
    var effective by remember { mutableStateOf(state.searchQuery) }
    LaunchedEffect(state.searchQuery) { delay(300); effective = state.searchQuery }
    val minute = nowSec / 60
    val hits = remember(effective, tick, minute) {
        ProgrammeSearch.search(loader.cachedGuides().map { it.channel to it.programmes }, effective, nowSec)
    }
    val channels = remember(hits) { hits.map { it.channel } }

    // Back from the player lands on the result opened last; a fresh open lands in the text box.
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        withFrameNanos { }; delay(120)
        if (memory.focusedId?.startsWith("guide-s") != true) {
            if (state.searchQuery.isNotBlank() && hits.isNotEmpty()) memory.tryFocusFirst(guideSearchResultFocusId(state.searchResultIndex), guideSearchResultFocusId(0), GUIDE_SEARCH_INPUT_ID)
            else memory.tryFocus(GUIDE_SEARCH_INPUT_ID)
        }
    }

    Column(Modifier.fillMaxSize().trapFocus().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Text("Search programmes", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
        OsTextField(
            id = GUIDE_SEARCH_INPUT_ID, memory = memory, value = state.searchQuery, onValueChange = { state.searchQuery = it },
            label = "Title or description", hint = "Back closes the keyboard; then Down reaches the results. Back again leaves the search.",
            imeAction = ImeAction.Done, downId = if (hits.isNotEmpty()) guideSearchResultFocusId(0) else null,
        )
        val cached = loader.cachedGuides().size
        val status = when {
            total > 0 && done < total -> "Searching ${done + 1} of $total channels"
            total > 0 -> "Searched $cached channel guides, including $total favorites just loaded"
            else -> "Searched $cached channel guides already loaded"
        }
        Text(status, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
        when {
            effective.isBlank() -> Text("Type a title or a word from the description. Only programmes still to come, or on now, are listed.", style = Tokens.Body, color = Tokens.TextSecondary)
            hits.isEmpty() -> Text(if (total > 0 && done < total) "Nothing yet…" else "No programme matches.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
            else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(hits, key = { _, h -> "${h.channel.id}:${h.programme.startSec}" }) { i, h ->
                    val upToInput = if (i == 0) Modifier.focusProperties { up = memory.requesterFor(GUIDE_SEARCH_INPUT_ID) } else Modifier
                    OsFocusable(
                        id = guideSearchResultFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth().then(upToInput),
                        onSelect = { state.searchResultIndex = i; onOpenChannel(channels, i) },
                    ) { _ ->
                        Column(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Spacing.S2, vertical = 6.dp)) {
                            Text(PortalText.decodeEntities(h.programme.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                            val whenText = ProgrammeSearch.whenLabel(h.programme.startSec, nowSec, java.util.TimeZone.getDefault(), java.util.Locale.getDefault()) { timeText(context, it) }
                            Text(
                                "${PortalText.decodeEntities(h.channel.name)}  ·  $whenText", style = Tokens.Body,
                                color = if (h.live) Tokens.AccentPrimary else Tokens.TextSecondary, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
