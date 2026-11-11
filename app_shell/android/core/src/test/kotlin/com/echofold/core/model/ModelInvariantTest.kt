package com.echofold.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/** core 模型不变量：合法构造必须成功，非法构造必须抛 IllegalArgumentException。 */
class ModelInvariantTest {
    private fun anchor(sessionId: String = "session-1") = ClockAnchor(
        sessionId = sessionId,
        utcMs = 1_700_000_000_000L,
        monoNs = 1_000_000_000L,
        quality = ClockQuality.DEVICE_BOOT_BASED,
    )

    private fun session(
        id: String = "session-1",
        modelId: String? = "x-asr-480ms-int8",
        modelVersion: String? = "1.2.0",
    ) = RecordingSession(
        id = id,
        status = SessionStatus.RECORDING,
        dispatchState = DispatchState.RUNNING,
        createdAtUtcMs = 1_700_000_000_000L,
        modelId = modelId,
        modelVersion = modelVersion,
        installationId = if (modelId == null) null else "install-1",
        asrConfigHash = if (modelId == null) null else "config-hash",
        activeChunkSeq = 0,
        clockAnchor = anchor(id),
    )

    private fun openChunk(
        state: ChunkState = ChunkState.OPEN,
        finalPath: String? = null,
        sealedAtUtcMs: Long? = null,
        checksum: String? = null,
    ) = AudioChunk(
        id = "chunk-1",
        sessionId = "session-1",
        chunkSeq = 0,
        state = state,
        tempPath = "tmp/chunk-0.m4a.part",
        finalPath = finalPath,
        createdAtUtcMs = 1_700_000_000_000L,
        sealedAtUtcMs = sealedAtUtcMs,
        captureStartedMonoNs = 1_000_000_000L,
        captureEndedMonoNs = 2_000_000_000L,
        capturedStartMs = 0L,
        capturedEndMs = 1_000L,
        codec = "audio/mp4a-latm",
        sampleRate = 48_000,
        bitRate = 192_000,
        durationMs = 60_000L,
        checksum = checksum,
        failureStage = null,
        resourceEpoch = 1L,
    )

    private fun textSegment(sourceFinalIds: List<String> = listOf("final-1")) = TranscriptSegment(
        id = "segment-1",
        sessionId = "session-1",
        kind = SegmentKind.TEXT,
        sourceFinalIds = sourceFinalIds,
        finalizedAtUtcMs = 1_700_000_001_000L,
        foldDueAtUtcMs = 1_700_000_046_000L,
        captureStartedMonoNs = 1_000_000_000L,
        captureEndedMonoNs = 2_000_000_000L,
        capturedStartMs = 0L,
        capturedEndMs = 1_000L,
        gapReason = null,
    )

    private fun gapSegment(
        sourceFinalIds: List<String> = emptyList(),
        foldDueAtUtcMs: Long? = null,
        reason: GapReason? = GapReason.ASR_OVERLOAD,
    ) = TranscriptSegment(
        id = "segment-gap",
        sessionId = "session-1",
        kind = SegmentKind.GAP,
        sourceFinalIds = sourceFinalIds,
        finalizedAtUtcMs = 1_700_000_001_000L,
        foldDueAtUtcMs = foldDueAtUtcMs,
        captureStartedMonoNs = 1_000_000_000L,
        captureEndedMonoNs = 2_000_000_000L,
        capturedStartMs = 0L,
        capturedEndMs = 15_001L,
        gapReason = reason,
    )

    @Test
    fun `合法模型可以构造`() {
        assertDoesNotThrow { anchor() }
        assertDoesNotThrow { session() }
        assertDoesNotThrow { session(modelId = null, modelVersion = null) }
        assertDoesNotThrow { openChunk() }
        assertDoesNotThrow {
            openChunk(
                state = ChunkState.SEALED,
                finalPath = "audio/chunk-0.m4a",
                sealedAtUtcMs = 1_700_000_060_000L,
                checksum = "abc123",
            )
        }
        assertDoesNotThrow { textSegment() }
        assertDoesNotThrow { gapSegment() }
        assertDoesNotThrow {
            TranscriptVersion(
                id = "version-1",
                segmentId = "segment-1",
                kind = VersionKind.ASR,
                text = "你好",
                parentVersionId = null,
                modelId = "x-asr-480ms-int8",
                modelVersion = "1.2.0",
                finalizedAtUtcMs = 1_700_000_001_000L,
                createdAtUtcMs = 1_700_000_002_000L,
                captureStartMs = 0L,
                captureEndMs = 1_000L,
            )
        }
    }

    @Test
    fun `会话模型版本必须成对出现`() {
        assertThrows(IllegalArgumentException::class.java) {
            session(modelId = "x-asr-480ms-int8", modelVersion = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            session(modelId = null, modelVersion = "1.2.0")
        }
    }

    @Test
    fun `时钟锚点必须属于同一会话`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingSession(
                id = "session-1",
                status = SessionStatus.IDLE,
                dispatchState = DispatchState.RUNNING,
                createdAtUtcMs = 1_700_000_000_000L,
                modelId = null,
                modelVersion = null,
                installationId = null,
                asrConfigHash = null,
                activeChunkSeq = 0,
                clockAnchor = anchor("session-2"),
            )
        }
    }

    @Test
    fun `活动块序号不能为负数`() {
        assertThrows(IllegalArgumentException::class.java) {
            session().copy(activeChunkSeq = -1)
        }
    }

    @Test
    fun `PLACEHOLDER 块不得携带封存信息`() {
        assertThrows(IllegalArgumentException::class.java) {
            openChunk(state = ChunkState.PLACEHOLDER, finalPath = "audio/chunk-0.m4a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            openChunk(state = ChunkState.PLACEHOLDER, sealedAtUtcMs = 1L)
        }
    }

    @Test
    fun `SEALED 块必须携带封存时间最终路径与校验和`() {
        assertThrows(IllegalArgumentException::class.java) {
            openChunk(
                state = ChunkState.SEALED,
                finalPath = "audio/chunk-0.m4a",
                sealedAtUtcMs = 1L,
                checksum = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            openChunk(state = ChunkState.SEALED, finalPath = null, sealedAtUtcMs = 1L, checksum = "abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            openChunk(state = ChunkState.SEALED, finalPath = "audio/chunk-0.m4a", sealedAtUtcMs = null, checksum = "abc")
        }
    }

    @Test
    fun `音频区间终点不能早于起点`() {
        assertThrows(IllegalArgumentException::class.java) {
            openChunk().copy(capturedStartMs = 2_000L, capturedEndMs = 1_000L)
        }
    }

    @Test
    fun `TEXT 段必须有有序且不重复的来源`() {
        assertThrows(IllegalArgumentException::class.java) { textSegment(sourceFinalIds = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            textSegment(sourceFinalIds = listOf("final-1", "final-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            textSegment(sourceFinalIds = listOf("final-1", " "))
        }
    }

    @Test
    fun `TEXT 段不得携带缺口原因`() {
        assertThrows(IllegalArgumentException::class.java) {
            textSegment().copy(gapReason = GapReason.ASR_OVERLOAD)
        }
    }

    @Test
    fun `GAP 段不引用 FINAL 且无折叠到期时间`() {
        assertThrows(IllegalArgumentException::class.java) {
            gapSegment(sourceFinalIds = listOf("final-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            gapSegment(foldDueAtUtcMs = 1_700_000_046_000L)
        }
        assertThrows(IllegalArgumentException::class.java) { gapSegment(reason = null) }
    }

    @Test
    fun `GAP 展示文本恒为字面量省略号`() {
        assertEquals("...", TranscriptSegment.GAP_DISPLAY_TEXT)
    }

    @Test
    fun `来源集合去重禁止且必须非空`() {
        val ref = SourceVersionRef("segment-1", "version-1", SourceKind.FINAL)
        assertThrows(IllegalArgumentException::class.java) { SourceSet(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { SourceSet(listOf(ref, ref)) }
        // 同一来源的不同版本同样非法：sourceId 是来源身份。
        assertThrows(IllegalArgumentException::class.java) {
            SourceSet(listOf(ref, SourceVersionRef("segment-1", "version-2", SourceKind.FINAL)))
        }
    }

    @Test
    fun `来源引用字段不能为空白`() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceVersionRef(" ", "version-1", SourceKind.AI)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceVersionRef("segment-1", "", SourceKind.AI)
        }
    }

    @Test
    fun `版本父指针不能指向自身`() {
        val version = TranscriptVersion(
            id = "version-1",
            segmentId = "segment-1",
            kind = VersionKind.USER,
            text = "校订后文字",
            parentVersionId = null,
            modelId = null,
            modelVersion = null,
            finalizedAtUtcMs = 0L,
            createdAtUtcMs = 1L,
            captureStartMs = 0L,
            captureEndMs = 1L,
        )
        assertThrows(IllegalArgumentException::class.java) { version.copy(parentVersionId = "version-1") }
    }
}
