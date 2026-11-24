package io.github.ravindren_sm.openstream.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * One background scope for the life of the app process, for small database writes that must finish even when the screen
 * that started them goes away (the history save when a player closes, a favourite toggled just before leaving). A scope tied
 * to the UI would cancel those writes; a new scope per screen rebuild would multiply. Not for long work: that belongs to the
 * screen or the background loops, which stop with it.
 */
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
