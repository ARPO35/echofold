package com.echofold.asr.install

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException

/**
 * 基于 [HttpURLConnection] 的 Range 下载实现（Android/JVM 均可用）。
 *
 * 关键约束：
 * - 只接受 HTTPS；构造 [HttpRequest] 时已校验，这里再防御一次；
 * - `instanceFollowRedirects = false`，重定向交给安装器逐跳校验；
 * - 不自动解压（`Accept-Encoding: identity`），保证 Range 偏移与压缩工件字节一一对应。
 */
class JdkHttpRangeClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
    private val userAgent: String = "EchoFold-ASR/1.0",
) : HttpRangeClient {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs 必须为正数：$connectTimeoutMs" }
        require(readTimeoutMs > 0) { "readTimeoutMs 必须为正数：$readTimeoutMs" }
    }

    override fun open(request: HttpRequest): HttpExchange {
        val uri = parseHttps(request.url)
        val connection =
            try {
                uri.toURL().openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw IOException("无法打开连接：${request.url}", e)
            }
        connection.instanceFollowRedirects = false
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        for ((name, value) in request.headers) {
            connection.setRequestProperty(name, value)
        }
        request.range?.let { connection.setRequestProperty("Range", it.headerValue()) }
        try {
            connection.connect()
            val status = connection.responseCode
            val location = connection.getHeaderField("Location")
            val contentRange = connection.getHeaderField("Content-Range")?.let { ContentRange.parse(it) }
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
            val etag = connection.getHeaderField("ETag")
            val body = readBody(connection, status)
            return JdkHttpExchange(connection, status, location, contentRange, contentLength, etag, body)
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private fun parseHttps(url: String): URI {
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("URL 非法：$url", e)
            }
        require(uri.scheme.equals("https", ignoreCase = true)) { "只允许 HTTPS 请求：$url" }
        require(!uri.host.isNullOrBlank()) { "URL 缺少主机：$url" }
        return uri
    }

    private fun readBody(connection: HttpURLConnection, status: Int): InputStream {
        if (status in 200..299) {
            return connection.inputStream
        }
        return connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
    }

    private class JdkHttpExchange(
        private val connection: HttpURLConnection,
        override val statusCode: Int,
        override val location: String?,
        override val contentRange: ContentRange?,
        override val contentLength: Long?,
        override val etag: String?,
        override val body: InputStream,
    ) : HttpExchange {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            try {
                body.close()
            } finally {
                connection.disconnect()
            }
        }
    }
}
