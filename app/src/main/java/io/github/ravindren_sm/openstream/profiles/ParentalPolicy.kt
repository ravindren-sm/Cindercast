package io.github.ravindren_sm.openstream.profiles

/** What a profile may do with a title. LOCKED means "ask for the PIN"; BLOCKED means "never show it here". */
enum class Verdict { ALLOWED, LOCKED, BLOCKED }

/** The parts of a profile the rules need (kept apart from the database row so the rules stay plain Kotlin). */
data class ProfileInfo(
    val id: Long,
    val name: String,
    val isKids: Boolean,
    /** Kids only: the highest age level this profile may watch (default [ParentalPolicy.DEFAULT_KIDS_MAX_AGE]). */
    val maxAgeLevel: Int = ParentalPolicy.DEFAULT_KIDS_MAX_AGE,
    /** Adults only: also list categories whose names mark adult content. */
    val showAdultCategories: Boolean = false,
    /** Index into the profile colours (the picker's tile colour). */
    val colorIndex: Int = 0,
)

/**
 * Turns the portal's age labels ("G", "PG", "U/A 13+", "TV-MA", "16+") into a minimum age, so one set of rules can
 * judge every label the catalog uses. A blank or unrecognised label has no level (null).
 */
object AgeLevels {
    /** From this level up, an adult profile must enter the PIN to play a title. */
    const val RESTRICTED_FROM = 16

    private val EXACT = mapOf(
        "G" to 0, "U" to 0, "ALL" to 0, "TV-G" to 0, "TV-Y" to 0, "TV-Y7" to 7, "PG" to 8, "TV-PG" to 8,
        "PG-13" to 13, "TV-14" to 14, "R" to 17, "NC-17" to 18, "UA" to 12, "U/A" to 12, "M" to 15, "TP" to 0, "TV-MA" to 18, "A" to 18, "X" to 18, "ADULT" to 18, "ADULTS" to 18,
    )
    private val UNKNOWN = setOf("", "NR", "UNRATED", "NOT RATED", "N/A", "NA", "-", "?")
    private val DIGITS = Regex("(\\d{1,2})")

    fun levelOf(label: String?): Int? {
        val n = label?.trim()?.uppercase()?.replace(Regex("\\s*\\(.*?\\)"), "")?.replace(Regex("\\s+"), " ")?.trim() ?: return null
        if (n in UNKNOWN) return null
        EXACT[n]?.let { return it }
        // "U/A 13+", "16+", "12A", "R18": the number is the minimum age.
        DIGITS.find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtMost(21) }
        return null
    }
}

object ParentalPolicy {
    /** G and PG. */
    const val DEFAULT_KIDS_MAX_AGE = 8

    /**
     * Kids: a title is allowed only if its label is known and at or under the profile's ceiling (an unknown label is
     * blocked). Adults: allowed, except at [restrictedFrom] and above, which asks for the PIN.
     */
    fun titleVerdict(profile: ProfileInfo, ageLabel: String?, restrictedFrom: Int = AgeLevels.RESTRICTED_FROM): Verdict {
        val level = AgeLevels.levelOf(ageLabel)
        return if (profile.isKids) {
            if (level != null && level <= profile.maxAgeLevel) Verdict.ALLOWED else Verdict.BLOCKED
        } else {
            if (level != null && level >= restrictedFrom) Verdict.LOCKED else Verdict.ALLOWED
        }
    }

    /**
     * Whether a category is listed for [profile]. Adult categories are hidden from everyone unless an adult profile
     * turned them on; a Kids profile sees only the categories an adult approved ([kidsAllowed], by id).
     */
    fun categoryVisible(profile: ProfileInfo, categoryId: String, categoryTitle: String, kidsAllowed: Set<String>): Boolean = when {
        CategoryRules.isAdult(categoryTitle) -> !profile.isKids && profile.showAdultCategories
        profile.isKids -> categoryId in kidsAllowed
        else -> true
    }

    /** Kids get no Live TV at first (channels cannot be vetted in advance). */
    fun liveAllowed(profile: ProfileInfo): Boolean = !profile.isKids

    /** Kids cannot open Settings or switch profiles without the PIN. */
    fun needsPinForSettings(profile: ProfileInfo): Boolean = profile.isKids
    fun needsPinToLeave(profile: ProfileInfo): Boolean = profile.isKids
}

object CategoryRules {
    private val ADULT = Regex("(?i)\\b(adults?|xxx|18\\+|porn|erotic|nsfw)\\b")
    private val KID_FRIENDLY = Regex("(?i)\\b(kids?|cartoons?|animation|animated|family|children)\\b")

    /** A category whose name marks adult content. */
    fun isAdult(title: String) = ADULT.containsMatchIn(title)

    /** A starting suggestion for a Kids profile's allow-list; an adult confirms it, it is never applied silently. */
    fun looksKidFriendly(title: String) = KID_FRIENDLY.containsMatchIn(title) && !isAdult(title)
}

/** When a watched title counts as finished (so it leaves Continue watching). */
object HistoryRules {
    const val NEAR_END_MILLIS = 45_000L

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs >= durationMs - NEAR_END_MILLIS
}
