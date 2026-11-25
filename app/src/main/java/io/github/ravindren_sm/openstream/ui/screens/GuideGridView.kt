package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.GuideGrid
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.GuideLogic
import io.github.ravindren_sm.openstream.data.ProgrammeSearch
import io.github.ravindren_sm.openstream.data.SportsLogic
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.delay

/** Focus id of one programme cell: row [row], the programme starting at [startSec] (null: the row's placeholder cell). */
fun guideCellFocusId(row: Int, startSec: Long?) = "guide-c-$row-${startSec ?: "p"}"

/** Width of the fixed channel column. */
private val LABEL_W = 300.dp
private val ROW_H = 88.dp
private val CELL_H = 80.dp
private val CELL_GAP = 4.dp
private val DETAILS_H = 188.dp

/**
 * What the grid screens know about the rows on screen, for moving focus between them: which rows are composed and the
 * programmes each shows, which cells exist right now, and the time column the viewer is in ([anchorSec], kept while moving
 * up and down so a column of rows is followed, reset when a horizontal move lands on a new programme). Touched only on the
 * main thread. It holds the same list objects as [GuideLoader]'s cache and only for rows that are on screen.
 */
internal class GridRegistry {
    /** Row index -> programmes (null while loading or failed). A key is present only while that row is composed. */
    val rows = HashMap<Int, List<EpgProgramme>?>()
    val cellIds = HashSet<String>()
    val labelIds = HashSet<String>()
    var anchorSec: Long = 0
    var windowStartSec: Long = 0

    /** The id of the cell of row [row] to land on for time [timeSec], or null if that row (or the cell) is not composed. */
    fun cellFor(row: Int, timeSec: Long): String? {
        if (!rows.containsKey(row)) return null
        val list = rows[row].orEmpty()
        val inWindow = list.filter { it.stopSec > windowStartSec && it.startSec < windowStartSec + GuideGrid.WINDOW_SEC }
        val id = GuideGrid.programmeNear(inWindow, timeSec)?.let { guideCellFocusId(row, it.startSec) } ?: guideCellFocusId(row, null)
        return id.takeIf { it in cellIds }
    }
}

/** What the details strip shows: the focused programme (null on a placeholder cell or a channel name) of a channel. */
internal class GuideDetail(val channel: LiveChannel, val programme: EpgProgramme?)

/**
 * The time grid: a header of half-hour marks, one row per channel (fixed channel column, then programme cells whose widths
 * follow their durations), a thin "now" marker and a strip with the focused programme's details. Rows load their guides one
 * request at a time as they appear. Only the visible rows and the cells inside the time window are composed.
 */
@Composable
internal fun GuideTimeGrid(
    state: GuideState,
    channels: List<LiveChannel>,
    listState: LazyListState,
    loader: GuideLoader,
    nowSec: Long,
    memory: FocusMemory,
    onOpenChannel: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { GridRegistry() }
    val baseSec = GuideGrid.floorToSlot(nowSec)
    // The window never starts before the current half hour; an offset left over from a long-open screen is clamped.
    val windowStart = baseSec + state.slotOffset.coerceAtLeast(0) * GuideGrid.SLOT_SEC
    SideEffect { registry.windowStartSec = windowStart }
    var detail by remember { mutableStateOf<GuideDetail?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    // After the window scrolls, the target cell is composed a frame or two later: ask for its focus until it takes it.
    LaunchedEffect(pendingFocus) {
        val id = pendingFocus ?: return@LaunchedEffect
        repeat(8) { withFrameNanos { }; if (memory.tryFocus(id)) { pendingFocus = null; return@LaunchedEffect } }
        pendingFocus = null
    }

    fun scrollWindowTo(newStart: Long) { state.slotOffset = ((newStart - baseSec) / GuideGrid.SLOT_SEC).toInt().coerceAtLeast(0) }

    /** Moves one programme right or left in [row] from [p], scrolling the window if the target is off screen. */
    fun step(row: Int, p: EpgProgramme, direction: Int): Boolean {
        val list = registry.rows[row].orEmpty().filter { it.stopSec > baseSec }
        val target = (if (direction > 0) GuideGrid.after(list, p) else GuideGrid.before(list, p))
        if (target == null) {
            // Left at the first programme goes to the channel name; Right at the last stays put.
            if (direction < 0) memory.tryFocus(guideRowFocusId(row))
            return true
        }
        val newStart = GuideGrid.windowStartFor(target, direction, windowStart, GuideGrid.WINDOW_SEC, baseSec)
        val id = guideCellFocusId(row, target.startSec)
        if (newStart == windowStart && id in registry.cellIds) memory.tryFocus(id)
        else { scrollWindowTo(newStart); pendingFocus = id }
        return true
    }

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gridW = maxWidth - LABEL_W
            val unitsPerSec = gridW.value / GuideGrid.WINDOW_SEC
            Column(Modifier.fillMaxSize()) {
                // Header: half-hour marks across the time area.
                Row(Modifier.fillMaxWidth().height(40.dp)) {
                    Box(Modifier.width(LABEL_W))
                    Box(Modifier.width(gridW).fillMaxHeight()) {
                        for (k in 0 until (GuideGrid.WINDOW_SEC / GuideGrid.SLOT_SEC).toInt()) {
                            Text(
                                timeText(context, windowStart + k * GuideGrid.SLOT_SEC), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1,
                                modifier = Modifier.offset(x = (k * GuideGrid.SLOT_SEC * unitsPerSec).dp + 8.dp),
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { i, channel ->
                            GuideRow(
                                index = i, channel = channel, loader = loader, registry = registry, memory = memory, nowSec = nowSec,
                                baseSec = baseSec, windowStart = windowStart, gridW = gridW, unitsPerSec = unitsPerSec,
                                onFocused = { detail = it }, onOpen = { onOpenChannel(i) }, onStep = ::step,
                            )
                        }
                    }
                    // The "now" marker: a thin line down the time area when now is inside the window.
                    if (nowSec in windowStart until windowStart + GuideGrid.WINDOW_SEC) {
                        Box(Modifier.offset(x = LABEL_W + ((nowSec - windowStart) * unitsPerSec).dp).width(2.dp).fillMaxHeight().background(Tokens.AccentPrimary))
                    }
                }
            }
        }
        DetailsStrip(detail, nowSec, Modifier.fillMaxWidth().height(DETAILS_H))
    }
}

@Composable
private fun GuideRow(
    index: Int, channel: LiveChannel, loader: GuideLoader, registry: GridRegistry, memory: FocusMemory, nowSec: Long,
    baseSec: Long, windowStart: Long, gridW: androidx.compose.ui.unit.Dp, unitsPerSec: Float,
    onFocused: (GuideDetail) -> Unit, onOpen: () -> Unit, onStep: (row: Int, p: EpgProgramme, direction: Int) -> Boolean,
) {
    // Filler entries ("No details available") say nothing: treat them as no guide.
    fun clean(l: List<EpgProgramme>?) = l?.filterNot { SportsLogic.isPlaceholder(it.name, channel.name) }
    var programmes: List<EpgProgramme>? by remember(channel.id) { mutableStateOf(clean(loader.peek(channel.id))) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        if (programmes != null) return@LaunchedEffect
        // A short pause first, so rows flicked past while scrolling are cancelled before they send a request.
        delay(150)
        val r = loader.loadQueued(channel)
        if (r == null) failed = true else { programmes = clean(r); failed = false }
    }
    val list = remember(programmes, baseSec) { programmes?.filter { it.stopSec > baseSec } }
    SideEffect { registry.rows[index] = list }
    DisposableEffect(index) { onDispose { registry.rows.remove(index) } }
    val labelId = guideRowFocusId(index)
    DisposableEffect(index) { registry.labelIds.add(labelId); onDispose { registry.labelIds.remove(labelId) } }

    fun requesterOrDefault(id: String?): FocusRequester = if (id != null) memory.requesterFor(id) else FocusRequester.Default
    /** Up/Down: the cell of the neighbouring row at the viewer's time column (the Group button above the first row). */
    fun vertical(dir: Int): FocusRequester {
        val r = index + dir
        if (r < 0) return memory.requesterFor(GUIDE_GROUP_ID)
        return requesterOrDefault(registry.cellFor(r, registry.anchorSec))
    }

    Row(Modifier.fillMaxWidth().height(ROW_H), verticalAlignment = Alignment.CenterVertically) {
        // The channel column. Select opens the channel; Right enters the row at the first visible programme.
        OsFocusable(
            id = labelId, memory = memory, variant = FocusVariant.ROW, onSelect = onOpen,
            modifier = Modifier.width(LABEL_W - 8.dp).height(CELL_H)
                .focusProperties {
                    if (index == 0) up = memory.requesterFor(GUIDE_GROUP_ID)
                    right = requesterOrDefault(registry.cellFor(index, registry.windowStartSec))
                },
            onFocusChange = { f ->
                if (f) onFocused(GuideDetail(channel, list?.let { GuideLogic.nowAndNext(it, nowSec).now }))
            },
        ) { _ ->
            Column(Modifier.padding(horizontal = Spacing.S2, vertical = 4.dp)) {
                Text(channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
                Text(PortalText.decodeEntities(channel.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
            }
        }
        Box(Modifier.width(8.dp))
        Box(Modifier.width(gridW).height(ROW_H)) {
            val cells = if (list.isNullOrEmpty()) emptyList() else GuideGrid.layout(list, windowStart, GuideGrid.WINDOW_SEC, unitsPerSec, CELL_GAP.value, 24f)
            if (cells.isEmpty()) {
                val text = when {
                    failed -> "Guide unavailable"
                    programmes == null -> ""
                    programmes.isNullOrEmpty() -> "No guide"
                    else -> "Nothing listed in this period"
                }
                val id = guideCellFocusId(index, null)
                DisposableEffect(id) { registry.cellIds.add(id); onDispose { registry.cellIds.remove(id) } }
                ProgrammeCell(
                    id = id, memory = memory, x = 0.dp, width = gridW - CELL_GAP, programme = null, text = text, live = false, nowSec = nowSec,
                    modifier = Modifier.focusProperties { up = vertical(-1); down = vertical(1) },
                    onFocus = { onFocused(GuideDetail(channel, null)) }, onSelect = onOpen,
                )
            } else cells.forEach { cell ->
                val p = cell.programme
                key(p.startSec) {
                    val id = guideCellFocusId(index, p.startSec)
                    DisposableEffect(id) { registry.cellIds.add(id); onDispose { registry.cellIds.remove(id) } }
                    ProgrammeCell(
                        id = id, memory = memory, x = cell.x.dp, width = cell.width.dp, programme = p, text = PortalText.decodeEntities(p.name),
                        live = p.startSec <= nowSec && nowSec < p.stopSec, nowSec = nowSec,
                        modifier = Modifier
                            .focusProperties { up = vertical(-1); down = vertical(1) }
                            .onPreviewKeyEvent { e ->
                                // Left and Right are handled here: the next programme of this row, scrolling the window at its edge.
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionRight || e.key == Key.DirectionLeft)) {
                                    onStep(index, p, if (e.key == Key.DirectionRight) 1 else -1)
                                } else false
                            },
                        onFocus = {
                            // Keep the time column while it still lies inside this programme (vertical moves); else take its visible start.
                            val s = maxOf(p.startSec, registry.windowStartSec)
                            val e = minOf(p.stopSec, registry.windowStartSec + GuideGrid.WINDOW_SEC)
                            if (registry.anchorSec !in s until e) registry.anchorSec = s
                            onFocused(GuideDetail(channel, p))
                        },
                        onSelect = onOpen,
                    )
                }
            }
        }
    }
}

/** One focusable box of the grid; [programme] null draws a quiet placeholder (loading, or [text] saying why there is none). */
@Composable
private fun ProgrammeCell(
    id: String, memory: FocusMemory, x: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, programme: EpgProgramme?, text: String,
    live: Boolean, nowSec: Long, modifier: Modifier, onFocus: () -> Unit, onSelect: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OsFocusable(
        id = id, memory = memory, variant = FocusVariant.BUTTON, onSelect = onSelect,
        modifier = Modifier.offset(x = x).width(width.coerceAtLeast(8.dp)).height(CELL_H).then(modifier),
        onFocusChange = { if (it) onFocus() },
    ) { focused ->
        val shape = RoundedCornerShape(Radii.Sm)
        Box(Modifier.fillMaxSize().background(if (focused) Color.Transparent else if (programme == null) Tokens.SurfaceEmpty else Tokens.Surface2, shape)) {
            if (programme != null) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(text, style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
                    Text("${timeText(context, programme.startSec)}–${timeText(context, programme.stopSec)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
                }
                if (live) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(GuideLogic.progress(programme, nowSec)).height(4.dp).background(Tokens.AccentPrimary))
            } else {
                Text(text, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** The strip under the grid: the focused programme's title, times, channel, category and description. */
@Composable
private fun DetailsStrip(detail: GuideDetail?, nowSec: Long, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.background(Tokens.Surface1, RoundedCornerShape(Radii.Md)).padding(horizontal = Spacing.S3, vertical = Spacing.S1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val ch = detail?.channel
        val p = detail?.programme
        when {
            detail == null -> Text("Move to a programme to see its details. Select watches the channel.", style = Tokens.Body, color = Tokens.TextSecondary)
            p == null -> {
                Text(PortalText.decodeEntities(ch!!.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                Text("${ch.number}  ·  No programme information for this time. Select watches the channel.", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
            }
            else -> {
                Text(PortalText.decodeEntities(p.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                val whenText = ProgrammeSearch.whenLabel(p.startSec, nowSec, java.util.TimeZone.getDefault(), java.util.Locale.getDefault()) { timeText(context, it) }
                val status = if (p.stopSec <= nowSec) "Ended" else whenText
                Text(
                    "${timeText(context, p.startSec)}–${timeText(context, p.stopSec)}  ·  ${ch!!.number} ${PortalText.decodeEntities(ch.name)}  ·  $status" +
                        (p.category?.let { "  ·  ${PortalText.decodeEntities(it)}" } ?: ""),
                    style = Tokens.Body, color = Tokens.AccentPrimary, maxLines = 1,
                )
                Text(
                    p.description?.let { PortalText.decodeEntities(it) } ?: "No description in the guide.", style = Tokens.Body, color = Tokens.TextSecondary,
                    maxLines = 3,
                )
            }
        }
    }
}
