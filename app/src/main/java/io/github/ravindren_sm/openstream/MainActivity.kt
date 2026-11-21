package io.github.ravindren_sm.openstream

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import io.github.ravindren_sm.openstream.provider.ClientIdentity
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.ProbeOutcome
import io.github.ravindren_sm.openstream.provider.Redactor
import io.github.ravindren_sm.openstream.provider.RequestBudget
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import io.github.ravindren_sm.openstream.provider.StalkerResult
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Phase 3 debug player (one channel, live TV).
 *
 * Purpose: find out whether a stream that the Mac probe could not open (403 at the edge)
 * plays from the Fire TV itself through Media3. Flow, run once at launch:
 *   handshake -> get_profile -> get_genres -> channel in genre -> create_link -> Media3 HLS.
 *
 * Diagnostics (debug only, CLAUDE.md rule 13), all redacted with the provider's Redactor:
 * - every portal step's status and safe summary lines,
 * - every stream HTTP response seen by the player's OkHttp client (status, content type,
 *   whether the host changed), including each redirect hop,
 * - player state changes, first rendered frame time, video/audio formats, load and
 *   playback errors (HTTP response codes when available).
 * Logs go to logcat tag [TAG] and to the on-screen panel. Raw URLs, MAC and tokens never
 * appear in either.
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val lines: SnapshotStateList<String> = mutableStateListOf()
    private var redactor = Redactor(emptyMap())
    private var player: ExoPlayer? = null
    private val startMs = SystemClock.elapsedRealtime()

    // VOD seek check state (main thread only).
    private var seekCheckPending = false
    private var seekStartedAt = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = DevConfig.load(this)
        config?.let { redactor = Redactor(it.secretsForRedaction()) }
        val exo = buildPlayer(config)
        player = exo
        setContent { DebugScreen(lines, exo) }

        if (config == null) {
            log("No dev config. Push it with scripts/push_dev_config.sh (debug builds only).")
            return
        }
        log("Config loaded: $config")
        if (config.mode == "keepalive") {
            Thread({ runKeepAlive(config) }, "keepalive").start()
        } else if (config.mode == "vod-catalog") {
            Thread({ runVodCatalog(config) }, "vod-catalog").start()
        } else if (config.mode == "vod-movie" || config.mode == "vod-episode") {
            seekCheckPending = true
            Thread({ runVodPlayback(config, exo, episode = config.mode == "vod-episode") }, "vod-playback").start()
        } else {
            Thread({ runPortalFlow(config, exo) }, "portal-flow").start()
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    // ---- logging ---------------------------------------------------------------------

    /**
     * Run log in app-private storage (files/debug-log.txt), recreated at each launch. logcat's
     * 256 KB ring buffer on the Fire TV overwrote a 9-minute run (Step 4g), so the redacted lines
     * are also kept here. Read with: adb shell run-as <pkg> cat files/debug-log.txt
     */
    private val runLog: java.io.File by lazy { java.io.File(filesDir, "debug-log.txt").also { it.writeText("") } }

    /** Every line is redacted before it reaches logcat, the screen or the run log file. */
    private fun log(message: String) {
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        val safe = redactor.redact("[%.1fs] %s".format(t, message))
        Log.i(TAG, safe)
        synchronized(runLog) { runLog.appendText(safe + "\n") }
        runOnUiThread {
            lines += safe
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
    }

    // ---- portal flow -----------------------------------------------------------------

    private fun runPortalFlow(config: DevConfig, exo: ExoPlayer) {
        // Same safety rails as the Mac probe: at most 6 portal requests, >= 2 s apart, no retries.
        val budget = RequestBudget(maxRequests = 6)
        val client = StalkerClient(config.portalUrl, config.mac, HttpProbe(budget, maxBodyBytes = 1_000_000), config.identity)

        fun step(label: String, r: StalkerResult): Boolean {
            val status = (r.outcome as? ProbeOutcome.Response)?.status?.toString()
                ?: "transport failure (${(r.outcome as ProbeOutcome.Failure).error::class.simpleName})"
            log("$label: $status")
            r.summary.forEach { log("  $it") }
            if (!r.ok) log("STOP at $label")
            return r.ok
        }

        try {
            if (!step("handshake", client.handshake())) return
            if (client.token == null) { log("STOP: no token"); return }
            val profile = client.profile()
            if (!step("get_profile", profile)) return
            if (profile.field("status") != "0") { log("STOP: profile status ${profile.field("status")}"); return }
            val (g, genreId) = client.findGenreId(config.genre)
            if (!step("get_genres", g) || genreId == null) { log("STOP: genre not found"); return }
            val (page, cmd, label) = client.channelInGenre(genreId, config.channel)
            if (!step("channels in genre", page) || cmd == null) { log("STOP: channel not found"); return }
            val (link, url) = client.createLink(cmd)
            if (!step("create_link ($label)", link) || url == null) { log("STOP: no stream URL"); return }

            log("Handing the stream URL to Media3 (stream UA: ${config.streamUserAgent})")
            runOnUiThread {
                exo.setMediaItem(MediaItem.fromUri(url))
                exo.prepare()
                exo.playWhenReady = true
            }
        } catch (e: Exception) {
            log("Portal flow error: ${redactor.redact(e)}")
        }
    }

    /**
     * Step 4e: VOD catalog check, no playback. Exactly the 6 requests of
     * ProbeRequests.vodCatalogPlan: handshake, get_profile, vod categories, vod page 1,
     * series categories, series page 1. Budget 6, >= 2 s apart, no retries. A missing series
     * module is logged and does not stop the run (it is the last step pair).
     */
    private fun runVodCatalog(config: DevConfig) {
        val budget = RequestBudget(maxRequests = 6)
        val client = StalkerClient(config.portalUrl, config.mac, HttpProbe(budget, maxBodyBytes = 1_000_000), config.identity)
        fun step(label: String, r: StalkerResult): Boolean {
            val status = (r.outcome as? ProbeOutcome.Response)?.status?.toString()
                ?: "transport failure (${(r.outcome as ProbeOutcome.Failure).error::class.simpleName})"
            log("$label: $status")
            r.summary.forEach { log("  $it") }
            return r.ok
        }
        try {
            log("Step 4e VOD catalog: 6 requests planned, no playback")
            if (!step("handshake", client.handshake()) || client.token == null) { log("STOP: no token"); return }
            val profile = client.profile()
            if (!step("get_profile", profile) || profile.field("status") != "0") { log("STOP: profile not authorized"); return }
            if (!step("vod get_categories", client.vodCategories("vod").first)) { log("STOP at vod categories"); return }
            if (!step("vod get_ordered_list (all, page 1)", client.vodPage("vod"))) { log("STOP at vod list"); return }
            val (seriesCats, _) = client.vodCategories("series")
            if (!step("series get_categories", seriesCats)) {
                log("series module: not available (shows are probably inside type=vod)"); log("Step 4e done"); return
            }
            step("series get_ordered_list (all, page 1)", client.vodPage("series"))
            log("Step 4e done")
        } catch (e: Exception) {
            log("VOD catalog error: ${redactor.redact(e)}")
        }
    }

    /**
     * Step 4f: pick one movie (or one episode) and play it, with a seek check.
     * Portal requests: movie 5 (handshake, profile, vod categories, category page 1,
     * create_link); episode 8 (adds seasons, episodes and the episode's files). Budget sized exactly, >= 2 s apart,
     * no retries. Stream requests are the player's own and are logged by the interceptor.
     */
    private fun runVodPlayback(config: DevConfig, exo: ExoPlayer, episode: Boolean) {
        val budget = RequestBudget(maxRequests = if (episode) 8 else 5)
        val client = StalkerClient(config.portalUrl, config.mac, HttpProbe(budget, maxBodyBytes = 1_000_000), config.identity)
        fun step(label: String, r: StalkerResult): Boolean {
            val status = (r.outcome as? ProbeOutcome.Response)?.status?.toString()
                ?: "transport failure (${(r.outcome as ProbeOutcome.Failure).error::class.simpleName})"
            log("$label: $status")
            r.summary.forEach { log("  $it") }
            return r.ok
        }
        try {
            log("Step 4f ${if (episode) "episode" else "movie"}: ${budget.maxRequests} portal requests planned")
            if (!step("handshake", client.handshake()) || client.token == null) { log("STOP: no token"); return }
            val profile = client.profile()
            if (!step("get_profile", profile) || profile.field("status") != "0") { log("STOP: profile not authorized"); return }
            val (catsResult, cats) = client.vodCategories("vod")
            if (!step("vod get_categories", catsResult)) { log("STOP at categories"); return }
            val cat = StalkerClient.pickCategory(cats, config.vodCategory)
                ?: run { log("STOP: no VOD category matching '${config.vodCategory}'"); return }
            log("chosen category: '${cat.second}'")
            val (pageResult, items) = client.vodPageItems("vod", cat.first)
            if (!step("vod get_ordered_list ('${cat.second}', page 1)", pageResult)) { log("STOP at category page"); return }

            val url: String? = if (!episode) {
                val movie = items.firstOrNull { !it.isSeries && it.cmd != null }
                    ?: run { log("STOP: no movie with a cmd on page 1"); return }
                log("chosen movie: ${movie.name}")
                val (link, u) = client.vodCreateLink(movie.cmd!!)
                if (!step("vod create_link (movie)", link)) { log("STOP at create_link"); return }
                u
            } else {
                val show = items.firstOrNull { it.isSeries } ?: run { log("STOP: no show on page 1"); return }
                log("chosen show: ${show.name}")
                val (seasonsResult, seasons) = client.vodChildren(show.id)
                if (!step("vod seasons", seasonsResult)) { log("STOP at seasons"); return }
                val season = seasons.firstOrNull { it.isSeason } ?: seasons.firstOrNull()
                if (season != null) {
                    log("shape: navigation; chosen season: ${season.name}")
                    val (epsResult, eps) = client.vodChildren(show.id, season.id)
                    if (!step("vod episodes", epsResult)) { log("STOP at episodes"); return }
                    val ep = eps.firstOrNull { it.isEpisode } ?: eps.firstOrNull()
                        ?: run { log("STOP: no episodes"); return }
                    log("chosen episode: ${ep.name}")
                    // The episode's own cmd, else an inline series_files cmd, else the file level.
                    val fileCmd: String = ep.playableCmd ?: run {
                        val (filesResult, files) = client.vodChildren(show.id, season.id, ep.id)
                        if (!step("vod files of the episode", filesResult)) { log("STOP at files"); return }
                        val file = files.firstOrNull { it.playableCmd != null }
                            ?: run { log("STOP: no file with a cmd"); return }
                        log("chosen file: ${file.name}")
                        file.playableCmd!!
                    }
                    val (link, u) = client.vodCreateLink(fileCmd)
                    if (!step("vod create_link (episode file)", link)) { log("STOP at create_link"); return }
                    u
                } else if (show.episodeNumbers.isNotEmpty() && show.cmd != null) {
                    log("shape: episode numbers on the show; using episode ${show.episodeNumbers.first()}")
                    val (link, u) = client.vodCreateLink(show.cmd!!, show.episodeNumbers.first())
                    if (!step("vod create_link (episode number)", link)) { log("STOP at create_link"); return }
                    u
                } else { log("STOP: show has neither seasons nor episode numbers"); return }
            }
            if (url == null) { log("STOP: no playable URL"); return }
            log("Handing the VOD URL to Media3 (stream UA: ${config.streamUserAgent})")
            runOnUiThread { exo.setMediaItem(MediaItem.fromUri(url)); exo.prepare(); exo.playWhenReady = true }
        } catch (e: Exception) {
            log("VOD playback error: ${redactor.redact(e)}")
        }
    }

    /**
     * Step 4g keep-alive test, no playback, at most 10 portal requests over about 8.5 minutes
     * (ProbeRequests.keepAlivePlan):
     *   handshake, profile; watchdog at 0/120/240 s; genres check (~250 s);
     *   240 s idle without watchdog; genres check;
     *   only if that check reports "Authorization failed": handshake, profile, genres.
     */
    private fun runKeepAlive(config: DevConfig) {
        val budget = RequestBudget(maxRequests = 10)
        val client = StalkerClient(config.portalUrl, config.mac, HttpProbe(budget, maxBodyBytes = 1_000_000), config.identity)
        fun step(label: String, r: StalkerResult): Boolean {
            val status = (r.outcome as? ProbeOutcome.Response)?.status?.toString()
                ?: "transport failure (${(r.outcome as ProbeOutcome.Failure).error::class.simpleName})"
            log("$label: $status${if (r.authFailed) " (AUTH FAILED)" else ""}")
            r.summary.forEach { log("  $it") }
            return r.ok
        }
        try {
            log("Step 4g keep-alive: up to 10 portal requests, ~8.5 min, no playback")
            if (!step("handshake", client.handshake()) || client.token == null) { log("STOP: no token"); return }
            val profile = client.profile()
            if (!step("get_profile", profile) || profile.field("status") != "0") { log("STOP: profile not authorized"); return }
            log("profile timeslot (pvr.stalker's watchdog interval): ${profile.field("timeslot") ?: "-"}")

            for (i in 0 until 3) {
                if (i > 0) { log("waiting 120 s until the next watchdog"); Thread.sleep(120_000) }
                if (!step("watchdog #${i + 1}", client.watchdog())) { log("STOP: watchdog failed"); return }
            }
            Thread.sleep(10_000)
            val withKeepAlive = client.genres()
            step("check after ~250 s WITH keep-alive (get_genres)", withKeepAlive)
            log("RESULT A: token ${if (withKeepAlive.ok) "VALID" else if (withKeepAlive.authFailed) "EXPIRED" else "check failed"} after ~250 s with keep-alive")

            log("idle 240 s WITHOUT watchdog (longer than watchdog_timeout 180 s)")
            Thread.sleep(240_000)
            val idle = client.genres()
            step("check after 240 s idle (get_genres)", idle)
            when {
                idle.ok -> log("RESULT B: token still VALID after 240 s idle; re-auth path not exercised live (unit-tested)")
                idle.authFailed -> {
                    log("RESULT B: token EXPIRED after 240 s idle; re-authenticating once")
                    val (hs, prof) = client.reauthenticate()
                    step("re-auth handshake", hs)
                    prof?.let { step("re-auth get_profile", it) }
                    if (prof?.ok == true && prof.field("status") == "0") {
                        val after = client.genres()
                        step("check after re-auth (get_genres)", after)
                        log("RESULT C: re-auth ${if (after.ok) "WORKED" else "did not restore access"}")
                    } else log("RESULT C: re-auth failed")
                }
                else -> log("RESULT B: check failed for another reason (see status above)")
            }
            log("Step 4g done")
        } catch (e: Exception) {
            log("keep-alive error: ${redactor.redact(e)}")
        }
    }

    /** VOD seek check: 10 s after first READY, seek to the middle (max 5 min in) and time the resume. */
    private fun onPlayerReady(exo: ExoPlayer) {
        if (seekStartedAt > 0) {
            log("seek check: resumed after ${SystemClock.elapsedRealtime() - seekStartedAt} ms at ${exo.currentPosition / 1000}s")
            seekStartedAt = 0
            return
        }
        if (!seekCheckPending) return
        seekCheckPending = false
        mainHandler.postDelayed({
            val dur = exo.duration
            log("vod duration: ${if (dur > 0) "${dur / 1000}s" else "unknown"}; seekable: ${exo.isCurrentMediaItemSeekable}; position ${exo.currentPosition / 1000}s")
            if (dur > 0 && exo.isCurrentMediaItemSeekable) {
                val target = minOf(dur / 2, 300_000L)
                seekStartedAt = SystemClock.elapsedRealtime()
                log("seek check: seeking to ${target / 1000}s")
                exo.seekTo(target)
            } else {
                log("seek check: skipped (not seekable or duration unknown)")
            }
        }, 10_000)
    }

    // ---- player ------------------------------------------------------------------------

    private fun buildPlayer(config: DevConfig?): ExoPlayer {
        var lastHost: String? = null
        // Network interceptor: sees every HTTP exchange the player makes, redirect hops included.
        val streamClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                val changed = lastHost?.let { if (it.equals(host, true)) "same host" else "new host" } ?: "first host"
                lastHost = host
                val resp = try { chain.proceed(req) } catch (e: IOException) {
                    log("stream ${req.method} ${req.url} -> FAILED ${e::class.simpleName} ($changed)")
                    throw e
                }
                log("stream ${req.method} ${req.url} -> ${resp.code} ${resp.header("Content-Type") ?: "-"} ($changed)")
                resp
            }
            .build()

        val userAgent = when (config?.streamUserAgent) {
            "mag" -> StalkerIdentity.MAG.userAgent
            "honest" -> ClientIdentity.HONEST_USER_AGENT
            else -> "AndroidXMedia3/${MediaLibraryInfo.VERSION}"   // the player's own identity
        }
        val dataSource = OkHttpDataSource.Factory(streamClient).setUserAgent(userAgent)

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .build()
            .also { it.addAnalyticsListener(Diagnostics()) }
    }

    /** Redacted player diagnostics. */
    private inner class Diagnostics : AnalyticsListener {
        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            val name = when (state) {
                Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> state.toString()
            }
            log("player state: $name")
            if (state == Player.STATE_READY) player?.let { onPlayerReady(it) }
        }

        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
            log("FIRST FRAME rendered")
        }

        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
            log("video: ${format.sampleMimeType} ${format.codecs ?: ""} ${format.width}x${format.height} ${format.frameRate}fps ${format.bitrate}bps")
        }

        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
            log("audio: ${format.sampleMimeType} ${format.codecs ?: ""} ${format.channelCount}ch ${format.sampleRate}Hz")
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData,
            error: IOException, wasCanceled: Boolean,
        ) {
            log("load error: ${describe(error)}")
        }

        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            log("PLAYER ERROR ${error.errorCodeName}: ${describe(error.cause)}")
        }
    }

    /** Error class plus HTTP code when available; messages are redacted, URIs never printed. */
    private fun describe(t: Throwable?): String = when (t) {
        null -> "-"
        is HttpDataSource.InvalidResponseCodeException -> "HTTP ${t.responseCode} (${t.responseMessage ?: ""})"
        else -> "${t::class.simpleName}: ${redactor.redact(t.message ?: "")}" + (t.cause?.let { " <- ${describe(it)}" } ?: "")
    }

    companion object {
        const val TAG = "FTVIPTV"
        private const val MAX_LINES = 18
    }
}

@Composable
private fun DebugScreen(lines: List<String>, player: ExoPlayer) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .background(Color(0xB0000000))
                        .padding(12.dp),
                ) {
                    Text("Debug player (Phase 3) - redacted log", fontSize = 14.sp, color = Color(0xFF9AD0FF))
                    lines.forEach { Text(it, fontSize = 11.sp, color = Color.White, maxLines = 2) }
                }
            }
        }
    }
}
