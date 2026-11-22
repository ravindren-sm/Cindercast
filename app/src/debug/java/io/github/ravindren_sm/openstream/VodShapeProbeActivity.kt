package io.github.ravindren_sm.openstream

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.AndroidKeystoreKey
import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import java.io.File

/**
 * Debug-only: one page of the portal's VOD list, reported as field names and fill counts (OpenStream.vodshape tag), never
 * content. Uses the app's saved settings:
 *   adb shell am start -n io.github.ravindren_sm.openstream/.VodShapeProbeActivity
 */
class VodShapeProbeActivity : ComponentActivity() {
    private val session = PortalSession()
    override fun onStart() { super.onStart(); session.onForeground() }
    override fun onStop() { session.onBackground(); super.onStop() }
    override fun onDestroy() { session.shutdown(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Box(Modifier.fillMaxSize().background(Tokens.BgCanvas)) { Text("Checking list fields…", color = Tokens.TextPrimary) } }
        val found = SettingsStore(File(filesDir, MainActivity.SETTINGS_FILE), AndroidKeystoreKey.box()).load() as? SettingsStore.Loaded.Found
        if (found == null) { Log.i(TAG, "no saved settings"); finish(); return }
        session.configure(found.settings)
        Thread {
            var waited = 0
            while (session.status !is SessionStatus.Ready && waited < 20) { Thread.sleep(500); waited++ }
            if (session.status !is SessionStatus.Ready) { Log.i(TAG, "session not ready"); finish(); return@Thread }
            val r = session.withSession { m -> m.call { c -> c.vodListShape("*", 1) } }
            Log.i(TAG, "list page: status=${r?.ok} " + r?.summary?.joinToString(" | "))
            Log.i(TAG, "done")
            finish()
        }.start()
    }

    private companion object { const val TAG = "OpenStream.vodshape" }
}
