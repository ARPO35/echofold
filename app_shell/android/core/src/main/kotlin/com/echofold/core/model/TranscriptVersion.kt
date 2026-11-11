package com.echofold.core.model

/**
 * 不可变转写版本。
 *
 * 同一段的每次重识别、AI 校订或用户编辑都新增一个版本；已有版本（尤其 L0 ASR 证据）永不被覆盖。
 * 只有通过来源与模型校验的新版本才能成为该段的默认展示。
 */
data class TranscriptVersion(
    val id: String,
    val segmentId: String,
    val kind: VersionKind,
    val text: String,
    val parentVersionId: String?,
    val modelId: String?,
    val modelVersion: String?,
    val finalizedAtUtcMs: Long,
    val createdAtUtcMs: Long,
    val captureStartMs: Long,
    val captureEndMs: Long,
) {
    init {
        requireNotBlank(id, "id")
        requireNotBlank(segmentId, "segmentId")
        requireNotBlankIfPresent(parentVersionId, "parentVersionId")
        requireNotBlankIfPresent(modelId, "modelId")
        requireNotBlankIfPresent(modelVersion, "modelVersion")
        requireNonNegative(finalizedAtUtcMs, "finalizedAtUtcMs")
        requireNonNegative(createdAtUtcMs, "createdAtUtcMs")
        requireRange(captureStartMs, captureEndMs, "captureStartMs", "captureEndMs")
        require(id != parentVersionId) { "parentVersionId 不能指向自身：$id" }
    }
}
