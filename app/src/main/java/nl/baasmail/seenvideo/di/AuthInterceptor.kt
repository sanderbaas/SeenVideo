package nl.baasmail.seenvideo.di

import nl.baasmail.seenvideo.BuildConfig
import nl.baasmail.seenvideo.ui.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.accessToken.value
        val request = chain.request()
        val url = request.url
        
        val newRequestBuilder = request.newBuilder()
        
        if (!token.isNullOrEmpty()) {
            newRequestBuilder.header("Authorization", "Bearer $token")
        } else {
            // Fallback to API Key if no token is available
            val newUrl = url.newBuilder()
                .addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
                .build()
            newRequestBuilder.url(newUrl)
        }
        
        return chain.proceed(newRequestBuilder.build())
    }
}
