package org.jellycopter.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Speech Clarity Vocal Formant Peaking EQ.
 *
 * 2nd-order Biquad parametric peaking equalizer filter centered at 2.5 kHz (+3.5 dB gain, Q = 1.0),
 * specifically boosting the human vocal intelligibility range (consonant recognition).
 *
 * Provides normalized Robert Bristow-Johnson (RBJ) coefficients dynamically for any sample rate.
 */
@UnstableApi
class VocalFormantFilter(
    initialEnabled: Boolean = true,
    initialFrequencyHz: Float = 2500.0f,
    initialGainDb: Float = 3.5f,
    initialQ: Float = 1.0f
) : AudioProcessor {

    data class BiquadCoefficients(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    )

    var enabled: Boolean = initialEnabled
        set(value) {
            field = value
            updateState()
        }

    var frequencyHz: Float = initialFrequencyHz
        set(value) {
            field = value
            recalculateCoefficients()
        }

    var gainDb: Float = initialGainDb
        set(value) {
            field = value
            recalculateCoefficients()
        }

    var q: Float = initialQ
        set(value) {
            field = value
            recalculateCoefficients()
        }

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    private var coeffs: BiquadCoefficients = calculateCoefficients(48000, frequencyHz, gainDb, q)

    // Filter delay state per channel: x1, x2, y1, y2
    private var x1: FloatArray = FloatArray(8)
    private var x2: FloatArray = FloatArray(8)
    private var y1: FloatArray = FloatArray(8)
    private var y2: FloatArray = FloatArray(8)

    init {
        recalculateCoefficients()
    }

    private fun recalculateCoefficients() {
        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 48000
        coeffs = calculateCoefficients(sampleRate, frequencyHz, gainDb, q)
    }

    private fun updateState() {
        outputAudioFormat = if (enabled && inputAudioFormat != AudioFormat.NOT_SET) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat

        val channels = inputAudioFormat.channelCount
        if (x1.size < channels) {
            x1 = FloatArray(channels)
            x2 = FloatArray(channels)
            y1 = FloatArray(channels)
            y2 = FloatArray(channels)
        }

        recalculateCoefficients()
        updateState()
        return outputAudioFormat
    }

    override fun isActive(): Boolean = enabled && inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channelCount = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = channelCount * bytesPerSample
        val frameCount = remaining / frameSize

        val requiredBytes = frameCount * frameSize
        if (buffer.capacity() < requiredBytes) {
            buffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        val b0 = coeffs.b0
        val b1 = coeffs.b1
        val b2 = coeffs.b2
        val a1 = coeffs.a1
        val a2 = coeffs.a2

        if (encoding == C.ENCODING_PCM_FLOAT) {
            val srcFloat = inputBuffer.asFloatBuffer()
            val dstFloat = buffer.asFloatBuffer()

            for (i in 0 until frameCount) {
                for (ch in 0 until channelCount) {
                    val x0 = srcFloat.get()
                    val y0 = b0 * x0 + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]

                    x2[ch] = x1[ch]
                    x1[ch] = x0
                    y2[ch] = y1[ch]
                    y1[ch] = y0

                    dstFloat.put(y0.coerceIn(-1.0f, 1.0f))
                }
            }

            inputBuffer.position(inputBuffer.position() + frameCount * channelCount * 4)
            buffer.position(buffer.position() + frameCount * channelCount * 4)
        } else {
            val srcShort = inputBuffer.asShortBuffer()
            val dstShort = buffer.asShortBuffer()

            for (i in 0 until frameCount) {
                for (ch in 0 until channelCount) {
                    val raw = srcShort.get()
                    val x0 = raw / 32768.0f
                    val y0 = b0 * x0 + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]

                    x2[ch] = x1[ch]
                    x1[ch] = x0
                    y2[ch] = y1[ch]
                    y1[ch] = y0

                    val outShort = (y0 * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                    dstShort.put(outShort)
                }
            }

            inputBuffer.position(inputBuffer.position() + frameCount * channelCount * 2)
            buffer.position(buffer.position() + frameCount * channelCount * 2)
        }

        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer == AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        x1.fill(0.0f)
        x2.fill(0.0f)
        y1.fill(0.0f)
        y2.fill(0.0f)
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    companion object {
        /**
         * Calculates normalized 2nd-order Biquad peaking EQ coefficients using Robert Bristow-Johnson's formulation.
         */
        fun calculateCoefficients(
            sampleRate: Int,
            f0: Float = 2500.0f,
            gainDb: Float = 3.5f,
            q: Float = 1.0f
        ): BiquadCoefficients {
            val fs = sampleRate.toDouble()
            val w0 = 2.0 * PI * f0.toDouble() / fs
            val alpha = sin(w0) / (2.0 * q.toDouble())
            val a = 10.0.pow(gainDb.toDouble() / 40.0) // 10^(G / 40)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cos(w0)
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cos(w0)
            val a2 = 1.0 - alpha / a

            // Normalize by a0
            return BiquadCoefficients(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat()
            )
        }

        /**
         * Computes the magnitude response in dB of the peaking filter at frequency f.
         */
        fun getMagnitudeResponseDb(
            frequencyHz: Float,
            sampleRate: Int,
            f0: Float = 2500.0f,
            gainDb: Float = 3.5f,
            q: Float = 1.0f
        ): Double {
            val coeffs = calculateCoefficients(sampleRate, f0, gainDb, q)
            val w = 2.0 * PI * frequencyHz.toDouble() / sampleRate.toDouble()

            val cosW = cos(w)
            val sinW = sin(w)
            val cos2W = cos(2.0 * w)
            val sin2W = sin(2.0 * w)

            val numReal = coeffs.b0.toDouble() + coeffs.b1.toDouble() * cosW + coeffs.b2.toDouble() * cos2W
            val numImag = -coeffs.b1.toDouble() * sinW - coeffs.b2.toDouble() * sin2W
            val denReal = 1.0 + coeffs.a1.toDouble() * cosW + coeffs.a2.toDouble() * cos2W
            val denImag = -coeffs.a1.toDouble() * sinW - coeffs.a2.toDouble() * sin2W

            val numMagSq = numReal * numReal + numImag * numImag
            val denMagSq = denReal * denReal + denImag * denImag

            return 10.0 * log10(numMagSq / denMagSq)
        }

        /**
         * Standalone array processor for Float audio buffers.
         */
        fun processFloatBuffer(
            input: FloatArray,
            channels: Int,
            sampleRate: Int,
            f0: Float = 2500.0f,
            gainDb: Float = 3.5f,
            q: Float = 1.0f
        ): FloatArray {
            val coeffs = calculateCoefficients(sampleRate, f0, gainDb, q)
            val b0 = coeffs.b0
            val b1 = coeffs.b1
            val b2 = coeffs.b2
            val a1 = coeffs.a1
            val a2 = coeffs.a2

            val x1 = FloatArray(channels)
            val x2 = FloatArray(channels)
            val y1 = FloatArray(channels)
            val y2 = FloatArray(channels)

            val output = FloatArray(input.size)
            val frameCount = input.size / channels

            for (i in 0 until frameCount) {
                for (ch in 0 until channels) {
                    val idx = i * channels + ch
                    val x0 = input[idx]
                    val y0 = b0 * x0 + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]

                    x2[ch] = x1[ch]
                    x1[ch] = x0
                    y2[ch] = y1[ch]
                    y1[ch] = y0

                    output[idx] = y0.coerceIn(-1.0f, 1.0f)
                }
            }
            return output
        }

        /**
         * Standalone array processor for 16-bit PCM buffers.
         */
        fun process16BitBuffer(
            input: ShortArray,
            channels: Int,
            sampleRate: Int,
            f0: Float = 2500.0f,
            gainDb: Float = 3.5f,
            q: Float = 1.0f
        ): ShortArray {
            val coeffs = calculateCoefficients(sampleRate, f0, gainDb, q)
            val b0 = coeffs.b0
            val b1 = coeffs.b1
            val b2 = coeffs.b2
            val a1 = coeffs.a1
            val a2 = coeffs.a2

            val x1 = FloatArray(channels)
            val x2 = FloatArray(channels)
            val y1 = FloatArray(channels)
            val y2 = FloatArray(channels)

            val output = ShortArray(input.size)
            val frameCount = input.size / channels

            for (i in 0 until frameCount) {
                for (ch in 0 until channels) {
                    val idx = i * channels + ch
                    val x0 = input[idx] / 32768.0f
                    val y0 = b0 * x0 + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]

                    x2[ch] = x1[ch]
                    x1[ch] = x0
                    y2[ch] = y1[ch]
                    y1[ch] = y0

                    output[idx] = (y0 * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                }
            }
            return output
        }
    }
}
