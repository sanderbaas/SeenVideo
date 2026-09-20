package nl.baasmail.seenvideo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoEntity::class, ChannelEntity::class, ChannelGroupEntity::class], version = 10)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new channel_groups table
                db.execSQL("CREATE TABLE IF NOT EXISTS `channel_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                
                // 2. Add the groupId column to the channels table
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `groupId` INTEGER DEFAULT NULL")
            }
        }
    }
}
