package org.jellycopter.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.jellycopter.core.model.DrcConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Audio Night Mode Dynamic Range Compression (DRC) Filter.
 *
 * Implements a dual-envelope dynamic range compressor featuring:
 * - +6 dB dialogue floor boost for quiet passages (< -40 dBFS).
 * - Linear smooth transition between -40 dBFS and -18 dBFS.
 * - 4:1 compression ratio above -18 dBFS.
 * - -3 dBFS ceiling clamping (reducing loud transients by up to -12 dB).
 * - Dual-envelope follower: 10 ms attack, 300 ms release.
 * - Zero-signal guard to prevent log(0) and noise explosion.
 */
@UnstableApi
class NightModeDrcFilter(
    initialConfig: DrcConfig = DrcConfig(enabled = true)
) : AudioProcessor {

    var config: DrcConfig = initialConfig
        set(value) {
            field = value
            recalculateParameters()
        }

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    // Envelope and gain state per channel (supports up to 8 channels)
    private var envelope: FloatArray = FloatArray(8)
    private var currentGain: FloatArray = FloatArray(8) { 1.0f }
    private var alphaAttack: Float = 0.0f
    private var alphaRelease: Float = 0.0f
    private var ceilingLinear: Float = 0.70794578f // 10^(-3.0 / 20.0)

    // Reusable scratch buffers for zero-allocation audio processing loop
    private val floatScratch = FloatArray(BLOCK_SIZE * 8)
    private val shortScratch = ShortArray(BLOCK_SIZE * 8)

    init {
        recalculateParameters()
    }

    private fun recalculateParameters() {
        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 48000
        val attackSec = max(0.001f, config.attackTimeMs / 1000.0f)
        val releaseSec = max(0.010f, config.releaseTimeMs / 1000.0f)

        alphaAttack = exp(-1.0f / (attackSec * sampleRate))
        alphaRelease = exp(-1.0f / (releaseSec * sampleRate))
        ceilingLinear = 10.0.pow(config.transientClampingCeilingDb / 20.0).toFloat()
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat

        if (envelope.size < inputAudioFormat.channelCount) {
            envelope = FloatArray(inputAudioFormat.channelCount)
            currentGain = FloatArray(inputAudioFormat.channelCount) { 1.0f }
        }

        recalculateParameters()

        outputAudioFormat = if (config.enabled) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }
        return outputAudioFormat
    }

    override fun isActive(): Boolean = config.enabled && inputAudioFormat != AudioFormat.NOT_SET

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

        var frameOffset = 0
        while (frameOffset < frameCount) {
            val blockSize = min(BLOCK_SIZE, frameCount - frameOffset)
            val sampleCount = blockSize * channels
            srcFloat.get(floatScratch, 0, sampleCount)

            for (ch in 0 until channels) {
                // Update envelope across block for this channel
                for (k in 0 until blockSize) {
                    val s = floatScratch[k * channels + ch]
                    val absSample = kotlin.math.abs(s)
                    envelope[ch] = if (absSample > envelope[ch]) {
                        alphaAttack * envelope[ch] + (1.0f - alphaAttack) * absSample
                    } else {
                        alphaRelease * envelope[ch] + (1.0f - alphaRelease) * absSample
                    }
                }

                val targetGain = 10.0.pow(computeGainDb(levelToDbfs(envelope[ch]), config) / 20.0).toFloat()
                val startGain = currentGain[ch]
                val gainStep = (targetGain - startGain) / blockSize
                var g = startGain

                for (k in 0 until blockSize) {
                    g += gainStep
                    val idx = k * channels + ch
                    val processed = floatScratch[idx] * g
                    floatScratch[idx] = processed.coerceIn(-ceilingLinear, ceilingLinear)
                }
                currentGain[ch] = targetGain
            }

            dstFloat.put(floatScratch, 0, sampleCount)
            frameOffset += blockSize
        }

        src.position(src.position() + frameCount * channels * 4)
        dst.position(dst.position() + frameCount * channels * 4)
    }

    private fun process16BitBuffer(
        src: ByteBuffer,
        dst: ByteBuffer,
        frameCount: Int,
        channels: Int
    ) {
        val srcShort = src.asShortBuffer()
        val dstShort = dst.asShortBuffer()
        val ceiling16Bit = (ceilingLinear * 32767.0f).coerceIn(0.0f, 32767.0f)

        var frameOffset = 0
        while (frameOffset < frameCount) {
            val blockSize = min(BLOCK_SIZE, frameCount - frameOffset)
            val sampleCount = blockSize * channels
            srcShort.get(shortScratch, 0, sampleCount)

            for (ch in 0 until channels) {
                for (k in 0 until blockSize) {
                    val raw = shortScratch[k * channels + ch]
                    val sample = raw / 32768.0f
                    val absSample = kotlin.math.abs(sample)
                    envelope[ch] = if (absSample > envelope[ch]) {
                        alphaAttack * envelope[ch] + (1.0f - alphaAttack) * absSample
                    } else {
                        alphaRelease * envelope[ch] + (1.0f - alphaRelease) * absSample
                    }
                }

                val targetGain = 10.0.pow(computeGainDb(levelToDbfs(envelope[ch]), config) / 20.0).toFloat()
                val startGain = currentGain[ch]
                val gainStep = (targetGain - startGain) / blockSize
                var g = startGain

                for (k in 0 until blockSize) {
                    g += gainStep
                    val idx = k * channels + ch
                    val sample = shortScratch[idx] / 32768.0f
                    val processed = sample * g
                    val clamped = processed.coerceIn(-ceilingLinear, ceilingLinear)
                    shortScratch[idx] = (clamped * 32767.0f).coerceIn(-ceiling16Bit, ceiling16Bit).toInt().toShort()
                }
                currentGain[ch] = targetGain
            }

            dstShort.put(shortScratch, 0, sampleCount)
            frameOffset += blockSize
        }

        src.position(src.position() + frameCount * channels * 2)
        dst.position(dst.position() + frameCount * channels * 2)
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
        envelope.fill(0.0f)
        currentGain.fill(1.0f)
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    companion object {
        const val BLOCK_SIZE = 32
        private const val SILENCE_FLOOR_DBFS = -120.0f
        private const val EPSILON = 1e-6f

        fun levelToDbfs(level: Float): Float {
            val safe = max(level, EPSILON)
            return (20.0 * log10(safe.toDouble())).toFloat()
        }

        /**
         * Computes the DRC gain G(x) in dB for a given input level x in dBFS.
         *
         * Formula:
         * - If x < -40.0: G(x) = +6.0 dB
         * - If -40.0 <= x <= -18.0: linear transition G(x) = 6.0 * (-18.0 - x) / 22.0
         * - If x > -18.0: G(x) = (-18.0 + (x - (-18.0)) / R) - x
         *
         * Additionally enforces that output level x + G(x) <= ceiling (-3 dBFS),
         * clamping gain if necessary: G_eff(x) = min(G(x), ceiling - x).
         */
        fun computeGainDb(inputDbfs: Float, config: DrcConfig = DrcConfig()): Float {
            val tLow = -40.0f
            val tHigh = config.compressionThresholdDb // -18.0 dBFS
            val floorBoost = config.dialogueFloorBoostDb // +6.0 dB
            val ratio = max(1.0f, config.compressionRatio) // 4:1
            val ceiling = config.transientClampingCeilingDb // -3.0 dBFS

            val rawGain = when {
                inputDbfs < tLow -> floorBoost
                inputDbfs <= tHigh -> {
                    floorBoost * (tHigh - inputDbfs) / (tHigh - tLow)
                }
                else -> {
                    // Compressed level: tHigh + (x - tHigh) / ratio
                    val compressedOutput = tHigh + (inputDbfs - tHigh) / ratio
                    compressedOutput - inputDbfs
                }
            }

            // Ceiling clamp
            val maxAllowedGain = ceiling - inputDbfs
            return min(rawGain, maxAllowedGain)
        }

        /**
         * Standalone array processor for Float audio buffers using block-based gain interpolation.
         */
        fun processFloatBuffer(
            input: FloatArray,
            channels: Int,
            sampleRate: Int,
            config: DrcConfig = DrcConfig(enabled = true)
        ): FloatArray {
            if (!config.enabled) return input.copyOf()

            val output = FloatArray(input.size)
            val envelope = FloatArray(channels)
            val currentGain = FloatArray(channels) { 1.0f }
            val attackSec = max(0.001f, config.attackTimeMs / 1000.0f)
            val releaseSec = max(0.010f, config.releaseTimeMs / 1000.0f)
            val alphaAtt = exp(-1.0f / (attackSec * sampleRate))
            val alphaRel = exp(-1.0f / (releaseSec * sampleRate))
            val ceilingLinear = 10.0.pow(config.transientClampingCeilingDb / 20.0).toFloat()

            val frameCount = input.size / channels
            var frameOffset = 0
            while (frameOffset < frameCount) {
                val blockSize = min(BLOCK_SIZE, frameCount - frameOffset)

                for (ch in 0 until channels) {
                    for (k in 0 until blockSize) {
                        val idx = (frameOffset + k) * channels + ch
                        val sample = input[idx]
                        val absSample = kotlin.math.abs(sample)
                        envelope[ch] = if (absSample > envelope[ch]) {
                            alphaAtt * envelope[ch] + (1.0f - alphaAtt) * absSample
                        } else {
                            alphaRel * envelope[ch] + (1.0f - alphaRel) * absSample
                        }
                    }

                    val targetGain = 10.0.pow(computeGainDb(levelToDbfs(envelope[ch]), config) / 20.0).toFloat()
                    val startGain = currentGain[ch]
                    val gainStep = (targetGain - startGain) / blockSize
                    var g = startGain

                    for (k in 0 until blockSize) {
                        g += gainStep
                        val idx = (frameOffset + k) * channels + ch
                        val processed = input[idx] * g
                        output[idx] = processed.coerceIn(-ceilingLinear, ceilingLinear)
                    }
                    currentGain[ch] = targetGain
                }
                frameOffset += blockSize
            }
            return output
        }

        /**
         * Standalone array processor for 16-bit PCM buffers using block-based gain interpolation.
         */
        fun process16BitBuffer(
            input: ShortArray,
            channels: Int,
            sampleRate: Int,
            config: DrcConfig = DrcConfig(enabled = true)
        ): ShortArray {
            if (!config.enabled) return input.copyOf()

            val output = ShortArray(input.size)
            val envelope = FloatArray(channels)
            val currentGain = FloatArray(channels) { 1.0f }
            val attackSec = max(0.001f, config.attackTimeMs / 1000.0f)
            val releaseSec = max(0.010f, config.releaseTimeMs / 1000.0f)
            val alphaAtt = exp(-1.0f / (attackSec * sampleRate))
            val alphaRel = exp(-1.0f / (releaseSec * sampleRate))
            val ceilingLinear = 10.0.pow(config.transientClampingCeilingDb / 20.0).toFloat()
            val ceiling16Bit = (ceilingLinear * 32767.0f).coerceIn(0.0f, 32767.0f)

            val frameCount = input.size / channels
            var frameOffset = 0
            while (frameOffset < frameCount) {
                val blockSize = min(BLOCK_SIZE, frameCount - frameOffset)

                for (ch in 0 until channels) {
                    for (k in 0 until blockSize) {
                        val idx = (frameOffset + k) * channels + ch
                        val raw = input[idx]
                        val sample = raw / 32768.0f
                        val absSample = kotlin.math.abs(sample)
                        envelope[ch] = if (absSample > envelope[ch]) {
                            alphaAtt * envelope[ch] + (1.0f - alphaAtt) * absSample
                        } else {
                            alphaRel * envelope[ch] + (1.0f - alphaRel) * absSample
                        }
                    }

                    val targetGain = 10.0.pow(computeGainDb(levelToDbfs(envelope[ch]), config) / 20.0).toFloat()
                    val startGain = currentGain[ch]
                    val gainStep = (targetGain - startGain) / blockSize
                    var g = startGain

                    for (k in 0 until blockSize) {
                        g += gainStep
                        val idx = (frameOffset + k) * channels + ch
                        val sample = input[idx] / 32768.0f
                        val processed = sample * g
                        val clamped = processed.coerceIn(-ceilingLinear, ceilingLinear)
                        output[idx] = (clamped * 32767.0f).coerceIn(-ceiling16Bit, ceiling16Bit).toInt().toShort()
                    }
                    currentGain[ch] = targetGain
                }
                frameOffset += blockSize
            }
            return output
        }
    }
}
