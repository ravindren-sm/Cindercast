package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Describes the structure of a guide (EPG) answer without quoting any of it: container type, how many channels and
 * programmes, the field names of a programme, and how far in time the programmes reach (hours before or after now,
 * from any numeric field that looks like a start or end time). Used to learn what a portal offers; the output holds
 * key names and numbers only, never programme titles or descriptions.
 */
object EpgShape {
    private val TIME_KEYS = Regex("(?i)^(start|stop|end|time|time_to|start_timestamp|stop_timestamp|real_id)$|timestamp")

    fun describe(js: JsonElement?, nowSeconds: Long): List<String> {
        val lines = mutableListOf<String>()
        val programmes = mutableListOf<JsonObject>()
        when (js) {
            null -> return listOf("guide: no payload")
            is JsonArray -> { lines += "guide: array of ${js.size}"; js.filterIsInstance<JsonObject>().let(programmes::addAll) }
            is JsonObject -> {
                lines += "guide: object, keys=${js.keys.take(12)}"
                when (val data = js["data"]) {
                    is JsonArray -> { lines += "data: array of ${data.size}"; data.filterIsInstance<JsonObject>().let(programmes::addAll) }
                    is JsonObject -> {
                        lines += "data: object with ${data.size} entries"
                        var withProgrammes = 0
                        for ((_, v) in data) if (v is JsonArray && v.isNotEmpty()) { withProgrammes++; v.filterIsInstance<JsonObject>().let(programmes::addAll) }
                        lines += "entries with programmes: $withProgrammes"
                    }
                    else -> lines += "data: ${data?.let { it::class.simpleName } ?: "absent"}"
                }
            }
            is JsonPrimitive -> return listOf("guide: a plain value")
        }
        lines += "programmes: ${programmes.size}"
        programmes.firstOrNull()?.let { lines += "programme fields: ${it.keys.take(20)}" }
        // Time spread, from fields that hold epoch seconds.
        val times = mutableListOf<Double>()
        for (p in programmes) for ((k, v) in p) {
            if (!TIME_KEYS.containsMatchIn(k)) continue
            val n = (v as? JsonPrimitive)?.doubleOrNull ?: (v as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: continue
            if (n > 1_000_000_000) times += n
        }
        if (times.isNotEmpty()) {
            fun hours(t: Double) = "%.1f".format((t - nowSeconds) / 3600.0)
            lines += "time spread from now (hours): ${hours(times.min())} to ${hours(times.max())}"
        }
        return lines
    }
}
