package com.echofold.asr.model

import kotlinx.serialization.json.JsonObject

/**
 * 公钥轮换的过渡声明：由**旧受信密钥**签名，授权新密钥在更高签名代数上继续签发清单。
 *
 * 声明本身不含签名值参与编码；签名覆盖 [canonicalBytes]（固定 schema 串 + 排序键）。
 * 只有旧钥签名可被内置受信密钥验证时，新钥才会被本次清单验签临时采信。
 */
data class KeyTransitionDeclaration(
    val fromKeyId: String,
    val toKeyId: String,
    /** Base64 编码的新公钥（32 字节原始公钥或 X.509 SubjectPublicKeyInfo）。 */
    val toPublicKeyBase64: String,
    val toGeneration: Long,
    /** Base64 编码的 Ed25519 签名，由 [fromKeyId] 对应私钥生成。 */
    val signatureBase64: String,
) {
    init {
        require(fromKeyId.isNotBlank()) { "过渡声明的 fromKeyId 不能为空白字符串" }
        require(toKeyId.isNotBlank()) { "过渡声明的 toKeyId 不能为空白字符串" }
        require(fromKeyId != toKeyId) { "过渡声明的新旧密钥 ID 不能相同：$fromKeyId" }
        require(toGeneration >= 1L) { "过渡声明的 toGeneration 必须为正数：$toGeneration" }
        require(toPublicKeyBase64.isNotBlank()) { "过渡声明的新公钥不能为空白字符串" }
        require(signatureBase64.isNotBlank()) { "过渡声明的签名不能为空白字符串" }
    }

    /** 签名覆盖的规范 JSON 字节。 */
    fun canonicalBytes(): ByteArray =
        CanonicalJson
            .jsonObject(
                listOf(
                    "fromKeyId" to CanonicalJson.quote(fromKeyId),
                    "schema" to CanonicalJson.quote(SCHEMA),
                    "toGeneration" to CanonicalJson.number(toGeneration),
                    "toKeyId" to CanonicalJson.quote(toKeyId),
                    "toPublicKey" to CanonicalJson.quote(toPublicKeyBase64),
                ),
            ).toByteArray(Charsets.UTF_8)

    companion object {
        /** 参与签名覆盖的 schema 串，避免与其他签名文档混淆。 */
        const val SCHEMA = "echofold.key-transition.v1"

        private val KEYS = setOf("fromKeyId", "toKeyId", "toPublicKey", "toGeneration", "signature")

        /** 严格解析过渡声明对象。 */
        fun parse(obj: JsonObject): KeyTransitionDeclaration {
            StrictJson.requireAllowedKeys(obj, KEYS, "keyTransition")
            StrictJson.requireKeys(obj, KEYS, "keyTransition")
            return try {
                KeyTransitionDeclaration(
                    fromKeyId = StrictJson.string(obj, "fromKeyId", "keyTransition"),
                    toKeyId = StrictJson.string(obj, "toKeyId", "keyTransition"),
                    toPublicKeyBase64 = StrictJson.string(obj, "toPublicKey", "keyTransition"),
                    toGeneration = StrictJson.long(obj, "toGeneration", "keyTransition"),
                    signatureBase64 = StrictJson.string(obj, "signature", "keyTransition"),
                )
            } catch (e: IllegalArgumentException) {
                throw ManifestFormatException("过渡声明格式不合法：${e.message}", e)
            }
        }
    }
}
