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
        PinnedCategoryEntity::class, KidsAllowedCategoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OpenStreamDb : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun household(): HouseholdDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun pinned(): PinnedCategoryDao
    abstract fun kidsAllowed(): KidsAllowedDao

    companion object {
        @Volatile private var instance: OpenStreamDb? = null

        /** Version 2: a history row remembers the command that plays it again, so Home can resume it. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE history ADD COLUMN playCmd TEXT") }
        }

        fun get(context: Context): OpenStreamDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OpenStreamDb::class.java, "openstream.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        /** An in-memory database for tests. */
        fun inMemory(context: Context): OpenStreamDb =
            Room.inMemoryDatabaseBuilder(context.applicationContext, OpenStreamDb::class.java).build()
    }
}
