package com.echofold.capture.pcm

import com.echofold.core.asr.AsrEvent
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionAsrEventPublisherTest {
    private val model = ModelRef("model", "version", "install", "a".repeat(64))
    private val chunks = listOf(
        AudioChunk("s-0", "s", 0, ChunkState.OPEN, "tmp", null, 1, null,
            null, null, 0, null, null, null, null, null, null, null, 0),
    )

    private fun frame(index: Long, monoNs: Long, capturedMs: Long) = PcmFrame(
        sessionId = "s", frameIndex = index, monoNs = monoNs,
        capturedStartMs = capturedMs, sampleRate = 16_000, samples = ShortArray(160),
    )

    @Test
    fun `Partial逐帧更新与FINAL携带单调时钟内容区间及音频块引用`() {
        val events = mutableListOf<AsrEvent>()
        val publisher = SessionAsrEventPublisher(
            sessionId = "s", model = model, chunksFor = { _, _ -> chunks },
            offer = { events += it; true }, wallClockMs = { 1234L },
        )
        publisher.onPcmFrame(frame(0, 10_000_000L, 0))
        publisher.partial("你好")
        publisher.partial("你好")
        publisher.onPcmFrame(frame(1, 20_000_000L, 10))
        publisher.partial("你好世界")
        publisher.final("你好世界")

        assertEquals(listOf("你好", "你好世界"), events.filterIsInstance<AsrEvent.Partial>().map { it.text })
        val final = events.filterIsInstance<AsrEvent.Final>().single()
        assertEquals(0, final.capturedStartMs)
        assertEquals(20, final.capturedEndMs)
        assertEquals(10_000_000L, final.captureStartedMonoNs)
        assertEquals(30_000_000L, final.captureEndedMonoNs)
        assertEquals(listOf("s-0"), final.chunkIds)
        assertEquals(1234L, final.finalizedAtUtcMs)
        assertEquals(2L, final.seq)
    }

    @Test
    fun `Partial队列拒绝不推进事件序号导致后续Final可通过门控`() {
        val events = mutableListOf<AsrEvent>()
        val publisher = SessionAsrEventPublisher(
            sessionId = "s", model = model, chunksFor = { _, _ -> chunks },
            offer = { event ->
                if (event is AsrEvent.Partial) false else { events += event; true }
            },
        )
        publisher.onPcmFrame(frame(0, 10, 0))

        publisher.partial("被拒绝的临时结果")
        publisher.final("最终证据")

        assertEquals(0L, events.single().seq)
        assertTrue(events.single() is AsrEvent.Final)
    }

    @Test
    fun `拒绝Final时保留本次不可变证据且抛出失败`() {
        var retained: AsrEvent? = null
        val publisher = SessionAsrEventPublisher(
            sessionId = "s", model = model, chunksFor = { _, _ -> chunks },
            offer = { event -> retained = event; false }, wallClockMs = { 1L },
        )
        publisher.onPcmFrame(frame(0, 10, 0))

        assertThrows(IllegalStateException::class.java) { publisher.final("证据") }
        assertTrue(retained is AsrEvent.Final)
        assertFalse((retained as AsrEvent.Final).text.isBlank())
    }

    @Test
    fun `FINAL过载后保留唯一序号并在新PCM前不重复生成证据`() {
        val buffer = com.echofold.core.asr.AsrEventBuffer(capacity = 1)
        val delivery = com.echofold.capture.service.SessionAsrEventDelivery(buffer)
        val publisher = SessionAsrEventPublisher("s", model, { _, _ -> chunks }, delivery::offer)
        publisher.onPcmFrame(frame(0, 10, 0))
        publisher.partial("临时结果")
        assertThrows(IllegalStateException::class.java) { publisher.final("证据一") }
        publisher.final("重复冲刷")
        val events = mutableListOf<AsrEvent>()
        buffer.deliver(events::add)
        delivery.retryPending()
        buffer.deliver(events::add)
        publisher.onPcmFrame(frame(1, 20, 10))
        publisher.final("证据二")
        buffer.deliver(events::add)
        assertEquals(listOf(0L, 1L, 2L), events.map { it.seq })
        assertEquals(listOf("s:final:1", "s:final:2"), events.filterIsInstance<AsrEvent.Final>().map { it.finalId })
    }
}
