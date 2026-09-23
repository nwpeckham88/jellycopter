package org.jellycopter.playback.dv

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Dolby Vision Track Selector and Dynamic RPU Rewriter.
 *
 * Solves the critical Dolby Vision Profile 7 compatibility issue on Android TV devices
 * (such as Nvidia Shield TV Tegra X1+ and Onn 4K Pro Amlogic S905X4):
 *
 * 1. Rewriting Profile 7 (dv_profile=7) RPU metadata on-the-fly to Profile 8.1 (dv_profile=8, compatibility_id=1).
 * 2. Clean fallback to HDR10 base layer on non-DV displays or incompatible decoders,
 *    eliminating the notorious purple/green tint caused by incorrect IPTPQc2 color reshaping.
 */
@UnstableApi
class DolbyVisionTrackSelector(
    val capabilities: DeviceCapabilities = DeviceCapabilities()
) {

    data class TrackOverride(
        val targetFormat: Format,
        val action: Action,
        val reason: String
    ) {
        enum class Action {
            PASSTHROUGH,
            REWRITE_TO_PROFILE_8_1,
            FALLBACK_TO_HDR10
        }
    }

    /**
     * Inspects a video format and applies Dolby Vision Profile 8.1 rewriting or clean HDR10 fallback.
     */
    fun resolveDolbyVisionTrack(
        format: Format,
        customCapabilities: DeviceCapabilities = capabilities
    ): TrackOverride {
        if (!isDolbyVisionFormat(format)) {
            return TrackOverride(
                targetFormat = format,
                action = TrackOverride.Action.PASSTHROUGH,
                reason = "Not a Dolby Vision track"
            )
        }

        val isProfile7 = isProfile7Format(format)

        return if (isProfile7) {
            if (customCapabilities.supportsDvProfile81 && !customCapabilities.supportsDvProfile7) {
                // Device supports Profile 8.1 but cannot handle Profile 7 dual-layer (e.g. Shield TV, Onn 4K Pro)
                val rewritten = rewriteToProfile81(format)
                TrackOverride(
                    targetFormat = rewritten,
                    action = TrackOverride.Action.REWRITE_TO_PROFILE_8_1,
                    reason = "Rewrote Profile 7 to Profile 8.1 with HDR10 compatibility ID for Shield TV / Onn 4K Pro"
                )
            } else if (!customCapabilities.supportsDolbyVision || !customCapabilities.supportsDvProfile81) {
                // Display does not support Dolby Vision or decoder cannot handle Profile 8.1
                val hdr10 = cleanFallbackToHdr10(format)
                TrackOverride(
                    targetFormat = hdr10,
                    action = TrackOverride.Action.FALLBACK_TO_HDR10,
                    reason = "Clean fallback to HDR10 base layer to eliminate purple/green tint on non-DV display"
                )
            } else {
                TrackOverride(
                    targetFormat = format,
                    action = TrackOverride.Action.PASSTHROUGH,
                    reason = "Device supports native Profile 7 decoding"
                )
            }
        } else {
            // Other DV profiles (e.g. Profile 8 or Profile 5)
            if (!customCapabilities.supportsDolbyVision) {
                val hdr10 = cleanFallbackToHdr10(format)
                TrackOverride(
                    targetFormat = hdr10,
                    action = TrackOverride.Action.FALLBACK_TO_HDR10,
                    reason = "Fell back to HDR10 base layer because display lacks Dolby Vision support"
                )
            } else {
                TrackOverride(
                    targetFormat = format,
                    action = TrackOverride.Action.PASSTHROUGH,
                    reason = "Dolby Vision supported natively"
                )
            }
        }
    }

    /**
     * Rewrites a Profile 7 Format to Profile 8.1.
     */
    fun rewriteToProfile81(format: Format): Format {
        val oldCodecs = format.codecs ?: "dvh1.07.06"
        val newCodecs = if (oldCodecs.contains(".07.")) {
            oldCodecs.replace(".07.", ".08.")
        } else {
            "dvh1.08.06"
        }

        val newInitData = mutableListOf<ByteArray>()
        if (format.initializationData.isNotEmpty()) {
            for (data in format.initializationData) {
                val parsed = DolbyVisionConfigRecord.parse(data)
                if (parsed != null && parsed.profile == DolbyVisionConfigRecord.PROFILE_DV_PROFILE_7) {
                    newInitData.add(DolbyVisionConfigRecord.rewriteProfile7To81(data))
                } else {
                    newInitData.add(data.copyOf())
                }
            }
        } else {
            // Generate standard Profile 8.1 config record
            newInitData.add(DolbyVisionConfigRecord.create(
                profile = DolbyVisionConfigRecord.PROFILE_DV_PROFILE_8,
                level = 6,
                rpuPresent = true,
                elPresent = false,
                blPresent = true,
                compatibilityId = DolbyVisionConfigRecord.COMPATIBILITY_ID_HDR10
            ))
        }

        val hdr10ColorInfo = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()

        return format.buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs(newCodecs)
            .setInitializationData(newInitData)
            .setColorInfo(hdr10ColorInfo)
            .build()
    }

    /**
     * Cleanly falls back a Dolby Vision stream to its HDR10 Base Layer,
     * stripping DV metadata and config records to eliminate purple/green tint.
     */
    fun cleanFallbackToHdr10(format: Format): Format {
        val hdr10ColorInfo = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()

        return format.buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs("hev1.2.4.L153.B0")
            .setInitializationData(emptyList()) // Strip DV configuration box
            .setColorInfo(hdr10ColorInfo)
            .build()
    }

    companion object {
        const val NAL_TYPE_UNSPEC62_RPU: Int = 62

        fun isDolbyVisionFormat(format: Format): Boolean {
            val mime = format.sampleMimeType ?: ""
            val codecs = format.codecs ?: ""
            return mime.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) ||
                    codecs.startsWith("dvh1", ignoreCase = true) ||
                    codecs.startsWith("dvhe", ignoreCase = true)
        }

        fun isProfile7Format(format: Format): Boolean {
            val codecs = format.codecs ?: ""
            if (codecs.contains(".07.")) return true

            for (data in format.initializationData) {
                val record = DolbyVisionConfigRecord.parse(data)
                if (record != null && record.profile == DolbyVisionConfigRecord.PROFILE_DV_PROFILE_7) {
                    return true
                }
            }
            return false
        }

        /**
         * Checks whether an HEVC NAL unit header represents a Dolby Vision RPU (NAL unit type 62 / 0x3E).
         * In Annex B byte streams, the NAL header byte follows the start code (0x000001 or 0x00000001).
         *
         * NAL unit type: (byte0 ushr 1) and 0x3F.
         */
        fun isRpuNalHeader(nalHeaderByte0: Byte): Boolean {
            val nalType = (nalHeaderByte0.toInt() ushr 1) and 0x3F
            return nalType == NAL_TYPE_UNSPEC62_RPU
        }
    }
}
