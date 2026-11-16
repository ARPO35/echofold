package com.echofold.capture.service

import com.echofold.capture.pcm.SessionAsrFactory
import com.echofold.core.asr.AsrEvent
import com.echofold.core.asr.AsrEventBuffer
import com.echofold.core.asr.AsrEvents
import com.echofold.core.asr.engine.AsrEngineException

/** app 模块在创建 Flutter engine 时注册实现；capture 不依赖 app。 */
object SessionAsrRegistry {
    @Volatile
    private var registeredFactory: SessionAsrFactory? = null

    val events = SessionAsrEventDelivery(AsrEvents.buffer)

    fun register(factory: SessionAsrFactory) {
        registeredFactory = factory
    }

    fun requireFactory(): SessionAsrFactory = checkNotNull(registeredFactory) {
        "实时转写服务尚未注册 ASR 模型工厂"
    }
}

/** FINAL 队列满时保留一个真实事件并同步终止生产；消费者腾空后按原序重试。 */
class SessionAsrEventDelivery(private val buffer: AsrEventBuffer) {
    private var pendingFinal: AsrEvent.Final? = null
    private var failure: AsrEngineException? = null

    @Synchronized
    fun offer(event: AsrEvent): Boolean {
        if (pendingFinal != null) {
            if (event is AsrEvent.Final) throw AsrEngineException("ASR FINAL 尚未交付，禁止继续生产")
            return false
        }
        if (buffer.offer(event)) return true
        if (event is AsrEvent.Final) {
            pendingFinal = event
            if (failure == null) failure = AsrEngineException("ASR FINAL 事件缓存已满，真实证据已暂存并停止会话")
        }
        return false
    }

    /** 仅在消费者从共享队列腾出位置后调用；旧 FINAL 入队前不接受后续事件。 */
    @Synchronized
    fun retryPending() {
        val event = pendingFinal ?: return
        if (buffer.offer(event)) pendingFinal = null
    }

    @Synchronized
    fun takeFailure(): AsrEngineException? = failure.also { failure = null }

    /** 上一会话的证据必须先进入共享缓冲，才能启动新的识别生产者。 */
    @Synchronized
    fun requireReadyForSession() {
        retryPending()
        check(pendingFinal == null) { "上一会话的 FINAL 尚未交付，请等待转写消费者恢复" }
        failure = null
    }
}
