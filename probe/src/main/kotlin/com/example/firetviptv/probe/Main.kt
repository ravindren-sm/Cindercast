package com.example.firetviptv.probe

import com.example.firetviptv.provider.InsecureSecretsFileException
import com.example.firetviptv.provider.MissingSecretException
import com.example.firetviptv.provider.ProbeRequests
import com.example.firetviptv.provider.ProbeStep
import com.example.firetviptv.provider.Redactor
import com.example.firetviptv.provider.Secrets
import com.example.firetviptv.provider.SecretsLoader
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
 *
 * Step 0 build: there is no HTTP client anywhere in this project, so every step is
 * dry-run only. A dry run loads the secrets, builds the request the step would send,
 * and prints it redacted. It also writes the same redacted text to
 * probe_output/<UTC timestamp>/ (gitignored).
 *
 * Every line printed or written goes through the Redactor, including error messages.
 */

private const val EXIT_USAGE = 64
private const val EXIT_CONFIG = 78
private const val EXIT_NOT_AVAILABLE = 2

fun main(args: Array<String>) {
    val command = args.firstOrNull { !it.startsWith("--") }
    val dryRun = "--dry-run" in args

    when (command) {
        null, "help", "list" -> { printSteps(); return }
        "secrets-check" -> { secretsCheck(); return }
    }

    val step = ProbeStep.fromCliName(command!!)
        ?: fail(EXIT_USAGE, "Unknown step '$command'. Run with 'list' to see the steps.")

    if (!dryRun) {
        fail(
            EXIT_NOT_AVAILABLE,
            "Network steps are not available in this build (Phase 2 Step 0: no HTTP client). " +
                "Use --dry-run. Each NETWORK step also needs the user's approval in chat.",
        )
    }
    dryRunStep(step)
}

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
        ProbeRequests.forStep(step, secrets)
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
