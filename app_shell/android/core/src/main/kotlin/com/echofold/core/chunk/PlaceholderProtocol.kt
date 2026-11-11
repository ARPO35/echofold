package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState

/**
 * 占位协议失败的阶段。阶段值会写入 [AudioChunk.failureStage]，供诊断与恢复使用。
 */
enum class PlaceholderStage {
    /** 独占创建临时文件失败：路径已存在或文件系统不可用。 */
    FILE_EXCLUSIVE,

    /** 数据库事务未能原子提交 `(sessionId, chunkSeq)` 占位。 */
    ALLOCATE,

    /** 编码器创建或初始化失败。 */
    INITIALIZE,

    /** 块未能提交为 `OPEN`。 */
    OPEN_COMMIT,
}

/** 占位协议结果：成功时同时交回已初始化编码器与 `OPEN` 块，失败时给出阶段与资源世代。 */
sealed interface PlaceholderOutcome {
    /** 占位已提交、编码器已初始化、块已置 `OPEN`，调用方可以恢复分发。 */
    data class Open(val chunk: AudioChunk, val encoder: ChunkEncoderHandle) : PlaceholderOutcome

    /**
     * 结构化失败：分发保持 `BLOCKED`，序号已消耗不可复用。
     *
     * [chunk] 为 `null` 表示失败发生在块登记之前（独占创建或占位提交），此时没有任何块被写入仓储；
     * 非 `null` 表示占位已登记，返回的块为保留临时文件的失败块，不得被当作 `OPEN`/`SEALED`。
     */
    data class Failed(
        val stage: PlaceholderStage,
        val chunk: AudioChunk?,
        val resourceEpoch: Long,
        val cause: Throwable?,
    ) : PlaceholderOutcome
}

/**
 * 新块先占位协议：把“停止分发 → 独占临时路径 → 原子占位 → 编码器初始化 → `OPEN` → 恢复分发”
 * 固化成一段不可重排的顺序代码。
 *
 * 顺序不可交换的原因：编码器必须在块身份与临时路径已持久化之后才创建，否则进程崩溃后会出现
 * 未登记编码器写入的孤儿文件；反过来，任何一步失败都不得把块标成 `OPEN`，也不得继续向旧块追加。
 *
 * 失败语义：
 * - 任一阶段失败都**保持 `BLOCKED`**（本类从不调用 [ChunkDispatchGate.resume]）；
 * - 占位提交之前失败：不写仓储、不创建编码器；
 * - 占位提交之后失败：把块落为 `SEAL_FAILED` 并保留临时文件与失败阶段，不标 `OPEN`/`SEALED`；
 * - 序号与临时路径一经尝试即作废，重试必须使用下一个序号（由 [ChunkSwitcher] 保证单调）。
 */
class PlaceholderProtocol(
    private val repository: ChunkRepository,
    private val fileSystem: ChunkFileSystem,
    private val gate: ChunkDispatchGate,
    private val nowUtcMs: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * 尝试为 [sessionId] 的第 [chunkSeq] 块开出一条可分发通道。
     *
     * @param resourceEpoch 调用方维护的资源世代号，写入成功块并随失败结果返回，便于把失败归因到具体编码器实例。
     * @param encoderFactory 只在占位提交成功后调用；传入的块身份可用于命名与诊断。
     */
    fun open(
        sessionId: String,
        chunkSeq: Int,
        tempPath: String,
        resourceEpoch: Long,
        encoderFactory: (AudioChunk) -> ChunkEncoderHandle,
    ): PlaceholderOutcome {
        // 第一步即切点：先冻结分发，之后任何属于新块区间的 PCM 都不会进入旧块。
        gate.block()

        val created: Boolean = try {
            fileSystem.createExclusive(tempPath)
        } catch (t: Throwable) {
            return PlaceholderOutcome.Failed(PlaceholderStage.FILE_EXCLUSIVE, null, resourceEpoch, t)
        }
        if (!created) {
            return PlaceholderOutcome.Failed(PlaceholderStage.FILE_EXCLUSIVE, null, resourceEpoch, null)
        }

        // 独占文件已建立，才允许登记块身份；此处失败会留下空临时文件（本接口不提供删除能力），
        // 由恢复扫描清理，绝不覆盖其他序号的文件。
        val placeholder = try {
            repository.allocatePlaceholder(sessionId, chunkSeq, tempPath, nowUtcMs())
        } catch (t: Throwable) {
            return PlaceholderOutcome.Failed(PlaceholderStage.ALLOCATE, null, resourceEpoch, t)
        }

        // 编码器只能在登记之后创建：未登记的编码器一律不得初始化，更不得写入。
        val encoder = try {
            encoderFactory(placeholder)
        } catch (t: Throwable) {
            return failRegistered(placeholder, PlaceholderStage.INITIALIZE, resourceEpoch, t)
        }
        try {
            encoder.initialize()
        } catch (t: Throwable) {
            return failRegistered(placeholder, PlaceholderStage.INITIALIZE, resourceEpoch, t)
        }

        val opened = placeholder.copy(state = ChunkState.OPEN, resourceEpoch = resourceEpoch)
        try {
            repository.update(opened)
        } catch (t: Throwable) {
            return failRegistered(placeholder, PlaceholderStage.OPEN_COMMIT, resourceEpoch, t)
        }

        // 到此为止：占位已提交、编码器已初始化、块已 OPEN，才允许恢复分发。
        gate.resume()
        return PlaceholderOutcome.Open(opened, encoder)
    }

    /**
     * 占位已登记之后的失败处理：落为 `SEAL_FAILED`、保留临时文件与失败阶段，绝不标 `OPEN`/`SEALED`。
     *
     * 写库本身失败时仍返回同一失败结果：调用方必须按 `BLOCKED` 处理，数据库状态留给恢复流程裁决。
     */
    private fun failRegistered(
        placeholder: AudioChunk,
        stage: PlaceholderStage,
        resourceEpoch: Long,
        cause: Throwable?,
    ): PlaceholderOutcome.Failed {
        val failed = placeholder.copy(
            state = ChunkState.SEAL_FAILED,
            failureStage = stage.name,
            resourceEpoch = resourceEpoch,
        )
        runCatching { repository.update(failed) }
        return PlaceholderOutcome.Failed(stage, failed, resourceEpoch, cause)
    }
}
