package io.github.ravindren_sm.openstream.data

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.WindowManager
import java.io.File

/**
 * No screenshots or screen recording of the app. The window is marked secure (FLAG_SECURE), so Android shows it as black in
 * screenshots, screen recordings (MediaProjection, `adb shell screencap` and `screenrecord`), screen mirroring and casting to
 * another display, and the recent-apps thumbnail.
 *
 * What it cannot stop: an HDMI capture device (that needs HDCP, which Android only turns on for DRM-protected video, and the
 * portal's streams are not DRM-protected), a camera pointed at the TV, or a copy of this open-source app built without it.
 *
 * Testing: a debuggable build lets captures through while a file named [ALLOW_FILE] exists in the app's private files, so a
 * test run can take the occasional screenshot (see docs/TESTING_ON_FIRE_TV.md). Release builds ignore the file. The flag is set
 * again every time the app comes to the screen, so creating or deleting the file takes effect on the next start.
 */
object CaptureGuard {
    const val ALLOW_FILE = "allow-screen-capture"

    /** Whether captures are blocked: always, unless the build is debuggable and the test file exists. */
    fun blocked(debuggable: Boolean, allowFileExists: Boolean): Boolean = !(debuggable && allowFileExists)

    fun apply(activity: Activity) {
        val debuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val allow = debuggable && File(activity.filesDir, ALLOW_FILE).exists()
        if (blocked(debuggable, allow)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
