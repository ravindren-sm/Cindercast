package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.ConnectionTester
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.data.TestResult
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.ui.components.OsButton
import io.github.ravindren_sm.openstream.ui.components.OsTextField
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep { WELCOME, PORTAL }

/**
 * First-run setup, following the prototype's wizard: a welcome step, then "Portal and MAC" with a
 * connection test that must succeed before the settings are saved.
 *
 * - Settings are validated as the user types (messages are fixed text, shown after the first test
 *   attempt) and are only ever written to the sealed [SettingsStore].
 * - Changing either field clears an earlier test result, so a saved result always matches what was tested.
 * - Back on the second step returns to the welcome step; on the first step it leaves the screen.
 * - Nothing the user types is logged or shown outside the two fields (the success message shows only
 *   the account end date and the elapsed time).
 */
@Composable
fun SetupScreen(
    store: SettingsStore,
    memory: FocusMemory,
    notice: String?,
    onSaved: (PortalSettings) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(SetupStep.WELCOME) }
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var showProblems by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestResult>(TestResult.Idle) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val validation = PortalSettings.validate(url, mac)

    BackHandler(enabled = step == SetupStep.PORTAL) {
        // A Back that just closed the keyboard must not also leave this step (see FocusMemory).
        if (!memory.justFinishedEditing()) { step = SetupStep.WELCOME; test = TestResult.Idle }
    }

    // Put focus on the first control of each step once it is on screen.
    LaunchedEffect(step) {
        withFrameNanos { }
        memory.tryFocus(if (step == SetupStep.WELCOME) "setup-start" else "setup-url")
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        Text("OpenStream · First-run setup", style = MaterialTheme.typography.headlineMedium, color = Tokens.TextPrimary)

        if (step == SetupStep.WELCOME) {
            Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                Text("Connect your Stalker or Ministra portal using your device's registered MAC address.",
                    style = MaterialTheme.typography.bodyLarge, color = Tokens.TextPrimary)
                Text("OpenStream does not bundle channels, track what you watch, or need an account. " +
                    "Your portal address and MAC stay on this device, sealed with a key held by Android.",
                    style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary)
                Text("You will need: the portal address from your provider (often ending in /c/ or /stalker_portal/c/) " +
                    "and the MAC address registered with your subscription.",
                    style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary)
                Text("Before you start, close other apps that use the same MAC (such as another IPTV player): " +
                    "a portal usually allows one active session per MAC.",
                    style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusWarning)
                notice?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusError) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                OsButton("setup-start", memory, "Configure portal", onSelect = { step = SetupStep.PORTAL })
                OsButton("setup-skip", memory, "Skip for now", onSelect = onSkip)
            }
        } else {
            Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                val problems = (validation as? PortalSettings.Result.Invalid)?.problems.orEmpty()
                fun problemFor(vararg kinds: PortalSettings.Problem) =
                    if (showProblems) problems.firstOrNull { it in kinds }?.message else null

                OsTextField(
                    id = "setup-url", memory = memory, value = url,
                    onValueChange = { url = it; test = TestResult.Idle; saveError = null },
                    label = "Portal address",
                    hint = "Starts with http:// or https://",
                    error = problemFor(PortalSettings.Problem.URL_EMPTY, PortalSettings.Problem.URL_BAD_SCHEME, PortalSettings.Problem.URL_NO_HOST),
                    keyboardType = KeyboardType.Uri,
                )
                OsTextField(
                    id = "setup-mac", memory = memory, value = mac,
                    onValueChange = { mac = it; test = TestResult.Idle; saveError = null },
                    label = "Registered MAC address",
                    hint = "12 hexadecimal digits, like 00:1A:79:12:34:56",
                    error = problemFor(PortalSettings.Problem.MAC_EMPTY, PortalSettings.Problem.MAC_BAD),
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    downId = "setup-test",      // Down from here lands on "Test connection", not the nearest button
                )
                (validation as? PortalSettings.Result.Valid)?.settings?.takeIf { it.isCleartext }?.let {
                    Text("This address uses plain HTTP, so traffic to it is not encrypted. Many IPTV providers work this way.",
                        style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusWarning)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                    OsButton("setup-test", memory, "Test connection", enabled = test != TestResult.Testing, onSelect = {
                        showProblems = true
                        val valid = validation as? PortalSettings.Result.Valid ?: return@OsButton
                        test = TestResult.Testing
                        scope.launch { test = withContext(Dispatchers.IO) { ConnectionTester.run(valid.settings) } }
                    })
                    OsButton("setup-save", memory, "Save and continue", enabled = test is TestResult.Success, onSelect = {
                        val valid = validation as? PortalSettings.Result.Valid ?: return@OsButton
                        try { store.save(valid.settings); onSaved(valid.settings) }
                        catch (_: java.io.IOException) { saveError = "Could not save the settings on this device." }
                    })
                    OsButton("setup-back", memory, "Back", onSelect = { step = SetupStep.WELCOME; test = TestResult.Idle })
                }

                when (val t = test) {
                    TestResult.Idle -> Unit
                    TestResult.Testing -> Text("Testing the portal… requesting a session and checking the MAC.",
                        style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary)
                    is TestResult.Success -> Text(
                        "Connection verified" + (t.expiry?.let { ". Account active until $it" } ?: "") +
                            " · answered in ${t.elapsedMillis} ms.",
                        style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusSuccess)
                    is TestResult.Failure -> Text("Connection failed. ${t.message}",
                        style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusError)
                }
                saveError?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusError) }
            }
        }
    }
}
