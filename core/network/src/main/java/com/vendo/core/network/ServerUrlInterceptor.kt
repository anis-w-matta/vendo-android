package com.vendo.core.network

import com.vendo.core.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Rewrites every request's scheme/host/port to the salesman-entered
 * server address (Login screen, persisted via SettingsDataStore) so one
 * build can point at any backend on any network without a recompile.
 * A blank/unset value or an unparseable one leaves the request pointed at
 * Retrofit's compiled-in BuildConfig.BASE_URL, unchanged. */
class ServerUrlInterceptor @Inject constructor(
    private val settings: SettingsDataStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val stored = runBlocking { settings.currentServerUrl() }
        val target = stored?.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
        val request = if (target == null) {
            original
        } else {
            val newUrl = original.url.newBuilder()
                .scheme(target.scheme)
                .host(target.host)
                .port(target.port)
                .build()
            original.newBuilder().url(newUrl).build()
        }
        return chain.proceed(request)
    }
}
