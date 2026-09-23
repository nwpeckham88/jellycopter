package org.jellycopter.playback.dv

/**
 * Parser and rewriter for Dolby Vision Configuration Records (dvcC / dvvC).
 *
 * Implements the Dolby Vision bitstream specification for configuration boxes:
 * - Byte 0: dv_version_major
 * - Byte 1: dv_version_minor
 * - Byte 2: dv_profile (7 bits) | dv_level high bit (1 bit)
 * - Byte 3: dv_level low bits (5 bits) | rpu_present_flag (1 bit) | el_present_flag (1 bit) | bl_present_flag (1 bit)
 * - Byte 4: dv_bl_signal_compatibility_id (4 bits) | reserved (4 bits)
 */
data class DolbyVisionConfigRecord(
    val versionMajor: Int,
    val versionMinor: Int,
    val profile: Int,
    val level: Int,
    val rpuPresent: Boolean,
    val elPresent: Boolean,
    val blPresent: Boolean,
    val compatibilityId: Int,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DolbyVisionConfigRecord
        return profile == other.profile &&
                level == other.level &&
                compatibilityId == other.compatibilityId &&
                rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = profile
        result = 31 * result + level
        result = 31 * result + compatibilityId
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }

    companion object {
        const val PROFILE_DV_PROFILE_5 = 5
        const val PROFILE_DV_PROFILE_7 = 7
        const val PROFILE_DV_PROFILE_8 = 8

        const val COMPATIBILITY_ID_NONE = 0
        const val COMPATIBILITY_ID_HDR10 = 1
        const val COMPATIBILITY_ID_SDR = 2
        const val COMPATIBILITY_ID_HLG = 4
        const val COMPATIBILITY_ID_BLU_RAY = 6

        /**
         * Parses a raw Dolby Vision configuration record byte array.
         */
        fun parse(data: ByteArray): DolbyVisionConfigRecord? {
            if (data.size < 5) return null

            val major = data[0].toInt() and 0xFF
            val minor = data[1].toInt() and 0xFF

            val b2 = data[2].toInt() and 0xFF
            val profile = b2 ushr 1
            val levelHigh = b2 and 0x01

            val b3 = data[3].toInt() and 0xFF
            val levelLow = (b3 ushr 3) and 0x1F
            val level = (levelHigh shl 5) or levelLow

            val rpuPresent = (b3 and 0x04) != 0
            val elPresent = (b3 and 0x02) != 0
            val blPresent = (b3 and 0x01) != 0

            val b4 = data[4].toInt() and 0xFF
            val compatibilityId = (b4 ushr 4) and 0x0F

            return DolbyVisionConfigRecord(
                versionMajor = major,
                versionMinor = minor,
                profile = profile,
                level = level,
                rpuPresent = rpuPresent,
                elPresent = elPresent,
                blPresent = blPresent,
                compatibilityId = compatibilityId,
                rawBytes = data.copyOf()
            )
        }

        /**
         * Creates a standard 24-byte Dolby Vision configuration record.
         */
        fun create(
            profile: Int,
            level: Int = 6,
            rpuPresent: Boolean = true,
            elPresent: Boolean = false,
            blPresent: Boolean = true,
            compatibilityId: Int = 1
        ): ByteArray {
            val record = ByteArray(24)
            record[0] = 1 // major
            record[1] = 0 // minor

            val levelHigh = (level ushr 5) and 0x01
            record[2] = (((profile and 0x7F) shl 1) or levelHigh).toByte()

            val levelLow = level and 0x1F
            var b3 = (levelLow shl 3)
            if (rpuPresent) b3 = b3 or 0x04
            if (elPresent) b3 = b3 or 0x02
            if (blPresent) b3 = b3 or 0x01
            record[3] = b3.toByte()

            record[4] = ((compatibilityId and 0x0F) shl 4).toByte()
            return record
        }

        /**
         * Rewrites a Profile 7 configuration record into a Profile 8.1 configuration record:
         * - Changes dv_profile from 7 to 8
         * - Changes dv_bl_signal_compatibility_id to 1 (HDR10)
         * - Clears el_present_flag (Profile 8.1 is single layer)
         */
        fun rewriteProfile7To81(data: ByteArray): ByteArray {
            val copy = data.copyOf()
            if (copy.size < 5) return copy

            // Byte 2: profile (bits 7..1), keep bit 0 (level high)
            val b2 = copy[2].toInt() and 0xFF
            val levelHigh = b2 and 0x01
            copy[2] = (((PROFILE_DV_PROFILE_8 and 0x7F) shl 1) or levelHigh).toByte()

            // Byte 3: clear EL present flag (bit 1)
            val b3 = copy[3].toInt() and 0xFF
            val newB3 = b3 and 0x02.inv()
            copy[3] = newB3.toByte()

            // Byte 4: set compatibility ID to 1 (bits 7..4)
            val b4 = copy[4].toInt() and 0xFF
            val reserved = b4 and 0x0F
            copy[4] = (((COMPATIBILITY_ID_HDR10 and 0x0F) shl 4) or reserved).toByte()

            return copy
        }
    }
}
