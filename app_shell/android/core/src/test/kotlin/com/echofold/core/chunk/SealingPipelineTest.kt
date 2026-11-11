package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/**
 * 封存流水线测试：`OPEN → SEALING → SEALED` 的每个断点都必须可观察，失败必须保留临时文件。
 */
class SealingPipelineTest {

    private val repo = InMemoryChunkRepository()
    private val fileSystem = FakeFileSystem()
    private val nowUtcMs = { 9_000L }

    private fun openChunk(): AudioChunk {
        val chunk = testChunk(tempPath = ChunkPaths.tempPath("s1", 0))
        repo.seed(chunk)
        fileSystem.put(chunk.tempPath, 0L)
        return chunk
    }

    private fun pipeline(
        chunk: AudioChunk,
        encoder: FakeEncoder,
        drainPending: () -> Unit = {},
    ): SealingPipeline = SealingPipeline(
        chunk = chunk,
        repository = repo,
        fileSystem = fileSystem,
        encoder = encoder,
        drainPending = drainPending,
        nowUtcMs = nowUtcMs,
    )

    @Test
    fun `正常封存先落 SEALING 再原子改名提交 SEALED`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)

        sealing.write(ByteArray(16), 0)
        sealing.write(ByteArray(8), 1)
        assertEquals(ChunkState.SEALING, sealing.beginSealing().state)
        assertEquals(ChunkState.SEALING, repo.find(chunk.id)?.state)

        val outcome = sealing.seal()
        val sealed = (outcome as SealOutcome.Sealed).chunk

        assertEquals(ChunkState.SEALED, sealed.state)
        assertEquals(ChunkPaths.finalPathOf(chunk.tempPath), sealed.finalPath)
        assertEquals("a".repeat(64), sealed.checksum)
        assertEquals(9_000L, sealed.sealedAtUtcMs)
        assertEquals(1_000L, sealed.durationMs)
        assertNull(sealed.failureStage)
        assertTrue(encoder.finished)
        assertEquals(listOf(0L, 1L), encoder.writtenFrames)
        assertTrue(fileSystem.exists(sealed.finalPath!!))
        assertFalse(fileSystem.exists(chunk.tempPath))
        assertEquals(ChunkState.SEALED, repo.find(chunk.id)?.state)
    }

    @Test
    fun `SEALING 期间写入抛 IllegalStateException`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)

        sealing.beginSealing()

        val error = assertThrows<IllegalStateException> { sealing.write(ByteArray(4), 7) }
        assertTrue(error.message!!.contains("SEALING"))
        assertTrue(encoder.writtenFrames.isEmpty())
    }

    @Test
    fun `原子改名失败保留临时文件并记录 RENAME 阶段`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)
        sealing.write(ByteArray(10), 0)
        fileSystem.failRename = IOException("跨卷改名被拒绝")

        val outcome = sealing.seal()

        val failed = outcome as SealOutcome.Failed
        assertEquals(SealStage.RENAME, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk.state)
        assertEquals(SealStage.RENAME.name, failed.chunk.failureStage)
        assertEquals(7L, failed.resourceEpoch)
        assertTrue(fileSystem.exists(chunk.tempPath))
        assertEquals(ChunkState.SEAL_FAILED, repo.find(chunk.id)?.state)
    }

    @Test
    fun `尺寸与编码器报告不一致时不得提交 SEALED`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem) { written ->
            FakeEncoder.defaultSealResult(written + 5)
        }
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)
        sealing.write(ByteArray(10), 0)

        val failed = sealing.seal() as SealOutcome.Failed

        assertEquals(SealStage.VERIFY, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk.state)
        assertTrue(fileSystem.exists(chunk.tempPath))
        assertEquals(10L, fileSystem.size(chunk.tempPath))
    }

    @Test
    fun `校验和格式非法时不得提交 SEALED`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem) { written ->
            FakeEncoder.defaultSealResult(written).copy(checksum = "NOT-A-CHECKSUM")
        }
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)

        val failed = sealing.seal() as SealOutcome.Failed

        assertEquals(SealStage.VERIFY, failed.stage)
        assertEquals(SealStage.VERIFY.name, failed.chunk.failureStage)
    }

    @Test
    fun `已登记校验和与落盘结果不一致时失败`() {
        val seed = openChunk()
        val chunk = seed.copy(checksum = "b".repeat(64))
        repo.seed(chunk)
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)

        val failed = sealing.seal() as SealOutcome.Failed

        assertEquals(SealStage.VERIFY, failed.stage)
        assertTrue(failed.cause!!.message!!.contains("校验和"))
    }

    @Test
    fun `容器结束失败时保留临时文件并记录 FINISH 阶段`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        encoder.finishFailure = IOException("fd 已失效")
        val sealing = pipeline(chunk, encoder)
        sealing.write(ByteArray(12), 0)

        val failed = sealing.seal() as SealOutcome.Failed

        assertEquals(SealStage.FINISH, failed.stage)
        assertEquals(ChunkState.SEAL_FAILED, failed.chunk.state)
        assertTrue(fileSystem.exists(chunk.tempPath))
    }

    @Test
    fun `排空失败时记录 DRAIN 阶段`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder) { throw IllegalStateException("队列未能排空") }

        val failed = sealing.seal() as SealOutcome.Failed

        assertEquals(SealStage.DRAIN, failed.stage)
        assertEquals(SealStage.DRAIN.name, failed.chunk.failureStage)
    }

    @Test
    fun `封存幂等并且只改名一次`() {
        val chunk = openChunk()
        val encoder = FakeEncoder(chunk.tempPath, fileSystem)
        encoder.initialize()
        val sealing = pipeline(chunk, encoder)

        val first = sealing.seal()
        val second = sealing.seal()

        assertSame(first, second)
        assertEquals(1, fileSystem.calls.count { it.startsWith("rename(") })
        assertEquals(ChunkState.SEALED, sealing.snapshot.state)
    }

    @Test
    fun `非 OPEN 块无法接管流水线`() {
        val sealed = testChunk(state = ChunkState.SEALED, finalPath = "/x.m4a", sealedAtUtcMs = 1L, checksum = "a".repeat(64))
        val encoder = FakeEncoder(sealed.tempPath, fileSystem)

        assertThrows<IllegalArgumentException> { pipeline(sealed, encoder) }
    }
}
