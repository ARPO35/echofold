package com.echofold.capture.session

import com.echofold.capture.command.RecordingCommand
import com.echofold.capture.command.RecordingCommandResult
import com.echofold.capture.command.RecordingCommandType
import com.echofold.capture.command.RecordingError
import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus

/**
 * 录音状态机依赖的设备侧条件。
 *
 * 由前台服务实现（权限检查、前台条件、采集管线生命周期），JVM 单测用 fake 注入；
 * 状态机本身不引用任何 Android 类型，因此“权限失败不得进入录音态”可被直接验证。
 */
interface RecordingEnvironment {
    /** 当前是否持有 `RECORD_AUDIO` 权限。 */
    fun hasMicrophonePermission(): Boolean

    /** 前台服务条件是否满足（通知渠道可用、`startForeground` 未失败等）。 */
    fun canStartForeground(): Boolean

    /**
     * 打开采集/编码管线。
     *
     * @return 成功返回 true；失败必须自行释放已部分获取的资源并返回 false。
     */
    fun acquirePipeline(): Boolean

    /** 暂停采集（保留资源）。 */
    fun pausePipeline()

    /** 继续采集。 */
    fun resumePipeline()

    /** 停止并释放采集/编码资源；幂等。 */
    fun releasePipeline()
}

/**
 * 录音命令状态机。
 *
 * 语义（与 audio-capture 规范一致）：
 * - `START` 只有在麦克风权限、前台条件、采集管线都成功之后才把状态推进到 `RECORDING`；
 *   任一失败都停留在 `IDLE` 并返回明确原因，绝不假装进入录音状态；
 * - `PAUSE`/`RESUME`/`STOP` 只接受合法来源状态，其余返回 `ILLEGAL_STATE`（幂等重放由命令总线按
 *   `commandId` 去重，同一状态再次 START 视为无变化成功）；
 * - `STOP` 释放资源并回到 `IDLE`；页面重建不会自动重放任何命令；
 * - 命令处理不阻塞：管线 `acquire/release` 由实现保证快速返回（不在这里等模型加载）。
 */
class RecordingController(private val environment: RecordingEnvironment) {

    /** 当前会话状态。 */
    var status: SessionStatus = SessionStatus.IDLE
        private set

    /** 分发布状态；`BLOCKED` 时 UI 必须显示保存异常而不是正常录音。 */
    var dispatchState: DispatchState = DispatchState.RUNNING
        private set

    /** 最近一次被拒绝的原因，成功命令会清空。 */
    var lastError: RecordingError? = null
        private set

    /** 处理一条命令并返回结果（不抛异常）。 */
    fun handle(command: RecordingCommand): RecordingCommandResult {
        val result = when (command.type) {
            RecordingCommandType.START -> start(command)
            RecordingCommandType.PAUSE -> pause(command)
            RecordingCommandType.RESUME -> resume(command)
            RecordingCommandType.STOP -> stop(command)
        }
        lastError = result.error
        return result
    }

    /** 采集管线报告分发布状态变化（例如背压进入 BLOCKED、恢复后回到 RUNNING）。 */
    fun reportDispatch(state: DispatchState) {
        dispatchState = state
    }

    /** 服务销毁时调用：释放资源并回到 `IDLE`（幂等）。 */
    fun release() {
        if (status == SessionStatus.IDLE) return
        environment.releasePipeline()
        status = SessionStatus.IDLE
        dispatchState = DispatchState.RUNNING
    }

    private fun start(command: RecordingCommand): RecordingCommandResult {
        when (status) {
            // 已录音或已暂停：页面重建后重复的 START 是幂等 no-op，不得重启管线。
            SessionStatus.RECORDING, SessionStatus.PAUSED ->
                return accepted(command, status)

            SessionStatus.STOPPING ->
                return rejected(command, RecordingError.ILLEGAL_STATE)

            else -> Unit
        }

        if (!environment.hasMicrophonePermission()) {
            return rejected(command, RecordingError.MIC_PERMISSION_DENIED)
        }
        if (!environment.canStartForeground()) {
            return rejected(command, RecordingError.FOREGROUND_UNAVAILABLE)
        }
        if (!environment.acquirePipeline()) {
            // 失败时状态保持 IDLE：不创建会话、不显示录音态、不动已有文件。
            return rejected(command, RecordingError.PIPELINE_UNAVAILABLE)
        }
        status = SessionStatus.RECORDING
        dispatchState = DispatchState.RUNNING
        return accepted(command, status)
    }

    private fun pause(command: RecordingCommand): RecordingCommandResult {
        when (status) {
            SessionStatus.PAUSED -> return accepted(command, status)
            SessionStatus.RECORDING -> {
                environment.pausePipeline()
                status = SessionStatus.PAUSED
                return accepted(command, status)
            }

            else -> return rejected(command, RecordingError.ILLEGAL_STATE)
        }
    }

    private fun resume(command: RecordingCommand): RecordingCommandResult {
        when (status) {
            SessionStatus.RECORDING -> return accepted(command, status)
            SessionStatus.PAUSED -> Unit
            else -> return rejected(command, RecordingError.ILLEGAL_STATE)
        }
        // 暂停期间权限可能被撤销；继续前必须重新确认，否则停留在 PAUSED。
        if (!environment.hasMicrophonePermission()) {
            return rejected(command, RecordingError.MIC_PERMISSION_DENIED)
        }
        if (!environment.canStartForeground()) {
            return rejected(command, RecordingError.FOREGROUND_UNAVAILABLE)
        }
        environment.resumePipeline()
        status = SessionStatus.RECORDING
        dispatchState = DispatchState.RUNNING
        return accepted(command, status)
    }

    private fun stop(command: RecordingCommand): RecordingCommandResult {
        if (status == SessionStatus.IDLE) {
            // 空闲态 STOP 是幂等 no-op；不允许由此创建或删除会话。
            return accepted(command, status)
        }
        status = SessionStatus.STOPPING
        environment.releasePipeline()
        status = SessionStatus.IDLE
        dispatchState = DispatchState.RUNNING
        return accepted(command, status)
    }

    private fun accepted(command: RecordingCommand, resulting: SessionStatus) =
        RecordingCommandResult(
            commandId = command.commandId,
            type = command.type,
            accepted = true,
            status = resulting,
        )

    private fun rejected(command: RecordingCommand, error: RecordingError) =
        RecordingCommandResult(
            commandId = command.commandId,
            type = command.type,
            accepted = false,
            status = status,
            error = error,
        )
}
