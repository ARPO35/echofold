package com.echofold.core.rerecognition

import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.model.requireNonNegative
import com.echofold.core.model.requireNotBlank
import com.echofold.core.model.requireRange

/**
 * 一次手动本地重识别请求（不可变输入快照）。
 *
 * 请求锁定该段的全部输入边界：段与会话、按采集顺序排列的来源 FINAL、有效音频区间、
 * 按采集顺序排列的音频块引用、会话固定模型与配置哈希、跳读世代。构造后不得重排、合并或裁剪：
 * [sourceFinalIds] 的顺序就是证据顺序，[chunkIds] 的顺序就是音频采集顺序。
 *
 * [sourceFinalIds] 允许为空：GAP 段不引用 FINAL（见 `TranscriptSegment` 不变量），
 * 它的重识别输入是 [capturedStartMs]/[capturedEndMs] 与 [chunkIds] 描述的跳过区间，
 * 此时来源集合是该 GAP 自身的不可变 L0 证据版本。
 *
 * 幂等键由 [RerecognitionKey.of] 派生；[generation] 不参与键，
 * 只用于完成时判断结果是否已落后于该段当前世代。
 */
data class RerecognitionRequest(
    val segmentId: String,
    val sessionId: String,
    val sourceFinalIds: List<String>,
    val capturedStartMs: Long,
    val capturedEndMs: Long,
    val chunkIds: List<String>,
    val model: ModelRef,
    val asrConfigHash: String,
    val generation: Long,
) {
    init {
        requireNotBlank(segmentId, "segmentId")
        requireNotBlank(sessionId, "sessionId")
        requireNotBlank(asrConfigHash, "asrConfigHash")
        requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
        requireNonNegative(generation, "generation")
        requireOrderedDistinct(sourceFinalIds, "sourceFinalIds", allowEmpty = true)
        requireOrderedDistinct(chunkIds, "chunkIds", allowEmpty = false)
    }
}

/**
 * 校验有序列表：元素非空白、互不重复，且不改变调用方顺序。
 *
 * 重复或空白元素会让来源集合与音频块引用失去可比对的身份，必须在构造期拒绝；
 * [allowEmpty] 只用于区分“GAP 段无来源 FINAL”与“文本段必须有音频块”两种合法输入。
 */
private fun requireOrderedDistinct(values: List<String>, field: String, allowEmpty: Boolean) {
    if (values.isEmpty()) {
        require(allowEmpty) { "$field 不能为空" }
        return
    }
    val seen = HashSet<String>(values.size)
    for (value in values) {
        require(value.isNotBlank()) { "$field 元素不能为空白字符串" }
        require(seen.add(value)) { "$field 不能包含重复值：$value" }
    }
}
