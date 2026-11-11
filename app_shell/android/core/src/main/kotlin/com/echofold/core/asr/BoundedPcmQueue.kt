package com.echofold.core.asr

import com.echofold.core.asr.engine.PcmFrame

/**
 * `offer` 的两种结果。
 *
 * 不存在“扩容后勉强接受”或“丢弃队首再接受”的第三态：容量固定，满了就拒绝，
 * 由采集线程据此进入 `BLOCKED` 背压，而不是覆盖或静默丢弃已接受帧。
 */
enum class PcmOfferResult {
    /** 帧已进入队列，`depth` 与 `highWatermark` 可能随之增长。 */
    ACCEPTED,

    /** 队列已满或已处于 `BLOCKED`；入参帧未被接收，已接受帧保持原样。 */
    REJECTED_FULL,
}

/**
 * ASR 侧有界的 PCM 帧队列（帧为容量单位，不是样本数）。
 *
 * 采集线程只调用 [offer] 并立即返回，绝不阻塞等待模型推理；ASR 线程调用 [poll]/[peek] 消费。
 * 队列本身不做重采样、不复制样本，只持有 [PcmFrame] 视图。
 *
 * 背压约定（对齐共享契约第 4 节）：
 * - 队列已满时 [offer] 返回 [PcmOfferResult.REJECTED_FULL] 并自动置位 [metrics] 的 `blocked`，
 *   迫使上层停止分发新 PCM，而不是把积压伪装成实时；
 * - `blocked` 一旦置位只能由调用方显式 [setBlocked]`(false)` 解除，避免背压被静默遗忘；
 * - 已接受的帧只能通过 [poll] 或 [clear] 离开队列，`offer` 永不覆盖队首。
 *
 * 所有方法都是线程安全的（同一把对象锁）；锁区只做 O(1) 的队列读写，不含算术或回调。
 */
class BoundedPcmQueue(val capacityFrames: Int) {
    init {
        require(capacityFrames > 0) { "capacityFrames 必须为正数：$capacityFrames" }
    }

    private val frames = ArrayDeque<PcmFrame>(capacityFrames)
    private var highWatermark = 0
    private var newestAcceptedMonoNs = 0L
    private var rejectedCount = 0L
    private var blocked = false

    /**
     * 尝试入队一帧。
     *
     * @return [PcmOfferResult.ACCEPTED] 表示该帧已被接受且后续会按 FIFO 顺序出队；
     *   [PcmOfferResult.REJECTED_FULL] 表示队列已满或处于 `BLOCKED`，入参帧被丢弃且计数。
     */
    @Synchronized
    fun offer(frame: PcmFrame): PcmOfferResult {
        if (blocked || frames.size >= capacityFrames) {
            blocked = true
            rejectedCount++
            return PcmOfferResult.REJECTED_FULL
        }
        frames.addLast(frame)
        if (frame.monoNs > newestAcceptedMonoNs) {
            newestAcceptedMonoNs = frame.monoNs
        }
        if (frames.size > highWatermark) {
            highWatermark = frames.size
        }
        return PcmOfferResult.ACCEPTED
    }

    /** 取出队首帧；队列为空时返回 `null`。调用方拿到的是不可变视图，不需要复制样本。 */
    @Synchronized
    fun poll(): PcmFrame? = frames.removeFirstOrNull()

    /** 查看队首帧但不移除；队列为空时返回 `null`。 */
    @Synchronized
    fun peek(): PcmFrame? = frames.firstOrNull()

    /**
     * 清空队列并返回被移除的帧数。
     *
     * 仅用于 ASR 跳读（GAP）后丢弃识别副本：证据音频在 AAC/持久化路径上独立推进，不受影响。
     * 水位与拒绝计数不因清空而回退，便于观察本次会话的峰值积压。
     */
    @Synchronized
    fun clear(): Int {
        val removed = frames.size
        frames.clear()
        return removed
    }

    /** 显式设置/解除背压；`blocked` 为 `true` 时 [offer] 一律返回 [PcmOfferResult.REJECTED_FULL]。 */
    @Synchronized
    fun setBlocked(blocked: Boolean) {
        this.blocked = blocked
    }

    /** 当前深度（帧）。 */
    @Synchronized
    fun depth(): Int = frames.size

    /**
     * 当前队列指标快照，可直接嵌入 `AsrEvent.Health.queue`。
     *
     * `oldestEnqueuedMonoNs` 取队首帧的单调时间，队列为空时为 `null`；
     * `newestEnqueuedMonoNs` 保留最近一次被接受帧的单调时间（队列排空后不回退），
     * 从未接受过帧时为 `0`，因此它始终不早于队首帧。
     */
    @Synchronized
    fun metrics(): QueueMetrics = QueueMetrics(
        capacity = capacityFrames,
        depth = frames.size,
        highWatermark = highWatermark,
        oldestEnqueuedMonoNs = frames.firstOrNull()?.monoNs,
        newestEnqueuedMonoNs = newestAcceptedMonoNs,
        rejectedCount = rejectedCount,
        blocked = blocked,
    )
}
