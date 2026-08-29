package nl.baasmail.seenvideo.ui.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import nl.baasmail.seenvideo.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)
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

    suspend fun autoSignIn(activityContext: Context): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleCredential(result.credential)
        } catch (e: Exception) {
            // Auto sign-in failed, probably no session
            null
        }
    }

    suspend fun signIn(activityContext: Context): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleCredential(result.credential)
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Sign-in failed", e)
            null
        }
    }

    private fun handleCredential(credential: androidx.credentials.Credential): String? {
        val googleIdTokenCredential = when {
            credential is GoogleIdTokenCredential -> credential
            credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (e: GoogleIdTokenParsingException) {
                    android.util.Log.e("AuthManager", "Received Google ID token but failed to parse it", e)
                    null
                }
            }
            else -> {
                android.util.Log.e("AuthManager", "Unexpected credential type: ${credential::class.java.name}")
                null
            }
        }

        return if (googleIdTokenCredential != null) {
            _userEmail.value = googleIdTokenCredential.id
            _userName.value = googleIdTokenCredential.displayName
            _userPhoto.value = googleIdTokenCredential.profilePictureUri?.toString()
            
            prefs.edit().apply {
                putString("user_email", _userEmail.value)
                putString("user_name", _userName.value)
                putString("user_photo", _userPhoto.value)
                apply()
            }
            
            "Success"
        } else {
            null
        }
    }

    suspend fun getYouTubeToken(activityContext: Context, forceRefresh: Boolean = false): String? {
        val email = _userEmail.value ?: return null
        
        if (forceRefresh) {
            withContext(Dispatchers.IO) {
                try {
                    GoogleAuthUtil.invalidateToken(context, _accessToken.value ?: "")
                    _accessToken.value = null
                } catch (e: Exception) {
                    Log.e("AuthManager", "Failed to invalidate token", e)
                }
            }
        }

        _isFetchingToken.value = true
        return withContext(Dispatchers.IO) {
            try {
                val scope = "oauth2:https://www.googleapis.com/auth/youtube"
                val token = GoogleAuthUtil.getToken(context, email, scope)
                android.util.Log.d("AuthManager", "Successfully retrieved YouTube access token")
                _accessToken.value = token
                token
            } catch (e: UserRecoverableAuthException) {
                activityContext.startActivity(e.intent)
                null
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Failed to get YouTube token", e)
                null
            } finally {
                _isFetchingToken.value = false
            }
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        _userEmail.value = null
        _userName.value = null
        _userPhoto.value = null
        _accessToken.value = null
        
        prefs.edit().clear().apply()
    }
}
