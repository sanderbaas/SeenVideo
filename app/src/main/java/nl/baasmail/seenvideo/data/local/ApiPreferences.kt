package nl.baasmail.seenvideo.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("api_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun getApiKey(): String {
        return prefs.getString("user_youtube_api_key", "")?.trim() ?: ""
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit {
            if (trimmed.isEmpty()) {
                remove("user_youtube_api_key")
            } else {
                putString("user_youtube_api_key", trimmed)
            }
        }
        _apiKey.value = getApiKey()
    }
}
