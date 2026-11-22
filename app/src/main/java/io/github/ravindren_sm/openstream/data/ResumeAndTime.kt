package io.github.ravindren_sm.openstream.data

import android.content.Context

/**
 * Resume positions kept in app-private preferences (title id to milliseconds). Not sensitive, but it is
 * viewing history, so it stays on the device and is cleared with the app's data.
 */
class PrefsResumeStore(context: Context) : ResumeStore {
    private val prefs = context.applicationContext.getSharedPreferences("resume", Context.MODE_PRIVATE)
    override fun get(id: String): Long? = if (prefs.contains(id)) prefs.getLong(id, 0L) else null
    override fun put(id: String, positionMillis: Long) { prefs.edit().putLong(id, positionMillis).apply() }
    override fun clear(id: String) { prefs.edit().remove(id).apply() }
}

object TimeText {
    /** 754_000 -> "12:34", 4_000_000 -> "1:06:40". Negative or unknown values show as "0:00". */
    fun format(millis: Long): String {
        val total = (millis.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
