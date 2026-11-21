package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.nav.NavStack
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.tabFocusId
import io.github.ravindren_sm.openstream.ui.theme.Contrast
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavStackTest {

    @Test fun `navigate remembers the focus of the screen being left, back restores it`() {
        val s = NavStack.start()
            .navigate(Route.MOVIES, currentFocusId = "home-continue-0")
            .navigate(Route.MOVIE_DETAIL, mapOf("movieId" to "42"), currentFocusId = "poster-7")
        assertEquals(Route.MOVIE_DETAIL, s.current.route)
        assertEquals("42", s.current.params["movieId"])
        assertNull(s.restoreFocusId)                      // nothing to restore on a forward move

        val back1 = s.goBack()!!
        assertEquals(Route.MOVIES, back1.current.route)
        assertEquals("poster-7", back1.restoreFocusId)    // the exact poster focused before opening the detail

        val back2 = back1.goBack()!!
        assertEquals(Route.HOME, back2.current.route)
        assertEquals("home-continue-0", back2.restoreFocusId)
    }

    @Test fun `back at the root returns null so the caller can exit`() {
        assertNull(NavStack.start().goBack())
        assertTrue(!NavStack.start().canGoBack)
    }

    @Test fun `replace swaps the top screen and keeps its saved focus`() {
        val s = NavStack.start()
            .navigate(Route.MOVIES, currentFocusId = "tab-movies")
            .navigate(Route.MOVIE_DETAIL, currentFocusId = "poster-1")
            .navigate(Route.VOD_PLAYER, currentFocusId = "play", replace = true)
        assertEquals(listOf(Route.HOME, Route.MOVIES, Route.VOD_PLAYER), s.frames.map { it.route })
        assertEquals("poster-1", s.goBack()!!.restoreFocusId)
    }

    @Test fun `a missing focus id does not erase one that was already recorded`() {
        val s = NavStack.start()
            .navigate(Route.MOVIES, currentFocusId = "first")           // HOME remembers "first"
            .navigate(Route.MOVIE_DETAIL, currentFocusId = null)        // MOVIES had no focus to record
        assertEquals("first", s.frames[0].savedFocusId)                 // HOME's memory is untouched
        assertNull(s.goBack()!!.restoreFocusId)                         // nothing recorded for MOVIES
        // Re-navigating from a screen with a recorded focus keeps it when no new id is given.
        val again = s.goBack()!!.navigate(Route.SEARCH, currentFocusId = "grid-4")
            .goBack()!!.navigate(Route.SHOWS, currentFocusId = null)
        assertEquals("grid-4", again.frames[1].savedFocusId)
    }

    @Test fun `switching tabs resets the stack so back returns home with that tab focused`() {
        val deep = NavStack.start()
            .navigate(Route.MOVIES, currentFocusId = "nav-movies")
            .navigate(Route.MOVIE_DETAIL, currentFocusId = "poster-3")
        val shows = deep.resetToTab(Route.SHOWS)
        assertEquals(listOf(Route.HOME, Route.SHOWS), shows.frames.map { it.route })
        assertEquals(tabFocusId(Route.SHOWS), shows.restoreFocusId)
        val back = shows.goBack()!!
        assertEquals(Route.HOME, back.current.route)
        assertEquals(tabFocusId(Route.SHOWS), back.restoreFocusId)
        assertEquals(1, NavStack.start().navigate(Route.MOVIES, currentFocusId = "x").resetToTab(Route.HOME).depth)
    }

    @Test fun `only top-level routes are tabs and tabs match the prototype order`() {
        assertEquals(
            listOf("Home", "Live TV", "TV Guide", "Movies", "Shows", "Search", "Favorites", "Settings"),
            Route.TABS.map { it.label },
        )
        assertFailsWith<IllegalArgumentException> { NavStack.start().resetToTab(Route.MOVIE_DETAIL) }
    }
}

/** Locks the prototype's color tokens to the contrast they actually deliver. */
class ContrastTest {
    private val canvas = 0x0B0E14L; private val s1 = 0x141923L; private val s2 = 0x1D2432L; private val s3 = 0x283142L
    private val primary = 0xF3F6FAL; private val secondary = 0xA6B3C6L; private val ring = 0xF8FAFCL

    @Test fun `primary text clears 15 to 1 on canvas and cards`() {
        assertTrue(Contrast.ratio(primary, canvas) > 15.0)
        assertTrue(Contrast.ratio(primary, s1) > 15.0)
        assertTrue(Contrast.ratio(primary, s3) > 12.0)
    }

    @Test fun `secondary text is above 8_5 to 1 on the canvas and above the 4_5 minimum everywhere`() {
        assertTrue(Contrast.ratio(secondary, canvas) > 8.5)
        for (surface in listOf(canvas, s1, s2, s3)) assertTrue(Contrast.ratio(secondary, surface) >= 6.0)
        // Resting and selected cards stay above 7 (AAA); the focused row is the weakest at about 6.2.
        assertTrue(Contrast.ratio(secondary, s1) > 8.0)
        assertTrue(Contrast.ratio(secondary, s2) > 7.0)
    }

    @Test fun `focus ring is easy to see on every surface`() {
        for (surface in listOf(canvas, s1, s2, s3)) assertTrue(Contrast.ratio(ring, surface) > 10.0)
    }

    @Test fun `ratio is symmetric and one for identical colors`() {
        assertEquals(1.0, Contrast.ratio(canvas, canvas), 1e-9)
        assertEquals(Contrast.ratio(primary, canvas), Contrast.ratio(canvas, primary), 1e-9)
        assertNotNull(Contrast.ratio(0xFFFFFFL, 0x000000L))
        assertEquals(21.0, Contrast.ratio(0xFFFFFFL, 0x000000L), 0.01)
    }
}
