package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.AgeLevels
import io.github.ravindren_sm.openstream.provider.VodItem

/**
 * A word list for text a Kids profile must not see (a title, a description, an episode name). A word matches only as a whole
 * word, ignoring case and punctuation, so "Scunthorpe" or "Essex" are not caught by "sex"; a phrase of several words matches
 * when its words appear next to each other. The list is a second net behind the age labels, not a replacement: a name can be
 * harmless in any list yet unsuitable, so a Kids profile also needs a known age label at or below its ceiling.
 */
class TextFilter(words: Collection<String> = DEFAULT_WORDS) {
    private val singles: Set<String>
    private val phrases: List<List<String>>

    init {
        val tokens = words.map { tokens(it) }.filter { it.isNotEmpty() }
        singles = tokens.filter { it.size == 1 }.mapTo(HashSet()) { it[0] }
        phrases = tokens.filter { it.size > 1 }
    }

    /** True if [text] contains a listed word or phrase. */
    fun blocks(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = tokens(text)
        if (t.any { it in singles }) return true
        return phrases.any { p -> (0..t.size - p.size).any { i -> p.indices.all { t[i + it] == p[it] } } }
    }

    fun blocksAny(vararg texts: String?): Boolean = texts.any { blocks(it) }

    companion object {
        private fun tokens(s: String): List<String> = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        /** Sexual, abusive and drug words that have no place in a child's list of titles. Edited here; adults may add more later. */
        val DEFAULT_WORDS: List<String> = listOf(
            "porn", "porno", "pornographic", "xxx", "sex", "sexy", "sexual", "sexting", "nude", "nudes", "nudity", "naked", "topless",
            "erotic", "erotica", "fetish", "bdsm", "orgy", "hentai", "stripper", "strippers", "strip club", "escort", "escorts",
            "fuck", "fucking", "fucked", "motherfucker", "shit", "bitch", "bitches", "slut", "whore", "cunt", "bastard",
            "rape", "raped", "rapist", "molest", "incest", "cocaine", "heroin", "meth", "adult", "adults only", "18+",
        )
    }
}

/**
 * What a Kids profile may see among movies and shows: a known age label at or under [maxAge] (an unknown label is blocked),
 * and no listed word in the name, description or genres. Seasons and episodes carry no age label of their own, so for them
 * only the words are checked: the show above them already passed.
 */
class KidsRules(val maxAge: Int, val words: TextFilter = TextFilter()) {
    fun allows(item: VodItem): Boolean {
        if (words.blocksAny(item.name, item.description, item.genres)) return false
        if (item.isSeason || item.isEpisode || item.isFile) return true
        val level = AgeLevels.levelOf(item.age) ?: return false
        return level <= maxAge
    }

    /** For rows that hold only a name and an age label (the on-device title index). */
    fun allows(name: String, age: String?, genres: String?): Boolean {
        if (words.blocksAny(name, genres)) return false
        val level = AgeLevels.levelOf(age) ?: return false
        return level <= maxAge
    }

    /** For text alone (a channel name, a category name, an episode title). */
    fun allowsText(text: String?): Boolean = !words.blocks(text)
}
