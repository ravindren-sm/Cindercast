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
 * Debug-only: asks the portal what guide (EPG) data it offers and logs the shape of the answers (key names, counts,
 * time spread) to the OpenStream.epg tag. Never logs programme text. Uses the app's saved settings:
 *   adb shell am start -n io.github.ravindren_sm.openstream/.EpgProbeActivity
 * One session, about six requests, then it finishes.
 */
class EpgProbeActivity : ComponentActivity() {
    private val session = PortalSession()
    override fun onStart() { super.onStart(); session.onForeground() }
    override fun onStop() { session.onBackground(); super.onStop() }
    override fun onDestroy() { session.shutdown(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Box(Modifier.fillMaxSize().background(Tokens.BgCanvas)) { Text("Probing guide data…", color = Tokens.TextPrimary) } }
        val store = SettingsStore(File(filesDir, MainActivity.SETTINGS_FILE), AndroidKeystoreKey.box())
        val found = store.load() as? SettingsStore.Loaded.Found
        if (found == null) { Log.i(TAG, "no saved settings"); finish(); return }
        session.configure(found.settings)
        Thread {
            var waited = 0
            while (session.status !is SessionStatus.Ready && waited < 20) { Thread.sleep(500); waited++ }
            if (session.status !is SessionStatus.Ready) { Log.i(TAG, "session not ready"); finish(); return@Thread }
            // One channel id from the first page of channels (nothing about it is logged).
            var channelId: String? = null
            session.withSession { m -> m.call { c -> val (r, p) = c.liveChannels("*", 1); channelId = p?.items?.firstOrNull()?.id; r } }
            val id = channelId
            Log.i(TAG, "channel id found: ${id != null}")
            if (id != null) {
                val short = session.withSession { m -> m.call { c -> c.epgShort(id, 10) } }
                Log.i(TAG, "short guide: status=${short?.ok} " + short?.summary?.joinToString(" | "))
            }
            val t0 = System.currentTimeMillis()
            val all = session.withSession(60_000) { m -> m.call { c -> c.epgInfo(2) } }
            Log.i(TAG, "all-channels guide (2 h): status=${all?.ok} in ${System.currentTimeMillis() - t0} ms " + all?.summary?.joinToString(" | "))
            Log.i(TAG, "done")
            finish()
        }.start()
    }

    private companion object { const val TAG = "OpenStream.epg" }
}
