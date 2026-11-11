package com.echofold.capture.pcm

import com.echofold.core.asr.engine.PcmFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 振幅窗口、RMS/peak 计算与暂停冻结的 JVM 单测。 */
class AmplitudeSamplerTest {

    private fun constant(value: Short, count: Int): ShortArray = ShortArray(count) { value }

    @Test
    fun `窗口未满不产生样本，凑满后产生一个窗口`() {
        val sampler = AmplitudeSampler(windowFrames = 4)

        assertTrue(sampler.append(constant(1, 3), 0, 3, 48_000, 0L, 0L).isEmpty())
        val samples = sampler.append(constant(1, 1), 0, 1, 48_000, 1_000_000L, 0L)

        assertEquals(1, samples.size)
        assertEquals(4, samples.single().frames)
        assertEquals(0L, samples.single().capturedStartMs)
        assertEquals(0L, samples.single().capturedEndMs)
        assertEquals(1L, sampler.sampleCount)
    }

    @Test
    fun `RMS 与 peak 按实际 PCM 计算`() {
        val sampler = AmplitudeSampler(windowFrames = 4)
        val pcm = shortArrayOf(16_384, -16_384, 16_384, -16_384)

        val sample = sampler.append(pcm, 0, pcm.size, 48_000, 0L, 0L).single()

        assertEquals(0.5, sample.rms, 1e-9)
        assertEquals(0.5, sample.peak, 1e-9)
    }

    @Test
    fun `静音窗口的 RMS 与 peak 为零`() {
        val sampler = AmplitudeSampler(windowFrames = 8)
        val sample = sampler.append(constant(0, 8), 0, 8, 48_000, 0L, 0L).single()

        assertEquals(0.0, sample.rms, 0.0)
        assertEquals(0.0, sample.peak, 0.0)
    }

    @Test
    fun `满量程负值归一化为 1`() {
        val sampler = AmplitudeSampler(windowFrames = 2)
        val sample = sampler.append(shortArrayOf(Short.MIN_VALUE, Short.MIN_VALUE), 0, 2, 48_000, 0L, 0L).single()

        assertEquals(1.0, sample.rms, 1e-9)
        assertEquals(1.0, sample.peak, 1e-9)
    }

    @Test
    fun `大块 PCM 可产生多个窗口且时间轴连续`() {
        val sampler = AmplitudeSampler(windowFrames = 4)
        val samples = sampler.append(constant(1_000, 10), 0, 10, 1_000, 0L, 0L)

        assertEquals(2, samples.size)
        assertEquals(2L, sampler.sampleCount)
        assertEquals(0L, samples[0].capturedStartMs)
        assertEquals(4L, samples[0].capturedEndMs)
        assertEquals(4L, samples[1].capturedStartMs)
        assertEquals(8L, samples[1].capturedEndMs)
        assertEquals(4, samples[1].frames)
    }

    @Test
    fun `暂停期间不产生样本且丢弃未满窗口`() {
        val sampler = AmplitudeSampler(windowFrames = 4)
        sampler.append(constant(100, 2), 0, 2, 1_000, 0L, 0L)

        sampler.pause()
        assertTrue(sampler.isPaused)
        assertTrue(sampler.append(constant(100, 4), 0, 4, 1_000, 1_000_000_000L, 5_000L).isEmpty())
        assertEquals(0L, sampler.sampleCount)

        sampler.resume()
        val resumed = sampler.append(constant(100, 4), 0, 4, 1_000, 2_000_000_000L, 9_000L).single()
        assertEquals(9_000L, resumed.capturedStartMs)
        assertEquals(9_004L, resumed.capturedEndMs)
        assertEquals(1L, sampler.sampleCount)
    }

    @Test
    fun `通过 AmplitudeSink 消费分发帧并携带单调时间戳`() {
        val received = mutableListOf<AmplitudeSample>()
        val sampler = AmplitudeSampler(windowFrames = 2) { received += it }
        val frame = PcmFrame(
            sessionId = "session-1",
            frameIndex = 7L,
            monoNs = 5_000_000_000L,
            capturedStartMs = 1_234L,
            sampleRate = 1_000,
            samples = shortArrayOf(100, 200),
        )

        sampler.accept(frame)

        assertEquals(1L, sampler.sampleCount)
        assertEquals(5_002_000_000L, received.single().monoNs)
        assertEquals(1_234L, received.single().capturedStartMs)
        assertEquals(1_236L, received.single().capturedEndMs)
    }

    @Test
    fun `回调监听器与返回值一致`() {
        val received = mutableListOf<AmplitudeSample>()
        val sampler = AmplitudeSampler(windowFrames = 2) { received += it }
        val produced = sampler.append(constant(1, 2), 0, 2, 48_000, 0L, 0L)

        assertEquals(produced, received)
    }

    @Test
    fun `窗口大小必须为正数`() {
        assertThrows(IllegalArgumentException::class.java) {
            AmplitudeSampler(windowFrames = 0)
        }
    }
}
