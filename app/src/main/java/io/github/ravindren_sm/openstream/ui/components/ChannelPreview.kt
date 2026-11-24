package io.github.ravindren_sm.openstream.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.screens.createStreamPlayer
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long the focus must rest on a channel before its preview starts, so scrolling through a list asks the portal for nothing. */
const val PREVIEW_SETTLE_MILLIS = 900L

/**
 * A small muted live picture of [channel], for beside a channel list: what the channel is showing, without opening it. It
 * starts after focus has rested on the channel for [PREVIEW_SETTLE_MILLIS], plays with no sound and a small buffer, and stops
 * when [channel] becomes null (focus left the list), the app leaves the screen, or the screen goes away. Select on the row
 * still opens the full player with sound. Not focusable: the D-pad never lands here.
 *
 * Each preview is one create_link request and one stream, like opening the channel; at most one runs at a time.
 */
@OptIn(UnstableApi::class)
@Composable
fun ChannelPreview(channel: LiveChannel?, playback: LivePlayback, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { createStreamPlayer(context, preview = true).also { it.volume = 0f } }
    // What the box says while there is no picture: waiting for focus to settle, starting, or no stream.
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) { playing = true; status = "" } }
            override fun onPlayerError(error: PlaybackException) { playing = false; player.stop(); status = "No preview for this channel" }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player.stop(); playing = false } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(channel?.id) {
        player.stop(); player.clearMediaItems(); playing = false
        if (channel == null) { status = "Move to a channel to see what it shows"; return@LaunchedEffect }
        status = "Preview…"
        delay(PREVIEW_SETTLE_MILLIS)
        when (val link = withContext(Dispatchers.IO) { playback.resolve(channel) }) {
            is StreamLink.Failed -> status = "No preview for this channel"
            is StreamLink.Ok -> {
                player.setMediaItem(MediaItem.fromUri(link.url))
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Radii.Md))
            .background(Tokens.Surface1).border(BorderStroke(1.dp, Tokens.Surface3), RoundedCornerShape(Radii.Md)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
        )
        if (!playing && status.isNotEmpty()) {
            Text(status, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2, modifier = Modifier.align(Alignment.Center).padding(Spacing.S2))
        }
        if (playing && channel != null) {
            Text(
                PortalText.decodeEntities(channel.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.S1).background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(horizontal = Spacing.S1),
            )
        }
    }
}
