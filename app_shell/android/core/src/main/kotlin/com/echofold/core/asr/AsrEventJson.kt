package com.echofold.core.asr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * [AsrEvent] 的 JSON 线上格式。
 *
 * 字段名一律 camelCase，判别字段为 `kind`，取值为 [AsrEventKind.wireName]；
 * 序列化时总是写出默认值（含派生的 `eventId`，以及 Health 的 null 错误字段），
 * 这样 Flutter 侧可以只依赖 JSON 字段而无需重算 ID。
 * 反序列化拒绝未知字段，避免协议悄悄漂移。
 */
object AsrEventJson {
    /** 协议编解码器；供 Kotlin 侧持久化、日志与契约测试复用。 */
    val format: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        prettyPrint = false
        classDiscriminator = "kind"
    }

    /** 编码为紧凑 JSON 字符串。 */
    fun encode(event: AsrEvent): String = format.encodeToString(AsrEvent.serializer(), event)

    /** 从 JSON 字符串解码；未知 `kind`、缺失字段或违反不变量都会抛出异常。 */
    fun decode(json: String): AsrEvent = format.decodeFromString(AsrEvent.serializer(), json)

    /**
     * 转成 EventChannel 可直接投递的 Map（只含 Map/List/String/数值/Boolean/null）。
     *
     * 与 [encode] 共用同一序列化器，保证通道字段名与 JSON 字段名逐项一致；
     * JSON 不区分整数宽度，因此整数字段统一以 [Long] 出现在 Map 中（Dart 侧即 `int`）。
     */
    fun toChannelMap(event: AsrEvent): Map<String, Any?> {
        val element = format.encodeToJsonElement(AsrEvent.serializer(), event)
        require(element is JsonObject) { "AsrEvent 顶层必须是 JSON 对象" }
        @Suppress("UNCHECKED_CAST")
        return element.toChannelValue() as Map<String, Any?>
    }
}

private fun JsonElement.toChannelValue(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> entries.associate { (key, value) -> key to value.toChannelValue() }
    is JsonArray -> map { it.toChannelValue() }
    is JsonPrimitive -> when {
        isString -> content
        content == "true" -> true
        content == "false" -> false
        longOrNull != null -> long
        doubleOrNull != null -> double
        else -> content
    }
}
