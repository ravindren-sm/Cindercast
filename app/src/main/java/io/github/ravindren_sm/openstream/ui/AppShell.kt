package io.github.ravindren_sm.openstream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.nav.NavStack
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.tabFocusId
import io.github.ravindren_sm.openstream.ui.components.TopNavBar
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.screens.PlaceholderScreen
import io.github.ravindren_sm.openstream.ui.screens.SetupScreen
import io.github.ravindren_sm.openstream.ui.screens.initialFocusId
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/**
 * App frame: top tab bar plus the current screen, driven by an immutable [NavStack].
 *
 * Focus rule (from the prototype): exactly one element is focused at any time. After every stack
 * change the shell restores the focus remembered by the screen being returned to
 * ([NavStack.restoreFocusId]); if that element no longer exists it falls back to the screen's
 * initial focus, then to the active tab. Back pops the stack; at the root it calls [onExit].
 */
@Composable
fun AppShell(store: SettingsStore, onExit: () -> Unit) {
    // First run (or unreadable settings) starts on the setup screen; otherwise on Home.
    val loaded = remember { store.load() }
    val notice = if (loaded is SettingsStore.Loaded.Unreadable) "The saved settings could not be opened on this device. Please enter them again." else null
    var stack by remember { mutableStateOf(NavStack.start(if (loaded is SettingsStore.Loaded.Found) Route.HOME else Route.SETUP)) }
    val memory = remember { FocusMemory() }

    BackHandler {
        if (memory.justFinishedEditing()) return@BackHandler    // that Back closed a keyboard
        val previous = stack.goBack()
        if (previous == null) onExit() else stack = previous
    }

    val topTab: Route? = stack.frames.lastOrNull { it.route.isTab }?.route

    Column(Modifier.fillMaxSize().background(Tokens.BgCanvas)) {
        if (stack.current.route == Route.SETUP) {
            SetupScreen(
                store = store,
                memory = memory,
                notice = notice,
                onSaved = { stack = NavStack.start(Route.HOME) },
                onSkip = { stack = NavStack.start(Route.HOME) },
            )
        } else {
            TopNavBar(
                activeTab = topTab,
                memory = memory,
                onSelectTab = { tab -> stack = stack.resetToTab(tab) },
            )
            PlaceholderScreen(
                entry = stack.current,
                memory = memory,
                onOpenDetail = { route, params ->
                    stack = stack.navigate(route, params, currentFocusId = memory.focusedId)
                },
            )
        }
    }

    // After each stack change, once the new screen is on screen, put focus where it belongs.
    LaunchedEffect(stack) {
        androidx.compose.runtime.withFrameNanos { }   // let the new screen compose and attach
        memory.tryFocusFirst(stack.restoreFocusId, initialFocusId(stack.current.route), topTab?.let(::tabFocusId))
    }
}
