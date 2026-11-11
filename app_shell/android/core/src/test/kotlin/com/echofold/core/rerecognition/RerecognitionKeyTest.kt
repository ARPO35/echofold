package com.echofold.core.rerecognition

import com.echofold.core.model.sourceFinalIdsHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** [RerecognitionKey] 的稳定性、顺序敏感性与空来源处理。 */
class RerecognitionKeyTest {
    @Test
    fun `同输入生成相同键与稳定 ID`() {
        val first = RerecognitionKey.of(Fixtures.request())
        val second = RerecognitionKey.of(Fixtures.request())

        assertEquals(first, second)
        assertEquals(first.stableId, second.stableId)
        assertEquals(64, first.stableId.length)
        assertEquals(first.stableId, first.stableId.lowercase())
    }

    @Test
    fun `音频块与世代不参与键`() {
        val base = RerecognitionKey.of(Fixtures.request())
        val otherChunks = RerecognitionKey.of(
            Fixtures.request(chunkIds = listOf("chunk-7"), generation = 9),
        )

        assertEquals(base, otherChunks)
        assertEquals(base.stableId, otherChunks.stableId)
    }

    @Test
    fun `区间或模型版本变化产生不同键`() {
        val base = RerecognitionKey.of(Fixtures.request())

        assertNotEquals(base, RerecognitionKey.of(Fixtures.request(capturedEndMs = 1_001)))
        assertNotEquals(base, RerecognitionKey.of(Fixtures.request(model = Fixtures.OTHER_MODEL)))
        assertNotEquals(base.stableId, RerecognitionKey.of(Fixtures.request(capturedStartMs = 1)).stableId)
    }

    @Test
    fun `来源 FINAL 顺序参与键`() {
        val forward = RerecognitionKey.of(Fixtures.request(sourceFinalIds = listOf("f1", "f2")))
        val backward = RerecognitionKey.of(Fixtures.request(sourceFinalIds = listOf("f2", "f1")))

        assertEquals(sourceFinalIdsHash(listOf("f1", "f2")), forward.sourceFinalIdsHash)
        assertNotEquals(forward.sourceFinalIdsHash, backward.sourceFinalIdsHash)
        assertNotEquals(forward.stableId, backward.stableId)
    }

    @Test
    fun `空来源使用空数组规范哈希且稳定`() {
        val gapKey = RerecognitionKey.of(Fixtures.request(sourceFinalIds = emptyList()))
        val other = RerecognitionKey.of(
            Fixtures.request(segmentId = "seg-gap", sourceFinalIds = emptyList()),
        )

        assertEquals(RerecognitionKey.EMPTY_SOURCE_FINAL_IDS_HASH, gapKey.sourceFinalIdsHash)
        assertEquals(gapKey.sourceFinalIdsHash, other.sourceFinalIdsHash)
        assertNotEquals(
            RerecognitionKey.EMPTY_SOURCE_FINAL_IDS_HASH,
            sourceFinalIdsHash(listOf("f1")),
        )
        assertEquals(64, RerecognitionKey.EMPTY_SOURCE_FINAL_IDS_HASH.length)
    }

    @Test
    fun `空白字段与非法区间拒绝构造`() {
        assertThrows(IllegalArgumentException::class.java) {
            RerecognitionKey(
                segmentId = " ",
                capturedStartMs = 0,
                capturedEndMs = 1,
                modelId = Fixtures.MODEL.modelId,
                modelVersion = Fixtures.MODEL.modelVersion,
                sourceFinalIdsHash = RerecognitionKey.EMPTY_SOURCE_FINAL_IDS_HASH,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RerecognitionKey(
                segmentId = "seg-1",
                capturedStartMs = 10,
                capturedEndMs = 9,
                modelId = Fixtures.MODEL.modelId,
                modelVersion = Fixtures.MODEL.modelVersion,
                sourceFinalIdsHash = RerecognitionKey.EMPTY_SOURCE_FINAL_IDS_HASH,
            )
        }
    }
}
