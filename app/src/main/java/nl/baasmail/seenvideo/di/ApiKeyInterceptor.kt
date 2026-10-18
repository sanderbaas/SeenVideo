package nl.baasmail.seenvideo.di

import nl.baasmail.seenvideo.data.local.ApiPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyInterceptor @Inject constructor(
    private val apiPreferences: ApiPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = apiPreferences.getApiKey()

        val newRequest = if (apiKey.isNotEmpty()) {
            val url = request.url.newBuilder()
                .addQueryParameter("key", apiKey)
                .build()
            request.newBuilder().url(url).build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
