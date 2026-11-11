package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import com.echofold.core.model.DispatchState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

/**
 * 换块状态机测试：并发合并、序号单调、失败不回退、同会话最多一个活动块。
 */
class ChunkSwitcherTest {

    private val repo = InMemoryChunkRepository()
    private val fileSystem = FakeFileSystem()
    private val encoders = mutableListOf<FakeEncoder>()

    private fun newSwitcher(
        budget: SpaceBudget? = null,
        reserveBytes: Long = 0L,
        encoderFactory: ((AudioChunk) -> ChunkEncoderHandle)? = null,
    ): ChunkSwitcher = ChunkSwitcher(
        sessionId = "s1",
        repository = repo,
        fileSystem = fileSystem,
        encoderFactory = encoderFactory ?: { chunk ->
            FakeEncoder(chunk.tempPath, fileSystem).also { encoders += it }
        },
        budget = budget,
        reserveBytes = reserveBytes,
        resourceEpoch = { 3L },
        nowUtcMs = { 1_000L },
    )

    @Test
    fun `首次换块建立 OPEN 块并恢复分发`() = runTest {
        val switcher = newSwitcher()

        val opened = switcher.switchToNextChunk() as SwitchOutcome.Opened

        assertEquals(0, opened.chunk.chunkSeq)
        assertFalse(opened.mergedIntoExisting)
        assertEquals(ChunkState.OPEN, opened.chunk.state)
        assertEquals(3L, opened.chunk.resourceEpoch)
        assertEquals(DispatchState.RUNNING, switcher.dispatchState)
        assertEquals(ChunkState.OPEN, repo.findActive("s1")?.state)
        assertEquals(1, repo.listBySession("s1").size)
        assertEquals(1, switcher.nextChunkSeq)

        switcher.write(ByteArray(8), 0)
        assertEquals(listOf(0L), encoders.single().writtenFrames)
    }

    @Test
    fun `换块先封存旧块再开新块`() = runTest {
        val switcher = newSwitcher()
        val first = switcher.switchToNextChunk() as SwitchOutcome.Opened
        switcher.write(ByteArray(8), 0)

        val second = switcher.switchToNextChunk() as SwitchOutcome.Opened

        assertEquals(1, second.chunk.chunkSeq)
        assertEquals(ChunkState.SEALED, repo.find(first.chunk.id)?.state)
        assertEquals(listOf(0, 1), repo.listBySession("s1").map { it.chunkSeq })
        assertEquals(ChunkState.OPEN, repo.findActive("s1")?.state)
        assertEquals(1, repo.listBySession("s1").count { it.state == ChunkState.OPEN })
        assertEquals(DispatchState.RUNNING, switcher.dispatchState)
    }

    @Test
    fun `ENSURE 合并到已有 OPEN 块且不重复分配`() = runTest {
        val switcher = newSwitcher()
        val first = switcher.switchToNextChunk() as SwitchOutcome.Opened

        val second = switcher.ensureOpenChunk() as SwitchOutcome.Opened

        assertTrue(second.mergedIntoExisting)
        assertEquals(first.chunk.id, second.chunk.id)
        assertEquals(1, repo.calls.count { it.startsWith("allocatePlaceholder") })
        assertEquals(1, repo.listBySession("s1").size)
    }

    @Test
    fun `重入换块请求合并到进行中的换块且不重复分配`() = runTest {
        lateinit var switcher: ChunkSwitcher
        var nested: SwitchOutcome? = null
        switcher = newSwitcher(encoderFactory = { chunk ->
            // 编码器工厂依赖换块结果时再次请求换块：不得自锁，也不得产生第二个块。
            nested = runBlocking { switcher.switchToNextChunk() }
            FakeEncoder(chunk.tempPath, fileSystem).also { encoders += it }
        })

        val outcome = switcher.switchToNextChunk()

        assertTrue(outcome is SwitchOutcome.Opened)
        assertEquals(SwitchOutcome.MergedIntoInFlight, nested)
        assertEquals(1, repo.calls.count { it.startsWith("allocatePlaceholder") })
        assertEquals(1, repo.listBySession("s1").size)
    }

    @Test
    fun `并发换块请求合并为同一个结果`() {
        val expectedJoiners = 4
        lateinit var switcher: ChunkSwitcher
        switcher = newSwitcher(encoderFactory = { chunk ->
            // 等待其余并发请求全部并入本次换块，确保它们在同一个临界区内被合并。
            val deadline = System.nanoTime() + 5_000_000_000L
            while (switcher.coalescedRequestCount < expectedJoiners && System.nanoTime() < deadline) {
                Thread.sleep(1)
            }
            FakeEncoder(chunk.tempPath, fileSystem).also { encoders += it }
        })

        val results = arrayOfNulls<SwitchOutcome>(expectedJoiners + 1)
        val barrier = CyclicBarrier(expectedJoiners + 1)
        val threads = results.indices.map { index ->
            thread {
                barrier.await()
                results[index] = runBlocking { switcher.switchToNextChunk() }
            }
        }
        threads.forEach { it.join() }

        assertEquals(1, repo.calls.count { it.startsWith("allocatePlaceholder") })
        assertEquals(1, repo.listBySession("s1").size)
        assertEquals(1, repo.listBySession("s1").count { it.state == ChunkState.OPEN })
        val first = results[0]
        assertTrue(first is SwitchOutcome.Opened)
        for (index in 1..expectedJoiners) {
            assertSame(first, results[index], "第 $index 个并发请求必须得到同一结果")
        }
        assertEquals(expectedJoiners, switcher.coalescedRequestCount)
    }

    @Test
    fun `占位失败保持 BLOCKED 且序号不复用`() = runTest {
        val switcher = newSwitcher()
        switcher.switchToNextChunk()
        repo.failAllocate = IllegalStateException("唯一约束冲突")

        val failed = switcher.switchToNextChunk() as SwitchOutcome.PlaceholderFailed

        assertEquals(PlaceholderStage.ALLOCATE, failed.stage)
        assertEquals(DispatchState.BLOCKED, switcher.dispatchState)
        // 序号 1 已被消耗，不允许回退复用。
        assertEquals(2, switcher.nextChunkSeq)
        assertThrows<IllegalStateException> { switcher.write(ByteArray(2), 1) }

        val retry = switcher.switchToNextChunk() as SwitchOutcome.Opened

        assertEquals(2, retry.chunk.chunkSeq)
        assertEquals(3, switcher.nextChunkSeq)
        assertEquals(listOf(0, 2), repo.listBySession("s1").map { it.chunkSeq })
        assertEquals(DispatchState.RUNNING, switcher.dispatchState)
    }

    @Test
    fun `编码器初始化失败后不恢复分发且重试使用新序号`() = runTest {
        var failInit = true
        val switcher = newSwitcher(encoderFactory = { chunk ->
            FakeEncoder(chunk.tempPath, fileSystem).also { encoder ->
                if (failInit) encoder.initializeFailure = IllegalStateException("编码器初始化失败")
                encoders += encoder
            }
        })

        val failed = switcher.switchToNextChunk() as SwitchOutcome.PlaceholderFailed

        assertEquals(PlaceholderStage.INITIALIZE, failed.stage)
        assertEquals(DispatchState.BLOCKED, switcher.dispatchState)
        assertNull(repo.findActive("s1"))
        assertEquals(ChunkState.SEAL_FAILED, repo.find("s1-0")?.state)
        assertFalse(encoders.single().initialized)

        failInit = false
        val retry = switcher.switchToNextChunk() as SwitchOutcome.Opened

        assertEquals(1, retry.chunk.chunkSeq)
        assertEquals(DispatchState.RUNNING, switcher.dispatchState)
    }

    @Test
    fun `存在未封存块时拒绝开新块`() = runTest {
        repo.seed(testChunk(id = "s1-0", chunkSeq = 0, state = ChunkState.OPEN))
        val switcher = newSwitcher()

        val outcome = switcher.switchToNextChunk()

        assertTrue(outcome is SwitchOutcome.BlockedByUnsealedChunk)
        assertTrue(repo.calls.none { it.startsWith("allocatePlaceholder") })
        assertEquals(DispatchState.BLOCKED, switcher.dispatchState)
        assertEquals(1, switcher.nextChunkSeq)
    }

    @Test
    fun `低存储预留失败按顺序封存并暂停且不创建下一块`() = runTest {
        val budget = FixedSpaceBudget(capacity = 1)
        val switcher = newSwitcher(budget = budget, reserveBytes = 1)
        val first = switcher.switchToNextChunk() as SwitchOutcome.Opened
        switcher.write(ByteArray(8), 0)

        val outcome = switcher.switchToNextChunk()

        val paused = outcome as SwitchOutcome.LowStoragePaused
        assertEquals(first.chunk.id, paused.chunk?.id)
        assertEquals(ChunkState.SEALED, repo.find(first.chunk.id)?.state)
        assertEquals(DispatchState.BLOCKED, switcher.dispatchState)
        assertEquals(1, repo.calls.count { it.startsWith("allocatePlaceholder") })
        assertEquals(1, repo.listBySession("s1").size)
        assertTrue(budget.calls.contains("release(1)"))
        assertEquals(0L, budget.used)
        assertThrows<IllegalStateException> { switcher.write(ByteArray(2), 1) }
    }

    @Test
    fun `封存当前块后分发保持 BLOCKED 且拒绝写入`() = runTest {
        val switcher = newSwitcher()
        switcher.switchToNextChunk()
        switcher.write(ByteArray(4), 0)

        val outcome = switcher.sealCurrent()

        assertTrue(outcome is SealOutcome.Sealed)
        assertEquals(DispatchState.BLOCKED, switcher.dispatchState)
        assertThrows<IllegalStateException> { switcher.write(ByteArray(2), 1) }
        // 重复封存请求合并到同一结果，不产生第二次改名。
        assertSame(outcome, switcher.sealCurrent())
        assertEquals(1, fileSystem.calls.count { it.startsWith("rename(") })

        // 开新块后再次封存的是新块，不会重复封存历史证据。
        val next = switcher.switchToNextChunk() as SwitchOutcome.Opened
        val resealed = switcher.sealCurrent() as SealOutcome.Sealed
        assertEquals(next.chunk.id, resealed.chunk.id)
    }
}
