package com.echofold.core.rerecognition

import com.echofold.core.model.requireNonNegative
import com.echofold.core.model.requireNotBlank
import com.echofold.core.model.requireRange

/**
 * 离线重识别产出的单个 FINAL。
 *
 * 这是引擎事件在重识别边界上的最小视图：稳定 FINAL ID、文字、有效音频区间、UTC 完成时间与模型身份。
 * 构造时校验区间与身份，空白文字不得作为证据；是否落在请求区间内、是否与会话固定模型一致，
 * 由 [RerecognitionCoordinator] 在提交前统一校验。
 */
data class RecognizedSpan(
    val finalId: String,
    val text: String,
    val capturedStartMs: Long,
    val capturedEndMs: Long,
    val finalizedAtUtcMs: Long,
    val modelId: String,
    val modelVersion: String,
) {
    init {
        requireNotBlank(finalId, "finalId")
        requireNotBlank(text, "text")
        requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
        requireNonNegative(finalizedAtUtcMs, "finalizedAtUtcMs")
        requireNotBlank(modelId, "modelId")
        requireNotBlank(modelVersion, "modelVersion")
    }
}
