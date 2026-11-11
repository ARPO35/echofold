package com.echofold.core.asr.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** ASR 引擎契约里的数据类校验（不含推理）。 */
class AsrEngineModelTest {
    private val model = ModelRef(
        modelId = "x-asr-480ms-int8",
        modelVersion = "1.2.0",
        installationId = "install-1",
        sha256 = "91957793597545bd91d439dd0fa97c64b27c897a9041a960c860519c9f373f1d",
    )

    private fun frame(
        samples: ShortArray = shortArrayOf(1, 2, 3, 4),
        offset: Int = 1,
        length: Int = 2,
        monoNs: Long = 1_000_000_000L,
        capturedStartMs: Long = 0L,
    ) = PcmFrame(
        sessionId = "session-1",
        frameIndex = 0L,
        monoNs = monoNs,
        capturedStartMs = capturedStartMs,
        sampleRate = 16_000,
        samples = samples,
        offset = offset,
        length = length,
    )

    @Test
    fun `合法 PCM 帧保留共享缓冲区与视图窗口`() {
        val samples = shortArrayOf(1, 2, 3, 4)
        val pcm = frame(samples = samples)

        assertEquals(4, pcm.samples.size)
        assertEquals(1, pcm.offset)
        assertEquals(2, pcm.length)
        assertArrayEquals(samples, pcm.samples)
    }

    @Test
    fun `默认窗口覆盖整个缓冲区`() {
        val pcm = PcmFrame(
            sessionId = "session-1",
            frameIndex = 3L,
            monoNs = 2_000_000_000L,
            capturedStartMs = 500L,
            sampleRate = 16_000,
            samples = shortArrayOf(7, 8, 9),
        )

        assertEquals(0, pcm.offset)
        assertEquals(3, pcm.length)
    }

    @Test
    fun `offset 与 length 越界必须拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { frame(offset = -1) }
        assertThrows(IllegalArgumentException::class.java) { frame(offset = 5) }
        assertThrows(IllegalArgumentException::class.java) { frame(offset = 3, length = 2) }
        assertThrows(IllegalArgumentException::class.java) { frame(length = 0) }
        assertThrows(IllegalArgumentException::class.java) { frame(length = -1) }
    }

    @Test
    fun `时钟与偏移不能为负数`() {
        assertThrows(IllegalArgumentException::class.java) { frame(monoNs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { frame(capturedStartMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) {
            PcmFrame(
                sessionId = "session-1",
                frameIndex = -1L,
                monoNs = 0L,
                capturedStartMs = 0L,
                sampleRate = 16_000,
                samples = shortArrayOf(1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PcmFrame(
                sessionId = " ",
                frameIndex = 0L,
                monoNs = 0L,
                capturedStartMs = 0L,
                sampleRate = 16_000,
                samples = shortArrayOf(1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PcmFrame(
                sessionId = "session-1",
                frameIndex = 0L,
                monoNs = 0L,
                capturedStartMs = 0L,
                sampleRate = 0,
                samples = shortArrayOf(1),
            )
        }
    }

    @Test
    fun `模型引用要求小写 64 位哈希`() {
        assertEquals(64, model.sha256.length)
        assertThrows(IllegalArgumentException::class.java) { model.copy(sha256 = "ABC") }
        assertThrows(IllegalArgumentException::class.java) {
            model.copy(sha256 = "91957793597545BD91D439DD0FA97C64B27C897A9041A960C860519C9F373F1D")
        }
        assertThrows(IllegalArgumentException::class.java) { model.copy(modelVersion = "") }
    }

    @Test
    fun `识别配置要求正数参数`() {
        val config = AsrConfig(model = model)
        assertEquals(16_000, config.sampleRate)
        assertEquals(480, config.chunkMs)
        assertEquals(1, config.threads)
        assertEquals(true, config.enableVad)

        assertThrows(IllegalArgumentException::class.java) { config.copy(sampleRate = 0) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(chunkMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(threads = 0) }
    }
}
