package org.jellycopter.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeckEntity::class,
        DeckProgressEntity::class,
        AchievementEntity::class,
        ChannelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JellycopterDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun achievementDao(): AchievementDao
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: JellycopterDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deck_progress` (
                        `deckId` TEXT NOT NULL PRIMARY KEY,
                        `currentCardId` TEXT NOT NULL,
                        `currentEpisodeId` TEXT NOT NULL,
                        `currentIndex` INTEGER NOT NULL,
                        `midwayResumeMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `lastWatchedTimestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channels` (
                        `channelId` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `showsJson` TEXT NOT NULL,
                        `interleaveStrategy` TEXT NOT NULL,
                        `audioNightMode` TEXT NOT NULL,
                        `skipBehavior` TEXT NOT NULL,
                        `resumeBehavior` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): JellycopterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JellycopterDatabase::class.java,
                    "jellycopter.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
