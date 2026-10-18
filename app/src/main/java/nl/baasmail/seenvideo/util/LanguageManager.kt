package nl.baasmail.seenvideo.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(prefs.getString("language", "nl") ?: "nl")
    val currentLanguage = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        prefs.edit().putString("language", lang).apply()
    }

    fun isEnglish() = _currentLanguage.value == "en"

    fun t(nl: String, en: String): String {
        return if (isEnglish()) en else nl
    }
}
