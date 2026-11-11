package com.echofold.core.time

import com.echofold.core.model.ClockAnchor
import com.echofold.core.model.ClockQuality
import com.echofold.core.model.requireNonNegative
import com.echofold.core.model.requireRange

/**
 * 会话有效音频时间轴：把同一锚点内的设备单调时钟映射为排除暂停间隙的采集 offset。
 *
 * 时间域约定：
 * - `monoNs` 属于设备单调时钟，只在 [anchor] 指定的同一锚点内可解释；
 * - `utcMs` 只记录到 [CaptureSpan]，用于持久化与展示，任何校时（回拨或前跳）都不得
 *   改变已经换算出的 `capturedStartMs/capturedEndMs`，也不得改写块边界；
 * - `currentCapturedMs` 与 `capturedMsAt` 返回的是会话实际采集内容的累计时长，
 *   暂停期间不增长，继续后从暂停前结束 offset 继续。
 *
 * 重启语义：进程/设备重启后必须调用 [anchorForRestart] 建立
 * [ClockQuality.RESTART_DEGRADED] 新锚点。旧锚点的裸 `monoNs` 落进已退役区间或早于
 * 新锚点起点时一律抛 [IllegalArgumentException]，绝不与新锚点数值直接比较。
 *
 * 线程安全：全部公开操作以对象锁串行化，可由采集线程与 UI/状态机线程共享。
 */
class SessionTimeline(
    val sessionId: String,
    initialAnchor: ClockAnchor,
    private val clock: ClockSource = SystemClockSource,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(initialAnchor.sessionId == sessionId) {
            "initialAnchor.sessionId(${initialAnchor.sessionId}) 必须等于会话 id($sessionId)"
        }
    }

    /** 一个已结束的采集段；暂停间隙不产生记录，因此 offset 在继续后保持连续。 */
    data class CaptureSpan(
        val captureStartedMonoNs: Long,
        val captureEndedMonoNs: Long,
        val capturedStartMs: Long,
        val capturedEndMs: Long,
        val startedAtUtcMs: Long,
        val endedAtUtcMs: Long,
    ) {
        init {
            requireNonNegative(captureStartedMonoNs, "captureStartedMonoNs")
            requireNonNegative(captureEndedMonoNs, "captureEndedMonoNs")
            require(captureEndedMonoNs >= captureStartedMonoNs) {
                "captureEndedMonoNs($captureEndedMonoNs) 不能早于 captureStartedMonoNs($captureStartedMonoNs)"
            }
            requireRange(capturedStartMs, capturedEndMs, "capturedStartMs", "capturedEndMs")
            // UTC 允许被校时回拨，因此不校验 startedAtUtcMs <= endedAtUtcMs。
            requireNonNegative(startedAtUtcMs, "startedAtUtcMs")
            requireNonNegative(endedAtUtcMs, "endedAtUtcMs")
        }
    }

    /** 已记录的块切点：`chunkSeq` 严格递增，`capturedMs` 单调不减且与 UTC 校时无关。 */
    data class ChunkBoundary(
        val chunkSeq: Int,
        val monoNs: Long,
        val capturedMs: Long,
    ) {
        init {
            require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
            requireNonNegative(monoNs, "monoNs")
            requireNonNegative(capturedMs, "capturedMs")
        }
    }

    private data class CaptureSpanRecord(
        val startMonoNs: Long,
        val endMonoNs: Long,
        val baseCapturedNs: Long,
        val endCapturedNs: Long,
        val startedAtUtcMs: Long,
        val endedAtUtcMs: Long,
    ) {
        fun toPublic(): CaptureSpan = CaptureSpan(
            captureStartedMonoNs = startMonoNs,
            captureEndedMonoNs = endMonoNs,
            capturedStartMs = nsToMs(baseCapturedNs),
            capturedEndMs = nsToMs(endCapturedNs),
            startedAtUtcMs = startedAtUtcMs,
            endedAtUtcMs = endedAtUtcMs,
        )
    }

    private var activeAnchor: ClockAnchor = initialAnchor

    /** 已退役锚点的单调时钟区间；新锚点内出现这些裸值时视为跨锚点比较并拒绝。 */
    private val retiredMonoRanges = mutableListOf<LongRange>()
    private val spans = mutableListOf<CaptureSpanRecord>()
    private val boundaries = mutableListOf<ChunkBoundary>()

    private var capturing = false
    private var segmentStartMonoNs = 0L
    private var segmentBaseCapturedNs = 0L
    private var segmentStartedAtUtcMs = 0L

    /** 所有已结束采集段的累计纳秒；采集进行中不更新，避免暂停间隙被计入。 */
    private var capturedTotalNs = 0L
    private var lastObservedMonoNs = initialAnchor.monoNs
    private var lastChunkSeq = -1
    private var lastBoundaryMonoNs = initialAnchor.monoNs
    private var lastBoundaryCapturedMs = 0L

    /** 当前生效的时钟锚点；重启后为 [ClockQuality.RESTART_DEGRADED]。 */
    @get:Synchronized
    val anchor: ClockAnchor
        get() = activeAnchor

    /** 当前锚点质量，便于诊断区分正常开机时钟与重启降级锚点。 */
    @get:Synchronized
    val quality: ClockQuality
        get() = activeAnchor.quality

    /** 是否处于采集段内（暂停后为 false，继续后再次为 true）。 */
    @get:Synchronized
    val isCapturing: Boolean
        get() = capturing

    /**
     * 截至当前时刻的有效音频 offset。
     *
     * 采集进行中按注入的 [clock] 投影到本段起点之后；暂停时停留在最后一次结束 offset。
     */
    @get:Synchronized
    val currentCapturedMs: Long
        get() {
            if (capturing) {
                val nowNs = clock.monoNowNs()
                if (nowNs >= segmentStartMonoNs) {
                    return nsToMs(segmentBaseCapturedNs + (nowNs - segmentStartMonoNs))
                }
            }
            return nsToMs(capturedTotalNs)
        }

    /** 已结束采集段的有序快照；顺序即实际采集顺序。 */
    @Synchronized
    fun captureSpans(): List<CaptureSpan> = spans.map(CaptureSpanRecord::toPublic)

    /** 已记录块切点的有序快照；`capturedMs` 单调不减。 */
    @Synchronized
    fun chunkBoundaries(): List<ChunkBoundary> = boundaries.toList()

    /**
     * 开始一个新的采集段。
     *
     * @param monoNs 当前设备单调时钟纳秒，必须落在当前锚点内且不早于最近观察值。
     * @param utcMs 当前 UTC 墙钟毫秒，仅记录，不参与 offset 计算。
     */
    @Synchronized
    fun onCaptureStarted(monoNs: Long, utcMs: Long) {
        requireMonoInActiveAnchor(monoNs)
        requireNonNegative(utcMs, "utcMs")
        check(!capturing) { "会话已有进行中的采集段，不能重复 onCaptureStarted(monoNs=$monoNs)" }
        require(monoNs >= lastObservedMonoNs) {
            "onCaptureStarted 的 monoNs($monoNs) 早于最近观察值($lastObservedMonoNs)，单调时钟不得回退"
        }

        capturing = true
        segmentStartMonoNs = monoNs
        segmentBaseCapturedNs = capturedTotalNs
        segmentStartedAtUtcMs = utcMs
        lastObservedMonoNs = monoNs
    }

    /**
     * 结束当前采集段并返回其在会话时间轴上的起止 offset。
     *
     * 暂停、普通停止与块封存共用该方法；两次调用之间流逝的单调时间不会被计入 offset。
     */
    @Synchronized
    fun onCaptureStopped(monoNs: Long, utcMs: Long): CaptureSpan {
        requireMonoInActiveAnchor(monoNs)
        requireNonNegative(utcMs, "utcMs")
        check(capturing) { "会话没有进行中的采集段，不能 onCaptureStopped(monoNs=$monoNs)" }
        require(monoNs >= segmentStartMonoNs) {
            "onCaptureStopped 的 monoNs($monoNs) 早于本段起点($segmentStartMonoNs)"
        }

        val endCapturedNs = segmentBaseCapturedNs + (monoNs - segmentStartMonoNs)
        val record = CaptureSpanRecord(
            startMonoNs = segmentStartMonoNs,
            endMonoNs = monoNs,
            baseCapturedNs = segmentBaseCapturedNs,
            endCapturedNs = endCapturedNs,
            startedAtUtcMs = segmentStartedAtUtcMs,
            endedAtUtcMs = utcMs,
        )
        spans += record
        capturedTotalNs = endCapturedNs
        capturing = false
        lastObservedMonoNs = monoNs
        return record.toPublic()
    }

    /**
     * 记录一个块切点。
     *
     * 规则：`chunkSeq` 严格递增，同一锚点内切点单调时间不回退，换算出的 `capturedMs`
     * 不得小于上一个切点。暂停期间的切点取上一个采集段结束 offset，不把暂停时长计入。
     *
     * @return 本次切点在会话有效音频时间轴上的位置。
     */
    @Synchronized
    fun onChunkBoundary(chunkSeq: Int, monoNs: Long): ChunkBoundary {
        require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
        requireMonoInActiveAnchor(monoNs)
        require(chunkSeq > lastChunkSeq) {
            "块序号必须严格递增：当前 $chunkSeq，上一块 $lastChunkSeq"
        }
        require(monoNs >= lastBoundaryMonoNs) {
            "块边界的 monoNs($monoNs) 早于上一切点($lastBoundaryMonoNs)，单调时钟不得回退"
        }

        val capturedMs = offsetAtBoundary(monoNs)
        require(capturedMs >= lastBoundaryCapturedMs) {
            "块边界 offset 不得回退：当前 ${capturedMs}ms，上一切点 ${lastBoundaryCapturedMs}ms"
        }

        val boundary = ChunkBoundary(chunkSeq = chunkSeq, monoNs = monoNs, capturedMs = capturedMs)
        boundaries += boundary
        lastChunkSeq = chunkSeq
        lastBoundaryMonoNs = monoNs
        lastBoundaryCapturedMs = capturedMs
        return boundary
    }

    /**
     * 把当前锚点内某个单调时钟值映射为有效音频 offset。
     *
     * 落在采集段内（或当前进行中的段起点之后）返回精确 offset；落在暂停间隙、
     * 首个采集段之前或会话最后一次采集之后返回 `null`。跨锚点裸值抛
     * [IllegalArgumentException]，绝不静默返回旧锚点的数值。
     */
    @Synchronized
    fun capturedMsAt(monoNs: Long): Long? {
        requireMonoInActiveAnchor(monoNs)

        findSpan(monoNs)?.let { span ->
            return nsToMs(span.baseCapturedNs + (monoNs - span.startMonoNs))
        }
        if (capturing && monoNs >= segmentStartMonoNs) {
            return nsToMs(segmentBaseCapturedNs + (monoNs - segmentStartMonoNs))
        }
        return null
    }

    /**
     * 重启后建立新锚点，旧锚点立即退役。
     *
     * 新锚点质量固定为 [ClockQuality.RESTART_DEGRADED]；`capturedTotalNs` 与已记录块边界
     * 保持不变，因此会话有效音频 offset 跨重启继续。调用前必须先结束当前采集段。
     *
     * @return 新建的锚点，供 `RecordingSession.clockAnchor` 持久化。
     */
    @Synchronized
    fun anchorForRestart(monoNs: Long, utcMs: Long): ClockAnchor {
        requireNonNegative(monoNs, "monoNs")
        requireNonNegative(utcMs, "utcMs")
        check(!capturing) { "重启锚点前必须先调用 onCaptureStopped 结束当前采集段" }

        // 旧锚点只记录实际观察过的区间；该区间内的裸值在新锚点内无法区分来源，必须拒绝。
        val retiredEnd = maxOf(lastObservedMonoNs, activeAnchor.monoNs)
        retiredMonoRanges += activeAnchor.monoNs..retiredEnd

        val restarted = ClockAnchor(
            sessionId = sessionId,
            utcMs = utcMs,
            monoNs = monoNs,
            quality = ClockQuality.RESTART_DEGRADED,
        )
        activeAnchor = restarted
        lastObservedMonoNs = monoNs
        // 块序号与 offset 继续；单调时钟基准切回新锚点，避免旧锚点数值参与比较。
        lastBoundaryMonoNs = monoNs
        return restarted
    }

    /**
     * 强制单调时钟输入属于当前锚点。
     *
     * 判定顺序：非负 → 不落在任何已退役锚点的观察区间 → 不早于新锚点起点。
     * 新锚点瞬间本身视为新时钟的起点，允许使用。
     */
    private fun requireMonoInActiveAnchor(monoNs: Long) {
        requireNonNegative(monoNs, "monoNs")
        val anchorMonoNs = activeAnchor.monoNs
        if (monoNs != anchorMonoNs) {
            for (range in retiredMonoRanges) {
                require(monoNs !in range) {
                    "monoNs($monoNs) 落在已退役锚点区间 ${range.first}..${range.last}，" +
                        "旧锚点的裸单调值不得与新锚点比较"
                }
            }
        }
        require(monoNs >= anchorMonoNs) {
            "monoNs($monoNs) 早于当前锚点起点($anchorMonoNs)（质量=${activeAnchor.quality}），" +
                "该值属于旧锚点，不能与新锚点裸比较"
        }
    }

    /** 二分查找精确覆盖 [monoNs] 的已结束采集段。 */
    private fun findSpan(monoNs: Long): CaptureSpanRecord? {
        var low = 0
        var high = spans.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val span = spans[mid]
            when {
                monoNs < span.startMonoNs -> high = mid - 1
                monoNs > span.endMonoNs -> low = mid + 1
                else -> return span
            }
        }
        return null
    }

    /**
     * 计算块边界位置：采集段内取精确 offset；间隙或段外取该时刻之前最后一段的结束 offset。
     * 暂停间隙因此不贡献任何 offset，下一块起点等于上一块结束。
     */
    private fun offsetAtBoundary(monoNs: Long): Long {
        findSpan(monoNs)?.let { span ->
            return nsToMs(span.baseCapturedNs + (monoNs - span.startMonoNs))
        }
        val previousEndMs = lastSpanEndingAtOrBefore(monoNs)?.let { nsToMs(it.endCapturedNs) } ?: 0L
        if (capturing && monoNs >= segmentStartMonoNs) {
            val currentMs = nsToMs(segmentBaseCapturedNs + (monoNs - segmentStartMonoNs))
            return maxOf(previousEndMs, currentMs)
        }
        return previousEndMs
    }

    /** 找到起点不晚于 [monoNs] 的最后一段；用于间隙中的边界定位。 */
    private fun lastSpanEndingAtOrBefore(monoNs: Long): CaptureSpanRecord? {
        var low = 0
        var high = spans.size - 1
        var candidate: CaptureSpanRecord? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            val span = spans[mid]
            if (span.startMonoNs <= monoNs) {
                candidate = span
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return candidate
    }

}

/** 纳秒转毫秒向下取整；输入非负，保持 offset 单调不减。 */
private fun nsToMs(ns: Long): Long = ns / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L
