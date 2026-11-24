package io.github.ravindren_sm.openstream.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureGuardTest {
    @Test fun capturesAreBlockedUnlessADebugBuildHasTheTestFile() {
        assertTrue(CaptureGuard.blocked(debuggable = false, allowFileExists = false))
        assertTrue(CaptureGuard.blocked(debuggable = false, allowFileExists = true))    // release builds ignore the file
        assertTrue(CaptureGuard.blocked(debuggable = true, allowFileExists = false))
        assertFalse(CaptureGuard.blocked(debuggable = true, allowFileExists = true))
    }
}
