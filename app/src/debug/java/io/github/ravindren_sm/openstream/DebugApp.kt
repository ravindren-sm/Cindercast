package io.github.ravindren_sm.openstream

import android.app.Application
import io.github.ravindren_sm.openstream.data.PortalTransport

/**
 * Debug-only Application (registered in src/debug/AndroidManifest.xml). It attaches the portal
 * traffic logger to [PortalTransport]. Release builds have no such class, so the logger is never
 * installed there. Nothing in main code refers to this class.
 */
class DebugApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PortalTransport.interceptors = listOf(PortalTrafficLog())
    }
}
