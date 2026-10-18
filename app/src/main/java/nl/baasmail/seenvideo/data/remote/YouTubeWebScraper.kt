package nl.baasmail.seenvideo.data.remote

import android.text.Html
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.VideoEntity
import nl.baasmail.seenvideo.data.repository.ChannelSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeWebScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun fetchChannelVideosFromRss(channelId: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        val cleanChannelId = channelId.trim()
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$cleanChannelId"
        Log.d("YouTubeWebScraper", "Fetching RSS feed: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        val parsedVideos = try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("YouTubeWebScraper", "RSS request failed with code ${response.code}")
                    return@withContext emptyList()
                }

                val xmlData = response.body?.string() ?: return@withContext emptyList()
                parseRssXml(xmlData, cleanChannelId)
            }
        } catch (e: Exception) {
            Log.e("YouTubeWebScraper", "Error fetching/parsing RSS for $channelId", e)
            return@withContext emptyList()
        }

        if (parsedVideos.isEmpty()) return@withContext emptyList()

        // Enrich videos in parallel to fetch length/duration for shorts filtering
        return@withContext coroutineScope {
            parsedVideos.map { video ->
                async { enrichVideoDetails(video) }
            }.awaitAll()
        }
    }

    suspend fun enrichVideoDetails(video: VideoEntity): VideoEntity = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=${video.id}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext video
                val html = response.body?.string() ?: return@withContext video

                val lengthSecRegex = Regex("\"lengthSeconds\":\"(\\d+)\"")
                val lengthSec = lengthSecRegex.find(html)?.groupValues?.get(1)?.toLongOrNull()

                val durationIsoRegex = Regex("<meta itemprop=\"duration\" content=\"([^\"]+)\">")
                val isoDuration = durationIsoRegex.find(html)?.groupValues?.get(1) ?: ""

                val formattedDuration = if (lengthSec != null && lengthSec > 0) {
                    formatSeconds(lengthSec)
                } else if (isoDuration.isNotEmpty()) {
                    parseIsoDuration(isoDuration)
                } else {
                    video.duration
                }

                return@withContext video.copy(duration = formattedDuration)
            }
        } catch (e: Exception) {
            video
        }
    }

    private fun parseRssXml(xmlData: String, channelId: String): List<VideoEntity> {
        val videos = mutableListOf<VideoEntity>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xmlData))

            var eventType = parser.eventType
            var currentVideoId: String? = null
            var currentTitle: String? = null
            var currentPublishedAt: Long = 0L
            var currentThumbnailUrl: String? = null
            var currentChannelName: String? = null
            var inEntry = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "entry") {
                            inEntry = true
                            currentVideoId = null
                            currentTitle = null
                            currentPublishedAt = 0L
                            currentThumbnailUrl = null
                            currentChannelName = null
                        } else if (inEntry) {
                            when (tagName) {
                                "videoId" -> currentVideoId = parser.nextText()
                                "title" -> currentTitle = parser.nextText()
                                "published" -> {
                                    val timeStr = parser.nextText()
                                    currentPublishedAt = try {
                                        Instant.parse(timeStr).toEpochMilli()
                                    } catch (e: Exception) {
                                        System.currentTimeMillis()
                                    }
                                }
                                "name" -> currentChannelName = parser.nextText()
                                "thumbnail" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrEmpty()) {
                                        currentThumbnailUrl = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "entry") {
                            inEntry = false
                            if (!currentVideoId.isNullOrEmpty()) {
                                val title = Html.fromHtml(currentTitle ?: "Video", Html.FROM_HTML_MODE_LEGACY).toString()
                                val thumbnail = currentThumbnailUrl
                                    ?: "https://img.youtube.com/vi/$currentVideoId/hqdefault.jpg"
                                val chName = currentChannelName ?: "YouTube"

                                videos.add(
                                    VideoEntity(
                                        id = currentVideoId,
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        channelId = channelId,
                                        channelName = chName,
                                        publishedAt = currentPublishedAt
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("YouTubeWebScraper", "Failed parsing XML", e)
        }
        return videos
    }

    suspend fun resolveChannelFromHandle(handle: String): ChannelEntity? = withContext(Dispatchers.IO) {
        val formattedHandle = if (handle.startsWith("@")) handle else "@$handle"
        val url = "https://www.youtube.com/$formattedHandle"
        Log.d("YouTubeWebScraper", "Resolving handle from web: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null

                // Extract Channel ID (UC...)
                val channelIdRegex = Regex("(?:\browse/|channel/|\"externalId\":\")([a-zA-Z0-9_-]{24})")
                val channelId = channelIdRegex.find(html)?.groupValues?.get(1) ?: return@withContext null

                // Extract Title
                val titleRegex = Regex("<meta property=\"og:title\" content=\"([^\"]+)\">")
                val rawTitle = titleRegex.find(html)?.groupValues?.get(1) ?: formattedHandle
                val title = Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_LEGACY).toString()

                val uploadsId = "UU" + channelId.removePrefix("UC")

                return@withContext ChannelEntity(
                    id = channelId,
                    name = title,
                    handle = formattedHandle,
                    uploadsPlaylistId = uploadsId
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeWebScraper", "Failed to resolve handle: $handle", e)
            null
        }
    }

    suspend fun searchChannelsWeb(query: String): List<ChannelSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        if (trimmed.startsWith("@") || !trimmed.contains(" ")) {
            val handleResult = resolveChannelFromHandle(trimmed)
            if (handleResult != null) {
                return@withContext listOf(
                    ChannelSearchResult(
                        id = handleResult.id,
                        title = handleResult.name,
                        thumbnailUrl = "",
                        handle = handleResult.handle
                    )
                )
            }
        }

        // Web search fallback
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAg%253D%253D"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: return@withContext emptyList()

                val results = mutableListOf<ChannelSearchResult>()
                val channelBlockRegex = Regex("\"channelRenderer\":\\{\"channelId\":\"([^\"]+)\",\"title\":\\{\"simpleText\":\"([^\"]+)\"\\}")
                
                channelBlockRegex.findAll(html).take(5).forEach { match ->
                    val id = match.groupValues[1]
                    val title = Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString()
                    results.add(
                        ChannelSearchResult(
                            id = id,
                            title = title,
                            thumbnailUrl = "https://img.youtube.com/vi/placeholder/hqdefault.jpg",
                            handle = ""
                        )
                    )
                }

                return@withContext results
            }
        } catch (e: Exception) {
            Log.e("YouTubeWebScraper", "Web channel search failed for $query", e)
            emptyList()
        }
    }

    suspend fun fetchVideoDetailsWeb(videoId: String): VideoEntity? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null

                val titleRegex = Regex("<meta property=\"og:title\" content=\"([^\"]+)\">")
                val title = titleRegex.find(html)?.groupValues?.get(1)?.let {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
                } ?: "Video ($videoId)"

                val lengthSecRegex = Regex("\"lengthSeconds\":\"(\\d+)\"")
                val lengthSec = lengthSecRegex.find(html)?.groupValues?.get(1)?.toLongOrNull()

                val durationIsoRegex = Regex("<meta itemprop=\"duration\" content=\"([^\"]+)\">")
                val durationIso = durationIsoRegex.find(html)?.groupValues?.get(1) ?: ""

                val duration = if (lengthSec != null && lengthSec > 0) {
                    formatSeconds(lengthSec)
                } else if (durationIso.isNotEmpty()) {
                    parseIsoDuration(durationIso)
                } else {
                    ""
                }

                val channelIdRegex = Regex("\"channelId\":\"([^\"]+)\"")
                val channelId = channelIdRegex.find(html)?.groupValues?.get(1) ?: "unknown"

                val channelNameRegex = Regex("<link itemprop=\"name\" content=\"([^\"]+)\">")
                val channelName = channelNameRegex.find(html)?.groupValues?.get(1) ?: "YouTube"

                return@withContext VideoEntity(
                    id = videoId,
                    title = title,
                    thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                    channelId = channelId,
                    channelName = channelName,
                    duration = duration,
                    publishedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeWebScraper", "Failed to fetch video details web for $videoId", e)
            null
        }
    }

    private fun formatSeconds(secondsTotal: Long): String {
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun parseIsoDuration(isoDuration: String): String {
        if (isoDuration.isEmpty()) return ""
        return try {
            val d = Duration.parse(isoDuration)
            val hours = d.toHours()
            val minutes = d.toMinutes() % 60
            val seconds = d.seconds % 60
            if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            ""
        }
    }
}
