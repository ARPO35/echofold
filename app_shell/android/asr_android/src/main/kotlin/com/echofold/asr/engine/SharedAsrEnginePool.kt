package com.echofold.asr.engine

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.PcmFrame

/**
 * 支持进程内复用的引擎：同一配置下可重置识别流而不重建权重。
 *
 * 共享权重（引擎实例）与会话/stream 状态分离：各会话取得引擎时重置识别流，
 * 不继承上一会话的音频、文字或端点状态。
 */
interface ReusableAsrEngine : AsrEngine {
    /** 清空识别流与 partial/final 去重状态；在会话边界调用，权重保留。 */
    fun resetStream()
}

/** 单个共享引擎的文本回调路由点；会话接管/释放时切换，无会话时丢弃文本。 */
class SharedEngineCallbacks {
    @Volatile var onFinalText: (String) -> Unit = {}
    @Volatile var onPartialText: (String) -> Unit = {}
}

/**
 * 进程内共享引擎池：同一已安装模型与相同 [AsrConfig] 复用已创建并初始化的引擎实例。
 *
 * 重建（销毁权重后重新加载）只发生在：显式 [invalidate]（model.select 切换、运行时失效等，
 * 原因记录在 [rebuildReason]）、同一安装换用不同 [AsrConfig]、或进程重启（池随进程消亡）。
 * 会话结束只减引用，不销毁权重；预热得到的空闲条目（引用为 0）也持续保留供后续会话复用。
 *
 * 并发安全：加载与引用计数在池锁内完成；识别器访问经会话句柄串行化。
 */
class SharedAsrEnginePool(private val loader: (AsrConfig, SharedEngineCallbacks) -> AsrEngine) {
    private class Entry(
        val config: AsrConfig,
        val engine: AsrEngine,
        val callbacks: SharedEngineCallbacks,
    ) {
        var refs = 0
        /** 失效后仍在被活跃会话使用：最后一个引用释放时销毁。 */
        var stale = false
        /** 序列化进入共享识别器，禁止重叠会话并发访问同一 stream。 */
        val accessLock = Any()
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    /** 最近一次重建原因（设置/诊断可查）；无重建时为 null。 */
    @Volatile
    var rebuildReason: String? = null
        private set

    /**
     * 预热：为当前配置创建并初始化引擎，引用为 0，仅供后续会话复用。
     * 已有相同配置的可用条目时为幂等空操作。
     *
     * @throws Exception 加载失败时原样抛出，不缓存半初始化状态；调用方显式降级。
     */
    fun warmup(config: AsrConfig) {
        synchronized(lock) {
            entries[config.model.installationId]?.takeIf { !it.stale && it.config == config } ?: load(config)
        }
    }

    /**
     * 取得会话绑定的引擎句柄。命中缓存时复用已加载权重并重置识别流（会话隔离）。
     *
     * @throws AsrEngineException 加载或流复位失败；引用不泄漏，调用方显式降级。
     */
    fun acquire(
        config: AsrConfig,
        onFinalText: (String) -> Unit,
        onPartialText: (String) -> Unit,
    ): AsrEngine {
        val entry = synchronized(lock) {
            val existing = entries[config.model.installationId]
            val target = existing?.takeIf { !it.stale && it.config == config } ?: load(config)
            target.refs += 1
            target
        }
        try {
            (entry.engine as? ReusableAsrEngine)?.resetStream()
        } catch (error: Throwable) {
            discardRef(entry)
            throw error
        }
        entry.callbacks.onFinalText = onFinalText
        entry.callbacks.onPartialText = onPartialText
        return SessionHandle(entry)
    }

    /** 显式失效：销毁全部空闲条目，在用条目标记 stale 于最后引用释放时销毁。 */
    fun invalidate(reason: String) {
        val toClose = mutableListOf<AsrEngine>()
        synchronized(lock) {
            rebuildReason = reason
            for ((id, entry) in entries) {
                if (entry.refs == 0) {
                    toClose += entry.engine
                    entries.remove(id)
                } else {
                    entry.stale = true
                }
            }
        }
        toClose.forEach { closeQuietly(it) }
    }

    private fun load(config: AsrConfig): Entry {
        val existing = entries[config.model.installationId]
        val callbacks = SharedEngineCallbacks()
        val engine = loader(config, callbacks)
        val entry = Entry(config, engine, callbacks)
        // 同一安装换用不同配置：空闲旧条目立即销毁，在用旧条目最后引用释放时销毁。
        if (existing != null) {
            entries.remove(config.model.installationId)
            if (existing.refs > 0) existing.stale = true else closeQuietly(existing.engine)
        }
        entries[config.model.installationId] = entry
        return entry
    }

    private fun discardRef(entry: Entry) {
        var close = false
        synchronized(lock) {
            entry.refs -= 1
            close = entry.refs == 0 && (entry.stale || entries[entry.config.model.installationId] !== entry)
        }
        if (close) closeQuietly(entry.engine)
    }

    private fun closeQuietly(engine: AsrEngine) {
        try {
            engine.close()
        } catch (_: Exception) {
            // 销毁失效权重没有可恢复动作；不阻断后续加载。
        }
    }

    /** 会话持有的共享引擎句柄；[close] 只减引用，是否销毁由池决定。 */
    private inner class SessionHandle(private val entry: Entry) : AsrEngine {
        override fun initialize(config: AsrConfig) = entry.engine.initialize(config)
        override fun acceptPcm(frame: PcmFrame) = synchronized(entry.accessLock) { entry.engine.acceptPcm(frame) }
        override fun flushSegment() = synchronized(entry.accessLock) { entry.engine.flushSegment() }
        override fun close() = this@SharedAsrEnginePool.discardRef(entry)
    }
}
