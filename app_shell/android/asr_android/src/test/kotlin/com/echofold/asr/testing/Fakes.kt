package com.echofold.asr.testing

import com.echofold.asr.install.ContentRange
import com.echofold.asr.install.HttpExchange
import com.echofold.asr.install.HttpRangeClient
import com.echofold.asr.install.HttpRequest
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.repository.LeaseState
import com.echofold.asr.repository.LeaseStore
import com.echofold.asr.repository.ModelIndex
import com.echofold.asr.repository.ModelIndexStore
import com.echofold.asr.trust.AcceptedGenerationStore
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** 脚本化 HTTP 客户端；按 URL 顺序返回响应，并记录每次请求（含重定向与 Range 头）。 */
class FakeHttpRangeClient : HttpRangeClient {
    private val routes = LinkedHashMap<String, ArrayDeque<ScriptedResponse>>()
    val requests = mutableListOf<HttpRequest>()

    fun enqueue(url: String, vararg responses: ScriptedResponse): FakeHttpRangeClient {
        val queue = routes.getOrPut(url) { ArrayDeque() }
        responses.forEach { queue.addLast(it) }
        return this
    }

    override fun open(request: HttpRequest): HttpExchange {
        requests += request
        val queue = routes[request.url] ?: throw IOException("未配置 URL：${request.url}")
        val response = queue.removeFirstOrNull() ?: throw IOException("URL 响应已用尽：${request.url}")
        return FakeExchange(response)
    }

    /** 最近一次请求。 */
    fun lastRequest(): HttpRequest = requests.last()

    sealed interface ScriptedResponse {
        data class Body(
            val bytes: ByteArray,
            val status: Int = 200,
            val contentRange: ContentRange? = null,
            val etag: String? = null,
        ) : ScriptedResponse

        data class Redirect(val location: String, val status: Int = 302) : ScriptedResponse

        data class Status(val code: Int) : ScriptedResponse
    }

    private class FakeExchange(
        private val response: ScriptedResponse,
    ) : HttpExchange {
        override val statusCode: Int =
            when (response) {
                is ScriptedResponse.Body -> response.status
                is ScriptedResponse.Redirect -> response.status
                is ScriptedResponse.Status -> response.code
            }
        override val location: String? = (response as? ScriptedResponse.Redirect)?.location
        override val contentRange: ContentRange? = (response as? ScriptedResponse.Body)?.contentRange
        override val contentLength: Long? = (response as? ScriptedResponse.Body)?.bytes?.size?.toLong()
        override val etag: String? = (response as? ScriptedResponse.Body)?.etag
        override val body: InputStream =
            ByteArrayInputStream((response as? ScriptedResponse.Body)?.bytes ?: ByteArray(0))

        override fun close() {
            body.close()
        }
    }
}

/** 内存索引存储。 */
class InMemoryModelIndexStore(
    initial: ModelIndex = ModelIndex(),
) : ModelIndexStore {
    var index: ModelIndex = initial

    override fun load(): ModelIndex = index

    override fun save(index: ModelIndex) {
        this.index = index
    }
}

/** 内存租约存储（可在多个管理器实例间共享以模拟重启）。 */
class InMemoryLeaseStore(
    initial: LeaseState = LeaseState(),
) : LeaseStore {
    var state: LeaseState = initial

    override fun load(): LeaseState = state

    override fun save(state: LeaseState) {
        this.state = state
    }
}

/** 内存已接受代数存储，可模拟“本机已接受更高代数”。 */
class InMemoryAcceptedGenerationStore : AcceptedGenerationStore {
    val generations = LinkedHashMap<ModelChannel, Long>()

    override fun acceptedGeneration(channel: ModelChannel): Long? = generations[channel]

    override fun acceptGeneration(channel: ModelChannel, generation: Long) {
        val current = generations[channel]
        require(current == null || generation >= current) { "签名代数不得降低" }
        if (current == null || generation > current) generations[channel] = generation
    }
}
