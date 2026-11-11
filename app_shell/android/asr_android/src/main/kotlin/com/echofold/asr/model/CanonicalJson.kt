package com.echofold.asr.model

/**
 * 签名覆盖范围的规范 JSON 编码。
 *
 * 手写编码而不是复用通用 JSON 库：清单签名必须对“键顺序固定、无空白、数组顺序保留、UTF-8”
 * 的字节计算，任何编码器默认值变化都会让同一清单得到不同签名结果。
 */
internal object CanonicalJson {
    private const val HEX = "0123456789abcdef"

    /** 以 JSON 字符串字面量转义；控制字符使用 `\u00xx`，非 ASCII 字符原样保留。 */
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
                ch.code < 0x20 ->
                    builder
                        .append("\\u00")
                        .append(HEX[(ch.code shr 4) and 0xF])
                        .append(HEX[ch.code and 0xF])
                else -> builder.append(ch)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    /** 编码字符串数组，顺序保持不变。 */
    fun stringArray(values: List<String>): String = values.joinToString(separator = ",", prefix = "[", postfix = "]") { quote(it) }

    /** 编码 64 位整数（十进制、无前导零）。 */
    fun number(value: Long): String = value.toString()

    /** 编码规范对象；调用方按字典序提供键值对，保证跨平台字节一致。 */
    fun jsonObject(entries: List<Pair<String, String>>): String =
        entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, encodedValue) -> quote(key) + ":" + encodedValue }
}
