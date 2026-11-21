package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.provider.AesGcmBox
import io.github.ravindren_sm.openstream.provider.PortalSettings
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The sealed settings file, tested with an ordinary AES key and a temp folder (no Android needed). */
class SettingsStoreTest {

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun newStore(dir: File = Files.createTempDirectory("settings-test").toFile(), box: AesGcmBox = AesGcmBox(newKey())) =
        SettingsStore(File(dir, "settings.sealed"), box)

    private fun settings(url: String = "http://portal.example.test:8080/stalker_portal/c/", mac: String = "00:1A:79:12:34:56") =
        (PortalSettings.validate(url, mac) as PortalSettings.Result.Valid).settings

    @Test fun `first run has nothing saved`() {
        assertEquals(SettingsStore.Loaded.None, newStore().load())
    }

    @Test fun `save then load returns the same settings`() {
        val store = newStore()
        store.save(settings())
        val loaded = assertIs<SettingsStore.Loaded.Found>(store.load())
        assertEquals("http://portal.example.test:8080/stalker_portal/c/", loaded.settings.portalUrl)
        assertEquals("00:1A:79:12:34:56", loaded.settings.mac)
    }

    @Test fun `the file holds no plaintext`() {
        val dir = Files.createTempDirectory("settings-test").toFile()
        val store = newStore(dir)
        store.save(settings())
        val raw = String(File(dir, "settings.sealed").readBytes(), Charsets.ISO_8859_1)
        listOf("portal.example.test", "8080", "00:1A", "001A79", "stalker_portal", "mac", "portalUrl")
            .forEach { assertFalse(raw.contains(it, ignoreCase = true), "plaintext leaked: $it") }
    }

    @Test fun `saving again replaces the old settings and leaves no temp file`() {
        val dir = Files.createTempDirectory("settings-test").toFile()
        val store = newStore(dir)
        store.save(settings())
        store.save(settings(url = "https://other.example.test/c/", mac = "AA-BB-CC-DD-EE-FF"))
        val loaded = assertIs<SettingsStore.Loaded.Found>(store.load())
        assertEquals("https://other.example.test/c/", loaded.settings.portalUrl)
        assertEquals("AA:BB:CC:DD:EE:FF", loaded.settings.mac)
        assertEquals(listOf("settings.sealed"), dir.list()!!.toList())
    }

    @Test fun `a damaged file or a different key is unreadable, not an exception`() {
        val dir = Files.createTempDirectory("settings-test").toFile()
        val key = newKey()
        SettingsStore(File(dir, "settings.sealed"), AesGcmBox(key)).save(settings())

        val damaged = File(dir, "settings.sealed")
        val bytes = damaged.readBytes(); bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 0x5A).toByte()
        damaged.writeBytes(bytes)
        assertEquals(SettingsStore.Loaded.Unreadable, SettingsStore(damaged, AesGcmBox(key)).load())

        SettingsStore(damaged, AesGcmBox(key)).save(settings())
        assertEquals(SettingsStore.Loaded.Unreadable, SettingsStore(damaged, AesGcmBox(newKey())).load())   // key lost

        damaged.writeBytes(ByteArray(3))
        assertEquals(SettingsStore.Loaded.Unreadable, SettingsStore(damaged, AesGcmBox(key)).load())
    }

    @Test fun `clear removes the settings`() {
        val store = newStore()
        store.save(settings())
        store.clear()
        assertEquals(SettingsStore.Loaded.None, store.load())
        assertTrue(true)
    }
}
