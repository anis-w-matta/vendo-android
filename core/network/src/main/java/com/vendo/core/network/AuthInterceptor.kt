package com.vendo.core.network

import com.vendo.core.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private val UNAUTHENTICATED_PATHS = setOf("auth/login", "health")

/** Injects the stored bearer token on every request except login/health,
 * and clears the session + notifies AuthEventBus on a 401 so the app can
 * fall back to the Login screen from anywhere. */
class AuthInterceptor @Inject constructor(
    private val settings: SettingsDataStore,
    private val authEventBus: AuthEventBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath.trimStart('/')
        val isUnauthenticated = UNAUTHENTICATED_PATHS.any { path.startsWith(it) }

        val request = if (isUnauthenticated) {
            original
        } else {
            val token = runBlocking { settings.currentToken() }
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
        }

        val response = chain.proceed(request)
        if (response.code == 401 && !isUnauthenticated) {
            runBlocking { settings.clearSession() }
            authEventBus.emitLoggedOut()
        }
        return response
    }
}
