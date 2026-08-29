package nl.baasmail.seenvideo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VideoEntity::class, ChannelEntity::class], version = 9)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao
}
