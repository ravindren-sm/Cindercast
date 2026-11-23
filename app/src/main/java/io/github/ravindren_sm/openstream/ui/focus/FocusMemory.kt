package io.github.ravindren_sm.openstream.ui.focus

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup

/**
 * Bridges stable string focus IDs (what [io.github.ravindren_sm.openstream.nav.NavStack]
 * remembers) and Compose's [FocusRequester]s.
 *
 * - Every focusable element registers a requester under its ID while it is on screen.
 * - [focusedId] always holds the ID of the element that currently has focus (single-focus rule).
 * - [tryFocus] moves focus to an ID if that element exists right now; callers fall back to a
 *   screen's initial-focus ID when the remembered one is gone (for example after a list reload).
 */
class FocusMemory(private val nanoTime: () -> Long = System::nanoTime) {
    var focusedId: String? = null
        internal set

    private val requesters = HashMap<String, FocusRequester>()

    internal fun requesterFor(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    internal fun release(id: String) {
        requesters.remove(id)
        if (focusedId == id) focusedId = null
    }

    private var editEndedAtNanos: Long? = null

    /** A text field just left edit mode (keyboard closed, or focus moved away). */
    fun noteEditEnded() { editEndedAtNanos = nanoTime() }

    /**
     * True shortly after an edit ended. On Fire OS, closing the keyboard with Back also clears the
     * field's focus, which ends the edit before the same Back press reaches the app; screens use this
     * to ignore that Back instead of leaving the screen.
     */
    fun justFinishedEditing(windowMillis: Long = 600): Boolean {
        val ended = editEndedAtNanos ?: return false
        return nanoTime() - ended < windowMillis * 1_000_000
    }

    /** True if focus moved. False if the ID is unknown or not attached yet. */
    fun tryFocus(id: String): Boolean {
        val requester = requesters[id] ?: return false
        return try { requester.requestFocus(); true } catch (_: IllegalStateException) { false }
    }

    /** Tries each ID in order; returns the one that took focus, or null. */
    fun tryFocusFirst(vararg ids: String?): String? = ids.firstOrNull { it != null && tryFocus(it) }
}

/**
 * For a horizontal row of items: Left at the first item and Right at the last stay put instead of jumping to whatever lies
 * diagonally nearest (the tab bar, or a button in the next row). Up and Down leave the row as usual. With [entryId], coming
 * into the row from above or below lands on that item (normally the first), not on whichever card is nearest the middle of
 * the element focus came from (a full-width row above would otherwise pick a card in the middle and skip the first ones).
 */
fun androidx.compose.ui.Modifier.keepHorizontalFocus(memory: FocusMemory? = null, entryId: String? = null): androidx.compose.ui.Modifier = this
    .then(androidx.compose.ui.Modifier.focusProperties {
        onEnter = {
            val d = requestedFocusDirection
            if (memory != null && entryId != null && (d == androidx.compose.ui.focus.FocusDirection.Down || d == androidx.compose.ui.focus.FocusDirection.Up)) {
                memory.tryFocus(entryId)
            }
        }
        onExit = {
            if (requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Left ||
                requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Right) cancelFocusChange()
        }
    })
    .focusGroup()

/**
 * For a layer drawn over the screen (a dialog, a picker, the profiles screen): the D-pad cannot move focus out of it. The
 * screen underneath is still composed and focusable, so without this a press at the layer's edge could move focus to a
 * hidden element behind it, where the viewer can no longer see it.
 */
fun androidx.compose.ui.Modifier.trapFocus(): androidx.compose.ui.Modifier = this
    .then(androidx.compose.ui.Modifier.focusProperties {
        onExit = {
            // Only D-pad moves are held; a focus request from code (for example returning focus when the layer closes) passes.
            val d = requestedFocusDirection
            if (d == androidx.compose.ui.focus.FocusDirection.Up || d == androidx.compose.ui.focus.FocusDirection.Down ||
                d == androidx.compose.ui.focus.FocusDirection.Left || d == androidx.compose.ui.focus.FocusDirection.Right ||
                d == androidx.compose.ui.focus.FocusDirection.Next || d == androidx.compose.ui.focus.FocusDirection.Previous) cancelFocusChange()
        }
    })
    .focusGroup()
