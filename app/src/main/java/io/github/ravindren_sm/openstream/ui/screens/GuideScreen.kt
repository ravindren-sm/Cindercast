package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.GuideLogic
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun guideRowFocusId(i: Int) = "guide-row-$i"
const val GUIDE_GROUP_ID = "guide-group"
const val FAVORITES_GROUP = "fav"

/** Which channels the guide lists: the favorites, or one portal group. Kept by the shell so the choice survives leaving the screen. */
class GuideState {
    var groupId by mutableStateOf<String?>(null)
    var groupTitle by mutableStateOf("")
    var channels: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set

    fun select(id: String, title: String, newChannels: PagedChannels) { groupId = id; groupTitle = title; channels = newChannels }
}

private fun timeText(context: android.content.Context, sec: Long) = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(sec * 1000))

/**
 * TV Guide: what is on now and next for each channel of a group (or the favorites). The portal gives a guide one channel
 * at a time, so a row asks for its channel's programmes when it first appears on screen and keeps them for ten minutes.
 * Select plays the channel; the list is what zapping then moves through.
 */
@Composable
fun GuideScreen(
    state: GuideState,
    favoriteCount: Int,
    genres: List<LiveGenre>,
    loader: GuideLoader,
    connected: Boolean,
    memory: FocusMemory,
    onChooseGroup: (id: String, title: String) -> Unit,
    onOpenChannel: (channels: List<LiveChannel>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }

    val paged = state.channels
    val listState = rememberLazyListState()
    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }.collect { (last, loaded) ->
            if (loaded > 0 && last >= loaded - 4 && !paged.exhausted && paged.error == null) withContext(Dispatchers.IO) { paged.loadNext() }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                Text("TV Guide", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
                OsFocusable(id = GUIDE_GROUP_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { picking = true }) { _ ->
                    Text("Group: ${state.groupTitle.ifEmpty { "…" }}", style = Tokens.BodyLarge, color = Tokens.AccentPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
                Text("Select a row to watch", style = Tokens.Body, color = Tokens.TextSecondary)
            }
            when {
                paged.items.isEmpty() && paged.error == null && !paged.exhausted ->
                    Text(if (connected) "Loading channels…" else "Waiting for the portal connection…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                paged.items.isEmpty() && paged.error != null -> Text(paged.error!!, style = Tokens.BodyLarge, color = Tokens.StatusError)
                paged.items.isEmpty() -> Text("No channels in this group.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(paged.items, key = { _, c -> c.id }) { i, channel ->
                        GuideRow(i, channel, loader, nowSec, context, memory) { onOpenChannel(paged.items, i) }
                    }
                }
            }
        }
        if (picking) {
            val options = buildList {
                if (favoriteCount > 0) add(Choice(FAVORITES_GROUP, "Favorites"))
                genres.forEach { add(Choice(it.id, PortalText.decodeEntities(it.title))) }
            }
            ChoicePicker(
                "Group", options, setOf(state.groupId.orEmpty()), multi = false,
                note = "The guide is asked for one channel at a time as rows appear.", memory = memory, returnFocusId = GUIDE_GROUP_ID,
                onToggle = { id -> onChooseGroup(id, options.firstOrNull { it.id == id }?.label ?: id) }, onClose = { picking = false },
            )
        }
    }
}

@Composable
private fun GuideRow(index: Int, channel: LiveChannel, loader: GuideLoader, nowSec: Long, context: android.content.Context, memory: FocusMemory, onSelect: () -> Unit) {
    // null = still asking; an empty list = the channel has no guide; failed = the portal could not be asked.
    var programmes: List<EpgProgramme>? by remember(channel.id) { mutableStateOf(null) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        val r = withContext(Dispatchers.IO) { loader.load(channel) }
        if (r == null) failed = true else programmes = r
    }
    // Up from the first row goes to the Group button; geometry alone would skip it (it is narrow and off to the side of
    // the full-width row) and land on a tab.
    val upToGroup = if (index == 0) Modifier.focusProperties { up = memory.requesterFor(GUIDE_GROUP_ID) } else Modifier
    OsFocusable(id = guideRowFocusId(index), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth().then(upToGroup), onSelect = onSelect) { _ ->
        Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Spacing.S2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(340.dp)) {
                Text(channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
                Text(PortalText.decodeEntities(channel.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
            }
            val list = programmes
            when {
                list == null -> Text(if (failed) "Guide unavailable" else "…", style = Tokens.Body, color = Tokens.TextSecondary)
                else -> {
                    val nn = GuideLogic.nowAndNext(list, nowSec)
                    if (nn.now == null && nn.next == null) Text("No guide data", style = Tokens.Body, color = Tokens.TextSecondary)
                    else {
                        Column(Modifier.weight(1f).padding(end = Spacing.S3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            nn.now?.let { p ->
                                Text("${PortalText.decodeEntities(p.name)}  ${timeText(context, p.startSec)}–${timeText(context, p.stopSec)}", style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
                                Box(Modifier.fillMaxWidth().height(5.dp).background(Tokens.Surface3)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(GuideLogic.progress(p, nowSec)).background(Tokens.AccentPrimary))
                                }
                            } ?: Text("Nothing listed right now", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
                            nn.next?.let { p -> Text("Next: ${PortalText.decodeEntities(p.name)}  ${timeText(context, p.startSec)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
