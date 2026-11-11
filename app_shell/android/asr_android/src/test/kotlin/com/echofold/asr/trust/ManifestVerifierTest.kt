package com.echofold.asr.trust

import com.echofold.asr.model.ManifestJson
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelSource
import com.echofold.asr.testing.InMemoryAcceptedGenerationStore
import com.echofold.asr.testing.TestKey
import com.echofold.asr.testing.testManifest
import com.echofold.asr.testing.transition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ManifestVerifierTest {
    private val keyA = TestKey("key-a", 1)
    private val keyB = TestKey("key-b", 2)
    private val registry = TrustedKeyRegistry.of(listOf(keyA.trusted()))
    private val generations = InMemoryAcceptedGenerationStore()
    private val verifier = ManifestVerifier(registry, generations, clock = { 42L })

    @Test
    fun `合法签名通过并记录已接受代数`() {
        val manifest = testManifest(signatureGeneration = 3)
        val verified = verifier.verify(keyA.signEnvelope(manifest))

        assertEquals(ModelSource.REMOTE, verified.source)
        assertEquals("key-a", verified.signatureKeyId)
        assertEquals(42L, verified.verifiedAtUtcMs)
        assertEquals(3L, generations.acceptedGeneration(ModelChannel.STABLE))
    }

    @Test
    fun `载荷被篡改时拒绝`() {
        val original = testManifest(signatureGeneration = 2)
        val signature = keyA.sign(ManifestJson.canonicalPayloadBytes(original))
        val tampered = original.copy(version = "9.9.9")

        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyA.signEnvelopeWith(tampered, signature))
        }
        assertTrue(error.message!!.contains("签名验证失败"))
        assertNull(generations.acceptedGeneration(ModelChannel.STABLE))
    }

    @Test
    fun `错误密钥签名被拒绝`() {
        val manifest = testManifest()
        val payload = ManifestJson.canonicalPayloadBytes(manifest)
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyA.signEnvelopeWith(manifest, keyB.sign(payload)))
        }
        assertTrue(error.message!!.contains("签名验证失败"))
    }

    @Test
    fun `未知签名密钥且无过渡声明被拒绝`() {
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyB.signEnvelope(testManifest(signatureGeneration = 2)))
        }
        assertTrue(error.message!!.contains("未知签名密钥"))
    }

    @Test
    fun `拒绝非 HTTPS 清单`() {
        val manifest = testManifest(url = "http://models.echofold.example/a.zip")
        val error = assertThrows<ManifestVerificationException> { verifier.verify(keyA.signEnvelope(manifest)) }
        assertTrue(error.message!!.contains("HTTPS"))
    }

    @Test
    fun `MANUAL 渠道不得走受信路径`() {
        val manifest = testManifest(channel = ModelChannel.MANUAL)
        val error = assertThrows<ManifestVerificationException> { verifier.verify(keyA.signEnvelope(manifest)) }
        assertTrue(error.message!!.contains("MANUAL"))
        assertNull(generations.acceptedGeneration(ModelChannel.MANUAL))
    }

    @Test
    fun `签名代数低于已接受代数时拒绝`() {
        generations.acceptGeneration(ModelChannel.STABLE, 5L)
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyA.signEnvelope(testManifest(signatureGeneration = 4)))
        }
        assertTrue(error.message!!.contains("回滚"))
        assertEquals(5L, generations.acceptedGeneration(ModelChannel.STABLE))
    }

    @Test
    fun `代数相同允许重复验证并推进更高代数`() {
        generations.acceptGeneration(ModelChannel.STABLE, 2L)
        verifier.verify(keyA.signEnvelope(testManifest(signatureGeneration = 2)))
        assertEquals(2L, generations.acceptedGeneration(ModelChannel.STABLE))

        verifier.verify(keyA.signEnvelope(testManifest(signatureGeneration = 3)))
        assertEquals(3L, generations.acceptedGeneration(ModelChannel.STABLE))
    }

    @Test
    fun `旧钥签发的过渡声明允许新钥签名`() {
        val manifest = testManifest(signatureGeneration = 2)
        val declaration = transition(keyA, keyB)
        val verified = verifier.verify(keyB.signEnvelope(manifest, declaration))

        assertEquals("key-b", verified.signatureKeyId)
        assertEquals(2L, generations.acceptedGeneration(ModelChannel.STABLE))
        // 过渡推进后，旧代数清单不再被接受
        assertThrows<ManifestVerificationException> {
            verifier.verify(keyA.signEnvelope(testManifest(signatureGeneration = 1)))
        }
    }

    @Test
    fun `过渡声明签名无效时拒绝`() {
        val manifest = testManifest(signatureGeneration = 2)
        val valid = transition(keyA, keyB)
        val forged = valid.copy(signatureBase64 = keyB.sign(valid.canonicalBytes()))
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyB.signEnvelope(manifest, forged))
        }
        assertTrue(error.message!!.contains("过渡声明签名验证失败"))
    }

    @Test
    fun `过渡声明目标密钥与签名密钥不一致时拒绝`() {
        val keyC = TestKey("key-c", 3)
        val manifest = testManifest(signatureGeneration = 3)
        val declaration = transition(keyA, keyC)
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyB.signEnvelope(manifest, declaration))
        }
        assertTrue(error.message!!.contains("不一致"))
    }

    @Test
    fun `清单代数低于过渡声明新钥代数时拒绝`() {
        val manifest = testManifest(signatureGeneration = 1)
        val declaration = transition(keyA, keyB)
        val error = assertThrows<ManifestVerificationException> {
            verifier.verify(keyB.signEnvelope(manifest, declaration))
        }
        assertTrue(error.message!!.contains("低于过渡声明"))
    }

    @Test
    fun `注册表构造时拒绝伪造的过渡声明`() {
        val valid = transition(keyA, keyB)
        val forged = valid.copy(signatureBase64 = keyB.sign(valid.canonicalBytes()))
        assertThrows<TrustedKeyException> { TrustedKeyRegistry.of(listOf(keyA.trusted()), listOf(forged)) }
    }

    @Test
    fun `注册表构造时拒绝未知旧钥的过渡声明`() {
        val declaration = transition(keyB, keyA)
        assertThrows<TrustedKeyException> { TrustedKeyRegistry.of(listOf(keyA.trusted()), listOf(declaration)) }
    }

    @Test
    fun `手动导入需要用户确认且必须声明 MANUAL`() {
        val manual = testManifest(channel = ModelChannel.MANUAL)
        assertThrows<ManifestVerificationException> {
            verifier.verifyManual(manual, manual.sha256, userConfirmed = false)
        }
        assertThrows<ManifestVerificationException> {
            verifier.verifyManual(testManifest(), testManifest().sha256, userConfirmed = true)
        }
        val verified = verifier.verifyManual(manual, manual.sha256, userConfirmed = true)
        assertEquals(ModelSource.MANUAL, verified.source)
        assertNull(verified.signatureKeyId)
        assertTrue(verified.userConfirmed)
    }

    @Test
    fun `手动导入 SHA-256 不一致时拒绝`() {
        val manual = testManifest(channel = ModelChannel.MANUAL)
        val error = assertThrows<ManifestVerificationException> {
            verifier.verifyManual(manual, "b".repeat(64), userConfirmed = true)
        }
        assertTrue(error.message!!.contains("SHA-256"))
    }

    @Test
    fun `文件代数存储跨实例持久化并拒绝降低`(@TempDir tempDir: File) {
        val file = File(tempDir, "accepted-generations.json")
        val store = FileAcceptedGenerationStore(file)
        assertNull(store.acceptedGeneration(ModelChannel.STABLE))
        store.acceptGeneration(ModelChannel.STABLE, 4L)

        val reloaded = FileAcceptedGenerationStore(file)
        assertEquals(4L, reloaded.acceptedGeneration(ModelChannel.STABLE))
        assertThrows<IllegalArgumentException> { reloaded.acceptGeneration(ModelChannel.STABLE, 3L) }
        assertThrows<IllegalArgumentException> { reloaded.acceptGeneration(ModelChannel.MANUAL, 1L) }
    }

    @Test
    fun `损坏的代数文件 fail closed`(@TempDir tempDir: File) {
        val file = File(tempDir, "accepted-generations.json")
        file.writeText("{broken", Charsets.UTF_8)
        assertThrows<AcceptedGenerationException> { FileAcceptedGenerationStore(file).acceptedGeneration(ModelChannel.STABLE) }
    }
}
