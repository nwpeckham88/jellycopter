package org.jellycopter.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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

        fun getDatabase(context: Context): JellycopterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JellycopterDatabase::class.java,
                    "jellycopter.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
