package io.github.ravindren_sm.openstream.data

import okhttp3.Interceptor

/**
 * Hook through which debug-only code can observe portal traffic (CLAUDE.md rule 13).
 *
 * Main code never fills [interceptors] itself. The debug build's Application class sets
 * it at start-up, so release builds keep the empty list and contain no logging code.
 * Interceptors here must never change requests or responses; they only observe them.
 */
object PortalTransport {
    @Volatile
    var interceptors: List<Interceptor> = emptyList()
}
