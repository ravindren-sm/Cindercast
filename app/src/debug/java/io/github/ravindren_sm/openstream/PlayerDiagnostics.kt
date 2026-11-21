package io.github.ravindren_sm.openstream

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Debug-only playback metrics (CLAUDE.md rule 13, M3 "time to first frame, rebuffering").
 * Pure logic with an injectable clock, so it is unit-tested. Each call returns the log line to
 * write, or null. No stream address ever enters this class.
 */
class PlayerMetrics(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var startedAt: Long? = null
    private var everReady = false
    private var bufferingSince: Long? = null
    private var firstFrameMillis: Long? = null
    var rebuffers = 0; private set
    var rebufferMillis = 0L; private set
    var errors = 0; private set
    var droppedFrames = 0; private set

    /** A Player.STATE_* change. */
    fun onState(state: Int): String? {
        val now = nowMillis()
        return when (state) {
            Player.STATE_BUFFERING -> when {
                startedAt == null -> { startedAt = now; null }
                everReady && bufferingSince == null -> { bufferingSince = now; "rebuffering started" }
                else -> null
            }
            // Idle means a stop or an error: the next start (a retry, a new channel) is timed afresh.
            Player.STATE_IDLE -> { startedAt = null; everReady = false; bufferingSince = null; firstFrameMillis = null; null }
            Player.STATE_READY -> when {
                !everReady -> { everReady = true; "ready after ${now - (startedAt ?: now)} ms" }
                bufferingSince != null -> {
                    val took = now - bufferingSince!!
                    bufferingSince = null; rebuffers++; rebufferMillis += took
                    "rebuffer #$rebuffers lasted $took ms"
                }
                else -> null
            }
            else -> null
        }
    }

    fun onFirstFrame(): String? {
        val start = startedAt ?: return null
        if (firstFrameMillis != null) return null
        val took = nowMillis() - start
        firstFrameMillis = took
        return "first frame after $took ms"
    }

    fun onDropped(count: Int) { droppedFrames += count }
    fun onError() { errors++ }

    fun summary(): String =
        "summary: first frame ${firstFrameMillis?.let { "$it ms" } ?: "never"}, rebuffers $rebuffers ($rebufferMillis ms), " +
            "dropped frames $droppedFrames, errors $errors"
}

/** Debug-only AnalyticsListener: state, metrics, codecs, errors. One instance per player. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerDiagnostics(
    private val sink: (String) -> Unit = { Log.i(TAG, it) },
    private val metrics: PlayerMetrics = PlayerMetrics(),
) : AnalyticsListener {

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val name = when (state) {
            Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> state.toString()
        }
        sink("player state: $name")
        metrics.onState(state)?.let(sink)
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        sink("playing: $isPlaying")
    }

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        metrics.onFirstFrame()?.let(sink)
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        metrics.onDropped(droppedFrames)
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
        sink("video: ${format.sampleMimeType} ${format.codecs ?: ""} ${format.width}x${format.height} ${format.frameRate}fps ${format.bitrate}bps")
    }

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
        sink("audio: ${format.sampleMimeType} ${format.codecs ?: ""} ${format.channelCount}ch ${format.sampleRate}Hz")
    }

    override fun onLoadError(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        sink("load error: ${describe(error)}")
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        metrics.onError()
        sink("PLAYER ERROR ${error.errorCodeName}: ${describe(error.cause)}")
    }

    override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
        sink(metrics.summary())
    }

    /** Exception class and HTTP status only. Messages are never logged: they can contain stream URLs. */
    private fun describe(t: Throwable?): String = when (t) {
        null -> "-"
        is HttpDataSource.InvalidResponseCodeException -> "HTTP ${t.responseCode}"
        else -> t::class.simpleName ?: "Throwable"
    }

    companion object { const val TAG = "OpenStream.player" }
}

/**
 * Debug-only network interceptor for the player's HTTP client: one line per stream request, with
 * no path and no query (stream paths can carry credentials). Hosts show as host#1, host#2 ... in
 * the order first seen, so a redirect to an edge server is visible without naming it.
 */
class StreamTrafficLog(private val sink: (String) -> Unit = { Log.i(PlayerDiagnostics.TAG, it) }) : Interceptor {
    private val hosts = LinkedHashMap<String, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val label = synchronized(hosts) { "host#" + hosts.getOrPut("${request.url.host}:${request.url.port}") { hosts.size + 1 } }
        val started = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            sink("stream ${request.method} $label -> FAILED ${t::class.simpleName} in ${(System.nanoTime() - started) / 1_000_000} ms")
            throw t
        }
        val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.matches(SAFE_TYPE) } ?: "-"
        sink("stream ${request.method} $label -> HTTP ${response.code} $type in ${(System.nanoTime() - started) / 1_000_000} ms")
        return response
    }

    private companion object { val SAFE_TYPE = Regex("[A-Za-z0-9.+/_-]{1,60}") }
}
