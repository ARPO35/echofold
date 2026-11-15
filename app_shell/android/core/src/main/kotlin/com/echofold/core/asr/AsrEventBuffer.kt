package com.echofold.core.asr

/** 有界投递缓存；拒绝时生产者保留事件并处理背压，绝不淘汰已接收证据。 */
class AsrEventBuffer(private val capacity: Int = 256) {
    init { require(capacity > 0) }
    private val events = ArrayDeque<AsrEvent>(capacity)
    var rejectedCount: Long = 0
        private set

    @Synchronized fun offer(event: AsrEvent): Boolean {
        if (events.size == capacity) {
            rejectedCount++
            return false
        }
        events.addLast(event)
        return true
    }

    /** 投递成功才移除；消费抛错时保留队首。仅允许一个消费者。 */
    @Synchronized fun deliver(consumer: (AsrEvent) -> Unit) {
        val count = events.size
        repeat(count) {
            consumer(events.first())
            events.removeFirst()
        }
    }
}

/** 进程内事件入口；生产端只能提交已经形成的真实事件。 */
object AsrEvents {
    val buffer = AsrEventBuffer()
}
