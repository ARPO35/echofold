package com.echofold.capture.pipeline

import com.echofold.capture.pcm.AmplitudeSampler
import com.echofold.capture.pcm.AsrSink
import com.echofold.capture.pcm.DispatchResult
import com.echofold.capture.pcm.EncoderSink
import com.echofold.capture.pcm.LaneSubscription
import com.echofold.capture.pcm.PcmDispatcher
import com.echofold.capture.source.AndroidAudioRecordFactory
import com.echofold.capture.source.AudioRecordConfig
import com.echofold.capture.source.AudioRecordFactory
import com.echofold.capture.source.AudioRecordSource
import com.echofold.capture.source.CaptureFailure
import com.echofold.capture.source.FrameReadResult
import com.echofold.capture.source.SourceOpenResult
import com.echofold.core.model.DispatchState
import java.util.concurrent.locks.LockSupport

/**
 * 管线状态回调：背压进入 `BLOCKED`、恢复或采集失败。
 *
 * 由服务注册并在主线程刷新手势；实现可能从采集线程调用，回调必须自己切线程。
 */
fun interface RecordingPipelineStateListener {
    fun onStateChanged(dispatch: DispatchState, failure: CaptureFailure?)
}

/**
 * 一次录音会话的采集管线。
 *
 * 由前台服务在 START 时创建、STOP/DESTROY 时释放；接口与实现分离，便于服务用 fake 验证命令流。
 */
interface RecordingPipeline {
    /** 分发布状态；`BLOCKED` 时 UI 必须显示保存异常。 */
    val dispatchState: DispatchState

    /** 打开麦克风并启动采集/消费线程；失败抛 [RecordingPipelineException]。 */
    fun start()

    /** 用户暂停：停止读取，保留资源。 */
    fun pause()

    /** 用户继续：恢复读取，时间轴从暂停前继续。 */
    fun resume()

    /** 停止并释放全部资源；幂等。 */
    fun stopAndRelease()

    /** 注册状态回调；传 null 解除。实现必须允许在 start 之后注册。 */
    fun setStateListener(listener: RecordingPipelineStateListener?) {
        // 默认实现忽略回调，便于测试用最小 fake。
    }
}

/** 管线启动失败；[failure] 是采集器给出的结构化分类。 */
class RecordingPipelineException(
    message: String,
    val failure: CaptureFailure,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 管线工厂；集成层用真实 ASR 引擎与块编码器构造车道，未就绪时返回 null（服务据此拒绝 START）。 */
fun interface RecordingPipelineFactory {
    fun create(sessionId: String): RecordingPipeline?
}

/**
 * 三条车道的消费者与容量。
 *
 * 容量单位是**帧**（一次 AudioRecord 读取结果），按 48kHz、读取缓冲约 100ms 估算：
 * ASR 车道需覆盖一次模型推理的排队，编码车道需覆盖一次文件写入抖动，振幅车道只需极短缓冲。
 */
data class CaptureLanes(
    val asr: AsrSink,
    val encoder: EncoderSink,
    val amplitude: AmplitudeSampler,
    val asrCapacityFrames: Int = DEFAULT_ASR_CAPACITY,
    val encoderCapacityFrames: Int = DEFAULT_ENCODER_CAPACITY,
    val amplitudeCapacityFrames: Int = DEFAULT_AMPLITUDE_CAPACITY,
    val readBufferFrames: Int = DEFAULT_READ_BUFFER_FRAMES,
) {
    init {
        require(asrCapacityFrames > 0) { "ASR 车道容量必须为正数：$asrCapacityFrames" }
        require(encoderCapacityFrames > 0) { "编码车道容量必须为正数：$encoderCapacityFrames" }
        require(amplitudeCapacityFrames > 0) { "振幅车道容量必须为正数：$amplitudeCapacityFrames" }
        require(readBufferFrames > 0) { "读取缓冲帧数必须为正数：$readBufferFrames" }
    }

    companion object {
        /** 约 8.5s 的 48kHz 音频（每帧 4096 样本）；超过该积压由上层跳读/GAP 处理。 */
        const val DEFAULT_ASR_CAPACITY: Int = 100

        /** 约 17s 的证据音频缓冲，足以吸收一次慢文件写入。 */
        const val DEFAULT_ENCODER_CAPACITY: Int = 200

        const val DEFAULT_AMPLITUDE_CAPACITY: Int = 4

        /** 48kHz 下约 85ms 的读取缓冲。 */
        const val DEFAULT_READ_BUFFER_FRAMES: Int = 4_096
    }
}

/**
 * 默认采集管线：一次 AudioRecord 读取 → [PcmDispatcher] 三条车道。
 *
 * 线程模型（全部守护线程）：
 * - `echofold-capture`：唯一读取 AudioRecord 的线程，只做读取、分发与振幅车道的内联消费；
 * - `echofold-asr` / `echofold-encoder`：各自 drain 自己的车道，慢推理不会阻塞采集线程；
 * - 任一车道拒绝 → 立即停止读取、设置 `source.block()`、`dispatchState = BLOCKED`，不丢已接受帧；
 * - 振幅车道采样成本固定（窗口 RMS），在采集线程内联消费，避免为它单独起线程。
 *
 * 本类只做设备绑定的编排，算法逻辑都在可 JVM 单测的类里。
 */
class DefaultRecordingPipeline(
    private val sessionId: String,
    private val audioConfig: AudioRecordConfig,
    private val sourceFactory: AudioRecordFactory,
    private val lanes: CaptureLanes,
) : RecordingPipeline {

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var blocked: Boolean = false

    /** 触发 BLOCKED 的车道 ID；供诊断与 UI 展示使用。 */
    @Volatile
    var lastBlockingLaneId: String? = null
        private set

    private var source: AudioRecordSource? = null
    private var dispatcher: PcmDispatcher? = null
    private var captureThread: Thread? = null
    private var asrThread: Thread? = null
    private var encoderThread: Thread? = null

    @Volatile
    private var stateListener: RecordingPipelineStateListener? = null

    private val readBuffer = ShortArray(lanes.readBufferFrames)

    override val dispatchState: DispatchState
        get() = if (blocked) DispatchState.BLOCKED else DispatchState.RUNNING

    override fun setStateListener(listener: RecordingPipelineStateListener?) {
        stateListener = listener
    }

    override fun start() {
        if (running) return
        val capture = AudioRecordSource(factory = sourceFactory, config = audioConfig)
        when (val opened = capture.open()) {
            is SourceOpenResult.Failed -> throw RecordingPipelineException(
                "打开麦克风失败：${opened.failure.error}",
                opened.failure,
            )

            is SourceOpenResult.Opened -> Unit
        }
        val activeDispatcher = PcmDispatcher(
            sessionId = sessionId,
            sampleRate = capture.sampleRate,
            subscriptions = listOf(
                LaneSubscription(ASR_LANE, lanes.asrCapacityFrames, lanes.asr),
                LaneSubscription(ENCODER_LANE, lanes.encoderCapacityFrames, lanes.encoder),
                LaneSubscription(AMPLITUDE_LANE, lanes.amplitudeCapacityFrames, lanes.amplitude),
            ),
        )
        source = capture
        dispatcher = activeDispatcher
        running = true
        blocked = false

        captureThread = daemon("echofold-capture") { captureLoop(capture, activeDispatcher) }
        asrThread = daemon("echofold-asr") { drainLoop(activeDispatcher, ASR_LANE) }
        encoderThread = daemon("echofold-encoder") { drainLoop(activeDispatcher, ENCODER_LANE) }
    }

    override fun pause() {
        lanes.amplitude.pause()
        source?.pause()
    }

    override fun resume() {
        lanes.amplitude.resume()
        source?.resume()
    }

    override fun stopAndRelease() {
        running = false
        captureThread?.interrupt()
        asrThread?.interrupt()
        encoderThread?.interrupt()
        listOfNotNull(captureThread, asrThread, encoderThread).forEach { thread ->
            runCatching { thread.join(THREAD_JOIN_TIMEOUT_MS) }
        }
        captureThread = null
        asrThread = null
        encoderThread = null
        source?.close()
        source = null
        dispatcher = null
        blocked = false
        lanes.amplitude.pause()
    }

    private fun captureLoop(capture: AudioRecordSource, activeDispatcher: PcmDispatcher) {
        try {
            while (running) {
                when (val result = capture.read(readBuffer)) {
                    is FrameReadResult.Read -> {
                        if (result.frames == 0) {
                            LockSupport.parkNanos(IDLE_PARK_NANOS)
                            continue
                        }
                        val outcome = activeDispatcher.submit(
                            frameIndex = result.frameIndex,
                            monoNs = result.firstMonoNs,
                            capturedStartMs = result.capturedStartMs,
                            samples = readBuffer,
                            offset = 0,
                            length = result.frames,
                        )
                        when (outcome) {
                            is DispatchResult.Accepted -> {
                                // 振幅车道成本固定，内联消费以降低波形延迟。
                                activeDispatcher.drain(AMPLITUDE_LANE, 1)
                            }

                            is DispatchResult.Backpressured -> {
                                haltForBackpressure(outcome.laneId)
                                return
                            }

                            DispatchResult.Blocked -> {
                                haltForBackpressure(activeDispatcher.blockingLaneId ?: AMPLITUDE_LANE)
                                return
                            }
                        }
                    }

                    FrameReadResult.Paused, FrameReadResult.Blocked ->
                        LockSupport.parkNanos(IDLE_PARK_NANOS)

                    FrameReadResult.NotOpened -> {
                        running = false
                    }

                    is FrameReadResult.Failed -> {
                        running = false
                        lanes.amplitude.pause()
                        stateListener?.onStateChanged(DispatchState.BLOCKED, result.failure)
                    }
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun haltForBackpressure(laneId: String) {
        running = false
        blocked = true
        source?.block()
        // BLOCKED 时不产生新振幅样本（波形冻结）。
        lanes.amplitude.pause()
        stateListener?.onStateChanged(DispatchState.BLOCKED, null)
        // 车道 ID 通过公开指标可查；这里只负责状态推进，诊断由 dispatcher.metricsOf(laneId) 提供。
        lastBlockingLaneId = laneId
    }

    private fun drainLoop(activeDispatcher: PcmDispatcher, laneId: String) {
        while (running) {
            val delivered = activeDispatcher.drain(laneId, DRAIN_BATCH)
            if (delivered == 0) {
                if (activeDispatcher.state == DispatchState.BLOCKED && activeDispatcher.totalDepth() == 0) {
                    // BLOCKED 且已排空：停止空转，等下一次 start/停止。
                    return
                }
                LockSupport.parkNanos(IDLE_PARK_NANOS)
            }
        }
    }

    private fun daemon(name: String, body: () -> Unit): Thread =
        Thread(body, name).apply {
            isDaemon = true
            start()
        }

    private companion object {
        const val ASR_LANE: String = "asr"
        const val ENCODER_LANE: String = "encoder"
        const val AMPLITUDE_LANE: String = "amplitude"
        const val DRAIN_BATCH: Int = 8
        const val IDLE_PARK_NANOS: Long = 1_000_000L
        const val THREAD_JOIN_TIMEOUT_MS: Long = 1_000L
    }
}

/**
 * 默认管线工厂。
 *
 * [lanesFactory] 由集成层提供：同一次采集的 ASR 车道消费者（含重采样）、编码车道消费者
 * （写入分块编码器）与振幅采样器。没有可用车道时返回 null，服务据此拒绝 START 而不是假装录音。
 */
class DefaultRecordingPipelineFactory(
    private val lanesFactory: (sessionId: String) -> CaptureLanes,
    private val audioConfig: AudioRecordConfig = AudioRecordConfig(),
    private val sourceFactory: AudioRecordFactory = AndroidAudioRecordFactory(),
) : RecordingPipelineFactory {

    override fun create(sessionId: String): RecordingPipeline = DefaultRecordingPipeline(
        sessionId = sessionId,
        audioConfig = audioConfig,
        sourceFactory = sourceFactory,
        lanes = lanesFactory(sessionId),
    )
}
