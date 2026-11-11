package com.echofold.asr.install

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * HTTP Range 请求的闭区间。
 *
 * `endInclusive` 为 `null` 表示“从 start 到文件末尾”，用于断点续传。
 */
data class ByteRange(
    val startInclusive: Long,
    val endInclusive: Long? = null,
) {
    init {
        require(startInclusive >= 0L) { "Range 起点不能为负数：$startInclusive" }
        endInclusive?.let { require(it >= startInclusive) { "Range 终点($it)不能早于起点($startInclusive)" } }
    }

    /** 生成 `Range` 头取值。 */
    fun headerValue(): String = "bytes=$startInclusive-${endInclusive ?: ""}"
}

/** 解析后的 `Content-Range`。 */
data class ContentRange(
    val startInclusive: Long,
    val endInclusive: Long,
    /** 总长度；服务器返回 `*` 时为 `null`。 */
    val totalLength: Long?,
) {
    companion object {
        private val PATTERN = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)

        /** 解析失败返回 `null`（调用方必须把缺失/非法 Content-Range 视为不可续传）。 */
        fun parse(header: String): ContentRange? {
            val match = PATTERN.find(header.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            if (end < start) return null
            val totalText = match.groupValues[3]
            val total = if (totalText == "*") null else totalText.toLongOrNull() ?: return null
            return ContentRange(start, end, total)
        }
    }
}

/**
 * 一次下载请求。
 *
 * 只允许 HTTPS：非 HTTPS 在构造阶段即失败，重定向的逐跳校验由安装器负责。
 */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val range: ByteRange? = null,
) {
    init {
        val scheme = schemeOf(url) ?: throw IllegalArgumentException("URL 非法：$url")
        require(scheme == "https") { "只允许 HTTPS 请求：$url" }
        require(headers.keys.none { it.equals("Range", ignoreCase = true) }) {
            "Range 头必须通过 range 参数传入，不得混入 headers"
        }
    }

    private companion object {
        fun schemeOf(url: String): String? =
            try {
                URI(url).scheme?.lowercase()
            } catch (e: URISyntaxException) {
                null
            }
    }
}

/**
 * 已打开的 HTTP 响应。
 *
 * 客户端**不得**自行跟随重定向：3xx 原样返回 [location]，由 [ModelInstaller] 逐跳校验
 * 来源、凭据与 HTTPS 降级。
 */
interface HttpExchange : AutoCloseable {
    val statusCode: Int
    val location: String?
    val contentRange: ContentRange?
    val contentLength: Long?
    val etag: String?
    val body: InputStream

    override fun close()
}

/** 可注入的 HTTP 客户端抽象；生产实现见 [JdkHttpRangeClient]，单测使用 fake。 */
interface HttpRangeClient {
    /** 打开请求；网络错误抛 [IOException]，HTTP 错误码通过 [HttpExchange.statusCode] 暴露。 */
    @Throws(IOException::class)
    fun open(request: HttpRequest): HttpExchange
}
