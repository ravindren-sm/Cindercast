package com.example.firetviptv.provider

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretsLoaderTest {

    private fun tempSecrets(content: String, mode: String): Path {
        val dir = Files.createTempDirectory("secrets-test")
        val file = dir.resolve("secrets.env")
        Files.writeString(file, content)
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(mode))
        return file
    }

    @Test fun `parses the save_secret format including escaped quotes`() {
        val parsed = SecretsLoader.parse(
            listOf(
                "# comment",
                "",
                "UNIFI_API_KEY='abc-123'",
                "XTREAM_PASSWORD='it'\\''s here'",   // file text: 'it'\''s here'
                "export M3U_URL='http://m.example.test/get.php?x=1&y=2'",
                "PLAIN=unquoted value",
                "bad line without equals",
                "1BAD='x'",
            )
        )
        assertEquals("abc-123", parsed["UNIFI_API_KEY"])
        assertEquals("it's here", parsed["XTREAM_PASSWORD"])
        assertEquals("http://m.example.test/get.php?x=1&y=2", parsed["M3U_URL"])
        assertEquals("unquoted value", parsed["PLAIN"])
        assertEquals(4, parsed.size)
    }

    @Test fun `loads a mode 600 file`() {
        val s = SecretsLoader.load(tempSecrets("XTREAM_USERNAME='alice123'\n", "rw-------"))
        assertEquals(listOf("XTREAM_USERNAME"), s.names)
        assertEquals("alice123", s.require("XTREAM_USERNAME"))
    }

    @Test fun `refuses files readable by group or others`() {
        for (mode in listOf("rw-r--r--", "rw-rw----", "rw----r--")) {
            assertFailsWith<InsecureSecretsFileException>(mode) {
                SecretsLoader.load(tempSecrets("A='x1234'\n", mode))
            }
        }
    }

    @Test fun `missing file gives a message without the path contents`() {
        assertFailsWith<InsecureSecretsFileException> {
            SecretsLoader.load(Files.createTempDirectory("none").resolve("absent.env"))
        }
    }

    @Test fun `toString and missing-secret errors never show values`() {
        val s = Secrets(mapOf("XTREAM_PASSWORD" to "hunter22"))
        assertFalse(s.toString().contains("hunter22"))
        val e = assertFailsWith<MissingSecretException> { s.require("EMU_MAC") }
        assertTrue(e.message!!.contains("EMU_MAC"))
    }

    @Test fun `short values are reported, not silently unredactable`() {
        val s = Secrets(mapOf("PIN" to "12", "XTREAM_PASSWORD" to "hunter22"))
        assertEquals(listOf("PIN"), s.namesTooShortToRedact)
    }

    @Test fun `env var overrides the default path`() {
        assertEquals("/tmp/x.env", SecretsLoader.defaultPath(mapOf(SecretsLoader.PATH_ENV_VAR to "/tmp/x.env")).toString())
        assertTrue(SecretsLoader.defaultPath(emptyMap()).toString().endsWith(".config/firetv-iptv/secrets.env"))
    }
}
