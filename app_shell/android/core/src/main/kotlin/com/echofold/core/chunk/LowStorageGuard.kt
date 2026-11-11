package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.SessionStatus

/** 低存储停止流程的失败阶段。 */
enum class LowStorageStage {
    /** 当前块未能封存。 */
    SEAL,

    /** 元数据未能冲刷提交。 */
    FLUSH,

    /** 预留空间未能释放。 */
    RELEASE,
}

/** 低存储停止结果。 */
sealed interface LowStorageOutcome {
    /**
     * 按顺序完成“冻结分发 → 封存当前块 → 冲刷元数据 → 释放预留”，可以显示低存储暂停。
     *
     * 注意：暂停态**不创建下一块占位**，空间恢复后必须由用户明确继续，并从完整占位协议重新开始。
     */
    data class Paused(
        val chunk: AudioChunk?,
        val status: SessionStatus = SessionStatus.LOW_STORAGE_PAUSED,
    ) : LowStorageOutcome

    /** 任一步失败：保持 `BLOCKED`，不自动恢复；[chunk] 为已落为 `SEAL_FAILED` 的块（若有）。 */
    data class Failed(
        val stage: LowStorageStage,
        val chunk: AudioChunk?,
        val cause: Throwable?,
    ) : LowStorageOutcome
}

/**
 * 低存储硬下限的保护序列。
 *
 * 顺序固定且不可交换：`BLOCKED` → 封存当前块 → 冲刷元数据 → 释放预留 → `LOW_STORAGE_PAUSED`。
 * 前置条件是下一块的 [SpaceBudget.reserve] 已经失败；因此本类**不持有 [ChunkRepository]**，
 * 结构上不可能创建下一块占位或初始化编码器——这正是“预留失败时绝不落新块”的最强保证。
 *
 * 失败语义：任一步失败都停在 `BLOCKED`，并把封存失败如实反映为 `SEAL_FAILED`（由 [sealer] 落库），
 * 绝不把失败伪装成用户暂停。
 */
class LowStorageGuard(
    private val budget: SpaceBudget,
    private val gate: ChunkDispatchGate,
    private val sealer: (AudioChunk) -> SealOutcome?,
    private val flushMetadata: () -> Unit = {},
) {
    /**
     * 执行低存储停止序列。
     *
     * @param activeChunk 当前需要封存的活动块；`null` 表示没有待封存内容（例如刚换块失败）。
     * @param holdBytes 当前块此前预留的字节数，封存成功后释放。
     */
    fun enter(activeChunk: AudioChunk?, holdBytes: Long): LowStorageOutcome {
        gate.block()

        var sealedChunk: AudioChunk? = null
        if (activeChunk != null) {
            val outcome = try {
                sealer(activeChunk)
            } catch (t: Throwable) {
                return LowStorageOutcome.Failed(LowStorageStage.SEAL, activeChunk, t)
            }
            when (outcome) {
                // 已经没有可封存内容（例如刚被换块流程封存）：继续后续步骤。
                null -> Unit
                is SealOutcome.Sealed -> sealedChunk = outcome.chunk
                is SealOutcome.Failed -> return LowStorageOutcome.Failed(
                    LowStorageStage.SEAL, outcome.chunk, outcome.cause,
                )
            }
        }

        try {
            flushMetadata()
        } catch (t: Throwable) {
            return LowStorageOutcome.Failed(LowStorageStage.FLUSH, sealedChunk, t)
        }

        try {
            budget.release(holdBytes)
        } catch (t: Throwable) {
            return LowStorageOutcome.Failed(LowStorageStage.RELEASE, sealedChunk, t)
        }

        return LowStorageOutcome.Paused(sealedChunk)
    }
}
