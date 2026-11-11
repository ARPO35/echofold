package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState

/**
 * 容器探测结果。实现负责解析 M4A 索引并回答“能否证明完整”以及“可解码前缀到哪里”。
 */
sealed interface ProbeReport {
    /**
     * 索引完整且末尾元数据完整。
     *
     * [bytes] 为解析器实际读到的文件字节数，用于与 [ChunkFileSystem.size] 对账；
     * [checksum] 为整文件 SHA-256 小写十六进制，缺失表示无法提供完整性证据。
     */
    data class Complete(val durationMs: Long, val bytes: Long, val checksum: String) : ProbeReport

    /** 仅前缀可解码：[decodablePrefixMs] 是解析器能证明可解码的时长（未量化）。 */
    data class Partial(val decodablePrefixMs: Long, val bytes: Long) : ProbeReport

    /** 文件不可解析（截断的容器头、非 M4A、IO 失败等）。 */
    data class Unreadable(val reason: String) : ProbeReport
}

/** 容器探测器：读取临时或最终文件并给出 [ProbeReport]。 */
fun interface ChunkContainerProbe {
    fun probe(path: String): ProbeReport
}

/** 恢复分类的原因，写入 [AudioChunk.failureStage] 之外单独返回，便于诊断与量化。 */
enum class RecoveryReason {
    /** 临时路径与最终路径都不存在：证据已丢失。 */
    MISSING_FILE,

    /** 容器不可解析。 */
    UNREADABLE_CONTAINER,

    /** 解析器报告的字节数与文件系统实际尺寸不一致。 */
    SIZE_MISMATCH,

    /** 校验和缺失、格式非法或与已登记值不一致。 */
    CHECKSUM_MISMATCH,

    /** 容器声明的时长与已记录的有效采集区间不一致，时间映射不可信。 */
    TIME_MAPPING_INCONSISTENT,
}

/**
 * 可解码前缀的量化边界。
 *
 * 恢复只承诺“量化后可证明”的部分：原始解析结果 [rawPrefixMs] 向下对齐到整帧，
 * [decodableEndMs] 即该块内可安全回听/导出的截止偏移，[lossMs] 为相对已知总时长的尾部损失（未知为 `null`）。
 */
data class QuantizedBoundary(
    val rawPrefixMs: Long,
    val decodableEndMs: Long,
    val frameDurationMs: Long,
    val lossMs: Long?,
)

/** 恢复扫描的三态结论；[chunk] 已带上目标状态，调用方只需按需提交。 */
sealed interface RecoveryVerdict {
    val chunk: AudioChunk

    /**
     * 可证明完整：可直接补交 `SEALED`。
     *
     * [needsCommit] 为 `true` 表示改名或数据库提交在崩溃中丢失，需要调用方重新提交；
     * 为 `false` 表示数据库已记录完整块，本次只是复核通过。
     */
    data class Sealed(override val chunk: AudioChunk, val needsCommit: Boolean) : RecoveryVerdict

    /** 仅可解码前缀：标为 `RECOVERABLE` 并给出量化边界，尾部损失单独记录。 */
    data class Recoverable(override val chunk: AudioChunk, val boundary: QuantizedBoundary) : RecoveryVerdict

    /** 不可用于默认回听、导出成功或重识别输入。 */
    data class Corrupt(override val chunk: AudioChunk, val reason: RecoveryReason) : RecoveryVerdict
}

/**
 * 未封存块的恢复扫描：输入持久化状态 + 文件系统/容器探测，输出 `SEALED`/`RECOVERABLE`/`CORRUPT` 三态。
 *
 * 判定顺序（全部基于可验证证据，不做乐观假设）：
 * 1. 最终路径存在且容器完整 → 可补交 `SEALED`（崩溃发生在改名之后、数据库提交之前）；
 * 2. 临时路径存在且容器完整、尺寸与校验一致、时间映射自洽 → 可补交 `SEALED`；
 * 3. 临时路径存在但只有可解码前缀 → `RECOVERABLE` + 量化边界；
 * 4. 文件缺失、容器不可解析、尺寸/校验不一致、时间映射矛盾 → `CORRUPT`。
 *
 * 本类只读，不删除也不改名任何文件，也不复用已分配序号：[nextSeqAfter] 永远返回“已见最大序号 + 1”。
 */
class RecoveryScanner(
    private val fileSystem: ChunkFileSystem,
    private val probe: ChunkContainerProbe,
    private val recoveredAtUtcMs: () -> Long = { System.currentTimeMillis() },
    private val frameDurationMs: Long = DEFAULT_FRAME_DURATION_MS,
    private val durationToleranceMs: Long = DEFAULT_DURATION_TOLERANCE_MS,
) {
    init {
        require(frameDurationMs > 0) { "frameDurationMs 必须为正数：$frameDurationMs" }
        require(durationToleranceMs >= 0) { "durationToleranceMs 不能为负数：$durationToleranceMs" }
    }

    /** 扫描单个未封存块；已 `SEALED` 的块原样返回（幂等）。 */
    fun scan(chunk: AudioChunk): RecoveryVerdict {
        if (chunk.state == ChunkState.SEALED) return RecoveryVerdict.Sealed(chunk, needsCommit = false)

        val finalPath = chunk.finalPath
        if (finalPath != null && fileSystem.exists(finalPath)) {
            return classify(chunk, finalPath, alreadyAtFinalPath = true)
        }
        if (fileSystem.exists(chunk.tempPath)) {
            return classify(chunk, chunk.tempPath, alreadyAtFinalPath = false)
        }
        return corrupt(chunk, RecoveryReason.MISSING_FILE)
    }

    private fun classify(chunk: AudioChunk, path: String, alreadyAtFinalPath: Boolean): RecoveryVerdict {
        val report = try {
            probe.probe(path)
        } catch (t: Throwable) {
            ProbeReport.Unreadable(t.message ?: "容器探测失败")
        }
        val actualSize = runCatching { fileSystem.size(path) }.getOrNull()
        return when (report) {
            is ProbeReport.Unreadable -> corrupt(chunk, RecoveryReason.UNREADABLE_CONTAINER)

            is ProbeReport.Partial -> {
                if (actualSize != null && report.bytes > actualSize) {
                    corrupt(chunk, RecoveryReason.SIZE_MISMATCH)
                } else {
                    recoverable(chunk, report)
                }
            }

            is ProbeReport.Complete -> complete(chunk, path, alreadyAtFinalPath, report, actualSize)
        }
    }

    private fun complete(
        chunk: AudioChunk,
        path: String,
        alreadyAtFinalPath: Boolean,
        report: ProbeReport.Complete,
        actualSize: Long?,
    ): RecoveryVerdict {
        if (actualSize != null && actualSize != report.bytes) {
            return corrupt(chunk, RecoveryReason.SIZE_MISMATCH)
        }
        if (!CHECKSUM_HEX.matches(report.checksum)) {
            return corrupt(chunk, RecoveryReason.CHECKSUM_MISMATCH)
        }
        chunk.checksum?.let { registered ->
            if (registered != report.checksum) return corrupt(chunk, RecoveryReason.CHECKSUM_MISMATCH)
        }
        if (!timeMappingConsistent(chunk, report.durationMs)) {
            return corrupt(chunk, RecoveryReason.TIME_MAPPING_INCONSISTENT)
        }

        val finalPath = if (alreadyAtFinalPath) path else ChunkPaths.finalPathOf(chunk.tempPath)
        val sealed = chunk.copy(
            state = ChunkState.SEALED,
            finalPath = finalPath,
            sealedAtUtcMs = chunk.sealedAtUtcMs ?: recoveredAtUtcMs(),
            checksum = report.checksum,
            durationMs = chunk.durationMs ?: report.durationMs,
            failureStage = null,
        )
        return RecoveryVerdict.Sealed(sealed, needsCommit = true)
    }

    private fun recoverable(chunk: AudioChunk, report: ProbeReport.Partial): RecoveryVerdict {
        val rawPrefix = report.decodablePrefixMs.coerceAtLeast(0)
        val quantized = rawPrefix - rawPrefix % frameDurationMs
        val expectedTotalMs = expectedTotalMs(chunk)
        val boundary = QuantizedBoundary(
            rawPrefixMs = rawPrefix,
            decodableEndMs = quantized,
            frameDurationMs = frameDurationMs,
            lossMs = expectedTotalMs?.let { (it - quantized).coerceAtLeast(0) },
        )
        return RecoveryVerdict.Recoverable(chunk.copy(state = ChunkState.RECOVERABLE), boundary)
    }

    /** 容器时长必须与已记录的有效采集区间自洽；两者任一未知时不作为拒绝理由。 */
    private fun timeMappingConsistent(chunk: AudioChunk, containerDurationMs: Long): Boolean {
        val expected = expectedTotalMs(chunk) ?: return true
        return kotlin.math.abs(expected - containerDurationMs) <= durationToleranceMs
    }

    private fun expectedTotalMs(chunk: AudioChunk): Long? {
        val start = chunk.capturedStartMs
        val end = chunk.capturedEndMs
        if (start != null && end != null) return end - start
        return chunk.durationMs
    }

    private fun corrupt(chunk: AudioChunk, reason: RecoveryReason): RecoveryVerdict.Corrupt =
        RecoveryVerdict.Corrupt(chunk.copy(state = ChunkState.CORRUPT), reason)

    companion object {
        /** AAC 常见帧长（毫秒），用于把可解码前缀向下对齐到整帧。 */
        const val DEFAULT_FRAME_DURATION_MS = 20L

        /** 容器时长与有效采集区间的允许偏差。 */
        const val DEFAULT_DURATION_TOLERANCE_MS = 100L

        private val CHECKSUM_HEX = Regex("^[0-9a-f]{64}$")

        /**
         * 下一个可用序号：已见最大序号 + 1。
         *
         * 恢复扫描不会让序号回退——即使已见块全部损坏，其序号也已分配过，不得复用。
         */
        fun nextSeqAfter(chunks: List<AudioChunk>): Int =
            ((chunks.maxOfOrNull { it.chunkSeq } ?: -1) + 1).coerceAtLeast(0)
    }
}
