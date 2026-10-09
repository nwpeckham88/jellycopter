package org.jellycopter.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural Retro Audio Synthesizer for 1970s/1980s Vintage TV Micro-Interactions.
 *
 * Synthesizes mechanical rotary switch clunks, analog static noise bursts,
 * and broadcast test tones in real-time via AudioTrack with zero external asset dependencies.
 */
object RetroAudioEffects {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isEnabled: Boolean = true

    /**
     * Plays a punchy, low-frequency mechanical rotary switch "clunk / click".
     * Simulates a heavy physical detent spring engaging in a 1978 TV tuner.
     */
    fun playKnobClick() {
        if (!isEnabled) return
        scope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 45
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Rapid decay envelope
                    val envelope = (1.0 - (i.toDouble() / numSamples))
                    // Dual low-frequency thumps (110Hz + 220Hz harmonic) + subtle snap noise
                    val sine1 = sin(2.0 * PI * 110.0 * t)
                    val sine2 = sin(2.0 * PI * 220.0 * t) * 0.5
                    val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.25 * (if (i < numSamples / 3) 1.0 else 0.0)
                    val sample = ((sine1 + sine2 + noise) * envelope * 0.75).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(samples, sampleRate)
            } catch (_: Exception) {
                // Ignore audio synthesis errors silently
            }
        }
    }

    /**
     * Plays a quick analog static noise burst during channel switching.
     */
    fun playStaticBurst(durationMs: Int = 120) {
        if (!isEnabled) return
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                var lastNoise = 0.0
                for (i in 0 until numSamples) {
                    val envelope = when {
                        i < numSamples * 0.2 -> (i.toDouble() / (numSamples * 0.2))
                        else -> (1.0 - ((i - numSamples * 0.2) / (numSamples * 0.8)))
                    }
                    val rawNoise = Random.nextDouble() * 2.0 - 1.0
                    // Simple low-pass smoothing to simulate band-limited TV tuner white noise
                    lastNoise = lastNoise * 0.6 + rawNoise * 0.4
                    val sample = (lastNoise * envelope * 0.40).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(samples, sampleRate)
            } catch (_: Exception) {
                // Ignore audio synthesis errors
            }
        }
    }

    /**
     * Plays a brief 1000Hz broadcast station test tone blip.
     */
    fun playTestTone(durationMs: Int = 160) {
        if (!isEnabled) return
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = when {
                        i < numSamples * 0.1 -> (i.toDouble() / (numSamples * 0.1))
                        i > numSamples * 0.85 -> (1.0 - (i - numSamples * 0.85) / (numSamples * 0.15))
                        else -> 1.0
                    }
                    val sample = (sin(2.0 * PI * 1000.0 * t) * envelope * 0.35).coerceIn(-1.0, 1.0)
                    samples[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(samples, sampleRate)
            } catch (_: Exception) {
                // Ignore audio synthesis errors
            }
        }
    }

    private fun playBuffer(samples: ShortArray, sampleRate: Int) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()

            // Release track after playback completes
            val sleepMs = ((samples.size.toDouble() / sampleRate) * 1000.0).toLong() + 50L
            Thread.sleep(sleepMs)
            audioTrack.stop()
        } finally {
            audioTrack.release()
        }
    }
}
