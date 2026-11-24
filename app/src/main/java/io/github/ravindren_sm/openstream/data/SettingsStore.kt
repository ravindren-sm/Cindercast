package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.AesGcmBox
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.SealedBoxException
import java.io.File
import java.io.IOException

/**
 * Where the portal settings live on the device: one file, sealed with AES-256-GCM.
 *
 * - The key never leaves the Android Keystore (see [AndroidKeystoreKey]); this class only sees the
 *   [AesGcmBox] built from it, so unit tests can use an ordinary key and a temp folder.
 * - The file holds no plaintext: not the portal address, not the MAC.
 * - Writes go to a temporary file first and are renamed into place, so a crash cannot leave a
 *   half-written settings file.
 * - A file that cannot be opened (damaged, or the Keystore key was lost, for example after a
 *   factory reset or a restored backup) is reported as [Loaded.Unreadable], never as an exception
 *   and never with any content.
 */
class SettingsStore(private val file: File, private val box: AesGcmBox) {

    sealed interface Loaded {
        /** Nothing saved yet: first run. */
        data object None : Loaded
        class Found(val settings: PortalSettings) : Loaded
        /** A file exists but cannot be opened. The user must enter the settings again. */
        data object Unreadable : Loaded
    }

    fun load(): Loaded {
        if (!file.isFile) return Loaded.None
        return try {
            val settings = PortalSettings.fromBytes(box.open(file.readBytes(), ASSOCIATED_DATA))
            if (settings != null) Loaded.Found(settings) else Loaded.Unreadable
        } catch (_: SealedBoxException) {
            Loaded.Unreadable
        } catch (_: IOException) {
            Loaded.Unreadable
        }
    }

    @Throws(IOException::class)
    fun save(settings: PortalSettings) {
        val sealed = box.seal(settings.toBytes(), ASSOCIATED_DATA)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) {            // renameTo cannot replace on some filesystems
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("could not store the settings")
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    companion object {
        /** Binds the sealed bytes to this use, so they cannot be opened as some other kind of data. */
        private val ASSOCIATED_DATA = "openstream.portal-settings.v1".toByteArray(Charsets.UTF_8)
    }
}
