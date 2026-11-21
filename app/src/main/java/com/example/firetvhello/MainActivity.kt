package com.example.firetvhello

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
import com.example.firetviptv.provider.ClientIdentity
import com.example.firetviptv.provider.HttpProbe
import com.example.firetviptv.provider.ProbeOutcome
import com.example.firetviptv.provider.Redactor
import com.example.firetviptv.provider.RequestBudget
import com.example.firetviptv.provider.StalkerClient
import com.example.firetviptv.provider.StalkerIdentity
import com.example.firetviptv.provider.StalkerResult
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
        Thread({ runPortalFlow(config, exo) }, "portal-flow").start()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    // ---- logging ---------------------------------------------------------------------

    /** Every line is redacted before it reaches logcat or the screen. */
    private fun log(message: String) {
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        val safe = redactor.redact("[%.1fs] %s".format(t, message))
        Log.i(TAG, safe)
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
