package com.echofold.core.session

/**
 * 有界幂等记录：`commandId → 首次执行结果`。
 *
 * 采用按访问顺序排序的 [LinkedHashMap] 实现 LRU（最近最少使用）淘汰：
 * - [get]/[put] 命中已有键会把该键移到队尾；
 * - 写入后若记录数超过 [capacity]，从队首淘汰最久未使用的 `commandId`。
 *
 * 因此记录数永不超过 [capacity]，不会随长会话无限增长。被淘汰的旧 `commandId`
 * 若再次到达会重新执行；这是有界幂等的固有取舍，状态机自身的状态校验仍会拒绝
 * 非法的重复转换。
 *
 * 线程安全：内部对映射加锁，可从采集、UI 或状态机线程并发访问。
 */
class SessionCommandLedger<V>(val capacity: Int) {
    init {
        require(capacity > 0) { "capacity 必须为正数：$capacity" }
    }

    /** `accessOrder = true` 让读取也刷新 LRU 顺序；初始容量仅为提示值，不限制增长上限。 */
    private val entries = LinkedHashMap<String, V>(capacity.coerceAtLeast(INITIAL_HINT), LOAD_FACTOR, true)

    /** 当前记录数，恒不超过 [capacity]。 */
    val size: Int
        get() = synchronized(entries) { entries.size }

    val isEmpty: Boolean
        get() = synchronized(entries) { entries.isEmpty() }

    fun contains(commandId: String): Boolean =
        synchronized(entries) { entries.containsKey(commandId) }

    /** 读取首次结果并把该键标记为最近使用；不存在返回 `null`。 */
    fun get(commandId: String): V? = synchronized(entries) { entries[commandId] }

    /**
     * 记录结果。
     *
     * @return 同一 `commandId` 的旧值（若有），便于调用方判断是否发生覆盖。
     */
    fun put(commandId: String, value: V): V? {
        require(commandId.isNotBlank()) { "commandId 不能为空白字符串" }
        return synchronized(entries) {
            val previous = entries.put(commandId, value)
            while (entries.size > capacity) {
                val eldestKey = entries.keys.first()
                entries.remove(eldestKey)
            }
            previous
        }
    }

    /** 当前保留的 `commandId`，按 LRU 顺序排列（队首最旧）。 */
    fun commandIds(): List<String> = synchronized(entries) { entries.keys.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    private companion object {
        const val INITIAL_HINT = 16
        const val LOAD_FACTOR = 0.75f
    }
}
