package com.echofold.core.time

/**
 * 会话时钟源：把 UTC 墙钟与设备单调时钟显式分开，禁止在音频寻址中混用。
 *
 * 三种时间域的用法固定：
 * - [utcNowMs] 只用于持久化、跨重启排序和界面展示，允许被用户或网络校时改变；
 * - [monoNowNs] 用于采集区间、块切点、排队时延与有效音频偏移换算；
 * - `capturedStartMs/capturedEndMs`（见 [SessionTimeline]）由单调时钟推导，排除暂停间隙。
 */
interface ClockSource {
    /** 当前 UTC 墙钟毫秒；可被校时回调或前跳，不得用于 PCM 寻址或 15 秒排队判定。 */
    fun utcNowMs(): Long

    /** 设备单调时钟纳秒；只在同一锚点内单调可解释，不得跨锚点裸比较。 */
    fun monoNowNs(): Long
}

/** 生产环境默认时钟：墙钟取 [System.currentTimeMillis]，单调时钟取 [System.nanoTime]。 */
object SystemClockSource : ClockSource {
    override fun utcNowMs(): Long = System.currentTimeMillis()

    override fun monoNowNs(): Long = System.nanoTime()
}
