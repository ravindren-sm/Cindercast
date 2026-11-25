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
import androidx.compose.runtime.mutableLongStateOf
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
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.MiniListWindow
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.ui.components.LiveInfoBanner
import io.github.ravindren_sm.openstream.ui.components.MiniChannelList
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Focus ids of the player screen. The surface holds focus while playing; the retry button on an error. */
const val PLAYER_SURFACE_ID = "player-surface"
const val PLAYER_RETRY_ID = "player-retry"

/**
 * One HTTP client for every player, so opening and closing players does not leave idle connection pools and threads behind.
 * Built on first use, after the debug app has filled [PlayerHooks.networkInterceptors] (none in release).
 */
private val streamClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .apply { PlayerHooks.networkInterceptors.forEach { addNetworkInterceptor(it) } }
        .build()
}

/** The player's own HTTP identity, on the shared client, with the debug hooks attached (none in release). */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createStreamPlayer(context: Context, preview: Boolean = false): ExoPlayer {
    val client = streamClient
    val dataSource = OkHttpDataSource.Factory(client).setUserAgent("AndroidXMedia3/${MediaLibraryInfo.VERSION}")
    // If a hardware decoder fails, try the next one (a software decoder) before giving up.
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    // A smaller buffer than Media3's default (50 s and up to about 50 MB for 1080p): this stick has little memory to spare, and a
    // few seconds' margin is enough on a steady connection. Bytes decide before time, so the cap holds for high-bitrate streams.
    // A preview inset gets a much smaller buffer (it plays muted in a small box and is dropped as soon as focus moves on).
    val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .apply { if (preview) setBufferDurationsMs(2_500, 6_000, 1_000, 2_000) else setBufferDurationsMs(10_000, 25_000, 2_500, 5_000) }
        .setTargetBufferBytes(if (preview) 4 * 1024 * 1024 else 14 * 1024 * 1024)
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
 * Full-screen live playback. Resolves the channel with create_link, plays it, and shows an info banner
 * (number, name, quality badge, the programme now with its progress, and the next one) for a few seconds
 * on start and on any key. Left opens a mini channel list over the picture (Up and Down move in it, Select
 * switches, Right or Back closes; playback goes on and no key zaps while it is open). Rewinding and skipping
 * within the live window is on the media keys only. Up and Down (or Channel Up and Down) zap to the next and
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
    /** Where the banner reads the programme now and next; without it the banner is just the number and name. */
    guide: GuideLoader? = null,
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
    // The pending automatic restart after a stream error, cancelled when the channel changes or the viewer retries, so a
    // restart meant for the old stream never lands on the new one.
    val recoveryJob = remember { arrayOfNulls<Job>(1) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember { mutableStateOf(false) }
    var phase: Phase by remember { mutableStateOf(Phase.Resolving) }
    var buffering by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    // Mini channel list: whether it is open, and the row Select would switch to (kept by the surface, not by focus).
    var miniOpen by remember { mutableStateOf(false) }
    var miniCursor by remember { mutableIntStateOf(startIndex) }
    // The playing channel's guide for the banner, tagged with its channel so a late answer for the previous one is not shown.
    var guideData: Pair<String, List<EpgProgramme>>? by remember { mutableStateOf(null) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); nowSec = System.currentTimeMillis() / 1000 } }
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
                    recoveryJob[0]?.cancel()
                    recoveryJob[0] = scope.launch { delay(PlaybackRecovery.RETRY_DELAY_MILLIS); player.seekToDefaultPosition(); player.prepare() }
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
        recoveryJob[0]?.cancel(); recoveryJob[0] = null
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

    // The banner's guide: read when the channel changes or the overlay comes back (the loader keeps it ten minutes, so this is
    // usually free). The pause lets the stream request go first, and means a held zap key asks for no guide on the channels it
    // passes. A channel without a guide, or a failed read, leaves the banner at number and name.
    LaunchedEffect(channel?.id, overlayVisible) {
        if (guide == null || channel == null || !overlayVisible) return@LaunchedEffect
        if (guideData?.first != channel.id) delay(700)
        val list = withContext(Dispatchers.IO) { guide.load(channel) }
        guideData = if (list != null) channel.id to list else guideData?.takeIf { it.first == channel.id }
    }

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

    fun closeMini() { miniOpen = false }
    fun openMini() { miniCursor = index; miniOpen = true }

    /** Moves the mini list's cursor one channel (fetching the next page at the end of what is loaded). */
    fun moveMini(forward: Boolean) {
        scope.launch {
            zapLock.withLock {
                val r = withContext(Dispatchers.IO) { if (forward) zapper.next(miniCursor) else zapper.previous(miniCursor) }
                if (r is ZapResult.To) miniCursor = r.index
            }
        }
    }

    fun selectMini() {
        val target = miniCursor
        closeMini()
        if (target != index) go(target)
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
    // Back closes the mini list first, too, before it can leave the screen.
    BackHandler(enabled = miniOpen && !tracksOpen) { closeMini() }

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
                // The mini channel list owns the arrow and Select keys while it is open: the player keeps playing and nothing zaps.
                // Only the media keys (play, pause, rewind, skip) still reach the player.
                if (miniOpen && !tracksOpen && !isMediaKey(e.key)) {
                    if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) closeMini(); return@onKeyEvent true }
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent isMiniKey(e.key)
                    overlayTick++
                    when (e.key) {
                        Key.DirectionUp -> moveMini(forward = false)
                        Key.DirectionDown -> moveMini(forward = true)
                        Key.DirectionRight -> closeMini()
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> selectMini()
                        else -> {}
                    }
                    return@onKeyEvent isMiniKey(e.key)
                }
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
                    // Left opens the channel list. (It used to rewind: this portal keeps only about 20 s of live window, too little to
                    // be useful, so rewinding and skipping moved to the media keys.) Right just shows the banner.
                    e.key == Key.DirectionLeft -> { openMini(); true }
                    e.key == Key.DirectionRight -> true
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
                    // Whether the screen stays on is set in update below (this window only; no device setting changes).
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            // Keep the screen on only while a stream plays or is starting: when paused or failed, the Fire TV's screensaver may
            // start as usual, so a forgotten pause cannot keep the TV lit all night.
            update = { view -> view.keepScreenOn = phase !is Phase.Failed && !paused },
        )

        // Top left: the key hints, and under them where playback is relative to live (shown while paused, or for a moment after any key).
        if ((overlayVisible || paused) && channel != null && !tracksOpen && !miniOpen) {
            Column(Modifier.align(Alignment.TopStart).padding(Spacing.SafeHorizontal, Spacing.SafeVertical)) {
                if (overlayVisible) Text(
                    "Left: channels  ·  Up / Down: change channel  ·  Menu: audio & subtitles", style = Tokens.Body, color = Tokens.TextSecondary,
                    modifier = Modifier.background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S1),
                )
                if (phase is Phase.Playing) {
                    val label = LiveShift.label(behindLive)
                    val text = (if (paused) "Paused" else "") + (if (paused && label.isNotEmpty()) " · " else "") + label
                    if (text.isNotEmpty()) Text(
                        text, style = Tokens.BodyLarge, color = if (paused || label.startsWith("-")) Tokens.StatusWarning else Tokens.StatusSuccess,
                        modifier = Modifier.padding(top = Spacing.S1).background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S1),
                    )
                }
            }
        }

        // Bottom: the info banner. Missing guide data degrades to number and name.
        val showBanner = overlayVisible && channel != null && !tracksOpen && !miniOpen
        if (showBanner && channel != null) {
            LiveInfoBanner(
                channel, guideData?.takeIf { it.first == channel.id }?.second, nowSec,
                Modifier.align(Alignment.BottomCenter).padding(Spacing.SafeHorizontal, Spacing.SafeVertical),
            )
        }

        if (miniOpen) {
            MiniChannelList(
                channels.items, miniCursor, index, MiniListWindow.range(channels.items.size, miniCursor),
                Modifier.align(Alignment.CenterStart).padding(start = Spacing.SafeHorizontal),
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
            Phase.Resolving -> StatusText(notice ?: "Starting channel…", lifted = showBanner)
            Phase.Playing -> if (notice != null) StatusText(notice!!, lifted = showBanner) else if (buffering) StatusText("Buffering…", lifted = showBanner)
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
private fun androidx.compose.foundation.layout.BoxScope.StatusText(text: String, lifted: Boolean = false) {
    // Lifted above the info banner when that is showing.
    Text(
        text, style = Tokens.BodyLarge, color = Tokens.TextPrimary,
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.S4, end = Spacing.S4, top = Spacing.S4, bottom = if (lifted) 260.dp else Spacing.S4)
            .background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
    )
}

/** Keys the player still handles while the mini channel list is open. */
private fun isMediaKey(key: Key): Boolean =
    key == Key.MediaPlayPause || key == Key.MediaPlay || key == Key.MediaPause || key == Key.MediaRewind || key == Key.MediaFastForward

/** Keys the mini channel list takes (and so keeps from the player's zapping, digits and drawer). */
private fun isMiniKey(key: Key): Boolean =
    key == Key.DirectionUp || key == Key.DirectionDown || key == Key.DirectionLeft || key == Key.DirectionRight ||
        key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter ||
        key == Key.ChannelUp || key == Key.ChannelDown || key == Key.Menu || key == Key.Captions || key == Key.L || digitOf(key) != null

/** The digit a key stands for (main row or number pad), or null. */
private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'; Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'; Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
