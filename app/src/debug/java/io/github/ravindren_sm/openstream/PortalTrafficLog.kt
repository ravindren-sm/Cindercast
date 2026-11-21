package io.github.ravindren_sm.openstream

import android.util.Log
import io.github.ravindren_sm.openstream.provider.Redactor
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

/**
 * Debug-only: writes one redacted line per portal call to logcat (CLAUDE.md rule 13).
 *
 * Line shape, with no bodies and no query values except type and action:
 *   portal GET https://<PORTAL_HOST>/stalker_portal/server/load.php type=stb action=handshake -> HTTP 200 in 412 ms
 *   portal GET https://<PORTAL_HOST>/stalker_portal/server/load.php type=watchdog action=get_events -> FAILED SocketTimeoutException in 10003 ms
 *
 * The host, the MAC (from the Cookie header) and the bearer token (from Authorization)
 * are known per request, so they are registered as secrets for [Redactor]. Every line is
 * passed through that redactor again before it is written, as a last guard.
 *
 * [sink] receives finished lines. Logcat is the default; tests pass a list.
 * This file is in src/debug only, so no release build contains it.
 */
class PortalTrafficLog(
    private val sink: (String) -> Unit = { Log.i(TAG, it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val redactor = redactorFor(request)
        val shownUrl = shapeOf(request.url)
        val typeAction = typeAction(request)
        val started = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            // Only the exception's class name is logged: its message can hold a URL.
            val elapsed = millisSince(started)
            sink(redactor.redact("portal ${request.method} $shownUrl $typeAction -> FAILED ${t::class.simpleName} in $elapsed ms"))
            throw t
        }
        val elapsed = millisSince(started)
        sink(redactor.redact("portal ${request.method} $shownUrl $typeAction -> HTTP ${response.code} in $elapsed ms"))
        return response
    }

    companion object {
        const val TAG = "OpenStream.portal"

        private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

        /** The protocol's own verbs only. Anything else (or a missing value) shows as "?". */
        private val SAFE_ACTION = Regex("[a-z_]{1,32}")

        /** Path segments that are protocol words, not secrets. Any other segment shows as "<p>". */
        private val SAFE_SEGMENTS = setOf(
            "stalker_portal", "server", "load.php", "portal.php", "c", "player_api.php", "get.php",
        )

        /**
         * The request URL with the host replaced by <HOST>, no port, no query, and unknown path
         * segments replaced by <p>. Built here rather than by Redactor's URL rule, which mangles
         * some stalker paths. The full line is still passed through the redactor afterwards.
         */
        internal fun shapeOf(url: okhttp3.HttpUrl): String {
            val path = url.encodedPathSegments
                .filter { it.isNotEmpty() }
                .joinToString("") { "/" + (if (it.lowercase() in SAFE_SEGMENTS) it else "<p>") }
            return "${url.scheme}://<HOST>$path"
        }

        internal fun typeAction(request: Request): String {
            fun part(name: String): String {
                val value = request.url.queryParameter(name) ?: return "$name=?"
                return if (SAFE_ACTION.matches(value)) "$name=$value" else "$name=?"
            }
            return "${part("type")} ${part("action")}"
        }

        /** Builds a redactor for this request's own host, MAC and bearer token. */
        internal fun redactorFor(request: Request): Redactor {
            // The bare host, so the scheme stays visible in the shown URL ("https://<PORTAL_HOST>/...").
            val secrets = linkedMapOf("PORTAL_HOST" to request.url.host)
            cookieValue(request, "mac")?.let { secrets["MAC"] = it }
            request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { secrets["TOKEN"] = it }
            return Redactor(secrets)
        }

        private fun cookieValue(request: Request, name: String): String? =
            request.header("Cookie")
                ?.split(';')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("$name=") }
                ?.substringAfter('=')
                ?.let { try { URLDecoder.decode(it, "UTF-8") } catch (_: IOException) { null } } // String overload: Android 11 lacks the Charset one
                ?.takeIf { it.isNotEmpty() }
    }
}
