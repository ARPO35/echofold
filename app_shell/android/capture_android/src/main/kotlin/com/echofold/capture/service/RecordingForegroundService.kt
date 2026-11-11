package com.echofold.capture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.echofold.capture.command.CommandSubmission
import com.echofold.capture.command.RecordingCommand
import com.echofold.capture.command.RecordingCommandBus
import com.echofold.capture.command.RecordingCommandResult
import com.echofold.capture.command.RecordingCommandType
import com.echofold.capture.command.RecordingError
import com.echofold.capture.pipeline.RecordingPipeline
import com.echofold.capture.pipeline.RecordingPipelineFactory
import com.echofold.capture.pipeline.RecordingPipelineStateListener
import com.echofold.capture.session.RecordingController
import com.echofold.capture.session.RecordingEnvironment
import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus

/**
 * 麦克风前台录音服务。
 *
 * 契约要点：
 * 1. 只在用户从可见界面发出 `START` 后启动；服务本身不主动创建会话；
 * 2. 声明 `foregroundServiceType="microphone"`，并以
 *    [ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE] 调用 `startForeground`；
 * 3. 权限、前台条件或采集管线任一失败都不进入录音态（状态保持 `IDLE`），
 *    结果写回 [RecordingCommandBus]，界面据此展示可操作原因；
 * 4. 命令按 `commandId` 幂等：页面重建/系统重投同一 ID 不重复执行，返回首次结果；
 * 5. 通知常驻显示录音状态（录音中/已暂停/保存异常），不随页面销毁消失；
 * 6. `STOP` 与 `onDestroy` 释放采集/编码资源，通知撤销。
 *
 * 管线由集成层通过 [installPipelineFactory] 注入（真实 ASR 引擎 + 块编码器）；未注入时
 * `START` 返回 `PIPELINE_UNAVAILABLE`，服务不会伪造采集。
 */
class RecordingForegroundService : Service() {

    private val commandBus = RecordingCommandBus()

    private lateinit var controller: RecordingController
    private lateinit var environment: ServiceEnvironment
    private var pipeline: RecordingPipeline? = null
    private var activeSessionId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 管线状态回调：切回主线程刷新通知与状态机可见状态。 */
    private val pipelineListener = RecordingPipelineStateListener { dispatch, _ ->
        mainHandler.post {
            controller.reportDispatch(dispatch)
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        environment = ServiceEnvironment()
        controller = RecordingController(environment)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val parsed = ParsedCommand.from(intent)
        if (parsed == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (val submission = commandBus.enqueue(parsed.command)) {
            is CommandSubmission.Accepted -> execute(parsed, startId)

            is CommandSubmission.Duplicate -> {
                // 重复命令不执行：若首次结果已存在，同步通知后按状态收尾。
                submission.result?.let { result ->
                    updateNotification()
                    if (isIdleOrStopped(result)) {
                        teardownForeground(startId)
                    }
                }
            }

            CommandSubmission.Full -> {
                // 队列已满：拒绝但不改变录音状态，界面稍后重试。
                val result = RecordingCommandResult(
                    commandId = parsed.command.commandId,
                    type = parsed.command.type,
                    accepted = false,
                    status = controller.status,
                    error = RecordingError.ILLEGAL_STATE,
                )
                commandBus.complete(parsed.command.commandId, result)
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.release()
        pipeline = null
        activeSessionId = null
        commandBus.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun execute(parsed: ParsedCommand, startId: Int) {
        val command = parsed.command
        if (command.type == RecordingCommandType.START) {
            if (parsed.sessionId.isNullOrBlank()) {
                finish(
                    RecordingCommandResult(
                        commandId = command.commandId,
                        type = command.type,
                        accepted = false,
                        status = SessionStatus.IDLE,
                        error = RecordingError.MISSING_SESSION,
                    ),
                    startId,
                )
                return
            }
            // 权限先于 startForeground 检查：Android 14 起麦克风前台类型在无权限时 startForeground 会抛
            // SecurityException，先分类才能给用户“去授权”的可操作原因。
            if (!environment.hasMicrophonePermission()) {
                finish(
                    RecordingCommandResult(
                        commandId = command.commandId,
                        type = command.type,
                        accepted = false,
                        status = SessionStatus.IDLE,
                        error = RecordingError.MIC_PERMISSION_DENIED,
                    ),
                    startId,
                )
                return
            }
            // 已在录音/暂停时重复 START 是幂等 no-op，不得替换当前会话。
            if (controller.status == SessionStatus.IDLE) {
                activeSessionId = parsed.sessionId
            }
            environment.foregroundReady = enterForeground()
            if (!environment.foregroundReady) {
                activeSessionId = null
                finish(
                    RecordingCommandResult(
                        commandId = command.commandId,
                        type = command.type,
                        accepted = false,
                        status = SessionStatus.IDLE,
                        error = RecordingError.FOREGROUND_UNAVAILABLE,
                    ),
                    startId,
                )
                return
            }
        } else {
            // 已在前台时保持通知最新；前台条件失败只会让命令被拒绝，不改变状态。
            environment.foregroundReady = enterForeground()
        }

        val result = try {
            controller.handle(command)
        } catch (error: RuntimeException) {
            RecordingCommandResult(
                commandId = command.commandId,
                type = command.type,
                accepted = false,
                status = controller.status,
                error = RecordingError.PIPELINE_UNAVAILABLE,
            )
        }
        if (!result.accepted) {
            activeSessionId = null
        }
        finish(result, startId)
    }

    private fun finish(result: RecordingCommandResult, startId: Int) {
        commandBus.complete(result.commandId, result)
        updateNotification()
        if (isIdleOrStopped(result)) {
            teardownForeground(startId)
        }
    }

    private fun isIdleOrStopped(result: RecordingCommandResult): Boolean =
        !result.accepted || result.status == SessionStatus.IDLE

    private fun teardownForeground(startId: Int) {
        pipeline = null
        activeSessionId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    /** 进入前台并返回是否成功；任何平台失败都不进入录音态。 */
    private fun enterForeground(): Boolean {
        val notification = buildNotification()
        return try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (controller.status == SessionStatus.IDLE) return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(statusText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        launchIntent()?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    private fun statusText(): String = when {
        controller.dispatchState == DispatchState.BLOCKED -> "录音保存异常：已停止采集，等待重试"
        controller.status == SessionStatus.RECORDING -> "正在录音"
        controller.status == SessionStatus.PAUSED -> "已暂停录音"
        controller.status == SessionStatus.STOPPING -> "正在结束录音"
        controller.status == SessionStatus.LOW_STORAGE_PAUSED -> "存储空间不足，已暂停"
        controller.status == SessionStatus.FAILED -> "录音失败，请检查权限或存储"
        else -> "未在录音"
    }

    private fun launchIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "录音进行中的常驻状态提示"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** 服务侧环境实现：权限、前台条件与管线生命周期都在这里落地。 */
    private inner class ServiceEnvironment : RecordingEnvironment {

        /** `startForeground` 是否成功；由 [execute] 在命令处理前设置。 */
        var foregroundReady: Boolean = false

        override fun hasMicrophonePermission(): Boolean =
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        override fun canStartForeground(): Boolean = foregroundReady

        override fun acquirePipeline(): Boolean {
            val factory = pipelineFactory ?: return false
            val sessionId = activeSessionId ?: return false
            val created = try {
                factory.create(sessionId)
            } catch (_: RuntimeException) {
                null
            } ?: return false
            return try {
                created.start()
                created.setStateListener(pipelineListener)
                pipeline = created
                controller.reportDispatch(created.dispatchState)
                true
            } catch (_: Exception) {
                // 启动失败必须释放已部分获取的资源，并保持 IDLE。
                runCatching { created.stopAndRelease() }
                false
            }
        }

        override fun pausePipeline() {
            runCatching { pipeline?.pause() }
        }

        override fun resumePipeline() {
            runCatching { pipeline?.resume() }
        }

        override fun releasePipeline() {
            runCatching { pipeline?.stopAndRelease() }
            pipeline = null
        }
    }

    /** 解析后的命令；`START` 必须携带会话 ID。 */
    private data class ParsedCommand(val command: RecordingCommand, val sessionId: String?) {
        companion object {
            fun from(intent: Intent?): ParsedCommand? {
                if (intent == null || intent.action != ACTION_COMMAND) return null
                val commandId = intent.getStringExtra(EXTRA_COMMAND_ID) ?: return null
                val typeName = intent.getStringExtra(EXTRA_COMMAND_TYPE) ?: return null
                val type = runCatching { RecordingCommandType.valueOf(typeName) }.getOrNull() ?: return null
                if (commandId.isBlank()) return null
                return ParsedCommand(
                    command = RecordingCommand(commandId, type),
                    sessionId = intent.getStringExtra(EXTRA_SESSION_ID),
                )
            }
        }
    }

    companion object {
        /** 服务动作：所有命令都通过带该 action 的 Intent 投递。 */
        const val ACTION_COMMAND: String = "com.echofold.capture.action.RECORDING_COMMAND"

        const val EXTRA_COMMAND_ID: String = "com.echofold.capture.extra.COMMAND_ID"
        const val EXTRA_COMMAND_TYPE: String = "com.echofold.capture.extra.COMMAND_TYPE"
        const val EXTRA_SESSION_ID: String = "com.echofold.capture.extra.SESSION_ID"

        private const val NOTIFICATION_ID = 4_711
        private const val CHANNEL_ID = "echofold.recording"
        private const val CHANNEL_NAME = "录音状态"
        private const val NOTIFICATION_TITLE = "EchoFold 录音"

        @Volatile
        private var pipelineFactory: RecordingPipelineFactory? = null

        /** 集成层注入真实管线工厂；注入前 `START` 会被拒绝而不是伪造采集。 */
        fun installPipelineFactory(factory: RecordingPipelineFactory?) {
            pipelineFactory = factory
        }

        /** 构造启动服务的命令 Intent，供 Flutter 桥/Activity 调用。 */
        fun commandIntent(
            context: Context,
            commandId: String,
            type: RecordingCommandType,
            sessionId: String? = null,
        ): Intent = Intent(context, RecordingForegroundService::class.java)
            .setAction(ACTION_COMMAND)
            .putExtra(EXTRA_COMMAND_ID, commandId)
            .putExtra(EXTRA_COMMAND_TYPE, type.name)
            .apply { if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId) }
    }
}
