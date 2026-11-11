package com.echofold.core.model

/**
 * 转写段。
 *
 * `sourceFinalIds` 按阅读/音频顺序保存，数组顺序有语义：不得排序、去重或按提交先后重排。
 * TEXT 段必须至少引用一个 FINAL 且互不重复；GAP 段不引用 FINAL、原因为 [GapReason.ASR_OVERLOAD]、
 * 无折叠到期时间，展示文本恒为字面量 `"..."`（正文不入库）。
 */
data class TranscriptSegment(
    val id: String,
    val sessionId: String,
    val kind: SegmentKind,
    val sourceFinalIds: List<String>,
    val finalizedAtUtcMs: Long?,
    val foldDueAtUtcMs: Long?,
    val captureStartedMonoNs: Long?,
    val captureEndedMonoNs: Long?,
    val capturedStartMs: Long,
    val capturedEndMs: Long,
    val gapReason: GapReason?,
) {
    init {
        requireNotBlank(id, "id")
        requireNotBlank(sessionId, "sessionId")
        requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
        finalizedAtUtcMs?.let { requireNonNegative(it, "finalizedAtUtcMs") }
        foldDueAtUtcMs?.let { requireNonNegative(it, "foldDueAtUtcMs") }
        captureStartedMonoNs?.let { requireNonNegative(it, "captureStartedMonoNs") }
        captureEndedMonoNs?.let { requireNonNegative(it, "captureEndedMonoNs") }
        if (captureStartedMonoNs != null && captureEndedMonoNs != null) {
            require(captureEndedMonoNs >= captureStartedMonoNs) {
                "captureEndedMonoNs($captureEndedMonoNs) 不能早于 captureStartedMonoNs($captureStartedMonoNs)"
            }
        }

        when (kind) {
            SegmentKind.TEXT -> {
                require(sourceFinalIds.isNotEmpty()) { "TEXT 段的 sourceFinalIds 不能为空" }
                val seen = HashSet<String>(sourceFinalIds.size)
                for (finalId in sourceFinalIds) {
                    requireNotBlank(finalId, "sourceFinalIds 元素")
                    require(seen.add(finalId)) { "TEXT 段的 sourceFinalIds 不能包含重复值：$finalId" }
                }
                require(gapReason == null) { "TEXT 段不得带 gapReason" }
            }

            SegmentKind.GAP -> {
                require(sourceFinalIds.isEmpty()) { "GAP 段的 sourceFinalIds 必须为空" }
                require(gapReason == GapReason.ASR_OVERLOAD) {
                    "GAP 段的 gapReason 必须为 ASR_OVERLOAD：$gapReason"
                }
                require(foldDueAtUtcMs == null) { "GAP 段不得带 foldDueAtUtcMs：跳过区间不参与自动折叠" }
            }
        }
    }

    companion object {
        /** GAP 在阅读流中的字面展示文本，不作为正文入库。 */
        const val GAP_DISPLAY_TEXT: String = "..."
    }
}
