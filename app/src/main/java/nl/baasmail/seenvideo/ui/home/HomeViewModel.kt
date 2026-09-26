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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.ChannelGroupEntity
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
            }
            refresh() // Haal vinkjes en videos op
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

    private val _selectedGroupId = MutableStateFlow<Long?>(-1L)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allVideos,
        repository.allChannels,
        repository.allGroups,
        _selectedChannelId,
        _selectedGroupId,
        _isWatchLaterSelected
    ) { flows ->
        val videos = flows[0] as List<VideoEntity>
        val channels = flows[1] as List<ChannelEntity>
        val groups = flows[2] as List<ChannelGroupEntity>
        val selectedId = flows[3] as String?
        val selectedGroupId = flows[4] as Long?
        val watchLaterOnly = flows[5] as Boolean

        // If no groups exist, we don't want any group filtering
        val effectiveGroupId = if (groups.isEmpty()) -1L else selectedGroupId

        val filteredVideos = videos.filter { video ->
            val channel = channels.find { it.id == video.channelId }
            
            // 1. Group Filter
            val isInGroup = when (effectiveGroupId) {
                null, -1L -> true
                else -> channel?.groupId == effectiveGroupId
            }
            if (!isInGroup) return@filter false

            // 2. Shorts Filter
            if (channel?.showShorts == false && video.isShortVideo()) {
                return@filter false
            }

            // 3. Category Filter
            if (watchLaterOnly) {
                video.watchLaterItemId != null
            } else if (selectedId != null) {
                video.channelId == selectedId
            } else {
                // "Home" mode
                channel?.showOnHome ?: false
            }
        }

        val filteredChannels = when (effectiveGroupId) {
            null, -1L -> channels
            else -> channels.filter { it.groupId == effectiveGroupId }
        }

        HomeUiState(
            videos = filteredVideos,
            channels = filteredChannels,
            groups = groups,
            selectedChannelId = selectedId,
            selectedGroupId = selectedGroupId,
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
                    val currentGroupId = _selectedGroupId.value
                    if (currentGroupId == null || currentGroupId == -1L) {
                        repository.refreshAll()
                    } else {
                        // Refresh all channels in the current group
                        val allChannels = repository.allChannels.first()
                        val groupChannels = allChannels.filter { it.groupId == currentGroupId }
                        groupChannels.forEach { channel ->
                            repository.refreshVideosForChannel(channel.id)
                        }
                    }
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

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
        _selectedChannelId.value = null // Reset selected channel when group changes
        refresh()
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
    val groups: List<ChannelGroupEntity> = emptyList(),
    val selectedChannelId: String? = null,
    val selectedGroupId: Long? = null,
    val isWatchLaterSelected: Boolean = false
)
