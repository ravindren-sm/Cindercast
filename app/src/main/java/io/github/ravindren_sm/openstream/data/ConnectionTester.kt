package io.github.ravindren_sm.openstream.data

import android.os.SystemClock
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity

/** Outcome of "Test connection" on the setup screen. */
sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    /** [expiry] is the account's end date (date part only), when the portal reports one. */
    class Success(val expiry: String?, val elapsedMillis: Long) : TestResult
    /** [message] is fixed, user-facing text: no addresses, MACs or tokens. */
    class Failure(val message: String) : TestResult
}

/**
 * Runs the portal's own start sequence (handshake, then profile) once, to tell the user whether
 * the address and MAC work. Two requests, paced, no retries. Blocking: call off the main thread.
 * Uses the honest client identity (CLAUDE.md, PHASE2 plan section 9).
 *
 * The caller should close other apps that use the same MAC first: a portal normally allows one
 * active session per MAC (CLAUDE.md rule 12).
 */
object ConnectionTester {

    fun run(settings: PortalSettings): TestResult {
        val started = SystemClock.elapsedRealtime()
        return try {
            val client = StalkerClient(settings.portalUrl, settings.mac, HttpProbe(RequestPacer(), 1_000_000), StalkerIdentity.HONEST)
            val session = SessionManager(client)
            if (session.start() == SessionManager.State.READY) {
                TestResult.Success(session.accountExpiry?.take(10), SystemClock.elapsedRealtime() - started)
            } else {
                TestResult.Failure(friendly(session.failure))
            }
        } catch (e: Exception) {
            // The exception class name is safe; its message is not printed (it could hold a URL).
            TestResult.Failure("Something went wrong while testing (${e::class.simpleName}).")
        }
    }

    /** Maps the session manager's fixed failure text to wording for the screen. Statuses are not secrets. */
    internal fun friendly(failure: String?): String = when {
        failure == null -> "The portal could not be reached."
        failure.contains("no response") ->
            "Could not reach the portal. Check the address and that this TV is online."
        failure.startsWith("account not authorized") ->
            "The portal says this account is not authorized (${failure.substringAfter('(').trimEnd(')')}). " +
                "Confirm the MAC address is registered with your provider."
        failure.contains("(403)") || failure.contains("(401)") ->
            "The portal refused the connection (HTTP ${failure.substringAfter('(').substringBefore(')')}). " +
                "Confirm this MAC address is registered with your provider, and close other apps that use it."
        else -> "The portal answered in an unexpected way ($failure)."
    }
}
