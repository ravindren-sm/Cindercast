package io.github.ravindren_sm.openstream.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.ravindren_sm.openstream.provider.AesGcmBox
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The app's secret-sealing key, held in the Android Keystore.
 *
 * - AES-256, GCM, no padding; usable only for encrypt and decrypt; the key material cannot be read
 *   out of the Keystore (hardware-backed where the device supports it).
 * - Created on first use. Not tied to the screen lock or StrongBox: those need Android 9 or newer
 *   and the Fire TV Stick runs Android 11 with minSdk 25 supported.
 * - The alias is versioned so a future change of algorithm can add a second key.
 */
object AndroidKeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "openstream.settings.v1"

    /** An [AesGcmBox] using the Keystore key, creating the key if it does not exist yet. */
    fun box(): AesGcmBox = AesGcmBox(getOrCreate())

    private fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Removes the key (debug self-test cleanup and "reset app"). Sealed files become unreadable. */
    fun delete() {
        KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
    }
}
