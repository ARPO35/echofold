package com.echofold.capture.pipeline

import com.echofold.capture.pcm.AmplitudeSampler
import com.echofold.capture.pcm.AsrSink
import com.echofold.capture.pcm.DispatchResult
import com.echofold.capture.pcm.EncoderSink
import com.echofold.capture.pcm.LifecycleEncoderSink
import com.echofold.capture.source.AudioCaptureError
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
    val capturedMs: Long get() = 0L

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
    val asr: AsrSink?,
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

/** 采集仅读取和入有界队列；ASR 与编码各自消费，暂停/停止先冻结读取再排空并封存。 */
class DefaultRecordingPipeline(
    private val sessionId: String,
    private val audioConfig: AudioRecordConfig,
    private val sourceFactory: AudioRecordFactory,
    private val lanes: CaptureLanes,
) : RecordingPipeline {
    private val readLock = Any()
    private val encoderControl = Any()
    @Volatile private var running = false
    @Volatile private var blocked = false
    @Volatile private var paused = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var listener: RecordingPipelineStateListener? = null
    @Volatile private var acceptedFrames = 0L
    @Volatile private var rotationPending = false
    private var samplesUntilBoundary = Long.MAX_VALUE
    private var source: AudioRecordSource? = null
    private var dispatcher: PcmDispatcher? = null
    private var captureThread: Thread? = null
    private val consumers = ArrayList<Thread>(2)
    private val buffer = ShortArray(lanes.readBufferFrames)
    private val lifecycle get() = lanes.encoder as? LifecycleEncoderSink
    override val capturedMs: Long get() = acceptedFrames * 1000L / (source?.sampleRate ?: actualSampleRate)
    private var actualSampleRate = audioConfig.primarySampleRate
    override val dispatchState: DispatchState get() = if (blocked || paused) DispatchState.BLOCKED else DispatchState.RUNNING

    override fun setStateListener(listener: RecordingPipelineStateListener?) { this.listener = listener }

    override fun start() {
        check(source == null) { "采集管线已打开" }
        val capture = AudioRecordSource(sourceFactory, audioConfig)
        source = capture
        when (val opened = capture.open()) {
            is SourceOpenResult.Failed -> throw RecordingPipelineException(opened.failure.message, opened.failure)
            is SourceOpenResult.Opened -> { actualSampleRate = opened.sampleRate; lifecycle?.start(opened.sampleRate) }
        }
        samplesUntilBoundary = lifecycle?.framesUntilBoundary ?: Long.MAX_VALUE
        val subscriptions = buildList {
            lanes.asr?.let { add(LaneSubscription("asr", lanes.asrCapacityFrames, it)) }
            add(LaneSubscription("encoder", lanes.encoderCapacityFrames, lanes.encoder))
            add(LaneSubscription("amplitude", lanes.amplitudeCapacityFrames, lanes.amplitude))
        }
        dispatcher = PcmDispatcher(sessionId, capture.sampleRate, subscriptions)
        running = true
        lanes.asr?.let { consumers += daemon("echofold-asr") { drainLoop("asr") } }
        consumers += daemon("echofold-encoder") { drainLoop("encoder") }
        captureThread = daemon("echofold-capture") { captureLoop() }
    }

    /** 读取为非阻塞；锁只保护当前 read+submit，不等待任何消费/文件操作。 */
    private fun freeze() {
        synchronized(readLock) { paused = true; source?.pause(); lanes.amplitude.pause() }
    }

    private fun drainAccepted() {
        val dispatch = dispatcher ?: return
        while (dispatch.totalDepth() > 0) {
            dispatch.lastConsumerFailure?.let { throw it }
            LockSupport.parkNanos(1_000_000L)
        }
        dispatch.lastConsumerFailure?.let { throw it }
    }

    override fun pause() {
        freeze()
        drainAccepted()
        synchronized(encoderControl) { lifecycle?.pause() }
        // 读取/背压失败仍保存全部已接受 PCM，但命令不能冒充正常暂停。
        failure?.let { throw it }
    }

    override fun resume() {
        check(!blocked) { "采集已失败，请先停止并检查保存文件" }
        synchronized(encoderControl) { lifecycle?.resume() }
        samplesUntilBoundary = lifecycle?.framesUntilBoundary ?: Long.MAX_VALUE
        rotationPending = false
        synchronized(readLock) {
            source?.resume()
            source?.failureOrNull?.let { throw RecordingPipelineException(it.message, it) }
            lanes.amplitude.resume()
            paused = false
        }
    }

    override fun stopAndRelease() {
        try {
            freeze()
            drainAccepted()
            synchronized(encoderControl) { lifecycle?.stop() }
            // 已保存的是故障切点前的证据，仍返回原始采集失败并保留会话。
            failure?.let { throw it }
        } finally {
            running = false
            captureThread?.join()
            consumers.forEach { it.join() }
            (lanes.asr as? com.echofold.capture.pcm.SessionAsrSink)?.close()
            source?.close()
            source = null
        }
    }

    private fun captureLoop() {
        while (running) {
            try {
                synchronized(readLock) {
                    if (!paused && !blocked && !rotationPending) {
                        val capture = checkNotNull(source)
                        val dispatch = checkNotNull(dispatcher)
                        if (!dispatch.hasCapacity()) throw com.echofold.capture.encoder.RecordingStorageException(
                            "PCM_BACKPRESSURE", "音频队列达到硬上限，已停止麦克风；正在保存已接受的音频")
                        when (val result = capture.read(buffer, length = minOf(buffer.size.toLong(), samplesUntilBoundary).toInt())) {
                            is FrameReadResult.Read -> if (result.frames > 0) {
                                check(dispatch.submit(result.frameIndex, result.firstMonoNs, result.capturedStartMs,
                                    buffer, 0, result.frames) is DispatchResult.Accepted) { "PCM 队列已满，已停止采集，当前读取未提交" }
                                acceptedFrames += result.frames
                                samplesUntilBoundary -= result.frames
                                if (samplesUntilBoundary == 0L) rotationPending = true
                                dispatch.drain("amplitude")
                            }
                            is FrameReadResult.Failed -> throw RecordingPipelineException(result.failure.message, result.failure)
                            else -> Unit
                        }
                    }
                }
            } catch (error: Throwable) { reportFailure(error) }
            LockSupport.parkNanos(1_000_000L)
        }
    }

    private fun drainLoop(laneId: String) {
        val dispatch = checkNotNull(dispatcher)
        while (running) {
            if (dispatch.lastConsumerFailure == null) {
                dispatch.drain(laneId, 8)
                dispatch.lastConsumerFailure?.let { reportFailure(it) }
                if (failure == null && laneId == "encoder" && rotationPending && dispatch.totalDepth() == 0 && !paused) {
                    try {
                        synchronized(encoderControl) {
                            if (!paused) {
                                lifecycle?.prepareNextChunk()
                                samplesUntilBoundary = lifecycle?.framesUntilBoundary ?: Long.MAX_VALUE
                                rotationPending = false
                            }
                        }
                    } catch (error: Throwable) { reportFailure(error) }
                }
            }
            LockSupport.parkNanos(1_000_000L)
        }
    }

    private fun reportFailure(error: Throwable) {
        synchronized(readLock) {
            if (failure != null) return
            failure = error
            blocked = true
            source?.block()
            lanes.amplitude.pause()
        }
        listener?.onStateChanged(DispatchState.BLOCKED,
            (error as? RecordingPipelineException)?.failure
                ?: CaptureFailure(AudioCaptureError.READ_FAILED, error.message ?: "录音采集失败", error))
    }

    private fun daemon(name: String, action: () -> Unit): Thread = Thread(action, name).apply { isDaemon = true; start() }
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
