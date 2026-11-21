package io.github.ravindren_sm.openstream.ui.focus

import androidx.compose.ui.focus.FocusRequester

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
