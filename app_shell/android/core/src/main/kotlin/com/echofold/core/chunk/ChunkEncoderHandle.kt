package com.echofold.core.chunk

/**
 * 单个音频块的编码器句柄（由采集/AAC 切片实现）。
 *
 * 生命周期由占位协议与封存流水线严格约束：
 * 1. [initialize] 只能在 [ChunkRepository.allocatePlaceholder] 提交成功之后调用；未登记编码器一律不得初始化，
 *    初始化失败必须抛出，由调用方把块落为失败态并保持 `BLOCKED`；
 * 2. [write] 只在块为 `OPEN`、且分发处于 `RUNNING` 时调用；[frameIndex] 是会话内单调递增的采集帧序号，
 *    用于事后对账，不参与寻址；
 * 3. [finishAndSeal] 只调用一次，返回前必须完成容器索引写入、flush 与 fsync，并给出落盘后的实际字节数与校验和。
 *
 * 实现不得在 [finishAndSeal] 之后继续接受写入，也不得自行删除临时文件：临时文件是失败恢复的唯一证据。
 */
interface ChunkEncoderHandle {
    /** 初始化编码/封装资源；失败抛出异常，调用方据此保持 `BLOCKED`。 */
    fun initialize()

    /** 写入一段 PCM；仅当块为 `OPEN` 时由 [SealingPipeline] 放行。 */
    fun write(bytes: ByteArray, frameIndex: Long)

    /** 结束容器并同步落盘，返回实际产物信息；失败抛出异常。 */
    fun finishAndSeal(): SealResult
}

/**
 * [ChunkEncoderHandle.finishAndSeal] 的结果：封装完成后由编码器实际观测到的产物信息。
 *
 * 字段必须来自真实落盘结果，不得回填请求参数（例如设备被裁剪到更低码率时必须报告实际码率）。
 * [checksum] 为落盘文件的 SHA-256 小写十六进制，由 [SealingPipeline] 校验格式与已登记值的一致性。
 */
data class SealResult(
    val bytesWritten: Long,
    val checksum: String,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int,
    val bitRate: Int,
) {
    init {
        require(bytesWritten >= 0) { "bytesWritten 不能为负数：$bytesWritten" }
        require(durationMs >= 0) { "durationMs 不能为负数：$durationMs" }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(bitRate > 0) { "bitRate 必须为正数：$bitRate" }
        require(codec.isNotBlank()) { "codec 不能为空" }
    }
}
