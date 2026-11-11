package com.echofold.core.chunk

import com.echofold.core.model.ChunkState
import com.echofold.core.model.DispatchState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 占位协议测试：顺序不可交换，任一阶段失败都必须停在 `BLOCKED` 且不产生 `OPEN`/可分发编码器。
 */
class PlaceholderProtocolTest {

    private val repo = InMemoryChunkRepository()
    private val fileSystem = FakeFileSystem()
    private val gate = ChunkDispatchGate(DispatchState.RUNNING)
    private val protocol = PlaceholderProtocol(repo, fileSystem, gate, nowUtcMs = { 5_000L })
    private val tempPath = ChunkPaths.tempPath("s1", 0)

    @Test
    fun `正常路径按固定顺序占位并恢复分发`() {
        var encoder: FakeEncoder? = null
        val outcome = protocol.open("s1", 0, tempPath, 42L) { chunk ->
            // 编码器创建时：占位已登记、分发已冻结，顺序在这一刻已被证明。
            assertEquals(ChunkState.PLACEHOLDER, repo.find(chunk.id)?.state)
            assertEquals(DispatchState.BLOCKED, gate.state)
            FakeEncoder(chunk.tempPath, fileSystem).also { encoder = it }
        }

        val opened = outcome as PlaceholderOutcome.Open
        assertTrue(encoder!!.initialized)
        assertEquals(ChunkState.OPEN, opened.chunk.state)
        assertEquals(42L, opened.chunk.resourceEpoch)
        assertEquals(5_000L, opened.chunk.createdAtUtcMs)
        assertSame(encoder, opened.encoder)
        assertEquals(DispatchState.RUNNING, gate.state)
        assertEquals(listOf("createExclusive($tempPath)"), fileSystem.calls)
        assertEquals(listOf("allocatePlaceholder(s1,0)", "update(s1-0,OPEN)"), repo.calls)
    }

    @Test
    fun `独占创建失败时不登记块也不创建编码器`() {
        fileSystem.createExclusiveResult = false
        var encoderCreated = false

        val outcome = protocol.open("s1", 0, tempPath, 42L) {
            encoderCreated = true
            FakeEncoder(it.tempPath, fileSystem)
        }

        val failed = outcome as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.FILE_EXCLUSIVE, failed.stage)
        assertNull(failed.chunk)
        assertEquals(42L, failed.resourceEpoch)
        assertFalse(encoderCreated)
        assertTrue(repo.calls.isEmpty())
        assertNull(repo.findActive("s1"))
        assertEquals(DispatchState.BLOCKED, gate.state)
    }

    @Test
    fun `占位提交失败时保持 BLOCKED 且不初始化编码器`() {
        repo.failAllocate = IllegalStateException("唯一约束冲突")
        var encoderCreated = false

        val outcome = protocol.open("s1", 0, tempPath, 9L) {
            encoderCreated = true
            FakeEncoder(it.tempPath, fileSystem)
        }

        val failed = outcome as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.ALLOCATE, failed.stage)
        assertNull(failed.chunk)
        assertEquals(9L, failed.resourceEpoch)
        assertFalse(encoderCreated)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertNull(repo.findActive("s1"))
        // 临时文件保留给恢复扫描清理，不覆盖、不复用。
        assertTrue(fileSystem.exists(tempPath))
    }

    @Test
    fun `编码器初始化失败时保留失败占位且不标 OPEN`() {
        var encoder: FakeEncoder? = null

        val outcome = protocol.open("s1", 0, tempPath, 11L) { chunk ->
            FakeEncoder(chunk.tempPath, fileSystem).also {
                it.initializeFailure = IllegalStateException("编码器初始化失败")
                encoder = it
            }
        }

        val failed = outcome as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.INITIALIZE, failed.stage)
        val chunk = requireNotNull(failed.chunk)
        assertEquals(ChunkState.SEAL_FAILED, chunk.state)
        assertEquals(PlaceholderStage.INITIALIZE.name, chunk.failureStage)
        assertEquals(11L, chunk.resourceEpoch)
        assertFalse(encoder!!.initialized)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertEquals(ChunkState.SEAL_FAILED, repo.find(chunk.id)?.state)
        assertNull(repo.findActive("s1"))
        assertTrue(fileSystem.exists(tempPath))
    }

    @Test
    fun `编码器工厂抛出异常时同样保留失败占位`() {
        val outcome = protocol.open("s1", 0, tempPath, 12L) {
            throw IllegalStateException("无法申请编码器")
        }

        val failed = outcome as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.INITIALIZE, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk?.state)
        assertEquals("s1-0", failed.chunk?.id)
        assertEquals(DispatchState.BLOCKED, gate.state)
    }

    @Test
    fun `OPEN 提交失败时不恢复分发`() {
        repo.failUpdate = IllegalStateException("数据库不可用")

        val outcome = protocol.open("s1", 0, tempPath, 13L) { chunk ->
            FakeEncoder(chunk.tempPath, fileSystem)
        }

        val failed = outcome as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.OPEN_COMMIT, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk?.state)
        assertEquals(PlaceholderStage.OPEN_COMMIT.name, failed.chunk?.failureStage)
        assertEquals(DispatchState.BLOCKED, gate.state)
        assertNull(repo.findActive("s1"))
    }

    @Test
    fun `同一会话重复序号被仓储拒绝并给出结构化失败`() {
        val first = protocol.open("s1", 0, tempPath, 1L) { chunk -> FakeEncoder(chunk.tempPath, fileSystem) }
        assertTrue(first is PlaceholderOutcome.Open)

        val secondTemp = ChunkPaths.tempPath("s1", 0) + ".again"
        val second = protocol.open("s1", 0, secondTemp, 2L) { chunk -> FakeEncoder(chunk.tempPath, fileSystem) }

        val failed = second as PlaceholderOutcome.Failed
        assertEquals(PlaceholderStage.ALLOCATE, failed.stage)
        assertNull(failed.chunk)
    }
}
