package io.github.ravindren_sm.openstream.profiles.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A person in the household. A Kids profile is limited by [maxAgeLevel]; the level is ignored for adults. */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val isKids: Boolean,
    val maxAgeLevel: Int,
    /** Adults only: show categories whose names mark adult content (still behind the PIN). */
    val showAdultCategories: Boolean = false,
    val createdAt: Long,
)

/** The one household PIN, kept as a salted hash plus its failed-attempt state. A single row (id 1). */
@Entity(tableName = "household")
data class HouseholdEntity(
    @PrimaryKey val id: Int = 1,
    val pinAlgorithm: String?,
    val pinIterations: Int,
    val pinSalt: ByteArray?,
    val pinHash: ByteArray?,
    val failedAttempts: Int,
    val lockedUntil: Long,
)

private const val CASCADE = ForeignKey.CASCADE

/** A favorite channel, movie or show of one profile. [kind] is CHANNEL, MOVIE or SHOW; [refId] is the portal's id. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "kind", "refId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId")],
)
data class FavoriteEntity(
    val profileId: Long,
    val kind: String,
    val refId: String,
    val name: String,
    val posterUri: String?,
    val addedAt: Long,
)

/** What a profile has watched: one row per resume key (a movie, or one episode), newest first on Home. */
@Entity(
    tableName = "history",
    primaryKeys = ["profileId", "resumeKey"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId", "updatedAt")],
)
data class HistoryEntity(
    val profileId: Long,
    val resumeKey: String,
    /** MOVIE or EPISODE. */
    val kind: String,
    val titleId: String,
    val title: String,
    val subtitle: String?,
    val posterUri: String?,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
    /** The portal command that plays this title again (a movie's, or an episode's chosen file). Never printed. */
    val playCmd: String? = null,
)

/** A category a profile pinned, which Home turns into a "new in ..." row. [kind] is MOVIES or SHOWS. */
@Entity(
    tableName = "pinned_categories",
    primaryKeys = ["profileId", "kind", "categoryId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId")],
)
data class PinnedCategoryEntity(
    val profileId: Long,
    val kind: String,
    val categoryId: String,
    val title: String,
    val position: Int,
)

/** A VOD category an adult has approved for a Kids profile. A Kids profile sees only these. */
@Entity(
    tableName = "kids_allowed_categories",
    primaryKeys = ["profileId", "categoryId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId")],
)
data class KidsAllowedCategoryEntity(
    val profileId: Long,
    val categoryId: String,
    val title: String,
)
