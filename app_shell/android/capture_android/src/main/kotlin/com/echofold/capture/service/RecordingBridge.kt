package com.echofold.capture.service

import android.os.Handler
import android.os.Looper
import com.echofold.capture.pcm.AmplitudeSample
import java.util.concurrent.ConcurrentLinkedQueue

/** 进程内桥只保存最新状态、有界振幅队列和命令结果，Activity 不持有采集资源。 */
object RecordingBridge {
    /** 投递队列上限；溢出丢最旧样本并计数，避免 UI 卡顿时无限积压。 */
    private const val AMPLITUDE_QUEUE_CAPACITY = 512

    private val main = Handler(Looper.getMainLooper())
    private var sequence = 0L
    @Volatile var snapshot: Map<String, Any?> = mapOf(
        "sessionId" to null, "status" to "IDLE", "dispatchState" to "BLOCKED",
        "capturedMs" to 0L, "onlyRecording" to true, "errorCode" to null,
        "errorMessage" to null, "sequence" to 0L,
    )
        private set
    var sessionListener: ((Map<String, Any?>) -> Unit)? = null
    var amplitudeListener: ((List<Map<String, Any?>>) -> Unit)? = null
    private val amplitudeQueue = ConcurrentLinkedQueue<Map<String, Any?>>()
    /** 溢出丢弃的样本数；可观测，不静默丢音频证据（振幅只是展示流）。 */
    @Volatile var droppedAmplitudes: Long = 0L
        private set
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
    /** 页面销毁只终结该页面的回调，不取消服务命令或缓存伪造的命令结果。 */
    fun cancelPending(id: String, code: String, message: String) {
        pending.remove(id)?.invoke(snapshot, code, message)
    }


    @Synchronized fun publish(sessionId: String?, status: String, blocked: Boolean, capturedMs: Long,
        onlyRecording: Boolean, code: String? = null, message: String? = null) {
        snapshot = mapOf("sessionId" to sessionId, "status" to status,
            "dispatchState" to if (blocked) "BLOCKED" else "DISPATCHING",
            "capturedMs" to capturedMs, "onlyRecording" to if (status == "IDLE") true else onlyRecording,
            "errorCode" to code, "errorMessage" to message, "sequence" to ++sequence)
    }

    fun offerAmplitude(sessionId: String, value: AmplitudeSample) {
        val payload = mapOf("sessionId" to sessionId, "monoNs" to value.monoNs,
            "capturedEndMs" to value.capturedEndMs, "rms" to value.rms, "peak" to value.peak)
        amplitudeQueue.add(payload)
        while (amplitudeQueue.size > AMPLITUDE_QUEUE_CAPACITY) {
            amplitudeQueue.poll()
            droppedAmplitudes++
        }
    }

    /** 仅由服务主线程的固定频率 ticker 调用，永不按 PCM 帧 post。 */
    fun deliver() {
        sessionListener?.invoke(snapshot)
        if (amplitudeQueue.isEmpty()) return
        val drained = ArrayList<Map<String, Any?>>(amplitudeQueue.size)
        while (true) {
            val value = amplitudeQueue.poll() ?: break
            if (snapshot["status"] == "RECORDING" && value["sessionId"] == snapshot["sessionId"]) {
                drained.add(value)
            }
        }
        if (drained.isNotEmpty()) amplitudeListener?.invoke(drained)
    }
}
