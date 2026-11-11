package com.echofold.core.session

import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus
import com.echofold.core.model.requireNonNegative
import com.echofold.core.model.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 稳定会话事件 ID 的构造规则：`sessionId:state:sequence`。 */
object SessionEventIds {
    const val SEPARATOR: String = ":"
    const val KIND: String = "state"

    /**
     * 由会话与状态变更序号派生事件 ID。
     *
     * 序号只在命令真正生效时递增，因此重复 `commandId` 重放不会产生新 ID，
     * 原生到 Flutter 的重复投递可按 [SessionStateEvent.eventId] 直接去重。
     */
    fun of(sessionId: String, sequence: Long): String =
        sessionId + SEPARATOR + KIND + SEPARATOR + sequence
}

/**
 * 一次生效的会话状态变更事件。
 *
 * 事件是 append-only 的不可变快照：携带变更前后 [SessionStatus] 与 [DispatchState]，
 * 以及触发它的稳定 `commandId`。`occurredAtUtcMs` 只用于展示与排序，不参与
 * 状态判定或音频寻址。
 */
data class SessionStateEvent(
    val sessionId: String,
    val sequence: Long,
    val commandId: String,
    val commandKind: SessionCommandKind,
    val previousStatus: SessionStatus,
    val previousDispatchState: DispatchState,
    val status: SessionStatus,
    val dispatchState: DispatchState,
    val occurredAtUtcMs: Long,
    /** 失败原因等诊断信息；普通转换为空。 */
    val reason: String? = null,
    val eventId: String = SessionEventIds.of(sessionId, sequence),
) {
    init {
        requireNotBlank(sessionId, "sessionId")
        requireNonNegative(sequence, "sequence")
        requireNotBlank(commandId, "commandId")
        requireNonNegative(occurredAtUtcMs, "occurredAtUtcMs")
        reason?.let { requireNotBlank(it, "reason") }
        require(eventId == SessionEventIds.of(sessionId, sequence)) {
            "eventId($eventId) 必须由 sessionId/sequence 派生，防止 copy() 后留下陈旧 ID"
        }
    }
}

/**
 * 状态变更事件的 append-only 日志，同时提供冷回放列表与热 [Flow]。
 *
 * - [events] 按发生顺序返回全部事件的不可变快照，供原生→Flutter 首次投递或重连补齐；
 * - [stream] 是广播流，供已订阅方实时接收；`extraBufferCapacity` 吸收短时背压，
 *   订阅方过慢时挂起发送方而不是静默丢弃事件。
 *
 * 状态变更事件数量与会话命令数同阶，保留完整日志用于幂等重放即可，不做截断。
 */
class SessionEventLog {
    private val lock = Any()
    private val recorded = mutableListOf<SessionStateEvent>()
    private val shared = MutableSharedFlow<SessionStateEvent>(extraBufferCapacity = STREAM_BUFFER_CAPACITY)

    /** 全部已记录事件的有序快照。 */
    val events: List<SessionStateEvent>
        get() = synchronized(lock) { recorded.toList() }

    /** 最近一次事件；尚无事件时为 `null`。 */
    val latest: SessionStateEvent?
        get() = synchronized(lock) { recorded.lastOrNull() }

    val size: Int
        get() = synchronized(lock) { recorded.size }

    /** 热事件流；订阅前已发生的事件从 [events] 读取。 */
    fun stream(): Flow<SessionStateEvent> = shared.asSharedFlow()

    /**
     * 追加事件并广播。
     *
     * 仅状态机在串行临界区内调用；先落日志再广播，确保任何一方观察到的顺序一致。
     */
    internal suspend fun record(event: SessionStateEvent) {
        synchronized(lock) { recorded += event }
        shared.emit(event)
    }

    private companion object {
        const val STREAM_BUFFER_CAPACITY = 64
    }
}
