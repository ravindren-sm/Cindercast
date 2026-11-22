package io.github.ravindren_sm.openstream.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import android.net.Uri
import androidx.activity.compose.BackHandler
import io.github.ravindren_sm.openstream.ui.components.LanguageSubtitlesOverlay
import io.github.ravindren_sm.openstream.ui.components.audioChoices
import io.github.ravindren_sm.openstream.ui.components.subtitleChoices
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.HistoryRecorder
import io.github.ravindren_sm.openstream.data.NoHistory
import io.github.ravindren_sm.openstream.data.PlayTarget
import io.github.ravindren_sm.openstream.data.PlaybackRecovery
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.SubtitleMime
import io.github.ravindren_sm.openstream.data.TrackLabels
import io.github.ravindren_sm.openstream.data.PlaybackMessages
import io.github.ravindren_sm.openstream.data.ResumePolicy
import io.github.ravindren_sm.openstream.data.ResumeStore
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.data.TimeText
import io.github.ravindren_sm.openstream.data.VodPlayback
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface VodPhase {
    data object Resolving : VodPhase
    data object Playing : VodPhase
    class Failed(val message: String) : VodPhase
}

private const val SEEK_MILLIS = 10_000L
private const val BIG_SEEK_MILLIS = 30_000L

/**
 * Full-screen movie playback. Left and Right seek by 10 s (rewind and fast-forward keys by 30 s), Select or
 * play/pause toggles pause, and the position is saved every few seconds so the next play resumes there (not
 * within 15 s of the start or 45 s of the end). The bar and title show on any key and stay while paused.
 * Errors show fixed text and a retry button; Back leaves the screen (the shell handles it).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    target: PlayTarget?,
    playback: VodPlayback,
    resume: ResumeStore,
    memory: FocusMemory,
    portalUrl: String,
    history: HistoryRecorder = NoHistory,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { createStreamPlayer(context) }
    val recovery = remember { PlaybackRecovery() }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember { mutableStateOf(false) }
    var phase: VodPhase by remember { mutableStateOf(VodPhase.Resolving) }
    var buffering by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var notice: String? by remember { mutableStateOf(null) }
    var resumeApplied by remember { mutableStateOf(false) }
    val id = target?.resumeKey

    fun knownDuration(): Long = player.duration.takeIf { it > 0 } ?: 0L

    /** Saves the position, or forgets it when the title was watched to the end. */
    fun savePosition() {
        if (id == null || phase !is VodPhase.Playing) return
        val pos = player.currentPosition
        val dur = knownDuration()
        if (target != null && pos > 0) history.record(target, pos, dur)   // Home's Continue watching
        when {
            ResumePolicy.shouldSave(pos, dur) -> resume.put(id, pos)
            dur > 0 && pos > dur - ResumePolicy.NEAR_END_MILLIS -> resume.clear(id)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && !resumeApplied) {
                    resumeApplied = true
                    val start = ResumePolicy.startPosition(id?.let(resume::get), knownDuration())
                    if (start > 0) { player.seekTo(start); notice = "Resumed at ${TimeText.format(start)}" }
                }
                if (state == Player.STATE_ENDED && id != null) {
                    resume.clear(id)
                    if (target != null) history.record(target, knownDuration(), knownDuration())   // finished: leaves Continue watching
                }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { paused = !playWhenReady }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(error: PlaybackException) {
                if (recovery.shouldRecover(error.errorCode)) {
                    // A momentary failure: pick up again from where it stopped, without the viewer doing anything.
                    notice = "Recovering…"
                    scope.launch { delay(PlaybackRecovery.RETRY_DELAY_MILLIS); player.prepare() }
                    return
                }
                val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                phase = VodPhase.Failed(PlaybackMessages.forError(error.errorCode, http))
            }
        }
        player.addListener(listener)
        onDispose { savePosition(); player.removeListener(listener); player.release() }
    }

    // Leaving the app: save, and stop the sound. The viewer presses Select to carry on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { savePosition(); player.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(id, attempt) {   // id is the resume key
        if (target == null) { phase = VodPhase.Failed("This title is not available."); return@LaunchedEffect }
        phase = VodPhase.Resolving
        resumeApplied = false
        recovery.reset()
        player.stop()
        when (val link = withContext(Dispatchers.IO) { playback.resolve(target.file) }) {
            is StreamLink.Failed -> phase = VodPhase.Failed(link.message)
            is StreamLink.Ok -> {
                // External subtitle files from the portal ride along with the stream; their addresses never leave memory.
                val subtitles = link.subtitles.mapNotNull { s ->
                    val url = PosterUrl.resolve(s.url, portalUrl) ?: return@mapNotNull null
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                        .setMimeType(SubtitleMime.forUrl(url))
                        .setLabel(s.language?.takeIf { it.length > 3 } ?: TrackLabels.languageName(s.language))
                        .setLanguage(s.language?.takeIf { it.length in 2..3 })
                        .build()
                }
                player.setMediaItem(MediaItem.Builder().setUri(link.url).setSubtitleConfigurations(subtitles).build())
                player.prepare()
                player.playWhenReady = true
                paused = false
                phase = VodPhase.Playing
            }
        }
    }

    // Progress for the bar; the saved position every 5 s.
    LaunchedEffect(Unit) {
        while (true) { position = player.currentPosition.coerceAtLeast(0); duration = knownDuration(); delay(500) }
    }
    LaunchedEffect(phase) { if (phase is VodPhase.Playing) while (true) { delay(5_000); savePosition() } }

    LaunchedEffect(overlayTick, paused) { overlayVisible = true; if (!paused) { delay(4_000); overlayVisible = false } }
    LaunchedEffect(notice) { if (notice != null) { delay(3_000); notice = null } }

    LaunchedEffect(phase is VodPhase.Failed) {
        withFrameNanos { }
        memory.tryFocus(if (phase is VodPhase.Failed) PLAYER_RETRY_ID else PLAYER_SURFACE_ID)
    }

    fun seekBy(delta: Long) {
        val dur = knownDuration()
        var target = (player.currentPosition + delta).coerceAtLeast(0)
        if (dur > 0) target = target.coerceAtMost(dur - 1_000)
        player.seekTo(target)
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
                when (e.key) {
                    Key.Menu, Key.Captions, Key.L -> { tracksOpen = true; true }
                    Key.DirectionLeft -> { seekBy(-SEEK_MILLIS); true }
                    Key.DirectionRight -> { seekBy(SEEK_MILLIS); true }
                    Key.MediaRewind -> { seekBy(-BIG_SEEK_MILLIS); true }
                    Key.MediaFastForward -> { seekBy(BIG_SEEK_MILLIS); true }
                    Key.MediaPlayPause, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { player.playWhenReady = !player.playWhenReady; true }
                    Key.MediaPlay -> { player.playWhenReady = true; true }
                    Key.MediaPause -> { player.playWhenReady = false; true }
                    else -> false   // Back and everything else still reach the shell
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
        )

        if (overlayVisible && target != null && phase is VodPhase.Playing) {
            Text(
                PortalText.decodeEntities(target.title), style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.SafeHorizontal, Spacing.SafeVertical)
                    .background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
            )
            ProgressBar(position, duration, Modifier.align(Alignment.BottomCenter))
        }

        when (val p = phase) {
            VodPhase.Resolving -> Status("Starting…")
            VodPhase.Playing -> when {
                notice != null -> Status(notice!!)
                paused -> Status("Paused")
                buffering -> Status("Buffering…")
            }
            is VodPhase.Failed -> Column(
                Modifier.align(Alignment.Center).background(Tokens.BgCanvas.copy(alpha = 0.85f)).padding(Spacing.S3),
                verticalArrangement = Arrangement.spacedBy(Spacing.S2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.message, style = Tokens.BodyLarge, color = Tokens.StatusError)
                OsFocusable(id = PLAYER_RETRY_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { attempt++ }) { _ ->
                    Text("Select to try again", style = Tokens.BodyLarge, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
                Text("Back returns to the title.", style = Tokens.Body, color = Tokens.TextSecondary)
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
private fun ProgressBar(position: Long, duration: Long, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.SafeHorizontal, Spacing.SafeVertical).background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S1),
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(Tokens.Surface3)) {
            val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Tokens.AccentPrimary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimeText.format(position), style = Tokens.Mono, color = Tokens.TextPrimary)
            Text("Menu: audio & subtitles", style = Tokens.Body, color = Tokens.TextSecondary)
            Text(if (duration > 0) TimeText.format(duration) else "", style = Tokens.Mono, color = Tokens.TextSecondary)
        }
    }
}

@Composable
private fun BoxScope.Status(text: String) {
    Text(
        text, style = Tokens.BodyLarge, color = Tokens.TextPrimary,
        modifier = Modifier.align(Alignment.Center).background(Tokens.BgCanvas.copy(alpha = 0.7f)).padding(Spacing.S2),
    )
}
