package io.github.ravindren_sm.openstream.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** What the app shows about the portal connection. Messages are fixed text: no addresses, MACs or tokens. */
sealed interface SessionStatus {
    /** No portal configured, or the app is in the background. */
    data object Idle : SessionStatus
    data object Connecting : SessionStatus
    class Ready(val expiry: String?) : SessionStatus
    /** The last keep-alive got no usable answer; the session is retried at the next interval. */
    data object Degraded : SessionStatus
    class Failed(val message: String) : SessionStatus
}

/**
 * The app's one portal session: connects when the app is in the foreground and a portal is
 * configured, then sends the watchdog keep-alive every [SessionManager.WATCHDOG_INTERVAL_MILLIS]
 * (inside this portal's 180 s watchdog timeout). In the background it stops entirely, so a TV left
 * on the home screen does not hold the MAC's single session or poll the portal.
 *
 * All portal work runs on one background thread, so calls are serialized (the portal expects one
 * request at a time per device). [status] is Compose state and safe to read from the UI.
 * Nothing here is logged.
 */
class PortalSession(
    private val newManager: (PortalSettings) -> SessionManager = { defaultManager(it, PortalTransport.interceptors) },
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "portal-session").apply { isDaemon = true } },
    private val intervalMillis: Long = SessionManager.WATCHDOG_INTERVAL_MILLIS,
) {
    var status: SessionStatus by mutableStateOf(SessionStatus.Idle)
        private set

    // Touched only on [executor]'s thread.
    private var settings: PortalSettings? = null
    private var foreground = false
    private var manager: SessionManager? = null
    private var watchdog: Future<*>? = null

    /** Sets (or clears, with null) the portal to use. Replaces any session for a previous portal. */
    fun configure(settings: PortalSettings?) = executor.execute {
        this.settings = settings
        restart()
    }

    fun onForeground() = executor.execute { foreground = true; restart() }

    fun onBackground() = executor.execute { foreground = false; restart() }

    /** Stops the background thread for good (tests, process end). */
    fun shutdown() { executor.shutdownNow() }

    private fun restart() {
        watchdog?.cancel(false); watchdog = null
        manager = null
        val s = settings
        if (s == null || !foreground) { status = SessionStatus.Idle; return }
        status = SessionStatus.Connecting
        val m = try { newManager(s) } catch (e: Exception) { status = SessionStatus.Failed("Could not start the portal session."); return }
        manager = m
        if (m.start() == SessionManager.State.READY) {
            status = SessionStatus.Ready(m.accountExpiry?.take(10))
            watchdog = executor.scheduleWithFixedDelay({ tick(m) }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
        } else {
            status = SessionStatus.Failed(ConnectionTester.friendly(m.failure))
        }
    }

    private fun tick(m: SessionManager) {
        if (manager !== m) return
        val result = try { m.keepAlive() } catch (e: Exception) { null }
        status = when {
            m.state == SessionManager.State.FAILED -> SessionStatus.Failed(ConnectionTester.friendly(m.failure))
            result?.ok == true -> SessionStatus.Ready(m.accountExpiry?.take(10))
            else -> SessionStatus.Degraded
        }
    }

    companion object {
        private fun defaultManager(s: PortalSettings, interceptors: List<okhttp3.Interceptor>) =
            SessionManager(StalkerClient(s.portalUrl, s.mac, HttpProbe(RequestPacer(), 1_000_000, interceptors), StalkerIdentity.HONEST))
    }
}
