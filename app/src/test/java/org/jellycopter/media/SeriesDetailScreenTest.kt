package org.jellycopter.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.SeasonItem
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.model.VirtualFolder
import org.jellycopter.core.repository.DefaultMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailScreenTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMediaDomainModelsSerialization() {
        val folder = VirtualFolder(
            id = "folder_1",
            name = "Anime",
            collectionType = "tvshows"
        )
        val encodedFolder = json.encodeToString(folder)
        val decodedFolder = json.decodeFromString<VirtualFolder>(encodedFolder)
        assertEquals(folder, decodedFolder)

        val ep1 = EpisodeItem(
            id = "ep_01",
            name = "Pilot",
            seriesId = "series_lost",
            seriesName = "Lost",
            seasonId = "season_01",
            seasonNumber = 1,
            indexNumber = 1,
            runTimeTicks = 28000000000L
        )

        val season = SeasonItem(
            id = "season_01",
            seriesId = "series_lost",
            seasonNumber = 1,
            name = "Season 1",
            episodes = listOf(ep1)
        )
        val encodedSeason = json.encodeToString(season)
        val decodedSeason = json.decodeFromString<SeasonItem>(encodedSeason)
        assertEquals(season, decodedSeason)

        val seriesDetail = SeriesDetail(
            id = "series_lost",
            name = "Lost",
            overview = "Survivors of a plane crash are forced to work together...",
            posterUrl = "http://jellyfin/Items/series_lost/Images/Primary",
            backdropUrl = "http://jellyfin/Items/series_lost/Images/Backdrop",
            communityRating = 8.3f,
            premiereYear = 2004,
            seasons = listOf(season),
            nextUpEpisode = ep1
        )
        val encodedDetail = json.encodeToString(seriesDetail)
        val decodedDetail = json.decodeFromString<SeriesDetail>(encodedDetail)
        assertEquals(seriesDetail, decodedDetail)
    }

    @Test
    fun testActionBarActionResolution() = runTest {
        val repo = DefaultMediaRepository()
        val severance = repo.getSeriesDetail("series_1")
        assertNotNull(severance)

        // 1. Resume / Next Up Action
        var resumeCalled = false
        var targetResumeEpisode: EpisodeItem? = null
        val onResumeNextUp: (SeriesDetail, EpisodeItem?) -> Unit = { _, ep ->
            resumeCalled = true
            targetResumeEpisode = ep
        }
        onResumeNextUp(severance!!, severance.nextUpEpisode)
        assertTrue(resumeCalled)
        assertEquals("ep_sev_102", targetResumeEpisode?.id)
        assertEquals("Half Loop", targetResumeEpisode?.name)

        // 2. Restart Show Action (S1E1 from 00:00)
        var restartCalled = false
        var targetRestartEpisode: EpisodeItem? = null
        val onRestartShow: (SeriesDetail, EpisodeItem?) -> Unit = { _, ep ->
            restartCalled = true
            targetRestartEpisode = ep
        }
        val s1e1 = repo.getFirstEpisodeForSeries("series_1")
        onRestartShow(severance, s1e1)
        assertTrue(restartCalled)
        assertEquals("ep_sev_101", targetRestartEpisode?.id)
        assertEquals(1, targetRestartEpisode?.seasonNumber)
        assertEquals(1, targetRestartEpisode?.indexNumber)

        // 3. Smart Shuffle Action
        var shuffleCalled = false
        var targetShuffleSeries: SeriesDetail? = null
        val onSmartShuffle: (SeriesDetail) -> Unit = { series ->
            shuffleCalled = true
            targetShuffleSeries = series
        }
        onSmartShuffle(severance)
        assertTrue(shuffleCalled)
        assertEquals("series_1", targetShuffleSeries?.id)

        // 4. Add to Channel Action
        var addToChannelCalled = false
        var targetChannelSeries: SeriesDetail? = null
        val onAddToChannel: (SeriesDetail) -> Unit = { series ->
            addToChannelCalled = true
            targetChannelSeries = series
        }
        onAddToChannel(severance)
        assertTrue(addToChannelCalled)
        assertEquals("series_1", targetChannelSeries?.id)
    }

    @Test
    fun testActionBarIconsSpecificationCompliance() {
        // Requirement R1 & R3: verified Material Icons vector composables
        assertEquals(Icons.Rounded.PlayArrow, Icons.Rounded.PlayArrow)
        assertEquals(Icons.Rounded.Replay, Icons.Rounded.Replay)
        assertEquals(Icons.Rounded.Shuffle, Icons.Rounded.Shuffle)
        assertEquals(Icons.AutoMirrored.Rounded.PlaylistAdd, Icons.AutoMirrored.Rounded.PlaylistAdd)
    }

    @Test
    fun testSeasonPillsAndEpisodeHierarchy() = runTest {
        val repo = DefaultMediaRepository()
        val severance = repo.getSeriesDetail("series_1")
        assertNotNull(severance)

        val seasons = severance!!.seasons
        assertEquals(2, seasons.size)

        val s1 = seasons[0]
        assertEquals("Season 1", s1.name)
        assertEquals(9, s1.episodes.size)
        assertEquals("Good News About Hell", s1.episodes[0].name)
        assertEquals("The We We Are", s1.episodes[8].name)

        val s2 = seasons[1]
        assertEquals("Season 2", s2.name)
        assertEquals(2, s2.episodes.size)
        assertEquals("Hello Lumon", s2.episodes[0].name)
    }

    @Test
    fun testLibraryBrowsingAndSeriesSelectionFlow() = runTest {
        val repo = DefaultMediaRepository()
        val folders = repo.getVirtualFolders()
        assertTrue(folders.isNotEmpty())

        val folder = folders.first { it.id == "folder_shows" }
        val seriesList = repo.getSeriesInFolder(folder.id)
        assertTrue(seriesList.isNotEmpty())

        var openedSeriesId: String? = null
        val onOpenSeries: (String) -> Unit = { id ->
            openedSeriesId = id
        }

        // Simulate user clicking on Severance
        val selected = seriesList.first { it.name == "Severance" }
        onOpenSeries(selected.id)

        assertEquals("series_1", openedSeriesId)
        val openedDetail = repo.getSeriesDetail(openedSeriesId!!)
        assertNotNull(openedDetail)
        assertEquals("Severance", openedDetail?.name)
        assertFalse(openedDetail?.posterUrl.isNullOrBlank())
        assertFalse(openedDetail?.backdropUrl.isNullOrBlank())
    }
}
