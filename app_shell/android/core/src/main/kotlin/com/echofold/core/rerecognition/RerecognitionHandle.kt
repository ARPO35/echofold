package com.echofold.core.rerecognition

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 一次重识别任务的句柄。
 *
 * 同一幂等键的重复请求返回同一个句柄实例，因此调用方可安全地比较引用并共享同一个终结结果。
 * 终结（成功/失败/取消/拒绝）恰好发生一次：先到者生效，之后到达的迟到结果被丢弃，
 * 既不会覆盖默认展示，也不会改写已交付的结果。
 */
class RerecognitionHandle internal constructor(
    /** 稳定幂等键；同时用于派生任务 ID 与重识别版本 ID。 */
    val key: RerecognitionKey,
    /** 任务所属段；同一段同一时刻只允许一个活动任务。 */
    val segmentId: String,
    /** 提交请求时观察到的跳读世代；完成时若已落后则判为迟到。 */
    val generation: Long,
    /**
     * 请求时锁定的默认版本 ID，作为新版本的父版本。
     *
     * 提交事务要求它仍是该段当前默认版本；期间若被用户编辑、AI 校订或其他重识别取代，
     * 本任务的结果只能以 [RerecognitionRejection.SUPERSEDED] 返回，绝不覆盖更新的选择。
     */
    val baseVersionId: String?,
) {
    private val terminationLock = Any()
    private val latch = CountDownLatch(1)

    @Volatile
    private var terminal: RerecognitionResult? = null

    /** 已终结时的结果；仍在执行（或等待执行）时为 `null`。 */
    val result: RerecognitionResult?
        get() = terminal

    /** 是否已终结；终结后结果不会再变化。 */
    val isDone: Boolean
        get() = terminal != null

    /** 阻塞直到终结并返回结果；任务由协调器保证一定会终结。 */
    fun await(): RerecognitionResult {
        latch.await()
        return checkNotNull(terminal) { "重识别任务已结束但缺少终结结果：$key" }
    }

    /** 在 [timeoutMs] 内等待终结；超时抛 [IllegalStateException]，避免测试与宿主无限阻塞。 */
    fun await(timeoutMs: Long): RerecognitionResult {
        require(timeoutMs > 0L) { "timeoutMs 必须为正数：$timeoutMs" }
        check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            "重识别任务在 ${timeoutMs}ms 内未终结：$key"
        }
        return checkNotNull(terminal) { "重识别任务已结束但缺少终结结果：$key" }
    }

    /** 写入终结结果；只有第一次写入生效，返回是否由本次调用终结。 */
    internal fun complete(value: RerecognitionResult): Boolean = synchronized(terminationLock) {
        if (terminal != null) {
            false
        } else {
            terminal = value
            latch.countDown()
            true
        }
    }
}
