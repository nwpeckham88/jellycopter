package org.jellycopter.discovery.model

sealed interface RequestResult {
    data class Success(
        val requestId: Int,
        val status: Int = 1,
        val is4k: Boolean = false,
        val message: String = "Requested successfully"
    ) : RequestResult

    data class AlreadyRequested(
        val mediaId: Int,
        val message: String = "Request already exists"
    ) : RequestResult

    data class PermissionDenied(
        val mediaId: Int,
        val message: String = "User does not have permission to request media"
    ) : RequestResult

    data class Error(
        val code: Int,
        val message: String,
        val cause: Throwable? = null
    ) : RequestResult
}
