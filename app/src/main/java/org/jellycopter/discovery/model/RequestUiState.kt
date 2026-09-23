package org.jellycopter.discovery.model

sealed interface RequestUiState {
    data object Idle : RequestUiState

    data class Submitting(
        val mediaId: Int,
        val title: String
    ) : RequestUiState

    data class Success(
        val mediaId: Int,
        val requestId: Int,
        val message: String = "Requested successfully"
    ) : RequestUiState

    data class AlreadyRequested(
        val mediaId: Int,
        val message: String = "Request already exists"
    ) : RequestUiState

    data class PermissionDenied(
        val mediaId: Int,
        val message: String = "User does not have permission to request media"
    ) : RequestUiState

    data class Error(
        val mediaId: Int,
        val message: String
    ) : RequestUiState
}
