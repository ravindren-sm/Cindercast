package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.TrackInfo
import io.github.ravindren_sm.openstream.data.TrackLabels
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** One row of the overlay: a track (or "Off"), its tags, whether it is playing now, and how to switch to it. */
class TrackChoice(val title: String, val tags: List<String>, val selected: Boolean, val onSelect: () -> Unit)

@androidx.annotation.OptIn(UnstableApi::class)
private fun Format.toTrackInfo(text: Boolean) = TrackInfo(
    language = language,
    label = label,
    // A side-loaded subtitle is parsed on load: its original type (SRT, VTT) is kept in the codecs field, the
    // container type or, for broadcast captions, the sample type.
    mimeType = if (text) textType() else sampleMimeType,
    channelCount = channelCount.takeIf { it != Format.NO_VALUE } ?: 0,
    describesVideo = roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0,
    isAlternate = roleFlags and C.ROLE_FLAG_ALTERNATE != 0,
    isDub = roleFlags and C.ROLE_FLAG_DUB != 0,
    isMain = roleFlags and C.ROLE_FLAG_MAIN != 0,
)

/** Broadcast captions keep their type in the sample type; a side-loaded or HLS subtitle file in the codecs or container field. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun Format.textType(): String? {
    val sample = sampleMimeType
    val broadcast = sample != null && (sample.startsWith("application/cea-") || sample == "application/dvbsubs" || sample == "application/x-teletext")
    return if (broadcast) sample else (codecs ?: containerMimeType ?: sample)
}

/** The playable audio tracks of [tracks], with the one in use marked. Empty or one entry means there is nothing to choose. */
@androidx.annotation.OptIn(UnstableApi::class)
fun audioChoices(player: Player, tracks: Tracks): List<TrackChoice> {
    val out = mutableListOf<TrackChoice>()
    var ordinal = 0
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            ordinal++
            val info = group.getTrackFormat(i).toTrackInfo(text = false)
            out += TrackChoice(TrackLabels.title(info, ordinal), TrackLabels.audioTags(info), group.isTrackSelected(i)) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
            }
        }
    }
    return out
}

/** "Off" first, then the playable text tracks (side-loaded files, captions, DVB). */
@androidx.annotation.OptIn(UnstableApi::class)
fun subtitleChoices(player: Player, tracks: Tracks): List<TrackChoice> {
    val out = mutableListOf<TrackChoice>()
    val anySelected = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val disabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
    out += TrackChoice("Off", emptyList(), selected = disabled || !anySelected) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    var ordinal = 0
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            ordinal++
            val info = group.getTrackFormat(i).toTrackInfo(text = true)
            out += TrackChoice(TrackLabels.title(info, ordinal), TrackLabels.textTags(info), group.isTrackSelected(i) && !disabled) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
            }
        }
    }
    return out
}

private fun audioRowId(i: Int) = "ls-audio-$i"
private fun subtitleRowId(i: Int) = "ls-sub-$i"
private const val DONE_ID = "ls-done"

/**
 * The right-hand drawer shared by the live and movie players: audio tracks on one side, subtitles on the other,
 * a check on what is playing, a summary line and Done. Focus cannot leave the drawer (the player's keys are
 * ignored while it is open), and Select on a row switches at once.
 */
@Composable
fun LanguageSubtitlesOverlay(
    audio: List<TrackChoice>,
    subtitles: List<TrackChoice>,
    memory: FocusMemory,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val first = audio.indexOfFirst { it.selected }.takeIf { it >= 0 }?.let(::audioRowId)
            ?: subtitles.indexOfFirst { it.selected }.takeIf { it >= 0 }?.let(::subtitleRowId)
        memory.tryFocusFirst(first, DONE_ID)
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .width(960.dp).fillMaxHeight()
                .background(Tokens.Surface1.copy(alpha = 0.97f))
                .focusGroup()   // nothing else on these screens can take focus, so no extra trap is needed
                .padding(Spacing.S3),
            verticalArrangement = Arrangement.spacedBy(Spacing.S2),
        ) {
            Text("Language & Subtitles", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                TrackColumn("Audio track", audio, hint = "No separate audio tracks", hintWhenAtMost = 0, idOf = ::audioRowId, memory = memory, modifier = Modifier.weight(1f))
                // Subtitles always start with "Off", so the hint shows when that is the only entry.
                TrackColumn("Subtitles", subtitles, hint = "No subtitle tracks in this stream", hintWhenAtMost = 1, idOf = ::subtitleRowId, memory = memory, modifier = Modifier.weight(1f))
            }
            Text(
                TrackLabels.summary(
                    audio.firstOrNull { it.selected }?.let { (it.title + " " + it.tags.joinToString(" ")).trim() },
                    subtitles.firstOrNull { it.selected && it.title != "Off" }?.title,
                ),
                style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1,
            )
            OsFocusable(id = DONE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onDone) { _ ->
                Text("Done", style = Tokens.TitleMedium, color = Tokens.TextPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S1))
            }
        }
    }
}

@Composable
private fun TrackColumn(
    title: String,
    choices: List<TrackChoice>,
    hint: String,
    hintWhenAtMost: Int,
    idOf: (Int) -> String,
    memory: FocusMemory,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(title, style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            itemsIndexed(choices) { index, choice ->
                OsFocusable(id = idOf(index), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = choice.onSelect) { _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 2.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                        Text(if (choice.selected) "✔" else " ", style = Tokens.BodyLarge, color = Tokens.AccentPrimary, modifier = Modifier.width(32.dp))
                        Text(choice.title, style = Tokens.BodyLarge, color = if (choice.selected) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        if (choice.tags.isNotEmpty()) Text(choice.tags.joinToString(" "), style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
                    }
                }
            }
            if (choices.size <= hintWhenAtMost) item { Text(hint, style = Tokens.Body, color = Tokens.TextSecondary) }
        }
    }
}
