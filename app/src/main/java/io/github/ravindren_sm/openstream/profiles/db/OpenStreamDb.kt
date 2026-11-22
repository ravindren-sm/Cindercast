package io.github.ravindren_sm.openstream.profiles.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's local database: profiles, the household PIN, and each profile's favorites, history and pinned
 * categories. Everything stays on the device. Schema changes need a version bump and a migration; the exported
 * schema in app/schemas shows each version.
 */
@Database(
    entities = [
        ProfileEntity::class, HouseholdEntity::class, FavoriteEntity::class, HistoryEntity::class,
        PinnedCategoryEntity::class, KidsAllowedCategoryEntity::class, RecentSearchEntity::class,
        CatalogTitleEntity::class, IndexProgressEntity::class, ViewingStatEntity::class, TitleTagEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class OpenStreamDb : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun household(): HouseholdDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun pinned(): PinnedCategoryDao
    abstract fun kidsAllowed(): KidsAllowedDao
    abstract fun recentSearches(): RecentSearchDao
    abstract fun catalogIndex(): CatalogIndexDao
    abstract fun viewingStats(): ViewingStatDao
    abstract fun titleTags(): TitleTagDao

    companion object {
        @Volatile private var instance: OpenStreamDb? = null

        /** Version 2: a history row remembers the command that plays it again, so Home can resume it. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE history ADD COLUMN playCmd TEXT") }
        }

        /** Version 3: a favorite keeps the command and the catalog details it needs to be opened again. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in listOf("playCmd TEXT", "number TEXT", "year TEXT", "description TEXT", "actors TEXT", "director TEXT", "genres TEXT", "age TEXT", "imdbRating REAL", "kinopoiskRating REAL")) {
                    db.execSQL("ALTER TABLE favorites ADD COLUMN $column")
                }
            }
        }

        /** Version 4: recent searches and the local title index (with its crawl progress). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`profileId` INTEGER NOT NULL, `text` TEXT NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `text`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_searches_profileId_lastUsedAt` ON `recent_searches` (`profileId`, `lastUsedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_titles` (`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `year` TEXT, `posterUri` TEXT, `cmd` TEXT, `age` TEXT, `categoryId` TEXT NOT NULL, PRIMARY KEY(`kind`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_titles_categoryId` ON `catalog_titles` (`categoryId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `index_progress` (`categoryId` TEXT NOT NULL, `nextPage` INTEGER NOT NULL, `totalItems` INTEGER NOT NULL, `done` INTEGER NOT NULL, PRIMARY KEY(`categoryId`))")
            }
        }

        /** Version 5: index rows carry ratings and the portal's order, so a category can be sorted and filtered on the device. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_titles ADD COLUMN imdbRating REAL")
                db.execSQL("ALTER TABLE catalog_titles ADD COLUMN kinopoiskRating REAL")
                db.execSQL("ALTER TABLE catalog_titles ADD COLUMN ord INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE catalog_titles ADD COLUMN genres TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `viewing_stats` (`profileId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `count` INTEGER NOT NULL, `lastAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `kind`, `key`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_viewing_stats_profileId_kind` ON `viewing_stats` (`profileId`, `kind`)")
            }
        }

        /** Version 6: what the optional online tag service said about each title (age certification, genres, keywords). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `title_tags` (`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `certLabel` TEXT, `certAge` INTEGER, `genres` TEXT, `language` TEXT, `rating` REAL, `keywords` TEXT, `flagged` INTEGER NOT NULL, `taggedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `id`))")
            }
        }

        /** Version 7: a tag keeps the IMDb id, and the optional second source's IMDb score and Rotten Tomatoes percentage. */
        /** An index on imdbId: storing the IMDb scores updates one row per id, which scanned the whole table each time (about 14 rows/s on the stick). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_tags_imdbId` ON `title_tags` (`imdbId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE title_tags ADD COLUMN imdbId TEXT")
                db.execSQL("ALTER TABLE title_tags ADD COLUMN imdbScore REAL")
                db.execSQL("ALTER TABLE title_tags ADD COLUMN rtScore INTEGER")
                db.execSQL("ALTER TABLE title_tags ADD COLUMN omdbAt INTEGER")
            }
        }

        fun get(context: Context): OpenStreamDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OpenStreamDb::class.java, "openstream.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
        }

        /** An in-memory database for tests. */
        fun inMemory(context: Context): OpenStreamDb =
            Room.inMemoryDatabaseBuilder(context.applicationContext, OpenStreamDb::class.java).build()
    }
}
