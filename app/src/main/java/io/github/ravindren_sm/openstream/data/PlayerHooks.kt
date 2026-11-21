package io.github.ravindren_sm.openstream.data

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import okhttp3.Interceptor

/**
 * Hook through which debug-only code observes playback (CLAUDE.md rule 13), like [PortalTransport]
 * does for portal calls. Main code never fills it; the debug build's Application class does, so
 * release builds keep the empty defaults and contain no playback logging.
 *
 * Observers must only observe: no changing requests, no printing stream URLs.
 */
@OptIn(UnstableApi::class)
object PlayerHooks {
    /** Network interceptors added to the player's HTTP client (sees every stream request, redirect hops included). */
    @Volatile
    var networkInterceptors: List<Interceptor> = emptyList()

    /** Called once per player to get fresh listeners, so per-session metrics start at zero. */
    @Volatile
    var newAnalyticsListeners: () -> List<AnalyticsListener> = { emptyList() }
}
