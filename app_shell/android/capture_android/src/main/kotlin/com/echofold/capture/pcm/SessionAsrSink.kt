package com.echofold.capture.pcm

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.PcmFrame

/** 会话 ASR 状态；模型不可用不阻止独立的编码与录音车道。 */
sealed interface AsrAvailability {
    data object Ready : AsrAvailability
    data class Unavailable(val code: String, val message: String, val cause: Throwable? = null) : AsrAvailability {
        init { require(code.isNotBlank()); require(message.isNotBlank()) }
    }
}


/** 会话语句边界；暂停与封存前必须先排空 PCM 再调用。 */
interface LifecycleAsrSink : AsrSink {
    fun finishSegment()
    fun close()
}
/** 已初始化、绑定单一会话与模型配置的 PCM 消费者。队列和推理线程由 PcmDispatcher 所有。 */
class SessionAsrSink(
    private val sessionId: String,
    private val config: AsrConfig,
    private val engine: AsrEngine,
    private val onFailure: (AsrEngineException) -> Unit = {},
) : LifecycleAsrSink {

    @Volatile private var closed = false
    private var terminalFailure: AsrEngineException? = null

    init {
        require(sessionId.isNotBlank())
    }

    @Synchronized
    override fun accept(frame: PcmFrame) {
        if (closed) throw AsrEngineException("ASR 会话已关闭：$sessionId")
        terminalFailure?.let { throw it }
        if (frame.sessionId != sessionId) throw AsrEngineException("PCM 会话不匹配：${frame.sessionId} != $sessionId")
        try {
            engine.acceptPcm(frame)
        } catch (error: AsrEngineException) {
            terminalFailure = error
            onFailure(error)
            throw error
        } catch (error: Exception) {
            val failure = AsrEngineException("ASR PCM 处理失败：${error.message ?: error.javaClass.simpleName}", error)
            terminalFailure = failure
            onFailure(failure)
            throw failure
        }
    }

    /** 在 PCM lane 排空后结束当前语句（暂停、分块封存与 STOP）。 */
    @Synchronized
    override fun finishSegment() {
        if (closed) return
        // 失败的推理/FINAL 已保留；不得重放 PCM 或再次冲刷制造重复证据。
        terminalFailure?.let { throw it }
        try {
            engine.flushSegment()
        } catch (error: AsrEngineException) {
            terminalFailure = error
            onFailure(error)
            throw error
        } catch (error: Exception) {
            val failure = AsrEngineException("ASR 分段冲刷失败：${error.message ?: error.javaClass.simpleName}", error)
            terminalFailure = failure
            onFailure(failure)
            throw failure
        }
    }

    /** 幂等释放会话固定的引擎。必须在 PcmDispatcher 排空 ASR lane 后调用。 */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        engine.close()
    }
}

/** 创建并初始化失败时返回显式不可用状态；绝不构造虚假的模型或识别文本。 */
sealed interface SessionAsrCreation {
    data class Available(val sink: SessionAsrSink) : SessionAsrCreation
    data class Unavailable(val status: AsrAvailability.Unavailable) : SessionAsrCreation
}

object SessionAsrSinkFactory {
    fun create(
        sessionId: String,
        config: AsrConfig,
        engineFactory: () -> AsrEngine,
        onFailure: (AsrEngineException) -> Unit = {},
    ): SessionAsrCreation {
        val engine = try {
            engineFactory()
        } catch (error: Exception) {
            return SessionAsrCreation.Unavailable(
                AsrAvailability.Unavailable("ASR_UNAVAILABLE", error.message ?: error.javaClass.simpleName, error),
            )
        }
        try {
            engine.initialize(config)
        } catch (error: Exception) {
            try { engine.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
            return SessionAsrCreation.Unavailable(
                AsrAvailability.Unavailable("ASR_UNAVAILABLE", error.message ?: error.javaClass.simpleName, error),
            )
        }
        return SessionAsrCreation.Available(SessionAsrSink(sessionId, config, engine, onFailure))
    }
}
