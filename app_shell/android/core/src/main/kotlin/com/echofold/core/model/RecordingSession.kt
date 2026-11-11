package com.echofold.core.model

/**
 * 会话级 UTC 墙钟与设备单调时钟的映射锚点。
 *
 * 重启后必须建立新锚点：旧的 `monoNs` 只能与原锚点一起解释，不得与其他设备或其他锚点的
 * 裸数值比较。`utcMs` 只用于持久化与展示，不参与 PCM 寻址或 15 秒排队判定。
 */
data class ClockAnchor(
    val sessionId: String,
    val utcMs: Long,
    val monoNs: Long,
    val quality: ClockQuality,
) {
    init {
        requireNotBlank(sessionId, "sessionId")
        requireNonNegative(utcMs, "utcMs")
        requireNonNegative(monoNs, "monoNs")
    }
}

/**
 * 一次录音会话。
 *
 * `modelId/modelVersion` 在会话开始时固定，实时识别与该会话所有重识别任务都不得改用其他版本；
 * 无可用模型时二者同时为空，此时只允许录音，不得伪装实时转写。
 */
data class RecordingSession(
    val id: String,
    val status: SessionStatus,
    val dispatchState: DispatchState,
    val createdAtUtcMs: Long,
    val modelId: String?,
    val modelVersion: String?,
    val installationId: String?,
    val asrConfigHash: String?,
    val activeChunkSeq: Int,
    val clockAnchor: ClockAnchor,
) {
    init {
        requireNotBlank(id, "id")
        requireNonNegative(createdAtUtcMs, "createdAtUtcMs")
        require(activeChunkSeq >= 0) { "activeChunkSeq 不能为负数：$activeChunkSeq" }
        requireNotBlankIfPresent(modelId, "modelId")
        requireNotBlankIfPresent(modelVersion, "modelVersion")
        requireNotBlankIfPresent(installationId, "installationId")
        requireNotBlankIfPresent(asrConfigHash, "asrConfigHash")
        require((modelId == null) == (modelVersion == null)) {
            "modelId 与 modelVersion 必须同时存在或同时为空：modelId=$modelId, modelVersion=$modelVersion"
        }
        require(clockAnchor.sessionId == id) {
            "clockAnchor.sessionId(${clockAnchor.sessionId}) 必须等于会话 id($id)"
        }
    }
}
