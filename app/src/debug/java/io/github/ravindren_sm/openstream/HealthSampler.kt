package io.github.ravindren_sm.openstream

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import io.github.ravindren_sm.openstream.ui.components.PosterLoader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Debug-only memory sampler. Every 30 s it writes one line of numbers (no addresses, names or tokens) to
 * files/health-log.txt in the app's private storage and to logcat (tag OpenStream.health), so a long watch can
 * be read afterwards and rebuffers or decoder errors matched to free memory. It warns when the Java heap is over
 * 75 percent of its limit or the system's free memory is low. The file is trimmed to its last ~2,000 lines.
 */
class HealthSampler(private val app: Context) {
    private val start = System.currentTimeMillis()
    private val file = File(app.filesDir, "health-log.txt")
    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "health-sampler").apply { isDaemon = true } }

    fun start() { executor.scheduleWithFixedDelay({ runCatching { sample() } }, 5, 30, TimeUnit.SECONDS) }

    private fun sample() {
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) / MB
        val limitMb = rt.maxMemory() / MB
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val sys = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val line = "t=${(System.currentTimeMillis() - start) / 1000}s java=${javaMb}/${limitMb}MB native=${Debug.getNativeHeapAllocatedSize() / MB}MB " +
            "pss=${info.totalPss / 1024}MB gfx=${info.getMemoryStat("summary.graphics").toIntOrNull()?.div(1024) ?: -1}MB " +
            "sysFree=${sys.availMem / MB}MB low=${sys.lowMemory} posters=${PosterLoader.cacheBytes() / 1024}KB threads=${Thread.activeCount()}"
        Log.i(TAG, line)
        if (javaMb * 100 > limitMb * 75) Log.w(TAG, "Java heap over 75% of its limit")
        if (sys.lowMemory) Log.w(TAG, "System reports low memory")
        file.appendText(line + "\n")
        if (file.length() > 200_000) file.writeText(file.readLines().takeLast(2_000).joinToString("\n") + "\n")
    }

    private companion object { const val TAG = "OpenStream.health"; const val MB = 1024 * 1024 }
}
