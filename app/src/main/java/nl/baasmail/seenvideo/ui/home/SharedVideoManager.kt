package nl.baasmail.seenvideo.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedVideoManager @Inject constructor() {
    private val _sharedVideoId = MutableStateFlow<String?>(null)
    val sharedVideoId = _sharedVideoId.asStateFlow()

    fun onVideoShared(videoId: String) {
        _sharedVideoId.value = videoId
    }

    fun clearSharedVideo() {
        _sharedVideoId.value = null
    }

    companion object {
        fun extractYouTubeVideoId(text: String?): String? {
            if (text.isNullOrEmpty()) return null
            val regex = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?|shorts)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            return match?.groupValues?.getOrNull(1)
        }
    }
}
