package org.jellycopter.media

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellycopter.core.database.DeckDao
import org.jellycopter.core.database.DeckEntity
import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShuffledDeck
import org.jellycopter.core.repository.RoomDeckRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomDeckRepositoryTest {

    private lateinit var deckDao: DeckDao
    private lateinit var repository: RoomDeckRepository
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        deckDao = mockk(relaxed = true)
        repository = RoomDeckRepository(deckDao, json)
    }

    @Test
    fun testGetDeckRestoresExhaustedCardsFromExhaustedCardIdsJson() = runTest {
        val card1 = MediaCard(
            id = "card_1",
            seriesId = "series_1",
            seasonId = "season_1",
            categoryKey = "season_1",
            items = listOf(EpisodeItem(id = "ep_1", name = "Ep 1", seriesId = "series_1", seasonId = "season_1", seasonNumber = 1, indexNumber = 1)),
            isExhausted = false
        )
        val card2 = MediaCard(
            id = "card_2",
            seriesId = "series_1",
            seasonId = "season_1",
            categoryKey = "season_1",
            items = listOf(EpisodeItem(id = "ep_2", name = "Ep 2", seriesId = "series_1", seasonId = "season_1", seasonNumber = 1, indexNumber = 2)),
            isExhausted = false
        )

        val entity = DeckEntity(
            deckId = "deck_test",
            name = "Test Deck",
            seriesId = "series_1",
            cycleNumber = 1,
            currentIndex = 1,
            cardsJson = json.encodeToString(listOf(card1, card2)),
            exhaustedCardIdsJson = json.encodeToString(listOf("ep_1")),
            updatedAt = 1000L
        )

        coEvery { deckDao.getDeckById("deck_test") } returns entity
        coEvery { deckDao.getProgress("deck_test") } returns null

        val result = repository.getDeck("deck_test")
        assertNotNull(result)
        assertEquals("deck_test", result!!.deckId)
        assertEquals(2, result.cards.size)
        // card1 should have isExhausted restored as true because ep_1 is in exhaustedCardIdsJson
        assertTrue("Card 1 should be exhausted", result.cards[0].isExhausted)
        assertEquals(false, result.cards[1].isExhausted)
    }

    @Test
    fun testSaveDeckPersistsCardAndItemExhaustedIds() = runTest {
        val card1 = MediaCard(
            id = "card_multi",
            seriesId = "series_1",
            seasonId = "season_1",
            categoryKey = "season_1",
            items = listOf(
                EpisodeItem(id = "ep_part1", name = "Part 1", seriesId = "series_1", seasonId = "season_1", seasonNumber = 1, indexNumber = 1),
                EpisodeItem(id = "ep_part2", name = "Part 2", seriesId = "series_1", seasonId = "season_1", seasonNumber = 1, indexNumber = 2)
            ),
            isExhausted = true
        )

        val deck = ShuffledDeck(
            deckId = "deck_save_test",
            name = "Save Deck",
            seriesId = "series_1",
            cards = listOf(card1),
            currentIndex = 0,
            midwayResumeMs = 5000L
        )

        repository.saveDeck(deck)

        coVerify {
            deckDao.upsertDeck(
                match { entity ->
                    entity.deckId == "deck_save_test" &&
                        entity.exhaustedCardIdsJson.contains("card_multi") &&
                        entity.exhaustedCardIdsJson.contains("ep_part1") &&
                        entity.exhaustedCardIdsJson.contains("ep_part2")
                }
            )
        }

        coVerify {
            deckDao.upsertProgress(
                match { progress ->
                    progress.deckId == "deck_save_test" &&
                        progress.midwayResumeMs == 5000L &&
                        progress.currentEpisodeId == "ep_part1"
                }
            )
        }
    }

    @Test
    fun testChannelEntityDefaultChannelNumber() {
        val channel = org.jellycopter.core.database.ChannelEntity(
            channelId = "ch_test",
            name = "Retro Toons"
        )
        assertEquals(3, channel.channelNumber)
    }

    @Test
    fun testMigration2To3ExecutesAlterTableAddChannelNumber() {
        val db = mockk<androidx.sqlite.db.SupportSQLiteDatabase>(relaxed = true)
        org.jellycopter.core.database.JellycopterDatabase.MIGRATION_2_3.migrate(db)
        io.mockk.verify {
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `channelNumber` INTEGER NOT NULL DEFAULT 3")
        }
    }
}
