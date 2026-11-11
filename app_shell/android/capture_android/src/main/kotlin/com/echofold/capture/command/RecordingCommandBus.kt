package com.echofold.capture.command

import com.echofold.core.model.SessionStatus

/** 录音控制命令类型；命令只来自可见界面的用户操作。 */
enum class RecordingCommandType { START, PAUSE, RESUME, STOP }

/**
 * 一条录音控制命令。
 *
 * [commandId] 由调用方生成（页面重建后重发同一 ID 必须复用），是幂等去重的唯一键。
 */
data class RecordingCommand(
    val commandId: String,
    val type: RecordingCommandType,
) {
    init {
        require(commandId.isNotBlank()) { "commandId 不能为空白字符串" }
    }
}

/** 命令拒绝原因；UI 必须展示可操作原因，而不是假装进入录音态。 */
enum class RecordingError {
    /** 未授予麦克风权限（含运行中被撤销）。 */
    MIC_PERMISSION_DENIED,

    /** 前台服务条件不满足（例如通知被禁止或 startForeground 失败）。 */
    FOREGROUND_UNAVAILABLE,

    /** 采集/编码管线未就绪或初始化失败。 */
    PIPELINE_UNAVAILABLE,

    /** 当前状态不允许该命令（例如空闲态 PAUSE）。 */
    ILLEGAL_STATE,

    /** START 缺少会话 ID；会话身份由上层管理，服务不自造。 */
    MISSING_SESSION,
}

/** 命令执行结果；同一个 `commandId` 的结果一经产生不得被覆盖。 */
data class RecordingCommandResult(
    val commandId: String,
    val type: RecordingCommandType,
    val accepted: Boolean,
    val status: SessionStatus,
    val error: RecordingError? = null,
) {
    init {
        require(commandId.isNotBlank()) { "commandId 不能为空白字符串" }
        require(accepted || error != null) { "被拒绝的命令必须给出原因" }
    }
}

/** 入队结果。 */
sealed interface CommandSubmission {
    /** 首次提交，命令已入队等待执行。 */
    data class Accepted(val command: RecordingCommand) : CommandSubmission

    /**
     * 重复的 `commandId`：不再入队、不重复执行。
     *
     * [result] 为首次结果；仍在该 ID 首次执行中时为 null。
     */
    data class Duplicate(val result: RecordingCommandResult?) : CommandSubmission

    /** 命令队列已满；调用方应提示稍后重试，不得丢弃已接受命令。 */
    data object Full : CommandSubmission
}

/**
 * 有界录音命令总线。
 *
 * - 命令队列与结果表都有固定容量，避免界面重试把服务压垮；
 * - 同一 `commandId` 只执行一次：已完成返回首次结果，执行中返回 `Duplicate(null)`；
 * - 结果表按插入顺序淘汰最旧条目（容量耗尽后极端情况下旧 ID 会被再次执行，
 *   因此 resultCapacity 必须显著大于 UI 可能重发的窗口）；
 * - 所有方法线程安全（界面线程与服务线程可并发访问）。
 */
class RecordingCommandBus(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val resultCapacity: Int = DEFAULT_RESULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "命令队列容量必须为正数：$capacity" }
        require(resultCapacity > 0) { "结果表容量必须为正数：$resultCapacity" }
    }

    private val lock = Any()
    private val pending = ArrayDeque<RecordingCommand>()
    private val results = LinkedHashMap<String, RecordingCommandResult>()

    /** 提交一条命令；重复 ID 不会再次入队。 */
    fun enqueue(command: RecordingCommand): CommandSubmission {
        synchronized(lock) {
            results[command.commandId]?.let { return CommandSubmission.Duplicate(it) }
            if (pending.any { it.commandId == command.commandId }) {
                return CommandSubmission.Duplicate(null)
            }
            if (pending.size >= capacity) {
                return CommandSubmission.Full
            }
            pending.addLast(command)
            return CommandSubmission.Accepted(command)
        }
    }

    /** 服务取出下一条待执行命令；无命令返回 null。 */
    fun next(): RecordingCommand? = synchronized(lock) { pending.removeFirstOrNull() }

    /**
     * 记录命令结果。
     *
     * @return 是否记录了新结果；同一 `commandId` 的首次结果不会被覆盖（返回 false）。
     */
    fun complete(commandId: String, result: RecordingCommandResult): Boolean {
        require(result.commandId == commandId) { "结果与 commandId 不匹配：$commandId / ${result.commandId}" }
        synchronized(lock) {
            if (results.containsKey(commandId)) return false
            results[commandId] = result
            pending.removeAll { it.commandId == commandId }
            while (results.size > resultCapacity) {
                val eldest = results.keys.firstOrNull() ?: break
                results.remove(eldest)
            }
            return true
        }
    }

    /** 查询首次结果；未完成返回 null。 */
    fun result(commandId: String): RecordingCommandResult? = synchronized(lock) { results[commandId] }

    /** 待执行命令数。 */
    val pendingCount: Int get() = synchronized(lock) { pending.size }

    /** 已记录结果数。 */
    val resultCount: Int get() = synchronized(lock) { results.size }

    /** 清空队列与结果（服务销毁时调用）；不影响已完成的外部状态。 */
    fun clear() = synchronized(lock) {
        pending.clear()
        results.clear()
    }

    companion object {
        /** 默认命令队列容量：界面最多短暂连发数次命令。 */
        const val DEFAULT_CAPACITY: Int = 8

        /** 默认结果表容量：覆盖常见的页面重发窗口。 */
        const val DEFAULT_RESULT_CAPACITY: Int = 64
    }
}
