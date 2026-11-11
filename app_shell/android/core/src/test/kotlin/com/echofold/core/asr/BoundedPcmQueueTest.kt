package com.echofold.core.asr

import com.echofold.core.asr.engine.PcmFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 有界 ASR PCM 队列：固定容量、拒绝而非覆盖、背压置位与可诊断指标。 */
class BoundedPcmQueueTest {
    private fun frame(index: Int): PcmFrame = PcmFrame(
        sessionId = SESSION,
        frameIndex = index.toLong(),
        monoNs = index.toLong() * 10_000_000L,
        capturedStartMs = index.toLong() * 10L,
        sampleRate = 16_000,
        samples = ShortArray(SAMPLES_PER_FRAME),
    )

    @Test
    fun `空队列指标自洽且不出队`() {
        val queue = BoundedPcmQueue(4)

        assertNull(queue.poll())
        assertNull(queue.peek())
        assertEquals(0, queue.depth())
        val metrics = queue.metrics()
        assertEquals(4, metrics.capacity)
        assertEquals(0, metrics.depth)
        assertEquals(0, metrics.highWatermark)
        assertNull(metrics.oldestEnqueuedMonoNs)
        assertEquals(0L, metrics.newestEnqueuedMonoNs)
        assertEquals(0L, metrics.rejectedCount)
        assertFalse(metrics.blocked)
    }

    @Test
    fun `饱和后拒绝新帧且不覆盖已接受帧`() {
        val queue = BoundedPcmQueue(3)
        repeat(3) { assertEquals(PcmOfferResult.ACCEPTED, queue.offer(frame(it))) }

        val before = queue.metrics()
        assertEquals(3, before.depth)
        assertEquals(3, before.highWatermark)
        assertEquals(0L, before.oldestEnqueuedMonoNs)
        assertEquals(20_000_000L, before.newestEnqueuedMonoNs)

        assertEquals(PcmOfferResult.REJECTED_FULL, queue.offer(frame(3)))

        val after = queue.metrics()
        assertEquals(3, after.depth, "拒绝不得扩容")
        assertEquals(1L, after.rejectedCount)
        assertTrue(after.blocked, "REJECTED_FULL 必须置位 BLOCKED 背压")
        assertEquals(3, after.highWatermark)
        assertEquals(0L, after.oldestEnqueuedMonoNs, "队首帧必须原样保留")
        assertEquals(20_000_000L, after.newestEnqueuedMonoNs, "被拒帧不得改写入队范围")
        assertEquals(0L, queue.peek()?.frameIndex)
    }

    @Test
    fun `FIFO 出队且水位保留峰值`() {
        val queue = BoundedPcmQueue(3)
        repeat(3) { queue.offer(frame(it)) }

        assertEquals(0L, queue.poll()?.frameIndex)
        assertEquals(1L, queue.peek()?.frameIndex)
        assertEquals(2, queue.depth())

        val drained = queue.metrics()
        assertEquals(2, drained.depth)
        assertEquals(3, drained.highWatermark, "水位是峰值，不随出队回退")
        assertEquals(10_000_000L, drained.oldestEnqueuedMonoNs)
        assertEquals(20_000_000L, drained.newestEnqueuedMonoNs)

        queue.poll()
        queue.poll()
        val empty = queue.metrics()
        assertEquals(0, empty.depth)
        assertEquals(3, empty.highWatermark)
        assertNull(empty.oldestEnqueuedMonoNs)
        assertEquals(20_000_000L, empty.newestEnqueuedMonoNs)
    }

    @Test
    fun `阻断时拒绝入队并可在显式解除后恢复`() {
        val queue = BoundedPcmQueue(2)
        queue.setBlocked(true)

        assertEquals(PcmOfferResult.REJECTED_FULL, queue.offer(frame(0)))
        assertEquals(0, queue.depth())
        assertEquals(1L, queue.metrics().rejectedCount)
        assertTrue(queue.metrics().blocked)

        queue.setBlocked(false)
        assertEquals(PcmOfferResult.ACCEPTED, queue.offer(frame(1)))
        assertEquals(1, queue.depth())
        assertFalse(queue.metrics().blocked)
    }

    @Test
    fun `清空返回移除帧数并保留历史水位与拒绝计数`() {
        val queue = BoundedPcmQueue(4)
        repeat(3) { queue.offer(frame(it)) }
        queue.offer(frame(3))
        queue.offer(frame(4)) // 第 5 帧被拒绝

        assertEquals(4, queue.clear())
        val metrics = queue.metrics()
        assertEquals(0, metrics.depth)
        assertEquals(4, metrics.highWatermark)
        assertEquals(1L, metrics.rejectedCount)
        assertNull(metrics.oldestEnqueuedMonoNs)
        assertNull(queue.poll())
    }

    @Test
    fun `容量必须为正数`() {
        assertThrows(IllegalArgumentException::class.java) { BoundedPcmQueue(0) }
        assertThrows(IllegalArgumentException::class.java) { BoundedPcmQueue(-1) }
    }

    private companion object {
        const val SESSION = "session-1"
        const val SAMPLES_PER_FRAME = 160
    }
}
