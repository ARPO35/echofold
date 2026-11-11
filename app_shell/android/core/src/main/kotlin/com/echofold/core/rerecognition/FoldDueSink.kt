package com.echofold.core.rerecognition

/**
 * 重识别成功后重建折叠到期时间的回调。
 *
 * 只有“来源 FINAL 集合被重新建立”的成功重识别才触发它：GAP 段（请求不带来源 FINAL）的跳过区间
 * 被识别出新的 FINAL 后，宿主必须据此重建 `sourceFinalIds[]` 与
 * `foldDueAt = max(finalizedAt) + 当前配置折叠延迟`。普通文本段的来源 FINAL 未变化，
 * 因此不得触发回调，避免重置已经到期或已折叠段的计时。
 *
 * 实现不得抛异常；回调只重建派生计时，不回滚已提交的版本。
 */
fun interface FoldDueSink {
    /**
     * @param segmentId 发生重识别的段。
     * @param sourceFinalIds 本次识别新产生的 FINAL ID，按采集顺序排列；即该段新的有序来源集合。
     * @param latestFinalizedAtUtcMs 新 FINAL 中最大的 UTC `finalizedAt`，作为折叠计时起点。
     */
    fun onNewFinal(segmentId: String, sourceFinalIds: List<String>, latestFinalizedAtUtcMs: Long)
}
