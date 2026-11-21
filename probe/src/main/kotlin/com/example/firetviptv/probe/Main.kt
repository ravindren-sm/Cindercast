package com.example.firetviptv.probe

import com.example.firetviptv.provider.HttpProbe
import com.example.firetviptv.provider.InsecureSecretsFileException
import com.example.firetviptv.provider.MissingSecretException
import com.example.firetviptv.provider.ProbeOutcome
import com.example.firetviptv.provider.ProbeRequest
import com.example.firetviptv.provider.ProbeRequests
import com.example.firetviptv.provider.ProbeStep
import com.example.firetviptv.provider.Redactor
import com.example.firetviptv.provider.RequestBudget
import com.example.firetviptv.provider.Secrets
import com.example.firetviptv.provider.SecretsLoader
import com.example.firetviptv.provider.StalkerClient
import com.example.firetviptv.provider.StalkerIdentity
import com.example.firetviptv.provider.StalkerResult
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/*
 * Phase 2 probe command line (docs/PHASE2_PROBE_PLAN.md).
 *
 *   ./gradlew :probe:run --args="list"
 *   ./gradlew :probe:run --args="secrets-check"
 *   ./gradlew :probe:run --args="<step> --dry-run"
 *   ./gradlew :probe:run --args="<step> --live"      (approved steps only)
 *   Step 4 (MAC) live runs also need --stbemu-idle; add --identity=mag only for the
 *   single MAG-header retry the user consented to (plan section 9).
 *
 * A dry run loads the secrets, builds the request(s) the step would send, and prints
 * them redacted. A live run sends them through HttpProbe (budget, no retries, no
 * redirects, TLS validation on) and only for steps listed in LIVE_APPROVED. Both
 * write the same redacted text to probe_output/<UTC timestamp>/ (gitignored).
 *
 * Every line printed or written goes through the Redactor, including error messages.
 */

private const val EXIT_USAGE = 64
private const val EXIT_CONFIG = 78
private const val EXIT_NOT_AVAILABLE = 2

/** Set from flags in main(). */
private var identity: StalkerIdentity = StalkerIdentity.HONEST
private var stbEmuIdle: Boolean = false

fun main(args: Array<String>) {
    val command = args.firstOrNull { !it.startsWith("--") }
    val dryRun = "--dry-run" in args
    stbEmuIdle = "--stbemu-idle" in args
    identity = when (args.firstOrNull { it.startsWith("--identity=") }?.substringAfter('=')) {
        null, "honest" -> StalkerIdentity.HONEST
        "mag" -> StalkerIdentity.MAG
        else -> fail(EXIT_USAGE, "--identity must be honest or mag")
    }

    when (command) {
        null, "help", "list" -> { printSteps(); return }
        "secrets-check" -> { secretsCheck(); return }
    }

    val step = ProbeStep.fromCliName(command!!)
        ?: fail(EXIT_USAGE, "Unknown step '$command'. Run with 'list' to see the steps.")

    val live = "--live" in args
    when {
        dryRun && live -> fail(EXIT_USAGE, "Choose one of --dry-run or --live.")
        dryRun -> dryRunStep(step)
        live -> liveStep(step)
        else -> fail(EXIT_USAGE, "Add --dry-run to preview, or --live to send (approved steps only).")
    }
}

/**
 * Steps the user has approved for live use, by chat approval date. A step not listed
 * here cannot send anything, whatever flags are passed.
 */
private val LIVE_APPROVED: Map<ProbeStep, String> = mapOf(
    ProbeStep.REACHABILITY_XTREAM to "2026-11-20",
    ProbeStep.DISCOVERY to "2026-11-20",
    ProbeStep.CAPTURE_PORTAL to "2026-11-20",   // runs DISCOVERY_PORTAL as its second part
    ProbeStep.PORTAL_HANDSHAKE to "2026-11-21", // Step 4a only; 4b-4d not approved
)

/** HTTP statuses that end Phase 2 until the user decides (plan section 10). */
private val STOP_STATUSES = setOf(403, 429)

private fun printSteps() {
    println("Steps in this build (dry-run only):")
    ProbeStep.entries.forEach { s ->
        println("  %-22s %s  needs: %s".format(s.cliName, s.planStep, s.needs.joinToString()))
    }
    println("Other commands: secrets-check, list")
}

/** Loads secrets; on failure prints a redaction-safe reason and exits. */
private fun loadSecrets(): Secrets = try {
    SecretsLoader.load()
} catch (e: InsecureSecretsFileException) {
    fail(EXIT_CONFIG, e.message ?: "Secrets file problem")
}

private fun secretsCheck() {
    val secrets = loadSecrets()
    println("Secrets file: found, permissions OK (owner read/write only).")
    println("Names present: ${secrets.names.joinToString().ifEmpty { "(none)" }}")
    if (secrets.namesTooShortToRedact.isNotEmpty()) {
        println("WARNING: too short to redact safely: ${secrets.namesTooShortToRedact.joinToString()}")
    }
    ProbeStep.entries.forEach { s ->
        val missing = s.needs.filterNot(secrets::has)
        println("  %-22s %s".format(s.cliName, if (missing.isEmpty()) "ready" else "missing: ${missing.joinToString()}"))
    }
}

private fun dryRunStep(step: ProbeStep) {
    val secrets = loadSecrets()
    val redactor = secrets.redactor()

    val tooShort = step.needs.filter { it in secrets.namesTooShortToRedact }
    if (tooShort.isNotEmpty()) {
        fail(EXIT_CONFIG, "Refusing: ${tooShort.joinToString()} too short to redact safely.")
    }

    val requests = try {
        if (step.usesMac) ProbeRequests.portalPlan(step, secrets, identity) else ProbeRequests.forStep(step, secrets)
    } catch (e: MissingSecretException) {
        fail(EXIT_CONFIG, e.message ?: "Missing secret ${e.secretName}")
    } catch (e: Exception) {
        fail(EXIT_CONFIG, "Could not build the request: ${redactor.redact(e)}")
    }

    val report = buildString {
        appendLine("DRY RUN: ${step.cliName} (${step.planStep}). Nothing was sent.")
        appendLine("Requests this step would send: ${requests.size} (budget per run: 10, >= 2 s apart, no retries)")
        requests.forEachIndexed { i, r ->
            appendLine("--- request ${i + 1}")
            appendLine(r.describe(redactor))
        }
    }
    // Belt and braces: the whole report passes through the redactor once more.
    val safe = redactor.redact(report)
    println(safe)
    val file = writeOutput("${step.cliName}-dry-run.txt", safe)
    println("Saved: $file")
}

/**
 * Sends a live step. Step 2 (reachability) logic: DNS lookup, then the HTTPS request;
 * the FALLBACK request is sent only if HTTPS produced no HTTP response at all.
 */
private fun liveStep(step: ProbeStep) {
    val approvedOn = LIVE_APPROVED[step]
        ?: fail(EXIT_NOT_AVAILABLE, "Step '${step.cliName}' is not approved for live use. It needs the user's approval in chat first.")
    val reachability = step == ProbeStep.REACHABILITY_XTREAM || step == ProbeStep.REACHABILITY_PORTAL
    if (!reachability && !step.usesMac && step != ProbeStep.DISCOVERY && step != ProbeStep.CAPTURE_PORTAL) {
        fail(EXIT_NOT_AVAILABLE, "Live mode is not implemented for '${step.cliName}'.")
    }
    if (step.usesMac && !stbEmuIdle) {
        fail(EXIT_USAGE, "Steps that use the MAC also need --stbemu-idle: confirm StbEmu is not playing on the TV (plan section 3).")
    }

    val secrets = loadSecrets()
    // Mutable: Step 3b stores a new secret mid-run, and from then on the redactor must know it.
    var redactor = secrets.redactor()
    val requests = try { ProbeRequests.forStep(step, secrets) } catch (e: Exception) {
        fail(EXIT_CONFIG, "Could not build the request: ${redactor.redact(e)}")
    }

    val report = StringBuilder()
    fun log(line: String) { val safe = redactor.redact(line); println(safe); report.appendLine(safe) }

    log("LIVE RUN: ${step.cliName} (${step.planStep}), approved by the user on $approvedOn.")
    if (step.usesMac) {
        portalStep(step, secrets, { redactor }, ::log)
        finish(step, report); return
    }
    if (step == ProbeStep.DISCOVERY) {
        val probe = com.example.firetviptv.provider.HttpProbe(
            com.example.firetviptv.provider.RequestBudget(maxRequests = requests.size)
        )
        runSequential(requests, probe, { redactor }, ::log, allowedRedirectHost = null)
        finish(step, report); return
    }
    if (step == ProbeStep.CAPTURE_PORTAL) {
        // One budget for all three Step 3b requests, so pacing holds across both parts.
        val probe = com.example.firetviptv.provider.HttpProbe(
            com.example.firetviptv.provider.RequestBudget(maxRequests = 3)
        )
        log("Planned: 1 capture request, then 2 anonymous requests to the captured portal host. No credentials, no MAC.")
        log("--- request 1 of 3 (capture)")
        val capture = probe.send(requests.single())
        log(capture.describe(redactor))
        val portalUrl = (capture as? com.example.firetviptv.provider.ProbeOutcome.Response)
            ?.takeIf { it.status in 300..399 }
            ?.let { ProbeRequests.portalUrlFromRedirect(it.request.url, it.header("Location")) }
        if (portalUrl == null) {
            log("STOP: no redirect to a Stalker portal path; nothing stored, remaining requests not sent.")
            finish(step, report); return
        }
        val secretsPath = SecretsLoader.defaultPath()
        SecretsLoader.put(secretsPath, "PORTAL_URL", portalUrl)
        val updated = SecretsLoader.load(secretsPath)
        redactor = updated.redactor()   // knows PORTAL_URL from here on
        val sameHost = java.net.URI(portalUrl).host.equals(java.net.URI(secrets.require("XTREAM_HOST").trim()).host, ignoreCase = true)
        log("stored: PORTAL_URL in the secrets file (value not shown). Same host as XTREAM_HOST: ${if (sameHost) "yes" else "no"}.")
        if (sameHost) {
            log("STOP: the redirect points back to the configured host; nothing new to probe.")
            finish(step, report); return
        }
        val portalHost = java.net.URI(portalUrl).host
        val v4 = try { java.net.InetAddress.getAllByName(portalHost).count { it is java.net.Inet4Address } } catch (_: Exception) { -1 }
        log(if (v4 < 0) "dns: portal host does not resolve" else "dns: portal host resolves ($v4 IPv4 address(es)); host is ${if (portalHost.all { it.isDigit() || it == '.' }) "a literal IP address" else "a name"}")
        val discovery = ProbeRequests.forStep(ProbeStep.DISCOVERY_PORTAL, updated)
        runSequential(discovery, probe, { redactor }, ::log, allowedRedirectHost = portalHost, firstIndex = 2, total = 3)
        finish(step, report); return
    }
    log("Planned: up to ${requests.size} request(s); fallback only if HTTPS gets no response. No credentials, no MAC.")

    // DNS: answered by the local resolver, not the provider. Only counts are reported.
    val host = java.net.URI(requests.first().url).host
    val addresses = try {
        java.net.InetAddress.getAllByName(host).toList()
    } catch (e: java.net.UnknownHostException) {
        log("dns: FAILED, host does not resolve. Stopping; nothing was sent.")
        finish(step, report); return
    }
    val v4 = addresses.count { it is java.net.Inet4Address }
    log("dns: resolves to ${addresses.size} address(es): $v4 IPv4, ${addresses.size - v4} IPv6")

    val probe = com.example.firetviptv.provider.HttpProbe(
        com.example.firetviptv.provider.RequestBudget(maxRequests = requests.size)
    )
    var httpsAnswered = false
    for ((i, request) in requests.withIndex()) {
        if (i > 0 && httpsAnswered) {
            log("--- request ${i + 1}: not sent (HTTPS answered, fallback not needed)")
            break
        }
        log("--- request ${i + 1}")
        val outcome = probe.send(request)
        log(outcome.describe(redactor))
        if (i == 0 && outcome is com.example.firetviptv.provider.ProbeOutcome.Response) httpsAnswered = true
    }
    log("--- summary")
    log(if (httpsAnswered) "HTTPS: the host answered over HTTPS (see tls lines above)." else "HTTPS: no HTTPS response; see the fallback result.")
    finish(step, report)
}

/**
 * Sends [requests] in order through [probe] (whose budget the caller sized). Stops at the
 * first transport failure (no retries), at a stop status (plan section 10), or, when
 * [allowedRedirectHost] is set, at a redirect to any other host (no redirect chasing).
 * Every line goes through [log], which redacts with the current [redactor].
 */
private fun runSequential(
    requests: List<com.example.firetviptv.provider.ProbeRequest>,
    probe: com.example.firetviptv.provider.HttpProbe,
    redactor: () -> Redactor,
    log: (String) -> Unit,
    allowedRedirectHost: String?,
    firstIndex: Int = 1,
    total: Int = requests.size,
) {
    log("Planned here: ${requests.size} request(s), in order, no credentials, no MAC. A failure or 403/429 stops the run.")
    for ((i, request) in requests.withIndex()) {
        log("--- request ${firstIndex + i} of $total")
        val outcome = probe.send(request)
        // A longer preview helps identify the server software; describe() redacts.
        log(outcome.describe(redactor(), previewChars = 1200))
        if (outcome is com.example.firetviptv.provider.ProbeOutcome.Failure) {
            log("STOP: transport failure ends the run (no retries). Remaining requests not sent.")
            return
        }
        val response = outcome as com.example.firetviptv.provider.ProbeOutcome.Response
        if (response.status in STOP_STATUSES) {
            log("STOP: HTTP ${response.status} is a Phase 2 stop condition. Remaining requests not sent.")
            return
        }
        if (allowedRedirectHost != null && response.status in 300..399) {
            val target = response.header("Location")?.let {
                try { java.net.URI(request.url).resolve(it.trim()).host } catch (_: Exception) { null }
            }
            if (target == null || !target.equals(allowedRedirectHost, ignoreCase = true)) {
                log("STOP: redirect to a different host; not followed, remaining requests not sent.")
                return
            }
            log("note: redirect stays on the portal host (not followed)")
        }
    }
    log("--- all ${requests.size} request(s) here sent")
}

/**
 * Step 4 runs against the portal API with the registered MAC. Every run starts with its own
 * handshake. Raw response bodies are never printed: only status lines and the client's
 * whitelisted summaries. Stops at the first failure, stop status, blocked/banned signal,
 * missing token, or non-zero profile status.
 */
private fun portalStep(step: ProbeStep, secrets: Secrets, redactor: () -> Redactor, log: (String) -> Unit) {
    val planned = ProbeRequests.forStep(step, secrets).size
    log("Identity: ${identity.name}${if (identity == StalkerIdentity.MAG) " (MAG headers, user-consented single retry)" else " (honest User-Agent)"}")
    log("Planned: up to $planned request(s); MAC sent as a cookie; raw bodies never printed. StbEmu confirmed idle by the user.")
    val budget = RequestBudget(maxRequests = planned)
    val apiProbe = HttpProbe(budget, maxBodyBytes = 1_000_000)
    val client = StalkerClient(secrets.require("PORTAL_URL"), secrets.require("EMU_MAC"), apiProbe, identity)

    var n = 0
    /** Logs one portal call; returns false when the run must stop. */
    fun report(label: String, r: StalkerResult): Boolean {
        n++
        log("--- request $n of $planned: $label")
        log(r.outcome.describe(redactor(), includeBody = false))
        r.summary.forEach { log("  $it") }
        val resp = r.outcome as? ProbeOutcome.Response
        when {
            resp == null -> { log("STOP: transport failure ends the run (no retries)."); return false }
            resp.status in STOP_STATUSES -> { log("STOP: HTTP ${resp.status} is a Phase 2 stop condition."); return false }
            blockSignal(r) != null -> { log("STOP: the portal signals '${blockSignal(r)}'. Check StbEmu on the TV now (plan section 8)."); return false }
            !r.ok -> { log("STOP: no usable JSON from the portal."); return false }
        }
        return true
    }

    if (!report("handshake", client.handshake())) return
    if (client.token == null) { log("STOP: the portal issued no token for this MAC."); return }
    if (step == ProbeStep.PORTAL_HANDSHAKE) { log("--- Step 4a complete. Check StbEmu on the TV before the next run."); return }

    val profile = client.profile()
    if (!report("get_profile", profile)) return
    val status = profile.field("status")
    if (status != null && status != "0") {
        log("STOP: profile status is $status (0 = authorized). The portal may need login/password auth or device fields.")
        return
    }
    if (step == ProbeStep.PORTAL_PROFILE) { log("--- Step 4b complete. Check StbEmu on the TV before the next run."); return }

    if (step == ProbeStep.PORTAL_CHANNELS) {
        if (!report("get_genres", client.genres())) return
        report("get_ordered_list page 1", client.channelsPage1().first)
        log("--- Step 4c complete. Check StbEmu on the TV before the next run.")
        return
    }

    // Step 4d
    val (page, cmd) = client.channelsPage1()
    if (!report("get_ordered_list page 1", page)) return
    if (cmd == null) { log("STOP: no channel cmd on page 1."); return }
    val (link, url) = client.createLink(cmd)
    if (!report("create_link (first channel)", link)) return
    if (url == null) { log("STOP: create_link returned no stream URL."); return }
    n++
    log("--- request $n of $planned: stream GET, first 64 KB only")
    val streamProbe = HttpProbe(budget, maxBodyBytes = 64 * 1024)
    val s = streamProbe.send(ProbeRequest("GET", url, mapOf("User-Agent" to identity.userAgent), "Step 4d stream format check"))
    log(s.describe(redactor(), includeBody = false))
    if (s is ProbeOutcome.Response) log("  stream format: ${StalkerClient.streamSignature(s.bodyPrefix)}")
    log("--- Step 4d complete. Check StbEmu on the TV.")
}

/** A blocked/banned signal from the portal, if any: blocked flag, or such words in msg fields or a non-JSON body. */
private fun blockSignal(r: StalkerResult): String? {
    if (r.field("blocked") == "1") return "blocked=1"
    val texts = if (r.isObject) listOfNotNull(r.field("msg"), r.field("block_msg"))
        else if (!r.hasJson) listOf(String((r.outcome as? ProbeOutcome.Response)?.bodyPrefix ?: ByteArray(0), Charsets.UTF_8))
        else emptyList()
    val words = listOf("banned", "blocked", "access denied", "not allowed", "suspended")
    return texts.firstNotNullOfOrNull { t -> words.firstOrNull { t.lowercase().contains(it) } }
}

private fun finish(step: ProbeStep, report: StringBuilder) {
    val file = writeOutput("${step.cliName}-live.txt", report.toString().trimEnd())
    println("Saved: $file")
}

/** Writes redacted text under probe_output/<UTC timestamp>/ and returns the relative path. */
private fun writeOutput(name: String, redactedText: String): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now())
    val dir = Paths.get("probe_output", stamp)
    Files.createDirectories(dir)
    val file = dir.resolve(name)
    Files.writeString(file, redactedText + "\n")
    return file.toString()
}

private fun fail(code: Int, message: String): Nothing {
    // Messages here come from our own code (names only) or were already redacted;
    // run them through a value-less redactor anyway to catch URLs, MACs and tokens.
    System.err.println("probe: " + Redactor(emptyMap()).redact(message))
    exitProcess(code)
}
