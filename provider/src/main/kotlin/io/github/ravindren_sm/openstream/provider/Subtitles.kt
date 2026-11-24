package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An external subtitle file offered by the portal's VOD create_link. [url] is as sent (absolute, or a path on the
 * portal host) and lives in memory only; [language] is a code or a name, if the portal gave one.
 */
class SubtitleRef(val language: String?, val url: String) {
    override fun toString() = "SubtitleRef(language=$language)"
}

/** A resolved VOD link: the stream address and any external subtitles. Never printed. */
class VodLink(val url: String, val subtitles: List<SubtitleRef>) {
    override fun toString() = "VodLink(subtitles=${subtitles.size})"
}

object SubtitleParser {
    private val URL_KEYS = listOf("file", "url", "src", "link", "path")
    private val LANGUAGE_KEYS = listOf("lang", "language", "title", "name", "label")

    /**
     * Subtitles from the "js" object of a VOD create_link. The portal's shape is not documented, so three are
     * accepted: a list of objects (address under file, url, src, link or path; language under lang, language,
     * title, name or label), a list of plain addresses, or a map of language to address. Anything else, or an
     * entry without an address, is skipped.
     */
    fun fromLink(js: JsonElement?): List<SubtitleRef> {
        val subs = (js as? JsonObject)?.get("subtitles") ?: return emptyList()
        return when (subs) {
            is JsonArray -> subs.mapNotNull { entry ->
                when (entry) {
                    is JsonPrimitive -> entry.contentOrNull?.let(::address)?.let { SubtitleRef(null, it) }
                    is JsonObject -> {
                        val url = URL_KEYS.firstNotNullOfOrNull { k -> (entry[k] as? JsonPrimitive)?.contentOrNull?.let(::address) }
                        val language = LANGUAGE_KEYS.firstNotNullOfOrNull { k -> (entry[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }
                        url?.let { SubtitleRef(language, it) }
                    }
                    else -> null
                }
            }
            is JsonObject -> subs.mapNotNull { (language, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let(::address)?.let { SubtitleRef(language.trim().ifEmpty { null }, it) }
            }
            else -> emptyList()
        }
    }

    /** An address worth keeping: absolute http(s), protocol-relative, or a path on the portal host. */
    private fun address(raw: String): String? {
        val v = raw.trim()
        return v.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("//") || it.startsWith("/") }
    }
}
