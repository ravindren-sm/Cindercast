package com.example.firetvhello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

// Phase 1 hello screen.
//
// Purpose: prove the toolchain builds an app that launches on the Fire TV and
// responds to D-pad focus and the Back button. No provider code, no network,
// no playback. The provider compatibility gate (Phase 2) comes first.
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Press counter: visible proof that D-pad focus and select reach the app.
            var presses by remember { mutableIntStateOf(0) }
            HelloScreen(presses = presses, onPressed = { presses++ })
        }
    }
}

@Composable
fun HelloScreen(presses: Int, onPressed: () -> Unit) {
    // Dark TV color scheme; Surface supplies matching background and text colors.
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Fire TV IPTV: Phase 1 hello", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onPressed) {
                    Text(text = "Press me with the D-pad")
                }
                Text(text = "Pressed $presses times", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
