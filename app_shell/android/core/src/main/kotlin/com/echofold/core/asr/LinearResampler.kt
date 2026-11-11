package com.echofold.core.asr

import com.echofold.core.asr.engine.Resampler

/**
 * S16 单声道线性插值重采样器，典型用途是把采集端 44.1kHz/48kHz PCM 降到 ASR 需要的 16kHz。
 *
 * 设计要点（对齐共享契约第 3 节与 `Resampler` 约定）：
 * - **长度可预测**：每个输入块要输出的样本数由整数运算算出，不依赖浮点累积；
 * - **无累积漂移**：相位余量以 `1/denStep` 输入样本为单位的整数保存，跨块精确结转，
 *   长会话的累计输出数始终等于全局采样网格上的应有数量，不会因每块舍入而逐渐偏移；
 * - **边界安全**：空输入返回空数组；进入新块时最多回看上一块的最后一个样本（保留一个样本的前视余量），
 *   因此单个样本、1 样本块、变长块都不会越界，也不会抛未声明异常；
 * - **零拷贝**：输入速率已经等于目标速率时原样返回入参（调用方不得原地修改返回值再复用入参）。
 *
 * 算法：第 k 个输出样本位于输入位置 `x_k = k * inputRate / targetRate`。
 * 约分后 `x_k = k * numStep / denStep`，位置以 `posNum = k * numStep`（单位 `1/denStep` 样本）整数表示。
 * 位置 `p + rem/denStep` 需要输入样本 `p` 与 `p + 1`，所以块内最后一个样本位置只在 `rem == 0`
 * （无需插值）时才输出，其余情况结转到下一块。插值采用四舍五入，误差不超过 1 LSB。
 */
class LinearResampler(
    private val targetRate: Int = DEFAULT_TARGET_RATE,
) : Resampler {
    init {
        require(targetRate > 0) { "targetRate 必须为正数：$targetRate" }
    }

    /** 输入样本数与输出步长的分子（已与 [denStep] 约分）。 */
    private var numStep = 0L

    /** 输入样本数与输出步长的分母（已与 [numStep] 约分）。 */
    private var denStep = 0L

    /** 下一个输出位置，单位 `1/denStep` 输入样本，相对当前块起点；可为负（最少回看一个样本）。 */
    private var posNum = 0L

    /** 上一块的最后一个输入样本，供跨块的负位置插值使用。 */
    private var prevSample = 0.toShort()

    /** 是否已存在上一块样本；首块不允许回看。 */
    private var hasPrev = false

    /** 当前相位对应的输入速率；速率变化会重置相位，避免混用两套采样网格。 */
    private var activeRate = 0

    override fun resample(input: ShortArray, inputRate: Int, channels: Int): ShortArray {
        require(channels == MONO_CHANNELS) { "仅支持单声道 S16，channels=$channels" }
        require(inputRate > 0) { "inputRate 必须为正数：$inputRate" }
        if (input.isEmpty()) {
            // 空块不改变相位：没有样本就没有可推进的采样网格。
            return EMPTY_PCM
        }
        configure(inputRate)
        if (inputRate == targetRate) {
            // 同速率无需插值；相位恒为 0，直接返回入参以避免无意义的整块复制。
            return input
        }

        val n = input.size
        // 位置严格小于最后一个样本索引（n-1 个样本间距）时需要 n-1 与 n 之间的插值，必须回看；
        // 因此可输出的位置上界是 (n-1) * denStep（含）。
        val limit = (n - 1).toLong() * denStep
        val count = if (posNum > limit) 0L else (limit - posNum) / numStep + 1L
        require(count <= Int.MAX_VALUE) { "重采样输出长度溢出：$count" }
        if (count == 0L) {
            prevSample = input[n - 1]
            hasPrev = true
            posNum -= n.toLong() * denStep
            return EMPTY_PCM
        }

        val out = ShortArray(count.toInt())
        var pos = posNum
        var index = 0
        while (index < out.size) {
            val whole = floorDiv(pos, denStep)
            val rem = (pos - whole * denStep).toInt()
            val base = whole.toInt()
            out[index] = if (rem == 0) {
                sampleAt(input, base)
            } else {
                interpolate(sampleAt(input, base), sampleAt(input, base + 1), rem)
            }
            pos += numStep
            index++
        }

        // 结转相位：下一块起点相对本块前移 n 个样本。
        posNum = pos - n.toLong() * denStep
        prevSample = input[n - 1]
        hasPrev = true
        return out
    }

    /** 重置跨块相位；开始新的音频流（例如切换会话）时调用。 */
    @Synchronized
    fun reset() {
        posNum = 0L
        hasPrev = false
        prevSample = 0
    }

    private fun configure(inputRate: Int) {
        if (inputRate == activeRate) return
        activeRate = inputRate
        val divisor = gcd(inputRate, targetRate)
        numStep = (inputRate / divisor).toLong()
        denStep = (targetRate / divisor).toLong()
        posNum = 0L
        hasPrev = false
        prevSample = 0
    }

    /** 取输入样本；`-1` 表示上一块的最后一个样本，`n-1` 是当前块可用的最大索引。 */
    private fun sampleAt(input: ShortArray, index: Int): Short {
        if (index >= 0) {
            require(index < input.size) { "重采样索引越界：$index 不在 0..${input.size - 1}" }
            return input[index]
        }
        check(index == -1) { "重采样只允许回看一个样本：index=$index" }
        check(hasPrev) { "首块不允许回看上一块样本" }
        return prevSample
    }

    /** 在 [s0] 与 [s1] 之间按 `rem/denStep` 线性插值，四舍五入并夹在 S16 范围内。 */
    private fun interpolate(s0: Short, s1: Short, rem: Int): Short {
        val diff = s1.toInt() - s0.toInt()
        val scaled = diff.toLong() * rem
        val rounded = if (scaled >= 0) {
            (scaled + denStep / 2) / denStep
        } else {
            -((-scaled + denStep / 2) / denStep)
        }
        val value = s0.toLong() + rounded
        return value.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
    }

    private fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        return if (value % divisor != 0L && (value xor divisor) < 0L) quotient - 1 else quotient
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    companion object {
        /** ASR 侧统一采样率。 */
        const val DEFAULT_TARGET_RATE: Int = 16_000

        /** 仅支持单声道。 */
        const val MONO_CHANNELS: Int = 1

        private val EMPTY_PCM = ShortArray(0)

        private const val SHORT_MIN = Short.MIN_VALUE.toLong()
        private const val SHORT_MAX = Short.MAX_VALUE.toLong()
    }
}
