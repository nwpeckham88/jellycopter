package org.jellycopter.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AchievementTier
import org.jellycopter.core.model.DeckConfig
import org.jellycopter.core.model.DrcConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.MediaDiscoverItem
import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.core.model.PlaybackState
import org.jellycopter.core.model.SeasonStatus
import org.jellycopter.core.model.ShowConfig
import org.jellycopter.core.model.ShuffledDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testEpisodeItemAndMediaCardSerialization() {
        val ep1 = EpisodeItem(
            id = "ep_01",
            name = "The Best of Both Worlds, Part 1",
            seriesId = "series_tng",
            seriesName = "Star Trek: TNG",
            seasonId = "season_03",
            seasonNumber = 3,
            indexNumber = 26,
            runTimeTicks = 27000000000L,
            playCount = 1.0
        )
        val ep2 = EpisodeItem(
            id = "ep_02",
            name = "The Best of Both Worlds, Part 2",
            seriesId = "series_tng",
            seriesName = "Star Trek: TNG",
            seasonId = "season_04",
            seasonNumber = 4,
            indexNumber = 1,
            runTimeTicks = 27000000000L,
            playCount = 0.0
        )

        val multiPartCard = MediaCard(
            id = "card_bobw",
            seriesId = "series_tng",
            seasonId = "season_03",
            categoryKey = "season_03",
            items = listOf(ep1, ep2),
            isMultiPart = true,
            seasonNumber = 3
        )

        assertTrue(multiPartCard.isMultiPart)
        assertEquals(listOf("ep_01", "ep_02"), multiPartCard.itemIds)

        val encoded = json.encodeToString(multiPartCard)
        val decoded = json.decodeFromString<MediaCard>(encoded)

        assertEquals("card_bobw", decoded.id)
        assertEquals(2, decoded.items.size)
        assertEquals("ep_01", decoded.items[0].id)
        assertEquals("ep_02", decoded.items[1].id)
        assertTrue(decoded.isMultiPart)
    }

    @Test
    fun testShuffledDeckState() {
        val ep = EpisodeItem(
            id = "ep_100",
            name = "Simpsons Roasting on an Open Fire",
            seriesId = "series_simpsons",
            seriesName = "The Simpsons",
            seasonId = "season_01",
            seasonNumber = 1,
            indexNumber = 1
        )
        val card = MediaCard(
            id = "card_100",
            seriesId = "series_simpsons",
            seasonId = "season_01",
            categoryKey = "season_01",
            items = listOf(ep)
        )

        val deck = ShuffledDeck(
            deckId = "deck_simpsons",
            name = "The Simpsons Shuffle",
            seriesId = "series_simpsons",
            cards = listOf(card),
            currentIndex = 0,
            cycleNumber = 1
        )

        assertFalse(deck.isExhausted)
        assertEquals(card, deck.currentCard)
        assertEquals(0f, deck.progressPercent, 0.01f)

        val advancedDeck = deck.copy(currentIndex = 1)
        assertTrue(advancedDeck.isExhausted)
        assertEquals(100f, advancedDeck.progressPercent, 0.01f)
    }

    @Test
    fun testDeckAndShowConfigs() {
        val deckConfig = DeckConfig(
            strictQueueMode = true,
            crossCycleBoundaryBufferRatio = 0.25f,
            cooldownThresholdRatio = 0.80f,
            antiClusteringTightnessThreshold = 0.5f
        )
        val jsonDeckConfig = json.encodeToString(deckConfig)
        val decodedDeckConfig = json.decodeFromString<DeckConfig>(jsonDeckConfig)
        assertEquals(deckConfig, decodedDeckConfig)

        val showConfig = ShowConfig(
            seriesId = "series_simpsons",
            seriesName = "The Simpsons",
            weight = 3,
            seasons = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        )
        val jsonShowConfig = json.encodeToString(showConfig)
        val decodedShowConfig = json.decodeFromString<ShowConfig>(jsonShowConfig)
        assertEquals(showConfig, decodedShowConfig)
    }

    @Test
    fun testPlaybackStateAndDrcConfig() {
        val playback = PlaybackState(
            deckId = "deck_01",
            currentCardId = "card_01",
            currentEpisodeId = "ep_01",
            positionMs = 15000L,
            durationMs = 1200000L,
            isPlaying = true
        )
        val jsonPlayback = json.encodeToString(playback)
        val decodedPlayback = json.decodeFromString<PlaybackState>(jsonPlayback)
        assertEquals(playback, decodedPlayback)

        val drc = DrcConfig(
            enabled = true,
            dialogueFloorBoostDb = 6.0f,
            transientClampingCeilingDb = -3.0f,
            compressionRatio = 4.0f,
            compressionThresholdDb = -18.0f,
            vocalPeakingEqEnabled = true,
            vocalPeakingGainDb = 3.5f,
            vocalPeakingFrequencyHz = 2500.0f,
            vocalPeakingQ = 1.0f
        )
        val jsonDrc = json.encodeToString(drc)
        val decodedDrc = json.decodeFromString<DrcConfig>(jsonDrc)
        assertEquals(drc, decodedDrc)
    }

    @Test
    fun testAchievementModel() {
        val achievement = Achievement(
            id = "trip_to_the_moon",
            title = "Trip to the Moon",
            description = "Accumulate 72 hours of total playback",
            tier = AchievementTier.PLATINUM,
            points = 100,
            isUnlocked = false,
            progress = 36.0f,
            maxProgress = 72.0f
        )
        val jsonAchievement = json.encodeToString(achievement)
        val decoded = json.decodeFromString<Achievement>(jsonAchievement)
        assertEquals(AchievementTier.PLATINUM, decoded.tier)
        assertEquals(100, decoded.points)
        assertFalse(decoded.isUnlocked)
    }

    @Test
    fun testSeasonStatusAndJellyseerrModels() {
        val seasonStatus = SeasonStatus(
            seasonNumber = 3,
            isAvailableLocally = false,
            isRequested = true,
            requestId = 42,
            requestStatus = 2,
            totalEpisodes = 24,
            localEpisodes = 0
        )
        val jsonStatus = json.encodeToString(seasonStatus)
        val decodedStatus = json.decodeFromString<SeasonStatus>(jsonStatus)
        assertEquals(seasonStatus, decodedStatus)

        val discoverItem = MediaDiscoverItem(
            id = 550,
            tmdbId = 550,
            mediaType = "movie",
            title = "Fight Club",
            overview = "An insomniac office worker...",
            voteAverage = 8.4,
            status = 5
        )
        val jsonDiscover = json.encodeToString(discoverItem)
        val decodedDiscover = json.decodeFromString<MediaDiscoverItem>(jsonDiscover)
        assertEquals("Fight Club", decodedDiscover.title)
        assertEquals(5, decodedDiscover.status)

        val requestPayload = MediaRequestPayload(
            mediaType = "tv",
            mediaId = 1399,
            is4k = false,
            seasons = listOf(1, 2)
        )
        val jsonRequest = json.encodeToString(requestPayload)
        val decodedRequest = json.decodeFromString<MediaRequestPayload>(jsonRequest)
        assertEquals(requestPayload, decodedRequest)
    }
}
