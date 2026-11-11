package com.echofold.core.asr

import java.util.TreeMap

/**
 * ASR 事件准入门，位于原生事件投递与阅读流/持久化之间。
 *
 * 三条收敛规则（对齐共享契约第 3 节）：
 * 1. **eventId 去重**：同一事件重复投递（恢复、重放、Health 反复采样）只准入一次；
 * 2. **generation 单调**：低于当前世代的事件一律 [Verdict.STALE]，绝不写入 GAP 或其他段；
 *    收到更高世代说明发生过跳读，旧世代的缓冲立即作废；
 * 3. **同世代按 seq 收敛**：缺口在 [outOfOrderWindow] 以内的事件先缓冲，等缺失序号到达后
 *    按 seq 升序一次性交付；缺口超过窗口（或序号已落后于已交付位置）的事件丢弃并计数。
 *
 * “交付”是唯一的副作用入口：[Admission.deliverable] 为空表示调用方不得应用任何内容，
 * 因此重复 Health 采样不会触发额外副作用（不重复插入 GAP、不重复推进水位）。
 *
 * 门是按会话划分的（[sessionId] 在构造时固定），所有方法线程安全。
 */
class AsrEventGate(
    private val sessionId: String,
    val outOfOrderWindow: Int = DEFAULT_OUT_OF_ORDER_WINDOW,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(outOfOrderWindow > 0) { "outOfOrderWindow 必须为正数：$outOfOrderWindow" }
    }

    /** 一次准入的判定结果。 */
    enum class Verdict {
        /** 事件可立即应用；[Admission.deliverable] 中按 seq 升序给出本次解锁的全部事件。 */
        ACCEPTED,

        /** 该 eventId 已准入过；无任何副作用。 */
        DUPLICATE,

        /** 属于已失效的旧世代；无任何副作用。 */
        STALE,

        /** 同一个世代内顺序未到：已在窗口内缓冲，或超出窗口被丢弃（见 [Admission.dropped]）。 */
        OUT_OF_ORDER,
    }

    /**
     * 准入结果。
     *
     * [deliverable] 非空时，调用方必须按给定顺序依次应用；
     * [dropped] 为 `true` 表示本次事件被丢弃（迟到或超窗），调用方不得应用它。
     */
    data class Admission(
        val verdict: Verdict,
        val deliverable: List<AsrEvent>,
        val dropped: Boolean,
    )

    /** 已准入事件的 eventId（有界 LRU 窗口，覆盖重放与重复采样）。 */
    private val seenEventIds = LinkedHashSet<String>()

    /** 同世代内超前到达、等待更小 seq 的事件。 */
    private val pending = TreeMap<Long, AsrEvent>()

    private var currentGeneration = 0L

    /** 当前世代下一个期望的 seq；`null` 表示该世代尚未收到任何事件。 */
    private var nextSeq: Long? = null

    private var deliveredCount = 0L
    private var duplicateCount = 0L
    private var staleCount = 0L
    private var droppedOutOfOrderCount = 0L

    /**
     * 准入一个事件。
     *
     * @throws IllegalArgumentException 事件属于其他会话（门按会话划分，混用即编程错误）。
     */
    @Synchronized
    fun admit(event: AsrEvent): Admission {
        require(event.sessionId == sessionId) {
            "事件属于会话 ${event.sessionId}，不能进入会话 $sessionId 的准入门"
        }
        if (!seenEventIds.add(event.eventId)) {
            duplicateCount++
            return Admission(Verdict.DUPLICATE, emptyList(), dropped = false)
        }
        trimSeenEventIds()

        if (event.generation < currentGeneration) {
            staleCount++
            return Admission(Verdict.STALE, emptyList(), dropped = false)
        }
        if (event.generation > currentGeneration) {
            // 更高世代 = 发生过跳读：旧世代的等待缓冲已经不可能再收敛，直接作废。
            currentGeneration = event.generation
            pending.clear()
            nextSeq = event.seq + 1
            deliveredCount++
            return Admission(Verdict.ACCEPTED, listOf(event), dropped = false)
        }

        val expected = nextSeq
        if (expected == null) {
            // 该世代的首个事件建立序号基线；采集流从任意 seq 起算都合法。
            nextSeq = event.seq + 1
            deliveredCount++
            return Admission(Verdict.ACCEPTED, listOf(event), dropped = false)
        }
        if (event.seq < expected) {
            // 已经越过该序号（此前交付过或判定为缺口），迟到的结果不得回灌。
            droppedOutOfOrderCount++
            return Admission(Verdict.OUT_OF_ORDER, emptyList(), dropped = true)
        }
        if (event.seq > expected) {
            if (event.seq - expected > outOfOrderWindow) {
                droppedOutOfOrderCount++
                return Admission(Verdict.OUT_OF_ORDER, emptyList(), dropped = true)
            }
            pending[event.seq] = event
            return Admission(Verdict.OUT_OF_ORDER, emptyList(), dropped = false)
        }

        // 正好补上缺口：按 seq 升序交付它以及所有被解锁的缓冲事件。
        val ready = ArrayList<AsrEvent>(pending.size + 1)
        ready.add(event)
        var cursor = event.seq + 1
        while (true) {
            val buffered = pending.remove(cursor) ?: break
            ready.add(buffered)
            cursor++
        }
        nextSeq = cursor
        deliveredCount += ready.size
        return Admission(Verdict.ACCEPTED, ready, dropped = false)
    }

    /**
     * 显式推进世代（跳读后调用）；单调，传入更小值不生效。
     *
     * @return 生效后的当前世代
     */
    @Synchronized
    fun advanceGeneration(generation: Long): Long {
        require(generation >= 0L) { "generation 不能为负数：$generation" }
        if (generation > currentGeneration) {
            currentGeneration = generation
            pending.clear()
            nextSeq = null
        }
        return currentGeneration
    }

    /** 当前世代；初始为 0。 */
    val generation: Long
        @Synchronized get() = currentGeneration

    /** 已交付事件数（含由缺口解锁的缓冲事件）。 */
    val deliveredEventCount: Long
        @Synchronized get() = deliveredCount

    /** 重复投递次数。 */
    val duplicateEventCount: Long
        @Synchronized get() = duplicateCount

    /** 旧世代事件拒绝次数。 */
    val staleEventCount: Long
        @Synchronized get() = staleCount

    /** 因迟到或超出乱序窗口而丢弃的事件数。 */
    val droppedOutOfOrderEventCount: Long
        @Synchronized get() = droppedOutOfOrderCount

    /** 当前仍在等待更小序号的事件数。 */
    val bufferedEventCount: Int
        @Synchronized get() = pending.size

    /** 去重窗口已记录的事件 ID 数，用于诊断内存上界。 */
    val trackedEventIdCount: Int
        @Synchronized get() = seenEventIds.size

    private fun trimSeenEventIds() {
        val iterator = seenEventIds.iterator()
        while (seenEventIds.size > MAX_TRACKED_EVENT_IDS && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    companion object {
        /** 默认乱序缓冲窗口：允许等待的最大 seq 缺口（缓冲条目数自然也被它约束）。 */
        const val DEFAULT_OUT_OF_ORDER_WINDOW: Int = 8

        /**
         * 去重窗口容量。
         *
         * 超出后最旧的 eventId 被淘汰；被淘汰事件的重复投递会退化为 [Verdict.OUT_OF_ORDER] 被丢弃，
         * 因而仍然不会被应用第二次，只是不再报告为 DUPLICATE。
         */
        const val MAX_TRACKED_EVENT_IDS: Int = 4096
    }
}
