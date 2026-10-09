package org.jellycopter.playback.dv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class DolbyVisionTrackSelectorTest {

    @Test
    fun testProfile7RecordParsingAndRewritingTo81() {
        // Create standard Profile 7 config record (dual-layer MEL/FEL, Blu-ray compatibility)
        val rawP7 = DolbyVisionConfigRecord.create(
            profile = DolbyVisionConfigRecord.PROFILE_DV_PROFILE_7,
            level = 6,
            rpuPresent = true,
            elPresent = true,
            blPresent = true,
            compatibilityId = DolbyVisionConfigRecord.COMPATIBILITY_ID_BLU_RAY
        )

        val parsedP7 = DolbyVisionConfigRecord.parse(rawP7)
        requireNotNull(parsedP7)
        assertEquals(7, parsedP7.profile)
        assertTrue(parsedP7.rpuPresent)
        assertTrue(parsedP7.elPresent)
        assertEquals(DolbyVisionConfigRecord.COMPATIBILITY_ID_BLU_RAY, parsedP7.compatibilityId)

        // Rewrite on-the-fly to Profile 8.1
        val rewrittenBytes = DolbyVisionConfigRecord.rewriteProfile7To81(rawP7)
        val parsedP8 = DolbyVisionConfigRecord.parse(rewrittenBytes)
        requireNotNull(parsedP8)

        // Verify transformed Profile 8.1 structure
        assertEquals(8, parsedP8.profile)
        assertTrue(parsedP8.rpuPresent)
        assertFalse("EL present flag must be cleared for Profile 8.1 single-layer", parsedP8.elPresent)
        assertEquals(
            "Compatibility ID must be 1 (standard HDR10 base layer)",
            DolbyVisionConfigRecord.COMPATIBILITY_ID_HDR10,
            parsedP8.compatibilityId
        )
    }

    @Test
    fun testShieldTvRewritesProfile7ToProfile81() {
        val shieldTvCaps = DeviceCapabilities.createShieldTv()
        val selector = DolbyVisionTrackSelector(shieldTvCaps)

        val rawP7Record = DolbyVisionConfigRecord.create(
            profile = 7,
            level = 6,
            rpuPresent = true,
            elPresent = true,
            blPresent = true,
            compatibilityId = 6
        )

        val formatP7 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvh1.07.06")
            .setInitializationData(listOf(rawP7Record))
            .build()

        val override = selector.resolveDolbyVisionTrack(formatP7)

        assertEquals(DolbyVisionTrackSelector.TrackOverride.Action.REWRITE_TO_PROFILE_8_1, override.action)
        assertEquals(MimeTypes.VIDEO_DOLBY_VISION, override.targetFormat.sampleMimeType)
        assertEquals("dvh1.08.06", override.targetFormat.codecs)

        // Verify config record in initializationData was rewritten to Profile 8
        val rewrittenInit = override.targetFormat.initializationData.firstOrNull()
        requireNotNull(rewrittenInit)
        val parsed = DolbyVisionConfigRecord.parse(rewrittenInit)
        requireNotNull(parsed)
        assertEquals(8, parsed.profile)
        assertEquals(1, parsed.compatibilityId)
    }

    @Test
    fun testOnn4kProRewritesProfile7ToProfile81() {
        val onnCaps = DeviceCapabilities.createOnn4kPro()
        val selector = DolbyVisionTrackSelector(onnCaps)

        val formatP7 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.07.06")
            .build()

        val override = selector.resolveDolbyVisionTrack(formatP7)

        assertEquals(DolbyVisionTrackSelector.TrackOverride.Action.REWRITE_TO_PROFILE_8_1, override.action)
        assertEquals("dvhe.08.06", override.targetFormat.codecs)
        assertEquals(MimeTypes.VIDEO_DOLBY_VISION, override.targetFormat.sampleMimeType)
    }

    @Test
    fun testNonDvDisplayTriggersCleanHdr10Fallback() {
        val nonDvCaps = DeviceCapabilities.createNonDvHdr10()
        val selector = DolbyVisionTrackSelector(nonDvCaps)

        val rawP7Record = DolbyVisionConfigRecord.create(profile = 7, compatibilityId = 6)
        val formatP7 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvh1.07.06")
            .setInitializationData(listOf(rawP7Record))
            .build()

        val override = selector.resolveDolbyVisionTrack(formatP7)

        assertEquals(DolbyVisionTrackSelector.TrackOverride.Action.FALLBACK_TO_HDR10, override.action)
        // Eliminates green/purple tint by switching to pure HEVC HDR10 base layer
        assertEquals(MimeTypes.VIDEO_H265, override.targetFormat.sampleMimeType)
        assertTrue("Initialization data must be stripped for HDR10 fallback", override.targetFormat.initializationData.isEmpty())

        val colorInfo = override.targetFormat.colorInfo
        requireNotNull(colorInfo)
        assertEquals(C.COLOR_SPACE_BT2020, colorInfo.colorSpace)
        assertEquals(C.COLOR_TRANSFER_ST2084, colorInfo.colorTransfer)
    }

    @Test
    fun testRpuNalHeaderDetection() {
        // NAL header byte for type 62 (UNSPEC62):
        // (0x7C ushr 1) and 0x3F = 62
        val rpuHeaderByte: Byte = 0x7C.toByte()
        assertTrue(DolbyVisionTrackSelector.isRpuNalHeader(rpuHeaderByte))

        // NAL header for IDR slice (type 19): (0x26 ushr 1) and 0x3F = 19
        val idrHeaderByte: Byte = 0x26.toByte()
        assertFalse(DolbyVisionTrackSelector.isRpuNalHeader(idrHeaderByte))
    }

    @Test
    fun testProfile5OnNonDvDisplayRoutesToMpv() {
        val nonDvCaps = DeviceCapabilities.createNonDvHdr10()
        val selector = DolbyVisionTrackSelector(nonDvCaps)

        val rawP5Record = DolbyVisionConfigRecord.create(
            profile = 5,
            level = 6,
            rpuPresent = true,
            elPresent = false,
            blPresent = true,
            compatibilityId = 0
        )
        val formatP5 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvh1.05.06")
            .setInitializationData(listOf(rawP5Record))
            .build()

        val override = selector.resolveDolbyVisionTrack(formatP5)

        // Must be excluded from HDR10 fallback (which causes green/purple color shifts) and routed to MPV/transcode
        assertEquals(DolbyVisionTrackSelector.TrackOverride.Action.ROUTE_TO_MPV, override.action)
    }

    @Test
    fun testProfile5OnDvDisplayPassesThrough() {
        val shieldCaps = DeviceCapabilities.createShieldTv()
        val selector = DolbyVisionTrackSelector(shieldCaps)

        val formatP5 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.05.06")
            .build()

        val override = selector.resolveDolbyVisionTrack(formatP5)

        assertEquals(DolbyVisionTrackSelector.TrackOverride.Action.PASSTHROUGH, override.action)
    }
}
