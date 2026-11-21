package com.example.firetviptv.provider

/**
 * Keeps one portal session alive for the app (docs/PROTOCOL_NOTES.md, "Session keep-alive").
 *
 * - [start]: handshake, then get_profile; ready only if the profile status is 0.
 * - [call]: runs a portal call; if the portal says "Authorization failed." (expired session) it
 *   re-authenticates ONCE and runs the call once more. No loops, no retries on other failures.
 * - [keepAlive]: the watchdog request. The app calls it every [WATCHDOG_INTERVAL_MILLIS] while
 *   the session is in use (120 s: inside this portal's 180 s watchdog_timeout).
 * - Re-authentication attempts are spaced at least [MIN_REAUTH_GAP_MILLIS] apart, so a portal
 *   that keeps refusing is not hammered; during the gap the failed result is returned as is.
 *
 * Thread-safe: calls are serialized, because the token is shared state and the portal expects one
 * request at a time from one device.
 */
class SessionManager(
    private val client: StalkerClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    enum class State { NEW, READY, FAILED }

    @Volatile var state: State = State.NEW
        private set

    /** Why the session is not READY: a fixed, non-identifying message. */
    @Volatile var failure: String? = null
        private set

    private var lastReauthAt: Long? = null

    /** Handshake + profile. */
    @Synchronized
    fun start(): State {
        val hs = client.handshake()
        if (!hs.ok || client.token == null) return fail("handshake failed (${statusOf(hs)})")
        val profile = client.profile()
        if (!profile.ok) return fail("profile failed (${statusOf(profile)})")
        if (profile.field("status") != "0") return fail("account not authorized (status ${profile.field("status") ?: "?"})")
        failure = null
        state = State.READY
        return state
    }

    /**
     * Runs [block] with the shared client. [isAuthFailure] says whether the result is the
     * portal's expired-session reply; if so, re-authenticates once and runs [block] again.
     */
    @Synchronized
    fun <R> call(isAuthFailure: (R) -> Boolean, block: (StalkerClient) -> R): R {
        val first = block(client)
        if (!isAuthFailure(first)) return first
        if (!reauthenticate()) return first
        return block(client)
    }

    /** Convenience for calls that return a [StalkerResult]. */
    fun call(block: (StalkerClient) -> StalkerResult): StalkerResult = call({ it.authFailed }, block)

    /** The watchdog. Returns the final result (after one re-authentication if the session had expired). */
    fun keepAlive(): StalkerResult = call { it.watchdog() }

    @Synchronized
    private fun reauthenticate(): Boolean {
        val now = nowMillis()
        val last = lastReauthAt
        if (last != null && now - last < MIN_REAUTH_GAP_MILLIS) return false
        lastReauthAt = now
        val (hs, profile) = client.reauthenticate()
        val ok = hs.ok && profile?.ok == true && profile.field("status") == "0"
        if (ok) { state = State.READY; failure = null } else fail("re-authentication failed")
        return ok
    }

    private fun fail(message: String): State {
        failure = message
        state = State.FAILED
        return state
    }

    private fun statusOf(r: StalkerResult): String =
        (r.outcome as? ProbeOutcome.Response)?.status?.toString() ?: "no response"

    companion object {
        const val WATCHDOG_INTERVAL_MILLIS = 120_000L
        const val MIN_REAUTH_GAP_MILLIS = 30_000L
    }
}
