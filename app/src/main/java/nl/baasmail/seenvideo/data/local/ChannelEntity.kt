package nl.baasmail.seenvideo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String, // YouTube Channel ID
    val name: String,
    val handle: String = "",
    val uploadsPlaylistId: String = "",
    val showThumbnails: Boolean = true,
    val showShorts: Boolean = false,
    val showOnHome: Boolean = true,
    val blurTitles: Boolean = false,
    val safeKeywords: String = "", // Comma-separated
    val groupId: Long? = null
)
