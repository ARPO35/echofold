package com.echofold.core.asr.engine

/**
 * 会话固定的模型身份引用。
 *
 * 正在进行的会话与它的每次重识别都固定同一组 `modelId/modelVersion/installationId`；
 * 任何初始化失败都不得改用其他模型版本或远端服务来“补齐”结果。
 */
data class ModelRef(
    val modelId: String,
    val modelVersion: String,
    val installationId: String,
    val sha256: String,
) {
    init {
        require(modelId.isNotBlank()) { "modelId 不能为空白字符串" }
        require(modelVersion.isNotBlank()) { "modelVersion 不能为空白字符串" }
        require(installationId.isNotBlank()) { "installationId 不能为空白字符串" }
        require(SHA256_PATTERN.matches(sha256)) { "sha256 必须是 64 位小写十六进制：$sha256" }
    }

    private companion object {
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * 一次识别的运行时配置。
 *
 * `chunkMs` 是模型块长（480ms 为原型候选值），不是端到端首字延迟承诺；
 * 会话必须把该配置整体固定，配置哈希写入 `RecordingSession.asrConfigHash`。
 */
data class AsrConfig(
    val model: ModelRef,
    val sampleRate: Int = 16_000,
    val chunkMs: Int = 480,
    val threads: Int = 1,
    val enableVad: Boolean = true,
) {
    init {
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(chunkMs > 0) { "chunkMs 必须为正数：$chunkMs" }
        require(threads > 0) { "threads 必须为正数：$threads" }
    }
}

/**
 * 一段待识别的 PCM 帧视图。
 *
 * [samples] 是共享缓冲区，[offset]/[length] 划定本帧有效区间；实现不得在接口约定之外强转或复制，
 * 也不得假定时钟域：`monoNs` 是设备单调时钟，`capturedStartMs` 是排除暂停间隙的有效音频偏移。
 */
data class PcmFrame(
    val sessionId: String,
    val frameIndex: Long,
    val monoNs: Long,
    val capturedStartMs: Long,
    val sampleRate: Int,
    val samples: ShortArray,
    val offset: Int = 0,
    val length: Int = samples.size,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(frameIndex >= 0L) { "frameIndex 不能为负数：$frameIndex" }
        require(monoNs >= 0L) { "monoNs 不能为负数：$monoNs" }
        require(capturedStartMs >= 0L) { "capturedStartMs 不能为负数：$capturedStartMs" }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(offset >= 0) { "offset 不能为负数：$offset" }
        require(length > 0) { "length 必须为正数：$length" }
        require(offset <= samples.size) { "offset($offset) 不能超过 samples 长度(${samples.size})" }
        require(length <= samples.size - offset) {
            "offset($offset) + length($length) 不能超过 samples 长度(${samples.size})"
        }
    }
}

/** ASR 引擎初始化或运行时不可恢复失败；调用方必须显式降级，不得静默换模型。 */
class AsrEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 单模型流式识别引擎。
 *
 * 调用顺序：[initialize] 成功后才可以 [acceptPcm]；[flushSegment] 冲刷当前段并产生
 * FinalCandidate/Final 事件；[close] 释放运行时资源且必须幂等。[acceptPcm] 不得阻塞采集线程，
 * 积压只能进入有界队列，超过 15 秒排队时延由上层触发跳读与 GAP，而不是在本接口内丢弃帧。
 */
interface AsrEngine {
    /** 初始化失败必须抛 [AsrEngineException]，不允许静默降级到其他模型。 */
    fun initialize(config: AsrConfig)

    /** 送入一帧 16kHz 单声道 PCM；必须在 [initialize] 之后调用。 */
    fun acceptPcm(frame: PcmFrame)

    /** 端点：冲刷当前段并产生 FinalCandidate/Final。 */
    fun flushSegment()

    /** 释放运行时资源；重复调用必须安全。 */
    fun close()
}

/** 引擎构造入口，便于测试用 fake 替换真实运行时。 */
interface AsrEngineFactory {
    fun create(): AsrEngine
}

/**
 * 重采样器。
 *
 * 有界、不得抛未声明异常；输出长度由实现保证（典型为 `input.size * targetRate / inputRate` 量级），
 * 调用方按返回数组长度继续分发，不得假定与输入等长。
 */
interface Resampler {
    fun resample(input: ShortArray, inputRate: Int, channels: Int = 1): ShortArray
}
