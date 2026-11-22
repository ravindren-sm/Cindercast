package io.github.ravindren_sm.openstream.data

import java.util.Locale

/**
 * What the Language and Subtitles overlay needs to know about one audio or text track. Copied out of Media3's
 * Format so the wording rules here are plain Kotlin and unit-tested.
 */
data class TrackInfo(
    val language: String? = null,
    val label: String? = null,
    val mimeType: String? = null,
    val channelCount: Int = 0,
    val describesVideo: Boolean = false,
    val isAlternate: Boolean = false,
    val isDub: Boolean = false,
    val isMain: Boolean = false,
)

object TrackLabels {
    /** Common three-letter codes, which Java's locale data does not name. */
    private val THREE_LETTER = mapOf(
        "eng" to "English", "hin" to "Hindi", "spa" to "Spanish", "fra" to "French", "fre" to "French", "deu" to "German",
        "ger" to "German", "ita" to "Italian", "por" to "Portuguese", "rus" to "Russian", "jpn" to "Japanese", "kor" to "Korean",
        "zho" to "Chinese", "chi" to "Chinese", "ara" to "Arabic", "tam" to "Tamil", "tel" to "Telugu", "pan" to "Punjabi",
        "ben" to "Bengali", "urd" to "Urdu", "mar" to "Marathi", "guj" to "Gujarati", "kan" to "Kannada", "mal" to "Malayalam",
        "tur" to "Turkish", "nld" to "Dutch", "dut" to "Dutch", "pol" to "Polish", "swe" to "Swedish", "tha" to "Thai",
    )

    /** "en" or "eng" become "English"; "und" or nothing become null; an unknown code is shown upper-cased. */
    fun languageName(code: String?): String? {
        val c = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" && it != "zxx" } ?: return null
        THREE_LETTER[c]?.let { return it }
        val tag = c.substringBefore('-').substringBefore('_')
        THREE_LETTER[tag]?.let { return it }
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH)
        return if (name.isNullOrBlank() || name.equals(tag, ignoreCase = true)) c.uppercase() else name
    }

    /** The line shown for a track: its label if it has one, else its language name, else "Track n" (1-based [ordinal]). */
    fun title(info: TrackInfo, ordinal: Int): String =
        info.label?.trim()?.takeIf { it.isNotEmpty() } ?: languageName(info.language) ?: "Track $ordinal"

    /** Channel layout and role tags for an audio track: 5.1, 2.0, AD, SAP, DUB, ORIG. */
    fun audioTags(info: TrackInfo): List<String> = buildList {
        when (info.channelCount) {
            0 -> {}
            1 -> add("1.0")
            2 -> add("2.0")
            6 -> add("5.1")
            8 -> add("7.1")
            else -> add("${info.channelCount}ch")
        }
        if (info.describesVideo) add("AD")
        if (info.isAlternate) add("SAP")
        if (info.isDub) add("DUB")
        if (info.isMain) add("ORIG")
    }

    /** Format tag for a text track: VTT, SRT, CC, DVB, TTX and a few more. */
    fun textTags(info: TrackInfo): List<String> {
        val tag = when (info.mimeType?.lowercase()) {
            "text/vtt" -> "VTT"
            "application/x-subrip" -> "SRT"
            "application/cea-608", "application/cea-708" -> "CC"
            "application/dvbsubs" -> "DVB"
            "application/x-teletext" -> "TTX"
            "text/x-ssa" -> "SSA"
            "application/ttml+xml" -> "TTML"
            else -> null
        }
        return listOfNotNull(tag)
    }

    /** "Audio: English 5.1 · Subtitles: Off" for the summary bar. */
    fun summary(audio: String?, subtitles: String?): String =
        "Audio: ${audio ?: "default"} · Subtitles: ${subtitles ?: "Off"}"
}

/** The type Media3 needs for a side-loaded subtitle file, guessed from its address (query and fragment ignored). */
object SubtitleMime {
    fun forUrl(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            path.endsWith(".vtt") || path.endsWith(".webvtt") -> "text/vtt"
            path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
            path.endsWith(".ttml") || path.endsWith(".dfxp") -> "application/ttml+xml"
            else -> "application/x-subrip"   // .srt, and the most common format when the address says nothing
        }
    }
}
