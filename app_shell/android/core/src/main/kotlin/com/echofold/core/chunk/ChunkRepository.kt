package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk

/**
 * 音频块持久化仓储。
 *
 * 调用顺序由共享契约第 5 节固定，实现必须保证以下约束：
 * 1. 先由会话状态机把分发放置为 `BLOCKED` 并冻结切点，才可以调用 [allocatePlaceholder]；
 * 2. [allocatePlaceholder] 必须在单个事务内完成 `(sessionId, chunkSeq)` 唯一占位与活动块指针推进，
 *    提交成功后才能初始化编码器；序号与临时路径一经分配不得复用；
 * 3. 编码器初始化成功、块被更新为 `OPEN` 之后，才允许恢复向 ASR/AAC 分发；
 * 4. 封存按 `OPEN → SEALING → SEALED` 用 [update] 提交，失败必须落为 `SEAL_FAILED` 并保留失败阶段，
 *    不得把未完成封存的块更新成 `SEALED`。
 *
 * 本接口只定义签名与顺序约束，实现属于后续切片。
 */
interface ChunkRepository {
    /**
     * 原子分配下一个块占位。
     *
     * 必须返回 `state = PLACEHOLDER`、`createdAtUtcMs = nowUtcMs` 的块，并在同一事务内拒绝
     * `(sessionId, chunkSeq)` 或临时路径冲突（抛出 [IllegalStateException]，调用方保持 `BLOCKED`）。
     */
    fun allocatePlaceholder(sessionId: String, chunkSeq: Int, tempPath: String, nowUtcMs: Long): AudioChunk

    /** 按块身份整体更新一行；重复写入同一状态必须幂等。 */
    fun update(chunk: AudioChunk)

    /** 返回该会话当前活动块（`PLACEHOLDER/OPEN/SEALING`）；同一会话最多一个。 */
    fun findActive(sessionId: String): AudioChunk?

    /** 按 `chunkSeq` 升序返回会话全部块，供回听、导出与恢复扫描使用。 */
    fun listBySession(sessionId: String): List<AudioChunk>
}
