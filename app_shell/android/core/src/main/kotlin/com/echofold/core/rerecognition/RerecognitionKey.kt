package com.echofold.core.rerecognition

import com.echofold.core.model.CanonicalJson
import com.echofold.core.model.SOURCE_FINAL_IDS_SCHEMA
import com.echofold.core.model.requireNotBlank
import com.echofold.core.model.requireRange
import com.echofold.core.model.sourceFinalIdsHash

/**
 * 重识别任务的稳定幂等键。
 *
 * 键由 `segmentId + 音频区间 + modelId/modelVersion + sourceFinalIdsHash` 组成：
 * 同一输入必然得到同一个键，因此重复请求只执行一次；任何一段文字、区间或模型身份变化都会产生新键，
 * 旧键的结果只能作为迟到结果被拒绝。[generation] 与 `chunkIds` 不参与键——前者只影响迟到判定，
 * 后者是同一区间的采集副本视图，不改变识别输入语义。
 *
 * [stableId] 是键的规范 JSON 的 SHA-256（64 位小写十六进制），可作为任务 ID、版本 ID
 * 与崩溃恢复后的安全重试标识。
 */
data class RerecognitionKey(
    val segmentId: String,
    val capturedStartMs: Long,
    val capturedEndMs: Long,
    val modelId: String,
    val modelVersion: String,
    val sourceFinalIdsHash: String,
) {
    init {
        requireNotBlank(segmentId, "segmentId")
        requireNotBlank(modelId, "modelId")
        requireNotBlank(modelVersion, "modelVersion")
        requireNotBlank(sourceFinalIdsHash, "sourceFinalIdsHash")
        requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
    }

    /**
     * 规范 JSON：键顺序固定、无空白、字符串按 JSON 规则转义、整数用十进制原文。
     *
     * 与 Phase 1 一样手写编码，避免通用 JSON 库改变键顺序或数字格式后让同一输入得到不同哈希。
     */
    private val canonicalJson: String = buildString {
        append("{\"schema\":")
        append(CanonicalJson.quote(KEY_SCHEMA))
        append(",\"segmentId\":")
        append(CanonicalJson.quote(segmentId))
        append(",\"capturedStartMs\":")
        append(capturedStartMs)
        append(",\"capturedEndMs\":")
        append(capturedEndMs)
        append(",\"modelId\":")
        append(CanonicalJson.quote(modelId))
        append(",\"modelVersion\":")
        append(CanonicalJson.quote(modelVersion))
        append(",\"sourceFinalIdsHash\":")
        append(CanonicalJson.quote(sourceFinalIdsHash))
        append('}')
    }

    /** [canonicalJson] 的 SHA-256，64 位小写十六进制。 */
    val stableId: String = CanonicalJson.sha256Hex(canonicalJson)

    companion object {
        /** 幂等键规范 JSON 的模式版本，参与 [stableId] 计算。 */
        const val KEY_SCHEMA: String = "echofold.rerecognition-key.v1"

        /**
         * 空 `sourceFinalIds`（GAP 段）的稳定哈希。
         *
         * Phase 1 的 [sourceFinalIdsHash] 拒绝空列表，但 GAP 段按不变量不引用 FINAL，
         * 因此这里沿用同一规范形状（`echofold.source-final-ids.v1` 的空数组文档）计算哈希，
         * 保证 GAP 键同样可复现，且不会与任何非空来源列表的哈希相撞。
         */
        val EMPTY_SOURCE_FINAL_IDS_HASH: String = CanonicalJson.sha256Hex(
            "{\"schema\":" + CanonicalJson.quote(SOURCE_FINAL_IDS_SCHEMA) + ",\"finalIds\":[]}",
        )

        /** 由请求派生幂等键；[sourceFinalIds] 为空时使用 [EMPTY_SOURCE_FINAL_IDS_HASH]。 */
        fun of(request: RerecognitionRequest): RerecognitionKey = RerecognitionKey(
            segmentId = request.segmentId,
            capturedStartMs = request.capturedStartMs,
            capturedEndMs = request.capturedEndMs,
            modelId = request.model.modelId,
            modelVersion = request.model.modelVersion,
            sourceFinalIdsHash = if (request.sourceFinalIds.isEmpty()) {
                EMPTY_SOURCE_FINAL_IDS_HASH
            } else {
                sourceFinalIdsHash(request.sourceFinalIds)
            },
        )
    }
}
