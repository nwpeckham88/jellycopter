package org.jellycopter.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ITU-R BS.775 Force Stereo Downmixer.
 *
 * Implements the ITU-R BS.775 multichannel downmixing standard with:
 * - Center dialogue boost parameter beta in [0.0, 6.0] dB (default 3.0 dB).
 * - Dynamic normalization preventing digital clipping (peak <= 1.0).
 * - Zero-cost passthrough for mono and stereo sources (channels <= 2).
 * - Support for 5.1 (L, R, C, LFE, Ls, Rs) and 7.1 (L, R, C, LFE, Ls, Rs, Rls, Rrs).
 */
@UnstableApi
class ItuRBs775Downmixer(
    initialCenterBoostDb: Float = 3.0f,
    initialEnabled: Boolean = true
) : AudioProcessor {

    data class Coefficients(
        val centerWeight: Float,
        val surroundWeight: Float,
        val normalizationFactor: Float,
        val effectiveCenterWeight: Float,
        val effectiveSurroundWeight: Float
    )

    var enabled: Boolean = initialEnabled
        set(value) {
            field = value
            updateState()
        }

    var centerBoostDb: Float = initialCenterBoostDb.coerceIn(0.0f, 6.0f)
        set(value) {
            field = value.coerceIn(0.0f, 6.0f)
            recalculateCoefficients()
        }

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    private var coeff51: Coefficients = calculate51Coefficients(centerBoostDb)
    private var coeff71: Coefficients = calculate71Coefficients(centerBoostDb)

    init {
        recalculateCoefficients()
    }

    private fun recalculateCoefficients() {
        coeff51 = calculate51Coefficients(centerBoostDb)
        coeff71 = calculate71Coefficients(centerBoostDb)
    }

    private fun updateState() {
        outputAudioFormat = if (enabled && inputAudioFormat != AudioFormat.NOT_SET && inputAudioFormat.channelCount > 2) {
            AudioFormat(
                inputAudioFormat.sampleRate,
                2,
                inputAudioFormat.encoding
            )
        } else {
            AudioFormat.NOT_SET
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat

        return if (enabled && inputAudioFormat.channelCount > 2) {
            outputAudioFormat = AudioFormat(
                inputAudioFormat.sampleRate,
                2,
                inputAudioFormat.encoding
            )
            outputAudioFormat
        } else {
            outputAudioFormat = AudioFormat.NOT_SET
            AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean = enabled && outputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channelCount = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val inputFrameSize = channelCount * bytesPerSample
        val outputFrameSize = 2 * bytesPerSample
        val frameCount = remaining / inputFrameSize

        val requiredBytes = frameCount * outputFrameSize
        if (buffer.capacity() < requiredBytes) {
            buffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloatBuffer(inputBuffer, buffer, frameCount, channelCount)
        } else {
            process16BitBuffer(inputBuffer, buffer, frameCount, channelCount)
        }

        buffer.flip()
        outputBuffer = buffer
    }

    private fun processFloatBuffer(
        src: ByteBuffer,
        dst: ByteBuffer,
        frameCount: Int,
        channels: Int
    ) {
        val srcFloat = src.asFloatBuffer()
        val dstFloat = dst.asFloatBuffer()

        val coeffs = if (channels >= 8) coeff71 else coeff51
        val nc = coeffs.effectiveCenterWeight
        val ns = coeffs.effectiveSurroundWeight
        val norm = coeffs.normalizationFactor

        for (i in 0 until frameCount) {
            val base = i * channels
            val l = srcFloat.get(base)
            val r = srcFloat.get(base + 1)
            val c = srcFloat.get(base + 2)
            // LFE at base + 3 is omitted per ITU-R BS.775
            val ls = srcFloat.get(base + 4)
            val rs = srcFloat.get(base + 5)

            val (outL, outR) = if (channels >= 8) {
                val rls = srcFloat.get(base + 6)
                val rrs = srcFloat.get(base + 7)
                val effLs = (ls + rls) * INV_SQRT_2
                val effRs = (rs + rrs) * INV_SQRT_2
                Pair(
                    norm * (l + nc * c + ns * effLs),
                    norm * (r + nc * c + ns * effRs)
                )
            } else {
                Pair(
                    norm * (l + nc * c + ns * ls),
                    norm * (r + nc * c + ns * rs)
                )
            }

            dstFloat.put(outL.coerceIn(-1.0f, 1.0f))
            dstFloat.put(outR.coerceIn(-1.0f, 1.0f))
        }

        src.position(src.position() + frameCount * channels * 4)
        dst.position(dst.position() + frameCount * 2 * 4)
    }

    private fun process16BitBuffer(
        src: ByteBuffer,
        dst: ByteBuffer,
        frameCount: Int,
        channels: Int
    ) {
        val srcShort = src.asShortBuffer()
        val dstShort = dst.asShortBuffer()

        val coeffs = if (channels >= 8) coeff71 else coeff51
        val nc = coeffs.effectiveCenterWeight
        val ns = coeffs.effectiveSurroundWeight
        val norm = coeffs.normalizationFactor

        for (i in 0 until frameCount) {
            val base = i * channels
            val l = srcShort.get(base).toFloat()
            val r = srcShort.get(base + 1).toFloat()
            val c = srcShort.get(base + 2).toFloat()
            val ls = srcShort.get(base + 4).toFloat()
            val rs = srcShort.get(base + 5).toFloat()

            val (outL, outR) = if (channels >= 8) {
                val rls = srcShort.get(base + 6).toFloat()
                val rrs = srcShort.get(base + 7).toFloat()
                val effLs = (ls + rls) * INV_SQRT_2
                val effRs = (rs + rrs) * INV_SQRT_2
                Pair(
                    norm * (l + nc * c + ns * effLs),
                    norm * (r + nc * c + ns * effRs)
                )
            } else {
                Pair(
                    norm * (l + nc * c + ns * ls),
                    norm * (r + nc * c + ns * rs)
                )
            }

            dstShort.put(outL.coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            dstShort.put(outR.coerceIn(-32768.0f, 32767.0f).toInt().toShort())
        }

        src.position(src.position() + frameCount * channels * 2)
        dst.position(dst.position() + frameCount * 2 * 2)
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
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    companion object {
        const val INV_SQRT_2: Float = 0.7071067811865475f

        /**
         * Calculate ITU-R BS.775 coefficients for 5.1 downmixing.
         *
         * Center weight: alpha_C = 10^((-3.0 + beta) / 20.0)
         * Surround weight: alpha_S = 1 / sqrt(2)
         * Normalization factor: N = 1 / (1.0 + alpha_C + alpha_S)
         */
        fun calculate51Coefficients(centerBoostDb: Float): Coefficients {
            val beta = centerBoostDb.coerceIn(0.0f, 6.0f)
            val alphaC = 10.0.pow((-3.0 + beta) / 20.0).toFloat()
            val alphaS = INV_SQRT_2
            val norm = 1.0f / (1.0f + alphaC + alphaS)
            return Coefficients(
                centerWeight = alphaC,
                surroundWeight = alphaS,
                normalizationFactor = norm,
                effectiveCenterWeight = alphaC,
                effectiveSurroundWeight = alphaS
            )
        }

        /**
         * Calculate ITU-R BS.775 coefficients for 7.1 downmixing.
         */
        fun calculate71Coefficients(centerBoostDb: Float): Coefficients {
            val beta = centerBoostDb.coerceIn(0.0f, 6.0f)
            val alphaC = 10.0.pow((-3.0 + beta) / 20.0).toFloat()
            val alphaS = INV_SQRT_2
            // For 7.1, side + rear surround each folded by 1/sqrt(2), so effective surround sum is alphaS * sqrt(2) = 1.0
            val norm = 1.0f / (2.0f + alphaC)
            return Coefficients(
                centerWeight = alphaC,
                surroundWeight = alphaS,
                normalizationFactor = norm,
                effectiveCenterWeight = alphaC,
                effectiveSurroundWeight = alphaS
            )
        }

        /**
         * Standalone array-based downmixer for float samples in [-1.0, 1.0].
         * Output is stereo interleaved: [L0, R0, L1, R1, ...].
         */
        fun downmixFloat(
            input: FloatArray,
            channels: Int,
            centerBoostDb: Float = 3.0f
        ): FloatArray {
            if (channels <= 2) return input.copyOf()

            val frameCount = input.size / channels
            val output = FloatArray(frameCount * 2)
            val coeffs = if (channels >= 8) calculate71Coefficients(centerBoostDb) else calculate51Coefficients(centerBoostDb)
            val nc = coeffs.effectiveCenterWeight
            val ns = coeffs.effectiveSurroundWeight
            val norm = coeffs.normalizationFactor

            for (i in 0 until frameCount) {
                val base = i * channels
                val l = input[base]
                val r = input[base + 1]
                val c = input[base + 2]
                val ls = input[base + 4]
                val rs = input[base + 5]

                val (outL, outR) = if (channels >= 8) {
                    val rls = input[base + 6]
                    val rrs = input[base + 7]
                    val effLs = (ls + rls) * INV_SQRT_2
                    val effRs = (rs + rrs) * INV_SQRT_2
                    Pair(
                        norm * (l + nc * c + ns * effLs),
                        norm * (r + nc * c + ns * effRs)
                    )
                } else {
                    Pair(
                        norm * (l + nc * c + ns * ls),
                        norm * (r + nc * c + ns * rs)
                    )
                }

                output[i * 2] = outL.coerceIn(-1.0f, 1.0f)
                output[i * 2 + 1] = outR.coerceIn(-1.0f, 1.0f)
            }
            return output
        }

        /**
         * Standalone array-based downmixer for 16-bit PCM samples in [-32768, 32767].
         */
        fun downmix16Bit(
            input: ShortArray,
            channels: Int,
            centerBoostDb: Float = 3.0f
        ): ShortArray {
            if (channels <= 2) return input.copyOf()

            val frameCount = input.size / channels
            val output = ShortArray(frameCount * 2)
            val coeffs = if (channels >= 8) calculate71Coefficients(centerBoostDb) else calculate51Coefficients(centerBoostDb)
            val nc = coeffs.effectiveCenterWeight
            val ns = coeffs.effectiveSurroundWeight
            val norm = coeffs.normalizationFactor

            for (i in 0 until frameCount) {
                val base = i * channels
                val l = input[base].toFloat()
                val r = input[base + 1].toFloat()
                val c = input[base + 2].toFloat()
                val ls = input[base + 4].toFloat()
                val rs = input[base + 5].toFloat()

                val (outL, outR) = if (channels >= 8) {
                    val rls = input[base + 6].toFloat()
                    val rrs = input[base + 7].toFloat()
                    val effLs = (ls + rls) * INV_SQRT_2
                    val effRs = (rs + rrs) * INV_SQRT_2
                    Pair(
                        norm * (l + nc * c + ns * effLs),
                        norm * (r + nc * c + ns * effRs)
                    )
                } else {
                    Pair(
                        norm * (l + nc * c + ns * ls),
                        norm * (r + nc * c + ns * rs)
                    )
                }

                output[i * 2] = outL.coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                output[i * 2 + 1] = outR.coerceIn(-32768.0f, 32767.0f).toInt().toShort()
            }
            return output
        }
    }
}
