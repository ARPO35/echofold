package com.echofold.capture.pcm

import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.DispatchState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 单路 PCM 三车道分发、独立背压与无静默丢弃的 JVM 单测。 */
class PcmDispatcherTest {

    private class CollectingAsrSink : AsrSink {
        val frames = mutableListOf<PcmFrame>()
        override fun accept(frame: PcmFrame) {
            frames += frame
        }
    }

    private class CollectingEncoderSink : EncoderSink {
        val frames = mutableListOf<PcmFrame>()
        override fun accept(frame: PcmFrame) {
            frames += frame
        }
    }

    private class CollectingAmplitudeSink : AmplitudeSink {
        val frames = mutableListOf<PcmFrame>()
        override fun accept(frame: PcmFrame) {
            frames += frame
        }
    }

    private class ThrowingAsrSink : AsrSink {
        var remainingFailures = 1
        val delivered = mutableListOf<Long>()
        override fun accept(frame: PcmFrame) {
            if (remainingFailures > 0) {
                remainingFailures--
                throw IllegalStateException("ASR 车道下游异常")
            }
            delivered += frame.frameIndex
        }
    }

    private val asr = CollectingAsrSink()
    private val encoder = CollectingEncoderSink()
    private val amplitude = CollectingAmplitudeSink()

    private fun dispatcher(
        asrCapacity: Int = 4,
        encoderCapacity: Int = 4,
        amplitudeCapacity: Int = 4,
        asrSink: AsrSink = asr,
    ) = PcmDispatcher(
        sessionId = "session-1",
        sampleRate = 48_000,
        subscriptions = listOf(
            LaneSubscription("asr", asrCapacity, asrSink),
            LaneSubscription("encoder", encoderCapacity, encoder),
            LaneSubscription("amplitude", amplitudeCapacity, amplitude),
        ),
    )

    @Test
    fun `一帧 PCM 同时进入三条车道且保留顺序`() {
        val dispatcher = dispatcher()
        val pcm = ShortArray(8) { it.toShort() }

        assertTrue(dispatcher.submit(0L, 1_000_000L, 0L, pcm, 0, 8) is DispatchResult.Accepted)
        assertTrue(dispatcher.submit(1L, 2_000_000L, 5L, pcm, 2, 4) is DispatchResult.Accepted)

        assertEquals(2, dispatcher.drain("asr"))
        assertEquals(2, dispatcher.drain("encoder"))
        assertEquals(2, dispatcher.drain("amplitude"))

        assertEquals(listOf(0L, 1L), asr.frames.map { it.frameIndex })
        assertEquals(listOf(0L, 1L), encoder.frames.map { it.frameIndex })
        assertEquals(listOf(0L, 1L), amplitude.frames.map { it.frameIndex })
        assertEquals(2_000_000L, encoder.frames[1].monoNs)
        assertEquals(5L, encoder.frames[1].capturedStartMs)
        assertArrayEquals(shortArrayOf(2, 3, 4, 5), encoder.frames[1].samples)
        assertEquals(48_000, encoder.frames[1].sampleRate)
    }

    @Test
    fun `单路容量耗尽返回背压并进入 BLOCKED`() {
        val dispatcher = dispatcher(encoderCapacity = 1)

        assertTrue(dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1, 2)) is DispatchResult.Accepted)
        val rejected = dispatcher.submit(1L, 2_000_000L, 1L, shortArrayOf(3, 4))

        assertTrue(rejected is DispatchResult.Backpressured)
        val backpressure = rejected as DispatchResult.Backpressured
        assertEquals("encoder", backpressure.laneId)
        assertEquals(DispatchState.BLOCKED, dispatcher.state)
        assertEquals("encoder", dispatcher.blockingLaneId)
        assertEquals(1L, backpressure.metrics.rejectedCount)
        assertTrue(backpressure.metrics.blocked)
    }

    @Test
    fun `任一路拒绝时不产生半分发`() {
        val dispatcher = dispatcher(encoderCapacity = 1)
        dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1, 2))
        dispatcher.submit(1L, 2_000_000L, 1L, shortArrayOf(3, 4))

        // ASR 与振幅车道仍有空位，但被拒帧不得只进部分车道。
        assertEquals(1, dispatcher.metricsOf("asr")!!.depth)
        assertEquals(1, dispatcher.metricsOf("amplitude")!!.depth)
        assertEquals(1, dispatcher.metricsOf("encoder")!!.depth)
        assertEquals(1L, dispatcher.metricsOf("asr")!!.acceptedCount)
    }

    @Test
    fun `BLOCKED 后新帧被拒绝且不重复计入拒绝数`() {
        val dispatcher = dispatcher(encoderCapacity = 1)
        dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1, 2))
        dispatcher.submit(1L, 2_000_000L, 1L, shortArrayOf(3, 4))

        assertEquals(DispatchResult.Blocked, dispatcher.submit(2L, 3_000_000L, 2L, shortArrayOf(5, 6)))
        assertEquals(1L, dispatcher.metricsOf("encoder")!!.rejectedCount)
    }

    @Test
    fun `车道指标记录容量深度与最高水位`() {
        val dispatcher = dispatcher(asrCapacity = 3, encoderCapacity = 5)

        repeat(3) { index ->
            dispatcher.submit(index.toLong(), 1_000_000L * (index + 1), index.toLong(), shortArrayOf(1))
        }
        val beforeDrain = dispatcher.metricsOf("asr")!!
        assertEquals(3, beforeDrain.capacityFrames)
        assertEquals(3, beforeDrain.depth)
        assertEquals(3, beforeDrain.highWatermark)
        assertEquals(1_000_000L, beforeDrain.oldestEnqueuedMonoNs)
        assertEquals(3_000_000L, beforeDrain.newestEnqueuedMonoNs)
        assertEquals(3L, beforeDrain.acceptedCount)
        assertEquals(0L, beforeDrain.rejectedCount)
        assertFalse(beforeDrain.blocked)

        assertEquals(2, dispatcher.drain("asr", 2))
        val afterDrain = dispatcher.metricsOf("asr")!!
        assertEquals(1, afterDrain.depth)
        assertEquals(3, afterDrain.highWatermark)
        assertEquals(3_000_000L, afterDrain.oldestEnqueuedMonoNs)
        assertEquals(3, dispatcher.metricsOf("encoder")!!.depth)
        assertEquals(5, dispatcher.metricsOf("encoder")!!.capacityFrames)
    }

    @Test
    fun `解除 BLOCKED 后恢复分发并保留历史计数`() {
        val dispatcher = dispatcher(encoderCapacity = 1)
        dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1))
        dispatcher.submit(1L, 2_000_000L, 1L, shortArrayOf(2))

        assertEquals(1, dispatcher.drain("encoder"))
        assertTrue(dispatcher.releaseBlock())
        assertEquals(DispatchState.RUNNING, dispatcher.state)
        assertNull(dispatcher.blockingLaneId)
        assertEquals(1L, dispatcher.metricsOf("encoder")!!.rejectedCount)
        assertFalse(dispatcher.metricsOf("encoder")!!.blocked)

        assertTrue(dispatcher.submit(2L, 3_000_000L, 2L, shortArrayOf(3)) is DispatchResult.Accepted)
        assertFalse(dispatcher.releaseBlock())
    }

    @Test
    fun `frameIndex 必须严格递增`() {
        val dispatcher = dispatcher()
        dispatcher.submit(5L, 1_000_000L, 0L, shortArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.submit(5L, 2_000_000L, 1L, shortArrayOf(2))
        }
    }

    @Test
    fun `采集缓冲区被复用不会改变已入队帧`() {
        val dispatcher = dispatcher()
        val pcm = shortArrayOf(10, 20, 30, 40)
        dispatcher.submit(0L, 1_000_000L, 0L, pcm, 0, 4)

        pcm[0] = 99
        pcm[1] = 99

        dispatcher.drain("asr")
        assertArrayEquals(shortArrayOf(10, 20, 30, 40), asr.frames.single().samples)
    }

    @Test
    fun `消费者异常不放行丢帧并进入 BLOCKED`() {
        val throwing = ThrowingAsrSink()
        val dispatcher = dispatcher(asrSink = throwing)
        dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1, 2))

        assertEquals(0, dispatcher.drain("asr"))
        assertEquals(1, dispatcher.metricsOf("asr")!!.depth)
        assertEquals(1L, dispatcher.metricsOf("asr")!!.consumerFailureCount)
        assertEquals(DispatchState.BLOCKED, dispatcher.state)

        // 放行后同一帧被重新交给消费者：没有任何帧被静默丢弃。
        dispatcher.releaseBlock()
        assertEquals(1, dispatcher.drain("asr"))
        assertEquals(listOf(0L), throwing.delivered)
        assertEquals(0, dispatcher.metricsOf("asr")!!.depth)
    }

    @Test
    fun `已排空但 BLOCKED 时仍报告总深度为零`() {
        val dispatcher = dispatcher(encoderCapacity = 1)
        dispatcher.submit(0L, 1_000_000L, 0L, shortArrayOf(1))
        dispatcher.submit(1L, 2_000_000L, 1L, shortArrayOf(2))
        assertEquals(3, dispatcher.totalDepth())

        dispatcher.drain("asr")
        dispatcher.drain("encoder")
        dispatcher.drain("amplitude")
        assertEquals(0, dispatcher.totalDepth())
        assertEquals(DispatchState.BLOCKED, dispatcher.state)
    }
}
