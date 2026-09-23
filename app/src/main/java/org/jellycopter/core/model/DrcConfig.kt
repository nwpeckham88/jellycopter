package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DrcConfig(
    val enabled: Boolean = false,
    val dialogueFloorBoostDb: Float = 6.0f,
    val transientClampingCeilingDb: Float = -3.0f,
    val compressionRatio: Float = 4.0f,
    val compressionThresholdDb: Float = -18.0f,
    val attackTimeMs: Float = 10.0f,
    val releaseTimeMs: Float = 300.0f,
    val vocalPeakingEqEnabled: Boolean = true,
    val vocalPeakingGainDb: Float = 3.5f,
    val vocalPeakingFrequencyHz: Float = 2500.0f,
    val vocalPeakingQ: Float = 1.0f
)
