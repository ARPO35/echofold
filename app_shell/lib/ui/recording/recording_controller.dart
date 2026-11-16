/// 编排录音与本地转写；所有会话状态都由原生快照确认。
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../../app/app_contract.dart';
import '../../domain/models.dart';
import '../../native/platform_contracts.dart';
import 'recording_platform.dart';

final class RecordingController extends ChangeNotifier {
  RecordingController({RecordingPlatform? platform})
      : _platform = platform ?? const ChannelRecordingPlatform();

  final RecordingPlatform _platform;
  StreamSubscription<Object?>? _sessions;
  StreamSubscription<Object?>? _amplitudes;
  RecordingUiState _snapshot = const RecordingUiState.idle();
  AppFailure? _failure;
  int _sequence = -1;
  int _lastMonoNs = -1;
  int _connection = 0;
  bool _pending = false;
  bool _connecting = false;
  bool _ready = false;
  bool _connected = false;
  bool _disposed = false;
  bool _permanentlyDenied = false;
  bool _pcmDispatching = false;
  bool _onlyRecording = true;
  bool _refreshingModel = false;
  String modelMessage = '尚未读取本地模型状态';
  bool modelAvailable = false;

  Future<void> refreshModel() async {
    if (_disposed || _refreshingModel || _pending) return;
    _refreshingModel = true;
    _emit();
    try {
      final result = await _platform.invoke(PlatformMethods.modelStatus);
      if (_disposed) return;
      _applyModel(result);
    } catch (error) {
      if (_disposed) return;
      modelAvailable = false;
      final String detail = error is PlatformException
          ? error.message ?? error.code : '请重试读取本地模型状态';
      modelMessage = '模型状态读取失败：$detail。仍可选择仅录音。';
    } finally {
      _refreshingModel = false;
      _emit();
    }
  }

  bool get canImportModel => !busy && !_disposed && _snapshot.canStart;

  Future<void> importModel() async {
    if (!canImportModel) return;
    await _perform(() async {
      try {
        final result = await _platform.invoke(PlatformMethods.modelImport);
        if (!_disposed) _applyModel(result);
      } on PlatformException catch (error) {
        // 关闭系统目录选择器是正常取消，保留已确认的模型状态。
        if (error.code != 'IMPORT_CANCELLED') rethrow;
      }
    });
  }

  void _applyModel(Map<Object?, Object?> result) {
    if (result['available'] is! bool || result['message'] is! String) {
      throw PlatformException(code: 'MODEL_STATUS_INVALID', message: '模型服务返回了无效状态');
    }
    modelAvailable = result['available']! as bool;
    modelMessage = result['message']! as String;
  }

  bool get onlyRecording => _onlyRecording;
  bool get waveformFrozen => _snapshot.waveformFrozen || !_pcmDispatching || !_connected;

  bool get ready => _ready && !_disposed;
  bool get connected => _connected;
  bool get permanentlyDenied => _permanentlyDenied;
  bool get recoveryRequired =>
      _failure?.code == 'RECORDING_RECOVERY_REQUIRED' ||
      _snapshot.failure?.code == 'RECORDING_RECOVERY_REQUIRED';
  bool get busy => _pending || _connecting || _refreshingModel;
  bool get canStart => ready && connected && !busy && _snapshot.canStart;
  bool get canPause => ready && connected && !busy && _snapshot.canPause;
  bool get canResume => ready && connected && !busy && _snapshot.canResume;
  bool get canStop => ready && connected && !busy && !recoveryRequired &&
      (_snapshot.canStop ||
          (_snapshot.state == SessionState.failed && _snapshot.sessionId != null));

  bool get modelReady => modelAvailable;

  RecordingUiState get state => RecordingUiState(
        state: _snapshot.state,
        sessionId: _snapshot.sessionId,
        elapsedMs: _snapshot.elapsedMs,
        amplitudes: _snapshot.amplitudes,
        transcriptAvailable: false,
        storageWarning: _snapshot.storageWarning,
        failure: _failure ?? _snapshot.failure,
        isDispatching: busy,
      );

  /// 首次显示、回到前台或用户重新连接时，重订阅并读取权威快照。
  Future<void> connect() async {
    if (_disposed || _connecting) return;
    _connecting = true;
    _connected = false;
    final int connection = ++_connection;
    _emit();
    try {
      await _sessions?.cancel();
      await _amplitudes?.cancel();
      if (_disposed || connection != _connection) return;
      _sessions = _platform.sessions.listen(
        _receiveSnapshot,
        onError: (Object error) => _connectionLost(error, connection),
        onDone: () => _connectionLost(null, connection),
      );
      _amplitudes = _platform.amplitudes.listen(
        _receiveAmplitude,
        onError: (Object error) => _connectionLost(error, connection),
        onDone: () => _connectionLost(null, connection),
      );
      _connected = true;
      await _refresh();
      if (_failure?.code == 'EVENTS_DISCONNECTED' ||
          _failure?.code == 'RECORDING_CONNECTION_FAILED') {
        _failure = null;
      }
    } catch (error) {
      _connected = false;
      _failure = _describe(error);
    } finally {
      _connecting = false;
      _emit();
    }
  }

  void _connectionLost(Object? error, int connection) {
    if (_disposed || connection != _connection) return;
    _connected = false;
    _failure = const AppFailure(
      code: 'EVENTS_DISCONNECTED',
      message: '录音状态连接已断开。请点击“重新连接”确认当前录音状态。',
    );
    _emit();
  }

  Future<void> _refresh() async {
    final Map<Object?, Object?> result =
        await _platform.invoke(PlatformMethods.recordingSnapshot);
    if (_disposed) return;
    _applySnapshot(result);
    _ready = true;
  }

  /// 模型可用时直接启动本地转写，不自动降级为仅录音。
  Future<void> start() async {
    if (!modelReady) return;
    await _start(onlyRecording: false);
  }

  /// 只由用户在确认弹窗明确选择“仅录音”后调用。
  Future<void> startOnlyRecording() => _start(onlyRecording: true);

  Future<void> _start({required bool onlyRecording}) async {
    if (!canStart) return;
    await _perform(() async {
      final Map<Object?, Object?> permission =
          await _platform.invoke(PlatformMethods.requestMicrophone);
      if (_disposed) return;
      if (permission['granted'] != true) {
        _permanentlyDenied = permission['permanentlyDenied'] == true;
        throw PlatformException(
          code: PlatformErrorCodes.microphoneDenied,
          message: _permanentlyDenied
              ? '麦克风权限已被拒绝，请前往系统设置允许麦克风后重试。'
              : '未获得麦克风权限，尚未开始录音。请允许麦克风后重试。',
        );
      }
      _permanentlyDenied = false;
      final Map<Object?, Object?> result = await _platform.invoke(
        PlatformMethods.startRecording,
        <String, Object?>{'commandId': const Uuid().v4(), 'onlyRecording': onlyRecording},
      );
      if (!_disposed) _applySnapshot(result);
    });
  }

  Future<void> pause() => _command('PAUSE', canPause);
  Future<void> resume() => _command('RESUME', canResume);
  Future<void> stop() => _command('STOP', canStop);

  Future<void> _command(String command, bool allowed) async {
    if (!allowed) return;
    await _perform(() async {
      final Map<Object?, Object?> result = await _platform.invoke(
        PlatformMethods.recordingCommand,
        <String, Object?>{'commandId': const Uuid().v4(), 'command': command},
      );
      if (!_disposed) _applySnapshot(result);
    });
  }

  Future<void> openSettings() async {
    if (busy || _disposed) return;
    await _perform(() async {
      await _platform.invoke(PlatformMethods.openAppSettings);
    });
  }

  Future<void> _perform(Future<void> Function() action) async {
    _pending = true;
    _failure = null;
    _emit();
    try {
      await action();
    } catch (error) {
      if (_disposed) return;
      final AppFailure failure = _describe(error);
      // 命令失败后仍读取实际状态，尤其不能把封存失败当成空闲。
      try {
        await _refresh();
      } catch (_) {
        // 保留最后确认的会话，并展示最初的操作失败。
      }
      if (_disposed) return;
      _failure = failure;
    } finally {
      _pending = false;
      _emit();
    }
  }

  void _receiveSnapshot(Object? payload) {
    if (_disposed) return;
    try {
      if (payload is! Map) throw const FormatException('无效快照');
      _applySnapshot(payload);
      _ready = true;
      _emit();
    } catch (error) {
      _failure = _describe(error);
      _emit();
    }
  }

  void _applySnapshot(Map<Object?, Object?> map) {
    final Object? sequence = map['sequence'];
    final Object? captured = map['capturedMs'];
    final Object? id = map['sessionId'];
    final Object? status = map['status'];
    final Object? dispatch = map['dispatchState'];
    if (sequence is! int || sequence < 0 || captured is! int || captured < 0 ||
        (id != null && (id is! String || id.isEmpty)) || status is! String ||
        (dispatch != 'BLOCKED' && dispatch != 'DISPATCHING') ||
        map['onlyRecording'] is! bool ||
        (map['errorCode'] != null && map['errorCode'] is! String) ||
        (map['errorMessage'] != null && map['errorMessage'] is! String)) {
      throw const FormatException('录音快照格式不正确');
    }
    final SessionState next = SessionState.fromWireName(status);
    if (sequence <= _sequence) return;
    _onlyRecording = map['onlyRecording']! as bool;
    _pcmDispatching = dispatch == 'DISPATCHING';
    final bool newSession = id != _snapshot.sessionId;
    // 原生结束后会保留旧会话最终 capturedMs；它不属于新的空闲录音面。
    // 只在权威 IDLE 快照确认会话释放后清空当前态，失败仍保留旧会话。
    final bool idle = next == SessionState.idle;
    if (newSession) _lastMonoNs = -1;
    final String? code = map['errorCode'] as String?;
    final String? message = map['errorMessage'] as String?;
    _snapshot = RecordingUiState(
      state: next,
      sessionId: id as String?,
      elapsedMs: idle ? 0 : captured,
      amplitudes: idle || newSession ? const <AmplitudePoint>[] : _snapshot.amplitudes,
      transcriptAvailable: false,
      storageWarning: next == SessionState.lowStoragePaused
          ? '可用空间不足，录音已暂停。请释放空间后结束并保存。'
          : null,
      isDispatching: false,
      failure: code != null
          ? _describe(PlatformException(code: code, message: message))
          : next == SessionState.recording && dispatch == 'BLOCKED'
              ? const AppFailure(code: 'CAPTURE_BLOCKED', message: '录音采集已中断。请结束并保存当前录音后重试。')
              : null,
    );
    _sequence = sequence;
  }

  /// 处理原生批量振幅事件：一次 tick 可能携带多个按序样本。
  ///
  /// 批内任一样本有效即应用该批；顺序由原生保证（同队列按序排空），
  /// 批内样本按 `capturedEndMs` 升序进入状态。
  void _receiveAmplitude(Object? payload) {
    if (_disposed || waveformFrozen || _snapshot.sessionId == null) return;
    final List<Object?>? batch = switch (payload) {
      Map single => <Object?>[single],
      List batch => batch,
      _ => null,
    };
    if (batch == null || batch.isEmpty) return;
    final List<AmplitudePoint> accepted = <AmplitudePoint>[];
    for (final Object? item in batch) {
      if (item is! Map || item['sessionId'] != _snapshot.sessionId) continue;
      final Object? mono = item['monoNs'];
      final Object? captured = item['capturedEndMs'];
      final Object? rms = item['rms'];
      final Object? peak = item['peak'];
      if (mono is! int || mono <= _lastMonoNs || captured is! int || captured < 0 ||
          rms is! num || peak is! num || !rms.isFinite || !peak.isFinite ||
          (_snapshot.amplitudes.isNotEmpty &&
              captured < _snapshot.amplitudes.last.capturedEndMs)) {
        continue;
      }
      _lastMonoNs = mono;
      accepted.add(AmplitudePoint(monoNs: mono, capturedEndMs: captured,
          rms: rms.toDouble(), peak: peak.toDouble()));
    }
    if (accepted.isEmpty) return;
    final int windowMs = accepted.last.capturedEndMs - 8000;
    final List<AmplitudePoint> points = <AmplitudePoint>[
      ..._snapshot.amplitudes.where((AmplitudePoint p) => p.capturedEndMs >= windowMs),
      ...accepted,
    ];
    const int capacity = 400;
    if (points.length > capacity) points.removeRange(0, points.length - capacity);
    _snapshot = RecordingUiState(
      state: _snapshot.state,
      sessionId: _snapshot.sessionId,
      elapsedMs: _snapshot.elapsedMs,
      amplitudes: List<AmplitudePoint>.unmodifiable(points),
      transcriptAvailable: false,
      storageWarning: _snapshot.storageWarning,
      failure: _snapshot.failure,
      isDispatching: false,
    );
    _emit();
  }

  AppFailure _describe(Object error) {
    if (error is PlatformException) {
      final String message = switch (error.code) {
        'MODEL_IMPORT_FAILED' || 'IMPORT_BUSY' || 'MODEL_STATUS_INVALID' =>
          '本地模型导入失败：${error.message ?? error.code}',
        'RECORDING_RECOVERY_REQUIRED' =>
          '当前录音需要恢复，音频文件已保留。此版本尚未提供恢复入口，请保留应用数据，等待恢复功能处理后再开始新录音。',
        PlatformErrorCodes.sealFailed =>
          '保存失败，当前录音仍保留。请释放存储空间后点击“重试保存”，不要开始新录音。',
        PlatformErrorCodes.storageLow =>
          '存储空间不足。请先释放空间，再重试当前操作。',
        PlatformErrorCodes.microphoneDenied => _permanentlyDenied
            ? '麦克风权限已被拒绝，请前往系统设置允许麦克风后重试。'
            : '未获得麦克风权限，尚未开始录音。请允许麦克风后重试。',
        PlatformErrorCodes.foregroundUnavailable =>
          '录音服务暂时无法启动。请保持应用在前台后重试。',
        _ => error.message ?? '录音操作失败，请重新连接确认状态后重试。',
      };
      return AppFailure(code: error.code, message: message);
    }
    return const AppFailure(
      code: 'RECORDING_CONNECTION_FAILED',
      message: '无法确认录音状态，请点击“重新连接”后重试。',
    );
  }

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_connection;
    unawaited(_sessions?.cancel());
    unawaited(_amplitudes?.cancel());
    // 页面离开只断开监听，绝不隐式暂停或停止原生录音。
    super.dispose();
  }
}
