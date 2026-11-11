package com.echofold.asr.testing

import com.echofold.asr.model.DetachedSignature
import com.echofold.asr.model.KeyTransitionDeclaration
import com.echofold.asr.model.ManifestJson
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelManifest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** 测试用 Ed25519 密钥；签名覆盖清单/过渡声明的规范字节。 */
class TestKey(
    val keyId: String,
    val generation: Long,
) {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    fun sign(message: ByteArray): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(message)
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    fun trusted(): com.echofold.asr.trust.TrustedKey =
        com.echofold.asr.trust.TrustedKey(keyId = keyId, publicKeyBase64 = publicKeyBase64, generation = generation)

    /** 构造清单信封 JSON。 */
    fun signEnvelope(
        manifest: ModelManifest,
        transition: KeyTransitionDeclaration? = null,
    ): String {
        val signature = DetachedSignature(keyId = keyId, algorithm = DetachedSignature.ED25519_ALGORITHM, valueBase64 = sign(ManifestJson.canonicalPayloadBytes(manifest)))
        return buildEnvelope(manifest, signature, transition)
    }

    /** 用固定签名值构造信封（用于篡改/错误密钥测试）。 */
    fun signEnvelopeWith(
        manifest: ModelManifest,
        signatureBase64: String,
        transition: KeyTransitionDeclaration? = null,
    ): String {
        val signature = DetachedSignature(keyId = keyId, algorithm = DetachedSignature.ED25519_ALGORITHM, valueBase64 = signatureBase64)
        return buildEnvelope(manifest, signature, transition)
    }

    private fun buildEnvelope(
        manifest: ModelManifest,
        signature: DetachedSignature,
        transition: KeyTransitionDeclaration?,
    ): String {
        val signatureJson =
            kotlinx.serialization.json.buildJsonObject {
                put("keyId", kotlinx.serialization.json.JsonPrimitive(signature.keyId))
                put("algorithm", kotlinx.serialization.json.JsonPrimitive(signature.algorithm))
                put("value", kotlinx.serialization.json.JsonPrimitive(signature.valueBase64))
            }
        val root =
            kotlinx.serialization.json.buildJsonObject {
                put("manifest", ManifestJson.toJsonObject(manifest))
                put("signature", signatureJson)
                if (transition != null) {
                    put(
                        "keyTransition",
                        kotlinx.serialization.json.buildJsonObject {
                            put("fromKeyId", kotlinx.serialization.json.JsonPrimitive(transition.fromKeyId))
                            put("toKeyId", kotlinx.serialization.json.JsonPrimitive(transition.toKeyId))
                            put("toPublicKey", kotlinx.serialization.json.JsonPrimitive(transition.toPublicKeyBase64))
                            put("toGeneration", kotlinx.serialization.json.JsonPrimitive(transition.toGeneration))
                            put("signature", kotlinx.serialization.json.JsonPrimitive(transition.signatureBase64))
                        },
                    )
                }
            }
        return root.toString()
    }
}

/** 生成“旧钥授权新钥”的过渡声明。 */
fun transition(from: TestKey, to: TestKey): KeyTransitionDeclaration {
    val placeholder = Base64.getEncoder().encodeToString(ByteArray(64))
    val unsigned =
        KeyTransitionDeclaration(
            fromKeyId = from.keyId,
            toKeyId = to.keyId,
            toPublicKeyBase64 = to.publicKeyBase64,
            toGeneration = to.generation,
            signatureBase64 = placeholder,
        )
    return unsigned.copy(signatureBase64 = from.sign(unsigned.canonicalBytes()))
}

/** 清单构造器：默认值均为合法值，测试按需覆盖。 */
fun testManifest(
    modelId: String = "x-asr-480ms-int8",
    version: String = "1.0.0",
    signatureGeneration: Long = 1L,
    channel: ModelChannel = ModelChannel.STABLE,
    runtimeMinVersion: String = "1.10.0",
    abi: List<String> = listOf("arm64-v8a"),
    url: String = "https://models.echofold.example/x-asr-480ms-int8.zip",
    sha256: String = "a".repeat(64),
    compressedSize: Long = 1_000L,
    extractedSize: Long = 2_000L,
    license: String = "Apache-2.0",
    capabilities: List<String> = listOf("streaming", "zh"),
): ModelManifest =
    ModelManifest(
        modelId = modelId,
        version = version,
        signatureGeneration = signatureGeneration,
        channel = channel,
        runtimeMinVersion = runtimeMinVersion,
        abi = abi,
        url = url,
        sha256 = sha256,
        compressedSize = compressedSize,
        extractedSize = extractedSize,
        license = license,
        capabilities = capabilities,
    )
