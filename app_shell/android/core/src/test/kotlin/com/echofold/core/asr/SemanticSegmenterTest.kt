package com.echofold.core.asr

import com.echofold.core.model.GapReason
import com.echofold.core.model.SegmentKind
import com.echofold.core.model.TranscriptSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 语义段归并：短段合并、来源顺序、foldDueAt 取最大、GAP 独立与折叠重算规则。 */
class SemanticSegmenterTest {
    private fun final(
        id: String,
        text: String,
        startMonoNs: Long,
        finalizedAtUtcMs: Long = 1_000L,
        capturedStartMs: Long = startMonoNs / 1_000_000L,
        seq: Long = startMonoNs / 1_000_000L,
        sessionId: String = SESSION,
    ): AsrEvent.Final = AsrEvent.Final(
        sessionId = sessionId,
        generation = 0L,
        seq = seq,
        finalId = id,
        text = text,
        finalizedAtUtcMs = finalizedAtUtcMs,
        captureStartedMonoNs = startMonoNs,
        captureEndedMonoNs = startMonoNs + FINAL_DURATION_NS,
        capturedStartMs = capturedStartMs,
        capturedEndMs = capturedStartMs + FINAL_DURATION_MS,
        chunkIds = listOf("chunk-0"),
    )

    private fun gap(
        id: String = "$SESSION:gap:1",
        startMonoNs: Long = 0L,
        capturedStartMs: Long = 0L,
        capturedEndMs: Long = 15_001L,
    ): TranscriptSegment = TranscriptSegment(
        id = id,
        sessionId = SESSION,
        kind = SegmentKind.GAP,
        sourceFinalIds = emptyList(),
        finalizedAtUtcMs = 2_000L,
        foldDueAtUtcMs = null,
        captureStartedMonoNs = startMonoNs,
        captureEndedMonoNs = startMonoNs + 15_000L * 1_000_000L,
        capturedStartMs = capturedStartMs,
        capturedEndMs = capturedEndMs,
        gapReason = GapReason.ASR_OVERLOAD,
    )

    private fun segmenter(
        minChars: Int = 5,
        maxChars: Int = 50,
        requireSentenceEnd: Boolean = true,
        maxGapMs: Long = 1_500L,
        foldDelayMs: Long = 45_000L,
    ) = SemanticSegmenter(
        sessionId = SESSION,
        rules = SegmentationRules(
            minChars = minChars,
            maxChars = maxChars,
            requireSentenceEnd = requireSentenceEnd,
            maxGapMs = maxGapMs,
            foldDelayMs = foldDelayMs,
        ),
    )

    @Test
    fun `短 FINAL 不单独成段而是与后继合并`() {
        val segmenter = segmenter(minChars = 5)

        assertTrue(segmenter.acceptFinal(final("f1", "你好", 0L)).isEmpty(), "2 字短段不得单独成段")
        assertEquals(listOf("f1"), segmenter.pendingFinalIds())

        val emitted = segmenter.acceptFinal(final("f2", "世界。", FINAL_DURATION_NS))

        assertEquals(1, emitted.size)
        assertEquals(listOf("f1", "f2"), emitted.single().sourceFinalIds)
        assertEquals(0, segmenter.pendingCount())
    }

    @Test
    fun `必须句末标点满足后才落定`() {
        val segmenter = segmenter(minChars = 2, requireSentenceEnd = true)

        assertTrue(segmenter.acceptFinal(final("f1", "你好世界", 0L)).isEmpty())

        val emitted = segmenter.acceptFinal(final("f2", "。", FINAL_DURATION_NS))

        assertEquals(1, emitted.size)
        assertEquals(listOf("f1", "f2"), emitted.single().sourceFinalIds)
    }

    @Test
    fun `sourceFinalIds 按采集顺序且不重复`() {
        val segmenter = segmenter(minChars = 10, maxChars = 100, requireSentenceEnd = false)

        assertTrue(segmenter.acceptFinal(final("f9", "aaaa", 0L)).isEmpty())
        assertTrue(segmenter.acceptFinal(final("f3", "bbbb", FINAL_DURATION_NS)).isEmpty())
        val emitted = segmenter.acceptFinal(final("f7", "cccc", FINAL_DURATION_NS * 2))

        assertEquals(1, emitted.size)
        assertEquals(listOf("f9", "f3", "f7"), emitted.single().sourceFinalIds, "必须保持到达顺序，不得排序")
        assertEquals(emitted.single().sourceFinalIds.distinct(), emitted.single().sourceFinalIds)
    }

    @Test
    fun `重复投递的 FINAL 不重复计入`() {
        val segmenter = segmenter(minChars = 100, maxChars = 200, requireSentenceEnd = false)
        val event = final("f1", "aaaa", 0L)

        assertTrue(segmenter.acceptFinal(event).isEmpty())
        assertTrue(segmenter.acceptFinal(event).isEmpty(), "同一 FINAL 不得重复进入同一段")

        assertEquals(listOf("f1"), segmenter.pendingFinalIds())
        assertEquals(1, segmenter.pendingCount())
    }

    @Test
    fun `foldDueAt 取源 FINAL 的最大 finalizedAt 加延迟`() {
        val segmenter = segmenter(minChars = 10, maxChars = 100, requireSentenceEnd = false, foldDelayMs = 45_000L)

        assertTrue(segmenter.acceptFinal(final("f1", "abcd", 0L, finalizedAtUtcMs = 1_000L)).isEmpty())
        assertTrue(segmenter.acceptFinal(final("f2", "efgh", FINAL_DURATION_NS, finalizedAtUtcMs = 3_000L)).isEmpty())
        val emitted = segmenter.acceptFinal(final("f3", "ijkl", FINAL_DURATION_NS * 2, finalizedAtUtcMs = 2_000L))

        val segment = emitted.single()
        assertEquals(3_000L, segment.finalizedAtUtcMs)
        assertEquals(3_000L + 45_000L, segment.foldDueAtUtcMs)
        assertEquals(listOf("f1", "f2", "f3"), segment.sourceFinalIds)
    }

    @Test
    fun `GAP 切断合并且自身无 foldDueAt`() {
        val segmenter = segmenter(minChars = 5, maxChars = 100, requireSentenceEnd = false)
        segmenter.acceptFinal(final("f1", "abcd", 0L))

        val emitted = segmenter.acceptGap(gap(startMonoNs = FINAL_DURATION_NS))

        assertEquals(2, emitted.size, "GAP 之前必须落定文本段")
        val text = emitted[0]
        assertEquals(SegmentKind.TEXT, text.kind)
        assertEquals(listOf("f1"), text.sourceFinalIds)
        assertEquals(45_000L + 1_000L, text.foldDueAtUtcMs)
        val gapSegment = emitted[1]
        assertEquals(SegmentKind.GAP, gapSegment.kind)
        assertNull(gapSegment.foldDueAtUtcMs)
        assertEquals(emptyList<String>(), gapSegment.sourceFinalIds)

        // GAP 之后的 FINAL 另起一段，不与 GAP 之前的文字混合。
        segmenter.acceptFinal(final("f2", "efgh", FINAL_DURATION_NS + 15_000L * 1_000_000L))
        assertEquals(listOf("f2"), segmenter.flush().single().sourceFinalIds)
    }

    @Test
    fun `间隔超过阈值时切断合并`() {
        val segmenter = segmenter(minChars = 5, requireSentenceEnd = false, maxGapMs = 1_000L)
        segmenter.acceptFinal(final("f1", "abc", 0L))

        val emitted = segmenter.acceptFinal(final("f2", "defg", FINAL_DURATION_NS + 2_000L * 1_000_000L))

        assertEquals(1, emitted.size, "超长间隔必须切断，不得跨间隔合并")
        assertEquals(listOf("f1"), emitted.single().sourceFinalIds)
        assertEquals(listOf("f2"), segmenter.pendingFinalIds())
    }

    @Test
    fun `字数上限切断合并`() {
        val segmenter = segmenter(minChars = 2, maxChars = 10, requireSentenceEnd = true)
        assertTrue(segmenter.acceptFinal(final("f1", "aaaa", 0L)).isEmpty())
        assertTrue(segmenter.acceptFinal(final("f2", "bbbb", FINAL_DURATION_NS)).isEmpty())

        val emitted = segmenter.acceptFinal(final("f3", "cccc", FINAL_DURATION_NS * 2))

        assertEquals(1, emitted.size)
        assertEquals(listOf("f1", "f2"), emitted.single().sourceFinalIds)
        assertEquals(listOf("f3"), segmenter.pendingFinalIds())
    }

    @Test
    fun `单个超长 FINAL 到达字数上限立即落定`() {
        val segmenter = segmenter(minChars = 2, maxChars = 5, requireSentenceEnd = true)

        val emitted = segmenter.acceptFinal(final("f1", "abcdef", 0L))

        assertEquals(1, emitted.size, "字数上限是硬边界，到达即落定")
        assertEquals(listOf("f1"), emitted.single().sourceFinalIds, "单个 FINAL 不得被截断")
        assertEquals(0, segmenter.pendingCount())
    }

    @Test
    fun `flush 落定未完整组且不丢文字`() {
        val segmenter = segmenter(minChars = 10, maxChars = 100, requireSentenceEnd = true)
        segmenter.acceptFinal(final("f1", "短句", 0L))

        val emitted = segmenter.flush()

        assertEquals(1, emitted.size)
        assertEquals(listOf("f1"), emitted.single().sourceFinalIds)
        assertEquals(1_000L + 45_000L, emitted.single().foldDueAtUtcMs)
        assertTrue(segmenter.flush().isEmpty())
    }

    @Test
    fun `其他会话与非法 GAP 被拒绝`() {
        val segmenter = segmenter()

        assertThrows(IllegalArgumentException::class.java) {
            segmenter.acceptFinal(final("f1", "你好。", 0L, sessionId = "session-2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            segmenter.acceptGap(gap().copy(sessionId = "session-2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            segmenter.acceptGap(
                gap().copy(
                    kind = SegmentKind.TEXT,
                    sourceFinalIds = listOf("f1"),
                    gapReason = null,
                    foldDueAtUtcMs = 1L,
                ),
            )
        }
    }

    @Test
    fun `配置变化只重算未到期段`() {
        val segmenter = segmenter()
        val notYetDue = textSegment("t1", finalizedAtUtcMs = 5_000L, foldDueAtUtcMs = 50_000L)
        val alreadyDue = textSegment("t2", finalizedAtUtcMs = 1_000L, foldDueAtUtcMs = 2_000L)
        val gapSegment = gap()

        val recalculated = segmenter.recalculateFoldDue(
            segments = listOf(notYetDue, alreadyDue, gapSegment),
            delayMs = 20_000L,
            nowUtcMs = 10_000L,
        )

        assertEquals(3, recalculated.size)
        assertEquals(5_000L + 20_000L, recalculated[0].foldDueAtUtcMs, "未到期段按新配置重算")
        assertEquals(2_000L, recalculated[1].foldDueAtUtcMs, "已到期段保留原到期事实")
        assertSame(alreadyDue, recalculated[1])
        assertSame(gapSegment, recalculated[2], "GAP 无 foldDueAt，不参与重算")
    }

    @Test
    fun `更短的折叠延迟可以让未到期段立即到期`() {
        val segmenter = segmenter()
        val notYetDue = textSegment("t1", finalizedAtUtcMs = 5_000L, foldDueAtUtcMs = 50_000L)

        val recalculated = segmenter.recalculateFoldDue(listOf(notYetDue), delayMs = 1_000L, nowUtcMs = 10_000L)

        assertEquals(6_000L, recalculated.single().foldDueAtUtcMs)
        assertTrue(recalculated.single().foldDueAtUtcMs!! <= 10_000L)
    }

    @Test
    fun `规则与参数必须合法`() {
        assertThrows(IllegalArgumentException::class.java) { SegmentationRules(minChars = 0) }
        assertThrows(IllegalArgumentException::class.java) { SegmentationRules(minChars = 10, maxChars = 9) }
        assertThrows(IllegalArgumentException::class.java) { SegmentationRules(foldDelayMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) {
            segmenter().recalculateFoldDue(emptyList(), delayMs = -1L, nowUtcMs = 0L)
        }
    }

    private fun textSegment(id: String, finalizedAtUtcMs: Long, foldDueAtUtcMs: Long): TranscriptSegment =
        TranscriptSegment(
            id = id,
            sessionId = SESSION,
            kind = SegmentKind.TEXT,
            sourceFinalIds = listOf("$id-final"),
            finalizedAtUtcMs = finalizedAtUtcMs,
            foldDueAtUtcMs = foldDueAtUtcMs,
            captureStartedMonoNs = 0L,
            captureEndedMonoNs = 1_000_000L,
            capturedStartMs = 0L,
            capturedEndMs = 1L,
            gapReason = null,
        )

    private companion object {
        const val SESSION = "session-1"
        const val FINAL_DURATION_MS = 500L
        const val FINAL_DURATION_NS = FINAL_DURATION_MS * 1_000_000L
    }
}
