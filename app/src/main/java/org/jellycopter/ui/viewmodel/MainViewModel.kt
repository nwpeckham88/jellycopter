package org.jellycopter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.core.database.DeckEntity
import org.jellycopter.core.model.MediaDiscoverItem
import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.core.repository.ChannelRepository
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.discovery.JellyseerrClient

/**
 * ViewModel coordinating top-level asynchronous mutations and network calls.
 * Executes mutations in [viewModelScope] so requests survive tab switching,
 * navigation events, and composable unmounting.
 */
class MainViewModel(
    private val channelRepository: ChannelRepository? = null,
    private val deckRepository: DeckRepository? = null,
    private val mediaRepository: MediaRepository? = null,
    private val jellyseerrClient: JellyseerrClient? = null
) : ViewModel() {

    fun executeMutation(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }

    fun saveChannel(channel: ChannelEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            channelRepository?.saveChannel(channel)
            onComplete()
        }
    }

    fun deleteChannel(channelId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            channelRepository?.deleteChannel(channelId)
            onComplete()
        }
    }

    fun saveDeck(deck: DeckEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            deckRepository?.saveDeck(deck)
            onComplete()
        }
    }

    fun requestMedia(item: MediaDiscoverItem, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                jellyseerrClient?.requestOneClick(
                    mediaId = item.tmdbId,
                    mediaType = item.mediaType,
                    title = item.title
                )
            }
            onComplete()
        }
    }

    fun requestMissingSeason(tmdbId: Int, seasonNum: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (tmdbId > 0) {
                    jellyseerrClient?.requestMedia(
                        MediaRequestPayload(
                            mediaType = "tv",
                            mediaId = tmdbId,
                            seasons = listOf(seasonNum)
                        )
                    )
                }
            }
            onComplete()
        }
    }
}
