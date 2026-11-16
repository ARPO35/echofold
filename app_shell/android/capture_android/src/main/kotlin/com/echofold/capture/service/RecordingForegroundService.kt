package com.echofold.capture.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.echofold.capture.command.*
import com.echofold.capture.encoder.*
import com.echofold.capture.pcm.AmplitudeSampler
import com.echofold.capture.pcm.SessionAsrBinding
import com.echofold.capture.pcm.SessionAsrEventPublisher
import com.echofold.core.session.SessionConfigHash
import com.echofold.capture.pipeline.*
import com.echofold.capture.session.RecordingController
import com.echofold.capture.session.RecordingEnvironment
import com.echofold.core.chunk.FileChunkRepository
import com.echofold.core.model.*
import com.echofold.core.session.FileSessionStore
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 前台服务是麦克风与文件生命周期的唯一所有者，页面销毁不发送 STOP。 */
class RecordingForegroundService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ScheduledThreadPoolExecutor(1)
    private val bus = RecordingCommandBus()
    private lateinit var controller: RecordingController
    private lateinit var sessions: FileSessionStore
    private lateinit var chunks: FileChunkRepository
    private lateinit var root: File
    private var sessionId: String? = null
    private var capturedMs = 0L
    private var onlyRecording = true
    private var errorMessage: String? = null
    private var errorCode: String? = null
    @Volatile private var foregroundReady = false
    @Volatile private var destroyed = false
    private var asrBinding: SessionAsrBinding? = null
    private var pipeline: RecordingPipeline? = null
    private val delivery = object : Runnable {
        override fun run() {
            RecordingBridge.deliver()
            main.postDelayed(this, 100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        root = File(filesDir, "recording")
        controller = RecordingController(Environment())
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "录音状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "录音进行中的常驻状态提示"; setShowBadge(false)
            })
        main.post(delivery)
        worker.scheduleAtFixedRate({
            try {
                pipeline?.let { capturedMs = it.capturedMs }
                if (asrBinding != null) SessionAsrRegistry.events.takeFailure()?.let { fail(it) }
                publish()
            } catch (error: Exception) { fail(error) }
        }, 250, 250, TimeUnit.MILLISECONDS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_COMMAND_ID)
        val type = RecordingCommandType.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_COMMAND_TYPE) }
        if (id.isNullOrBlank() || type == null || intent.action != ACTION_COMMAND) {
            // 无效或系统重投 Intent 不得结束现有录音，也不自动恢复。
            if (sessionId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val command = RecordingCommand(id, type)
        val submission = bus.enqueue(command)
        if (submission !is CommandSubmission.Accepted) {
            if (submission is CommandSubmission.Duplicate && submission.result != null) {
                RecordingBridge.complete(id, submission.result.error?.name, submission.result.error?.name)
            } else if (submission == CommandSubmission.Full) RecordingBridge.complete(id, "COMMAND_BUSY", "录音命令繁忙，请稍后重试")
            return START_NOT_STICKY
        }
        if (type == RecordingCommandType.START && !foregroundReady) {
            try {
                check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "请先授予麦克风权限" }
                startForeground(NOTIFICATION_ID, notification("正在准备录音"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                foregroundReady = true
            } catch (error: Exception) {
                val code = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) "MIC_PERMISSION_DENIED" else "FOREGROUND_UNAVAILABLE"
                RecordingBridge.complete(id, code, error.message ?: "无法启动麦克风前台服务，请从可见页面重试")
                bus.complete(id, RecordingCommandResult(id, type, false, SessionStatus.IDLE, RecordingError.FOREGROUND_UNAVAILABLE))
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        val requestedSession = intent.getStringExtra(EXTRA_SESSION_ID)
        val onlyRecording = intent.getBooleanExtra(EXTRA_ONLY_RECORDING, true)
        worker.execute { execute(command, requestedSession, onlyRecording, startId) }
        return START_NOT_STICKY
    }

    private fun execute(command: RecordingCommand, requestedSession: String?, requestedOnlyRecording: Boolean, startId: Int) {
        val rejectedStart = command.type == RecordingCommandType.START && controller.status != SessionStatus.IDLE
        try {
            if (!rejectedStart) {
                errorCode = null
                errorMessage = null
            }
            if (command.type == RecordingCommandType.START) {
                check(controller.status == SessionStatus.IDLE) { "已有会话，请先停止当前录音" }
                check(!requestedSession.isNullOrBlank()) { "缺少会话身份" }
                sessions = FileSessionStore(File(root, "sessions.json"))
                chunks = FileChunkRepository(File(root, "chunks.json"))
                val sid = requestedSession
                check(sessions.find(sid) == null) { "会话身份已存在，禁止覆写" }
                val now = System.currentTimeMillis()
                var publisher: SessionAsrEventPublisher? = null
                if (!requestedOnlyRecording) SessionAsrRegistry.events.requireReadyForSession()
                val binding = if (requestedOnlyRecording) null else SessionAsrRegistry.requireFactory().create(
                    sessionId = sid,
                    onPcmFrame = { publisher?.onPcmFrame(it) ?: error("ASR事件发布器尚未就绪") },
                    onFinalText = { publisher?.final(it) ?: error("ASR事件发布器尚未就绪") },
                    onPartialText = { publisher?.partial(it) ?: error("ASR事件发布器尚未就绪") },
                    onFailure = ::handleAsrFailure,
                )
                asrBinding = binding
                if (binding != null) {
                    publisher = SessionAsrEventPublisher(sid, binding.model,
                        chunksFor = { start, end -> chunks.listBySession(sid).filter { chunk ->
                            when (chunk.state) {
                                ChunkState.PLACEHOLDER, ChunkState.OPEN, ChunkState.SEALING -> true
                                else -> {
                                    val chunkStart = chunk.capturedStartMs
                                    val chunkEnd = chunk.capturedEndMs
                                    chunkStart != null && chunkEnd != null && chunkStart <= end && chunkEnd >= start
                                }
                            }
                        } },
                        offer = SessionAsrRegistry.events::offer)
                }
                sessions.create(RecordingSession(sid, SessionStatus.IDLE, DispatchState.BLOCKED, now,
                    binding?.model?.modelId, binding?.model?.modelVersion, binding?.model?.installationId,
                    SessionConfigHash.of(binding?.model, binding?.config), 0,
                    ClockAnchor(sid, now, System.nanoTime(), ClockQuality.DEVICE_BOOT_BASED)))
                sessionId = sid
                capturedMs = 0L
                onlyRecording = requestedOnlyRecording
            }
            val result = controller.handle(command)
            if (!result.accepted) {
                errorCode = result.error?.name ?: "PIPELINE_UNAVAILABLE"
                errorMessage = when (result.error) {
                    RecordingError.MIC_PERMISSION_DENIED -> "请授予麦克风权限后重试"
                    RecordingError.FOREGROUND_UNAVAILABLE -> "请从可见页面启动录音"
                    RecordingError.ILLEGAL_STATE -> "当前状态不允许该操作"
                    else -> "录音管线不可用，请检查麦克风和存储空间"
                }
                if (command.type == RecordingCommandType.START) throw IllegalStateException(errorMessage)
            }
            persist()
            if (result.accepted && result.status == SessionStatus.IDLE) {
                sessionId = null
                onlyRecording = true
            }
            publish()
            bus.complete(command.commandId, result)
            RecordingBridge.complete(command.commandId, errorCode, errorMessage)
            main.post {
                if (result.status == SessionStatus.IDLE) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundReady = false
                    stopSelf(startId)
                } else updateNotification()
            }
        } catch (error: Exception) {
            if (rejectedStart) {
                RecordingBridge.complete(command.commandId, "ILLEGAL_STATE", error.message)
            } else {
                if (command.type == RecordingCommandType.START && (pipeline?.capturedMs ?: 0L) == 0L) {
                    try { pipeline?.stopAndRelease() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                    pipeline = null
                    releaseAsrBinding(error)
                    controller.reportStartupFailure()
                    try { persist() } catch (persistError: Exception) { error.addSuppressed(persistError) }
                    sessionId = null
                    capturedMs = 0L
                    onlyRecording = true
                    errorCode = "PIPELINE_UNAVAILABLE"
                    errorMessage = error.message ?: "录音服务启动失败"
                    publish()
                } else fail(error)
                RecordingBridge.complete(command.commandId, errorCode, errorMessage)
            }
            bus.complete(command.commandId, RecordingCommandResult(command.commandId, command.type,
                false, controller.status, if (rejectedStart) RecordingError.ILLEGAL_STATE else RecordingError.PIPELINE_UNAVAILABLE))
            if (sessionId == null) main.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundReady = false
                stopSelf(startId)
            }
        }
    }

    private fun fail(error: Exception) {
        errorCode = (error as? RecordingStorageException)?.code
            ?: (error.cause as? RecordingStorageException)?.code
            ?: (error as? RecordingPipelineException)?.failure?.error?.name ?: "PIPELINE_UNAVAILABLE"
        errorMessage = error.message ?: "录音失败，已保留文件，请检查麦克风或存储"
        controller.reportFailure()
        try { pipeline?.pause() } catch (pauseError: Exception) { error.addSuppressed(pauseError) }
        try { persist() } catch (persistError: Exception) {
            errorMessage = "$errorMessage；保存会话状态失败：${persistError.message}"
        }
        publish()
        main.post { updateNotification() }
    }

    private fun handleAsrFailure(error: com.echofold.core.asr.engine.AsrEngineException) {
        if (!worker.isShutdown) try {
            worker.execute { if (sessionId != null && asrBinding != null && controller.status != SessionStatus.FAILED) fail(error) }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    private fun persist() {
        val sid = sessionId ?: return
        if (::sessions.isInitialized) sessions.updateState(sid, controller.status, controller.dispatchState)
    }

    private fun publish() {
        RecordingBridge.publish(sessionId, controller.status.name,
            controller.status != SessionStatus.RECORDING || controller.dispatchState == DispatchState.BLOCKED,
            capturedMs, onlyRecording, errorCode, errorMessage)
    }

    private fun releaseAsrBinding(previous: Exception? = null) {
        val binding = asrBinding ?: return
        // 先移交所有权，close/release 抛错或重复清理也不能再次释放租约。
        asrBinding = null
        var failure = previous
        try { binding.sink.close() } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        try { binding.release() } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        if (previous == null) failure?.let { throw it }
    }

    private inner class Environment : RecordingEnvironment {
        override fun hasMicrophonePermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        override fun canStartForeground() = foregroundReady
        override fun acquirePipeline(): Boolean {
            val sid = checkNotNull(sessionId)
            val sink = RecordingChunkSink(sid, root, chunks, AndroidAacChunkEncoderFactory(), { chunk ->
                sessions.updateActiveChunk(sid, chunk.chunkSeq)
            })
            val created = DefaultRecordingPipelineFactory({ CaptureLanes(asrBinding?.sink, sink,
                AmplitudeSampler(listener = { RecordingBridge.offerAmplitude(sid, it) })) },
                audioConfig = com.echofold.capture.source.AudioRecordConfig(readBufferFrames = 96_000)).create(sid)
            pipeline = created
            created.setStateListener { _, failure -> if (!worker.isShutdown) worker.execute {
                fail(failure?.cause as? Exception ?: if (failure != null)
                    RecordingPipelineException(failure.message, failure)
                    else RecordingStorageException("PCM_BACKPRESSURE", "音频保存队列已满，已停止采集，请检查存储"))
            } }
            try { created.start() } catch (error: Exception) {
                try { created.stopAndRelease() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                pipeline = null
                releaseAsrBinding(error)
                throw error
            }
            return true
        }
        override fun pausePipeline() { checkNotNull(pipeline).pause(); capturedMs = checkNotNull(pipeline).capturedMs }
        override fun resumePipeline() { checkNotNull(pipeline).resume() }
        override fun releasePipeline() {
            val current = pipeline
            try {
                if (current != null) {
                    if (controller.status == SessionStatus.STOPPING) publish()
                    current.stopAndRelease()
                }
            } finally {
                if (current != null) capturedMs = current.capturedMs
                pipeline = null
                releaseAsrBinding()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        foregroundReady = false
        main.removeCallbacks(delivery)
        worker.execute {
            try { controller.release(); persist() }
            catch (error: Exception) { fail(error) }
            finally {
                try { releaseAsrBinding() } catch (error: Exception) { fail(error) }
                if (controller.status == SessionStatus.IDLE) {
                    sessionId = null
                    onlyRecording = true
                }
                publish()
            }
        }
        worker.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun updateNotification() {
        if (!foregroundReady || destroyed) return
        val text = when (RecordingBridge.snapshot["status"]) {
            "RECORDING" -> if (onlyRecording) "正在录音" else "正在录音并实时转写"
            "PAUSED" -> if (onlyRecording) "录音已暂停" else "录音与实时转写已暂停"
            "FAILED" -> "录音保存异常，文件已保留，请打开查看"
            else -> "录音状态已更新"
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("EchoFold 录音")
        .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
        .apply { packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            setContentIntent(PendingIntent.getActivity(this@RecordingForegroundService, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } }.build()

    companion object {
        const val ACTION_COMMAND = "com.echofold.capture.action.RECORDING_COMMAND"
        const val EXTRA_COMMAND_ID = "com.echofold.capture.extra.COMMAND_ID"
        const val EXTRA_COMMAND_TYPE = "com.echofold.capture.extra.COMMAND_TYPE"
        const val EXTRA_SESSION_ID = "com.echofold.capture.extra.SESSION_ID"
        const val EXTRA_ONLY_RECORDING = "com.echofold.capture.extra.ONLY_RECORDING"
        private const val NOTIFICATION_ID = 4711
        private const val CHANNEL_ID = "echofold.recording"
        fun commandIntent(context: Context, commandId: String, type: RecordingCommandType,
            sessionId: String? = null, onlyRecording: Boolean = true): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND_ID, commandId).putExtra(EXTRA_COMMAND_TYPE, type.name)
                .putExtra(EXTRA_SESSION_ID, sessionId).putExtra(EXTRA_ONLY_RECORDING, onlyRecording)
    }
}
