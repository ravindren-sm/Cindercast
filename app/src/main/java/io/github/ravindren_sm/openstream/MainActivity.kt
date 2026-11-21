package io.github.ravindren_sm.openstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.ravindren_sm.openstream.ui.AppShell
import io.github.ravindren_sm.openstream.ui.theme.OpenStreamTheme

/**
 * App entry point. Hosts the themed shell (top tabs, back stack with focus memory).
 * The debug player lives in the debug source set (DebugPlayerActivity) and is not in release builds.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenStreamTheme { AppShell(onExit = { finish() }) } }
    }
}
