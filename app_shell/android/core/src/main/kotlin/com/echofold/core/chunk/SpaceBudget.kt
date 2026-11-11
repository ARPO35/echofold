package com.echofold.core.chunk

/**
 * 块存储空间预算。
 *
 * 顺序约束：正常自动分块必须在普通块边界之前为“当前块封存 + 下一块占位/初始化 + 数据库日志”
 * 预留空间。硬下限触发时的顺序固定为：先进入 `BLOCKED` 并冻结切点 → 封存当前块 → 冲刷元数据
 * → 成功后才是 `LOW_STORAGE_PAUSED`。任何一步失败都必须保持 `BLOCKED`，不得继续采集或自动继续。
 *
 * [reserve] 失败的调用方不得创建下一块占位，也不得初始化编码器。
 */
interface SpaceBudget {
    /** 预留 [bytes]；空间不足返回 `false`（不得抛异常作为控制流）。 */
    fun reserve(bytes: Long): Boolean

    /** 释放此前预留的字节数；重复释放或多释放由实现按失配额处理。 */
    fun release(bytes: Long)
}
