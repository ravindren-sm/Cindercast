package com.example.firetvhello

import android.content.Context
import com.example.firetviptv.provider.StalkerIdentity
import org.json.JSONObject
import java.io.File

/**
 * Debug-only connection settings, read from the app's private storage:
 *   files/dev-config.json  (written by scripts/push_dev_config.sh through `adb shell run-as`)
 *
 * Nothing here is in the APK, resources, BuildConfig, git or logs (CLAUDE.md rules 3 and 10).
 * The production app replaces this with an on-device setup screen and Keystore-backed
 * storage (docs/MILESTONES.md, M2).
 *
 * File shape (values are the user's; never logged):
 *   {"portalUrl": "...", "mac": "...", "genre": "ENGLISH USA LOCAL",
 *    "channel": "NEWS 12 LONG ISLAND", "identity": "honest", "streamUserAgent": "player",
 *    "mode": "live" | "vod-catalog" | "vod-movie" | "vod-episode", "vodCategory": "ENGLISH MOVIES"}
 */
data class DevConfig(
    val portalUrl: String,
    val mac: String,
    val genre: String,
    val channel: String,
    /** Identity for portal API calls. */
    val identity: StalkerIdentity,
    /** "player" (Media3 default), "mag" (MAG box UA) or "honest" (probe UA) for stream requests. */
    val streamUserAgent: String,
    /** "live", "vod-catalog" (4e), "vod-movie" or "vod-episode" (4f: play one VOD item, seek check). */
    val mode: String = "live",
    /** VOD category title for 4f (exact match, else shortest title containing it). */
    val vodCategory: String = "ENGLISH MOVIES",
) {
    /** Values the redactor must mask, labelled like the Mac probe's placeholders. */
    fun secretsForRedaction(): Map<String, String> = mapOf("PORTAL_URL" to portalUrl, "EMU_MAC" to mac)

    /** Safe description: no URL, no MAC. */
    override fun toString() = "DevConfig(mode=$mode, genre=$genre, channel=$channel, vodCategory=$vodCategory, identity=$identity, streamUserAgent=$streamUserAgent)"

    companion object {
        const val FILE_NAME = "dev-config.json"

        /** Returns null if the file is missing or incomplete; never throws with values in the message. */
        fun load(context: Context): DevConfig? = try {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.isFile) null else {
                val j = JSONObject(f.readText())
                DevConfig(
                    portalUrl = j.getString("portalUrl").trim(),
                    mac = j.getString("mac").trim(),
                    genre = j.optString("genre", "ENGLISH USA LOCAL"),
                    channel = j.optString("channel", "NEWS 12 LONG ISLAND"),
                    identity = if (j.optString("identity", "honest") == "mag") StalkerIdentity.MAG else StalkerIdentity.HONEST,
                    streamUserAgent = j.optString("streamUserAgent", "player"),
                    mode = j.optString("mode", "live"),
                    vodCategory = j.optString("vodCategory", "ENGLISH MOVIES"),
                ).takeIf { it.portalUrl.isNotEmpty() && it.mac.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
