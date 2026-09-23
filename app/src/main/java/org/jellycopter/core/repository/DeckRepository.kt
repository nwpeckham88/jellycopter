package org.jellycopter.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellycopter.core.database.DeckDao
import org.jellycopter.core.database.DeckEntity
import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShuffledDeck

interface DeckRepository {
    fun getAllDecks(): Flow<List<ShuffledDeck>>
    suspend fun getDeck(deckId: String): ShuffledDeck?
    suspend fun saveDeck(deck: ShuffledDeck)
    suspend fun deleteDeck(deckId: String)
    suspend fun saveProgress(
        deckId: String,
        currentCardId: String,
        currentEpisodeId: String,
        currentIndex: Int,
        positionMs: Long,
        durationMs: Long
    )
    suspend fun getProgress(deckId: String): DeckProgressEntity?
    fun getProgressFlow(deckId: String): Flow<DeckProgressEntity?>
    suspend fun deleteProgress(deckId: String)
}

class RoomDeckRepository(
    private val deckDao: DeckDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : DeckRepository {

    override fun getAllDecks(): Flow<List<ShuffledDeck>> {
        return deckDao.getAllDecksFlow().map { entities ->
            entities.map { entity ->
                val progress = deckDao.getProgress(entity.deckId)
                toShuffledDeck(entity, progress?.midwayResumeMs ?: 0L)
            }
        }
    }

    override suspend fun getDeck(deckId: String): ShuffledDeck? {
        val entity = deckDao.getDeckById(deckId) ?: return null
        val progress = deckDao.getProgress(deckId)
        return toShuffledDeck(entity, progress?.midwayResumeMs ?: 0L)
    }

    override suspend fun saveDeck(deck: ShuffledDeck) {
        val cardsJson = json.encodeToString(deck.cards)
        val exhaustedCardIds = deck.cards.filter { it.isExhausted }.map { it.id }
        val exhaustedJson = json.encodeToString(exhaustedCardIds)

        val entity = DeckEntity(
            deckId = deck.deckId,
            name = deck.name,
            seriesId = deck.seriesId,
            channelId = deck.channelId,
            cycleNumber = deck.cycleNumber,
            currentIndex = deck.currentIndex,
            cardsJson = cardsJson,
            exhaustedCardIdsJson = exhaustedJson,
            strictQueueMode = deck.strictQueueMode,
            updatedAt = deck.updatedAt
        )
        deckDao.upsertDeck(entity)

        if (deck.midwayResumeMs > 0L) {
            val curCard = deck.currentCard
            val currentEpisodeId = curCard?.items?.firstOrNull()?.id ?: curCard?.id ?: ""
            saveProgress(
                deckId = deck.deckId,
                currentCardId = curCard?.id ?: "",
                currentEpisodeId = currentEpisodeId,
                currentIndex = deck.currentIndex,
                positionMs = deck.midwayResumeMs,
                durationMs = 0L
            )
        }
    }

    override suspend fun deleteDeck(deckId: String) {
        deckDao.deleteDeck(deckId)
        deckDao.deleteProgress(deckId)
    }

    override suspend fun saveProgress(
        deckId: String,
        currentCardId: String,
        currentEpisodeId: String,
        currentIndex: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val progress = DeckProgressEntity(
            deckId = deckId,
            currentCardId = currentCardId,
            currentEpisodeId = currentEpisodeId,
            currentIndex = currentIndex,
            midwayResumeMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
        deckDao.upsertProgress(progress)
    }

    override suspend fun getProgress(deckId: String): DeckProgressEntity? {
        return deckDao.getProgress(deckId)
    }

    override fun getProgressFlow(deckId: String): Flow<DeckProgressEntity?> {
        return deckDao.getProgressFlow(deckId)
    }

    override suspend fun deleteProgress(deckId: String) {
        deckDao.deleteProgress(deckId)
    }

    private fun toShuffledDeck(entity: DeckEntity, midwayResumeMs: Long = 0L): ShuffledDeck {
        val cards = runCatching {
            json.decodeFromString<List<MediaCard>>(entity.cardsJson)
        }.getOrDefault(emptyList())

        return ShuffledDeck(
            deckId = entity.deckId,
            name = entity.name,
            seriesId = entity.seriesId,
            channelId = entity.channelId,
            cards = cards,
            currentIndex = entity.currentIndex,
            cycleNumber = entity.cycleNumber,
            midwayResumeMs = midwayResumeMs,
            strictQueueMode = entity.strictQueueMode,
            updatedAt = entity.updatedAt
        )
    }
}
