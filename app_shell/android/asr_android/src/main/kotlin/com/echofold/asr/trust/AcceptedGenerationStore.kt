package com.echofold.asr.trust

import com.echofold.asr.internal.AtomicFiles
import com.echofold.asr.model.ModelChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/**
 * 本机已接受的清单签名代数（按渠道维度）。
 *
 * 只记录通过内置受信密钥验签的远程渠道；[ModelChannel.MANUAL] 永远不会进入该存储。
 */
interface AcceptedGenerationStore {
    /** 返回该渠道已接受的最大签名代数；从未接受过返回 `null`。 */
    fun acceptedGeneration(channel: ModelChannel): Long?

    /** 记录更高的签名代数；不允许降低已接受代数。 */
    fun acceptGeneration(channel: ModelChannel, generation: Long)
}

/** 已接受代数持久化失败或内容损坏。 */
class AcceptedGenerationException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 文件实现：`{ "BETA": 3, "STABLE": 7 }`，原子写入。
 *
 * 首次读取时加载并缓存；文件损坏时 fail closed（抛异常），避免因解析失败而把回滚防护降级为“无记录”。
 */
class FileAcceptedGenerationStore(
    private val file: File,
) : AcceptedGenerationStore {
    private val lock = Any()
    private var loaded = false
    private val generations = LinkedHashMap<ModelChannel, Long>()

    override fun acceptedGeneration(channel: ModelChannel): Long? = synchronized(lock) {
        requireTrustedChannel(channel)
        ensureLoaded()
        generations[channel]
    }

    override fun acceptGeneration(channel: ModelChannel, generation: Long) = synchronized(lock) {
        requireTrustedChannel(channel)
        require(generation >= 1L) { "签名代数必须为正数：$generation" }
        ensureLoaded()
        val current = generations[channel]
        if (current != null && generation < current) {
            throw IllegalArgumentException("签名代数不得降低：$channel 已接受 $current，收到 $generation")
        }
        if (current == null || generation > current) {
            generations[channel] = generation
            persist()
        }
    }

    private fun requireTrustedChannel(channel: ModelChannel) {
        require(channel != ModelChannel.MANUAL) { "MANUAL 渠道不走受信代数记录" }
    }

    private fun ensureLoaded() {
        if (loaded) return
        if (!file.isFile) {
            loaded = true
            return
        }
        val text =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                throw AcceptedGenerationException("读取已接受签名代数失败：$file", e)
            }
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw AcceptedGenerationException("已接受签名代数文件损坏：$file", e)
            }
        for ((key, element) in root) {
            val channel =
                ModelChannel.entries.firstOrNull { it.name == key }
                    ?: throw AcceptedGenerationException("已接受签名代数文件包含未知渠道：$key")
            if (channel == ModelChannel.MANUAL) throw AcceptedGenerationException("已接受签名代数文件不得包含 MANUAL 渠道")
            val primitive = element as? JsonPrimitive ?: throw AcceptedGenerationException("渠道 $key 的代数不是整数")
            val value = primitive.longOrNull ?: throw AcceptedGenerationException("渠道 $key 的代数不是整数")
            if (value < 1L) throw AcceptedGenerationException("渠道 $key 的代数非法：$value")
            generations[channel] = value
        }
        loaded = true
    }

    private fun persist() {
        val root =
            buildJsonObject {
                for ((channel, generation) in generations) {
                    put(channel.name, JsonPrimitive(generation))
                }
            }
        try {
            AtomicFiles.writeText(file, root.toString())
        } catch (e: IOException) {
            throw AcceptedGenerationException("写入已接受签名代数失败：$file", e)
        }
    }
}
