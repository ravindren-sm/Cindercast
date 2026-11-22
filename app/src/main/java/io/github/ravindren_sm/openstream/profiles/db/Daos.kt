package io.github.ravindren_sm.openstream.profiles.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileDao {
    @Insert suspend fun insert(profile: ProfileEntity): Long
    @Update suspend fun update(profile: ProfileEntity)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM profiles ORDER BY id") suspend fun all(): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun byId(id: Long): ProfileEntity?
    @Query("SELECT COUNT(*) FROM profiles") suspend fun count(): Int
}

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM household WHERE id = 1") suspend fun get(): HouseholdEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: HouseholdEntity)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE profileId = :profileId AND kind = :kind AND refId = :refId")
    suspend fun remove(profileId: Long, kind: String, refId: String)
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND kind = :kind ORDER BY addedAt DESC")
    suspend fun list(profileId: Long, kind: String): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun all(profileId: Long): List<FavoriteEntity>
    @Query("DELETE FROM favorites WHERE profileId = :profileId") suspend fun clear(profileId: Long)
    @Query("SELECT COUNT(*) FROM favorites WHERE profileId = :profileId AND kind = :kind AND refId = :refId")
    suspend fun count(profileId: Long, kind: String, refId: String): Int
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(h: HistoryEntity)
    @Query("SELECT * FROM history WHERE profileId = :profileId AND resumeKey = :key") suspend fun get(profileId: Long, key: String): HistoryEntity?
    @Query("SELECT * FROM history WHERE profileId = :profileId AND completed = 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun unfinished(profileId: Long, limit: Int): List<HistoryEntity>
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(profileId: Long, limit: Int): List<HistoryEntity>
    @Query("SELECT * FROM history WHERE profileId = :profileId AND completed = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun finished(profileId: Long, limit: Int): List<HistoryEntity>
    @Query("UPDATE history SET completed = 1, updatedAt = :now WHERE profileId = :profileId AND resumeKey = :key")
    suspend fun markWatched(profileId: Long, key: String, now: Long)
    /** Back to unwatched: the title starts again from the beginning. */
    @Query("UPDATE history SET completed = 0, positionMs = 0, updatedAt = :now WHERE profileId = :profileId AND resumeKey = :key")
    suspend fun markUnwatched(profileId: Long, key: String, now: Long)
    @Query("DELETE FROM history WHERE profileId = :profileId AND resumeKey = :key") suspend fun remove(profileId: Long, key: String)
    @Query("DELETE FROM history WHERE profileId = :profileId") suspend fun clear(profileId: Long)
}

@Dao
interface PinnedCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(c: PinnedCategoryEntity)
    @Query("DELETE FROM pinned_categories WHERE profileId = :profileId AND kind = :kind AND categoryId = :categoryId")
    suspend fun remove(profileId: Long, kind: String, categoryId: String)
    @Query("SELECT * FROM pinned_categories WHERE profileId = :profileId AND kind = :kind ORDER BY position")
    suspend fun list(profileId: Long, kind: String): List<PinnedCategoryEntity>
}

@Dao
interface KidsAllowedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(c: KidsAllowedCategoryEntity)
    @Query("DELETE FROM kids_allowed_categories WHERE profileId = :profileId AND categoryId = :categoryId")
    suspend fun remove(profileId: Long, categoryId: String)
    @Query("SELECT * FROM kids_allowed_categories WHERE profileId = :profileId ORDER BY title")
    suspend fun list(profileId: Long): List<KidsAllowedCategoryEntity>
}

@Dao
interface RecentSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: RecentSearchEntity)
    @Query("SELECT * FROM recent_searches WHERE profileId = :profileId ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recent(profileId: Long, limit: Int): List<RecentSearchEntity>
    @Query("DELETE FROM recent_searches WHERE profileId = :profileId") suspend fun clear(profileId: Long)
    @Query("DELETE FROM recent_searches WHERE profileId = :profileId AND text = :text") suspend fun remove(profileId: Long, text: String)
}

@Dao
interface CatalogIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTitles(items: List<CatalogTitleEntity>)
    @Query("SELECT COUNT(*) FROM catalog_titles") suspend fun titleCount(): Int
    /** Titles whose name starts with the text or has a word that does; [prefix] and [wordPrefix] are the LIKE patterns. */
    @Query("SELECT * FROM catalog_titles WHERE nameKey LIKE :prefix ESCAPE '\\' OR nameKey LIKE :wordPrefix ESCAPE '\\' LIMIT :limit")
    suspend fun match(prefix: String, wordPrefix: String, limit: Int): List<CatalogTitleEntity>
    @Query("DELETE FROM catalog_titles") suspend fun clearTitles()

    /**
     * One category's titles in a chosen order, with a minimum rating. [sort]: 1 newest added (the portal's order), 2 release year,
     * 3 best rated (IMDb, else Kinopoisk), 4 IMDb, 5 Kinopoisk, 6 name. Unrated titles come last in the rating orders, and are
     * kept or dropped by [keepUnrated] (1 or 0) when [minRating] is set.
     */
    @Query("""SELECT * FROM catalog_titles WHERE categoryId = :categoryId
        AND (:minRating IS NULL OR COALESCE(imdbRating, kinopoiskRating) >= :minRating
             OR (COALESCE(imdbRating, kinopoiskRating) IS NULL AND :keepUnrated = 1))
        ORDER BY
          CASE :sort WHEN 1 THEN ord END ASC,
          CASE :sort WHEN 2 THEN CAST(year AS INTEGER) END DESC,
          CASE :sort WHEN 3 THEN COALESCE(imdbRating, kinopoiskRating) END DESC,
          CASE :sort WHEN 4 THEN imdbRating END DESC,
          CASE :sort WHEN 5 THEN kinopoiskRating END DESC,
          CASE :sort WHEN 6 THEN nameKey END ASC,
          ord ASC
        LIMIT :limit OFFSET :offset""")
    suspend fun browse(categoryId: String, minRating: Double?, keepUnrated: Int, sort: Int, limit: Int, offset: Int): List<CatalogTitleEntity>

    @Query("""SELECT COUNT(*) FROM catalog_titles WHERE categoryId = :categoryId
        AND (:minRating IS NULL OR COALESCE(imdbRating, kinopoiskRating) >= :minRating
             OR (COALESCE(imdbRating, kinopoiskRating) IS NULL AND :keepUnrated = 1))""")
    suspend fun browseCount(categoryId: String, minRating: Double?, keepUnrated: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProgress(p: IndexProgressEntity)
    @Query("SELECT * FROM index_progress") suspend fun progress(): List<IndexProgressEntity>
    @Query("DELETE FROM index_progress") suspend fun clearProgress()
}
