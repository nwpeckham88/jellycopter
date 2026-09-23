package org.jellycopter.playback.dsp

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.jellycopter.core.model.DrcConfig

/**
 * Composite AudioProcessor Chain for Media3 Playback Sink.
 *
 * Maintains and controls the three key DSP processors:
 * 1. ItuRBs775Downmixer
 * 2. NightModeDrcFilter
 * 3. VocalFormantFilter
 */
@UnstableApi
class PlaybackAudioProcessorChain(
    initialCenterBoostDb: Float = 3.0f,
    initialDrcConfig: DrcConfig = DrcConfig()
) {

    val downmixer = ItuRBs775Downmixer(initialCenterBoostDb)
    val drcFilter = NightModeDrcFilter(initialDrcConfig)
    val vocalFilter = VocalFormantFilter(
        initialEnabled = initialDrcConfig.vocalPeakingEqEnabled,
        initialFrequencyHz = initialDrcConfig.vocalPeakingFrequencyHz,
        initialGainDb = initialDrcConfig.vocalPeakingGainDb,
        initialQ = initialDrcConfig.vocalPeakingQ
    )

    fun getAudioProcessors(): Array<AudioProcessor> {
        return arrayOf(downmixer, drcFilter, vocalFilter)
    }

    fun setForceStereo(enabled: Boolean, centerBoostDb: Float = 3.0f) {
        downmixer.enabled = enabled
        downmixer.centerBoostDb = centerBoostDb
    }

    fun setAudioNightMode(enabled: Boolean, config: DrcConfig = DrcConfig()) {
        val updatedConfig = config.copy(enabled = enabled)
        drcFilter.config = updatedConfig
        vocalFilter.enabled = enabled && config.vocalPeakingEqEnabled
        vocalFilter.frequencyHz = config.vocalPeakingFrequencyHz
        vocalFilter.gainDb = config.vocalPeakingGainDb
        vocalFilter.q = config.vocalPeakingQ
    }
}
