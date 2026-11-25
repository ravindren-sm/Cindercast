package io.github.ravindren_sm.openstream.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.ChannelNames
import io.github.ravindren_sm.openstream.data.GuideLogic
import io.github.ravindren_sm.openstream.data.LiveInfo
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** A clock time in the viewer's 12 or 24 hour format. */
internal fun clockText(context: Context, sec: Long): String = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(sec * 1000))

/** A thin progress bar, 0 to 1. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).background(Tokens.Surface3, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Tokens.AccentPrimary, RoundedCornerShape(3.dp)))
    }
}

/**
 * The now-and-next block shared by the live player's banner and the Live TV side panel: the programme on now with its times,
 * a progress bar and the time left, then the next one. Draws nothing when [programmes] is null (still asking) or the channel
 * has no usable guide, so missing data never leaves a hole or an error.
 */
@Composable
fun NowNextBlock(channel: LiveChannel, programmes: List<EpgProgramme>?, nowSec: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nn = LiveInfo.nowNext(programmes, channel.name, nowSec) ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nn.now?.let { p ->
            Text(
                "${PortalText.decodeEntities(p.name)}  ·  ${clockText(context, p.startSec)} to ${clockText(context, p.stopSec)}  ·  ${LiveInfo.timeLeft(p.stopSec, nowSec)}",
                style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1,
            )
            ProgressBar(GuideLogic.progress(p, nowSec))
        }
        nn.next?.let { p ->
            Text("Next ${clockText(context, p.startSec)}: ${PortalText.decodeEntities(p.name)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
        }
    }
}

/**
 * The live player's bottom banner: channel number, name and quality badge, and what is on now and next when the guide is
 * known. Without guide data it is just the number and name. Not focusable.
 */
@Composable
fun LiveInfoBanner(channel: LiveChannel, programmes: List<EpgProgramme>?, nowSec: Long, modifier: Modifier = Modifier) {
    val parsed = ChannelNames.parse(PortalText.decodeEntities(channel.name))
    Column(
        modifier.fillMaxWidth()
            .background(Tokens.BgCanvas.copy(alpha = 0.85f), RoundedCornerShape(Radii.Md))
            .padding(horizontal = Spacing.S3, vertical = Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            if (channel.number.isNotBlank()) Text(channel.number, style = Tokens.TitleMedium, color = Tokens.AccentPrimary, maxLines = 1)
            Text(parsed.display, style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            parsed.quality?.let { QualityBadge(it) }
        }
        NowNextBlock(channel, programmes, nowSec)
    }
}

/**
 * The live player's mini channel list: the channels around the one being watched, as a left-hand panel. It is a display only:
 * the player surface keeps focus and moves [cursor] with the arrow keys, so no focus can be lost while it opens, closes or scrolls.
 * [playing] marks the channel on screen; [cursor] is the row Select would switch to.
 */
@Composable
fun MiniChannelList(channels: List<LiveChannel>, cursor: Int, playing: Int, window: IntRange, modifier: Modifier = Modifier) {
    Column(
        modifier.width(560.dp).background(Tokens.Surface1.copy(alpha = 0.94f), RoundedCornerShape(Radii.Lg)).padding(Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Channels", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
        Text("Up/Down choose · Select watches · Back closes", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
        for (i in window) {
            val c = channels.getOrNull(i) ?: continue
            val parsed = ChannelNames.parse(PortalText.decodeEntities(c.name))
            val on = i == cursor
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .background(if (on) Tokens.Surface3 else Tokens.Surface1, RoundedCornerShape(Radii.Sm))
                    .then(if (on) Modifier.border(3.dp, Tokens.FocusRing, RoundedCornerShape(Radii.Sm)) else Modifier)
                    .padding(horizontal = Spacing.S2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1),
            ) {
                Text(c.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.width(84.dp))
                Text(parsed.display, style = Tokens.BodyLarge, color = if (i == playing) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                parsed.quality?.let { QualityBadge(it) }
                if (i == playing) Text("  ▶", style = Tokens.Body, color = Tokens.AccentPrimary)
            }
        }
        if (window.isEmpty()) Text("No channels", style = Tokens.Body, color = Tokens.TextSecondary)
    }
}
