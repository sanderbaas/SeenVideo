package nl.baasmail.seenvideo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

import androidx.room.Transaction

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY publishedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY publishedAt DESC")
    fun getVideosByChannel(channelId: String): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Query("UPDATE videos SET isWatched = :isWatched WHERE id = :videoId")
    suspend fun markAsWatched(videoId: String, isWatched: Boolean)

    @Query("UPDATE videos SET playlistItemId = :playlistItemId WHERE id = :videoId")
    suspend fun updatePlaylistItemId(videoId: String, playlistItemId: String?)

    @Query("UPDATE videos SET watchLaterItemId = :watchLaterItemId WHERE id = :videoId")
    suspend fun updateWatchLaterItemId(videoId: String, watchLaterItemId: String?)

    @Query("DELETE FROM videos WHERE channelId = :channelId")
    suspend fun deleteVideosByChannel(channelId: String)

    @Query("SELECT id FROM videos WHERE isWatched = 1")
    suspend fun getWatchedVideoIds(): List<String>

    @Query("SELECT id FROM videos")
    suspend fun getAllVideoIds(): List<String>

    @Query("UPDATE videos SET isNew = 0")
    suspend fun clearAllNewFlags()

    @Transaction
    suspend fun replaceVideosForChannel(channelId: String, videos: List<VideoEntity>) {
        deleteVideosByChannel(channelId)
        insertVideos(videos)
    }
}
