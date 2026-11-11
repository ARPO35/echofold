package com.echofold.core.chunk

/**
 * 块文件系统操作抽象（便于用 fake 做 JVM 单测）。
 *
 * 封存协议要求“临时写入 → flush/同步 → 校验 → 同卷原子改名 → 提交 SEALED”：
 * 写入方只能在 [createExclusive] 成功的独占路径上写数据，校验通过后用 [renameAtomically]
 * 落到最终路径；改名必须与目标同一卷，跨卷或非原子实现会破坏可恢复性，因此实现必须拒绝。
 */
interface ChunkFileSystem {
    /**
     * 以独占方式创建文件；路径已存在时返回 `false`，调用方不得覆盖既有文件。
     *
     * 必须先于任何写入调用，用于保证临时路径唯一。
     */
    fun createExclusive(path: String): Boolean

    /** 返回文件字节数；文件不存在时抛出 [java.io.IOException]。 */
    fun size(path: String): Long

    /** 判断路径是否存在（不区分文件或目录，由实现文档化）。 */
    fun exists(path: String): Boolean

    /**
     * 同一卷内原子改名。
     *
     * 只在源文件已完整 flush/sync 且校验通过后调用；失败必须抛出异常并保留源文件，
     * 调用方据此把块置为 `SEAL_FAILED`。
     */
    fun renameAtomically(from: String, to: String)
}
