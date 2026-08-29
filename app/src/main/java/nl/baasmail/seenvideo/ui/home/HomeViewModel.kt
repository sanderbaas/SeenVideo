package nl.baasmail.seenvideo.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.VideoEntity
import nl.baasmail.seenvideo.data.repository.YouTubeRepository
import nl.baasmail.seenvideo.ui.YouTubeLauncher
import javax.inject.Inject

import nl.baasmail.seenvideo.ui.auth.AuthManager

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val youtubeLauncher: YouTubeLauncher,
    private val authManager: AuthManager
) : ViewModel() {

    val userEmail = authManager.userEmail
    val userName = authManager.userName
    val userPhoto = authManager.userPhoto
    val accessToken = authManager.accessToken
    val isFetchingToken = authManager.isFetchingToken

    fun autoSignIn(context: Context) {
        if (authManager.userEmail.value != null) {
            // Reeds een opgeslagen sessie, haal token op en synchroniseer
            viewModelScope.launch {
                authManager.getYouTubeToken(context)
                repository.syncLocalWatchedToYouTube()
                refresh()
            }
            return
        }
        
        viewModelScope.launch {
            val result = authManager.autoSignIn(context)
            if (result == "Success") {
                authManager.getYouTubeToken(context)
                repository.syncLocalWatchedToYouTube()
                refresh() // Haal vinkjes en videos op
            }
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            val result = authManager.signIn(context)
            if (result == "Success") {
                authManager.getYouTubeToken(context)
                repository.syncLocalWatchedToYouTube()
                refresh() // Haal vinkjes en videos op
            }
        }
    }

    fun fetchYouTubeToken(context: Context) {
        viewModelScope.launch {
            authManager.getYouTubeToken(context)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId = _selectedChannelId.asStateFlow()

    private val _isWatchLaterSelected = MutableStateFlow(false)
    val isWatchLaterSelected = _isWatchLaterSelected.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allVideos,
        repository.allChannels,
        _selectedChannelId,
        _isWatchLaterSelected
    ) { videos, channels, selectedId, watchLaterOnly ->
        val filteredVideos = videos.filter { video ->
            val channel = channels.find { it.id == video.channelId }
            if (watchLaterOnly) {
                video.watchLaterItemId != null
            } else {
                if (selectedId == null) {
                    channel?.showOnHome ?: false
                } else {
                    video.channelId == selectedId
                }
            }
        }

        HomeUiState(
            videos = filteredVideos,
            channels = channels,
            selectedChannelId = selectedId,
            isWatchLaterSelected = watchLaterOnly
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Forceer een nieuw token bij een handmatige refresh om zeker te zijn van actuele data
                val context = authManager.getContext() 
                authManager.getYouTubeToken(context, forceRefresh = false) // Probeer eerst normaal
                
                val currentId = _selectedChannelId.value
                if (currentId == null) {
                    repository.refreshAll()
                } else {
                    repository.refreshVideosForChannel(currentId)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isRefreshing.value || _isWatchLaterSelected.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                repository.loadMoreVideos(_selectedChannelId.value)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun selectChannel(channelId: String?) {
        _isWatchLaterSelected.value = false
        _selectedChannelId.value = channelId
        refresh()
    }

    fun selectWatchLater(selected: Boolean) {
        _isWatchLaterSelected.value = selected
        if (selected) _selectedChannelId.value = null
        refresh()
    }

    fun markAsWatched(videoId: String, isWatched: Boolean) {
        viewModelScope.launch {
            repository.markAsWatched(videoId, isWatched)
        }
    }

    fun toggleWatchLater(videoId: String, addToWatchLater: Boolean) {
        viewModelScope.launch {
            repository.toggleWatchLater(videoId, addToWatchLater)
        }
    }

    fun playVideo(video: VideoEntity) {
        youtubeLauncher.openVideo(video.id)
    }

    fun getRelativeTime(timestamp: Long): String {
        return repository.getRelativeTime(timestamp)
    }
}

data class HomeUiState(
    val videos: List<VideoEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val selectedChannelId: String? = null,
    val isWatchLaterSelected: Boolean = false
)
