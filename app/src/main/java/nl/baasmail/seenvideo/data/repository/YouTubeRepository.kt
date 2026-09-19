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

data class ChannelSearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val handle: String = ""
)

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
                handle = channel.snippet.customUrl ?: handle,
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
        Log.d("YouTubeRepository", "loadVideos started for channelId: $channelId, isRefresh: $isRefresh")
        val channels = allChannels.first()
        val channelSettings = channels.find { it.id == channelId } ?: run {
            Log.e("YouTubeRepository", "Channel settings not found for $channelId")
            return
        }
        
        // Ensure we have a valid playlist ID for uploads
        val playlistId = if (channelSettings.uploadsPlaylistId.isNotEmpty()) {
            channelSettings.uploadsPlaylistId
        } else {
            "UU" + channelId.removePrefix("UC")
        }
        
        Log.d("YouTubeRepository", "Using playlistId: $playlistId for channel: ${channelSettings.name}")
        
        var currentToken: String? = if (isRefresh) null else nextPageTokens[channelId]
        var hasMore = true
        var attempts = 0
        // If we have no videos for this channel yet, fetch more pages to populate it
        val existingVideos = videoDao.getAllVideos().first()
        val channelVideosCount = existingVideos.count { it.channelId == channelId }
        val maxAttempts = when {
            !isRefresh -> 3
            channelVideosCount < 10 -> 5 // Newly added or empty channel: get more history
            else -> 1 // Regular refresh: just 1 page
        }

        Log.d("YouTubeRepository", "Starting fetch loop. channelVideosCount: $channelVideosCount, maxAttempts: $maxAttempts")

        while (hasMore && attempts < maxAttempts) {
            attempts++
            Log.d("YouTubeRepository", "Attempt $attempts, pageToken: $currentToken")
            val playlistResponse = try {
                apiService.getPlaylistItems(
                    playlistId = playlistId,
                    pageToken = if (currentToken.isNullOrEmpty()) null else currentToken,
                    maxResults = 50,
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Error fetching playlist items for $channelId", e)
                break
            }
            
            val items = playlistResponse.items
            if (items.isNullOrEmpty()) {
                Log.d("YouTubeRepository", "No more items in playlist")
                if (isRefresh) nextPageTokens.remove(channelId)
                break
            }
            Log.d("YouTubeRepository", "Fetched ${items.size} playlist items")

            currentToken = playlistResponse.nextPageToken
            if (!currentToken.isNullOrEmpty()) {
                nextPageTokens[channelId] = currentToken
            } else {
                nextPageTokens.remove(channelId)
                hasMore = false
            }

            val videoIdsString = items.map { it.snippet.resourceId.videoId }.joinToString(",")
            val detailsResponse = try {
                apiService.getVideoDetails(ids = videoIdsString, apiKey = apiKey)
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Error fetching video details for $channelId", e)
                break
            }
            
            val currentExistingVideos = videoDao.getAllVideos().first()
            val watchedIds = currentExistingVideos.filter { it.isWatched }.associateBy({ it.id }, { it.playlistItemId })
            val watchLaterIds = currentExistingVideos.filter { it.watchLaterItemId != null }.associateBy({ it.id }, { it.watchLaterItemId })

            var brandNewVideosCount = 0

            val validVideos = detailsResponse.items.mapNotNull { item ->
                val playlistItem = items.find { it.snippet.resourceId.videoId == item.id }
                if (playlistItem == null) return@mapNotNull null
                
                val duration = try { Duration.parse(item.contentDetails.duration) } catch (e: Exception) { Duration.ZERO }
                val title = playlistItem.snippet.title
                val isShort = duration.seconds <= 180 || title.contains("shorts", ignoreCase = true)
                
                if (!channelSettings.showShorts && isShort) {
                    return@mapNotNull null
                }

                if (currentExistingVideos.none { it.id == item.id }) {
                    brandNewVideosCount++
                }

                val thumbnails = playlistItem.snippet.thumbnails
                val bestThumbnailUrl = thumbnails.high?.url ?: thumbnails.medium?.url ?: thumbnails.default.url
                
                VideoEntity(
                    id = item.id,
                    title = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY).toString(),
                    thumbnailUrl = bestThumbnailUrl,
                    channelId = channelId,
                    channelName = channelSettings.name,
                    duration = formatDuration(item.contentDetails.duration),
                    isWatched = watchedIds.containsKey(item.id),
                    playlistItemId = watchedIds[item.id],
                    watchLaterItemId = watchLaterIds[item.id],
                    publishedAt = try { Instant.parse(playlistItem.snippet.publishedAt).toEpochMilli() } catch (e: Exception) { 0L }
                )
            }

            Log.d("YouTubeRepository", "Inserting ${validVideos.size} videos, brandNewCount: $brandNewVideosCount")
            if (validVideos.isNotEmpty()) {
                videoDao.insertVideos(validVideos)
            }
            
            // Only stop refresh if we found NO brand new videos AND we already had some videos.
            // This handles the case where the latest videos are already in DB (from playlists) 
            // but we still need to fetch the rest of the channel's uploads.
            if (isRefresh && brandNewVideosCount == 0 && channelVideosCount > 20) {
                Log.d("YouTubeRepository", "Stopping refresh because we seem to be up to date")
                break
            }
        }
        Log.d("YouTubeRepository", "loadVideos finished for $channelId")
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
        
        // Update missing handles
        val missingHandles = channels.filter { it.handle.isEmpty() }
        if (missingHandles.isNotEmpty()) {
            missingHandles.forEach { channel ->
                try {
                    val response = apiService.getChannelDetails(id = channel.id, apiKey = apiKey)
                    response.items?.firstOrNull()?.snippet?.customUrl?.let { handle ->
                        channelDao.updateChannel(channel.copy(handle = handle))
                    }
                } catch (e: Exception) {
                    Log.e("YouTubeRepository", "Failed to update handle for ${channel.name}", e)
                }
            }
        }

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
        Log.d("YouTubeRepository", "syncPlaylist started for $newTitle")
        try {
            val playlistId = getOrCreatePlaylist(newTitle, oldTitle) ?: run {
                Log.w("YouTubeRepository", "Playlist $newTitle not found or created")
                return
            }
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

            Log.d("YouTubeRepository", "Synced $newTitle: found ${ytVideos.size} videos on YouTube")
            val existingVideos = videoDao.getAllVideos().first()
            
            // 1. Find videos that are on YT but are NOT in our local database OR are incomplete
            val idsToFetch = ytVideos.keys.filter { id ->
                val local = existingVideos.find { it.id == id }
                local == null || local.publishedAt == 0L || local.channelName.isEmpty()
            }

            if (idsToFetch.isNotEmpty()) {
                Log.d("YouTubeRepository", "Fetching details for ${idsToFetch.size} new/incomplete videos in $newTitle")
                idsToFetch.chunked(50).forEach { batch ->
                    val details = apiService.getVideoDetails(ids = batch.joinToString(","), apiKey = apiKey)
                    val newEntities = details.items.map { item ->
                        Log.d("YouTubeRepository", "Adding video ${item.id} from playlist sync, channelId: ${item.snippet?.channelId}")
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

    suspend fun searchChannels(query: String): List<ChannelSearchResult> {
        if (query.length < 2) return emptyList()
        
        return try {
            val response = apiService.search(
                query = query,
                type = "channel",
                maxResults = 5,
                apiKey = apiKey
            )
            
            response.items?.mapNotNull { item ->
                val channelId = item.id.channelId ?: return@mapNotNull null
                ChannelSearchResult(
                    id = channelId,
                    title = Html.fromHtml(item.snippet.title, Html.FROM_HTML_MODE_LEGACY).toString(),
                    thumbnailUrl = item.snippet.thumbnails.default.url,
                    handle = "" // Search results don't always contain the handle easily
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Channel search failed", e)
            emptyList()
        }
    }

    suspend fun addChannelById(channelId: String, name: String): Boolean {
        Log.d("YouTubeRepository", "Adding channel by ID: $channelId ($name)")
        
        // Fetch details to get the uploads playlist ID
        val response = try {
            apiService.getChannelDetails(id = channelId, apiKey = apiKey)
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "API Call failed for $channelId", e)
            return false
        }
        
        val channel = response.items?.firstOrNull() ?: return false
        
        channelDao.insertChannel(
            ChannelEntity(
                id = channel.id,
                name = name,
                handle = channel.snippet.customUrl ?: "",
                uploadsPlaylistId = channel.contentDetails?.relatedPlaylists?.uploads ?: ""
            )
        )
        refreshVideosForChannel(channel.id)
        return true
    }
}
