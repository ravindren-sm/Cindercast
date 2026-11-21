package io.github.ravindren_sm.openstream

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import io.github.ravindren_sm.openstream.data.AndroidKeystoreKey
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.SealedBoxException
import java.io.File

/**
 * Debug-only: proves the Android Keystore path on the real device and can seed or clear the app's
 * real settings for testing. Uses only placeholder values; logs only PASS/FAIL lines (tag OS_SELFTEST).
 *
 *   adb shell am start -n io.github.ravindren_sm.openstream/.KeystoreSelfTestActivity
 *   ... --ez seed true     (writes placeholder settings, so the app starts on Home)
 *   ... --ez clear true    (removes the saved settings, so the app starts on setup)
 */
class KeystoreSelfTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun report(name: String, ok: Boolean, detail: String = "") = Log.i(TAG, (if (ok) "PASS " else "FAIL ") + name + detail)
        try {
            val placeholder = (PortalSettings.validate("http://portal.example.test:8080/stalker_portal/c/", "00:1A:79:00:00:01")
                as PortalSettings.Result.Valid).settings

            val box = AndroidKeystoreKey.box()
            val sealed = box.seal("placeholder".toByteArray())
            report("key created and seal/open round trip", String(box.open(sealed)) == "placeholder")

            val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            report("tampered data rejected", try { box.open(tampered); false } catch (_: SealedBoxException) { true })

            report("key persists (second lookup opens earlier data)", String(AndroidKeystoreKey.box().open(sealed)) == "placeholder")

            val temp = File(cacheDir, "selftest.sealed")
            val tempStore = SettingsStore(temp, AndroidKeystoreKey.box())
            tempStore.save(placeholder)
            val raw = String(temp.readBytes(), Charsets.ISO_8859_1)
            report("store round trip", (tempStore.load() as? SettingsStore.Loaded.Found)?.settings?.mac == "00:1A:79:00:00:01")
            report("file holds no plaintext", !raw.contains("portal.example") && !raw.contains("00:1A"))
            tempStore.clear()

            val real = SettingsStore(File(filesDir, MainActivity.SETTINGS_FILE), AndroidKeystoreKey.box())
            if (intent.getBooleanExtra("seed", false)) { real.save(placeholder); report("seeded placeholder settings", true) }
            if (intent.getBooleanExtra("clear", false)) { real.clear(); report("cleared saved settings", true) }
        } catch (e: Exception) {
            report("self-test", false, " (${e::class.simpleName})")
        }
        Log.i(TAG, "DONE")
        finish()
    }

    private companion object { const val TAG = "OS_SELFTEST" }
}
