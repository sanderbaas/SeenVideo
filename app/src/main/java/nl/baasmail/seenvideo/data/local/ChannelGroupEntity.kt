package nl.baasmail.seenvideo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_groups")
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
