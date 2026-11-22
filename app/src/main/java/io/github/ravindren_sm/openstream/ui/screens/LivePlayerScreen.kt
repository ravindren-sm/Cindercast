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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import io.github.ravindren_sm.openstream.ui.components.LanguageSubtitlesOverlay
import io.github.ravindren_sm.openstream.ui.components.audioChoices
import io.github.ravindren_sm.openstream.ui.components.subtitleChoices
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import io.github.ravindren_sm.openstream.data.LiveShift
import io.github.ravindren_sm.openstream.data.SeekRamp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.LiveZapper
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.PlaybackMessages
import io.github.ravindren_sm.openstream.data.PlaybackRecovery
import io.github.ravindren_sm.openstream.data.PlayerHooks
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.data.ZapResult
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Focus ids of the player screen. The surface holds focus while playing; the retry button on an error. */
const val PLAYER_SURFACE_ID = "player-surface"
const val PLAYER_RETRY_ID = "player-retry"

/** The player's own HTTP identity and client, with the debug hooks attached (none in release). */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createStreamPlayer(context: Context): ExoPlayer {
    val client = OkHttpClient.Builder()
        .apply { PlayerHooks.networkInterceptors.forEach { addNetworkInterceptor(it) } }
        .build()
    val dataSource = OkHttpDataSource.Factory(client).setUserAgent("AndroidXMedia3/${MediaLibraryInfo.VERSION}")
    // If a hardware decoder fails, try the next one (a software decoder) before giving up.
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    // A smaller buffer than Media3's default (50 s and up to about 50 MB for 1080p): this stick has little memory to spare, and a
    // few seconds' margin is enough on a steady connection. Bytes decide before time, so the cap holds for high-bitrate streams.
    val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(10_000, 25_000, 2_500, 5_000)
        .setTargetBufferBytes(14 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
    return ExoPlayer.Builder(context, renderers)
        .setLoadControl(load)
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
 * for a few seconds on start and on any key. Up and Down (or Channel Up and Down) zap to the next and
 * previous channel of the genre; typing digits goes to that channel number after a short pause or on
 * Enter. A zap waits 350 ms before asking the portal for a stream, so holding a key does not request
 * every channel it passes. Errors show fixed text and a retry button; Back is handled by the shell (it
 * leaves the screen, which releases the player). [onChannelChanged] reports the new list position, so
 * Back can return to the channel being watched.
 *
 * The stream address stays in this function's memory: it is never logged, shown or saved.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    channels: PagedChannels,
    startIndex: Int,
    playback: LivePlayback,
    memory: FocusMemory,
    onChannelChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zapper = remember(channels) { LiveZapper(channels) }
    val zapLock = remember { Mutex() }
    var index by remember { mutableIntStateOf(startIndex) }
    val channel: LiveChannel? = channels.items.getOrNull(index)
    var zapped by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var notice: String? by remember { mutableStateOf(null) }
    val player = remember { createStreamPlayer(context) }
    val recovery = remember { PlaybackRecovery() }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember { mutableStateOf(false) }
    var phase: Phase by remember { mutableStateOf(Phase.Resolving) }
    var buffering by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    // How far behind the live edge playback is (null when the stream is not live or the value is not known).
    var behindLive: Long? by remember { mutableStateOf(null) }
    LaunchedEffect(player) {
        while (true) {
            val offset = player.currentLiveOffset
            behindLive = if (player.isCurrentMediaItemLive && offset != androidx.media3.common.C.TIME_UNSET) offset else null
            delay(500)
        }
    }

    /** Pause, rewind and skip within what the stream keeps. A live stream that keeps no history cannot be moved. */
    fun shift(delta: Long) {
        if (!player.isCurrentMediaItemSeekable) { notice = "This channel cannot be rewound"; return }
        if (delta > 0 && LiveShift.reachesLive(behindLive, delta)) { player.seekToDefaultPosition(); return }
        player.seekTo((player.currentPosition + delta).coerceAtLeast(0))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { paused = !playWhenReady }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // A live window moved past the playhead (for example after a long pause): rejoin live.
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                if (recovery.shouldRecover(error.errorCode)) {
                    // A momentary failure: rejoin the live edge without the viewer doing anything.
                    notice = "Recovering…"
                    scope.launch { delay(PlaybackRecovery.RETRY_DELAY_MILLIS); player.seekToDefaultPosition(); player.prepare() }
                    return
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
        recovery.reset()
        player.stop()
        if (zapped) delay(350)   // a held key moves on before this runs (the effect restarts), so only the last channel resolves
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

    fun go(i: Int) { zapped = true; index = i; overlayTick++; onChannelChanged(i) }

    fun zap(forward: Boolean) {
        scope.launch {
            zapLock.withLock {
                when (val r = withContext(Dispatchers.IO) { if (forward) zapper.next(index) else zapper.previous(index) }) {
                    is ZapResult.To -> go(r.index)
                    ZapResult.AtEnd -> notice = if (forward) "Last channel" else "First channel"
                    ZapResult.NotFound -> {}
                }
            }
        }
    }

    fun commitTyped() {
        val number = typed
        typed = ""
        if (number.isEmpty()) return
        scope.launch {
            zapLock.withLock {
                when (val r = withContext(Dispatchers.IO) { zapper.byNumber(number) }) {
                    is ZapResult.To -> go(r.index)
                    else -> notice = "No channel $number"
                }
            }
        }
    }

    // Typed digits go to that channel after a 2 s pause (each digit restarts the wait).
    LaunchedEffect(typed) { if (typed.isNotEmpty()) { delay(2_000); commitTyped() } }
    // Messages ("Last channel", "No channel 123") fade after a moment.
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }

    // Focus: the surface while playing (so keys reach it), the retry button on an error.
    LaunchedEffect(phase is Phase.Failed) {
        withFrameNanos { }
        memory.tryFocus(if (phase is Phase.Failed) PLAYER_RETRY_ID else PLAYER_SURFACE_ID)
    }

    // Back closes the drawer first and hands focus back to the player.
    fun closeTracks() { tracksOpen = false; scope.launch { withFrameNanos { }; memory.tryFocus(PLAYER_SURFACE_ID) } }
    BackHandler(enabled = tracksOpen) { closeTracks() }

    val requester = remember { memory.requesterFor(PLAYER_SURFACE_ID) }
    DisposableEffect(Unit) { onDispose { memory.release(PLAYER_SURFACE_ID) } }

    Box(
        modifier
            .fillMaxSize()
            .background(Tokens.BgCanvas)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) memory.focusedId = PLAYER_SURFACE_ID }
            .focusable()
            .onKeyEvent { e ->
                // While the drawer is open it owns the keys. Back closes it here (on key release, consuming both events)
                // so it works wherever focus is; the system Back handler below stays as a fallback.
                if (tracksOpen && e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) closeTracks(); return@onKeyEvent true }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (tracksOpen) return@onKeyEvent false
                overlayTick++
                val digit = digitOf(e.key)
                when {
                    e.key == Key.Menu || e.key == Key.Captions || e.key == Key.L -> { tracksOpen = true; true }
                    digit != null -> { if (typed.length < 5) typed += digit; true }
                    e.key == Key.DirectionUp || e.key == Key.ChannelUp -> { zap(forward = true); true }
                    e.key == Key.DirectionDown || e.key == Key.ChannelDown -> { zap(forward = false); true }
                    typed.isNotEmpty() && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter) -> { commitTyped(); true }
                    e.key == Key.DirectionLeft -> { shift(-SeekRamp.stepMillis(e.nativeKeyEvent.repeatCount)); true }
                    e.key == Key.DirectionRight -> { shift(SeekRamp.stepMillis(e.nativeKeyEvent.repeatCount)); true }
                    e.key == Key.MediaRewind -> { shift(-30_000); true }
                    e.key == Key.MediaFastForward -> { shift(30_000); true }
                    e.key == Key.MediaPlayPause || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter -> { player.playWhenReady = !player.playWhenReady; true }
                    e.key == Key.MediaPlay -> { player.playWhenReady = true; true }
                    e.key == Key.MediaPause -> { player.playWhenReady = false; true }
                    else -> false   // Back and everything else still reach the shell
                }
            },
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

        if (overlayVisible && channel != null && !tracksOpen) {
            Text(
                "Menu: audio & subtitles", style = Tokens.Body, color = Tokens.TextSecondary,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.SafeHorizontal, Spacing.SafeVertical + 76.dp),
            )
        }

        // Where playback is relative to live (shown while paused, or for a moment after any key).
        if ((overlayVisible || paused) && channel != null && !tracksOpen && phase is Phase.Playing) {
            val label = LiveShift.label(behindLive)
            Text(
                (if (paused) "Paused" else "") + (if (paused && label.isNotEmpty()) " · " else "") + label,
                style = Tokens.BodyLarge, color = if (paused || label.startsWith("-")) Tokens.StatusWarning else Tokens.StatusSuccess,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.SafeHorizontal, Spacing.SafeVertical + 40.dp)
                    .background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
            )
        }

        if (typed.isNotEmpty()) {
            Text(
                "Channel $typed", style = Tokens.TitleMedium, color = Tokens.AccentPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.SafeHorizontal, Spacing.SafeVertical)
                    .background(Tokens.BgCanvas.copy(alpha = 0.7f))
                    .padding(Spacing.S2),
            )
        }

        when (val p = phase) {
            Phase.Resolving -> StatusText(notice ?: "Starting channel…")
            Phase.Playing -> if (notice != null) StatusText(notice!!) else if (buffering) StatusText("Buffering…")
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

        if (tracksOpen) {
            LanguageSubtitlesOverlay(
                audio = audioChoices(player, tracks), subtitles = subtitleChoices(player, tracks), memory = memory,
                onDone = ::closeTracks,
            )
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

/** The digit a key stands for (main row or number pad), or null. */
private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'; Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'; Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
