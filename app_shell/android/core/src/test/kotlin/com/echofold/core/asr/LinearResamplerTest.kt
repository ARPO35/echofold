package com.echofold.core.asr

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** 44.1k/48k → 16k 线性重采样：长度可预测、跨块无漂移、边界安全。 */
class LinearResamplerTest {
    @Test
    fun `空输入返回空数组且不推进相位`() {
        val resampler = LinearResampler()

        assertArrayEquals(ShortArray(0), resampler.resample(ShortArray(0), 48_000))
        assertArrayEquals(ShortArray(0), resampler.resample(ShortArray(0), 44_100))
        // 空块不消耗采样网格：随后的标准整块仍得到标准输出长度。
        assertEquals(OUT_100MS, resampler.resample(ShortArray(4_800), 48_000).size)
    }

    @Test
    fun `48k 到 16k 按整数网格取样且长度精确`() {
        val input = ShortArray(4_800) { (it % 1_000).toShort() }

        val output = LinearResampler().resample(input, 48_000)

        assertEquals(OUT_100MS, output.size)
        for (index in output.indices) {
            assertEquals(input[index * 3], output[index], "第 $index 个输出应取 3 倍位置样本")
        }
    }

    @Test
    fun `44_1k 到 16k 输出长度精确`() {
        assertEquals(OUT_100MS, LinearResampler().resample(ShortArray(4_410), 44_100).size)
    }

    @Test
    fun `分块重采样不累积漂移`() {
        val resampler = LinearResampler()
        var total = 0
        repeat(100) { total += resampler.resample(ShortArray(441), 44_100).size }
        assertEquals(16_000, total, "1 秒 44.1k 音频必须恰好得到 16000 个 16k 样本")

        val at48k = LinearResampler()
        var total48 = 0
        repeat(100) { total48 += at48k.resample(ShortArray(480), 48_000).size }
        assertEquals(16_000, total48, "1 秒 48k 音频必须恰好得到 16000 个 16k 样本")
    }

    @Test
    fun `单样本与变长块边界安全`() {
        val resampler = LinearResampler()

        val single = resampler.resample(shortArrayOf(1_234), 48_000)
        assertEquals(1, single.size)
        assertEquals(1_234.toShort(), single[0])

        val afterSingle = resampler.resample(ShortArray(4_800) { 7 }, 48_000)
        assertEquals(OUT_100MS, afterSingle.size)
        assertTrue(afterSingle.all { it == 7.toShort() }, "延续块必须保持直流电平")

        // 刻意使用不能被 160 整除的块长，验证相位余量而不是每块截断。
        val odd = LinearResampler()
        assertEquals(36, odd.resample(ShortArray(100), 44_100).size)
        assertEquals(37, odd.resample(ShortArray(100), 44_100).size)
    }

    @Test
    fun `速率变化重置相位而不沿用旧网格`() {
        val resampler = LinearResampler()
        resampler.resample(ShortArray(441), 44_100)

        val output = resampler.resample(ShortArray(4_800) { 100 }, 48_000)

        assertEquals(OUT_100MS, output.size)
        assertTrue(output.all { it == 100.toShort() })
    }

    @Test
    fun `同速率零拷贝返回入参`() {
        val input = ShortArray(160) { it.toShort() }
        val output = LinearResampler().resample(input, 16_000)
        assertSame(input, output)
    }

    @Test
    fun `插值误差不超过一 LSB 且斜坡单调`() {
        val input = ShortArray(4_410) { (it / 4).toShort() }

        val output = LinearResampler().resample(input, 44_100)

        assertEquals(OUT_100MS, output.size)
        for (index in output.indices) {
            val exact = index * 441.0 / 160.0 / 4.0
            assertTrue(
                abs(output[index] - exact) <= 1.0,
                "第 $index 个输出 ${output[index]} 偏离理论位置 $exact 超过 1 LSB",
            )
            if (index > 0) {
                assertTrue(output[index] >= output[index - 1], "斜坡输入必须产生单调不减输出")
            }
        }
    }

    @Test
    fun `非法声道与非法速率被拒绝`() {
        val resampler = LinearResampler()
        assertThrows(IllegalArgumentException::class.java) { resampler.resample(ShortArray(16), 48_000, 2) }
        assertThrows(IllegalArgumentException::class.java) { resampler.resample(ShortArray(16), 0) }
        assertThrows(IllegalArgumentException::class.java) { LinearResampler(0) }
    }

    @Test
    fun `重置后相位回到流起点`() {
        val resampler = LinearResampler()
        resampler.resample(ShortArray(100), 44_100)
        resampler.reset()

        assertEquals(36, resampler.resample(ShortArray(100), 44_100).size, "重置后重新从块首计算")
    }

    private companion object {
        const val OUT_100MS = 1_600
    }
}
