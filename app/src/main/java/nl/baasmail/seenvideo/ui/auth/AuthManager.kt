package nl.baasmail.seenvideo.ui.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName = _userName.asStateFlow()

    private val _userPhoto = MutableStateFlow<String?>(null)
    val userPhoto = _userPhoto.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken = _accessToken.asStateFlow()

    private val _isFetchingToken = MutableStateFlow(false)
    val isFetchingToken = _isFetchingToken.asStateFlow()

    init {
        _userEmail.value = prefs.getString("user_email", null)
        _userName.value = prefs.getString("user_name", null)
        _userPhoto.value = prefs.getString("user_photo", null)
    }

    fun getContext() = context

    suspend fun autoSignIn(activityContext: Context): String? = null

    suspend fun signIn(activityContext: Context): String? = null

    suspend fun getYouTubeToken(activityContext: Context, forceRefresh: Boolean = false): String? = null

    suspend fun signOut() {
        _userEmail.value = null
        _userName.value = null
        _userPhoto.value = null
        _accessToken.value = null
        prefs.edit().clear().apply()
    }
}
