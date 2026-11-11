package com.echofold.core.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 事件类型；[wireName] 同时是 JSON 判别字段 `kind` 的取值。 */
enum class AsrEventKind(val wireName: String) {
    PARTIAL("partial"),
    FINAL_CANDIDATE("finalCandidate"),
    FINAL("final"),
    HEALTH("health"),
}

/** 稳定事件 ID 的构造规则：`sessionId:generation:kind:seq`。 */
object AsrEventIds {
    const val SEPARATOR: String = ":"

    /** 由会话、跳读世代、事件类型与序号拼出稳定 ID；同一事件重复投递必须得到相同值。 */
    fun of(sessionId: String, generation: Long, kind: AsrEventKind, seq: Long): String =
        sessionId + SEPARATOR + generation + SEPARATOR + kind.wireName + SEPARATOR + seq
}

/**
 * 原生 ASR 投递给 Flutter 的事件。
 *
 * 协议要点：
 * - 每个事件带 [generation]（跳读世代）与 [seq]（采集顺序号），[eventId] 由三者派生；
 * - 接收方按 [eventId] 去重，并按 [generation] 收敛顺序：旧世代事件一律拒绝，绝不写入 GAP 或其他段；
 * - 时间字段后缀 `Ms`/`MonoNs` 明确时钟域：`*UtcMs` 是 UTC 墙钟，`*MonoNs` 是设备单调时钟，
 *   `capturedStartMs/capturedEndMs` 是排除暂停间隙的有效音频偏移。
 */
@Serializable
sealed interface AsrEvent {
    /** 所属录音会话。 */
    val sessionId: String

    /** 跳读世代；每次实际跳读自增，旧世代结果不得提交。 */
    val generation: Long

    /** 同一世代内单调递增的事件序号。 */
    val seq: Long

    /** 事件类型，与 JSON 判别字段 `kind` 一致。 */
    val kind: AsrEventKind

    /** 稳定事件 ID，形如 `sessionId:generation:kind:seq`。 */
    val eventId: String

    /**
     * 可覆盖的临时识别结果。
     *
     * 只有 [AsrEventKind.PARTIAL] 事件允许 `text` 为空（段刚建立尚无输出）；
     * Partial 不得充当已保存证据，也不得写入 GAP。
     */
    @Serializable
    @SerialName("partial")
    data class Partial(
        override val sessionId: String,
        override val generation: Long,
        override val seq: Long,
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val modelId: String,
        val modelVersion: String,
        override val eventId: String = AsrEventIds.of(sessionId, generation, AsrEventKind.PARTIAL, seq),
    ) : AsrEvent {
        override val kind: AsrEventKind get() = AsrEventKind.PARTIAL

        init {
            requireSessionEvent(sessionId, generation, seq, eventId, kind)
            require(startMs in 0..endMs) { "Partial 区间非法：startMs=$startMs, endMs=$endMs" }
            require(modelId.isNotBlank()) { "modelId 不能为空白字符串" }
            require(modelVersion.isNotBlank()) { "modelVersion 不能为空白字符串" }
        }
    }

    /**
     * 已出现稳定前缀的端点候选。
     *
     * `stablePrefixChars` 是 [text] 中不会再被修改的前缀长度，用于安全提交；
     * 候选本身仍不是不可变证据，只有 [AsrEventKind.FINAL] 才能落库。
     */
    @Serializable
    @SerialName("finalCandidate")
    data class FinalCandidate(
        override val sessionId: String,
        override val generation: Long,
        override val seq: Long,
        val text: String,
        val stablePrefixChars: Int,
        val startMs: Long,
        val endMs: Long,
        override val eventId: String = AsrEventIds.of(sessionId, generation, AsrEventKind.FINAL_CANDIDATE, seq),
    ) : AsrEvent {
        override val kind: AsrEventKind get() = AsrEventKind.FINAL_CANDIDATE

        init {
            requireSessionEvent(sessionId, generation, seq, eventId, kind)
            require(startMs in 0..endMs) { "FinalCandidate 区间非法：startMs=$startMs, endMs=$endMs" }
            require(stablePrefixChars in 0..text.length) {
                "stablePrefixChars($stablePrefixChars) 必须落在 0..text.length(${text.length})"
            }
        }
    }

    /**
     * 已提交的不可变 FINAL 事件。
     *
     * 携带 UTC `finalizedAtUtcMs`、单调时钟区间、有效音频偏移与有序 [chunkIds]；
     * 后续 AI 校订、用户编辑或重识别只能新增版本，不得改写该证据。
     */
    @Serializable
    @SerialName("final")
    data class Final(
        override val sessionId: String,
        override val generation: Long,
        override val seq: Long,
        val finalId: String,
        val text: String,
        val finalizedAtUtcMs: Long,
        val captureStartedMonoNs: Long,
        val captureEndedMonoNs: Long,
        val capturedStartMs: Long,
        val capturedEndMs: Long,
        val chunkIds: List<String>,
        override val eventId: String = AsrEventIds.of(sessionId, generation, AsrEventKind.FINAL, seq),
    ) : AsrEvent {
        override val kind: AsrEventKind get() = AsrEventKind.FINAL

        init {
            requireSessionEvent(sessionId, generation, seq, eventId, kind)
            require(finalId.isNotBlank()) { "finalId 不能为空白字符串" }
            require(text.isNotBlank()) { "FINAL 的 text 不能为空白：空白结果不得作为证据提交" }
            require(finalizedAtUtcMs >= 0L) { "finalizedAtUtcMs 不能为负数：$finalizedAtUtcMs" }
            require(captureStartedMonoNs >= 0L) { "captureStartedMonoNs 不能为负数：$captureStartedMonoNs" }
            require(captureEndedMonoNs >= captureStartedMonoNs) {
                "captureEndedMonoNs($captureEndedMonoNs) 不能早于 captureStartedMonoNs($captureStartedMonoNs)"
            }
            require(capturedStartMs in 0..capturedEndMs) {
                "FINAL 有效音频区间非法：capturedStartMs=$capturedStartMs, capturedEndMs=$capturedEndMs"
            }
            require(chunkIds.isNotEmpty()) { "FINAL 必须引用至少一个音频块" }
            val seen = HashSet<String>(chunkIds.size)
            for (chunkId in chunkIds) {
                require(chunkId.isNotBlank()) { "chunkIds 元素不能为空白字符串" }
                require(seen.add(chunkId)) { "chunkIds 不能包含重复值：$chunkId" }
            }
        }
    }

    /**
     * 运行健康采样。
     *
     * 记录实时系数、最旧未处理 PCM 的排队时延、线程与内存、跳读次数、ASR 队列指标与错误类别。
     * 同一次过载可能被多次采样上报，接收方必须按 [eventId] 去重并复用同一个 GAP，不得重复插入。
     */
    @Serializable
    @SerialName("health")
    data class Health(
        override val sessionId: String,
        override val generation: Long,
        override val seq: Long,
        val rtf: Double,
        val oldestUnprocessedAgeMs: Long,
        val threadCount: Int,
        val memoryBytes: Long,
        val skipCount: Long,
        val queue: QueueMetrics,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        override val eventId: String = AsrEventIds.of(sessionId, generation, AsrEventKind.HEALTH, seq),
    ) : AsrEvent {
        override val kind: AsrEventKind get() = AsrEventKind.HEALTH

        init {
            requireSessionEvent(sessionId, generation, seq, eventId, kind)
            require(rtf >= 0.0 && !rtf.isNaN()) { "rtf 必须为非负有限值：$rtf" }
            require(oldestUnprocessedAgeMs >= 0L) {
                "oldestUnprocessedAgeMs 不能为负数：$oldestUnprocessedAgeMs"
            }
            require(threadCount > 0) { "threadCount 必须为正数：$threadCount" }
            require(memoryBytes >= 0L) { "memoryBytes 不能为负数：$memoryBytes" }
            require(skipCount >= 0L) { "skipCount 不能为负数：$skipCount" }
            errorCode?.let { require(it.isNotBlank()) { "errorCode 存在时不能为空白字符串" } }
            errorMessage?.let { require(it.isNotBlank()) { "errorMessage 存在时不能为空白字符串" } }
        }
    }
}

/** ASR 队列指标；容量固定，不得用扩容掩盖积压。 */
@Serializable
data class QueueMetrics(
    val capacity: Int,
    val depth: Int,
    val highWatermark: Int,
    val oldestEnqueuedMonoNs: Long?,
    val newestEnqueuedMonoNs: Long,
    val rejectedCount: Long,
    val blocked: Boolean,
) {
    init {
        require(capacity >= 0) { "capacity 不能为负数：$capacity" }
        require(depth in 0..capacity) { "depth($depth) 必须落在 0..capacity($capacity)" }
        require(highWatermark in depth..capacity) {
            "highWatermark($highWatermark) 必须落在 depth..capacity($depth..$capacity)"
        }
        require(newestEnqueuedMonoNs >= 0L) { "newestEnqueuedMonoNs 不能为负数：$newestEnqueuedMonoNs" }
        require(rejectedCount >= 0L) { "rejectedCount 不能为负数：$rejectedCount" }
        if (depth == 0) {
            require(oldestEnqueuedMonoNs == null) { "队列为空时 oldestEnqueuedMonoNs 必须为 null" }
        } else {
            require(oldestEnqueuedMonoNs != null) { "队列非空时 oldestEnqueuedMonoNs 必须有值" }
            require(oldestEnqueuedMonoNs <= newestEnqueuedMonoNs) {
                "oldestEnqueuedMonoNs($oldestEnqueuedMonoNs) 不能晚于 newestEnqueuedMonoNs($newestEnqueuedMonoNs)"
            }
        }
    }
}

/**
 * 通用事件字段校验：会话非空、世代与序号非负，且 [AsrEvent.eventId] 必须与派生规则一致
 * （防止 `copy()` 修改 generation/seq 后留下陈旧 ID）。
 */
private fun requireSessionEvent(
    sessionId: String,
    generation: Long,
    seq: Long,
    eventId: String,
    kind: AsrEventKind,
) {
    require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
    require(generation >= 0L) { "generation 不能为负数：$generation" }
    require(seq >= 0L) { "seq 不能为负数：$seq" }
    val expected = AsrEventIds.of(sessionId, generation, kind, seq)
    require(eventId == expected) { "eventId($eventId) 必须由 sessionId/generation/kind/seq 派生为 $expected" }
}
