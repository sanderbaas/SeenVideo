package nl.baasmail.seenvideo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelName: String = "",
    val duration: String = "",
    val isWatched: Boolean = false,
    val isNew: Boolean = false,
    val publishedAt: Long,
    val playlistItemId: String? = null,
    val watchLaterItemId: String? = null
)
