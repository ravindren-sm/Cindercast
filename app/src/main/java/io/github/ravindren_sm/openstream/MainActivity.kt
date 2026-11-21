package io.github.ravindren_sm.openstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.AndroidKeystoreKey
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.ui.AppShell
import io.github.ravindren_sm.openstream.ui.theme.OpenStreamTheme
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import java.io.File

/**
 * App entry point. Hosts the themed shell (top tabs, back stack with focus memory).
 * Portal settings are kept in a sealed file (see [SettingsStore]); the key is held by the Android Keystore.
 * The debug player and self-test live in the debug source set and are not in release builds.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = try {
            SettingsStore(File(filesDir, SETTINGS_FILE), AndroidKeystoreKey.box())
        } catch (_: Exception) {
            null   // the device could not provide a Keystore key
        }
        setContent {
            OpenStreamTheme {
                if (store != null) {
                    AppShell(store = store, onExit = { finish() })
                } else {
                    Box(Modifier.fillMaxSize().background(Tokens.BgCanvas).padding(Spacing.SafeHorizontal)) {
                        Text(
                            "This device could not provide secure storage for your portal settings, so OpenStream cannot run here.",
                            style = MaterialTheme.typography.headlineSmall, color = Tokens.StatusError,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val SETTINGS_FILE = "settings.sealed"
    }
}
