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
