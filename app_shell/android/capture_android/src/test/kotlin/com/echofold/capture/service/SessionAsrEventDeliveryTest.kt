package com.echofold.capture.service

import com.echofold.core.asr.AsrEvent
import com.echofold.core.asr.AsrEventBuffer
import com.echofold.core.asr.engine.AsrEngineException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SessionAsrEventDeliveryTest {
    private fun final(seq: Long): AsrEvent.Final = AsrEvent.Final(
        sessionId = "session",
        generation = 0,
        seq = seq,
        finalId = "session:final:$seq",
        text = "证据$seq",
        finalizedAtUtcMs = 10_000 + seq,
        captureStartedMonoNs = 1_000 + seq,
        captureEndedMonoNs = 2_000 + seq,
        capturedStartMs = seq,
        capturedEndMs = seq + 1,
        chunkIds = listOf("chunk-0"),
    )

    @Test
    fun `队列满时保留FINAL并在缓冲腾空后按序重试`() {
        val buffer = AsrEventBuffer(capacity = 1)
        val delivery = SessionAsrEventDelivery(buffer)
        val alreadyQueued = final(0)
        val retained = final(1)
        assertTrue(buffer.offer(alreadyQueued))

        assertFalse(delivery.offer(retained))
        assertNotNull(delivery.takeFailure())
        assertNull(delivery.takeFailure())
        delivery.retryPending()
        val first = mutableListOf<AsrEvent>()
        buffer.deliver(first::add)
        assertEquals(listOf(alreadyQueued), first)

        delivery.retryPending()
        val second = mutableListOf<AsrEvent>()
        buffer.deliver(second::add)
        assertEquals(listOf(retained), second)
    }

    @Test
    fun `已有待发FINAL时拒绝继续生产且阻止新会话直到交付`() {
        val buffer = AsrEventBuffer(capacity = 1)
        val delivery = SessionAsrEventDelivery(buffer)
        assertTrue(buffer.offer(final(0)))
        val firstPending = final(1)
        val secondPending = final(2)

        assertFalse(delivery.offer(firstPending))
        repeat(1000) {
            assertThrows(AsrEngineException::class.java) { delivery.offer(secondPending) }
        }
        assertThrows(IllegalStateException::class.java) { delivery.requireReadyForSession() }
        buffer.deliver { }
        delivery.retryPending()
        val delivered = mutableListOf<AsrEvent>()
        buffer.deliver(delivered::add)
        assertEquals(listOf(firstPending), delivered)
        delivery.requireReadyForSession()
        assertTrue(delivery.offer(secondPending))
        buffer.deliver(delivered::add)
        assertEquals(listOf(firstPending, secondPending), delivered)
    }
}
