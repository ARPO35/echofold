package com.echofold.capture.encoder

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * AAC-LC + M4A 分块编码器。
 *
 * 只负责“把一段 PCM 编成可独立播放的 M4A 并如实上报参数”，块占位与状态机属于上层协议。
 *
 * 时序：`open(target, tempPath)` → `write(samples)*` → `closeAndSeal()`。
 * - `open`：先按探测到的 [EncoderCaps] 夹取目标参数（避免必然失败），再依次
 *   `configure → start → 创建封装器`；失败返回 [EncoderFailure] 并注明阶段，绝不标成功；
 * - `write`：送入 PCM 后立即排空所有可用编码输出；封装器在首个样本时用编码器实际格式建轨；
 * - `closeAndSeal`：通知 EOS、排空尾部、收尾容器、计算文件 SHA-256；
 * - 任何阶段失败都保留临时文件（[EncoderSealResult.Failed.tempPath]），不删除、不重命名；
 * - 实际参数与目标不一致时通过 [EncoderDiagnostics] 上报偏差，不虚标为 192kbps。
 *
 * 本类不引用 `android.*`：`MediaCodec`/`MediaMuxer` 由 [AudioEncoderCodec]/[AudioContainerWriter]
 * 的实现包装，因此全部流程可在 JVM 单测中用 fake 验证。
 */
class AacChunkEncoder(
    private val codec: AudioEncoderCodec,
    private val writer: AudioContainerWriter,
    private val capabilitySource: EncoderCapabilitySource? = null,
) : CaptureChunkEncoder {

    override var state: EncoderState = EncoderState.CREATED
        private set

    /** 目标参数（原始请求）。 */
    var target: EncoderTarget? = null
        private set

    /** 设备实际参数；打开后先由探测结果初始化，取到输出格式后刷新为真实值。 */
    var capability: EncoderCapability? = null
        private set

    /** 目标与实际的偏差诊断。 */
    var diagnostics: EncoderDiagnostics? = null
        private set

    private var tempPath: String = ""
    private var activeFormat: AudioEncoderFormat? = null
    private var writeFormat: AudioEncoderFormat? = null
    private var inputFrames: Long = 0L
    private var resourcesReleased: Boolean = false

    override fun open(target: EncoderTarget, tempPath: String): EncoderOpenResult {
        if (state != EncoderState.CREATED) {
            return EncoderOpenResult.Failed(
                EncoderFailure(EncoderStage.OPEN, "编码器当前状态 $state 不允许再次打开"),
            )
        }
        if (tempPath.isBlank()) {
            return EncoderOpenResult.Failed(EncoderFailure(EncoderStage.OPEN, "临时路径不能为空"))
        }

        val caps = capabilitySource?.probe(target.mimeType)
        val resolution = when {
            caps == null && capabilitySource != null -> {
                state = EncoderState.FAILED
                return EncoderOpenResult.Failed(
                    EncoderFailure(EncoderStage.OPEN, "设备没有支持 ${target.mimeType} 的编码器"),
                )
            }

            caps == null -> EncoderResolution(target = target, deviations = emptyList())
            else -> EncoderCapabilityDiagnostics.resolve(target, caps)
                ?: run {
                    state = EncoderState.FAILED
                    return EncoderOpenResult.Failed(
                        EncoderFailure(EncoderStage.OPEN, "编码器没有可用的采样率候选"),
                    )
                }
        }

        val planned = resolution.target
        val format = AudioEncoderFormat(
            mimeType = planned.mimeType,
            sampleRate = planned.sampleRate,
            channelCount = planned.channelCount,
            bitRate = planned.bitRate,
            aacProfile = planned.aacProfile,
        )

        try {
            codec.configure(format)
        } catch (error: Exception) {
            return failOpen(EncoderStage.CONFIGURE, "配置 AAC 编码器失败", error)
        }
        try {
            codec.start()
        } catch (error: Exception) {
            return failOpen(EncoderStage.START, "启动 AAC 编码器失败", error)
        }
        try {
            writer.open(tempPath)
        } catch (error: Exception) {
            return failOpen(EncoderStage.OPEN, "创建 M4A 封装器失败", error)
        }

        this.target = target
        this.tempPath = tempPath
        this.activeFormat = format
        refreshCapability(fallback = format)
        state = EncoderState.OPEN
        return EncoderOpenResult.Opened(
            capability = requireNotNull(capability),
            diagnostics = requireNotNull(diagnostics),
        )
    }

    override fun write(samples: ShortArray, offset: Int, length: Int): EncoderWriteResult {
        if (state != EncoderState.OPEN) {
            return EncoderWriteResult.Failed(
                EncoderFailure(EncoderStage.WRITE, "编码器未打开（state=$state）"),
            )
        }
        val format = activeFormat ?: return failWrite(
            EncoderStage.WRITE,
            "编码格式缺失",
            null,
        )
        if (length <= 0 || offset < 0 || offset + length > samples.size) {
            return EncoderWriteResult.Failed(
                EncoderFailure(EncoderStage.WRITE, "非法的 PCM 视图：offset=$offset length=$length"),
            )
        }

        val presentationTimeUs = inputFrames * MICROS_PER_SECOND / format.sampleRate
        try {
            codec.queueInput(samples, offset, length, presentationTimeUs)
        } catch (error: Exception) {
            return failWrite(EncoderStage.WRITE, "送入 PCM 失败", error)
        }
        inputFrames += length
        return drainOutputs()
    }

    override fun closeAndSeal(): EncoderSealResult {
        if (state != EncoderState.OPEN) {
            return EncoderSealResult.Failed(
                EncoderFailure(EncoderStage.FINALIZE, "编码器未处于 OPEN（state=$state）"),
                tempPath,
            )
        }

        try {
            codec.signalEndOfStream()
        } catch (error: Exception) {
            return failSeal(EncoderStage.FINALIZE, "通知编码器结束失败", error)
        }

        var tailOutputs = 0
        while (tailOutputs <= MAX_TAIL_OUTPUTS) {
            val buffer = try {
                codec.dequeueOutput()
            } catch (error: Exception) {
                return failSeal(EncoderStage.DRAIN, "尾部排空失败", error)
            } ?: break
            val writeFailure = handleOutput(buffer)
            if (writeFailure != null) {
                return failSeal(writeFailure.stage, writeFailure.message, writeFailure.cause)
            }
            tailOutputs++
            if (buffer.isEndOfStream) break
        }

        try {
            writer.finalizeAndClose()
        } catch (error: Exception) {
            return failSeal(EncoderStage.FINALIZE, "M4A 容器收尾失败", error)
        }
        // 尾部输出全部排空且容器已收尾；释放编码器失败不影响已封存文件的完整性。
        runCatching { codec.release() }
        resourcesReleased = true

        val size: Long
        val sha256: String
        try {
            val file = File(tempPath)
            size = file.length()
            sha256 = sha256Of(file)
        } catch (error: Exception) {
            return failSeal(EncoderStage.CHECKSUM, "计算块校验和失败", error)
        }

        state = EncoderState.SEALED
        return EncoderSealResult.Sealed(
            tempPath = tempPath,
            sizeBytes = size,
            sha256 = sha256,
            durationMs = durationMs(),
            capability = requireNotNull(capability),
            diagnostics = requireNotNull(diagnostics),
        )
    }

    override fun abort() {
        if (state == EncoderState.SEALED) return
        releaseResources()
        if (state == EncoderState.OPEN) {
            state = EncoderState.FAILED
        }
    }

    /** 已送入编码器的有效音频时长（向下取整毫秒）。 */
    private fun durationMs(): Long {
        val format = activeFormat ?: return 0L
        return inputFrames * 1_000L / format.sampleRate
    }

    /** 排空编码输出；返回写入结果（`Written` 或阶段化失败）。 */
    private fun drainOutputs(): EncoderWriteResult {
        while (true) {
            val buffer = try {
                codec.dequeueOutput()
            } catch (error: Exception) {
                return failWrite(EncoderStage.DRAIN, "取编码输出失败", error)
            } ?: return EncoderWriteResult.Written
            val failure = handleOutput(buffer)
            if (failure != null) {
                return failWrite(failure.stage, failure.message, failure.cause)
            }
        }
    }

    /** 处理一段编码输出；返回失败描述或 null（成功）。 */
    private fun handleOutput(buffer: EncodedBuffer): EncoderFailure? {
        if (buffer.isCodecConfig || buffer.isEndOfStream || buffer.size == 0) {
            // 编解码配置与零长 EOS 标记不需要写入容器；顺手刷新实际格式。
            refreshCapability(fallback = activeFormat ?: return null)
            return null
        }
        val format = writeFormat ?: activeFormat
            ?: return EncoderFailure(EncoderStage.DRAIN, "编码格式缺失")
        return try {
            writer.writeSample(buffer, format)
            null
        } catch (error: Exception) {
            EncoderFailure(EncoderStage.WRITE, "写入 M4A 样本失败", error)
        }
    }

    /**
     * 用编码器输出格式刷新实际参数；尚未产生输出格式时用 [fallback] 记录。
     *
     * 编码器名称始终来自平台编码器本身，不写死、不猜测。
     */
    private fun refreshCapability(fallback: AudioEncoderFormat) {
        val actual = codec.actualFormat() ?: fallback
        writeFormat = actual
        val resolved = actual.toCapability(codec.codecName.ifBlank { UNKNOWN_CODEC })
        capability = resolved
        diagnostics = target?.let { EncoderCapabilityDiagnostics.evaluate(it, resolved) }
    }

    private fun failOpen(stage: EncoderStage, message: String, cause: Throwable): EncoderOpenResult {
        releaseResources()
        state = EncoderState.FAILED
        return EncoderOpenResult.Failed(EncoderFailure(stage, message, cause))
    }

    private fun failWrite(stage: EncoderStage, message: String, cause: Throwable?): EncoderWriteResult {
        releaseResources()
        state = EncoderState.FAILED
        return EncoderWriteResult.Failed(EncoderFailure(stage, message, cause))
    }

    private fun failSeal(stage: EncoderStage, message: String, cause: Throwable?): EncoderSealResult {
        releaseResources()
        state = EncoderState.FAILED
        // 临时文件必须保留：调用方据此把块标为 SEAL_FAILED 并做恢复扫描。
        return EncoderSealResult.Failed(EncoderFailure(stage, message, cause), tempPath)
    }

    private fun releaseResources() {
        if (resourcesReleased) return
        resourcesReleased = true
        runCatching { writer.abort() }
        runCatching { codec.release() }
    }

    private companion object {
        const val MICROS_PER_SECOND: Long = 1_000_000L

        /** 尾部排空上限，防止编码器故障时死循环。 */
        const val MAX_TAIL_OUTPUTS: Int = 10_000

        const val UNKNOWN_CODEC: String = "未上报"

        /** 计算文件 SHA-256（小写十六进制）。 */
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}
