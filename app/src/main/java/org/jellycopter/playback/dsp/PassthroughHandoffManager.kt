package org.jellycopter.playback.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Smart Passthrough Handoff Manager.
 *
 * Coordinates transparent state transitions between:
 * - HDMI bitstream passthrough (AC-3, E-AC-3, Dolby TrueHD, DTS) directly to an AVR.
 * - Decoded multi-channel PCM DSP (when Audio Night Mode or Force Stereo is active).
 * - PCM Direct (standard decoded PCM without DSP when passthrough is unsupported).
 *
 * Ensures zero-click, zero-pop transitions between bitstream and PCM modes.
 */
class PassthroughHandoffManager(
    initialPassthroughSupported: Boolean = true
) {

    enum class AudioRoutingMode {
        PASSTHROUGH_BITSTREAM,
        PCM_DSP,
        PCM_DIRECT
    }

    fun interface RoutingChangeListener {
        fun onRoutingChanged(oldMode: AudioRoutingMode, newMode: AudioRoutingMode)
    }

    private val listeners = mutableListOf<RoutingChangeListener>()

    private val _currentMode = MutableStateFlow(
        if (initialPassthroughSupported) AudioRoutingMode.PASSTHROUGH_BITSTREAM else AudioRoutingMode.PCM_DIRECT
    )
    val currentMode: StateFlow<AudioRoutingMode> = _currentMode.asStateFlow()

    var isNightModeEnabled: Boolean = false
        private set

    var isForceStereoEnabled: Boolean = false
        private set

    var isServerDownmixActive: Boolean = false
        private set

    var isPassthroughSupported: Boolean = initialPassthroughSupported
        private set

    var currentMimeType: String? = null
        private set

    fun addListener(listener: RoutingChangeListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: RoutingChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun setNightMode(enabled: Boolean) {
        if (isNightModeEnabled != enabled) {
            isNightModeEnabled = enabled
            evaluateRouting()
        }
    }

    fun setForceStereo(enabled: Boolean) {
        if (isForceStereoEnabled != enabled) {
            isForceStereoEnabled = enabled
            evaluateRouting()
        }
    }

    fun setServerDownmixActive(active: Boolean) {
        if (isServerDownmixActive != active) {
            isServerDownmixActive = active
            evaluateRouting()
        }
    }

    fun setPassthroughSupported(supported: Boolean) {
        if (isPassthroughSupported != supported) {
            isPassthroughSupported = supported
            evaluateRouting()
        }
    }

    fun setCurrentStream(mimeType: String?) {
        if (currentMimeType != mimeType) {
            currentMimeType = mimeType
            evaluateRouting()
        }
    }

    private fun evaluateRouting() {
        val oldMode = _currentMode.value
        val newMode = when {
            isServerDownmixActive -> {
                AudioRoutingMode.PCM_DIRECT
            }
            isNightModeEnabled || isForceStereoEnabled -> {
                AudioRoutingMode.PCM_DSP
            }
            isPassthroughSupported && isPassthroughCompatible(currentMimeType) -> {
                AudioRoutingMode.PASSTHROUGH_BITSTREAM
            }
            else -> {
                AudioRoutingMode.PCM_DIRECT
            }
        }

        if (oldMode != newMode) {
            _currentMode.value = newMode
            notifyListeners(oldMode, newMode)
        }
    }

    private fun notifyListeners(oldMode: AudioRoutingMode, newMode: AudioRoutingMode) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (listener in copy) {
            listener.onRoutingChanged(oldMode, newMode)
        }
    }

    companion object {
        private val PASSTHROUGH_MIME_TYPES = setOf(
            "audio/ac3",
            "audio/eac3",
            "audio/eac3-joc",
            "audio/true-hd",
            "audio/vnd.dts",
            "audio/vnd.dts.hd"
        )

        fun isPassthroughCompatible(mimeType: String?): Boolean {
            if (mimeType == null) return false
            return PASSTHROUGH_MIME_TYPES.contains(mimeType.lowercase())
        }
    }
}
