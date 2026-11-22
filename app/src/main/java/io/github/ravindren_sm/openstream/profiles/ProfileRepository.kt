package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.data.ResumePolicy
import io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity
import io.github.ravindren_sm.openstream.profiles.db.HistoryEntity
import io.github.ravindren_sm.openstream.profiles.db.HouseholdEntity
import io.github.ravindren_sm.openstream.profiles.db.KidsAllowedCategoryEntity
import io.github.ravindren_sm.openstream.profiles.db.OpenStreamDb
import io.github.ravindren_sm.openstream.profiles.db.PinnedCategoryEntity
import io.github.ravindren_sm.openstream.profiles.db.ProfileEntity
import io.github.ravindren_sm.openstream.profiles.db.ViewingStatEntity

/** The kinds of things a profile can favorite or pin. */
object Kinds {
    const val CHANNEL = "CHANNEL"
    const val MOVIE = "MOVIE"
    const val SHOW = "SHOW"
    const val EPISODE = "EPISODE"
    const val MOVIES = "MOVIES"
}

/** What to count when a title has been watched for a minute. */
class WatchInfo(val titleKey: String, val label: String, val genres: List<String>, val languages: Set<String>)

/** A title counts as watched after this much playing. */
const val WATCH_THRESHOLD_MS = 60_000L

/** Stores the household PIN record in the database's single `household` row. */
class RoomPinStore(private val db: OpenStreamDb) : PinStore {
    override suspend fun load(): PinRecord? {
        val row = db.household().get() ?: return null
        val algorithm = row.pinAlgorithm ?: return null
        val salt = row.pinSalt ?: return null
        val hash = row.pinHash ?: return null
        return PinRecord(algorithm, row.pinIterations, salt, hash, row.failedAttempts, row.lockedUntil)
    }

    override suspend fun save(record: PinRecord) {
        db.household().put(
            HouseholdEntity(1, record.algorithm, record.iterations, record.salt, record.hash, record.failures, record.lockedUntil),
        )
    }
}

/**
 * Everything the app remembers per person: profiles, the Kids allow-list, favorites, watch history and pinned
 * categories. Every call suspends (Room), so call it from a coroutine, not the main thread. Deleting a profile
 * deletes all of its data.
 */
class ProfileRepository(private val db: OpenStreamDb, private val nowMillis: () -> Long = System::currentTimeMillis) {
    val pins = PinService(RoomPinStore(db))

    // ---- profiles ----

    suspend fun profiles(): List<ProfileInfo> = db.profiles().all().map { it.info() }
    /** The first profile, made on first use so history has an owner before the picker exists. */
    suspend fun ensureDefaultProfile(): ProfileInfo = profiles().firstOrNull() ?: create("Family", 0, isKids = false)
    suspend fun profile(id: Long): ProfileInfo? = db.profiles().byId(id)?.info()
    suspend fun count(): Int = db.profiles().count()

    suspend fun create(
        name: String, colorIndex: Int, isKids: Boolean,
        maxAgeLevel: Int = ParentalPolicy.DEFAULT_KIDS_MAX_AGE, showAdultCategories: Boolean = false,
    ): ProfileInfo {
        require(name.isNotBlank()) { "A profile needs a name" }
        val entity = ProfileEntity(
            name = name.trim(), colorIndex = colorIndex, isKids = isKids, maxAgeLevel = maxAgeLevel,
            // A Kids profile never shows adult categories, whatever was asked.
            showAdultCategories = showAdultCategories && !isKids, createdAt = nowMillis(),
        )
        return entity.copy(id = db.profiles().insert(entity)).info()
    }

    suspend fun update(profile: ProfileInfo, colorIndex: Int? = null) {
        val old = db.profiles().byId(profile.id) ?: return
        db.profiles().update(
            old.copy(
                name = profile.name.trim().ifEmpty { old.name }, isKids = profile.isKids, maxAgeLevel = profile.maxAgeLevel,
                showAdultCategories = profile.showAdultCategories && !profile.isKids, colorIndex = colorIndex ?: old.colorIndex,
            ),
        )
    }

    suspend fun delete(id: Long) = db.profiles().delete(id)

    private fun ProfileEntity.info() = ProfileInfo(id, name, isKids, maxAgeLevel, showAdultCategories, colorIndex)

    // ---- Kids allow-list ----

    suspend fun kidsAllowed(profileId: Long): Set<String> = db.kidsAllowed().list(profileId).mapTo(HashSet()) { it.categoryId }

    suspend fun allowKidsCategory(profileId: Long, categoryId: String, title: String) {
        if (CategoryRules.isAdult(title)) return   // never, whatever the caller asks
        db.kidsAllowed().put(KidsAllowedCategoryEntity(profileId, categoryId, title))
    }

    suspend fun disallowKidsCategory(profileId: Long, categoryId: String) = db.kidsAllowed().remove(profileId, categoryId)

    /**
     * Adds the categories whose names look kid-friendly. Call it only from an adult's confirmed action ("use suggested
     * categories"), never silently. Returns how many were added.
     */
    suspend fun seedKidsAllowed(profileId: Long, categories: List<Pair<String, String>>): Int {
        var added = 0
        val have = kidsAllowed(profileId)
        for ((id, title) in categories) {
            if (id !in have && CategoryRules.looksKidFriendly(title)) { allowKidsCategory(profileId, id, title); added++ }
        }
        return added
    }

    // ---- favorites ----

    suspend fun setFavorite(profileId: Long, kind: String, refId: String, name: String, posterUri: String?, favorite: Boolean, details: FavoriteEntity? = null) {
        // [details] carries the optional columns (command, catalog details); its key fields are replaced by the ones given.
        if (favorite) db.favorites().put((details ?: FavoriteEntity(profileId, kind, refId, name, posterUri, 0)).copy(
            profileId = profileId, kind = kind, refId = refId, name = name, posterUri = posterUri, addedAt = nowMillis(),
        ))
        else db.favorites().remove(profileId, kind, refId)
    }
    suspend fun clearFavorites(profileId: Long) = db.favorites().clear(profileId)
    suspend fun allFavorites(profileId: Long): List<FavoriteEntity> = db.favorites().all(profileId)

    suspend fun isFavorite(profileId: Long, kind: String, refId: String) = db.favorites().count(profileId, kind, refId) > 0
    suspend fun favorites(profileId: Long, kind: String): List<FavoriteEntity> = db.favorites().list(profileId, kind)

    // ---- viewing stats ----

    /** Counts one watched title: its name key, each of its genres and each of its languages. */
    suspend fun recordWatch(profileId: Long, w: WatchInfo) {
        val now = nowMillis()
        suspend fun bump(kind: String, key: String, label: String) {
            if (db.viewingStats().bump(profileId, kind, key, label, now) == 0) db.viewingStats().insert(ViewingStatEntity(profileId, kind, key, label, 1, now))
        }
        bump("TITLE", w.titleKey, w.label)
        w.genres.forEach { bump("GENRE", it, it) }
        w.languages.forEach { bump("LANGUAGE", it, it) }
    }

    suspend fun topStats(profileId: Long, kind: String, limit: Int = 5): List<ViewingStatEntity> = db.viewingStats().top(profileId, kind, limit)
    suspend fun clearStats(profileId: Long) = db.viewingStats().clear(profileId)

    /** The profile's viewing as scoring input for search ordering. */
    suspend fun personalization(profileId: Long): io.github.ravindren_sm.openstream.data.Personalization {
        if (profileId <= 0) return io.github.ravindren_sm.openstream.data.Personalization()
        val g = db.viewingStats().top(profileId, "GENRE", 60).associate { it.key to it.count }
        val l = db.viewingStats().top(profileId, "LANGUAGE", 30).associate { it.key to it.count }
        val t = db.viewingStats().top(profileId, "TITLE", 400).associate { it.key to it.count }
        return io.github.ravindren_sm.openstream.data.Personalization(g, l, t)
    }

    // ---- recent searches ----

    /** Remembers a search text (trimmed, at least two characters); the newest use moves it to the front. */
    suspend fun recordSearch(profileId: Long, text: String) {
        val t = text.trim()
        if (t.length >= 2 && profileId > 0) db.recentSearches().put(io.github.ravindren_sm.openstream.profiles.db.RecentSearchEntity(profileId, t, nowMillis()))
    }
    suspend fun recentSearches(profileId: Long, limit: Int = 8): List<String> = db.recentSearches().recent(profileId, limit).map { it.text }
    suspend fun clearRecentSearches(profileId: Long) = db.recentSearches().clear(profileId)

    // ---- watch history ----

    /**
     * Records where a profile is in a title. A title that has barely started (under the resume minimum) is not
     * recorded, so a quick look does not clutter Continue watching; one watched to within 45 s of the end is marked
     * completed and leaves Continue watching.
     */
    suspend fun recordProgress(
        profileId: Long, resumeKey: String, kind: String, titleId: String, title: String, subtitle: String?,
        posterUri: String?, positionMs: Long, durationMs: Long, playCmd: String? = null, watch: WatchInfo? = null,
    ) {
        val existing = db.history().get(profileId, resumeKey)
        if (existing == null && positionMs < ResumePolicy.MIN_MILLIS) return
        // A title counts toward the viewing stats the first time it passes a minute of watching.
        if (watch != null && positionMs >= WATCH_THRESHOLD_MS && (existing == null || existing.positionMs < WATCH_THRESHOLD_MS)) recordWatch(profileId, watch)
        db.history().put(
            HistoryEntity(
                profileId, resumeKey, kind, titleId, title, subtitle, posterUri, positionMs, durationMs,
                completed = HistoryRules.isCompleted(positionMs, durationMs), updatedAt = nowMillis(), playCmd = playCmd,
            ),
        )
    }

    suspend fun continueWatching(profileId: Long, limit: Int = 14): List<HistoryEntity> = db.history().unfinished(profileId, limit)
    /** Titles watched to the end, newest first (Home's Recently watched row). */
    suspend fun recentlyFinished(profileId: Long, limit: Int = 14): List<HistoryEntity> = db.history().finished(profileId, limit)
    suspend fun setWatched(profileId: Long, resumeKey: String, watched: Boolean) =
        if (watched) db.history().markWatched(profileId, resumeKey, nowMillis()) else db.history().markUnwatched(profileId, resumeKey, nowMillis())
    suspend fun recentlyWatched(profileId: Long, limit: Int = 14): List<HistoryEntity> = db.history().recent(profileId, limit)
    suspend fun forget(profileId: Long, resumeKey: String) = db.history().remove(profileId, resumeKey)
    suspend fun clearHistory(profileId: Long) = db.history().clear(profileId)

    // ---- pinned categories (the "new in ..." rows on Home) ----

    suspend fun pinCategory(profileId: Long, kind: String, categoryId: String, title: String) {
        val position = db.pinned().list(profileId, kind).size
        db.pinned().put(PinnedCategoryEntity(profileId, kind, categoryId, title, position))
    }

    suspend fun unpinCategory(profileId: Long, kind: String, categoryId: String) = db.pinned().remove(profileId, kind, categoryId)
    suspend fun pinnedCategories(profileId: Long, kind: String): List<PinnedCategoryEntity> = db.pinned().list(profileId, kind)
}
