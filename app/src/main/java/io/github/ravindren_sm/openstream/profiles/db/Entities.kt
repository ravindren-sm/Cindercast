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
    // What the favorite needs to be opened again without asking the portal for the catalog entry. Labels and
    // commands only; the command is never printed.
    val playCmd: String? = null,
    /** A channel's number as the portal sends it. */
    val number: String? = null,
    val year: String? = null,
    val description: String? = null,
    val actors: String? = null,
    val director: String? = null,
    val genres: String? = null,
    val age: String? = null,
    val imdbRating: Double? = null,
    val kinopoiskRating: Double? = null,
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

/** A search the viewer ran or opened a result from, newest first in the suggestions. One row per distinct text. */
@Entity(
    tableName = "recent_searches",
    primaryKeys = ["profileId", "text"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId", "lastUsedAt")],
)
data class RecentSearchEntity(val profileId: Long, val text: String, val lastUsedAt: Long)

/**
 * One title of the portal's catalog, kept lean (no description or cast) so about 130,000 of them stay near 20 MB.
 * It exists to suggest and find titles instantly; opening one shows what the row holds. [nameKey] is the name lower-cased
 * with entities decoded. The portal's catalog is shared by every profile, so this table has no profile.
 */
@Entity(tableName = "catalog_titles", primaryKeys = ["kind", "id"], indices = [Index("categoryId")])
data class CatalogTitleEntity(
    val kind: String,
    val id: String,
    val name: String,
    val nameKey: String,
    val year: String?,
    val posterUri: String?,
    val cmd: String?,
    val age: String?,
    val categoryId: String,
    /** The portal's own ratings (null when it gave none or zero). The one shown is IMDb when present, else Kinopoisk. */
    val imdbRating: Double? = null,
    val kinopoiskRating: Double? = null,
    /** The genres the title lists (text as the portal sends it). */
    val genres: String? = null,
    /** Position in the category as the portal lists it, newest added first (0 is the newest). */
    @androidx.room.ColumnInfo(defaultValue = "0") val ord: Int = 0,
)

/** How far the title crawl has got in one category. */
@Entity(tableName = "index_progress")
data class IndexProgressEntity(
    @PrimaryKey val categoryId: String,
    val nextPage: Int,
    val totalItems: Int,
    val done: Boolean,
)

/**
 * What a profile watches, counted once per title the viewer has actually watched for a minute or more. [kind] is GENRE, LANGUAGE or
 * TITLE (a show or movie, keyed by its name with quality and language tags removed so versions count together). [label] is how it
 * is shown. Used to order search results and to show "most watched" in Settings. Stays on the device.
 */
@Entity(
    tableName = "viewing_stats",
    primaryKeys = ["profileId", "kind", "key"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = CASCADE)],
    indices = [Index("profileId", "kind")],
)
data class ViewingStatEntity(val profileId: Long, val kind: String, val key: String, val label: String, val count: Int, val lastAt: Long)

/**
 * What the optional online tag service said about one portal title. [tmdbId] is 0 when it was looked up and no confident match
 * was found (kept so the title is not asked about again). [certAge] is the minimum age of the certification chosen, [flagged]
 * says a genre or keyword made it unsuitable for Kids.
 */
@Entity(tableName = "title_tags", primaryKeys = ["kind", "id"], indices = [androidx.room.Index("imdbId")])
data class TitleTagEntity(
    val kind: String,
    val id: String,
    val tmdbId: Long,
    val certLabel: String?,
    val certAge: Int?,
    val genres: String?,
    val language: String?,
    val rating: Double?,
    val keywords: String?,
    val flagged: Boolean,
    val taggedAt: Long,
    /** The IMDb id (tt1234567) from the tag service; empty text when it has none (so the title is not asked about again), null before it was asked. */
    val imdbId: String? = null,
    /** The IMDb average rating read from IMDb's free non-commercial ratings file, for personal use only. Never uploaded or shared. */
    val imdbScore: Double? = null,
    /** Unused (a Rotten Tomatoes percentage; no source). Kept because SQLite here cannot drop columns. */
    val rtScore: Int? = null,
    /** Unused (reserved for a second source that was dropped). */
    val omdbAt: Long? = null,
)

/** The part of a tag a Kids profile needs. */
data class KidsTagRow(val id: String, val certAge: Int?, val flagged: Boolean)

