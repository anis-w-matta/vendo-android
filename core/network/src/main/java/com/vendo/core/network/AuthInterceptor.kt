package com.vendo.core.network

import com.vendo.core.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private val UNAUTHENTICATED_PATHS = setOf("auth/login", "health")

/** A 401 here means "your input was wrong" (a bad current password), not
 * "your session died" - unlike every other 401 in the app, it must not
 * trigger a global logout. Previously unset, so mistyping the current
 * password on the Change Password screen force-logged the user out (401,
 * not in UNAUTHENTICATED_PATHS, tripped the same global-logout path as an
 * actually-expired token) instead of just letting them retry the field. */
private val NO_LOGOUT_ON_401_PATHS = UNAUTHENTICATED_PATHS + "auth/change-password"

/** Injects the stored bearer token on every request except login/health,
 * and the shared-secret API key (BuildConfig.API_KEY, blank by default) on
 * every request including those two - the backend's optional require_api_key
 * gate (app/api/deps.py) applies to /auth/login as well. Clears the session
 * + notifies AuthEventBus on a 401 so the app can fall back to the Login
 * screen from anywhere, except on endpoints where a 401 is an expected,
 * locally-handled validation failure rather than session expiry. */
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
        val skipLogoutOn401 = NO_LOGOUT_ON_401_PATHS.any { path.startsWith(it) }
        if (response.code == 401 && !skipLogoutOn401) {
            runBlocking { settings.clearSession() }
            authEventBus.emitLoggedOut()
        }
        return response
    }
}
