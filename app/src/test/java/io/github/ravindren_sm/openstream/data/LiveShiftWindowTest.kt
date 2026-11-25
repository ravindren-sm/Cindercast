package io.github.ravindren_sm.openstream.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveShiftWindowTest {
    @Test fun theLabelSaysHowFarBackPlaybackCanGoWhenTheWindowIsKnown() {
        assertEquals("Live", LiveShift.label(1_000, windowMillis = 20_000))
        assertEquals("-0:29 behind live", LiveShift.label(29_000))
        assertEquals("-0:29 behind live · can go back 0:20", LiveShift.label(29_000, windowMillis = 20_000))
        assertEquals("", LiveShift.label(null, windowMillis = 20_000))
    }
}
