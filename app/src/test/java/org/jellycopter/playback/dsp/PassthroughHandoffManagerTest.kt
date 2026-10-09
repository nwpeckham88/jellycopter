package org.jellycopter.playback.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassthroughHandoffManagerTest {

    @Test
    fun testInitialPassthroughRouting() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/true-hd")

        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, manager.currentMode.value)
    }

    @Test
    fun testNightModeTriggersPcmDspTransition() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/eac3")

        val transitions = mutableListOf<Pair<PassthroughHandoffManager.AudioRoutingMode, PassthroughHandoffManager.AudioRoutingMode>>()
        manager.addListener { oldMode, newMode ->
            transitions.add(Pair(oldMode, newMode))
        }

        manager.setNightMode(true)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)
        assertEquals(1, transitions.size)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, transitions[0].first)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, transitions[0].second)

        // Toggling off returns to bitstream
        manager.setNightMode(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, manager.currentMode.value)
        assertEquals(2, transitions.size)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, transitions[1].first)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, transitions[1].second)
    }

    @Test
    fun testForceStereoTriggersPcmDspTransition() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/ac3")

        manager.setForceStereo(true)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)

        manager.setForceStereo(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, manager.currentMode.value)
    }

    @Test
    fun testBothActiveRequiresBothOffToReturnToPassthrough() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/true-hd")

        manager.setNightMode(true)
        manager.setForceStereo(true)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)

        // Turn Night Mode off, but Force Stereo is still on -> remains in PCM_DSP
        manager.setNightMode(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)

        // Turn Force Stereo off -> now returns to PASSTHROUGH_BITSTREAM
        manager.setForceStereo(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, manager.currentMode.value)
    }

    @Test
    fun testIncompatibleStreamRoutesToPcmDirect() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/mp4a-latm") // AAC stream

        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DIRECT, manager.currentMode.value)

        // Turning Night Mode on should switch to PCM_DSP
        manager.setNightMode(true)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)
    }

    @Test
    fun testServerDownmixOverridesToPcmDirect() {
        val manager = PassthroughHandoffManager(initialPassthroughSupported = true)
        manager.setCurrentStream("audio/true-hd")
        manager.setNightMode(true)
        manager.setForceStereo(true)

        // Without server downmix, PCM_DSP is active
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)

        // When server downmix is active, it forces PCM_DIRECT to prevent duplicate DSP
        manager.setServerDownmixActive(true)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DIRECT, manager.currentMode.value)

        // When server downmix ceases, returns to PCM_DSP
        manager.setServerDownmixActive(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, manager.currentMode.value)
    }
}
