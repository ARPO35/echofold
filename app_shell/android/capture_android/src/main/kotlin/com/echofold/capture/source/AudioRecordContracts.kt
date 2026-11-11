package com.echofold.capture.source

/**
 * 采集采样率偏好：优先 48kHz，其次 44.1kHz，最后退回 16kHz。
 *
 * 是否真正可用由 [AudioRecordFactory] 探测（`getMinBufferSize` 与初始化状态），
 * 选中值通过 [AudioRecordSource.open] 的返回值上报，不得假定等于首选值。
 */
data class AudioRecordConfig(
    val preferredSampleRates: List<Int> = listOf(48_000, 44_100, 16_000),
    val channelCount: Int = 1,
    val source: AudioSourceKind = AudioSourceKind.VOICE_RECOGNITION,
    /** 读取缓冲区帧数；null 表示由平台 `minBufferSize` 决定。 */
    val readBufferFrames: Int? = null,
) {
    init {
        require(preferredSampleRates.isNotEmpty()) { "至少需要一个采样率候选" }
        require(preferredSampleRates.all { it > 0 }) { "采样率必须为正数：$preferredSampleRates" }
        require(channelCount in 1..2) { "本阶段只支持 1 或 2 声道：$channelCount" }
        require(readBufferFrames == null || readBufferFrames > 0) {
            "readBufferFrames 必须为正数：$readBufferFrames"
        }
    }

    /** 首个候选采样率，用于未打开时的默认上报。 */
    val primarySampleRate: Int get() = preferredSampleRates.first()
}

/** 采集音源类型；语音识别音源通常比 MIC 更少的处理与增益。 */
enum class AudioSourceKind { MIC, VOICE_RECOGNITION }

/** 采集错误分类；UI 与诊断据此给出可操作原因，而不是统一的“录音失败”。 */
enum class AudioCaptureError {
    /** 未授予 `RECORD_AUDIO`，或权限在运行中被撤销。 */
    MIC_PERMISSION_DENIED,

    /** 设备被其他应用占用，或 AudioRecord 处于非法状态。 */
    DEVICE_IN_USE,

    /** 读取返回错误、设备被拔出或底层音频服务失效。 */
    READ_FAILED,

    /** 设备时间戳断裂：音频仍可用，但排队时延与漂移诊断需要标记。 */
    TIMESTAMP_DISCONTINUITY,

    /** 当前设备不支持任何候选采样率/格式组合。 */
    UNSUPPORTED_CONFIGURATION,
}

/** 结构化的采集失败描述。 */
data class CaptureFailure(
    val error: AudioCaptureError,
    val message: String,
    val cause: Throwable? = null,
)

/** 打开 AudioRecord 的结果。 */
sealed interface SourceOpenResult {
    /** 已成功初始化并开始采集；[sampleRate] 是设备实际值。 */
    data class Opened(val sampleRate: Int, val channelCount: Int) : SourceOpenResult

    /** 打开失败；调用方不得因此进入录音态。 */
    data class Failed(val failure: CaptureFailure) : SourceOpenResult
}

/** 一次读取循环的结果。 */
sealed interface FrameReadResult {
    /**
     * 读到 [frames] 帧。
     *
     * [frameIndex] 是本次读取在会话内的单调序号，[firstMonoNs] 为该批首样本的单调时间戳，
     * [capturedStartMs] 为批首在有效音频时间轴上的偏移（暂停间隙已排除）。
     * [frames] 为 0 表示设备暂无数据（非阻塞读取），调用方应短暂退避而不是视为错误。
     */
    data class Read(
        val frames: Int,
        val frameIndex: Long,
        val firstMonoNs: Long,
        val capturedStartMs: Long,
        val timestampDiscontinuity: Boolean,
    ) : FrameReadResult

    /** 采集已暂停（用户暂停或块切换准备），不读取、不推进时间轴。 */
    data object Paused : FrameReadResult

    /** 分发进入 BLOCKED（AAC/持久化硬上限），必须停止读取。 */
    data object Blocked : FrameReadResult

    /** 尚未 [AudioRecordSource.open]。 */
    data object NotOpened : FrameReadResult

    /** 读取失败；失败是粘性的，必须关闭后重新打开。 */
    data class Failed(val failure: CaptureFailure) : FrameReadResult
}

/** 设备单调时钟抽象，便于 JVM 单测注入固定时间。 */
fun interface MonotonicClock {
    fun nanoTime(): Long
}

/**
 * AudioRecord 句柄抽象（生产实现见 `AndroidAudioRecordFactory`）。
 *
 * 各方法允许抛平台异常（[SecurityException]、[IllegalStateException] 等），
 * 由 [AudioRecordSource] 统一分类，工厂实现不负责翻译。
 */
interface AudioRecordHandle {
    val sampleRate: Int
    val channelCount: Int

    /** 开始录音；权限缺失或设备被占用时抛异常。 */
    fun start()

    /**
     * 读取 PCM16 单声道样本到 [destination]。
     *
     * @return 实际读取帧数（≥0）；负值为平台错误码，由 [AudioRecordSource] 分类。
     */
    fun read(destination: ShortArray, offset: Int, length: Int): Int

    /** 停止录音但保留句柄（暂停用）；重复调用必须安全。 */
    fun stop()

    /** 释放句柄；重复调用必须安全。 */
    fun release()

    /** 设备单调时间戳（`AudioRecord.getTimestamp` 语义）；不可用时返回 null。 */
    fun latestTimestampMonoNs(): Long?
}

/** AudioRecord 工厂；JVM 单测用 fake 替换，避免触摸 Android 运行期类。 */
interface AudioRecordFactory {
    fun create(config: AudioRecordConfig): AudioRecordHandle
}
