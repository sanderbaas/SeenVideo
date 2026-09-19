package nl.baasmail.seenvideo.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration.Companion.milliseconds
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.ChannelGroupEntity
import nl.baasmail.seenvideo.data.repository.ChannelSearchResult
import nl.baasmail.seenvideo.data.repository.YouTubeRepository
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repository: YouTubeRepository
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = repository.allChannels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groups: StateFlow<List<ChannelGroupEntity>> = repository.allGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChannelSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(500.milliseconds)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query ->
                    _isSearching.value = true
                    val results = repository.searchChannels(query)
                    _searchResults.value = results
                    _isSearching.value = false
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
        }
    }

    fun addChannel(handle: String) {
        viewModelScope.launch {
            try {
                val success = repository.addChannelByHandle(handle)
                if (!success) {
                    _errorEvents.emit("Kanaal niet gevonden. Controleer de handle (bijv. @google).")
                }
            } catch (e: Exception) {
                _errorEvents.emit("Er is een fout opgetreden bij het toevoegen.")
            }
        }
    }

    fun addChannelFromSearch(result: ChannelSearchResult) {
        viewModelScope.launch {
            try {
                val success = repository.addChannelById(result.id, result.title)
                if (!success) {
                    _errorEvents.emit("Fout bij het toevoegen van ${result.title}.")
                }
            } catch (e: Exception) {
                _errorEvents.emit("Er is een fout opgetreden bij het toevoegen.")
            }
        }
    }

    fun updateSettings(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.updateChannelSettings(channel)
        }
    }

    fun removeChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.deleteChannel(channel)
        }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            repository.addGroup(name)
        }
    }

    fun removeGroup(group: ChannelGroupEntity) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }
}
