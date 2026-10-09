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

    private var userForceStereoEnabled: Boolean = false
    private var userCenterBoostDb: Float = initialCenterBoostDb
    private var userNightModeEnabled: Boolean = initialDrcConfig.enabled
    private var userDrcConfig: DrcConfig = initialDrcConfig

    /**
     * When true, indicates the server is already downmixing to stereo / applying DRC & voice boost.
     * The local audio processor chain automatically bypasses downmixing and DRC to prevent
     * double-compression, severe dialogue pumping, and shrill acoustic artifacts.
     */
    var isServerProcessingActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyEffectiveState()
            }
        }

    fun getAudioProcessors(): Array<AudioProcessor> {
        return arrayOf(downmixer, drcFilter, vocalFilter)
    }

    fun setForceStereo(enabled: Boolean, centerBoostDb: Float = 3.0f) {
        userForceStereoEnabled = enabled
        userCenterBoostDb = centerBoostDb
        applyEffectiveState()
    }

    fun setAudioNightMode(enabled: Boolean, config: DrcConfig = DrcConfig()) {
        userNightModeEnabled = enabled
        userDrcConfig = config.copy(enabled = enabled)
        applyEffectiveState()
    }

    private fun applyEffectiveState() {
        if (isServerProcessingActive) {
            // Bypass local DSP: server has already processed / downmixed the stream
            downmixer.enabled = false
            drcFilter.config = userDrcConfig.copy(enabled = false)
            vocalFilter.enabled = false
        } else {
            // Apply requested user DSP configurations
            downmixer.enabled = userForceStereoEnabled
            downmixer.centerBoostDb = userCenterBoostDb

            drcFilter.config = userDrcConfig.copy(enabled = userNightModeEnabled)
            vocalFilter.enabled = userNightModeEnabled && userDrcConfig.vocalPeakingEqEnabled
            vocalFilter.frequencyHz = userDrcConfig.vocalPeakingFrequencyHz
            vocalFilter.gainDb = userDrcConfig.vocalPeakingGainDb
            vocalFilter.q = userDrcConfig.vocalPeakingQ
        }
    }
}
