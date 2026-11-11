package com.echofold.asr.trust

import com.echofold.asr.model.KeyTransitionDeclaration
import com.echofold.asr.model.ManifestFormatException
import com.echofold.asr.model.ManifestJson
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import com.echofold.asr.model.SignedManifest
import java.net.URI
import java.net.URISyntaxException

/** 清单验签失败（格式、来源、签名、代数或手动确认不满足）。 */
class ManifestVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 通过校验的清单。
 *
 * [source] 区分远程受信与用户手动导入；[signatureKeyId] 在 MANUAL 来源必须为 `null`，
 * 避免把未经签名的工件描述成受信远程清单。
 */
data class VerifiedManifest(
    val manifest: ModelManifest,
    val source: ModelSource,
    val signatureKeyId: String?,
    val verifiedAtUtcMs: Long,
    val userConfirmed: Boolean,
) {
    init {
        when (source) {
            ModelSource.REMOTE -> {
                require(signatureKeyId != null) { "远程受信清单必须记录签名密钥 ID" }
                require(!userConfirmed) { "远程受信清单不应携带手动确认标记" }
            }
            ModelSource.MANUAL -> {
                require(signatureKeyId == null) { "手动导入清单不得携带受信签名密钥 ID" }
                require(userConfirmed) { "手动导入必须记录用户确认" }
            }
        }
    }
}

/**
 * 受信清单校验器。
 *
 * 校验顺序：严格解析 → 渠道/传输约束 → 密钥解析（含旧钥签发过渡）→ Ed25519 验签 → 代数回滚拒绝
 * → 持久化已接受代数。任一步失败都不会推进代数，也不会安装工件。
 */
class ManifestVerifier(
    private val keyRegistry: TrustedKeyRegistry,
    private val acceptedGenerations: AcceptedGenerationStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 校验远程受信清单信封（JSON 文本）。 */
    fun verify(envelopeText: String): VerifiedManifest {
        val envelope =
            try {
                ManifestJson.parseEnvelope(envelopeText)
            } catch (e: ManifestFormatException) {
                throw ManifestVerificationException("清单格式非法：${e.message}", e)
            }
        val manifest = envelope.manifest
        if (manifest.channel == ModelChannel.MANUAL) {
            throw ManifestVerificationException("MANUAL 渠道清单不得走受信校验路径")
        }
        requireHttps(manifest.url)
        val signingKey = resolveSigningKey(envelope)
        if (manifest.signatureGeneration < signingKey.generation) {
            throw ManifestVerificationException(
                "清单签名代数 ${manifest.signatureGeneration} 低于签名密钥代数 ${signingKey.generation}",
            )
        }
        val verified =
            try {
                signingKey.verify(ManifestJson.canonicalPayloadBytes(manifest), envelope.signature.valueBase64)
            } catch (e: IllegalArgumentException) {
                throw ManifestVerificationException("签名材料非法：${e.message}", e)
            }
        if (!verified) throw ManifestVerificationException("清单签名验证失败：keyId=${signingKey.keyId}")
        val accepted = acceptedGenerations.acceptedGeneration(manifest.channel)
        if (accepted != null && manifest.signatureGeneration < accepted) {
            throw ManifestVerificationException(
                "清单签名代数回滚：${manifest.channel} 已接受 $accepted，收到 ${manifest.signatureGeneration}",
            )
        }
        try {
            acceptedGenerations.acceptGeneration(manifest.channel, manifest.signatureGeneration)
        } catch (e: RuntimeException) {
            throw ManifestVerificationException("记录已接受签名代数失败：${e.message}", e)
        }
        return VerifiedManifest(
            manifest = manifest,
            source = ModelSource.REMOTE,
            signatureKeyId = signingKey.keyId,
            verifiedAtUtcMs = clock(),
            userConfirmed = false,
        )
    }

    /**
     * 手动导入校验。
     *
     * 手动路径**不验签**：清单必须明确声明 [ModelChannel.MANUAL]，用户必须显式确认，
     * 且归档实际 SHA-256 必须与清单声明一致；结果来源固定为 MANUAL。
     */
    fun verifyManual(manifest: ModelManifest, actualSha256: String, userConfirmed: Boolean): VerifiedManifest {
        if (!userConfirmed) throw ManifestVerificationException("手动导入必须由用户确认")
        if (manifest.channel != ModelChannel.MANUAL) {
            throw ManifestVerificationException("手动导入清单必须声明 MANUAL 渠道：${manifest.channel}")
        }
        if (actualSha256 != manifest.sha256) {
            throw ManifestVerificationException("手动导入文件 SHA-256 与清单声明不一致：$actualSha256 != ${manifest.sha256}")
        }
        return VerifiedManifest(
            manifest = manifest,
            source = ModelSource.MANUAL,
            signatureKeyId = null,
            verifiedAtUtcMs = clock(),
            userConfirmed = true,
        )
    }

    private fun resolveSigningKey(envelope: SignedManifest): TrustedKey {
        val signature = envelope.signature
        keyRegistry.find(signature.keyId)?.let { return it }
        val transition = envelope.keyTransition
            ?: throw ManifestVerificationException("未知签名密钥：${signature.keyId}（缺少旧钥签发的过渡声明）")
        return resolveTransitionKey(transition, signature.keyId, envelope.manifest.signatureGeneration)
    }

    private fun resolveTransitionKey(
        transition: KeyTransitionDeclaration,
        signatureKeyId: String,
        manifestGeneration: Long,
    ): TrustedKey {
        if (transition.toKeyId != signatureKeyId) {
            throw ManifestVerificationException(
                "过渡声明的目标密钥 ${transition.toKeyId} 与清单签名密钥 $signatureKeyId 不一致",
            )
        }
        val fromKey = keyRegistry.find(transition.fromKeyId)
            ?: throw ManifestVerificationException("过渡声明引用了未知的旧密钥：${transition.fromKeyId}")
        if (transition.toGeneration <= fromKey.generation) {
            throw ManifestVerificationException(
                "过渡声明的新密钥代数必须高于旧密钥：${transition.toGeneration} <= ${fromKey.generation}",
            )
        }
        val verified =
            try {
                fromKey.verify(transition.canonicalBytes(), transition.signatureBase64)
            } catch (e: IllegalArgumentException) {
                throw ManifestVerificationException("过渡声明签名材料非法：${e.message}", e)
            }
        if (!verified) {
            throw ManifestVerificationException("过渡声明签名验证失败：${transition.fromKeyId} -> ${transition.toKeyId}")
        }
        if (manifestGeneration < transition.toGeneration) {
            throw ManifestVerificationException(
                "清单签名代数 $manifestGeneration 低于过渡声明的新密钥代数 ${transition.toGeneration}",
            )
        }
        return TrustedKey(transition.toKeyId, transition.toPublicKeyBase64, transition.toGeneration)
    }

    private fun requireHttps(url: String) {
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw ManifestVerificationException("清单 URL 非法：$url", e)
            }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw ManifestVerificationException("受信清单只允许 HTTPS 来源：$url")
        }
        if (uri.host.isNullOrBlank()) throw ManifestVerificationException("清单 URL 缺少主机：$url")
    }
}
