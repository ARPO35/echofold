package com.echofold.core.asr

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `AsrEvent` 的 JSON 契约：camelCase 字段、kind 判别、往返一致与派生 eventId。 */
class AsrEventJsonTest {
    private val partial = AsrEvent.Partial(
        sessionId = "session-1",
        generation = 0L,
        seq = 1L,
        text = "你好",
        startMs = 0L,
        endMs = 480L,
        modelId = "x-asr-480ms-int8",
        modelVersion = "1.2.0",
    )

    private val candidate = AsrEvent.FinalCandidate(
        sessionId = "session-1",
        generation = 0L,
        seq = 2L,
        text = "你好世界",
        stablePrefixChars = 2,
        startMs = 0L,
        endMs = 1_200L,
    )

    private val final = AsrEvent.Final(
        sessionId = "session-1",
        generation = 2L,
        seq = 7L,
        finalId = "final-7",
        text = "这是最终结果。",
        finalizedAtUtcMs = 1_700_000_000_000L,
        captureStartedMonoNs = 1_000_000_000L,
        captureEndedMonoNs = 2_500_000_000L,
        capturedStartMs = 1_500L,
        capturedEndMs = 3_000L,
        chunkIds = listOf("chunk-0", "chunk-1"),
    )

    private val health = AsrEvent.Health(
        sessionId = "session-1",
        generation = 2L,
        seq = 8L,
        rtf = 0.42,
        oldestUnprocessedAgeMs = 3_000L,
        threadCount = 2,
        memoryBytes = 104_857_600L,
        skipCount = 1L,
        queue = QueueMetrics(
            capacity = 512,
            depth = 128,
            highWatermark = 256,
            oldestEnqueuedMonoNs = 1_000_000_000L,
            newestEnqueuedMonoNs = 4_000_000_000L,
            rejectedCount = 0L,
            blocked = false,
        ),
    )

    @Test
    fun `事件 ID 由会话世代类型与序号派生`() {
        assertEquals("session-1:0:partial:1", partial.eventId)
        assertEquals("session-1:0:finalCandidate:2", candidate.eventId)
        assertEquals("session-1:2:final:7", final.eventId)
        assertEquals("session-1:2:health:8", health.eventId)
        assertEquals(AsrEventKind.PARTIAL, partial.kind)
        assertEquals(AsrEventKind.FINAL_CANDIDATE, candidate.kind)
        assertEquals(AsrEventKind.FINAL, final.kind)
        assertEquals(AsrEventKind.HEALTH, health.kind)
    }

    @Test
    fun `JSON 使用 camelCase 字段与 kind 判别`() {
        val json = AsrEventJson.encode(final)

        assertTrue(json.contains("\"kind\":\"final\""), json)
        assertTrue(json.contains("\"eventId\":\"session-1:2:final:7\""), json)
        assertTrue(json.contains("\"finalizedAtUtcMs\":1700000000000"), json)
        assertTrue(json.contains("\"captureStartedMonoNs\":1000000000"), json)
        assertTrue(json.contains("\"capturedStartMs\":1500"), json)
        assertTrue(json.contains("\"chunkIds\":[\"chunk-0\",\"chunk-1\"]"), json)
        assertFalse(json.contains("event_id"), json)
    }

    @Test
    fun `四种事件 JSON 往返一致`() {
        val events = listOf(partial, candidate, final, health)
        for (event in events) {
            assertEquals(event, AsrEventJson.decode(AsrEventJson.encode(event)), event::class.simpleName)
        }
    }

    @Test
    fun `Partial 允许空文字候选不允许稳定前缀越界`() {
        assertEquals("", partial.copy(text = "").text)
        assertThrows(IllegalArgumentException::class.java) { candidate.copy(stablePrefixChars = 99) }
    }

    @Test
    fun `FINAL 拒绝空白文字与重复块引用`() {
        assertThrows(IllegalArgumentException::class.java) { final.copy(text = " ") }
        assertThrows(IllegalArgumentException::class.java) {
            final.copy(chunkIds = listOf("chunk-0", "chunk-0"))
        }
        assertThrows(IllegalArgumentException::class.java) { final.copy(chunkIds = emptyList()) }
    }

    @Test
    fun `事件 ID 不能与派生值不一致`() {
        assertThrows(IllegalArgumentException::class.java) { partial.copy(seq = 5L) }
        assertThrows(IllegalArgumentException::class.java) {
            AsrEvent.Partial(
                sessionId = "session-1",
                generation = 0L,
                seq = 1L,
                text = "你好",
                startMs = 0L,
                endMs = 480L,
                modelId = "x-asr-480ms-int8",
                modelVersion = "1.2.0",
                eventId = "session-1:0:partial:99",
            )
        }
    }

    @Test
    fun `反序列化拒绝未知事件类型`() {
        val corrupt = AsrEventJson.encode(partial).replace("\"kind\":\"partial\"", "\"kind\":\"unknown\"")
        assertThrows(SerializationException::class.java) { AsrEventJson.decode(corrupt) }
    }

    @Test
    fun `健康事件要求队列指标自洽`() {
        assertThrows(IllegalArgumentException::class.java) {
            health.queue.copy(depth = 1_024)
        }
        assertThrows(IllegalArgumentException::class.java) {
            health.queue.copy(depth = 0)
        }
        assertThrows(IllegalArgumentException::class.java) { health.copy(threadCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { health.copy(rtf = -0.1) }
    }

    @Test
    fun `通道 Map 与 JSON 字段一一对应`() {
        val channelMap = AsrEventJson.toChannelMap(health)
        val jsonKeys = AsrEventJson.format
            .parseToJsonElement(AsrEventJson.encode(health))
            .jsonObject
            .keys

        assertEquals(jsonKeys, channelMap.keys)
        assertEquals("session-1", channelMap["sessionId"])
        assertEquals(2L, channelMap["generation"])
        assertEquals("session-1:2:health:8", channelMap["eventId"])
        assertEquals("health", channelMap["kind"])
        @Suppress("UNCHECKED_CAST")
        val queue = channelMap["queue"] as Map<String, Any?>
        // JSON 只区分“整数/小数”，整数统一还原为 Long（Dart 侧即 int）。
        assertEquals(512L, queue["capacity"])
        assertEquals(128L, queue["depth"])
        assertEquals(256L, queue["highWatermark"])
        assertEquals(1_000_000_000L, queue["oldestEnqueuedMonoNs"])
        assertEquals(4_000_000_000L, queue["newestEnqueuedMonoNs"])
        assertEquals(0L, queue["rejectedCount"])
        assertEquals(false, queue["blocked"])
        assertEquals(null, channelMap["errorCode"])
    }

    @Test
    fun `通道 Map 中 FINAL 的块引用保持顺序`() {
        val channelMap = AsrEventJson.toChannelMap(final)

        assertEquals(listOf("chunk-0", "chunk-1"), channelMap["chunkIds"])
        assertEquals("final", channelMap["kind"])
        assertEquals("final-7", channelMap["finalId"])
    }
}
