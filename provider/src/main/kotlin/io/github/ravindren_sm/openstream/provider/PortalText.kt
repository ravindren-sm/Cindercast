package io.github.ravindren_sm.openstream.provider

/**
 * Text cleanup for catalog labels. The portal sends some titles with HTML entities (for example
 * `&#124;` for a vertical bar), so every label shown in the UI goes through [decodeEntities].
 */
object PortalText {
    private val NUMERIC = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
    private val NAMED = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ")

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        var out = NUMERIC.replace(text) { m ->
            val body = m.groupValues[1]
            val code = if (body.startsWith("x")) body.drop(1).toIntOrNull(16) else body.toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
        for ((entity, replacement) in NAMED) out = out.replace(entity, replacement)
        return out
    }

    /** A rating the portal sends as text ("7.5", "0", "", "N/A"): a positive number, else null (hide the badge). */
    fun rating(raw: String?): Double? = raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 10.0 }
}
