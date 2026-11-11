package com.echofold.capture.pcm

import com.echofold.core.asr.engine.PcmFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 一个振幅窗口样本。
 *
 * [rms] 与 [peak] 都是相对满量程（32768）的 0..1 归一化值，来自实际采集 PCM，
 * 不得由随机数或 ASR/LLM 活动模拟。[monoNs] 是窗口最后一个样本的设备单调时间戳，
 * `capturedStartMs/capturedEndMs` 是会话有效音频时间轴上的偏移（不含暂停间隙）。
 */
data class AmplitudeSample(
    val monoNs: Long,
    val capturedStartMs: Long,
    val capturedEndMs: Long,
    val rms: Double,
    val peak: Double,
    val frames: Int,
)

/**
 * 从实际 PCM 计算 RMS/peak 的振幅采样器。
 *
 * 行为约定：
 * - 按固定窗口 [windowFrames] 聚合，跨帧的部分窗口会被保留，凑满一个窗口才产生样本；
 * - [pause] 后不再产生任何样本，且丢弃未满窗口，避免把暂停前后的音频拼成一个假窗口
 *   （BLOCKED 时调用方同样必须暂停采样器）；
 * - 纯计算、无 Android 依赖，可直接在 JVM 单测中验证。
 */
class AmplitudeSampler(
    private val windowFrames: Int = DEFAULT_WINDOW_FRAMES,
    private val listener: ((AmplitudeSample) -> Unit)? = null,
) : AmplitudeSink {

    init {
        require(windowFrames > 0) { "窗口帧数必须为正数：$windowFrames" }
    }

    private var paused: Boolean = false
    private var windowSampleCount: Int = 0
    private var sumSquares: Double = 0.0
    private var maxAbs: Int = 0
    private var windowStartCapturedMs: Long = 0L

    /** 已产生的窗口样本数。 */
    var sampleCount: Long = 0L
        private set

    /** 是否处于暂停（冻结）状态。 */
    val isPaused: Boolean get() = paused

    /** 消费分发车道送来的帧；`frame.samples` 只读。 */
    override fun accept(frame: PcmFrame) {
        append(
            samples = frame.samples,
            offset = frame.offset,
            length = frame.length,
            sampleRate = frame.sampleRate,
            monoNs = frame.monoNs,
            capturedStartMs = frame.capturedStartMs,
        )
    }

    /**
     * 追加一段 PCM 并返回本次凑满的窗口样本（可能为空）。
     *
     * [monoNs] 与 [capturedStartMs] 对应 [samples] 首样本的时间；窗口中途的样本时间
     * 按 [sampleRate] 线性外推。
     */
    fun append(
        samples: ShortArray,
        offset: Int,
        length: Int,
        sampleRate: Int,
        monoNs: Long,
        capturedStartMs: Long,
    ): List<AmplitudeSample> {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "非法的 PCM 视图：offset=$offset length=$length samples=${samples.size}"
        }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        if (paused || length == 0) return emptyList()

        val produced = ArrayList<AmplitudeSample>(length / windowFrames + 1)
        var consumed = 0
        while (consumed < length) {
            val take = minOf(windowFrames - windowSampleCount, length - consumed)
            if (windowSampleCount == 0) {
                windowStartCapturedMs = capturedStartMs + consumed * 1_000L / sampleRate
            }
            val start = offset + consumed
            for (index in start until start + take) {
                val value = samples[index].toInt()
                sumSquares += value.toDouble() * value
                maxAbs = max(maxAbs, abs(value))
            }
            windowSampleCount += take
            consumed += take

            if (windowSampleCount == windowFrames) {
                val endMonoNs = monoNs + consumed * NANOS_PER_SECOND / sampleRate
                val endCapturedMs = capturedStartMs + consumed * 1_000L / sampleRate
                val sample = AmplitudeSample(
                    monoNs = endMonoNs,
                    capturedStartMs = windowStartCapturedMs,
                    capturedEndMs = endCapturedMs,
                    rms = normalize(sqrt(sumSquares / windowFrames)),
                    peak = normalize(maxAbs.toDouble()),
                    frames = windowFrames,
                )
                produced += sample
                sampleCount++
                resetWindow()
            }
        }

        if (listener != null) {
            produced.forEach(listener)
        }
        return produced
    }

    /** 暂停采集（用户暂停或分发 BLOCKED）：丢弃未满窗口，之后不再产生样本。 */
    fun pause() {
        paused = true
        resetWindow()
    }

    /** 恢复采集：从下一个完整窗口重新开始累计。 */
    fun resume() {
        paused = false
        resetWindow()
    }

    private fun resetWindow() {
        windowSampleCount = 0
        sumSquares = 0.0
        maxAbs = 0
    }

    /** 归一化到 0..1；Short 的 -32768 绝对值等于满量程。 */
    private fun normalize(value: Double): Double = (value / FULL_SCALE).coerceIn(0.0, 1.0)

    companion object {
        /** 48kHz 下约 21.3ms 的窗口，兼顾波形刷新率与计算量。 */
        const val DEFAULT_WINDOW_FRAMES: Int = 1_024
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
        private const val FULL_SCALE: Double = 32_768.0
    }
}
