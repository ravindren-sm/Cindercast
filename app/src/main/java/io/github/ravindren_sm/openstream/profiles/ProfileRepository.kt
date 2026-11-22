package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.data.ResumePolicy
import io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity
import io.github.ravindren_sm.openstream.profiles.db.HistoryEntity
import io.github.ravindren_sm.openstream.profiles.db.HouseholdEntity
import io.github.ravindren_sm.openstream.profiles.db.KidsAllowedCategoryEntity
import io.github.ravindren_sm.openstream.profiles.db.OpenStreamDb
import io.github.ravindren_sm.openstream.profiles.db.PinnedCategoryEntity
import io.github.ravindren_sm.openstream.profiles.db.ProfileEntity

/** The kinds of things a profile can favorite or pin. */
object Kinds {
    const val CHANNEL = "CHANNEL"
    const val MOVIE = "MOVIE"
    const val SHOW = "SHOW"
    const val EPISODE = "EPISODE"
    const val MOVIES = "MOVIES"
}

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

    private fun ProfileEntity.info() = ProfileInfo(id, name, isKids, maxAgeLevel, showAdultCategories)

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

    suspend fun setFavorite(profileId: Long, kind: String, refId: String, name: String, posterUri: String?, favorite: Boolean) {
        if (favorite) db.favorites().put(FavoriteEntity(profileId, kind, refId, name, posterUri, nowMillis()))
        else db.favorites().remove(profileId, kind, refId)
    }

    suspend fun isFavorite(profileId: Long, kind: String, refId: String) = db.favorites().count(profileId, kind, refId) > 0
    suspend fun favorites(profileId: Long, kind: String): List<FavoriteEntity> = db.favorites().list(profileId, kind)

    // ---- watch history ----

    /**
     * Records where a profile is in a title. A title that has barely started (under the resume minimum) is not
     * recorded, so a quick look does not clutter Continue watching; one watched to within 45 s of the end is marked
     * completed and leaves Continue watching.
     */
    suspend fun recordProgress(
        profileId: Long, resumeKey: String, kind: String, titleId: String, title: String, subtitle: String?,
        posterUri: String?, positionMs: Long, durationMs: Long,
    ) {
        val existing = db.history().get(profileId, resumeKey)
        if (existing == null && positionMs < ResumePolicy.MIN_MILLIS) return
        db.history().put(
            HistoryEntity(
                profileId, resumeKey, kind, titleId, title, subtitle, posterUri, positionMs, durationMs,
                completed = HistoryRules.isCompleted(positionMs, durationMs), updatedAt = nowMillis(),
            ),
        )
    }

    suspend fun continueWatching(profileId: Long, limit: Int = 14): List<HistoryEntity> = db.history().unfinished(profileId, limit)
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
