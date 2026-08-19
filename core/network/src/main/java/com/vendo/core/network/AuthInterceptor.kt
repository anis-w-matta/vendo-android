package com.vendo.core.network

import com.vendo.core.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private val UNAUTHENTICATED_PATHS = setOf("auth/login", "health")

/** Injects the stored bearer token on every request except login/health,
 * and the shared-secret API key (BuildConfig.API_KEY, blank by default) on
 * every request including those two - the backend's optional require_api_key
 * gate (app/api/deps.py) applies to /auth/login as well. Clears the session
 * + notifies AuthEventBus on a 401 so the app can fall back to the Login
 * screen from anywhere. */
class AuthInterceptor @Inject constructor(
    private val settings: SettingsDataStore,
    private val authEventBus: AuthEventBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath.trimStart('/')
        val isUnauthenticated = UNAUTHENTICATED_PATHS.any { path.startsWith(it) }

        val builder = original.newBuilder()
        if (BuildConfig.API_KEY.isNotBlank()) {
            builder.addHeader("X-Api-Key", BuildConfig.API_KEY)
        }
        if (!isUnauthenticated) {
            val token = runBlocking { settings.currentToken() }
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
        }
        val request = builder.build()

        val response = chain.proceed(request)
        if (response.code == 401 && !isUnauthenticated) {
            runBlocking { settings.clearSession() }
            authEventBus.emitLoggedOut()
        }
        return response
    }
}
