package com.echofold.core.chunk

import com.echofold.core.model.DispatchState

/**
 * 会话分发放行开关：决定采集线程能否把新 PCM 交给 ASR 与 AAC 编码。
 *
 * 换块协议把它当作唯一的切点权威：任何换块/封存流程的第一步都是 [block]，只有
 * “占位提交 + 编码器初始化 + 块置 OPEN”全部成功后才允许 [resume]。失败路径必须停在 `BLOCKED`，
 * 不允许为了“让录音继续”而提前放行。默认初值为 `BLOCKED`，避免构造后误放行。
 */
class ChunkDispatchGate(initial: DispatchState = DispatchState.BLOCKED) {
    @Volatile
    var state: DispatchState = initial
        private set

    val isBlocked: Boolean get() = state == DispatchState.BLOCKED

    /** 冻结切点；重复调用幂等。 */
    fun block() {
        state = DispatchState.BLOCKED
    }

    /** 恢复分发；只允许在换块协议完整成功后调用。 */
    fun resume() {
        state = DispatchState.RUNNING
    }
}

/**
 * 块文件命名规则。
 *
 * 临时路径与最终路径同时由 `(sessionId, chunkSeq)` 唯一确定，序号不复用，因此不会出现两个块指向同一路径。
 * 这里只做纯字符串推导，不触碰文件系统，便于 JVM 单测直接断言路径唯一性。
 */
object ChunkPaths {
    /** 写入中的临时文件后缀；封存成功后被原子改名去掉该后缀。 */
    const val TEMP_SUFFIX = ".partial.m4a"

    /** 封存后的最终文件后缀。 */
    const val FINAL_SUFFIX = ".m4a"

    /** 返回 `(sessionId, chunkSeq)` 对应的独占临时路径。 */
    fun tempPath(sessionId: String, chunkSeq: Int): String {
        require(sessionId.isNotBlank()) { "sessionId 不能为空" }
        require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
        return "sessions/$sessionId/chunk-$chunkSeq$TEMP_SUFFIX"
    }

    /** 由临时路径推导最终路径；已登记 [com.echofold.core.model.AudioChunk.finalPath] 时以登记值为准。 */
    fun finalPathOf(tempPath: String): String =
        if (tempPath.endsWith(TEMP_SUFFIX)) {
            tempPath.removeSuffix(TEMP_SUFFIX) + FINAL_SUFFIX
        } else {
            tempPath + FINAL_SUFFIX
        }
}
