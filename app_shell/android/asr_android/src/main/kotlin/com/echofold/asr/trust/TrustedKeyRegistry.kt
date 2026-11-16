package com.echofold.asr.trust

import com.echofold.asr.model.KeyTransitionDeclaration

/**
 * 内置受信密钥。
 *
 * [generation] 只描述密钥在轮换链上的代数；清单的 `signatureGeneration` 是渠道维度的
 * 单调计数，两者都不允许回退。
 */
data class TrustedKey(
    val keyId: String,
    /** Base64 编码的 Ed25519 公钥（32 字节原始或 X.509 DER）。 */
    val publicKeyBase64: String,
    /** 密钥代数，递增。 */
    val generation: Long,
) {
    init {
        require(KEY_ID_PATTERN.matches(keyId)) { "受信密钥 ID 不合法：$keyId" }
        require(generation >= 1L) { "受信密钥代数必须为正数：$generation" }
        Ed25519.requireValidPublicKey(publicKeyBase64)
    }

    /** 用本密钥验证签名。 */
    fun verify(message: ByteArray, signatureBase64: String): Boolean = Ed25519.verify(publicKeyBase64, message, signatureBase64)

    private companion object {
        val KEY_ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}

/** 受信密钥注册表或过渡声明不合法。 */
class TrustedKeyException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * 内置受信密钥注册表（应用打包时固化），可选携带由旧钥签名的过渡声明。
 *
 * 构造即完成过渡声明的验签：任何一条声明无法被其 `fromKeyId` 对应旧钥验证，整个注册表
 * 构造失败（fail closed），不会静默忽略轮换材料。
 */
class TrustedKeyRegistry private constructor(
    private val keysById: Map<String, TrustedKey>,
) {
    /** 按 ID 查找受信密钥（含已验证的过渡密钥）。 */
    fun find(keyId: String): TrustedKey? = keysById[keyId]

    /** 全部受信密钥，按代数升序。 */
    fun all(): List<TrustedKey> = keysById.values.sortedBy { it.generation }

    companion object {
        /**
         * 构建注册表。
         *
         * @param keys 内置密钥列表（至少一个）
         * @param transitions 可选的旧钥签发过渡声明
         */
        fun empty(): TrustedKeyRegistry = TrustedKeyRegistry(emptyMap())

        /** 构建注册表。 */
        fun of(keys: List<TrustedKey>, transitions: List<KeyTransitionDeclaration> = emptyList()): TrustedKeyRegistry {
            if (keys.isEmpty()) throw TrustedKeyException("受信密钥列表不能为空")
            val registered = LinkedHashMap<String, TrustedKey>()
            for (key in keys) {
                val previous = registered.put(key.keyId, key)
                if (previous != null) throw TrustedKeyException("重复的受信密钥 ID：${key.keyId}")
            }
            for (transition in transitions) {
                val from = registered[transition.fromKeyId]
                    ?: throw TrustedKeyException("过渡声明引用了未知的旧密钥：${transition.fromKeyId}")
                if (transition.toGeneration <= from.generation) {
                    throw TrustedKeyException(
                        "过渡声明的新密钥代数必须高于旧密钥：${transition.toGeneration} <= ${from.generation}",
                    )
                }
                val existing = registered[transition.toKeyId]
                if (existing != null) {
                    if (existing.publicKeyBase64 != transition.toPublicKeyBase64 || existing.generation != transition.toGeneration) {
                        throw TrustedKeyException("过渡声明的目标密钥与已注册密钥冲突：${transition.toKeyId}")
                    }
                    continue
                }
                val verified =
                    try {
                        from.verify(transition.canonicalBytes(), transition.signatureBase64)
                    } catch (e: IllegalArgumentException) {
                        throw TrustedKeyException("过渡声明签名材料非法：${e.message}", e)
                    }
                if (!verified) throw TrustedKeyException("过渡声明签名验证失败：${transition.fromKeyId} -> ${transition.toKeyId}")
                registered[transition.toKeyId] = TrustedKey(transition.toKeyId, transition.toPublicKeyBase64, transition.toGeneration)
            }
            return TrustedKeyRegistry(registered)
        }
    }
}
