package com.echofold.core.asr

import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.model.GapReason
import com.echofold.core.model.SegmentKind
import com.echofold.core.model.TranscriptSegment

/**
 * 一次 ASR 过载采样的输入。
 *
 * 时间全部来自设备单调时钟与实际采集 offset：
 * - [oldestUnprocessedMonoNs]：本轮最早未处理 PCM 的入队单调时间（含已入队与正在推理的输入）；
 * - [newestCapturedMonoNs]：判定时刻已由采集/持久化路径接收的最新安全采集单调时间；
 * - [skippedCapturedStartMs]/[skippedCapturedEndMs]：被跳过识别副本的实际采集 offset 区间；
 * - [chunkIds]：该区间覆盖的音频块引用，顺序即采集顺序，调用方不得排序或去重。
 */
data class AsrOverloadSample(
    val oldestUnprocessedMonoNs: Long,
    val newestCapturedMonoNs: Long,
    val skippedCapturedStartMs: Long,
    val skippedCapturedEndMs: Long,
    val chunkIds: List<String>,
) {
    init {
        require(oldestUnprocessedMonoNs >= 0L) {
            "oldestUnprocessedMonoNs 不能为负数：$oldestUnprocessedMonoNs"
        }
        require(newestCapturedMonoNs >= oldestUnprocessedMonoNs) {
            "newestCapturedMonoNs($newestCapturedMonoNs) 不能早于 oldestUnprocessedMonoNs($oldestUnprocessedMonoNs)"
        }
        require(skippedCapturedStartMs in 0..skippedCapturedEndMs) {
            "跳过区间非法：$skippedCapturedStartMs..$skippedCapturedEndMs"
        }
        require(chunkIds.isNotEmpty()) { "GAP 必须引用至少一个音频块" }
        val seen = HashSet<String>(chunkIds.size)
        for (chunkId in chunkIds) {
            require(chunkId.isNotBlank()) { "chunkIds 元素不能为空白字符串" }
            require(seen.add(chunkId)) { "chunkIds 不能包含重复值：$chunkId" }
        }
    }
}

/**
 * 已落定的 ASR 过载缺口（L0 证据）。
 *
 * [segment] 本身就是可持久化的 `TranscriptSegment`：`kind=GAP`、`sourceFinalIds` 为空、
 * `gapReason=ASR_OVERLOAD`、`foldDueAtUtcMs=null`（缺口不参与自动折叠）。
 * 块引用与模型身份单独保留，用于跳读诊断与之后的手动同模型重识别。
 */
data class AsrGap(
    val generation: Long,
    val lagMs: Long,
    val segment: TranscriptSegment,
    val chunkIds: List<String>,
    val modelId: String,
    val modelVersion: String,
) {
    init {
        require(generation >= 0L) { "generation 不能为负数：$generation" }
        require(lagMs > 0L) { "lagMs 必须为正数：$lagMs" }
        require(segment.kind == SegmentKind.GAP) { "AsrGap.segment 必须是 GAP 段" }
        require(segment.gapReason == GapReason.ASR_OVERLOAD) { "GAP 原因必须是 ASR_OVERLOAD" }
        require(segment.foldDueAtUtcMs == null) { "GAP 不得带 foldDueAtUtcMs" }
        require(segment.sourceFinalIds.isEmpty()) { "GAP 不得引用 FINAL" }
        require(chunkIds.isNotEmpty()) { "GAP 必须引用至少一个音频块" }
        require(modelId.isNotBlank()) { "modelId 不能为空白字符串" }
        require(modelVersion.isNotBlank()) { "modelVersion 不能为空白字符串" }
    }
}

/** [GapCoordinator.observe] 的判定结果。 */
sealed interface GapResolution {
    /** 排队时延未超过阈值，不产生 GAP。 */
    data class WithinBudget(val lagMs: Long) : GapResolution

    /** 同一次过载的重复采样：沿用已记录的 GAP，不重复插入、不再次推进世代。 */
    data class AlreadyRecorded(val gap: AsrGap) : GapResolution

    /** 新过载：已产生新的 GAP、推进世代并清空 ASR 积压。 */
    data class Created(val gap: AsrGap) : GapResolution
}

/**
 * 15 秒跳读协调器：把“ASR 排队时延超过阈值”翻译成一次不可变的 GAP 与一次世代推进。
 *
 * 触发条件是严格大于 [OVERLOAD_THRESHOLD_NS]（等于 15000ms 不触发）。
 * 一次过载只产生一个 GAP：重复采样同一次过载（最早未处理音频仍在已标记区间内）返回
 * [GapResolution.AlreadyRecorded]；只有积压重新增长到已标记区间之后才另起 GAP 和新 ID。
 *
 * 触发后的动作顺序固定：世代自增 → 通过注入的 [clearAsrBacklog] 清空 ASR 待识别积压 →
 * 通过注入的 [onGap] 交出新 GAP 由调用方持久化。协调器不持有也不删除任何音频：
 * AAC 与持久化路径的证据音频完全不受影响。
 *
 * 协调器按会话划分，所有方法线程安全；健康采样与跳读可能来自不同线程。
 */
class GapCoordinator(
    private val sessionId: String,
    private val model: ModelRef,
    private val onGap: (AsrGap) -> Unit = {},
    private val clearAsrBacklog: () -> Unit = {},
    private val utcNowMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
    }

    private var currentGeneration = 0L
    private var gapCount = 0
    private var active: AsrGap? = null

    /** 上一次缺口覆盖到的单调时间上界；用于识别“同一次过载”的重复采样。 */
    private var lastGapEndMonoNs = Long.MIN_VALUE

    /**
     * 用一次过载采样推进状态机。
     *
     * @return 未触发 / 重复采样 / 新 GAP
     */
    @Synchronized
    fun observe(sample: AsrOverloadSample): GapResolution {
        val lagNs = sample.newestCapturedMonoNs - sample.oldestUnprocessedMonoNs
        if (lagNs <= OVERLOAD_THRESHOLD_NS) {
            return GapResolution.WithinBudget(lagNs / NANOS_PER_MILLI)
        }

        val previous = active
        if (previous != null && sample.oldestUnprocessedMonoNs <= lastGapEndMonoNs) {
            // 最早未处理音频仍在已跳过的区间内：这是同一次过载的重复采样。
            return GapResolution.AlreadyRecorded(previous)
        }

        val generation = currentGeneration + 1
        currentGeneration = generation
        val lagMs = lagNs / NANOS_PER_MILLI
        val segment = TranscriptSegment(
            // 缺口 ID 由会话与世代派生：同一世代重复调用得到同一个 ID，满足幂等落库。
            id = "$sessionId:gap:$generation",
            sessionId = sessionId,
            kind = SegmentKind.GAP,
            sourceFinalIds = emptyList(),
            // 缺口自身是“本次跳读发生”的 L0 证据，记录判定时刻的 UTC 墙钟。
            finalizedAtUtcMs = utcNowMs(),
            foldDueAtUtcMs = null,
            captureStartedMonoNs = sample.oldestUnprocessedMonoNs,
            captureEndedMonoNs = sample.newestCapturedMonoNs,
            capturedStartMs = sample.skippedCapturedStartMs,
            capturedEndMs = sample.skippedCapturedEndMs,
            gapReason = GapReason.ASR_OVERLOAD,
        )
        val gap = AsrGap(
            generation = generation,
            lagMs = lagMs,
            segment = segment,
            chunkIds = sample.chunkIds,
            modelId = model.modelId,
            modelVersion = model.modelVersion,
        )
        active = gap
        lastGapEndMonoNs = sample.newestCapturedMonoNs
        gapCount++

        // 先清掉识别副本积压，再交出 GAP：调用方在收到 GAP 时积压已经是空世代。
        clearAsrBacklog()
        onGap(gap)
        return GapResolution.Created(gap)
    }

    /** 当前世代；每次新跳读自增一次。 */
    val generation: Long
        @Synchronized get() = currentGeneration

    /** 已产生的缺口数量（同一次过载的重复采样不计数）。 */
    val gapTotal: Int
        @Synchronized get() = gapCount

    /** 最近一次产生的缺口；未发生过跳读时为 `null`。 */
    val activeGap: AsrGap?
        @Synchronized get() = active

    companion object {
        /** 跳读阈值：严格大于 15 秒才触发。 */
        const val OVERLOAD_THRESHOLD_NS: Long = 15_000_000_000L

        private const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
