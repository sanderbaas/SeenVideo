package nl.baasmail.seenvideo.data.repository

import android.text.Html
import android.text.format.DateUtils
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import nl.baasmail.seenvideo.data.local.ApiPreferences
import nl.baasmail.seenvideo.data.local.ChannelDao
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.ChannelGroupEntity
import nl.baasmail.seenvideo.data.local.VideoDao
import nl.baasmail.seenvideo.data.local.VideoEntity
import nl.baasmail.seenvideo.data.remote.YouTubeApiService
import nl.baasmail.seenvideo.data.remote.YouTubeWebScraper
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
    private val apiPreferences: ApiPreferences,
    private val webScraper: YouTubeWebScraper
) {
    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val allGroups: Flow<List<ChannelGroupEntity>> = channelDao.getAllGroups()
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()

    // Store nextPageToken per channel for infinite scrolling
    private val nextPageTokens = ConcurrentHashMap<String, String>()

    suspend fun addGroup(name: String) {
        channelDao.insertGroup(ChannelGroupEntity(name = name))
    }

    suspend fun deleteGroup(group: ChannelGroupEntity) {
        val channels = allChannels.first()
        channels.filter { it.groupId == group.id }.forEach { channel ->
            channelDao.updateChannel(channel.copy(groupId = null))
        }
        channelDao.deleteGroup(group)
    }

    suspend fun addChannelByHandle(handle: String): Boolean {
        Log.d("YouTubeRepository", "Adding channel by handle: $handle")
        val formattedHandle = if (handle.startsWith("@")) handle else "@$handle"
        val apiKey = apiPreferences.getApiKey()

        if (apiKey.isNotEmpty()) {
            var response = try {
                apiService.getChannelDetails(handle = formattedHandle, apiKey = apiKey)
            } catch (e: Exception) {
                null
            }

            if (response?.items.isNullOrEmpty() && formattedHandle != handle) {
                response = try {
                    apiService.getChannelDetails(handle = handle, apiKey = apiKey)
                } catch (e: Exception) {
                    null
                }
            }

            val channel = response?.items?.firstOrNull()
            if (channel != null) {
                val uploadsId = channel.contentDetails?.relatedPlaylists?.uploads?.takeIf { it.startsWith("UU") }
                    ?: ("UU" + channel.id.removePrefix("UC"))

                channelDao.insertChannel(
                    ChannelEntity(
                        id = channel.id,
                        name = Html.fromHtml(channel.snippet.title, Html.FROM_HTML_MODE_LEGACY).toString(),
                        handle = channel.snippet.customUrl ?: handle,
                        uploadsPlaylistId = uploadsId
                    )
                )
                refreshVideosForChannel(channel.id)
                return true
            }
        }

        // Web Scraper Fallback
        Log.d("YouTubeRepository", "Using web scraper fallback for handle: $formattedHandle")
        val webChannel = webScraper.resolveChannelFromHandle(formattedHandle) ?: return false
        channelDao.insertChannel(webChannel)
        refreshVideosForChannel(webChannel.id)
        return true
    }

    suspend fun refreshVideosForChannel(channelId: String) {
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
        val channelSettings = channelDao.getChannelById(channelId) ?: return
        val apiKey = apiPreferences.getApiKey()

        if (apiKey.isNotEmpty()) {
            val playlistId = if (channelSettings.uploadsPlaylistId.startsWith("UU")) {
                channelSettings.uploadsPlaylistId
            } else {
                val correctId = "UU" + channelId.removePrefix("UC")
                channelDao.updateChannel(channelSettings.copy(uploadsPlaylistId = correctId))
                correctId
            }

            var currentToken: String? = if (isRefresh) null else nextPageTokens[channelId]
            var hasMore = true
            var attempts = 0
            val existingVideos = videoDao.getAllVideos().first()
            val channelVideosCount = existingVideos.count { it.channelId == channelId }
            val maxAttempts = when {
                !isRefresh -> 3
                channelVideosCount < 10 -> 5
                else -> 1
            }

            var apiSuccess = false
            while (hasMore && attempts < maxAttempts) {
                attempts++
                val playlistResponse = try {
                    apiService.getPlaylistItems(
                        playlistId = playlistId,
                        pageToken = if (currentToken.isNullOrEmpty()) null else currentToken,
                        maxResults = 50,
                        apiKey = apiKey
                    )
                } catch (e: Exception) {
                    Log.e("YouTubeRepository", "API Error fetching playlist items for $channelId", e)
                    break
                }

                val items = playlistResponse.items
                if (items.isNullOrEmpty()) {
                    if (isRefresh) nextPageTokens.remove(channelId)
                    apiSuccess = true
                    break
                }
                apiSuccess = true

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
                    Log.e("YouTubeRepository", "API Error fetching video details for $channelId", e)
                    break
                }

                val currentExisting = videoDao.getAllVideos().first()
                val watchedIds = currentExisting.filter { it.isWatched }.associateBy({ it.id }, { it.playlistItemId })
                val watchLaterIds = currentExisting.filter { it.watchLaterItemId != null }.associateBy({ it.id }, { it.watchLaterItemId })

                var brandNewVideosCount = 0

                val validVideos = detailsResponse.items.mapNotNull { item ->
                    val playlistItem = items.find { it.snippet.resourceId.videoId == item.id }
                        ?: return@mapNotNull null

                    val duration = try { Duration.parse(item.contentDetails.duration) } catch (e: Exception) { Duration.ZERO }
                    val title = playlistItem.snippet.title
                    val isShort = duration.seconds <= 180 || title.contains("shorts", ignoreCase = true)

                    if (!channelSettings.showShorts && isShort) {
                        return@mapNotNull null
                    }

                    if (currentExisting.none { it.id == item.id }) {
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

                if (validVideos.isNotEmpty()) {
                    videoDao.insertVideos(validVideos)
                }

                if (isRefresh && brandNewVideosCount == 0 && channelVideosCount > 20) {
                    break
                }
            }

            if (apiSuccess) return
        }

        // Web RSS Scraper Fallback
        Log.d("YouTubeRepository", "Loading videos via RSS fallback for $channelId")
        val rssVideos = webScraper.fetchChannelVideosFromRss(channelId)
        val currentExisting = videoDao.getAllVideos().first()
        val watchedIds = currentExisting.filter { it.isWatched }.associateBy({ it.id }, { it.playlistItemId })
        val watchLaterIds = currentExisting.filter { it.watchLaterItemId != null }.associateBy({ it.id }, { it.watchLaterItemId })

        val validVideos = rssVideos.mapNotNull { video ->
            val seconds = parseDurationToSeconds(video.duration)
            val isShort = (seconds in 1..180) || video.title.contains("shorts", ignoreCase = true) || video.title.contains("#shorts", ignoreCase = true)
            if (!channelSettings.showShorts && isShort) return@mapNotNull null

            video.copy(
                channelName = channelSettings.name,
                isWatched = watchedIds.containsKey(video.id),
                playlistItemId = watchedIds[video.id],
                watchLaterItemId = watchLaterIds[video.id]
            )
        }

        if (validVideos.isNotEmpty()) {
            videoDao.insertVideos(validVideos)
        }
    }

    private fun parseDurationToSeconds(duration: String): Long {
        if (duration.isBlank()) return 0L
        val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun formatDuration(isoDuration: String): String {
        return try {
            val d = Duration.parse(isoDuration)
            val totalSeconds = d.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getRelativeTime(timestamp: Long): String {
        return DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    suspend fun refreshAll() {
        val channels = allChannels.first()
        val apiKey = apiPreferences.getApiKey()

        // Update missing handles
        val missingHandles = channels.filter { it.handle.isEmpty() }
        if (missingHandles.isNotEmpty()) {
            missingHandles.forEach { channel ->
                if (apiKey.isNotEmpty()) {
                    try {
                        val response = apiService.getChannelDetails(id = channel.id, apiKey = apiKey)
                        response.items?.firstOrNull()?.snippet?.customUrl?.let { handle ->
                            channelDao.updateChannel(channel.copy(handle = handle))
                        }
                    } catch (e: Exception) {
                        Log.e("YouTubeRepository", "Failed to update handle via API for ${channel.name}", e)
                    }
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

    suspend fun ensureVideoExists(videoId: String): VideoEntity {
        val existing = videoDao.getVideoById(videoId)
        if (existing != null) return existing

        val apiKey = apiPreferences.getApiKey()
        if (apiKey.isNotEmpty()) {
            try {
                val response = apiService.getVideoDetails(ids = videoId, apiKey = apiKey)
                val item = response.items.firstOrNull()
                val snippet = item?.snippet
                if (item != null && snippet != null) {
                    val entity = VideoEntity(
                        id = item.id,
                        title = Html.fromHtml(snippet.title, Html.FROM_HTML_MODE_LEGACY).toString(),
                        thumbnailUrl = snippet.thumbnails.high?.url ?: snippet.thumbnails.medium?.url ?: snippet.thumbnails.default.url,
                        channelId = snippet.channelId ?: "unknown",
                        channelName = snippet.channelTitle ?: "YouTube",
                        duration = formatDuration(item.contentDetails.duration),
                        publishedAt = try { Instant.parse(snippet.publishedAt).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                    )
                    videoDao.insertVideos(listOf(entity))
                    return entity
                }
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "Failed to fetch video details via API for $videoId", e)
            }
        }

        // Web Scraper Fallback
        val webVideo = webScraper.fetchVideoDetailsWeb(videoId)
        if (webVideo != null) {
            videoDao.insertVideos(listOf(webVideo))
            return webVideo
        }

        val fallback = VideoEntity(
            id = videoId,
            title = "Gedeelde Video ($videoId)",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            channelId = "shared",
            channelName = "YouTube",
            publishedAt = System.currentTimeMillis()
        )
        videoDao.insertVideos(listOf(fallback))
        return fallback
    }

    suspend fun markAsWatched(videoId: String, isWatched: Boolean) {
        ensureVideoExists(videoId)
        videoDao.markAsWatched(videoId, isWatched)
    }

    suspend fun toggleWatchLater(videoId: String, addToWatchLater: Boolean) {
        ensureVideoExists(videoId)
        val watchLaterItemId = if (addToWatchLater) "watch_later_$videoId" else null
        videoDao.updateWatchLaterItemId(videoId, watchLaterItemId)
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
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val apiKey = apiPreferences.getApiKey()

        if (apiKey.isNotEmpty()) {
            try {
                val response = apiService.search(
                    query = trimmed,
                    type = "channel",
                    maxResults = 5,
                    apiKey = apiKey
                )

                val items = response.items
                if (!items.isNullOrEmpty()) {
                    return items.mapNotNull { item ->
                        val channelId = item.id.channelId ?: return@mapNotNull null
                        ChannelSearchResult(
                            id = channelId,
                            title = Html.fromHtml(item.snippet.title, Html.FROM_HTML_MODE_LEGACY).toString(),
                            thumbnailUrl = item.snippet.thumbnails.default.url,
                            handle = ""
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("YouTubeRepository", "API Channel search failed for $trimmed", e)
            }
        }

        // Web Fallback
        return webScraper.searchChannelsWeb(trimmed)
    }

    suspend fun addChannelById(channelId: String, name: String): Boolean {
        Log.d("YouTubeRepository", "Adding channel by ID: $channelId ($name)")
        val apiKey = apiPreferences.getApiKey()

        if (apiKey.isNotEmpty()) {
            val response = try {
                apiService.getChannelDetails(id = channelId, apiKey = apiKey)
            } catch (e: Exception) {
                null
            }

            val channel = response?.items?.firstOrNull()
            if (channel != null) {
                val uploadsId = channel.contentDetails?.relatedPlaylists?.uploads?.takeIf { it.startsWith("UU") }
                    ?: ("UU" + channel.id.removePrefix("UC"))

                channelDao.insertChannel(
                    ChannelEntity(
                        id = channel.id,
                        name = name,
                        handle = channel.snippet.customUrl ?: "",
                        uploadsPlaylistId = uploadsId
                    )
                )
                refreshVideosForChannel(channel.id)
                return true
            }
        }

        // Web Fallback
        val uploadsId = "UU" + channelId.removePrefix("UC")
        channelDao.insertChannel(
            ChannelEntity(
                id = channelId,
                name = name,
                handle = "",
                uploadsPlaylistId = uploadsId
            )
        )
        refreshVideosForChannel(channelId)
        return true
    }
}
