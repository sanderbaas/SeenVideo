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
) {
    fun isShortVideo(): Boolean {
        if (title.contains("shorts", ignoreCase = true)) return true
        if (duration.isEmpty()) return false
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        val totalSeconds = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
        return totalSeconds in 1..180
    }
}
