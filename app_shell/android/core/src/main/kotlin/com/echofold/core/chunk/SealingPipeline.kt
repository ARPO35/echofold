package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState

/**
 * 封存失败的阶段。写入 [AudioChunk.failureStage]，恢复扫描与诊断都以此定位断点。
 */
enum class SealStage {
    /** 停止接收后未能把已接受 PCM 排空到编码器。 */
    DRAIN,

    /** 容器索引写入、flush 或 fsync 失败（位于 [ChunkEncoderHandle.finishAndSeal] 内）。 */
    FINISH,

    /** 改名前的校验失败：文件缺失、尺寸不符或校验和不可信。 */
    VERIFY,

    /** 同卷原子改名失败。 */
    RENAME,

    /** 数据库提交 `SEALED` 失败。 */
    COMMIT,
}

/** 封存结果：成功返回 `SEALED` 行，失败返回保留临时文件的 `SEAL_FAILED` 行。 */
sealed interface SealOutcome {
    /** 时长、校验、最终路径与封存时间均已提交。 */
    data class Sealed(val chunk: AudioChunk) : SealOutcome

    /**
     * 封存未完成：块已落为 `SEAL_FAILED`，临时文件与已写入字节保留。
     *
     * [resourceEpoch] 用于把失败归因到具体编码资源世代；[stage] 记录失败阶段。
     */
    data class Failed(
        val chunk: AudioChunk,
        val stage: SealStage,
        val resourceEpoch: Long,
        val cause: Throwable?,
    ) : SealOutcome
}

/**
 * 单块封存流水线：`OPEN → SEALING（停止接收并排空）→ flush/同步 → 校验和 → 原子改名 → SEALED`。
 *
 * 关键约束：
 * - 进入 [seal] 后立刻把块提交为 `SEALING`，此后任何 [write] 都抛 [IllegalStateException]（不可重新打开追加）；
 * - 只有 [ChunkFileSystem.exists] 为真、尺寸与 [SealResult.bytesWritten] 一致、校验和合法且与已登记值一致，
 *   才允许把块提交为 `SEALED`（[ChunkFileSystem] 不提供读取字节的能力，因此完整性以编码器落盘后自报的
 *   尺寸与校验和为准，二者都必须来自真实文件而不是请求参数）；
 * - 任一步失败落为 `SEAL_FAILED` 并保留临时文件与失败阶段，**不删除任何文件**，也不把可解码前缀冒充完整块；
 * - [seal] 幂等：同一流水线重复调用返回同一结果，不会二次改名。
 */
class SealingPipeline(
    chunk: AudioChunk,
    private val repository: ChunkRepository,
    private val fileSystem: ChunkFileSystem,
    private val encoder: ChunkEncoderHandle,
    private val drainPending: () -> Unit = {},
    private val nowUtcMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var current: AudioChunk = chunk
    private var settled: SealOutcome? = null

    init {
        require(chunk.state == ChunkState.OPEN || chunk.state == ChunkState.SEALING) {
            "流水线只能接管 OPEN/SEALING 块，实际为 ${chunk.state}"
        }
    }

    /** 当前块的最新持久化快照。 */
    val snapshot: AudioChunk get() = synchronized(lock) { current }

    /**
     * 写入 PCM；`SEALING` 期间（以及任何非 `OPEN` 状态）抛 [IllegalStateException]。
     *
     * 写入与封存共用同一把锁：切点前的写入按原块归属完成，切点后的写入一律被拒绝。
     */
    fun write(bytes: ByteArray, frameIndex: Long) {
        synchronized(lock) {
            val chunk = current
            check(chunk.state == ChunkState.OPEN) {
                "块 ${chunk.id} 处于 ${chunk.state}，拒绝写入"
            }
            encoder.write(bytes, frameIndex)
        }
    }

    /**
     * 停止接收并把块提交为 `SEALING`；重复调用幂等。
     *
     * 调用返回后本流水线不再接受任何写入，直到 [seal] 完成。
     */
    fun beginSealing(): AudioChunk = synchronized(lock) { beginSealingLocked() }

    /** 执行完整封存；失败返回 `SEAL_FAILED` 结果，不抛异常。 */
    fun seal(): SealOutcome = synchronized(lock) {
        settled?.let { return it }
        if (current.state == ChunkState.SEALED) {
            return SealOutcome.Sealed(current).also { settled = it }
        }

        var stage = SealStage.DRAIN
        try {
            beginSealingLocked()
            drainPending()
            stage = SealStage.FINISH
            val result = encoder.finishAndSeal()

            stage = SealStage.VERIFY
            verifyBeforeRename(result)

            stage = SealStage.RENAME
            val finalPath = current.finalPath ?: ChunkPaths.finalPathOf(current.tempPath)
            fileSystem.renameAtomically(current.tempPath, finalPath)
            verifyAfterRename(finalPath, result)

            stage = SealStage.COMMIT
            val sealed = current.copy(
                state = ChunkState.SEALED,
                finalPath = finalPath,
                sealedAtUtcMs = nowUtcMs(),
                checksum = result.checksum,
                durationMs = result.durationMs,
                codec = result.codec,
                sampleRate = result.sampleRate,
                bitRate = result.bitRate,
                failureStage = null,
            )
            repository.update(sealed)
            current = sealed
            return SealOutcome.Sealed(sealed).also { settled = it }
        } catch (t: Throwable) {
            return failLocked(stage, t)
        }
    }

    private fun beginSealingLocked(): AudioChunk {
        val chunk = current
        if (chunk.state == ChunkState.OPEN) {
            val sealing = chunk.copy(state = ChunkState.SEALING)
            repository.update(sealing)
            current = sealing
        }
        return current
    }

    /** 改名前的完整性校验：文件存在、尺寸一致、校验和格式合法且与已登记值一致。 */
    private fun verifyBeforeRename(result: SealResult) {
        val tempPath = current.tempPath
        check(fileSystem.exists(tempPath)) { "临时文件不存在：$tempPath" }
        val size = fileSystem.size(tempPath)
        check(size == result.bytesWritten) {
            "临时文件尺寸与编码器报告不一致：期望 ${result.bytesWritten}，实际 $size"
        }
        check(SHA256_HEX.matches(result.checksum)) { "校验和不是 SHA-256 小写十六进制：${result.checksum}" }
        current.checksum?.let { registered ->
            check(registered == result.checksum) {
                "校验和与已登记值不一致：登记 $registered，实际 ${result.checksum}"
            }
        }
    }

    /** 改名后的复核：最终路径存在且尺寸一致、临时路径已消失，否则视为改名未完整提交。 */
    private fun verifyAfterRename(finalPath: String, result: SealResult) {
        check(fileSystem.exists(finalPath)) { "改名后最终路径缺失：$finalPath" }
        val size = fileSystem.size(finalPath)
        check(size == result.bytesWritten) { "改名后尺寸不一致：期望 ${result.bytesWritten}，实际 $size" }
        check(!fileSystem.exists(current.tempPath)) { "改名后临时路径仍存在：${current.tempPath}" }
    }

    private fun failLocked(stage: SealStage, cause: Throwable): SealOutcome {
        val failed = current.copy(state = ChunkState.SEAL_FAILED, failureStage = stage.name)
        current = failed
        // 数据库不可用时也必须返回同一失败结果：调用方据此保持 BLOCKED，临时文件原样保留。
        runCatching { repository.update(failed) }
        return SealOutcome.Failed(failed, stage, failed.resourceEpoch, cause).also { settled = it }
    }

    private companion object {
        /** SHA-256 小写十六进制，长度固定 64，与共享契约的校验和格式一致。 */
        val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
