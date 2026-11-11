package com.echofold.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** `sourceSetHash` / `sourceFinalIdsHash` 的规范性与顺序敏感性。 */
class SourceSetHashTest {
    private val finalRef = SourceVersionRef("seg-1", "v-1", SourceKind.FINAL)
    private val aiRef = SourceVersionRef("seg-2", "v-2", SourceKind.AI)

    @Test
    fun `规范 JSON 键顺序与空白固定`() {
        val sourceSet = SourceSet(listOf(finalRef, aiRef))

        assertEquals(SOURCE_SET_SCHEMA, "echofold.source-set.v1")
        assertEquals(
            "{\"schema\":\"echofold.source-set.v1\",\"sources\":[" +
                "{\"sourceId\":\"seg-1\",\"versionId\":\"v-1\",\"sourceKind\":\"FINAL\"}," +
                "{\"sourceId\":\"seg-2\",\"versionId\":\"v-2\",\"sourceKind\":\"AI\"}]}",
            sourceSet.canonicalJson,
        )
    }

    @Test
    fun `已知黄金哈希保持不变`() {
        // 黄金值由上面的规范 JSON 原文做 SHA-256 得到；改动规范格式必须同步更新并说明理由。
        assertEquals(
            "91957793597545bd91d439dd0fa97c64b27c897a9041a960c860519c9f373f1d",
            SourceSet(listOf(finalRef, aiRef)).sourceSetHash,
        )
        assertEquals(
            "a82d3c0dc71d65761ee8ad822deba6fff5b1ba2a5f7d365574a9caee21fa36c6",
            SourceSet(listOf(aiRef, finalRef)).sourceSetHash,
        )
    }

    @Test
    fun `数组顺序参与哈希`() {
        val forward = SourceSet(listOf(finalRef, aiRef))
        val backward = SourceSet(listOf(aiRef, finalRef))

        assertNotEquals(forward.sourceSetHash, backward.sourceSetHash)
        assertEquals(forward.canonicalJson.length, backward.canonicalJson.length)
    }

    @Test
    fun `版本变化改变哈希`() {
        val original = SourceSet(listOf(finalRef))
        val reversioned = SourceSet(listOf(finalRef.copy(versionId = "v-9")))

        assertNotEquals(original.sourceSetHash, reversioned.sourceSetHash)
    }

    @Test
    fun `字符串按 JSON 规则转义`() {
        val escaped = SourceSet(
            listOf(SourceVersionRef("seg\"1", "v\\1", SourceKind.GAP)),
        )

        assertEquals(
            "{\"schema\":\"echofold.source-set.v1\",\"sources\":[" +
                "{\"sourceId\":\"seg\\\"1\",\"versionId\":\"v\\\\1\",\"sourceKind\":\"GAP\"}]}",
            escaped.canonicalJson,
        )
        assertEquals(64, escaped.sourceSetHash.length)
        assertEquals(escaped.sourceSetHash, escaped.sourceSetHash.lowercase())
    }

    @Test
    fun `sourceFinalIdsHash 保持顺序并拒绝重复`() {
        assertEquals(
            "8a65658cb71da1cec21da2bad7c3fd91d4dc46a0a617227533cecab81f876953",
            sourceFinalIdsHash(listOf("final-1", "final-2")),
        )
        assertNotEquals(
            sourceFinalIdsHash(listOf("final-1", "final-2")),
            sourceFinalIdsHash(listOf("final-2", "final-1")),
        )
        assertThrows(IllegalArgumentException::class.java) { sourceFinalIdsHash(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            sourceFinalIdsHash(listOf("final-1", "final-1"))
        }
    }

    @Test
    fun `规范 JSON 只使用 UTF-8 原文字符`() {
        val chinese = SourceSet(listOf(SourceVersionRef("段\"一", "版本一", SourceKind.USER)))

        assertEquals(true, chinese.canonicalJson.contains("段\\\"一"))
        assertEquals(true, chinese.canonicalJson.contains("版本一"))
        assertEquals(64, chinese.sourceSetHash.length)
    }
}
