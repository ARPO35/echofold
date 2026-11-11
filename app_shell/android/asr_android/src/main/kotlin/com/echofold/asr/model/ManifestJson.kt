package com.echofold.asr.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull

/** 清单格式错误（JSON 结构、字段类型、未知字段或字段取值非法）。 */
class ManifestFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

private const val DETACHED_ALGORITHM = "Ed25519"
private const val DETACHED_SIGNATURE_BYTES = 64

/**
 * 分离签名：签名值不参与载荷编码，覆盖范围固定为 [ModelManifest] 的规范 JSON 字节。
 */
data class DetachedSignature(
    val keyId: String,
    val algorithm: String,
    /** Base64 编码的 Ed25519 签名（必须为 64 字节原始签名）。 */
    val valueBase64: String,
) {
    init {
        require(keyId.isNotBlank()) { "签名 keyId 不能为空白字符串" }
        require(algorithm == DETACHED_ALGORITHM) { "不支持的签名算法：$algorithm" }
        require(valueBase64.isNotBlank()) { "签名值不能为空白字符串" }
        val decoded =
            try {
                java.util.Base64.getDecoder().decode(valueBase64)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("签名值不是合法 Base64", e)
            }
        require(decoded.size == DETACHED_SIGNATURE_BYTES) {
            "Ed25519 签名必须为 $DETACHED_SIGNATURE_BYTES 字节：${decoded.size}"
        }
    }

    companion object {
        const val ED25519_ALGORITHM = DETACHED_ALGORITHM
        const val ED25519_SIGNATURE_BYTES = DETACHED_SIGNATURE_BYTES
    }
}

/** 已签名的远程清单信封。 */
data class SignedManifest(
    val manifest: ModelManifest,
    val signature: DetachedSignature,
    /** 可选：由旧受信密钥签发的密钥过渡声明，允许本次清单由新密钥签名。 */
    val keyTransition: KeyTransitionDeclaration? = null,
)

/**
 * 清单 JSON 的严格解析与规范编码。
 *
 * 解析规则：只接受明确白名单字段，未知字段、缺失字段、类型不符（例如把数字写成字符串）
 * 一律抛 [ManifestFormatException]，不做“尽量读懂”的兼容。
 */
object ManifestJson {
    private val PAYLOAD_KEYS =
        setOf(
            "modelId",
            "version",
            "signatureGeneration",
            "channel",
            "runtimeMinVersion",
            "abi",
            "url",
            "sha256",
            "compressedSize",
            "extractedSize",
            "license",
            "capabilities",
        )
    private val ENVELOPE_KEYS = setOf("manifest", "signature", "keyTransition")
    private val SIGNATURE_KEYS = setOf("keyId", "algorithm", "value")

    /** 解析完整信封。 */
    fun parseEnvelope(text: String): SignedManifest {
        val root = parseObject(text, "清单信封")
        StrictJson.requireAllowedKeys(root, ENVELOPE_KEYS, "清单信封")
        StrictJson.requireKeys(root, setOf("manifest", "signature"), "清单信封")
        val manifest = parseManifestObject(StrictJson.objectAt(root, "manifest", "清单信封"))
        val signature = parseSignature(StrictJson.objectAt(root, "signature", "清单信封"))
        val transition = root["keyTransition"]?.let { KeyTransitionDeclaration.parse(StrictJson.objectOf(it, "keyTransition")) }
        return SignedManifest(manifest, signature, transition)
    }

    /** 解析清单载荷对象（不含签名）。 */
    fun parseManifestObject(obj: JsonObject): ModelManifest {
        StrictJson.requireAllowedKeys(obj, PAYLOAD_KEYS, "清单载荷")
        StrictJson.requireKeys(obj, PAYLOAD_KEYS, "清单载荷")
        return try {
            ModelManifest(
                modelId = StrictJson.string(obj, "modelId", "清单载荷"),
                version = StrictJson.string(obj, "version", "清单载荷"),
                signatureGeneration = StrictJson.long(obj, "signatureGeneration", "清单载荷"),
                channel = ModelChannel.parse(StrictJson.string(obj, "channel", "清单载荷")),
                runtimeMinVersion = StrictJson.string(obj, "runtimeMinVersion", "清单载荷"),
                abi = StrictJson.stringList(obj, "abi", "清单载荷"),
                url = StrictJson.string(obj, "url", "清单载荷"),
                sha256 = StrictJson.string(obj, "sha256", "清单载荷"),
                compressedSize = StrictJson.long(obj, "compressedSize", "清单载荷"),
                extractedSize = StrictJson.long(obj, "extractedSize", "清单载荷"),
                license = StrictJson.string(obj, "license", "清单载荷"),
                capabilities = StrictJson.stringList(obj, "capabilities", "清单载荷"),
            )
        } catch (e: IllegalArgumentException) {
            throw ManifestFormatException("清单字段不合法：${e.message}", e)
        }
    }

    /** 清单载荷对象（持久化快照与测试构造共用）。 */
    fun toJsonObject(manifest: ModelManifest): JsonObject =
        buildJsonObject {
            put("modelId", JsonPrimitive(manifest.modelId))
            put("version", JsonPrimitive(manifest.version))
            put("signatureGeneration", JsonPrimitive(manifest.signatureGeneration))
            put("channel", JsonPrimitive(manifest.channel.name))
            put("runtimeMinVersion", JsonPrimitive(manifest.runtimeMinVersion))
            put("abi", buildJsonArray { manifest.abi.forEach { add(JsonPrimitive(it)) } })
            put("url", JsonPrimitive(manifest.url))
            put("sha256", JsonPrimitive(manifest.sha256))
            put("compressedSize", JsonPrimitive(manifest.compressedSize))
            put("extractedSize", JsonPrimitive(manifest.extractedSize))
            put("license", JsonPrimitive(manifest.license))
            put("capabilities", buildJsonArray { manifest.capabilities.forEach { add(JsonPrimitive(it)) } })
        }

    /** 签名覆盖的规范 JSON 文本：键按字典序、无空白、数组顺序保留。 */
    fun canonicalPayloadJson(manifest: ModelManifest): String =
        CanonicalJson.jsonObject(
            listOf(
                "abi" to CanonicalJson.stringArray(manifest.abi),
                "capabilities" to CanonicalJson.stringArray(manifest.capabilities),
                "channel" to CanonicalJson.quote(manifest.channel.name),
                "compressedSize" to CanonicalJson.number(manifest.compressedSize),
                "extractedSize" to CanonicalJson.number(manifest.extractedSize),
                "license" to CanonicalJson.quote(manifest.license),
                "modelId" to CanonicalJson.quote(manifest.modelId),
                "runtimeMinVersion" to CanonicalJson.quote(manifest.runtimeMinVersion),
                "sha256" to CanonicalJson.quote(manifest.sha256),
                "signatureGeneration" to CanonicalJson.number(manifest.signatureGeneration),
                "url" to CanonicalJson.quote(manifest.url),
                "version" to CanonicalJson.quote(manifest.version),
            ),
        )

    /** 签名覆盖的 UTF-8 字节。 */
    fun canonicalPayloadBytes(manifest: ModelManifest): ByteArray = canonicalPayloadJson(manifest).toByteArray(Charsets.UTF_8)

    private fun parseSignature(obj: JsonObject): DetachedSignature {
        StrictJson.requireAllowedKeys(obj, SIGNATURE_KEYS, "signature")
        StrictJson.requireKeys(obj, SIGNATURE_KEYS, "signature")
        return try {
            DetachedSignature(
                keyId = StrictJson.string(obj, "keyId", "signature"),
                algorithm = StrictJson.string(obj, "algorithm", "signature"),
                valueBase64 = StrictJson.string(obj, "value", "signature"),
            )
        } catch (e: IllegalArgumentException) {
            throw ManifestFormatException("签名格式不合法：${e.message}", e)
        }
    }

    private fun parseObject(text: String, context: String): JsonObject {
        val element =
            try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ManifestFormatException("$context 不是合法 JSON", e)
            }
        return StrictJson.objectOf(element, context)
    }
}

/**
 * 严格 JSON 读取助手；所有错误都带路径与上下文，便于定位清单问题。
 */
internal object StrictJson {
    fun objectOf(element: JsonElement, context: String): JsonObject =
        element as? JsonObject ?: throw ManifestFormatException("$context 必须是 JSON 对象")

    fun objectAt(parent: JsonObject, key: String, context: String): JsonObject {
        val element = parent[key] ?: throw ManifestFormatException("$context 缺少字段 $key")
        return objectOf(element, "$context.$key")
    }

    fun requireAllowedKeys(obj: JsonObject, allowed: Set<String>, context: String) {
        val unknown = obj.keys - allowed
        if (unknown.isNotEmpty()) throw ManifestFormatException("$context 包含未知字段：${unknown.sorted()}")
    }

    fun requireKeys(obj: JsonObject, required: Set<String>, context: String) {
        val missing = required - obj.keys
        if (missing.isNotEmpty()) throw ManifestFormatException("$context 缺少字段：${missing.sorted()}")
    }

    fun string(obj: JsonObject, key: String, context: String): String {
        val primitive = primitiveOf(obj, key, context)
        if (!primitive.isString) throw ManifestFormatException("$context.$key 必须是字符串")
        return primitive.content
    }

    fun long(obj: JsonObject, key: String, context: String): Long {
        val primitive = primitiveOf(obj, key, context)
        if (primitive.isString) throw ManifestFormatException("$context.$key 必须是整数")
        return primitive.longOrNull ?: throw ManifestFormatException("$context.$key 必须是整数")
    }

    fun stringList(obj: JsonObject, key: String, context: String): List<String> {
        val array = obj[key] as? JsonArray ?: throw ManifestFormatException("$context.$key 必须是字符串数组")
        return array.mapIndexed { index, element ->
            val primitive = element as? JsonPrimitive ?: throw ManifestFormatException("$context.$key[$index] 必须是字符串")
            if (!primitive.isString) throw ManifestFormatException("$context.$key[$index] 必须是字符串")
            primitive.content
        }
    }

    private fun primitiveOf(obj: JsonObject, key: String, context: String): JsonPrimitive {
        val element = obj[key] ?: throw ManifestFormatException("$context 缺少字段 $key")
        return element as? JsonPrimitive ?: throw ManifestFormatException("$context.$key 必须是基本类型")
    }
}
