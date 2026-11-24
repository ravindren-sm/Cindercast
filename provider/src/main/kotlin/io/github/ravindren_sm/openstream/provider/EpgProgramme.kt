package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One programme of a channel's guide. Times are seconds since the epoch. Titles and descriptions are catalog text. */
class EpgProgramme(val name: String, val description: String?, val category: String?, val startSec: Long, val stopSec: Long) {
    val durationSec get() = stopSec - startSec
    override fun toString() = "EpgProgramme($startSec-$stopSec)"
}

object EpgParser {
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.sec(vararg keys: String): Long? = keys.firstNotNullOfOrNull { str(it)?.toDoubleOrNull()?.toLong() }?.takeIf { it > 1_000_000_000 }

    /** Programmes from a get_short_epg answer (a bare array, or an object with a "data" array), in time order. Entries without a name or times are dropped. */
    fun programmes(js: JsonElement?): List<EpgProgramme> {
        val array = (js as? JsonArray) ?: ((js as? JsonObject)?.get("data") as? JsonArray) ?: return emptyList()
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val start = o.sec("start_timestamp", "time") ?: return@mapNotNull null
            val stop = o.sec("stop_timestamp", "time_to") ?: o.str("duration")?.toLongOrNull()?.let { start + it } ?: return@mapNotNull null
            if (stop <= start) return@mapNotNull null
            EpgProgramme(name, o.str("descr"), o.str("category"), start, stop)
        }.sortedBy { it.startSec }
    }
}
