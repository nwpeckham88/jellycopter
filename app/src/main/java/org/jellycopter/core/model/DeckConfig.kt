package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DeckConfig(
    val strictQueueMode: Boolean = false,
    val crossCycleBoundaryBufferRatio: Float = 0.20f,
    val cooldownThresholdRatio: Float = 0.75f,
    val antiClusteringTightnessThreshold: Float = 0.5f
)
