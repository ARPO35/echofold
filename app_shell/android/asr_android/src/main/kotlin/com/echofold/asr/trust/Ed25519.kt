package com.echofold.asr.trust

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.EdECPoint
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * JDK `Ed25519` 验签工具。
 *
 * 公钥接受两种编码：32 字节原始 Ed25519 公钥（按 RFC 8032 的小端 y + x 符号位重建），
 * 或 X.509 SubjectPublicKeyInfo DER。签名必须是 64 字节原始签名。
 */
object Ed25519 {
    const val ALGORITHM = "Ed25519"
    const val RAW_PUBLIC_KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64

    /** 校验 Base64 公钥是否可解析，失败抛 [IllegalArgumentException]。 */
    fun requireValidPublicKey(publicKeyBase64: String) {
        publicKeyFromBase64(publicKeyBase64)
    }

    /** 解析 Base64 公钥。 */
    fun publicKeyFromBase64(publicKeyBase64: String): PublicKey {
        val raw = decodeBase64(publicKeyBase64, "Ed25519 公钥")
        val spec =
            if (raw.size == RAW_PUBLIC_KEY_BYTES) {
                rawPublicKeySpec(raw)
            } else {
                X509EncodedKeySpec(raw)
            }
        return try {
            KeyFactory.getInstance(ALGORITHM).generatePublic(spec)
        } catch (e: Exception) {
            throw IllegalArgumentException("Ed25519 公钥无效", e)
        }
    }

    /**
     * 验证签名；签名内容不匹配返回 `false`，而公钥/签名格式非法抛 [IllegalArgumentException]。
     */
    fun verify(publicKeyBase64: String, message: ByteArray, signatureBase64: String): Boolean {
        val publicKey = publicKeyFromBase64(publicKeyBase64)
        val signatureBytes = decodeBase64(signatureBase64, "Ed25519 签名")
        require(signatureBytes.size == SIGNATURE_BYTES) { "Ed25519 签名必须为 $SIGNATURE_BYTES 字节：${signatureBytes.size}" }
        val verifier = Signature.getInstance(ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(message)
        return verifier.verify(signatureBytes)
    }

    /** Base64 编码。 */
    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Base64 解码，失败时抛 [IllegalArgumentException] 并带上语义描述。 */
    fun decodeBase64(value: String, what: String): ByteArray =
        try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$what 不是合法 Base64", e)
        }

    private fun rawPublicKeySpec(raw: ByteArray): EdECPublicKeySpec {
        // RFC 8032：32 字节小端 y，最高位是 x 的奇偶符号。
        val yBytes = raw.copyOf()
        val xOdd = (yBytes[RAW_PUBLIC_KEY_BYTES - 1].toInt() and 0x80) != 0
        yBytes[RAW_PUBLIC_KEY_BYTES - 1] = (yBytes[RAW_PUBLIC_KEY_BYTES - 1].toInt() and 0x7F).toByte()
        val y = BigInteger(1, yBytes.reversedArray())
        return EdECPublicKeySpec(NamedParameterSpec.ED25519, EdECPoint(xOdd, y))
    }
}
