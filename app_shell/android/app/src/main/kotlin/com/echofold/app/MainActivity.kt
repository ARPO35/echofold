package com.echofold.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import com.echofold.core.asr.AsrEvents
import com.echofold.core.asr.AsrEventJson
import com.echofold.capture.command.RecordingCommandType
import com.echofold.capture.service.RecordingBridge
import com.echofold.capture.service.RecordingForegroundService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

/** Activity 只桥接命令与权限；麦克风资源由前台服务独立拥有。 */
class MainActivity : FlutterActivity() {
    private var visible = false
    private var permissionResult: MethodChannel.Result? = null
    private var sessionListener: ((Map<String, Any?>) -> Unit)? = null
    private var amplitudeListener: ((List<Map<String, Any?>>) -> Unit)? = null
    private val asrHandler = Handler(Looper.getMainLooper())
    private var asrSink: EventChannel.EventSink? = null
    private val asrDelivery = object : Runnable {
        override fun run() {
            val sink = asrSink ?: return
            try {
                AsrEvents.buffer.deliver { sink.success(AsrEventJson.toChannelMap(it)) }
            } catch (error: Exception) {
                asrSink = null
                sink.error("ASR_DELIVERY_FAILED", "转写事件投递失败", null)
                return
            }
            asrHandler.postDelayed(this, 50)
        }
    }

    override fun onResume() { super.onResume(); visible = true; sessionListener?.invoke(RecordingBridge.snapshot) }
    override fun onPause() { visible = false; super.onPause() }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        EventChannel(messenger, "com.echofold.app/asr_events").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                asrHandler.removeCallbacks(asrDelivery)
                asrSink = events
                asrHandler.post(asrDelivery)
            }
            override fun onCancel(arguments: Any?) {
                asrSink = null
                asrHandler.removeCallbacks(asrDelivery)
            }
        })
        EventChannel(messenger, "com.echofold.app/session_events").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                sessionListener = { events.success(it) }
                RecordingBridge.sessionListener = sessionListener
                events.success(RecordingBridge.snapshot)
            }
            override fun onCancel(arguments: Any?) {
                if (RecordingBridge.sessionListener === sessionListener) RecordingBridge.sessionListener = null
                sessionListener = null
            }
        })
        EventChannel(messenger, "com.echofold.app/amplitude_events").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                amplitudeListener = { batch: List<Map<String, Any?>> -> events.success(batch) }
                RecordingBridge.amplitudeListener = amplitudeListener
            }
            override fun onCancel(arguments: Any?) {
                if (RecordingBridge.amplitudeListener === amplitudeListener) RecordingBridge.amplitudeListener = null
                amplitudeListener = null
            }
        })
        MethodChannel(messenger, "com.echofold.app/platform").setMethodCallHandler { call, result ->
            when (call.method) {
                "recording.snapshot" -> result.success(RecordingBridge.snapshot)
                "permission.microphone" -> requestMicrophone(result)
                "app.settings" -> try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    result.success(mapOf("opened" to true))
                } catch (error: Exception) { result.error("SETTINGS_UNAVAILABLE", "无法打开应用设置：${error.message}", null) }
                "recording.start", "recording.command" -> {
                    val id = call.argument<String>("commandId")
                    val validId = try { id != null && UUID.fromString(id).toString().equals(id, true) } catch (_: IllegalArgumentException) { false }
                    val start = call.method == "recording.start"
                    val type = if (start) RecordingCommandType.START else
                        RecordingCommandType.entries.firstOrNull { it.name == call.argument<String>("command") && it != RecordingCommandType.START }
                    when {
                        !validId || type == null -> result.error("INVALID_ARGUMENT", "请提供有效 commandId 和录音命令", null)
                        start && call.argument<Boolean>("onlyRecording") != true -> result.error("ONLY_RECORDING_REQUIRED", "请显式选择仅录音模式", null)
                        (start || type == RecordingCommandType.RESUME) && !visible -> result.error("FOREGROUND_UNAVAILABLE", "请回到录音页面后操作", null)
                        start && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> result.error("MIC_PERMISSION_DENIED", "请先授予麦克风权限", null)
                        else -> {
                            val commandId = checkNotNull(id)
                            val reserved = RecordingBridge.reserve(commandId) { snapshot, code, message ->
                                if (code == null) result.success(snapshot) else result.error(code, message, snapshot)
                            }
                            if (reserved) {
                                try {
                                    val intent = RecordingForegroundService.commandIntent(this, commandId, type,
                                        if (start) UUID.randomUUID().toString() else null, onlyRecording = start)
                                    if (start) startForegroundService(intent) else startService(intent)
                                } catch (error: Exception) {
                                    RecordingBridge.complete(commandId, "FOREGROUND_UNAVAILABLE", "无法发送录音命令：${error.message}")
                                }
                            }
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun requestMicrophone(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            result.success(mapOf("granted" to true, "permanentlyDenied" to false)); return
        }
        if (!visible) { result.error("FOREGROUND_UNAVAILABLE", "请回到应用后授予麦克风权限", null); return }
        if (permissionResult != null) { result.error("PERMISSION_PENDING", "麦克风权限请求仍在等待系统结果", null); return }
        permissionResult = result
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_REQUEST) return
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionResult?.success(mapOf("granted" to granted,
            "permanentlyDenied" to (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))))
        permissionResult = null
    }

    override fun onDestroy() {
        asrSink = null
        asrHandler.removeCallbacks(asrDelivery)
        if (RecordingBridge.sessionListener === sessionListener) RecordingBridge.sessionListener = null
        if (RecordingBridge.amplitudeListener === amplitudeListener) RecordingBridge.amplitudeListener = null
        permissionResult?.error("ACTIVITY_DESTROYED", "权限页面已重建，请重新请求权限", null)
        permissionResult = null
        super.onDestroy()
    }

    companion object { private const val MICROPHONE_REQUEST = 4712 }
}
