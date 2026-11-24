package io.github.ravindren_sm.openstream

import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

/** Debug playback metrics and the stream logger. Local servers only; nothing reaches the provider. */
class PlayerMetricsTest {
    private var t = 0L
    private val m = PlayerMetrics { t }

    @Test fun `time to ready and first frame are measured from the first buffering`() {
        t = 100; assertNull(m.onState(Player.STATE_BUFFERING))
        t = 900; assertEquals("ready after 800 ms", m.onState(Player.STATE_READY))
        t = 1_100; assertEquals("first frame after 1000 ms", m.onFirstFrame())
        t = 1_200; assertNull(m.onFirstFrame())            // reported once
    }

    @Test fun `a buffering after ready counts as a rebuffer with its duration`() {
        m.onState(Player.STATE_BUFFERING); t = 500; m.onState(Player.STATE_READY)
        t = 5_000; assertEquals("rebuffering started", m.onState(Player.STATE_BUFFERING))
        t = 5_600; assertEquals("rebuffer #1 lasted 600 ms", m.onState(Player.STATE_READY))
        t = 9_000; m.onState(Player.STATE_BUFFERING); t = 9_250; m.onState(Player.STATE_READY)
        assertEquals(2, m.rebuffers); assertEquals(850L, m.rebufferMillis)
    }

    @Test fun `after a stop or error a retry is timed from its own start, not the failed one`() {
        t = 0; m.onState(Player.STATE_BUFFERING)          // first attempt starts, then fails
        t = 500; m.onState(Player.STATE_IDLE)
        t = 13_000; m.onState(Player.STATE_BUFFERING)     // the user retries much later
        t = 13_800; assertEquals("ready after 800 ms", m.onState(Player.STATE_READY))
        t = 14_000; assertEquals("first frame after 1000 ms", m.onFirstFrame())
    }

    @Test fun `no first frame before playback started, and repeated READY is silent`() {
        assertNull(m.onFirstFrame())
        m.onState(Player.STATE_BUFFERING); m.onState(Player.STATE_READY)
        assertNull(m.onState(Player.STATE_READY))
    }

    @Test fun `the summary reports first frame, rebuffers, dropped frames and errors`() {
        m.onState(Player.STATE_BUFFERING); t = 400; m.onState(Player.STATE_READY); m.onFirstFrame()
        m.onDropped(3); m.onDropped(2); m.onError()
        assertEquals("summary: first frame 400 ms, rebuffers 0 (0 ms), dropped frames 5, errors 1", m.summary())
        assertTrue(PlayerMetrics { 0 }.summary().contains("first frame never"))
    }
}

class StreamTrafficLogTest {
    private lateinit var edge: MockWebServer
    private lateinit var origin: MockWebServer
    private val lines = CopyOnWriteArrayList<String>()
    private val client by lazy { OkHttpClient.Builder().addNetworkInterceptor(StreamTrafficLog { lines += it }).build() }

    @BeforeTest fun start() { edge = MockWebServer().apply { start() }; origin = MockWebServer().apply { start() } }
    @AfterTest fun stop() { edge.shutdown(); origin.shutdown() }

    @Test fun `a redirect to another server shows as host 1 then host 2, with no path or query`() {
        origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", edge.url("/segments/secret-seg.ts?token=SECRETQUERY")))
        edge.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp2t; charset=x").setBody("TS"))
        client.newCall(Request.Builder().url(origin.url("/live/secretchannel.m3u8?user=SECRETUSER")).build()).execute().close()

        assertEquals(2, lines.size)
        assertTrue(Regex("""stream GET host#1 -> HTTP 302 - in \d+ ms""").matches(lines[0]), lines[0])
        assertTrue(Regex("""stream GET host#2 -> HTTP 200 video/mp2t in \d+ ms""").matches(lines[1]), lines[1])
        val all = lines.joinToString("\n")
        listOf("localhost", origin.port.toString(), edge.port.toString(), "secretchannel", "secret-seg", "SECRETQUERY", "SECRETUSER", "token", "user=")
            .forEach { assertTrue(!all.contains(it), "leaked '$it' in: $all") }
    }

    @Test fun `a failing request logs a FAILED line with the class name only`() {
        origin.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        runCatching { client.newCall(Request.Builder().url(origin.url("/live/secretchannel.m3u8")).build()).execute().close() }
        val line = lines.last()
        assertTrue(Regex("""stream GET host#1 -> FAILED \w+ in \d+ ms""").matches(line), line)
        assertTrue(!line.contains("secretchannel") && !line.contains("localhost"), line)
    }
}
