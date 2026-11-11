package com.echofold.core.chunk

import com.echofold.core.model.ChunkState
import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * 低存储保护测试：顺序固定为 `BLOCKED → 封存 → 冲刷 → 释放 → LOW_STORAGE_PAUSED`，
 * 且任何路径都不得创建下一块占位。
 */
class LowStorageGuardTest {

    private val repo = InMemoryChunkRepository()
    private val fileSystem = FakeFileSystem()

    private fun seedOpenChunk(): com.echofold.core.model.AudioChunk {
        val chunk = testChunk(tempPath = ChunkPaths.tempPath("s1", 0))
        repo.seed(chunk)
        fileSystem.put(chunk.tempPath, 0L)
        return chunk
    }

    @Test
    fun `按顺序封存-冲刷-释放并进入低存储暂停`() {
        val chunk = seedOpenChunk()
        val budget = FixedSpaceBudget(capacity = 10)
        budget.reserve(4)
        val gate = ChunkDispatchGate(DispatchState.RUNNING)
        val order = mutableListOf<String>()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem).apply { initialize() }
        val guard = LowStorageGuard(
            budget = budget,
            gate = gate,
            sealer = { target ->
                order += "seal"
                SealingPipeline(target, repo, fileSystem, encoder, nowUtcMs = { 1L }).seal()
            },
            flushMetadata = { order += "flush" },
        )

        val outcome = guard.enter(chunk, holdBytes = 4)

        val paused = outcome as LowStorageOutcome.Paused
        assertEquals(SessionStatus.LOW_STORAGE_PAUSED, paused.status)
        assertEquals(ChunkState.SEALED, paused.chunk?.state)
        assertEquals(listOf("seal", "flush"), order)
        assertEquals(listOf("reserve(4)", "release(4)"), budget.calls)
        assertEquals(0L, budget.used)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertTrue(repo.calls.none { it.startsWith("allocatePlaceholder") })
        assertEquals(1, repo.listBySession("s1").size)
    }

    @Test
    fun `封存失败保持 BLOCKED 且如实反映 SEAL_FAILED`() {
        val chunk = seedOpenChunk()
        val budget = FixedSpaceBudget(capacity = 10)
        val gate = ChunkDispatchGate(DispatchState.RUNNING)
        var flushed = false
        val encoder = FakeEncoder(chunk.tempPath, fileSystem).apply {
            initialize()
            finishFailure = IOException("文件描述符失效")
        }
        val guard = LowStorageGuard(
            budget = budget,
            gate = gate,
            sealer = { target -> SealingPipeline(target, repo, fileSystem, encoder).seal() },
            flushMetadata = { flushed = true },
        )

        val failed = guard.enter(chunk, holdBytes = 0) as LowStorageOutcome.Failed

        assertEquals(LowStorageStage.SEAL, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk?.state)
        assertEquals(SealStage.FINISH.name, failed.chunk?.failureStage)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertFalse(flushed)
        assertTrue(budget.calls.isEmpty())
        assertTrue(repo.calls.none { it.startsWith("allocatePlaceholder") })
    }

    @Test
    fun `冲刷失败不进入低存储暂停`() {
        val chunk = seedOpenChunk()
        val budget = FixedSpaceBudget(capacity = 10)
        val gate = ChunkDispatchGate(DispatchState.RUNNING)
        val encoder = FakeEncoder(chunk.tempPath, fileSystem).apply { initialize() }
        val guard = LowStorageGuard(
            budget = budget,
            gate = gate,
            sealer = { target -> SealingPipeline(target, repo, fileSystem, encoder).seal() },
            flushMetadata = { throw IllegalStateException("数据库不可用") },
        )

        val failed = guard.enter(chunk, holdBytes = 2) as LowStorageOutcome.Failed

        assertEquals(LowStorageStage.FLUSH, failed.stage)
        assertEquals(ChunkState.SEALED, failed.chunk?.state)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertTrue(budget.calls.isEmpty())
    }

    @Test
    fun `没有活动块时直接暂停且不封存`() {
        var sealerCalled = false
        val gate = ChunkDispatchGate(DispatchState.RUNNING)
        val guard = LowStorageGuard(
            budget = FixedSpaceBudget(capacity = 0),
            gate = gate,
            sealer = {
                sealerCalled = true
                null
            },
        )

        val paused = guard.enter(null, holdBytes = 0) as LowStorageOutcome.Paused

        assertFalse(sealerCalled)
        assertNull(paused.chunk)
        assertEquals(SessionStatus.LOW_STORAGE_PAUSED, paused.status)
        assertEquals(DispatchState.BLOCKED, gate.state)
    }

    @Test
    fun `释放预留失败报 RELEASE 阶段`() {
        val budget = object : SpaceBudget {
            override fun reserve(bytes: Long): Boolean = true
            override fun release(bytes: Long) {
                throw IllegalStateException("预算表损坏")
            }
        }
        val gate = ChunkDispatchGate(DispatchState.RUNNING)
        val guard = LowStorageGuard(budget = budget, gate = gate, sealer = { null })

        val failed = guard.enter(null, holdBytes = 3) as LowStorageOutcome.Failed

        assertEquals(LowStorageStage.RELEASE, failed.stage)
        assertEquals(DispatchState.BLOCKED, gate.state)
    }
}
