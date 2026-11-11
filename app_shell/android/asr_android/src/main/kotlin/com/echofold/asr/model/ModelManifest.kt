package com.echofold.asr.model

import java.net.URI
import java.net.URISyntaxException

/**
 * 模型清单渠道。
 *
 * [MANUAL] 只表示用户手动导入的工件，未经过内置受信密钥验签，**不得**进入受信校验路径；
 * 受信远程清单只能是 [STABLE] 或 [BETA]。
 */
enum class ModelChannel {
    STABLE,
    BETA,
    MANUAL,

    ;

    companion object {
        /** 按清单原始字符串解析渠道；未知取值必须拒绝，而不是回退到默认值。 */
        fun parse(raw: String): ModelChannel =
            entries.firstOrNull { it.name == raw }
                ?: throw ManifestFormatException("未知的模型渠道：$raw")
    }
}

/** 安装来源：远程受信清单下载，或用户手动导入。 */
enum class ModelSource {
    REMOTE,
    MANUAL,
}

/**
 * 受信模型清单载荷（签名覆盖的部分）。
 *
 * 字段只读且构造时校验不变量；[url] 允许 `http` 只为让“拒绝非 HTTPS”发生在验签器/安装器，
 * 而不是 JSON 解析阶段。真正的传输约束由 [com.echofold.asr.trust.ManifestVerifier] 与
 * [com.echofold.asr.install.ModelInstaller] 强制。
 */
data class ModelManifest(
    val modelId: String,
    val version: String,
    /** 渠道维度的单调签名代数；低于本机已接受代数的清单必须拒绝。 */
    val signatureGeneration: Long,
    val channel: ModelChannel,
    /** 运行时（sherpa-onnx 绑定）最低版本，语义版本号，如 `1.10.0`。 */
    val runtimeMinVersion: String,
    /** 工件支持的 Android ABI 列表，如 `arm64-v8a`。 */
    val abi: List<String>,
    /** 工件下载地址；受信路径要求 HTTPS。 */
    val url: String,
    /** 压缩工件的 SHA-256，64 位小写十六进制。 */
    val sha256: String,
    /** 压缩工件字节数。 */
    val compressedSize: Long,
    /** 解压后全部文件字节数之和。 */
    val extractedSize: Long,
    val license: String,
    /** 模型能力标记，如 `streaming`、`zh`、`en`。 */
    val capabilities: List<String>,
) {
    init {
        require(MODEL_ID_PATTERN.matches(modelId)) { "modelId 不合法：$modelId" }
        require(VERSION_PATTERN.matches(version)) { "version 不合法：$version" }
        require(signatureGeneration >= 1L) { "signatureGeneration 必须为正数：$signatureGeneration" }
        VersionNumber.requireValid(runtimeMinVersion, "runtimeMinVersion")
        require(abi.isNotEmpty()) { "abi 不能为空" }
        require(abi.distinct().size == abi.size) { "abi 不能重复：$abi" }
        abi.forEach { require(ABI_PATTERN.matches(it)) { "abi 不合法：$it" } }
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("url 不合法：$url", e)
            }
        require(uri.isAbsolute && !uri.host.isNullOrBlank()) { "url 必须是带主机的绝对地址：$url" }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "url 只允许 http/https：$url" }
        require(uri.userInfo == null) { "url 不得内嵌凭据：$url" }
        require(SHA256_PATTERN.matches(sha256)) { "sha256 必须是 64 位小写十六进制：$sha256" }
        require(compressedSize > 0L) { "compressedSize 必须为正数：$compressedSize" }
        require(extractedSize > 0L) { "extractedSize 必须为正数：$extractedSize" }
        require(license.isNotBlank()) { "license 不能为空白字符串" }
        require(capabilities.isNotEmpty()) { "capabilities 不能为空" }
        require(capabilities.distinct().size == capabilities.size) { "capabilities 不能重复：$capabilities" }
        capabilities.forEach { require(it.isNotBlank()) { "capability 不能为空白字符串" } }
    }

    /** 规范 JSON 文本；签名字节即它的 UTF-8 编码。 */
    fun canonicalJson(): String = ManifestJson.canonicalPayloadJson(this)

    private companion object {
        val MODEL_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
        val VERSION_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$")
        val ABI_PATTERN = Regex("^[a-z0-9_]+(-[a-z0-9_]+)*$")
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
