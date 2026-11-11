package com.echofold.capture.pcm

import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.DispatchState

/**
 * 一路 PCM 车道的消费者。
 *
 * [accept] 由消费线程（ASR 推理线程、编码线程或采集线程内联的振幅采样）调用，
 * 不得在内部调用阻塞式模型推理以外的重活；实现 MUST NOT 修改 [PcmFrame.samples]，
 * 因为同一帧缓冲区会同时交给多条车道。
 */
interface PcmLaneConsumer {
    fun accept(frame: PcmFrame)
}

/** ASR 车道消费者；实现方负责把采集采样率降到模型采样率。 */
interface AsrSink : PcmLaneConsumer

/** AAC 编码车道消费者；实现方把 PCM 写入分块编码器。 */
interface EncoderSink : PcmLaneConsumer

/** 振幅车道消费者；实现方通常为 [AmplitudeSampler]。 */
interface AmplitudeSink : PcmLaneConsumer

/**
 * 单路车道的可观测指标。
 *
 * `depth` 是当前排队帧数，`highWatermark` 是历史最高深度，`rejectedCount` 是因该车道
 * 容量耗尽而被拒绝的分发次数；`oldestEnqueuedMonoNs/newestEnqueuedMonoNs` 用于计算排队时延。
 */
data class LaneMetrics(
    val laneId: String,
    val capacityFrames: Int,
    val depth: Int,
    val highWatermark: Int,
    val acceptedCount: Long,
    val rejectedCount: Long,
    val consumerFailureCount: Long,
    val oldestEnqueuedMonoNs: Long?,
    val newestEnqueuedMonoNs: Long?,
    val blocked: Boolean,
)

/** 车道注册信息：容量按车道独立配置，互不影响。 */
data class LaneSubscription(
    val laneId: String,
    val capacityFrames: Int,
    val sink: PcmLaneConsumer,
) {
    init {
        require(laneId.isNotBlank()) { "laneId 不能为空白字符串" }
        require(capacityFrames > 0) { "车道容量必须为正数：$capacityFrames" }
    }
}

/** 单次分发结果。 */
sealed interface DispatchResult {
    /** 所有车道都接受了该帧；[frameIndex] 为该帧在会话内的单调序号。 */
    data class Accepted(val frameIndex: Long) : DispatchResult

    /**
     * 至少一路车道容量耗尽：本帧**没有**被任何车道接受（先校验后提交，避免半分发），
     * 分发状态置为 [DispatchState.BLOCKED]，采集线程必须立即停止读取新 PCM。
     */
    data class Backpressured(val laneId: String, val metrics: LaneMetrics) : DispatchResult

    /** 分发已处于 `BLOCKED`：本帧未被接受，必须由上层完成换块/恢复协议后再放行。 */
    data object Blocked : DispatchResult
}

/**
 * 单路 PCM 有界分发器。
 *
 * 设计要点：
 * 1. 一次采集只向上游要一份 PCM，本类把它复制一份后投递给 ASR、编码、振幅三条车道，
 *    因此三路消费的是同一批音频，不会为证据音频另抢一个采集器；
 * 2. 每条车道容量独立，分发前先统一校验所有车道剩余空间，全部通过才一起入队，
 *    任一路满即整体拒绝并返回 [DispatchResult.Backpressured]，绝不覆盖、绝不静默丢弃已接受帧；
 * 3. [submit] 只在采集线程做一次短临界区与一次数组复制，不做模型推理、不写文件，
 *    消费由 [drain] 在各自消费线程完成；
 * 4. 消费者抛异常时该帧被放回队首、车道与分发状态置 `BLOCKED`，异常不会被当作成功消费。
 *
 * 恢复只允许在块占位协议完成后调用 [releaseBlock]，本类不自行“自动恢复”。
 */
class PcmDispatcher(
    private val sessionId: String,
    /** 采集采样率（48kHz 优先）；ASR 车道消费者负责重采样。 */
    val sampleRate: Int,
    subscriptions: List<LaneSubscription>,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(subscriptions.isNotEmpty()) { "至少需要一条分发车道" }
        require(subscriptions.map { it.laneId }.toSet().size == subscriptions.size) {
            "车道 ID 必须唯一：${subscriptions.map { it.laneId }}"
        }
    }

    private val lock = Any()
    private val lanes: List<Lane> = subscriptions.map { Lane(it) }
    private val lanesById: Map<String, Lane> = lanes.associateBy { it.id }

    @Volatile
    private var dispatchState: DispatchState = DispatchState.RUNNING

    /** 最近一次触发背压的车道；`BLOCKED` 期间保持不变，便于诊断与恢复。 */
    @Volatile
    var blockingLaneId: String? = null
        private set

    private var lastAcceptedFrameIndex: Long = -1L

    /** 当前分发状态；`BLOCKED` 表示必须停止读取新 PCM。 */
    val state: DispatchState get() = dispatchState

    /** 已接受帧总数（每条车道相同）。 */
    val acceptedFrameCount: Long
        get() = synchronized(lock) { lanes.first().acceptedCount }

    /**
     * 分发一帧 PCM。
     *
     * [samples] 是采集侧的复用缓冲区，本类在入队前复制 `[offset, offset+length)` 区间，
     * 因此调用方可以立即复用它继续读取。[frameIndex] 必须严格递增。
     */
    fun submit(
        frameIndex: Long,
        monoNs: Long,
        capturedStartMs: Long,
        samples: ShortArray,
        offset: Int = 0,
        length: Int = samples.size,
    ): DispatchResult {
        require(frameIndex >= 0L) { "frameIndex 不能为负数：$frameIndex" }
        require(monoNs >= 0L) { "monoNs 不能为负数：$monoNs" }
        require(capturedStartMs >= 0L) { "capturedStartMs 不能为负数：$capturedStartMs" }
        require(offset >= 0 && length > 0 && offset + length <= samples.size) {
            "非法的 PCM 视图：offset=$offset length=$length samples=${samples.size}"
        }

        synchronized(lock) {
            if (dispatchState == DispatchState.BLOCKED) {
                // 已 BLOCKED：不重复计入拒绝次数，也不触碰任何车道。
                return DispatchResult.Blocked
            }
            require(frameIndex > lastAcceptedFrameIndex) {
                "frameIndex 必须严格递增：$frameIndex 不大于 $lastAcceptedFrameIndex"
            }

            // 先校验全部车道，再统一提交：任一路满都不会产生“半分发”。
            val saturated = lanes.firstOrNull { it.depth >= it.capacity }
            if (saturated != null) {
                saturated.rejectedCount++
                enterBlockedLocked(saturated.id)
                return DispatchResult.Backpressured(saturated.id, saturated.metricsLocked())
            }

            val frame = PcmFrame(
                sessionId = sessionId,
                frameIndex = frameIndex,
                monoNs = monoNs,
                capturedStartMs = capturedStartMs,
                sampleRate = sampleRate,
                samples = samples.copyOfRange(offset, offset + length),
            )
            for (lane in lanes) {
                lane.frames.addLast(frame)
                lane.depth++
                lane.acceptedCount++
                if (lane.depth > lane.highWatermark) {
                    lane.highWatermark = lane.depth
                }
                lane.newestEnqueuedMonoNs = monoNs
                if (lane.oldestEnqueuedMonoNs == null) {
                    lane.oldestEnqueuedMonoNs = monoNs
                }
            }
            lastAcceptedFrameIndex = frameIndex
            return DispatchResult.Accepted(frameIndex)
        }
    }

    /**
     * 在消费线程取走车道里的帧并交给消费者。
     *
     * @return 实际消费的帧数；单次调用最多消费 [maxFrames] 帧。
     */
    fun drain(laneId: String, maxFrames: Int = Int.MAX_VALUE): Int {
        require(maxFrames > 0) { "maxFrames 必须为正数：$maxFrames" }
        val lane = lanesById[laneId] ?: throw IllegalArgumentException("未知车道：$laneId")

        var consumed = 0
        while (consumed < maxFrames) {
            val frame = synchronized(lock) { lane.frames.removeFirstOrNull() }
                ?: return consumed
            try {
                lane.sink.accept(frame)
                consumed++
            } catch (error: Throwable) {
                // 消费者异常不得伪装成成功消费：把该帧放回队首并进入 BLOCKED。
                synchronized(lock) {
                    lane.frames.addFirst(frame)
                    lane.consumerFailureCount++
                    enterBlockedLocked(lane.id)
                }
                return consumed
            }
            synchronized(lock) {
                lane.depth--
                lane.oldestEnqueuedMonoNs = lane.frames.firstOrNull()?.monoNs
            }
        }
        return consumed
    }

    /** 车道指标快照（按注册顺序）。 */
    fun metrics(): List<LaneMetrics> = synchronized(lock) { lanes.map { it.metricsLocked() } }

    /** 指定车道指标；未知车道返回 null。 */
    fun metricsOf(laneId: String): LaneMetrics? = synchronized(lock) { lanesById[laneId]?.metricsLocked() }

    /**
     * 在块占位协议完成、编码器初始化成功并置 `OPEN` 之后放行分发。
     *
     * 本方法只清除 `BLOCKED` 标记并保留历史计数；调用方必须先完成契约第 5 节的占位顺序。
     *
     * @return 调用前是否处于 `BLOCKED`。
     */
    fun releaseBlock(): Boolean = synchronized(lock) {
        val wasBlocked = dispatchState == DispatchState.BLOCKED
        dispatchState = DispatchState.RUNNING
        blockingLaneId = null
        lanes.forEach { it.blocked = false }
        wasBlocked
    }

    /** 供健康上报使用：所有车道排空后仍可能处于 `BLOCKED`。 */
    fun totalDepth(): Int = synchronized(lock) { lanes.sumOf { it.depth } }

    private fun enterBlockedLocked(laneId: String) {
        dispatchState = DispatchState.BLOCKED
        blockingLaneId = laneId
        lanes.forEach { it.blocked = true }
    }

    private class Lane(subscription: LaneSubscription) {
        val id: String = subscription.laneId
        val capacity: Int = subscription.capacityFrames
        val sink: PcmLaneConsumer = subscription.sink
        val frames: ArrayDeque<PcmFrame> = ArrayDeque(capacity)
        var depth: Int = 0
        var highWatermark: Int = 0
        var acceptedCount: Long = 0
        var rejectedCount: Long = 0
        var consumerFailureCount: Long = 0
        var oldestEnqueuedMonoNs: Long? = null
        var newestEnqueuedMonoNs: Long? = null
        var blocked: Boolean = false

        fun metricsLocked(): LaneMetrics = LaneMetrics(
            laneId = id,
            capacityFrames = capacity,
            depth = depth,
            highWatermark = highWatermark,
            acceptedCount = acceptedCount,
            rejectedCount = rejectedCount,
            consumerFailureCount = consumerFailureCount,
            oldestEnqueuedMonoNs = oldestEnqueuedMonoNs,
            newestEnqueuedMonoNs = newestEnqueuedMonoNs,
            blocked = blocked,
        )
    }
}
