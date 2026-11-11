package com.echofold.core.asr

import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.GapReason
import com.echofold.core.model.SegmentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 15 秒过载协议：严格阈值、GAP 唯一性、证据完整性、世代推进与积压清空。 */
class GapCoordinatorTest {
    private val model = ModelRef(
        modelId = "x-asr-480ms-int8",
        modelVersion = "1.2.0",
        installationId = "install-1",
        sha256 = "a".repeat(64),
    )

    private fun coordinator(
        onGap: (AsrGap) -> Unit = {},
        clearAsrBacklog: () -> Unit = {},
    ): GapCoordinator = GapCoordinator(
        sessionId = SESSION,
        model = model,
        onGap = onGap,
        clearAsrBacklog = clearAsrBacklog,
        utcNowMs = { FIXED_UTC_MS },
    )

    private fun sample(
        oldestUnprocessedMonoNs: Long,
        newestCapturedMonoNs: Long,
        skippedCapturedStartMs: Long = 1_000L,
        skippedCapturedEndMs: Long = 16_001L,
        chunkIds: List<String> = listOf("chunk-0", "chunk-1"),
    ): AsrOverloadSample = AsrOverloadSample(
        oldestUnprocessedMonoNs = oldestUnprocessedMonoNs,
        newestCapturedMonoNs = newestCapturedMonoNs,
        skippedCapturedStartMs = skippedCapturedStartMs,
        skippedCapturedEndMs = skippedCapturedEndMs,
        chunkIds = chunkIds,
    )

    @Test
    fun `恰好 15000ms 不触发`() {
        val coordinator = coordinator()

        val result = coordinator.observe(sample(1_000_000_000L, 16_000_000_000L))

        assertEquals(GapResolution.WithinBudget(15_000L), result)
        assertEquals(0L, coordinator.generation)
        assertEquals(0, coordinator.gapTotal)
        assertNull(coordinator.activeGap)
    }

    @Test
    fun `15001ms 触发并记录完整证据`() {
        val published = mutableListOf<AsrGap>()
        val coordinator = coordinator(onGap = { published += it })

        val result = coordinator.observe(sample(1_000_000_000L, 16_001_000_000L))

        val gap = (result as GapResolution.Created).gap
        assertEquals(1L, gap.generation)
        assertEquals(15_001L, gap.lagMs)
        assertEquals(listOf(gap), published, "GAP 必须交给持久化回调")

        val segment = gap.segment
        assertEquals("$SESSION:gap:1", segment.id)
        assertEquals(SESSION, segment.sessionId)
        assertEquals(SegmentKind.GAP, segment.kind)
        assertEquals(emptyList<String>(), segment.sourceFinalIds)
        assertEquals(GapReason.ASR_OVERLOAD, segment.gapReason)
        assertNull(segment.foldDueAtUtcMs, "GAP 不参与折叠")
        assertEquals(FIXED_UTC_MS, segment.finalizedAtUtcMs)
        assertEquals(1_000_000_000L, segment.captureStartedMonoNs)
        assertEquals(16_001_000_000L, segment.captureEndedMonoNs)
        assertEquals(1_000L, segment.capturedStartMs)
        assertEquals(16_001L, segment.capturedEndMs)
        assertEquals(listOf("chunk-0", "chunk-1"), gap.chunkIds)
        assertEquals(model.modelId, gap.modelId)
        assertEquals(model.modelVersion, gap.modelVersion)
    }

    @Test
    fun `同一次过载重复采样只产生一个 GAP`() {
        var clears = 0
        val coordinator = coordinator(clearAsrBacklog = { clears++ })
        val first = sample(1_000_000_000L, 16_001_000_000L)

        val created = coordinator.observe(first) as GapResolution.Created
        val repeated = coordinator.observe(first)

        val again = repeated as GapResolution.AlreadyRecorded
        assertSame(created.gap, again.gap, "重复采样必须沿用同一个 GAP 对象")
        assertEquals(1L, coordinator.generation, "重复采样不得再次推进世代")
        assertEquals(1, coordinator.gapTotal)
        assertEquals(1, clears, "重复采样不得重复清空积压")

        // 积压仍在已标记区间内（哪怕时延继续增长）也属于同一次过载。
        val stillSame = coordinator.observe(sample(16_000_000_000L, 40_000_000_000L))
        assertTrue(stillSame is GapResolution.AlreadyRecorded)
        assertEquals(1L, coordinator.generation)
    }

    @Test
    fun `新过载另起 GAP 与新 ID`() {
        val coordinator = coordinator()
        val first = (coordinator.observe(sample(1_000_000_000L, 16_001_000_000L)) as GapResolution.Created).gap

        val second = coordinator.observe(
            sample(
                oldestUnprocessedMonoNs = 17_000_000_000L,
                newestCapturedMonoNs = 34_000_000_000L,
                skippedCapturedStartMs = 17_000L,
                skippedCapturedEndMs = 34_000L,
                chunkIds = listOf("chunk-2", "chunk-3"),
            ),
        ) as GapResolution.Created

        assertEquals(2L, second.gap.generation)
        assertEquals("$SESSION:gap:2", second.gap.segment.id)
        assertTrue(first.segment.id != second.gap.segment.id)
        assertEquals(listOf("chunk-2", "chunk-3"), second.gap.chunkIds)
        assertEquals(17_000L, second.gap.segment.capturedStartMs)
        assertEquals(34_000L, second.gap.segment.capturedEndMs)
        assertEquals(2, coordinator.gapTotal)
        assertSame(second.gap, coordinator.activeGap)
    }

    @Test
    fun `跳读推进世代并清空 ASR 积压而不触碰队列容量`() {
        val queue = BoundedPcmQueue(4)
        repeat(3) { index ->
            queue.offer(
                PcmFrame(
                    sessionId = SESSION,
                    frameIndex = index.toLong(),
                    monoNs = index * 10_000_000L,
                    capturedStartMs = index * 10L,
                    sampleRate = 16_000,
                    samples = ShortArray(160),
                ),
            )
        }
        val order = mutableListOf<String>()
        val coordinator = coordinator(
            onGap = { order += "gap" },
            clearAsrBacklog = {
                order += "clear"
                queue.clear()
            },
        )

        val gap = (coordinator.observe(sample(0L, 15_001_000_000L)) as GapResolution.Created).gap

        assertEquals(listOf("clear", "gap"), order, "必须先清空积压再交出 GAP")
        assertEquals(0, queue.depth())
        assertEquals(1L, coordinator.generation)
        assertEquals(gap.generation, coordinator.generation)
        // 队列容量与证据路径不受影响：队列仍可继续接收新帧。
        assertEquals(PcmOfferResult.ACCEPTED, queue.offer(
            PcmFrame(
                sessionId = SESSION,
                frameIndex = 9L,
                monoNs = 90_000_000L,
                capturedStartMs = 90L,
                sampleRate = 16_000,
                samples = ShortArray(160),
            ),
        ))
    }

    @Test
    fun `未超阈值时世代与 GAP 都保持不变`() {
        val coordinator = coordinator()
        coordinator.observe(sample(0L, 15_001_000_000L))

        val result = coordinator.observe(sample(16_001_000_000L, 31_001_000_000L))

        assertTrue(result is GapResolution.WithinBudget)
        assertEquals(1L, coordinator.generation)
        assertEquals(1, coordinator.gapTotal)
        assertNotNull(coordinator.activeGap)
    }

    @Test
    fun `样本必须自洽`() {
        assertThrows(IllegalArgumentException::class.java) {
            sample(oldestUnprocessedMonoNs = 2L, newestCapturedMonoNs = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sample(0L, 1L, chunkIds = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            sample(0L, 1L, chunkIds = listOf("chunk-0", "chunk-0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            sample(0L, 1L, skippedCapturedStartMs = 10L, skippedCapturedEndMs = 5L)
        }
    }

    private companion object {
        const val SESSION = "session-1"
        const val FIXED_UTC_MS = 1_700_000_000_000L
    }
}
