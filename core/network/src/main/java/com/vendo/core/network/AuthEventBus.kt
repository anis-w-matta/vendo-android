package com.vendo.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthEvent {
    data object LoggedOut : AuthEvent
}

/** Emitted by AuthInterceptor whenever the backend returns 401 (expired or
 * invalid token). Collected once at the top of the nav graph to force
 * navigation back to Login and clear the stored session. */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emitLoggedOut() {
        _events.tryEmit(AuthEvent.LoggedOut)
    }
}
