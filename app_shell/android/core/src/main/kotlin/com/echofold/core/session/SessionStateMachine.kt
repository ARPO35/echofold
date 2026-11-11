package com.echofold.core.session

import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus
import com.echofold.core.time.ClockSource
import com.echofold.core.time.SystemClockSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 会话控制命令类型；[wireName] 用于事件与诊断输出。 */
enum class SessionCommandKind(val wireName: String) {
    START("start"),
    PAUSE("pause"),
    RESUME("resume"),
    STOP("stop"),
    LOW_STORAGE_STOP("lowStorageStop"),
    RETRY_PLACEHOLDER("retryPlaceholder"),
    MARK_FAILED("markFailed"),
    ;

    companion object {
        /** 由线上名称反查命令类型；未知名称返回 `null`，不猜测。 */
        fun fromWireName(wireName: String): SessionCommandKind? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * 一条带幂等键的会话命令。
 *
 * `commandId` 由调用方生成（页面重建后必须复用同一 ID），状态机用它识别重放：
 * 同一 ID 只执行一次，之后的到达直接返回首次结果，不再产生状态变更或事件。
 */
sealed class SessionCommand(open val commandId: String) {
    /**
     * 子类构造完成后校验幂等键。
     *
     * 不能在基类 `init` 中读取 `commandId`：基类初始化早于子类属性赋值，
     * 通过开放属性读取会得到 JVM 默认的 `null`。
     */
    protected fun requireValidCommandId() {
        require(commandId.isNotBlank()) { "commandId 不能为空白字符串" }
    }

    abstract val kind: SessionCommandKind

    /** 空闲会话开始录音；只允许从 [SessionStatus.IDLE] 触发。 */
    data class Start(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.START
    }

    /** 暂停录音；只允许从 [SessionStatus.RECORDING] 触发，暂停后分发放置为 BLOCKED。 */
    data class Pause(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.PAUSE
    }

    /** 继续录音；允许从用户暂停或低存储暂停恢复，恢复后分发为 RUNNING。 */
    data class Resume(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.RESUME
    }

    /** 结束会话；进入 [SessionStatus.STOPPING] 并立即 BLOCKED，等待外部封存完成。 */
    data class Stop(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.STOP
    }

    /** 存储硬下限保护；进入 [SessionStatus.LOW_STORAGE_PAUSED] 并 BLOCKED，不创建下一块。 */
    data class LowStorageStop(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.LOW_STORAGE_STOP
    }

    /** 用户明确重试失败的块占位；仅在录音或暂停状态允许，成功后恢复分发。 */
    data class RetryPlaceholder(override val commandId: String) : SessionCommand(commandId) {
        init {
            requireValidCommandId()
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.RETRY_PLACEHOLDER
    }

    /** 标记不可恢复失败；进入 [SessionStatus.FAILED] 并 BLOCKED，保留失败原因。 */
    data class MarkFailed(override val commandId: String, val reason: String) :
        SessionCommand(commandId) {
        init {
            requireValidCommandId()
            require(reason.isNotBlank()) { "MarkFailed.reason 不能为空白字符串" }
        }

        override val kind: SessionCommandKind get() = SessionCommandKind.MARK_FAILED
    }
}

/** 命令执行结果：生效返回 [Applied]，非法转换返回 [Rejected] 且状态保持不变。 */
sealed interface SessionCommandResult {
    val commandId: String
    val commandKind: SessionCommandKind

    /** 执行后（或拒绝时当前）的会话状态。 */
    val status: SessionStatus

    /** 执行后（或拒绝时当前）的分发状态。 */
    val dispatchState: DispatchState

    data class Applied(
        override val commandId: String,
        override val commandKind: SessionCommandKind,
        override val status: SessionStatus,
        override val dispatchState: DispatchState,
        val event: SessionStateEvent,
    ) : SessionCommandResult

    data class Rejected(
        override val commandId: String,
        override val commandKind: SessionCommandKind,
        override val status: SessionStatus,
        override val dispatchState: DispatchState,
        val reason: String,
    ) : SessionCommandResult {
        init {
            require(reason.isNotBlank()) { "Rejected.reason 不能为空白字符串" }
        }
    }
}

/** 会话状态快照，供 UI/诊断无锁读取。 */
data class SessionSnapshot(
    val sessionId: String,
    val status: SessionStatus,
    val dispatchState: DispatchState,
)

/**
 * 串行会话状态机。
 *
 * 所有命令经由 [Mutex] 串行执行，保证同一时刻只有一个转换被应用；命令结果写入
 * [SessionCommandLedger]，同一 `commandId` 重放返回首次结果且不重复执行副作用。
 *
 * 状态不变式：
 * - 只有 [SessionStatus.RECORDING] 允许 [DispatchState.RUNNING]；
 * - `STOPPING/PAUSED/LOW_STORAGE_PAUSED/FAILED/IDLE` 一律 BLOCKED；
 * - RECORDING 允许 BLOCKED（换块占位或背压保护期间）。
 *
 * 事件通过 [eventLog] 以 append-only 列表与热流输出，事件 ID 稳定可去重。
 */
class SessionStateMachine(
    val sessionId: String,
    initialStatus: SessionStatus = SessionStatus.IDLE,
    initialDispatchState: DispatchState = DispatchState.BLOCKED,
    ledgerCapacity: Int = DEFAULT_LEDGER_CAPACITY,
    private val clock: ClockSource = SystemClockSource,
    val eventLog: SessionEventLog = SessionEventLog(),
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(invariantHolds(initialStatus, initialDispatchState)) {
            "初始状态违反会话不变式：status=$initialStatus, dispatchState=$initialDispatchState"
        }
    }

    private val mutex = Mutex()
    private val ledger = SessionCommandLedger<SessionCommandResult>(ledgerCapacity)

    @Volatile
    private var current = SessionSnapshot(sessionId, initialStatus, initialDispatchState)

    /** 事件序号；只在命令生效时递增，全部访问位于 [mutex] 临界区。 */
    private var nextEventSequence = 0L

    /** 当前不可变状态快照，可无锁读取。 */
    fun snapshot(): SessionSnapshot = current

    /** 已记录命令结果数（受容量上限约束）。 */
    val ledgerSize: Int
        get() = ledger.size

    /** 指定幂等键是否仍在有效窗口内。 */
    fun isCommandRecorded(commandId: String): Boolean = ledger.contains(commandId)

    /**
     * 串行执行一条命令。
     *
     * 重放语义：命中 [ledger] 的 `commandId` 原样返回首次结果（包括首次的
     * [SessionCommandResult.Applied.event]），不更新状态、不追加事件。
     */
    suspend fun execute(command: SessionCommand): SessionCommandResult = mutex.withLock {
        ledger.get(command.commandId)?.let { return@withLock it }

        val before = current
        val transition = transition(before, command)
        val result = when (transition) {
            is Transition.Reject -> SessionCommandResult.Rejected(
                commandId = command.commandId,
                commandKind = command.kind,
                status = before.status,
                dispatchState = before.dispatchState,
                reason = transition.reason,
            )

            is Transition.Apply -> apply(before, command, transition)
        }
        ledger.put(command.commandId, result)
        result
    }

    private suspend fun apply(
        before: SessionSnapshot,
        command: SessionCommand,
        transition: Transition.Apply,
    ): SessionCommandResult {
        val next = SessionSnapshot(sessionId, transition.status, transition.dispatchState)
        require(invariantHolds(next.status, next.dispatchState)) {
            "转换结果违反会话不变式：status=${next.status}, dispatchState=${next.dispatchState}"
        }

        val event = SessionStateEvent(
            sessionId = sessionId,
            sequence = nextEventSequence,
            commandId = command.commandId,
            commandKind = command.kind,
            previousStatus = before.status,
            previousDispatchState = before.dispatchState,
            status = next.status,
            dispatchState = next.dispatchState,
            occurredAtUtcMs = clock.utcNowMs(),
            reason = transition.reason,
        )

        // 先提交状态与序号，再广播事件：订阅方看到的事件顺序与状态历史一致。
        current = next
        nextEventSequence += 1
        eventLog.record(event)

        return SessionCommandResult.Applied(
            commandId = command.commandId,
            commandKind = command.kind,
            status = next.status,
            dispatchState = next.dispatchState,
            event = event,
        )
    }

    /** 纯转换表：只做判定，不修改任何状态，便于非法转换保持状态不变。 */
    private fun transition(before: SessionSnapshot, command: SessionCommand): Transition = when (command) {
        is SessionCommand.Start ->
            if (before.status == SessionStatus.IDLE) {
                Transition.Apply(SessionStatus.RECORDING, DispatchState.RUNNING)
            } else {
                Transition.Reject("Start 只允许从 IDLE 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.Pause ->
            if (before.status == SessionStatus.RECORDING) {
                Transition.Apply(SessionStatus.PAUSED, DispatchState.BLOCKED)
            } else {
                Transition.Reject("Pause 只允许从 RECORDING 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.Resume ->
            if (before.status == SessionStatus.PAUSED || before.status == SessionStatus.LOW_STORAGE_PAUSED) {
                Transition.Apply(SessionStatus.RECORDING, DispatchState.RUNNING)
            } else {
                Transition.Reject("Resume 只允许从 PAUSED 或 LOW_STORAGE_PAUSED 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.Stop ->
            if (before.status == SessionStatus.RECORDING ||
                before.status == SessionStatus.PAUSED ||
                before.status == SessionStatus.LOW_STORAGE_PAUSED
            ) {
                Transition.Apply(SessionStatus.STOPPING, DispatchState.BLOCKED)
            } else {
                Transition.Reject("Stop 只允许从 RECORDING/PAUSED/LOW_STORAGE_PAUSED 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.LowStorageStop ->
            if (before.status == SessionStatus.RECORDING || before.status == SessionStatus.PAUSED) {
                Transition.Apply(SessionStatus.LOW_STORAGE_PAUSED, DispatchState.BLOCKED)
            } else {
                Transition.Reject("LowStorageStop 只允许从 RECORDING 或 PAUSED 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.RetryPlaceholder ->
            if (before.status == SessionStatus.RECORDING || before.status == SessionStatus.PAUSED) {
                Transition.Apply(SessionStatus.RECORDING, DispatchState.RUNNING)
            } else {
                Transition.Reject("RetryPlaceholder 只允许从 RECORDING 或 PAUSED 触发，当前状态为 ${before.status}")
            }

        is SessionCommand.MarkFailed ->
            if (before.status == SessionStatus.RECORDING ||
                before.status == SessionStatus.PAUSED ||
                before.status == SessionStatus.STOPPING ||
                before.status == SessionStatus.LOW_STORAGE_PAUSED
            ) {
                Transition.Apply(
                    status = SessionStatus.FAILED,
                    dispatchState = DispatchState.BLOCKED,
                    reason = command.reason,
                )
            } else {
                Transition.Reject("MarkFailed 只允许从活动会话状态触发，当前状态为 ${before.status}")
            }
    }

    /** 纯判定，不修改状态。 */
    private sealed interface Transition {
        data class Apply(
            val status: SessionStatus,
            val dispatchState: DispatchState,
            val reason: String? = null,
        ) : Transition

        data class Reject(val reason: String) : Transition
    }

    companion object {
        /** 默认幂等窗口容量；覆盖页面重建与短时重复投递，长会话按 LRU 淘汰。 */
        const val DEFAULT_LEDGER_CAPACITY: Int = 256

        /**
         * 会话不变式：RUNNING 只能出现在 RECORDING。
         *
         * 由此自动保证 STOPPING、PAUSED、LOW_STORAGE_PAUSED、FAILED、IDLE 均为 BLOCKED。
         */
        fun invariantHolds(status: SessionStatus, dispatchState: DispatchState): Boolean =
            dispatchState != DispatchState.RUNNING || status == SessionStatus.RECORDING
    }
}
