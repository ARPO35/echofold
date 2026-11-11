package com.echofold.capture.encoder

/** 编码器硬件/参数抽象层的输入格式；不依赖 `android.media.MediaFormat`，便于 JVM 单测。 */
data class AudioEncoderFormat(
    val mimeType: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitRate: Int,
    val aacProfile: AacProfile = AacProfile.LC,
    /** 编码器输出格式里的 `csd-0`（AAC AudioSpecificConfig）；封装器建轨需要。 */
    val codecSpecificData: ByteArray? = null,
) {
    init {
        require(mimeType.isNotBlank()) { "mimeType 不能为空白字符串" }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(channelCount > 0) { "channelCount 必须为正数：$channelCount" }
        require(bitRate > 0) { "bitRate 必须为正数：$bitRate" }
    }

    /** 转为诊断用的实际能力描述。 */
    fun toCapability(codecName: String): EncoderCapability = EncoderCapability(
        codecName = codecName,
        mimeType = mimeType,
        sampleRate = sampleRate,
        bitRate = bitRate,
        channelCount = channelCount,
    )
}

/** 编码器输出缓冲的标志位；数值与 `MediaCodec.BUFFER_FLAG_*` 对齐。 */
object EncodedBufferFlags {
    const val KEY_FRAME: Int = 1
    const val CODEC_CONFIG: Int = 2
    const val END_OF_STREAM: Int = 4
}

/**
 * 一段已编码输出。
 *
 * [data] 是整段字节；[offset]/[size] 为有效区间（生产实现会复制出等长数组，offset 恒为 0）。
 */
data class EncodedBuffer(
    val data: ByteArray,
    val offset: Int,
    val size: Int,
    val presentationTimeUs: Long,
    val flags: Int,
    val isCodecConfig: Boolean,
) {
    init {
        require(offset >= 0 && size >= 0 && offset + size <= data.size) {
            "非法的编码输出视图：offset=$offset size=$size data=${data.size}"
        }
        require(presentationTimeUs >= 0L) { "presentationTimeUs 不能为负数：$presentationTimeUs" }
    }

    val isEndOfStream: Boolean get() = flags and EncodedBufferFlags.END_OF_STREAM != 0
}

/**
 * 编码器抽象（生产实现包装 `MediaCodec`，测试用 fake）。
 *
 * 契约：`configure → start → queueInput / dequeueOutput → signalEndOfStream → release`。
 * 平台错误以异常上抛，由 [AacChunkEncoder] 转成结构化 [EncoderFailure]。
 */
interface AudioEncoderCodec {
    /** 平台编码器名称；未配置时可返回空串。 */
    val codecName: String

    /** 配置编码器；失败必须抛异常。 */
    fun configure(format: AudioEncoderFormat)

    /** 启动编码器；必须在 [configure] 之后。 */
    fun start()

    /** 送入 PCM16 单声道样本；[presentationTimeUs] 为有效音频时间轴上的显示时间。 */
    fun queueInput(samples: ShortArray, offset: Int, length: Int, presentationTimeUs: Long)

    /** 取一个已编码输出；暂无输出返回 null（非 EOS）。 */
    fun dequeueOutput(): EncodedBuffer?

    /** 通知输入结束，触发尾部冲刷。 */
    fun signalEndOfStream()

    /** 编码器输出格式（含 csd-0）；尚未产生输出格式时返回 null。 */
    fun actualFormat(): AudioEncoderFormat?

    /** 释放编码器；幂等。 */
    fun release()
}

/**
 * 容器封装抽象（生产实现包装 `MediaMuxer`，测试用 fake）。
 *
 * 写入方必须先调用 [open]；首个样本写入时用编码器实际格式建轨并 start 封装器。
 */
interface AudioContainerWriter {
    /** 创建封装器并绑定 [path]；不得覆盖已存在的完整文件（由块占位协议保证路径唯一）。 */
    fun open(path: String)

    /** 写入一段编码样本；首次调用时用 [format] 建轨并启动封装器。 */
    fun writeSample(buffer: EncodedBuffer, format: AudioEncoderFormat)

    /** 写完索引并释放封装器；失败必须抛异常。 */
    fun finalizeAndClose()

    /** 放弃封装并尽力释放资源，保留已写入文件用于恢复诊断。 */
    fun abort()
}

/** 编码流程所处阶段；失败时与 [EncoderFailure] 一起上报。 */
enum class EncoderStage { OPEN, CONFIGURE, START, WRITE, DRAIN, FINALIZE, CHECKSUM }

/** 结构化编码失败；调用方必须保留临时文件与阶段信息，不得把它当作成功。 */
data class EncoderFailure(
    val stage: EncoderStage,
    val message: String,
    val cause: Throwable? = null,
)

/** 编码器状态。 */
enum class EncoderState { CREATED, OPEN, SEALED, FAILED }

/** 打开结果。 */
sealed interface EncoderOpenResult {
    /** 编码器与封装器均已就绪；[capability] 是设备实际参数，[diagnostics] 是偏差说明。 */
    data class Opened(
        val capability: EncoderCapability,
        val diagnostics: EncoderDiagnostics,
    ) : EncoderOpenResult

    data class Failed(val failure: EncoderFailure) : EncoderOpenResult
}

/** 写入结果。 */
sealed interface EncoderWriteResult {
    data object Written : EncoderWriteResult
    data class Failed(val failure: EncoderFailure) : EncoderWriteResult
}

/** 封存结果。 */
sealed interface EncoderSealResult {
    /** 尾部完整写入、容器已收尾；[sha256] 是文件的 SHA-256（小写十六进制）。 */
    data class Sealed(
        val tempPath: String,
        val sizeBytes: Long,
        val sha256: String,
        val durationMs: Long,
        val capability: EncoderCapability,
        val diagnostics: EncoderDiagnostics,
    ) : EncoderSealResult

    /** 封存失败：临时文件保留在 [tempPath]，不得删除或标记为完整块。 */
    data class Failed(val failure: EncoderFailure, val tempPath: String) : EncoderSealResult
}

/** 探测编码器能力的来源；生产实现查询 `MediaCodecList`。 */
fun interface EncoderCapabilitySource {
    /** 查询支持 [mimeType] 的编码器能力；没有可用编码器时返回 null。 */
    fun probe(mimeType: String): EncoderCaps?
}

/**
 * 采集侧块编码器（后续集成层适配 core 的 `ChunkRepository` 协议）。
 *
 * 生命周期固定为 `open → write* → closeAndSeal`；任一步失败都返回结构化结果，
 * 不抛异常给采集线程。`open` 成功后 [state] 为 `OPEN`，`closeAndSeal` 成功后为 `SEALED`。
 */
interface CaptureChunkEncoder {
    val state: EncoderState

    /** 打开编码器并绑定临时路径；重复调用必须失败而不是静默重开。 */
    fun open(target: EncoderTarget, tempPath: String): EncoderOpenResult

    /** 写入 PCM16 单声道样本（采样率须等于目标采样率）。 */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size): EncoderWriteResult

    /** 结束输入、写完容器索引、计算校验和；失败时保留临时文件。 */
    fun closeAndSeal(): EncoderSealResult

    /** 放弃本次编码并尽力释放资源；幂等，不删除临时文件。 */
    fun abort()
}
