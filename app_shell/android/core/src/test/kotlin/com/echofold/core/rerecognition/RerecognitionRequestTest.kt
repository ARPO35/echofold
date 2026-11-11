package com.echofold.core.rerecognition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** [RerecognitionRequest] 构造期不变量：区间与有序列表。 */
class RerecognitionRequestTest {
    @Test
    fun `合法请求保留有序列表与区间`() {
        val request = Fixtures.request(
            sourceFinalIds = listOf("f2", "f1"),
            chunkIds = listOf("chunk-9", "chunk-10"),
            capturedStartMs = 120,
            capturedEndMs = 1_360,
            generation = 7,
        )

        assertEquals(listOf("f2", "f1"), request.sourceFinalIds)
        assertEquals(listOf("chunk-9", "chunk-10"), request.chunkIds)
        assertEquals(120L, request.capturedStartMs)
        assertEquals(1_360L, request.capturedEndMs)
        assertEquals(7L, request.generation)
        assertEquals(Fixtures.MODEL, request.model)
        assertEquals(Fixtures.CONFIG_HASH, request.asrConfigHash)
    }

    @Test
    fun `GAP 段允许空来源 FINAL 但必须有音频块`() {
        val request = Fixtures.request(sourceFinalIds = emptyList(), chunkIds = listOf("chunk-1"))

        assertEquals(emptyList<String>(), request.sourceFinalIds)
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(sourceFinalIds = emptyList(), chunkIds = emptyList())
        }
    }

    @Test
    fun `区间终点早于起点时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(capturedStartMs = 500, capturedEndMs = 499)
        }
    }

    @Test
    fun `负区间或负世代拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(capturedStartMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(generation = -1)
        }
    }

    @Test
    fun `来源 FINAL 重复或空白时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(sourceFinalIds = listOf("f1", "f1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(sourceFinalIds = listOf("f1", " "))
        }
    }

    @Test
    fun `音频块重复或空白时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(chunkIds = listOf("chunk-1", "chunk-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Fixtures.request(chunkIds = listOf(""))
        }
    }

    @Test
    fun `空白段或会话或配置哈希时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { Fixtures.request(segmentId = " ") }
        assertThrows(IllegalArgumentException::class.java) { Fixtures.request(sessionId = "") }
        assertThrows(IllegalArgumentException::class.java) { Fixtures.request(asrConfigHash = " ") }
    }
}
