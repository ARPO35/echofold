package com.echofold.core.time

import com.echofold.core.model.ClockAnchor
import com.echofold.core.model.ClockQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 会话三时间域：暂停间隙排除、块边界 offset 单调、UTC 校时隔离与跨锚点拒绝。
 */
class SessionTimelineTest {
    private val sessionId = "session-1"
    private val baseUtcMs = 1_700_000_000_000L
    private val baseMonoNs = 1_000_000_000L
    private val ms = 1_000_000L

    private class FakeClock(
        var utcMs: Long = 1_700_000_000_000L,
        var monoNs: Long = 1_000_000_000L,
    ) : ClockSource {
        override fun utcNowMs(): Long = utcMs

        override fun monoNowNs(): Long = monoNs
    }

    private fun anchor(
        utcMs: Long = baseUtcMs,
        monoNs: Long = baseMonoNs,
        quality: ClockQuality = ClockQuality.DEVICE_BOOT_BASED,
    ) = ClockAnchor(sessionId = sessionId, utcMs = utcMs, monoNs = monoNs, quality = quality)

    private fun timeline(clock: ClockSource = FakeClock(baseUtcMs, baseMonoNs)) =
        SessionTimeline(sessionId, anchor(), clock)

    @Test
    fun `暂停间隙不计入有效音频 offset 且继续后 offset 连续`() {
        val timeline = timeline()

        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        val first = timeline.onCaptureStopped(baseMonoNs + 1_000L * ms, baseUtcMs + 1_000)

        assertEquals(0L, first.capturedStartMs)
        assertEquals(1_000L, first.capturedEndMs)
        assertEquals(1_000L, timeline.currentCapturedMs)
        assertNull(timeline.capturedMsAt(baseMonoNs + 5_000L * ms), "暂停间隙不映射到任何音频 offset")

        val resumeMonoNs = baseMonoNs + 10_000L * ms
        timeline.onCaptureStarted(resumeMonoNs, baseUtcMs + 10_000)
        val second = timeline.onCaptureStopped(resumeMonoNs + 1_500L * ms, baseUtcMs + 11_500)

        assertEquals(1_000L, second.capturedStartMs, "继续后必须从暂停前结束 offset 接续")
        assertEquals(2_500L, second.capturedEndMs)
        assertEquals(2_500L, timeline.currentCapturedMs)
        assertEquals(1_000L, timeline.capturedMsAt(resumeMonoNs))
        assertEquals(1_500L, timeline.capturedMsAt(resumeMonoNs + 500L * ms))
        assertTrue(timeline.captureSpans().zipWithNext().all { (a, b) -> a.capturedEndMs <= b.capturedStartMs })
    }

    @Test
    fun `跨块边界的 offset 单调不减且暂停后仍从上一块结束接续`() {
        val timeline = timeline()

        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        val first = timeline.onChunkBoundary(0, baseMonoNs + 60_000L * ms)
        assertEquals(60_000L, first.capturedMs)

        // 第一块实际采集到 61 秒后暂停，下一块的切点位于继续瞬间。
        timeline.onCaptureStopped(baseMonoNs + 61_000L * ms, baseUtcMs + 61_000)
        val resumeMonoNs = baseMonoNs + 600_000L * ms
        timeline.onCaptureStarted(resumeMonoNs, baseUtcMs + 600_000)

        val second = timeline.onChunkBoundary(1, resumeMonoNs)
        val third = timeline.onChunkBoundary(2, resumeMonoNs + 30_000L * ms)

        assertEquals(61_000L, second.capturedMs, "暂停 9 分钟不得进入有效音频 offset")
        assertEquals(91_000L, third.capturedMs)

        val offsets = timeline.chunkBoundaries().map { it.capturedMs }
        assertEquals(listOf(60_000L, 61_000L, 91_000L), offsets)
        assertTrue(offsets.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `UTC 回拨与前跳不改变 offset 和块边界`() {
        val correctedUtc = { monoNs: Long ->
            when {
                monoNs < baseMonoNs + 61_000L * ms -> baseUtcMs
                monoNs < baseMonoNs + 600_000L * ms -> baseUtcMs - 3_600_000L // 回拨 1 小时
                else -> baseUtcMs + 86_400_000L // 前跳 1 天
            }
        }
        val wildTimeline = timeline()
        val steadyTimeline = timeline()

        fun feed(timeline: SessionTimeline, utcAt: (Long) -> Long) {
            timeline.onCaptureStarted(baseMonoNs, utcAt(baseMonoNs))
            timeline.onChunkBoundary(0, baseMonoNs + 60_000L * ms)
            timeline.onCaptureStopped(baseMonoNs + 61_000L * ms, utcAt(baseMonoNs + 61_000L * ms))
            val resumeMonoNs = baseMonoNs + 600_000L * ms
            timeline.onCaptureStarted(resumeMonoNs, utcAt(resumeMonoNs))
            timeline.onChunkBoundary(1, resumeMonoNs)
            timeline.onCaptureStopped(resumeMonoNs + 30_000L * ms, utcAt(resumeMonoNs + 30_000L * ms))
        }

        feed(wildTimeline, correctedUtc)
        feed(steadyTimeline, { baseUtcMs + (it - baseMonoNs) / ms })

        assertEquals(
            steadyTimeline.captureSpans().map { it.capturedStartMs to it.capturedEndMs },
            wildTimeline.captureSpans().map { it.capturedStartMs to it.capturedEndMs },
        )
        assertEquals(steadyTimeline.chunkBoundaries(), wildTimeline.chunkBoundaries())
        assertEquals(
            baseUtcMs - 3_600_000L,
            wildTimeline.captureSpans().first().endedAtUtcMs,
            "UTC 仍按实际校时值记录，只是不参与 offset 计算",
        )
    }

    @Test
    fun `重启产生 RESTART_DEGRADED 新锚点并拒绝旧锚点裸单调值`() {
        val timeline = timeline()
        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        timeline.onCaptureStopped(baseMonoNs + 1_000L * ms, baseUtcMs + 1_000)
        val oldMonoNs = baseMonoNs + 500L * ms

        val restartMonoNs = baseMonoNs + 5_000L * ms
        val restarted = timeline.anchorForRestart(restartMonoNs, baseUtcMs + 20_000)

        assertEquals(ClockQuality.RESTART_DEGRADED, restarted.quality)
        assertEquals(restartMonoNs, restarted.monoNs)
        assertEquals(restarted, timeline.anchor)
        assertThrows(IllegalArgumentException::class.java) { timeline.capturedMsAt(oldMonoNs) }
        assertThrows(IllegalArgumentException::class.java) { timeline.onChunkBoundary(0, oldMonoNs) }
        assertThrows(IllegalArgumentException::class.java) {
            timeline.onCaptureStarted(oldMonoNs, baseUtcMs + 30_000)
        }

        // 新锚点内 offset 跨重启继续，不因锚点切换重置。
        timeline.onCaptureStarted(restartMonoNs, baseUtcMs + 30_000)
        val span = timeline.onCaptureStopped(restartMonoNs + 1_000L * ms, baseUtcMs + 31_000)
        assertEquals(1_000L, span.capturedStartMs)
        assertEquals(2_000L, span.capturedEndMs)
    }

    @Test
    fun `设备重启后新锚点单调值低于旧区间时仍拒绝旧值`() {
        val timeline = timeline()
        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        timeline.onCaptureStopped(baseMonoNs + 2_000L * ms, baseUtcMs + 2_000)
        val oldMonoNs = baseMonoNs + 1_500L * ms

        // 模拟设备重启：单调时钟从接近 0 重新开始。
        val restarted = timeline.anchorForRestart(500L * ms, baseUtcMs + 120_000)
        assertEquals(ClockQuality.RESTART_DEGRADED, restarted.quality)

        assertThrows(IllegalArgumentException::class.java) { timeline.capturedMsAt(oldMonoNs) }
        assertThrows(IllegalArgumentException::class.java) { timeline.onChunkBoundary(0, oldMonoNs) }
        // 新锚点起点以下的旧值同样被拒绝。
        assertThrows(IllegalArgumentException::class.java) { timeline.capturedMsAt(100L * ms) }

        // 新锚点内全新值可正常解释：尚未采集时无 offset。
        assertNull(timeline.capturedMsAt(600L * ms))
        timeline.onCaptureStarted(600L * ms, baseUtcMs + 121_000)
        assertEquals(2_000L, timeline.capturedMsAt(600L * ms), "重启后 offset 从既有累计值继续")
    }

    @Test
    fun `采集中不允许直接建立重启锚点`() {
        val timeline = timeline()
        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)

        assertThrows(IllegalStateException::class.java) {
            timeline.anchorForRestart(baseMonoNs + 10_000L * ms, baseUtcMs + 10_000)
        }
        assertTrue(timeline.isCapturing)
    }

    @Test
    fun `采集中的 currentCapturedMs 随单调时钟推进暂停后冻结`() {
        val clock = FakeClock(baseUtcMs, baseMonoNs)
        val timeline = timeline(clock)

        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        clock.monoNs = baseMonoNs + 2_500L * ms
        assertEquals(2_500L, timeline.currentCapturedMs)

        timeline.onCaptureStopped(clock.monoNs, clock.utcMs)
        clock.monoNs += 10_000L * ms
        assertEquals(2_500L, timeline.currentCapturedMs, "暂停期间 offset 不增长")
        assertFalse(timeline.isCapturing)
    }

    @Test
    fun `采集段生命周期违规立即失败`() {
        val timeline = timeline()
        assertThrows(IllegalStateException::class.java) {
            timeline.onCaptureStopped(baseMonoNs + ms, baseUtcMs)
        }

        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        assertThrows(IllegalStateException::class.java) {
            timeline.onCaptureStarted(baseMonoNs + ms, baseUtcMs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timeline.onCaptureStopped(baseMonoNs - ms, baseUtcMs)
        }
    }

    @Test
    fun `块序号严格递增且边界单调时钟不得回退`() {
        val timeline = timeline()
        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        timeline.onChunkBoundary(0, baseMonoNs + 10_000L * ms)

        assertThrows(IllegalArgumentException::class.java) {
            timeline.onChunkBoundary(0, baseMonoNs + 20_000L * ms)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timeline.onChunkBoundary(1, baseMonoNs + 5_000L * ms)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timeline.onChunkBoundary(-1, baseMonoNs + 30_000L * ms)
        }
    }

    @Test
    fun `未采集区间和暂停后时刻不映射 offset`() {
        val timeline = timeline()
        assertNull(timeline.capturedMsAt(baseMonoNs))

        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        timeline.onCaptureStopped(baseMonoNs + 1_000L * ms, baseUtcMs + 1_000)

        assertEquals(1_000L, timeline.capturedMsAt(baseMonoNs + 1_000L * ms))
        assertNull(timeline.capturedMsAt(baseMonoNs + 1_001L * ms))
        assertNull(timeline.capturedMsAt(baseMonoNs + 60_000L * ms))
    }

    @Test
    fun `重启后块边界继续沿用累计 offset`() {
        val timeline = timeline()
        timeline.onCaptureStarted(baseMonoNs, baseUtcMs)
        timeline.onChunkBoundary(0, baseMonoNs + 2_000L * ms)
        timeline.onCaptureStopped(baseMonoNs + 2_000L * ms, baseUtcMs + 2_000)

        val restartMonoNs = baseMonoNs + 10_000L * ms
        timeline.anchorForRestart(restartMonoNs, baseUtcMs + 60_000)

        val boundary = timeline.onChunkBoundary(1, restartMonoNs + 1_000L * ms)
        assertEquals(2_000L, boundary.capturedMs, "重启不重置会话有效音频 offset")
        assertEquals(listOf(2_000L, 2_000L), timeline.chunkBoundaries().map { it.capturedMs })
    }
}
