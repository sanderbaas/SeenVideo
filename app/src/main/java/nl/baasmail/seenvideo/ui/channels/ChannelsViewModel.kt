package nl.baasmail.seenvideo.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.baasmail.seenvideo.data.local.ChannelEntity
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

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

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
}
