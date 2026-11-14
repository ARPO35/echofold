/// Flutter 与 Android 原生层的通道契约。
///
/// 所有 MethodChannel 结果都是 `Map`，失败时通过 `PlatformException(code, message)`
/// 返回可操作错误；原生侧不得用成功结果掩盖权限、占位或封存失败。
library;

/// 录音命令与模型管理方法通道。
const String platformMethodsChannel = 'com.echofold.app/platform';

/// 会话状态事件通道。
const String sessionEventsChannel = 'com.echofold.app/session_events';

/// 振幅事件通道。
const String amplitudeEventsChannel = 'com.echofold.app/amplitude_events';

/// 方法名。
abstract final class PlatformMethods {
  static const String startRecording = 'recording.start';
  static const String recordingCommand = 'recording.command';
  static const String recordingSnapshot = 'recording.snapshot';
  static const String createSession = 'session.create';
  static const String chunks = 'chunks.list';
  static const String storageStatus = 'storage.status';
  static const String modelStatus = 'model.status';
  static const String modelImport = 'model.import';
  static const String modelDelete = 'model.delete';
  static const String modelRollback = 'model.rollback';
  static const String modelRerecognize = 'model.rerecognize';
  static const String exportAudio = 'export.audio';
  static const String exportChunkPaths = 'export.chunkPaths';
  static const String diagnostics = 'diagnostics.summary';
  static const String recoveryScan = 'recovery.scan';
  static const String openAppSettings = 'app.settings';
  static const String requestMicrophone = 'permission.microphone';
}

/// 原生错误码。
abstract final class PlatformErrorCodes {
  static const String microphoneDenied = 'MIC_PERMISSION_DENIED';
  static const String foregroundUnavailable = 'FOREGROUND_UNAVAILABLE';
  static const String pipelineUnavailable = 'PIPELINE_UNAVAILABLE';
  static const String modelUnavailable = 'MODEL_UNAVAILABLE';
  static const String storageLow = 'STORAGE_LOW';
  static const String placeholderFailed = 'PLACEHOLDER_FAILED';
  static const String sealFailed = 'SEAL_FAILED';
  static const String exportFailed = 'EXPORT_FAILED';
  static const String unsupported = 'UNSUPPORTED';
}
