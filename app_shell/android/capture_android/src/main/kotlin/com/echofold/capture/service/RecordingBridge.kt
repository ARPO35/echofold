package com.echofold.capture.service

import android.os.Handler
import android.os.Looper
import com.echofold.capture.pcm.AmplitudeSample
import java.util.concurrent.atomic.AtomicReference

/** 进程内桥只保存最新状态/振幅和有界命令结果，Activity 不持有采集资源。 */
object RecordingBridge {
    private val main = Handler(Looper.getMainLooper())
    private var sequence = 0L
    @Volatile var snapshot: Map<String, Any?> = mapOf(
        "sessionId" to null, "status" to "IDLE", "dispatchState" to "BLOCKED",
        "capturedMs" to 0L, "onlyRecording" to true, "errorCode" to null,
        "errorMessage" to null, "sequence" to 0L,
    )
        private set
    var sessionListener: ((Map<String, Any?>) -> Unit)? = null
    var amplitudeListener: ((Map<String, Any?>) -> Unit)? = null
    private val amplitude = AtomicReference<Map<String, Any?>?>(null)
    private val pending = LinkedHashMap<String, (Map<String, Any?>, String?, String?) -> Unit>()
    private data class Completed(val snapshot: Map<String, Any?>, val code: String?, val message: String?)
    private val completed = LinkedHashMap<String, Completed>()

    fun reserve(id: String, callback: (Map<String, Any?>, String?, String?) -> Unit): Boolean {
        completed[id]?.let { callback(it.snapshot, it.code, it.message); return false }
        if (pending.containsKey(id)) { callback(snapshot, "COMMAND_PENDING", "同一命令仍在执行，请等待结果"); return false }
        if (pending.size >= 8) { callback(snapshot, "COMMAND_BUSY", "录音命令繁忙，请稍后重试"); return false }
        pending[id] = callback
        return true
    }

    fun complete(id: String, code: String? = null, message: String? = null) {
        val result = Completed(snapshot, code, message)
        main.post {
            completed[id] = result
            while (completed.size > 64) completed.remove(completed.keys.first())
            pending.remove(id)?.invoke(result.snapshot, code, message)
        }
    }

    @Synchronized fun publish(sessionId: String?, status: String, blocked: Boolean, capturedMs: Long,
        code: String? = null, message: String? = null) {
        snapshot = mapOf("sessionId" to sessionId, "status" to status,
            "dispatchState" to if (blocked) "BLOCKED" else "DISPATCHING",
            "capturedMs" to capturedMs, "onlyRecording" to true,
            "errorCode" to code, "errorMessage" to message, "sequence" to ++sequence)
    }

    fun offerAmplitude(sessionId: String, value: AmplitudeSample) {
        amplitude.set(mapOf("sessionId" to sessionId, "monoNs" to value.monoNs,
            "capturedEndMs" to value.capturedEndMs, "rms" to value.rms, "peak" to value.peak))
    }

    /** 仅由服务主线程的固定频率 ticker 调用，永不按 PCM 帧 post。 */
    fun deliver() {
        sessionListener?.invoke(snapshot)
        amplitude.getAndSet(null)?.let { value ->
            if (snapshot["status"] == "RECORDING" && value["sessionId"] == snapshot["sessionId"]) amplitudeListener?.invoke(value)
        }
    }
}
