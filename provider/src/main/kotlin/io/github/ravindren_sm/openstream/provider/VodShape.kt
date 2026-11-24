package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Describes what fields a VOD list answer carries, without quoting content: every key name seen in the entries, how many
 * entries have each of the keys that could hold subtitle, language or audio information filled in, and for those keys only the
 * kind of value and its length (never the text, which could be a file address). Used to learn what a portal offers.
 */
object VodShape {
    private val INTERESTING = Regex("(?i)sub|lang|audio|track|dub|caption|cc_|closed")

    fun describe(js: JsonElement?): List<String> {
        val items = ((js as? JsonObject)?.get("data") as? JsonArray)?.filterIsInstance<JsonObject>() ?: return listOf("no list data")
        if (items.isEmpty()) return listOf("list is empty")
        val keys = items.flatMapTo(sortedSetOf<String>()) { it.keys }
        val lines = mutableListOf("entries: ${items.size}", "keys: $keys")
        for (k in keys.filter { INTERESTING.containsMatchIn(it) }) {
            val values = items.mapNotNull { it[k] }
            val filled = values.count { v -> kindAndLength(v).second > 0 }
            val sample = values.firstOrNull { kindAndLength(it).second > 0 }?.let { kindAndLength(it) }
            lines += "field $k: filled in $filled of ${items.size}" + (sample?.let { " (kind ${it.first}, length ${it.second})" } ?: "")
        }
        if (keys.none { INTERESTING.containsMatchIn(it) }) lines += "no field name mentions subtitles, language, audio or tracks"
        return lines
    }

    private fun kindAndLength(v: JsonElement): Pair<String, Int> = when (v) {
        is JsonPrimitive -> "text" to (v.contentOrNull?.trim()?.length ?: 0)
        is JsonArray -> "list" to v.size
        is JsonObject -> "object" to v.size
        else -> "other" to 0
    }
}
