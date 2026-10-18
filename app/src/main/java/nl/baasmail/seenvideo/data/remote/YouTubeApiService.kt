package nl.baasmail.seenvideo.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String? = null,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 10,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubeResponse

    @GET("youtube/v3/channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("forHandle") handle: String? = null,
        @Query("id") id: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubeChannelResponse

    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("id") ids: String,
        @Query("key") apiKey: String? = null
    ): YouTubeVideoDetailsResponse

    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubePlaylistResponse
}

data class YouTubePlaylistResponse(
    val items: List<YouTubePlaylistItem>? = null,
    val nextPageToken: String? = null
)

data class YouTubePlaylistItem(
    val id: String,
    val snippet: YouTubePlaylistItemSnippet
)

data class YouTubePlaylistItemSnippet(
    val title: String,
    val publishedAt: String,
    val thumbnails: YouTubeThumbnails,
    val resourceId: YouTubeResourceId
)

data class YouTubeResourceId(
    val kind: String = "youtube#video",
    val videoId: String
)

data class YouTubeChannelResponse(
    val items: List<YouTubeChannelItem>? = null
)

data class YouTubeChannelItem(
    val id: String,
    val snippet: YouTubeChannelSnippet,
    val contentDetails: YouTubeChannelContentDetails? = null
)

data class YouTubeChannelContentDetails(
    val relatedPlaylists: YouTubeRelatedPlaylists
)

data class YouTubeRelatedPlaylists(
    val uploads: String
)

data class YouTubeChannelSnippet(
    val title: String,
    val customUrl: String? = null
)

data class YouTubeResponse(
    val items: List<YouTubeItem>? = null,
    val nextPageToken: String? = null
)

data class YouTubeItem(
    val id: YouTubeId,
    val snippet: YouTubeSnippet
)

data class YouTubeId(
    val kind: String,
    val videoId: String? = null,
    val channelId: String? = null
)

data class YouTubeSnippet(
    val title: String,
    val publishedAt: String,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val thumbnails: YouTubeThumbnails
)

data class YouTubeThumbnails(
    val default: YouTubeThumbnail,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null
)

data class YouTubeThumbnail(
    val url: String
)

data class YouTubeVideoDetailsResponse(
    val items: List<YouTubeVideoDetailsItem>
)

data class YouTubeVideoDetailsItem(
    val id: String,
    val snippet: YouTubeSnippet? = null,
    val contentDetails: YouTubeContentDetails
)

data class YouTubeContentDetails(
    val duration: String
)
