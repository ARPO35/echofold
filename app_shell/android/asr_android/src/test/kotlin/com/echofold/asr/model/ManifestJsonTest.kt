package com.echofold.asr.model

import com.echofold.asr.testing.TestKey
import com.echofold.asr.testing.testManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ManifestJsonTest {
    private val key = TestKey("test-key-1", 1)

    @Test
    fun `解析合法信封并保留全部字段`() {
        val manifest = testManifest()
        val envelope = ManifestJson.parseEnvelope(key.signEnvelope(manifest))

        assertEquals(manifest, envelope.manifest)
        assertEquals("test-key-1", envelope.signature.keyId)
        assertEquals("Ed25519", envelope.signature.algorithm)
        assertEquals(null, envelope.keyTransition)
    }

    @Test
    fun `拒绝清单载荷中的未知字段`() {
        val json = key.signEnvelope(testManifest()).replace("\"manifest\":{", "\"manifest\":{\"extraField\":1,")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("未知字段"))
    }

    @Test
    fun `拒绝信封中的未知字段`() {
        val json = key.signEnvelope(testManifest()).replace("\"signature\":{", "\"unexpected\":true,\"signature\":{")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("未知字段"))
    }

    @Test
    fun `拒绝缺失字段`() {
        val json = key.signEnvelope(testManifest()).replace("\"license\":\"Apache-2.0\",", "")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("缺少字段"))
    }

    @Test
    fun `拒绝字段类型不符`() {
        val json = key.signEnvelope(testManifest()).replace("\"compressedSize\":1000", "\"compressedSize\":\"1000\"")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("必须是整数"))
    }

    @Test
    fun `拒绝非法 JSON`() {
        assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope("{not json") }
    }

    @Test
    fun `拒绝非法 SHA-256`() {
        val json = key.signEnvelope(testManifest()).replace("a".repeat(64), "ABC")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("sha256"))
    }

    @Test
    fun `规范载荷按键排序且无空白`() {
        val manifest = testManifest()
        val expected =
            "{\"abi\":[\"arm64-v8a\"],\"capabilities\":[\"streaming\",\"zh\"],\"channel\":\"STABLE\"," +
                "\"compressedSize\":1000,\"extractedSize\":2000,\"license\":\"Apache-2.0\"," +
                "\"modelId\":\"x-asr-480ms-int8\",\"runtimeMinVersion\":\"1.10.0\"," +
                "\"sha256\":\"${"a".repeat(64)}\",\"signatureGeneration\":1," +
                "\"url\":\"https://models.echofold.example/x-asr-480ms-int8.zip\",\"version\":\"1.0.0\"}"
        assertEquals(expected, ManifestJson.canonicalPayloadJson(manifest))
        assertTrue(manifest.canonicalJson().startsWith("{\"abi\":"))
    }

    @Test
    fun `解析允许 http URL 与 MANUAL 渠道以留给验签器拒绝`() {
        val manifest = testManifest(channel = ModelChannel.MANUAL, url = "http://models.echofold.example/a.zip")
        val parsed = ManifestJson.parseEnvelope(key.signEnvelope(manifest))
        assertEquals(ModelChannel.MANUAL, parsed.manifest.channel)
        assertEquals("http://models.echofold.example/a.zip", parsed.manifest.url)
    }

    @Test
    fun `拒绝未知渠道`() {
        val json = key.signEnvelope(testManifest()).replace("\"channel\":\"STABLE\"", "\"channel\":\"NIGHTLY\"")
        val error = assertThrows<ManifestFormatException> { ManifestJson.parseEnvelope(json) }
        assertTrue(error.message!!.contains("渠道"))
    }
}
