package com.echofold.core.asr

import com.echofold.core.model.SegmentKind
import com.echofold.core.model.TranscriptSegment

/**
 * 语义段切分规则。
 *
 * 数值都是本切片可配置的原型候选值，不是冻结后的最优参数：
 * - [minChars]：一个 TEXT 段成立所需的最小字数；低于它的独立 FINAL 不单独成段，继续与后继合并；
 * - [maxChars]：合并时允许的最大字数；超过就先落定当前组（最小字数优先，因此极长单个 FINAL 不被截断）；
 * - [requireSentenceEnd]：段尾必须是句末标点（`。！？!?.…`，允许尾随右引号/右括号）；
 * - [maxGapMs]：相邻 FINAL 之间的最大单调时钟间隔，超过则认为不是一个连续语义段；
 * - [foldDelayMs]：自动折叠延迟，`foldDueAt = max(sourceFinal.finalizedAtUtcMs) + foldDelayMs`。
 */
data class SegmentationRules(
    val minChars: Int = 12,
    val maxChars: Int = 120,
    val requireSentenceEnd: Boolean = true,
    val maxGapMs: Long = 1_500L,
    val foldDelayMs: Long = 45_000L,
) {
    init {
        require(minChars > 0) { "minChars 必须为正数：$minChars" }
        require(maxChars >= minChars) { "maxChars($maxChars) 不能小于 minChars($minChars)" }
        require(maxGapMs >= 0L) { "maxGapMs 不能为负数：$maxGapMs" }
        require(foldDelayMs >= 0L) { "foldDelayMs 不能为负数：$foldDelayMs" }
    }
}

/**
 * 语义段归并器：把相邻且连续的 FINAL 合并为 TEXT 段，并让 GAP 保持独立证据身份。
 *
 * 规则（对齐设计决策 6 与任务 3.8）：
 * - 相邻 FINAL 只有在单调时钟间隔不超过 [SegmentationRules.maxGapMs] 时才合并；GAP、过长间隔、
 *   超长字数都会切断合并；
 * - `sourceFinalIds` 严格按 FINAL 到达（即采集）顺序记录，只去重、绝不排序；
 * - 短 FINAL 不单独成段：[acceptFinal] 在组未满足完整性时返回空列表，等待后继 FINAL；
 * - 组一旦完整（字数达标且以句末标点结尾）立即落定，不等待折叠延迟，从而立刻获得预处理资格；
 * - `foldDueAt = max(sourceFinal.finalizedAtUtcMs) + foldDelayMs`，多源段不能任取一个 FINAL 的时间；
 * - GAP 不参与合并、`foldDueAt` 为 `null`，前后文本在 GAP 处断开。
 *
 * 显式的 [flush] 用于暂停/停止：语义上已经没有“后继 FINAL”可等，此时即使组未完整也必须落定，
 * 以免已识别文字丢失。所有方法线程安全。
 */
class SemanticSegmenter(
    private val sessionId: String,
    val rules: SegmentationRules = SegmentationRules(),
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
    }

    /** 当前未落定的 FINAL，按采集顺序。 */
    private val pending = ArrayList<AsrEvent.Final>()

    /** 当前未落定组的拼接文字，避免每次判断完整性时重新拼接。 */
    private val pendingText = StringBuilder()

    /**
     * 接受一个 FINAL。
     *
     * @return 因本次输入而落定的 TEXT 段（0 或 1 个；切断旧组时可能先落定旧组再落定新组，故最多 2 个）。
     */
    @Synchronized
    fun acceptFinal(event: AsrEvent.Final): List<TranscriptSegment> {
        require(event.sessionId == sessionId) {
            "FINAL 属于会话 ${event.sessionId}，不能进入会话 $sessionId 的分段器"
        }
        if (pending.any { it.finalId == event.finalId }) {
            // 重复投递（事件门之外的第二道保险）：同一 FINAL 不得在同一段内出现两次。
            return emptyList()
        }

        val emitted = ArrayList<TranscriptSegment>(2)
        if (pending.isNotEmpty() && breaksRun(pending.last(), event)) {
            emitted += buildSegment()
        }
        append(event)
        if (isComplete()) {
            emitted += buildSegment()
        }
        return emitted
    }

    /**
     * 接受一个 GAP 段。
     *
     * GAP 不参与合并：先落定挂起组（保持阅读顺序），再把 GAP 原样交给调用方；
     * GAP 自身的 `foldDueAt` 恒为 `null`。
     *
     * @throws IllegalArgumentException 传入的不是 GAP 段或不属于本会话
     */
    @Synchronized
    fun acceptGap(gap: TranscriptSegment): List<TranscriptSegment> {
        require(gap.sessionId == sessionId) {
            "GAP 属于会话 ${gap.sessionId}，不能进入会话 $sessionId 的分段器"
        }
        require(gap.kind == SegmentKind.GAP) { "acceptGap 只接受 GAP 段，实际为 ${gap.kind}" }
        val emitted = ArrayList<TranscriptSegment>(2)
        if (pending.isNotEmpty()) {
            emitted += buildSegment()
        }
        emitted += gap
        return emitted
    }

    /** 暂停/停止时冲刷：把挂起组按现状落定为 TEXT 段（未完整也落定，不丢已识别文字）。 */
    @Synchronized
    fun flush(): List<TranscriptSegment> =
        if (pending.isEmpty()) emptyList() else listOf(buildSegment())

    /** 当前挂起的 FINAL 数量。 */
    @Synchronized
    fun pendingCount(): Int = pending.size

    /** 当前挂起的 FINAL ID，按采集顺序；供诊断与测试。 */
    @Synchronized
    fun pendingFinalIds(): List<String> = pending.map { it.finalId }

    /**
     * 折叠配置变化后重算到期时间。
     *
     * 只重算**在 [nowUtcMs] 时刻尚未到期**的段（`foldDueAtUtcMs > nowUtcMs`）：
     * 新值 = `finalizedAtUtcMs + delayMs`，因此不会为已过去的稳定期额外补等一个周期；
     * 已到期段保留原到期事实（不重置、不延后）；GAP（无 `foldDueAt`）与 `foldDueAt` 为空的段不变。
     *
     * @return 与入参同序的新列表；未改动的元素复用原对象
     */
    fun recalculateFoldDue(
        segments: List<TranscriptSegment>,
        delayMs: Long,
        nowUtcMs: Long,
    ): List<TranscriptSegment> {
        require(delayMs >= 0L) { "delayMs 不能为负数：$delayMs" }
        require(nowUtcMs >= 0L) { "nowUtcMs 不能为负数：$nowUtcMs" }
        val result = ArrayList<TranscriptSegment>(segments.size)
        for (segment in segments) {
            val due = segment.foldDueAtUtcMs
            val finalizedAt = segment.finalizedAtUtcMs
            if (segment.kind != SegmentKind.TEXT || due == null || finalizedAt == null || due <= nowUtcMs) {
                result += segment
                continue
            }
            result += segment.copy(foldDueAtUtcMs = finalizedAt + delayMs)
        }
        return result
    }

    /** 判断新 FINAL 是否切断当前组：间隔过长，或在当前组已达标时加上它会超过字数上限。 */
    private fun breaksRun(previous: AsrEvent.Final, next: AsrEvent.Final): Boolean {
        val gapNs = next.captureStartedMonoNs - previous.captureEndedMonoNs
        if (gapNs > rules.maxGapMs * NANOS_PER_MILLI) {
            return true
        }
        val added = joinedLength(pendingText, next.text)
        return pendingText.length + added > rules.maxChars && pendingText.length >= rules.minChars
    }

    private fun append(event: AsrEvent.Final) {
        if (pendingText.isNotEmpty() && needSpace(pendingText, event.text)) {
            pendingText.append(' ')
        }
        pendingText.append(event.text)
        pending += event
    }

    private fun isComplete(): Boolean {
        // 字数上限是硬边界：到达即落定，避免单段无限增长。
        if (pendingText.length >= rules.maxChars) return true
        if (pendingText.length < rules.minChars) return false
        return !rules.requireSentenceEnd || endsWithSentencePunctuation(pendingText)
    }

    /** 落定当前组并清空挂起状态；调用前必须保证 [pending] 非空。 */
    private fun buildSegment(): TranscriptSegment {
        check(pending.isNotEmpty()) { "没有挂起的 FINAL 可以落定" }
        val first = pending.first()
        val last = pending.last()
        var maxFinalizedAt = 0L
        for (event in pending) {
            if (event.finalizedAtUtcMs > maxFinalizedAt) {
                maxFinalizedAt = event.finalizedAtUtcMs
            }
        }
        val segment = TranscriptSegment(
            // 以组内首个 FINAL 派生稳定 ID：同样的来源集合反复推导得到同一个段 ID。
            id = "$sessionId:seg:${first.finalId}",
            sessionId = sessionId,
            kind = SegmentKind.TEXT,
            sourceFinalIds = pending.map { it.finalId },
            finalizedAtUtcMs = maxFinalizedAt,
            foldDueAtUtcMs = maxFinalizedAt + rules.foldDelayMs,
            captureStartedMonoNs = first.captureStartedMonoNs,
            captureEndedMonoNs = last.captureEndedMonoNs,
            capturedStartMs = first.capturedStartMs,
            capturedEndMs = last.capturedEndMs,
            gapReason = null,
        )
        pending.clear()
        pendingText.setLength(0)
        return segment
    }

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L

        /** 句末标点；中英文混排都可能出现。 */
        val SENTENCE_END_CHARS = charArrayOf('。', '！', '？', '!', '?', '.', '…')

        /** 句末标点后允许尾随的右引号/右括号。 */
        val TRAILING_CLOSERS = charArrayOf('”', '’', '」', '』', '）', ')', '】', '》')

        fun joinedLength(current: CharSequence, next: String): Int =
            next.length + if (current.isNotEmpty() && needSpace(current, next)) 1 else 0

        /** 中英混排时，两个 ASCII 文字字符之间补一个空格，避免粘成 "hello world" 之类的错误。 */
        fun needSpace(current: CharSequence, next: String): Boolean {
            if (next.isEmpty()) return false
            val last = current.last()
            val first = next[0]
            return isAsciiWordChar(last) && isAsciiWordChar(first)
        }

        fun isAsciiWordChar(c: Char): Boolean = c.code < 128 && c.isLetterOrDigit()

        fun endsWithSentencePunctuation(text: CharSequence): Boolean {
            var end = text.length - 1
            while (end >= 0 && text[end] in TRAILING_CLOSERS) {
                end--
            }
            return end >= 0 && text[end] in SENTENCE_END_CHARS
        }
    }
}
