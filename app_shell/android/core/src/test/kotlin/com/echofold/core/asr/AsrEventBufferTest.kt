package com.echofold.core.asr

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AsrEventBufferTest {
    private fun event(seq: Long) = AsrEvent.Partial("session", 0, seq, "文字", 0, 480, "model", "1")

    @Test fun `队满拒绝新事件且原事件仍按序交付`() {
        val buffer = AsrEventBuffer(2)
        assertTrue(buffer.offer(event(1)))
        assertTrue(buffer.offer(event(2)))
        assertFalse(buffer.offer(event(3)))
        val received = mutableListOf<Long>()
        buffer.deliver { received.add(it.seq) }
        assertEquals(listOf(1L, 2L), received)
        assertEquals(1L, buffer.rejectedCount)
        assertTrue(buffer.offer(event(3)))
    }

    @Test fun `消费者失败保留未投递证据`() {
        val buffer = AsrEventBuffer(2)
        buffer.offer(event(1))
        assertThrows(IllegalStateException::class.java) {
            buffer.deliver { error("通道断开") }
        }
        val received = mutableListOf<Long>()
        buffer.deliver { received.add(it.seq) }
        assertEquals(listOf(1L), received)
    }

    @Test fun `GAP序列化保留区间及模型身份`() {
        val gap = AsrEvent.Gap("session", 1, 2, "gap-1", 1000, 1, 2,
            0, 480, "ASR_OVERLOAD", listOf("chunk-1"), "model", "1", 16000)
        assertEquals(gap, AsrEventJson.decode(AsrEventJson.encode(gap)))
        val payload = AsrEventJson.toChannelMap(gap)
        assertEquals("gap", payload["kind"])
        assertEquals(480L, payload["capturedEndMs"])
        assertFalse(payload.containsKey("text"))
    }
}
