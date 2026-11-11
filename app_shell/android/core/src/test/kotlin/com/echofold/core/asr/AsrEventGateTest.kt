package com.echofold.core.asr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 事件准入门：eventId 去重、世代单调、同世代按 seq 收敛。
 *
 * 所有断言都落在“可交付事件”上：判定为重复/旧世代/被丢弃时，调用方拿不到任何事件，
 * 因此重复 Health 不可能触发第二次副作用。
 */
class AsrEventGateTest {
    private fun partial(seq: Long, generation: Long = 0L, sessionId: String = SESSION): AsrEvent.Partial =
        AsrEvent.Partial(
            sessionId = sessionId,
            generation = generation,
            seq = seq,
            text = "片段$seq",
            startMs = 0L,
            endMs = 480L,
            modelId = "x-asr-480ms-int8",
            modelVersion = "1.2.0",
        )

    private fun health(seq: Long, generation: Long = 0L): AsrEvent.Health = AsrEvent.Health(
        sessionId = SESSION,
        generation = generation,
        seq = seq,
        rtf = 0.5,
        oldestUnprocessedAgeMs = 0L,
        threadCount = 1,
        memoryBytes = 1L,
        skipCount = 0L,
        queue = QueueMetrics(
            capacity = 8,
            depth = 0,
            highWatermark = 0,
            oldestEnqueuedMonoNs = null,
            newestEnqueuedMonoNs = 0L,
            rejectedCount = 0L,
            blocked = false,
        ),
    )

    private fun seqs(admission: AsrEventGate.Admission): List<Long> = admission.deliverable.map { it.seq }

    @Test
    fun `同一 eventId 重复投递只准入一次`() {
        val gate = AsrEventGate(SESSION)
        val event = partial(1)

        val first = gate.admit(event)
        assertEquals(AsrEventGate.Verdict.ACCEPTED, first.verdict)
        assertEquals(listOf(1L), seqs(first))

        val second = gate.admit(event)
        assertEquals(AsrEventGate.Verdict.DUPLICATE, second.verdict)
        assertTrue(second.deliverable.isEmpty())
        assertFalse(second.dropped)
        assertEquals(1L, gate.deliveredEventCount)
        assertEquals(1L, gate.duplicateEventCount)
    }

    @Test
    fun `重复 Health 不产生额外副作用`() {
        val gate = AsrEventGate(SESSION)
        val sample = health(0)

        assertEquals(AsrEventGate.Verdict.ACCEPTED, gate.admit(sample).verdict)
        val repeated = gate.admit(sample)

        assertEquals(AsrEventGate.Verdict.DUPLICATE, repeated.verdict)
        assertTrue(repeated.deliverable.isEmpty(), "重复健康采样不得再次投递")
        assertEquals(1L, gate.deliveredEventCount)
        assertEquals(0, gate.bufferedEventCount)
        assertEquals(1L, gate.duplicateEventCount)
    }

    @Test
    fun `旧世代事件一律拒绝且不推进序号`() {
        val gate = AsrEventGate(SESSION)
        gate.admit(partial(1, generation = 0))

        assertEquals(3L, gate.advanceGeneration(3))

        val stale = gate.admit(partial(2, generation = 0))
        assertEquals(AsrEventGate.Verdict.STALE, stale.verdict)
        assertTrue(stale.deliverable.isEmpty())
        assertEquals(1L, gate.deliveredEventCount, "旧世代事件不得交付")
        assertEquals(1L, gate.staleEventCount)

        val current = gate.admit(partial(0, generation = 3))
        assertEquals(AsrEventGate.Verdict.ACCEPTED, current.verdict)
        assertEquals(listOf(0L), seqs(current))
    }

    @Test
    fun `世代推进是单调的`() {
        val gate = AsrEventGate(SESSION)

        assertEquals(5L, gate.advanceGeneration(5))
        assertEquals(5L, gate.advanceGeneration(2), "较小的世代值不得回退")
        assertEquals(5L, gate.generation)
    }

    @Test
    fun `更高世代立即接管并清空旧缓冲`() {
        val gate = AsrEventGate(SESSION, outOfOrderWindow = 4)
        gate.admit(partial(1))
        val buffered = gate.admit(partial(3))
        assertEquals(AsrEventGate.Verdict.OUT_OF_ORDER, buffered.verdict)
        assertEquals(1, gate.bufferedEventCount)

        val takeover = gate.admit(partial(0, generation = 1))

        assertEquals(AsrEventGate.Verdict.ACCEPTED, takeover.verdict)
        assertEquals(listOf(0L), seqs(takeover))
        assertEquals(0, gate.bufferedEventCount, "旧世代缓冲必须作废")
        assertEquals(1L, takeover.deliverable.single().generation)
        assertEquals(AsrEventGate.Verdict.STALE, gate.admit(partial(4)).verdict)
    }

    @Test
    fun `乱序窗口内缓冲并在缺口补齐后按序交付`() {
        val gate = AsrEventGate(SESSION, outOfOrderWindow = 2)
        gate.admit(partial(5))

        val ahead = gate.admit(partial(7))
        assertEquals(AsrEventGate.Verdict.OUT_OF_ORDER, ahead.verdict)
        assertFalse(ahead.dropped, "窗口内的乱序只缓冲，不丢弃")
        assertTrue(ahead.deliverable.isEmpty())
        assertEquals(1, gate.bufferedEventCount)

        val fill = gate.admit(partial(6))
        assertEquals(AsrEventGate.Verdict.ACCEPTED, fill.verdict)
        assertEquals(listOf(6L, 7L), seqs(fill), "缺口补齐后必须按 seq 升序交付")
        assertEquals(0, gate.bufferedEventCount)
        assertEquals(3L, gate.deliveredEventCount)
    }

    @Test
    fun `超出乱序窗口的事件被丢弃并计数`() {
        val gate = AsrEventGate(SESSION, outOfOrderWindow = 1)
        gate.admit(partial(5))

        val tooFar = gate.admit(partial(8))
        assertEquals(AsrEventGate.Verdict.OUT_OF_ORDER, tooFar.verdict)
        assertTrue(tooFar.dropped)
        assertTrue(tooFar.deliverable.isEmpty())
        assertEquals(1L, gate.droppedOutOfOrderEventCount)
        assertEquals(0, gate.bufferedEventCount)

        // 迟到（序号已越过）同样只丢弃，不回灌。
        val late = gate.admit(partial(4))
        assertEquals(AsrEventGate.Verdict.OUT_OF_ORDER, late.verdict)
        assertTrue(late.dropped)
        assertEquals(2L, gate.droppedOutOfOrderEventCount)

        // 缺口恰好等于窗口时仍可缓冲。
        val insideWindow = gate.admit(partial(7))
        assertEquals(AsrEventGate.Verdict.OUT_OF_ORDER, insideWindow.verdict)
        assertFalse(insideWindow.dropped)
        assertEquals(1, gate.bufferedEventCount)
    }

    @Test
    fun `缺口补齐后解锁的事件按 seq 升序交付`() {
        val gate = AsrEventGate(SESSION, outOfOrderWindow = 3)
        gate.admit(partial(0))
        gate.admit(partial(3))
        gate.admit(partial(2))

        val fill = gate.admit(partial(1))

        assertEquals(AsrEventGate.Verdict.ACCEPTED, fill.verdict)
        assertEquals(listOf(1L, 2L, 3L), seqs(fill))
        assertEquals(4L, gate.deliveredEventCount)
        assertEquals(0, gate.bufferedEventCount)
    }

    @Test
    fun `其他会话的事件被拒绝`() {
        val gate = AsrEventGate(SESSION)
        assertThrows(IllegalArgumentException::class.java) {
            gate.admit(partial(0, sessionId = "session-2"))
        }
    }

    @Test
    fun `窗口与会话参数必须合法`() {
        assertThrows(IllegalArgumentException::class.java) { AsrEventGate(" ") }
        assertThrows(IllegalArgumentException::class.java) { AsrEventGate(SESSION, outOfOrderWindow = 0) }
    }

    private companion object {
        const val SESSION = "session-1"
    }
}
