package org.jellycopter.core.model

import org.jellycopter.core.session.UserSession

/**
 * Represents the current authentication state of the application.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val session: UserSession) : AuthState
    data class Error(val message: String) : AuthState
}
