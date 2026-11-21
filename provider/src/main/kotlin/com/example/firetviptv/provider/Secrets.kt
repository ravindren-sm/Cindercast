package com.example.firetviptv.provider

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

/**
 * Secret values loaded from the user's local secrets file.
 *
 * Values never leave this class except through [require] (for building requests)
 * and [redactor] (so every log line can be scrubbed). [toString] lists names only,
 * so an accidental print or string template cannot leak a value.
 */
class Secrets(private val values: Map<String, String>) {

    /** Names present in the file, sorted. Safe to print. */
    val names: List<String> get() = values.keys.sorted()

    fun has(name: String): Boolean = values.containsKey(name)

    /**
     * Names whose values are too short for the redactor to match safely.
     * The probe refuses network steps that need any of these.
     */
    val namesTooShortToRedact: List<String>
        get() = values.filterValues { it.length < Redactor.MIN_SECRET_LENGTH }.keys.sorted()

    /** Returns the value for [name], or throws with a message that names only the key. */
    fun require(name: String): String =
        values[name] ?: throw MissingSecretException(name)

    /** A redactor that knows every loaded value, labelled by its name (for example <EMU_MAC>). */
    fun redactor(): Redactor = Redactor(values)

    override fun toString(): String = "Secrets(names=$names)"
}

class MissingSecretException(val secretName: String) :
    IllegalStateException("Secret '$secretName' is not set. Save it with: bash scripts/save_secret.sh $secretName")

class InsecureSecretsFileException(message: String) : IllegalStateException(message)

/**
 * Reads the secrets file written by scripts/save_secret.sh.
 *
 * File format, one entry per line: NAME='value'. An embedded single quote is written
 * the shell way, as '\'' (close quote, escaped quote, reopen quote). Blank lines,
 * comments (#) and an optional leading "export " are accepted. Unquoted values are
 * taken literally up to the end of the line.
 */
object SecretsLoader {

    const val PATH_ENV_VAR = "FIRETV_IPTV_SECRETS"

    /** Path from $FIRETV_IPTV_SECRETS, else ~/.config/firetv-iptv/secrets.env. */
    fun defaultPath(env: Map<String, String> = System.getenv()): Path =
        env[PATH_ENV_VAR]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".config", "firetv-iptv", "secrets.env")

    /**
     * Loads [path]. Refuses a file that group or others can read or write:
     * it must be mode 600 (or stricter), as save_secret.sh creates it.
     */
    fun load(path: Path = defaultPath()): Secrets {
        if (!Files.isRegularFile(path)) {
            throw InsecureSecretsFileException("Secrets file not found at the configured path. Create it with scripts/save_secret.sh.")
        }
        checkPermissions(path)
        return Secrets(parse(Files.readAllLines(path)))
    }

    private fun checkPermissions(path: Path) {
        val perms = Files.getPosixFilePermissions(path)
        val tooOpen = perms - setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        if (tooOpen.isNotEmpty()) {
            throw InsecureSecretsFileException(
                "Secrets file permissions are too open (${tooOpen.joinToString()}). Run: chmod 600 on the file."
            )
        }
    }

    /** Parses file lines into name -> value. Exposed for tests. */
    fun parse(lines: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (raw in lines) {
            val line = raw.trim().removePrefix("export ").trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val name = line.substring(0, eq).trim()
            if (!NAME_PATTERN.matches(name)) continue
            result[name] = unquote(line.substring(eq + 1))
        }
        return result
    }

    private val NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Undoes shell single-quoting: 'a'\''b' -> a'b. Unquoted text is kept as is. */
    private fun unquote(text: String): String {
        if (!text.startsWith("'")) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '\'' -> {
                    // Quoted run: copy everything up to the closing quote.
                    val end = text.indexOf('\'', i + 1)
                    if (end < 0) { out.append(text, i + 1, text.length); break }
                    out.append(text, i + 1, end)
                    i = end + 1
                }
                text.startsWith("\\'", i) -> { out.append('\''); i += 2 }
                else -> { out.append(text[i]); i++ }
            }
        }
        return out.toString()
    }
}
