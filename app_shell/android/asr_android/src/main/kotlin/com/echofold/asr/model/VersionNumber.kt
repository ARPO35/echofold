package com.echofold.asr.model

/**
 * 运行时版本号比较。
 *
 * 只接受纯数字点分版本（`1`、`1.10`、`1.10.0`），避免因预发布标签导致“看起来更新”的歧义；
 * 缺失的段按 0 处理。
 */
object VersionNumber {
    private val PATTERN = Regex("^\\d{1,9}(\\.\\d{1,9}){0,3}$")

    /** 校验版本号格式；非法时抛出 [IllegalArgumentException]，字段名进入异常消息。 */
    fun requireValid(value: String, field: String) {
        require(PATTERN.matches(value)) { "$field 必须是纯数字点分版本号：$value" }
    }

    /** 按段比较两个版本号，返回负数/零/正数；任一版本非法时抛出 [IllegalArgumentException]。 */
    fun compare(left: String, right: String): Int {
        requireValid(left, "left")
        requireValid(right, "right")
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftValue = leftParts.getOrNull(index)?.toLong() ?: 0L
            val rightValue = rightParts.getOrNull(index)?.toLong() ?: 0L
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }

    /** 判断 [actual] 是否不低于 [minimum]。 */
    fun atLeast(actual: String, minimum: String): Boolean = compare(actual, minimum) >= 0
}
