package io.github.ravindren_sm.openstream.ui.screens

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.PlaybackMessages
import io.github.ravindren_sm.openstream.data.PlayerHooks
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Focus ids of the player screen. The surface holds focus while playing; the retry button on an error. */
const val PLAYER_SURFACE_ID = "player-surface"
const val PLAYER_RETRY_ID = "player-retry"

/** The player's own HTTP identity and client, with the debug hooks attached (none in release). */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createLivePlayer(context: Context): ExoPlayer {
    val client = OkHttpClient.Builder()
        .apply { PlayerHooks.networkInterceptors.forEach { addNetworkInterceptor(it) } }
        .build()
    val dataSource = OkHttpDataSource.Factory(client).setUserAgent("AndroidXMedia3/${MediaLibraryInfo.VERSION}")
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
        .build()
        .also { p -> PlayerHooks.newAnalyticsListeners().forEach { p.addAnalyticsListener(it) } }
}

private sealed interface Phase {
    data object Resolving : Phase
    data object Playing : Phase
    class Failed(val message: String) : Phase
}

/**
 * Full-screen live playback. Resolves the channel with create_link, plays it, and shows the channel
 * for a few seconds on start and on any key. Errors show fixed text and a retry button; Back is
 * handled by the shell (it leaves the screen, which releases the player).
 *
 * The stream address stays in this function's memory: it is never logged, shown or saved.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    channel: LiveChannel?,
    playback: LivePlayback,
    memory: FocusMemory,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { createLivePlayer(context) }
    var phase: Phase by remember { mutableStateOf(Phase.Resolving) }
    var buffering by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // A live window moved past the playhead (for example after a long pause): rejoin live.
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                phase = Phase.Failed(PlaybackMessages.forError(error.errorCode, http))
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    // Leaving the app (Home, screensaver): stop playing, so audio does not carry on behind the launcher.
    // Coming back: rejoin the live edge. Ignored until a stream is actually playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (phase is Phase.Playing) { player.seekToDefaultPosition(); player.prepare(); player.play() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Resolve and start; runs again on every retry.
    LaunchedEffect(channel?.id, attempt) {
        if (channel == null) { phase = Phase.Failed("This channel is not available."); return@LaunchedEffect }
        phase = Phase.Resolving
        player.stop()
        when (val link = withContext(Dispatchers.IO) { playback.resolve(channel) }) {
            is StreamLink.Failed -> phase = Phase.Failed(link.message)
            is StreamLink.Ok -> {
                player.setMediaItem(MediaItem.fromUri(link.url))
                player.prepare()
                player.playWhenReady = true
                phase = Phase.Playing
            }
        }
    }

    // Channel overlay: shown on start and on any key press, hidden after 4 s.
    LaunchedEffect(overlayTick) { overlayVisible = true; delay(4_000); overlayVisible = false }

    // Focus: the surface while playing (so keys reach it), the retry button on an error.
    LaunchedEffect(phase is Phase.Failed) {
        withFrameNanos { }
        memory.tryFocus(if (phase is Phase.Failed) PLAYER_RETRY_ID else PLAYER_SURFACE_ID)
    }

    val requester = remember { memory.requesterFor(PLAYER_SURFACE_ID) }
    DisposableEffect(Unit) { onDispose { memory.release(PLAYER_SURFACE_ID) } }

    Box(
        modifier
            .fillMaxSize()
            .background(Tokens.BgCanvas)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) memory.focusedId = PLAYER_SURFACE_ID }
            .focusable()
            .onKeyEvent { overlayTick++; false },   // observe only; Back still reaches the shell
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true                       // this window only; no device setting changes
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
        )

        if (overlayVisible && channel != null) {
            Text(
                listOf(channel.number, channel.name).filter { it.isNotBlank() }.joinToString("  "),
                style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.SafeHorizontal, Spacing.SafeVertical)
                    .background(Tokens.BgCanvas.copy(alpha = 0.7f))
                    .padding(Spacing.S2),
            )
        }

        when (val p = phase) {
            Phase.Resolving -> StatusText("Starting channel…")
            Phase.Playing -> if (buffering) StatusText("Buffering…")
            is Phase.Failed -> Column(
                Modifier.align(Alignment.Center).background(Tokens.BgCanvas.copy(alpha = 0.85f)).padding(Spacing.S3),
                verticalArrangement = Arrangement.spacedBy(Spacing.S2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.message, style = Tokens.BodyLarge, color = Tokens.StatusError)
                OsFocusable(id = PLAYER_RETRY_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { attempt++ }) { _ ->
                    Text("Select to try again", style = Tokens.BodyLarge, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
                Text("Back returns to the channel list.", style = Tokens.Body, color = Tokens.TextSecondary)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StatusText(text: String) {
    Text(
        text, style = Tokens.BodyLarge, color = Tokens.TextPrimary,
        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.S4).background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
    )
}
