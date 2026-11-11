package com.echofold.core.model

/**
 * 一个 AAC/M4A 音频块。
 *
 * 时间域：`createdAtUtcMs/sealedAtUtcMs` 是 UTC 墙钟，`captureStartedMonoNs/captureEndedMonoNs`
 * 是设备单调时钟，`capturedStartMs/capturedEndMs` 是该块在会话有效音频时间轴上的偏移（不含暂停间隙）。
 * `(sessionId, chunkSeq)` 与临时/最终路径唯一，序号永不复用。
 */
data class AudioChunk(
    val id: String,
    val sessionId: String,
    val chunkSeq: Int,
    val state: ChunkState,
    val tempPath: String,
    val finalPath: String?,
    val createdAtUtcMs: Long,
    val sealedAtUtcMs: Long?,
    val captureStartedMonoNs: Long?,
    val captureEndedMonoNs: Long?,
    val capturedStartMs: Long?,
    val capturedEndMs: Long?,
    val codec: String?,
    val sampleRate: Int?,
    val bitRate: Int?,
    val durationMs: Long?,
    val checksum: String?,
    val failureStage: String?,
    val resourceEpoch: Long,
) {
    init {
        requireNotBlank(id, "id")
        requireNotBlank(sessionId, "sessionId")
        requireNotBlank(tempPath, "tempPath")
        require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
        requireNonNegative(createdAtUtcMs, "createdAtUtcMs")
        requireNonNegative(resourceEpoch, "resourceEpoch")
        requireNotBlankIfPresent(finalPath, "finalPath")
        requireNotBlankIfPresent(codec, "codec")
        requireNotBlankIfPresent(checksum, "checksum")
        requireNotBlankIfPresent(failureStage, "failureStage")
        sealedAtUtcMs?.let { requireNonNegative(it, "sealedAtUtcMs") }
        durationMs?.let { requireNonNegative(it, "durationMs") }
        sampleRate?.let { require(it > 0) { "sampleRate 必须为正数：$it" } }
        bitRate?.let { require(it > 0) { "bitRate 必须为正数：$it" } }

        if (captureStartedMonoNs != null && captureEndedMonoNs != null) {
            require(captureEndedMonoNs >= captureStartedMonoNs) {
                "captureEndedMonoNs($captureEndedMonoNs) 不能早于 captureStartedMonoNs($captureStartedMonoNs)"
            }
        }
        captureStartedMonoNs?.let { requireNonNegative(it, "captureStartedMonoNs") }
        captureEndedMonoNs?.let { requireNonNegative(it, "captureEndedMonoNs") }
        if (capturedStartMs != null && capturedEndMs != null) {
            requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
        }
        capturedStartMs?.let { requireNonNegative(it, "capturedStartMs") }
        capturedEndMs?.let { requireNonNegative(it, "capturedEndMs") }

        when (state) {
            ChunkState.PLACEHOLDER -> {
                require(sealedAtUtcMs == null) { "PLACEHOLDER 块不得带 sealedAtUtcMs" }
                require(finalPath == null) { "PLACEHOLDER 块不得带 finalPath" }
            }

            ChunkState.SEALED -> {
                require(sealedAtUtcMs != null) { "SEALED 块必须带 sealedAtUtcMs" }
                require(finalPath != null) { "SEALED 块必须带 finalPath" }
                require(checksum != null) { "SEALED 块必须带 checksum" }
            }

            else -> Unit
        }
    }
}
