import 'dart:async';

import 'package:echofold_app/app/app_contract.dart';
import 'package:echofold_app/domain/models.dart';
import 'package:echofold_app/native/platform_contracts.dart';
import 'package:echofold_app/ui/recording/recording_controller.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';

import 'fake_recording_platform.dart';

void main() {
  late FakeRecordingPlatform platform;
  late RecordingController controller;

  setUp(() async {
    platform = FakeRecordingPlatform();
    controller = RecordingController(platform: platform);
    await controller.connect();
  });
  tearDown(() async {
    controller.dispose();
    await platform.dispose();
  });

  test('模型就绪后启动真实转写，快照确认模式且保留暂停保存行为', () async {
    platform.modelAvailable = true;
    await controller.refreshModel();
    await controller.start();
    expect(controller.state.state, SessionState.recording);
    expect(controller.onlyRecording, isFalse);
    expect(platform.snapshot['onlyRecording'], isFalse);
    platform.amplitude(1, 1200);
    platform.publish('RECORDING', capturedMs: 1200);
    await controller.pause();
    expect(controller.state.state, SessionState.paused);
    expect(controller.state.amplitudes, hasLength(1));
    await controller.resume();
    expect(controller.state.state, SessionState.recording);
    await controller.stop();
    expect(controller.state.state, SessionState.idle);
    expect(controller.state.elapsedMs, 0);
    expect(controller.state.amplitudes, isEmpty);
  });

  test('没有可用模型时不隐式降级为仅录音', () async {
    await controller.refreshModel();
    await controller.start();
    expect(platform.permissions, 0);
    expect(controller.state.state, SessionState.idle);
    await controller.startOnlyRecording();
    expect(controller.state.state, SessionState.recording);
    expect(controller.onlyRecording, isTrue);
    expect(platform.snapshot['onlyRecording'], isTrue);
  });
  test('模型状态读取失败可见，重试后恢复可用状态', () async {
    platform.modelStatusError = PlatformException(code: 'STATUS_FAILED', message: '无法读取模型目录');
    await controller.refreshModel();
    expect(controller.modelReady, isFalse);
    expect(controller.modelMessage, contains('无法读取模型目录'));
    platform.modelStatusError = null;
    platform.modelAvailable = true;
    await controller.refreshModel();
    expect(controller.modelReady, isTrue);
    expect(controller.modelMessage, '本地转写模型已就绪');
  });

  test('导入等待期间禁止重复导入与开始，成功后恢复操作', () async {
    final Completer<void> gate = Completer<void>();
    platform.modelImportGate = gate;
    final Future<void> importing = controller.importModel();
    expect(controller.busy, isTrue);
    expect(controller.state.isDispatching, isTrue);
    expect(controller.canStart, isFalse);
    await controller.importModel();
    await controller.startOnlyRecording();
    expect(platform.modelImports, 1);
    expect(platform.starts, 0);
    gate.complete();
    await importing;
    expect(controller.busy, isFalse);
    expect(controller.modelReady, isTrue);
    expect(controller.state.failure, isNull);
    await controller.start();
    expect(controller.state.state, SessionState.recording);
    expect(controller.onlyRecording, isFalse);
  });

  test('取消导入保留已知模型且不显示失败，导入失败可见并允许重试', () async {
    platform.modelAvailable = true;
    await controller.refreshModel();
    final String previous = controller.modelMessage;
    platform.modelImportError = PlatformException(code: 'IMPORT_CANCELLED');
    await controller.importModel();
    expect(controller.busy, isFalse);
    expect(controller.modelReady, isTrue);
    expect(controller.modelMessage, previous);
    expect(controller.state.failure, isNull);
    platform.modelImportError = PlatformException(code: 'MODEL_IMPORT_FAILED', message: '模型校验失败');
    await controller.importModel();
    expect(controller.state.failure!.message, contains('模型校验失败'));
    expect(controller.modelReady, isTrue);
    expect(controller.busy, isFalse);
    platform.modelImportError = null;
    await controller.importModel();
    expect(controller.state.failure, isNull);
    expect(controller.modelReady, isTrue);
  });

  test('模型状态读取期间不能开始或导入，重复刷新不覆盖状态', () async {
    final Completer<void> gate = Completer<void>();
    platform.modelStatusGate = gate;
    final Future<void> refreshing = controller.refreshModel();
    expect(controller.canStart, isFalse);
    expect(controller.canImportModel, isFalse);
    await controller.refreshModel();
    await controller.importModel();
    expect(platform.modelStatusReads, 1);
    expect(platform.modelImports, 0);
    gate.complete();
    await refreshing;
    expect(controller.canStart, isTrue);
    expect(controller.canImportModel, isTrue);
  });

  test('销毁后模型刷新和导入的异步结果不再通知或读取会话', () async {
    for (final bool importing in <bool>[false, true]) {
      final FakeRecordingPlatform boundary = FakeRecordingPlatform();
      final RecordingController disposed = RecordingController(platform: boundary);
      final Completer<void> gate = Completer<void>();
      if (importing) {
        boundary.modelImportGate = gate;
        boundary.modelImportError = PlatformException(code: 'MODEL_IMPORT_FAILED');
      } else {
        boundary.modelStatusGate = gate;
        boundary.modelStatusError = PlatformException(code: 'STATUS_FAILED');
      }
      int notifications = 0;
      disposed.addListener(() => notifications++);
      final Future<void> pending = importing ? disposed.importModel() : disposed.refreshModel();
      disposed.dispose();
      final int before = notifications;
      gate.complete();
      await pending;
      expect(notifications, before);
      await boundary.dispose();
    }
  });


  test('权限拒绝不开始录音，永久拒绝可前往设置', () async {
    platform.granted = false;
    platform.permanentlyDenied = true;
    await controller.startOnlyRecording();
    expect(platform.starts, 0);
    expect(controller.state.state, SessionState.idle);
    expect(controller.state.elapsedMs, 0);
    expect(controller.state.failure!.message, contains('系统设置'));
    expect(controller.permanentlyDenied, isTrue);
    await controller.openSettings();
    expect(platform.settingsOpened, 1);
  });

  test('命令等待期间不假录音、不重复提交，按原生结果开始暂停继续', () async {
    final Completer<void> gate = Completer<void>();
    platform.commandGate = gate;
    final Future<void> start = controller.startOnlyRecording();
    await pumpEventQueue();
    expect(controller.state.state, SessionState.idle);
    expect(controller.state.isDispatching, isTrue);
    await controller.startOnlyRecording();
    expect(platform.starts, 1);
    gate.complete();
    await start;
    platform.commandGate = null;
    expect(controller.state.state, SessionState.recording);
    platform.publish('RECORDING', capturedMs: 1250);
    await controller.pause();
    expect(controller.state.state, SessionState.paused);
    expect(controller.state.elapsedMs, 1250);
    await controller.resume();
    expect(controller.state.state, SessionState.recording);
    expect(platform.commands, <String>['PAUSE', 'RESUME']);
  });

  test('封存失败保留旧会话，仅重试成功后允许再次开始', () async {
    await controller.startOnlyRecording();
    platform.amplitude(10, 100);
    platform.publish('RECORDING', capturedMs: 100);
    platform.sealFails = true;
    await controller.stop();
    expect(controller.state.state, SessionState.failed);
    expect(controller.state.sessionId, 'session-1');
    expect(controller.state.elapsedMs, 100);
    expect(controller.state.amplitudes, hasLength(1));
    expect(controller.state.failure!.code, PlatformErrorCodes.sealFailed);
    expect(controller.state.failure!.message, contains('重试保存'));
    expect(controller.canStop, isTrue);
    await controller.startOnlyRecording();
    expect(platform.starts, 1);
    platform.sealFails = false;
    await controller.stop();
    expect(controller.state.state, SessionState.idle);
    expect(controller.state.sessionId, isNull);
    expect(controller.state.elapsedMs, 0);
    expect(platform.snapshot['capturedMs'], 100,
        reason: '原生最终累计时长属于已保存会话，不改写历史证据');
    expect(controller.state.amplitudes, isEmpty);
    expect(platform.starts, 1, reason: '保存成功也不自动开始下一段');
    expect(controller.canStart, isTrue);
    await controller.connect();
    expect(controller.state.elapsedMs, 0, reason: '重连读取同一封存快照仍为空闲');
    await controller.startOnlyRecording();
    expect(platform.starts, 2);
    expect(controller.state.state, SessionState.recording);
    expect(controller.state.elapsedMs, 0);
    platform.publish('RECORDING', sessionId: 'session-2', capturedMs: 1260);
    expect(controller.state.elapsedMs, 1260);
  });

  test('需要恢复的录音保留会话，禁止重复封存和新建', () async {
    await controller.startOnlyRecording();
    platform.publish('FAILED', capturedMs: 1500,
        errorCode: 'RECORDING_RECOVERY_REQUIRED');
    expect(controller.recoveryRequired, isTrue);
    expect(controller.state.sessionId, 'session-1');
    expect(controller.state.elapsedMs, 1500);
    expect(controller.state.failure!.message, contains('此版本尚未提供恢复入口'));
    expect(controller.canStop, isFalse);
    expect(controller.canStart, isFalse);
    await controller.stop();
    await controller.startOnlyRecording();
    expect(platform.commands, isEmpty);
    expect(platform.starts, 1);
  });

  test('PCM正在分发不锁定命令，BLOCKED仅冻结波形', () async {
    await controller.startOnlyRecording();
    expect(platform.snapshot['dispatchState'], 'DISPATCHING');
    expect(controller.state.isDispatching, isFalse);
    expect(controller.canPause, isTrue);
    expect(controller.canStop, isTrue);
    platform.snapshot = <Object?, Object?>{
      ...platform.snapshot,
      'dispatchState': 'BLOCKED',
      'sequence': 10,
    };
    platform.sessionEvents.add(platform.snapshot);
    platform.amplitude(10, 100);
    expect(controller.state.amplitudes, isEmpty);
    expect(controller.waveformFrozen, isTrue);
    expect(controller.state.failure!.message, contains('采集已中断'));
    expect(controller.canStop, isTrue);
  });

  test('真实振幅单调去乱序、暂停冻结、时长只读取快照', () async {
    await controller.startOnlyRecording();
    platform.amplitude(10, 100);
    platform.amplitude(10, 200);
    platform.amplitude(9, 250);
    platform.amplitude(11, 300, sessionId: 'old-session');
    expect(controller.state.amplitudes, hasLength(1));
    expect(controller.state.elapsedMs, 0);
    platform.publish('PAUSED', capturedMs: 100);
    platform.amplitude(12, 400);
    expect(controller.state.amplitudes, hasLength(1));
    expect(controller.state.elapsedMs, 100);
    platform.publish('RECORDING', sessionId: 'session-2', capturedMs: 0);
    expect(controller.state.amplitudes, isEmpty);
    platform.amplitude(1, 100, sessionId: 'session-2');
    expect(controller.state.amplitudes, hasLength(1));
  });

  test('一次事件可携带批量样本，按序接受并整体去乱序', () async {
    await controller.startOnlyRecording();
    platform.pushAmplitudes(<Map<String, Object?>>[
      for (int i = 1; i <= 5; i++)
        <String, Object?>{'sessionId': 'session-1', 'monoNs': i, 'capturedEndMs': i * 100, 'rms': 0.3, 'peak': 0.6},
    ]);
    expect(controller.state.amplitudes, hasLength(5));
    expect(controller.state.amplitudes.map((AmplitudePoint p) => p.capturedEndMs), [100, 200, 300, 400, 500]);
    // 批内含乱序/外来样本：monoNs 乱序(99<=100)被拒、外来会话被拒、
    // capturedEndMs 450<500 乱序也被拒——但 monoNs 200>100 有效，会被接受。
    // 注意有效性以 monoNs 为主判据：captured 450 搭配 mono 200 是合法样本（原生保证同队列有序，
    // captured 也随 mono 单调），此处模拟矛盾输入验证 captured 乱序防线。
    platform.pushAmplitudes(<Map<String, Object?>>[
      <String, Object?>{'sessionId': 'session-1', 'monoNs': 99, 'capturedEndMs': 450, 'rms': 0.1, 'peak': 0.2},
      <String, Object?>{'sessionId': 'old', 'monoNs': 100, 'capturedEndMs': 600, 'rms': 0.1, 'peak': 0.2},
      <String, Object?>{'sessionId': 'session-1', 'monoNs': 200, 'capturedEndMs': 500, 'rms': 0.1, 'peak': 0.2},
    ]);
    // mono 200 有效被接受（captured 500 恰与既有 500 并列，未乱序拒收）。
    expect(controller.state.amplitudes.map((AmplitudePoint p) => p.capturedEndMs), [100, 200, 300, 400, 500, 500]);
  });

  test('capturedEndMs 乱序样本被拒收，窗口保持升序', () async {
    await controller.startOnlyRecording();
    platform.amplitude(1, 500);
    platform.amplitude(2, 600);
    platform.amplitude(3, 550);
    expect(controller.state.amplitudes.map((AmplitudePoint p) => p.capturedEndMs), [500, 600]);
  });

  test('波形固定容量且只保留有效音频最近八秒', () async {
    await controller.startOnlyRecording();
    for (int i = 1; i <= 500; i++) {
      platform.amplitude(i, i);
    }
    expect(controller.state.amplitudes, hasLength(400));
    platform.amplitude(501, 10000);
    expect(controller.state.amplitudes, hasLength(1));
  });

  test('旧快照不能覆盖新状态，重新连接后读取原生现状', () async {
    await controller.startOnlyRecording();
    final Map<Object?, Object?> old = platform.snapshot;
    platform.publish('PAUSED', capturedMs: 2000);
    platform.sessionEvents.add(old);
    expect(controller.state.state, SessionState.paused);
    expect(controller.state.elapsedMs, 2000);
    platform.sessionEvents.addError(StateError('连接中断'));
    expect(controller.connected, isFalse);
    expect(controller.canResume, isFalse);
    platform.snapshot = <Object?, Object?>{
      ...platform.snapshot, 'status': 'RECORDING', 'capturedMs': 3000,
      'sequence': 20,
    };
    await controller.connect();
    expect(controller.connected, isTrue);
    expect(controller.state.state, SessionState.recording);
    expect(controller.state.elapsedMs, 3000);
  });

  test('销毁控制器只断监听，不发送停止命令', () async {
    final RecordingController other = RecordingController(platform: platform);
    await other.connect();
    await other.startOnlyRecording();
    other.dispose();
    expect(platform.commands, isEmpty);
    expect(platform.snapshot['status'], 'RECORDING');
  });
}
