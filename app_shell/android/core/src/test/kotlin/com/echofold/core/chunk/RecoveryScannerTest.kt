package com.echofold.core.chunk

import com.echofold.core.model.ChunkState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 恢复扫描测试：三态映射（SEALED / RECOVERABLE / CORRUPT）、量化边界与序号不复用。
 */
class RecoveryScannerTest {

    private val fileSystem = FakeFileSystem()
    private val checksum = "c".repeat(64)

    private fun scanner(reports: Map<String, ProbeReport>): RecoveryScanner = RecoveryScanner(
        fileSystem = fileSystem,
        probe = FakeProbe(reports),
        recoveredAtUtcMs = { 77_000L },
        frameDurationMs = 20L,
    )

    @Test
    fun `最终文件完整时可补交 SEALED`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        val finalPath = ChunkPaths.finalPathOf(tempPath)
        fileSystem.put(finalPath, 900L)
        val chunk = testChunk(
            tempPath = tempPath,
            finalPath = finalPath,
            checksum = checksum,
            capturedStartMs = 0L,
            capturedEndMs = 1_000L,
        )

        val verdict = scanner(mapOf(finalPath to ProbeReport.Complete(1_000L, 900L, checksum))).scan(chunk)

        val sealed = verdict as RecoveryVerdict.Sealed
        assertTrue(sealed.needsCommit)
        assertEquals(ChunkState.SEALED, sealed.chunk.state)
        assertEquals(finalPath, sealed.chunk.finalPath)
        assertEquals(checksum, sealed.chunk.checksum)
        assertEquals(77_000L, sealed.chunk.sealedAtUtcMs)
        assertEquals(1_000L, sealed.chunk.durationMs)
    }

    @Test
    fun `临时文件完整时可补交 SEALED 并推导最终路径`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 640L)
        val chunk = testChunk(tempPath = tempPath, checksum = checksum)

        val verdict = scanner(mapOf(tempPath to ProbeReport.Complete(1_000L, 640L, checksum))).scan(chunk)

        val sealed = verdict as RecoveryVerdict.Sealed
        assertEquals(ChunkPaths.finalPathOf(tempPath), sealed.chunk.finalPath)
        assertEquals(ChunkState.SEALED, sealed.chunk.state)
        assertNull(sealed.chunk.failureStage)
    }

    @Test
    fun `仅可解码前缀映射为 RECOVERABLE 并量化到整帧`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 512L)
        val chunk = testChunk(tempPath = tempPath, capturedStartMs = 0L, capturedEndMs = 2_000L)

        val verdict = scanner(mapOf(tempPath to ProbeReport.Partial(1_234L, 512L))).scan(chunk)

        val recoverable = verdict as RecoveryVerdict.Recoverable
        assertEquals(ChunkState.RECOVERABLE, recoverable.chunk.state)
        assertEquals(1_234L, recoverable.boundary.rawPrefixMs)
        assertEquals(1_220L, recoverable.boundary.decodableEndMs)
        assertEquals(20L, recoverable.boundary.frameDurationMs)
        assertEquals(780L, recoverable.boundary.lossMs)
    }

    @Test
    fun `文件缺失映射为 CORRUPT`() {
        val chunk = testChunk(tempPath = ChunkPaths.tempPath("s1", 0))

        val verdict = scanner(emptyMap()).scan(chunk)

        val corrupt = verdict as RecoveryVerdict.Corrupt
        assertEquals(RecoveryReason.MISSING_FILE, corrupt.reason)
        assertEquals(ChunkState.CORRUPT, corrupt.chunk.state)
    }

    @Test
    fun `容器不可解析映射为 CORRUPT`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 128L)
        val chunk = testChunk(tempPath = tempPath)

        val verdict = scanner(mapOf(tempPath to ProbeReport.Unreadable("moov 截断"))).scan(chunk)

        assertEquals(RecoveryReason.UNREADABLE_CONTAINER, (verdict as RecoveryVerdict.Corrupt).reason)
    }

    @Test
    fun `字节数与实际文件尺寸不一致映射为 CORRUPT`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 100L)
        val chunk = testChunk(tempPath = tempPath)

        val verdict = scanner(mapOf(tempPath to ProbeReport.Complete(1_000L, 200L, checksum))).scan(chunk)

        assertEquals(RecoveryReason.SIZE_MISMATCH, (verdict as RecoveryVerdict.Corrupt).reason)
    }

    @Test
    fun `校验和与已登记值不一致映射为 CORRUPT`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 100L)
        val chunk = testChunk(tempPath = tempPath, checksum = "d".repeat(64))

        val verdict = scanner(mapOf(tempPath to ProbeReport.Complete(1_000L, 100L, checksum))).scan(chunk)

        assertEquals(RecoveryReason.CHECKSUM_MISMATCH, (verdict as RecoveryVerdict.Corrupt).reason)
    }

    @Test
    fun `容器时长与实际采集区间不一致映射为 CORRUPT`() {
        val tempPath = ChunkPaths.tempPath("s1", 0)
        fileSystem.put(tempPath, 100L)
        val chunk = testChunk(
            tempPath = tempPath,
            checksum = checksum,
            capturedStartMs = 0L,
            capturedEndMs = 1_000L,
        )

        val verdict = scanner(mapOf(tempPath to ProbeReport.Complete(5_000L, 100L, checksum))).scan(chunk)

        assertEquals(RecoveryReason.TIME_MAPPING_INCONSISTENT, (verdict as RecoveryVerdict.Corrupt).reason)
    }

    @Test
    fun `已 SEALED 的块幂等返回`() {
        val chunk = testChunk(
            state = ChunkState.SEALED,
            finalPath = ChunkPaths.finalPathOf(ChunkPaths.tempPath("s1", 0)),
            sealedAtUtcMs = 1L,
            checksum = checksum,
        )

        val verdict = scanner(emptyMap()).scan(chunk) as RecoveryVerdict.Sealed

        assertEquals(false, verdict.needsCommit)
        assertEquals(chunk, verdict.chunk)
    }

    @Test
    fun `序号永不复用`() {
        val chunks = listOf(
            testChunk(id = "s1-0", chunkSeq = 0, state = ChunkState.CORRUPT),
            testChunk(id = "s1-1", chunkSeq = 1, state = ChunkState.OPEN),
            testChunk(id = "s1-2", chunkSeq = 2, state = ChunkState.SEAL_FAILED),
        )

        assertEquals(3, RecoveryScanner.nextSeqAfter(chunks))
        assertEquals(0, RecoveryScanner.nextSeqAfter(emptyList()))
        // 即使已见块全部失败/损坏，其序号也已分配过：恢复不得让序号回退。
        assertEquals(3, RecoveryScanner.nextSeqAfter(chunks.filter { it.state != ChunkState.SEALED }))
    }
}
