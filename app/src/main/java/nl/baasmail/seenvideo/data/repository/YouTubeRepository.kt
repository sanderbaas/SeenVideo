package nl.baasmail.seenvideo.data.repository

import android.text.Html
import android.text.format.DateUtils
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.baasmail.seenvideo.BuildConfig
import nl.baasmail.seenvideo.data.local.ChannelDao
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.VideoDao
import nl.baasmail.seenvideo.data.local.VideoEntity
import nl.baasmail.seenvideo.data.remote.*
import nl.baasmail.seenvideo.ui.auth.AuthManager
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val apiService: YouTubeApiService,
    private val videoDao: VideoDao,
    private val channelDao: ChannelDao,
    private val authManager: AuthManager
) {
    private val apiKey = BuildConfig.YOUTUBE_API_KEY
    private var syncPlaylistId: String? = null
    private var watchLaterPlaylistId: String? = null

    companion object {
        const val PLAYLIST_WATCHED_NEW = "SeenVideo Watched"
        const val PLAYLIST_WATCH_LATER_NEW = "SeenVideo Watch Later"
        const val PLAYLIST_WATCHED_OLD = "MyYouTube Watched"
        const val PLAYLIST_WATCH_LATER_OLD = "MyYouTube Watch Later"
    }

    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()

    // Store nextPageToken per channel for infinite scrolling
    private val nextPageTokens = ConcurrentHashMap<String, String>()

    suspend fun addChannelByHandle(handle: String): Boolean {
        Log.d("YouTubeRepository", "Adding channel: $handle")
        val response = try {
            apiService.getChannelDetails(handle = handle, apiKey = apiKey)
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "API Call failed for $handle", e)
            return false
        }
        
        val channel = response.items?.firstOrNull() ?: return false
        
        channelDao.insertChannel(
            ChannelEntity(
                id = channel.id,
                name = Html.fromHtml(channel.snippet.title, Html.FROM_HTML_MODE_LEGACY).toString(),
                handle = handle,
                uploadsPlaylistId = channel.contentDetails?.relatedPlaylists?.uploads ?: ""
            )
        )
        refreshVideosForChannel(channel.id)
        return true
    }

    suspend fun refreshVideosForChannel(channelId: String) {
        if (authManager.accessToken.value != null) {
            try {
                syncFromYouTubePlaylists()
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Playlist sync failed during refresh", e)
            }
        }
        nextPageTokens.remove(channelId)
        loadVideos(channelId, isRefresh = true)
    }

    suspend fun loadMoreVideos(channelId: String? = null) {
        val channels = allChannels.first()
        coroutineScope {
            if (channelId != null) {
                if (nextPageTokens.containsKey(channelId)) {
                    loadVideos(channelId, isRefresh = false)
                }
            } else {
                channels.map { channel ->
                    async {
                        if (nextPageTokens.containsKey(channel.id)) {
                            loadVideos(channel.id, isRefresh = false)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun loadVideos(channelId: String, isRefresh: Boolean) {
        val channels = allChannels.first()
        val channelSettings = channels.find { it.id == channelId } ?: return
        val playlistId = channelSettings.uploadsPlaylistId.ifEmpty { "UU" + channelId.removePrefix("UC") }
        
        var currentToken: String? = if (isRefresh) null else nextPageTokens[channelId]
        var hasMore = true
        var attempts = 0
        val maxAttempts = 3

        while (hasMore && attempts < maxAttempts) {
            attempts++
            val playlistResponse = try {
                apiService.getPlaylistItems(
                    playlistId = playlistId,
                    pageToken = if (currentToken.isNullOrEmpty()) null else currentToken,
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Error fetching playlist items for $channelId", e)
                break
            }
            
            if (playlistResponse.items.isNullOrEmpty()) {
                if (isRefresh) nextPageTokens.remove(channelId)
                break
            }

            currentToken = playlistResponse.nextPageToken
            if (!currentToken.isNullOrEmpty()) {
                nextPageTokens[channelId] = currentToken
            } else {
                nextPageTokens.remove(channelId)
                hasMore = false
            }

            val videoIdsString = playlistResponse.items.map { it.snippet.resourceId.videoId }.joinToString(",")
            val detailsResponse = try {
                apiService.getVideoDetails(ids = videoIdsString, apiKey = apiKey)
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Error fetching video details for $channelId", e)
                break
            }
            
            val existingVideos = videoDao.getAllVideos().first()
            val watchedIds = existingVideos.filter { it.isWatched }.associateBy({ it.id }, { it.playlistItemId })
            val watchLaterIds = existingVideos.filter { it.watchLaterItemId != null }.associateBy({ it.id }, { it.watchLaterItemId })

            // Count how many of these videos are actually new to our database
            var brandNewVideosCount = 0

            val validVideos = detailsResponse.items.filter { item ->
                val duration = Duration.parse(item.contentDetails.duration)
                val playlistItem = playlistResponse.items.find { it.snippet.resourceId.videoId == item.id }
                val title = playlistItem?.snippet?.title ?: ""
                val isShort = duration.seconds <= 180 || title.contains("shorts", ignoreCase = true)
                channelSettings.showShorts || !isShort
            }.map { item ->
                val playlistItem = playlistResponse.items.find { it.snippet.resourceId.videoId == item.id }
                val thumbnails = playlistItem?.snippet?.thumbnails
                val bestThumbnailUrl = thumbnails?.high?.url ?: thumbnails?.medium?.url ?: thumbnails?.default?.url ?: ""
                
                if (existingVideos.none { it.id == item.id }) {
                    brandNewVideosCount++
                }

                VideoEntity(
                    id = item.id,
                    title = Html.fromHtml(playlistItem?.snippet?.title ?: "", Html.FROM_HTML_MODE_LEGACY).toString(),
                    thumbnailUrl = bestThumbnailUrl,
                    channelId = channelId,
                    channelName = channelSettings.name,
                    duration = formatDuration(item.contentDetails.duration),
                    isWatched = watchedIds.containsKey(item.id),
                    playlistItemId = watchedIds[item.id],
                    watchLaterItemId = watchLaterIds[item.id],
                    publishedAt = playlistItem?.snippet?.publishedAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L
                )
            }

            if (validVideos.isNotEmpty()) {
                videoDao.insertVideos(validVideos)
            }
            
            // If we are refreshing and found NO brand new videos on this page, 
            // it means we are already up to date and can stop.
            // If we ARE finding brand new videos, we might want to keep going for a bit 
            // (especially if the app was closed for a long time), but for a refresh, 1-2 pages is usually enough.
            if (isRefresh && brandNewVideosCount == 0) break
            if (isRefresh && attempts >= 1) break // For refresh, don't go too deep even if there are many new ones
        }
    }

    private fun formatDuration(isoDuration: String): String {
        val d = Duration.parse(isoDuration)
        val totalSeconds = d.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    fun getRelativeTime(timestamp: Long): String {
        return DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    suspend fun refreshAll() {
        if (authManager.accessToken.value != null) {
            syncFromYouTubePlaylists()
        }

        val channels = allChannels.first()
        coroutineScope {
            channels.map { channel ->
                async {
                    try {
                        loadVideos(channel.id, isRefresh = true)
                    } catch (e: Exception) {
                        Log.e("YouTubeRepository", "Failed to refresh ${channel.name}", e)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun syncFromYouTubePlaylists() {
        coroutineScope {
            launch { syncPlaylist(PLAYLIST_WATCHED_NEW, PLAYLIST_WATCHED_OLD, isWatchedList = true) }
            launch { syncPlaylist(PLAYLIST_WATCH_LATER_NEW, PLAYLIST_WATCH_LATER_OLD, isWatchedList = false) }
        }
    }

    private suspend fun syncPlaylist(newTitle: String, oldTitle: String, isWatchedList: Boolean) {
        try {
            val playlistId = getOrCreatePlaylist(newTitle, oldTitle) ?: return
            val ytVideos = mutableMapOf<String, String>() // videoId -> playlistItemId
            var pageToken: String? = null
            
            do {
                val response = apiService.getPlaylistItems(
                    playlistId = playlistId, 
                    pageToken = pageToken, 
                    maxResults = 50,
                    apiKey = apiKey
                )
                response.items?.forEach { item ->
                    ytVideos[item.snippet.resourceId.videoId] = item.id
                }
                pageToken = response.nextPageToken
            } while (pageToken != null)

            val existingVideos = videoDao.getAllVideos().first()
            
            // 1. Find videos that are on YT but are NOT in our local database OR are incomplete
            val idsToFetch = ytVideos.keys.filter { id ->
                val local = existingVideos.find { it.id == id }
                local == null || local.publishedAt == 0L || local.channelName.isEmpty()
            }

            if (idsToFetch.isNotEmpty()) {
                idsToFetch.chunked(50).forEach { batch ->
                    val details = apiService.getVideoDetails(ids = batch.joinToString(","), apiKey = apiKey)
                    val newEntities = details.items.map { item ->
                        VideoEntity(
                            id = item.id,
                            title = Html.fromHtml(item.snippet?.title ?: "Onbekende video", Html.FROM_HTML_MODE_LEGACY).toString(),
                            thumbnailUrl = item.snippet?.thumbnails?.high?.url ?: item.snippet?.thumbnails?.medium?.url ?: "",
                            channelId = item.snippet?.channelId ?: "", 
                            channelName = item.snippet?.channelTitle ?: "Onbekend kanaal", 
                            duration = formatDuration(item.contentDetails.duration),
                            publishedAt = item.snippet?.publishedAt?.let { 
                                try { Instant.parse(it).toEpochMilli() } catch (e: Exception) { 0L }
                            } ?: 0L,
                            isWatched = isWatchedList,
                            playlistItemId = if (isWatchedList) ytVideos[item.id] else null,
                            watchLaterItemId = if (!isWatchedList) ytVideos[item.id] else null
                        )
                    }
                    videoDao.insertVideos(newEntities)
                }
            }

            // 2. Update status for existing videos
            val localVideos = videoDao.getAllVideos().first()
            localVideos.forEach { video ->
                val ytPlaylistItemId = ytVideos[video.id]
                val isOnYt = ytPlaylistItemId != null
                
                if (isWatchedList) {
                    if (video.isWatched != isOnYt || video.playlistItemId != ytPlaylistItemId) {
                        videoDao.markAsWatched(video.id, isOnYt)
                        videoDao.updatePlaylistItemId(video.id, ytPlaylistItemId)
                    }
                } else {
                    if (video.watchLaterItemId != ytPlaylistItemId) {
                        videoDao.updateWatchLaterItemId(video.id, ytPlaylistItemId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Failed to sync $newTitle", e)
        }
    }

    suspend fun syncLocalWatchedToYouTube() {
        if (authManager.accessToken.value == null) return
        try {
            val localVideos = videoDao.getAllVideos().first()
            
            // Sync Watched
            localVideos.filter { it.isWatched && it.playlistItemId == null }.forEach { 
                syncWatchedStatusToYouTube(it.id, true) 
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Failed to sync local watched to YouTube", e)
        }
    }

    suspend fun markAsWatched(videoId: String, isWatched: Boolean) {
        videoDao.markAsWatched(videoId, isWatched)
        if (authManager.accessToken.value != null) {
            syncWatchedStatusToYouTube(videoId, isWatched)
        }
    }

    private suspend fun syncWatchedStatusToYouTube(videoId: String, isWatched: Boolean) {
        try {
            val playlistId = getOrCreatePlaylist(PLAYLIST_WATCHED_NEW, PLAYLIST_WATCHED_OLD) ?: return
            if (isWatched) {
                val response = apiService.insertPlaylistItem(
                    body = PlaylistItemInsertBody(snippet = PlaylistItemInsertSnippet(playlistId = playlistId, resourceId = YouTubeResourceId(videoId = videoId)))
                )
                videoDao.updatePlaylistItemId(videoId, response.id)
            } else {
                val video = videoDao.getAllVideos().first().find { it.id == videoId }
                video?.playlistItemId?.let { 
                    apiService.deletePlaylistItem(it)
                    videoDao.updatePlaylistItemId(videoId, null)
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Failed to sync watched status", e)
        }
    }

    suspend fun toggleWatchLater(videoId: String, addToWatchLater: Boolean) {
        if (authManager.accessToken.value != null) {
            syncWatchLaterStatusToYouTube(videoId, addToWatchLater)
        }
    }

    private suspend fun syncWatchLaterStatusToYouTube(videoId: String, addToWatchLater: Boolean) {
        try {
            val playlistId = getOrCreatePlaylist(PLAYLIST_WATCH_LATER_NEW, PLAYLIST_WATCH_LATER_OLD) ?: return
            if (addToWatchLater) {
                val response = apiService.insertPlaylistItem(
                    body = PlaylistItemInsertBody(snippet = PlaylistItemInsertSnippet(playlistId = playlistId, resourceId = YouTubeResourceId(videoId = videoId)))
                )
                videoDao.updateWatchLaterItemId(videoId, response.id)
            } else {
                val video = videoDao.getAllVideos().first().find { it.id == videoId }
                video?.watchLaterItemId?.let { 
                    apiService.deletePlaylistItem(it)
                    videoDao.updateWatchLaterItemId(videoId, null)
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Failed to sync watch later status", e)
        }
    }

    private suspend fun getOrCreatePlaylist(newTitle: String, oldTitle: String): String? {
        if (newTitle == PLAYLIST_WATCHED_NEW && syncPlaylistId != null) return syncPlaylistId
        if (newTitle == PLAYLIST_WATCH_LATER_NEW && watchLaterPlaylistId != null) return watchLaterPlaylistId
        
        return try {
            val playlists = apiService.getMyPlaylists().items ?: emptyList()
            
            // 1. Check for the new title
            var existing = playlists.find { it.snippet.title == newTitle }
            
            // 2. Fallback to old title if new one doesn't exist
            if (existing == null) {
                existing = playlists.find { it.snippet.title == oldTitle }
            }
            
            val id = if (existing != null) {
                existing.id
            } else {
                // 3. Create new one if neither exists
                val newPlaylist = apiService.createPlaylist(
                    body = PlaylistCreateBody(snippet = YouTubePlaylistSnippet(title = newTitle, description = "Generated by SeenVideo app"))
                )
                newPlaylist.id
            }
            
            if (newTitle == PLAYLIST_WATCHED_NEW) syncPlaylistId = id
            else watchLaterPlaylistId = id
            id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateChannelSettings(channel: ChannelEntity) {
        channelDao.updateChannel(channel)
    }

    suspend fun deleteChannel(channel: ChannelEntity) {
        channelDao.deleteChannel(channel)
        videoDao.deleteVideosByChannel(channel.id)
        nextPageTokens.remove(channel.id)
    }
}
