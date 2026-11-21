package com.example.firetviptv.provider

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * The only place in the project that sends HTTP requests to the provider.
 *
 * Guarantees, enforced here rather than by callers:
 * - Every request first takes a slot from [budget] (count cap and pacing).
 * - No retries: OkHttp's connection-failure retry is off, and a failure is returned
 *   to the caller as [ProbeOutcome.Failure], never retried.
 * - No redirects followed: a redirect is recorded (Location shape) but not visited,
 *   so a request cannot wander to another host.
 * - No cookie storage, no cache.
 * - TLS: OkHttp defaults, which validate the certificate chain and hostname against
 *   the JDK trust store. There is no code path that relaxes this (hard rule 4).
 * - At most [maxBodyBytes] of a response body are read.
 *
 * Results hold raw data; callers print them only through [ProbeOutcome.describe].
 */
class HttpProbe internal constructor(
    private val budget: RequestBudget,
    private val maxBodyBytes: Int,
    private val client: OkHttpClient,
) {
    /** Public entry point: always the locked-down default client (OkHttp stays internal). */
    constructor(budget: RequestBudget, maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES) :
        this(budget, maxBodyBytes, defaultClient())

    fun send(request: ProbeRequest): ProbeOutcome {
        budget.acquire()
        val builder = Request.Builder().url(request.url).method(request.method, null)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.byteStream()?.use { it.readNBytes(maxBodyBytes) } ?: ByteArray(0)
                val tls = response.handshake?.let { hs ->
                    val leaf = hs.peerCertificates.firstOrNull() as? X509Certificate
                    TlsInfo(
                        tlsVersion = hs.tlsVersion.javaName,
                        cipherSuite = hs.cipherSuite.javaName,
                        subject = leaf?.subjectX500Principal?.name,
                        issuer = leaf?.issuerX500Principal?.name,
                        notAfter = leaf?.notAfter?.toInstant()?.toString(),
                    )
                }
                ProbeOutcome.Response(
                    request = request,
                    status = response.code,
                    headers = response.headers.map { it.first to it.second },
                    bodyPrefix = body,
                    tls = tls,
                )
            }
        } catch (e: IOException) {
            ProbeOutcome.Failure(request, e)
        }
    }

    companion object {
        const val DEFAULT_MAX_BODY_BYTES = 4096

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

data class TlsInfo(
    val tlsVersion: String,
    val cipherSuite: String,
    val subject: String?,
    val issuer: String?,
    val notAfter: String?,
)

/** Result of one request. [describe] is the only sanctioned way to print it. */
sealed class ProbeOutcome {
    abstract val request: ProbeRequest

    class Response(
        override val request: ProbeRequest,
        val status: Int,
        val headers: List<Pair<String, String>>,
        val bodyPrefix: ByteArray,
        val tls: TlsInfo?,
    ) : ProbeOutcome() {
        fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    class Failure(override val request: ProbeRequest, val error: IOException) : ProbeOutcome()

    /**
     * Redacted, human-readable summary; [previewChars] caps the body preview, and
     * [includeBody] = false omits it entirely (portal API calls: summaries only).
     */
    fun describe(redactor: Redactor, previewChars: Int = BODY_PREVIEW_CHARS, includeBody: Boolean = true): String = redactor.redact(buildString {
        appendLine("request: ${request.method} ${request.url}")
        when (val o = this@ProbeOutcome) {
            is Response -> {
                appendLine("result: HTTP ${o.status}")
                SHOWN_HEADERS.forEach { h -> o.header(h)?.let { appendLine("  $h: $it") } }
                if (o.header("Set-Cookie") != null) appendLine("  Set-Cookie: present (value not shown)")
                o.tls?.let { t ->
                    appendLine("tls: ${t.tlsVersion}, ${t.cipherSuite}")
                    appendLine("  certificate subject: ${t.subject}")
                    appendLine("  certificate issuer: ${t.issuer}")
                    appendLine("  certificate valid until: ${t.notAfter}")
                } ?: appendLine("tls: none (plain HTTP)")
                val text = String(o.bodyPrefix, Charsets.UTF_8)
                    .replace(Regex("\\s+"), " ").trim().take(previewChars)
                appendLine("body: first ${o.bodyPrefix.size} bytes read")
                if (includeBody) appendLine("  preview: ${text.ifEmpty { "(empty)" }}")
            }
            is Failure -> {
                appendLine("result: FAILED, ${o.error::class.simpleName}")
                appendLine("  detail: ${redactor.redact(o.error)}")
            }
        }
    }).trimEnd()

    companion object {
        private val SHOWN_HEADERS = listOf("Server", "Content-Type", "Content-Length", "Location", "X-Powered-By", "WWW-Authenticate")
        private const val BODY_PREVIEW_CHARS = 300
    }
}
