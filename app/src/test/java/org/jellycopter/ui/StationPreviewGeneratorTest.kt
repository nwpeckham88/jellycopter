package org.jellycopter.ui

import io.mockk.every
import io.mockk.mockk
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.StationPreviewGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPreviewGeneratorTest {

    @Test
    fun testExtractShowIdsValidJson() {
        val json = """["show-1", "show-2", "show-3"]"""
        val ids = StationPreviewGenerator.extractShowIds(json)
        assertEquals(listOf("show-1", "show-2", "show-3"), ids)
    }

    @Test
    fun testExtractShowIdsMalformedJsonReturnsEmptyList() {
        val ids = StationPreviewGenerator.extractShowIds("not-a-json-list")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun testResolveArtworkBuildsBackdropsAndPosters() {
        val repo = mockk<MediaRepository>()
        every { repo.buildBackdropImageUrl("show-1") } returns "https://jf.local/show1/backdrop"
        every { repo.buildPrimaryImageUrl("show-1") } returns "https://jf.local/show1/poster"
        every { repo.buildBackdropImageUrl("show-2") } returns "https://jf.local/show2/backdrop"
        every { repo.buildPrimaryImageUrl("show-2") } returns "https://jf.local/show2/poster"

        val json = """["show-1", "show-2"]"""
        val artwork = StationPreviewGenerator.resolveArtwork(json, repo)

        assertEquals("https://jf.local/show1/backdrop", artwork.primaryBackdropUrl)
        assertEquals("https://jf.local/show1/poster", artwork.primaryPosterUrl)
        assertEquals(2, artwork.showPosterUrls.size)
        assertEquals(2, artwork.showBackdropUrls.size)
    }

    @Test
    fun testResolveArtworkNullRepoReturnsEmpty() {
        val artwork = StationPreviewGenerator.resolveArtwork("""["show-1"]""", null)
        assertNull(artwork.primaryBackdropUrl)
        assertNull(artwork.primaryPosterUrl)
        assertTrue(artwork.showPosterUrls.isEmpty())
    }

    @Test
    fun testResolveShowTitlesUsesAvailableSeriesNames() {
        val seriesList = listOf(
            SeriesDetail(id = "s1", name = "Breaking Bad"),
            SeriesDetail(id = "s2", name = "Better Call Saul")
        )
        val json = """["s1", "s2", "s3"]"""
        val titles = StationPreviewGenerator.resolveShowTitles(json, null, seriesList)

        assertEquals("Breaking Bad", titles[0])
        assertEquals("Better Call Saul", titles[1])
        assertEquals("s3", titles[2])
    }

    @Test
    fun testExtractShowIdsSingleShowAndPrefixed() {
        // Direct prefixed series ID
        val prefixed = StationPreviewGenerator.extractShowIds("series:show-123")
        assertEquals(listOf("show-123"), prefixed)

        // Single JSON string
        val singleJson = StationPreviewGenerator.extractShowIds("\"show-456\"")
        assertEquals(listOf("show-456"), singleJson)

        // Bracketed single unquoted ID
        val bracketed = StationPreviewGenerator.extractShowIds("[show-789]")
        assertEquals(listOf("show-789"), bracketed)
    }
}
