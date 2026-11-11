package com.echofold.core.model

import java.security.MessageDigest

/**
 * 规范 JSON 编码与哈希工具。
 *
 * 共享契约要求 `sourceSetHash` 由“键固定顺序、无空白、UTF-8、数组顺序保留”的规范 JSON
 * 计算 SHA-256，并输出小写十六进制。这里手写编码而不是复用通用 JSON 库，是为了让
 * 键顺序、数字格式与转义规则完全确定，任何实现漂移都会让同一来源集合得到不同哈希。
 */
internal object CanonicalJson {
    private const val HEX_DIGITS = "0123456789abcdef"

    /** 以 JSON 字符串字面量转义；控制字符使用 `\u00XX`，非 ASCII 字符按 UTF-8 原样保留。 */
    fun quote(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (ch in value) {
            when {
                ch == '"' -> builder.append("\\\"")
                ch == '\\' -> builder.append("\\\\")
                ch == '\n' -> builder.append("\\n")
                ch == '\r' -> builder.append("\\r")
                ch == '\t' -> builder.append("\\t")
                ch == '\b' -> builder.append("\\b")
                ch == '\u000C' -> builder.append("\\f")
                ch < ' ' -> builder
                    .append("\\u00")
                    .append(HEX_DIGITS[(ch.code shr 4) and 0xF])
                    .append(HEX_DIGITS[ch.code and 0xF])
                else -> builder.append(ch)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    /** 对 UTF-8 字节计算 SHA-256，返回 64 位小写十六进制。 */
    fun sha256Hex(utf8: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(utf8.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value shr 4]).append(HEX_DIGITS[value and 0xF])
        }
        return builder.toString()
    }
}

/** 校验字符串非空白；字段名参与异常消息，便于定位非法构造。 */
internal fun requireNotBlank(value: String, field: String): String {
    require(value.isNotBlank()) { "$field 不能为空白字符串" }
    return value
}

/** 校验可空字符串：非空时不得为空白。 */
internal fun requireNotBlankIfPresent(value: String?, field: String): String? {
    if (value != null) requireNotBlank(value, field)
    return value
}

/** 校验时间/序号类字段非负。 */
internal fun requireNonNegative(value: Long, field: String): Long {
    require(value >= 0L) { "$field 不能为负数：$value" }
    return value
}

/** 校验区间 `[start, end]` 合法：两端非负且终点不早于起点。 */
internal fun requireRange(startMs: Long, endMs: Long, startField: String, endField: String) {
    requireNonNegative(startMs, startField)
    requireNonNegative(endMs, endField)
    require(endMs >= startMs) { "$endField($endMs) 不能早于 $startField($startMs)" }
}
