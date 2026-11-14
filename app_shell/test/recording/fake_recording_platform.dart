import 'dart:async';

import 'package:echofold_app/native/platform_contracts.dart';
import 'package:echofold_app/ui/recording/recording_platform.dart';
import 'package:flutter/services.dart';

final class FakeRecordingPlatform implements RecordingPlatform {
  final StreamController<Object?> sessionEvents = StreamController<Object?>.broadcast(sync: true);
  final StreamController<Object?> amplitudeEvents = StreamController<Object?>.broadcast(sync: true);
  final List<String> commands = <String>[];
  Map<Object?, Object?> snapshot = <Object?, Object?>{
    'sessionId': null, 'status': 'IDLE', 'dispatchState': 'BLOCKED',
    'capturedMs': 0, 'onlyRecording': true, 'errorCode': null,
    'errorMessage': null, 'sequence': 0,
  };
  bool granted = true;
  bool permanentlyDenied = false;
  bool sealFails = false;
  int starts = 0;
  int permissions = 0;
  int settingsOpened = 0;
  Completer<void>? commandGate;

  @override
  Stream<Object?> get sessions => sessionEvents.stream;
  @override
  Stream<Object?> get amplitudes => amplitudeEvents.stream;

  void publish(String status, {String? sessionId, int? capturedMs, String? errorCode}) {
    snapshot = <Object?, Object?>{
      ...snapshot,
      'status': status,
      'dispatchState': status == 'RECORDING' && errorCode == null ? 'DISPATCHING' : 'BLOCKED',
      'sessionId': status == 'IDLE' ? null : sessionId ?? snapshot['sessionId'] ?? 'session-1',
      'capturedMs': capturedMs ?? snapshot['capturedMs'],
      'errorCode': errorCode,
      'errorMessage': errorCode == null ? null : '原生保存失败',
      'sequence': (snapshot['sequence']! as int) + 1,
    };
    sessionEvents.add(snapshot);
  }

  void amplitude(int monoNs, int capturedMs, {String sessionId = 'session-1'}) {
    amplitudeEvents.add(<String, Object?>{
      'sessionId': sessionId, 'monoNs': monoNs, 'capturedEndMs': capturedMs,
      'rms': 0.3, 'peak': 0.6,
    });
  }

  @override
  Future<Map<Object?, Object?>> invoke(String method, [Map<String, Object?>? arguments]) async {
    switch (method) {
      case PlatformMethods.recordingSnapshot:
        return snapshot;
      case PlatformMethods.requestMicrophone:
        permissions++;
        return <Object?, Object?>{'granted': granted, 'permanentlyDenied': permanentlyDenied};
      case PlatformMethods.openAppSettings:
        settingsOpened++;
        return <Object?, Object?>{};
      case PlatformMethods.startRecording:
        starts++;
        await commandGate?.future;
        publish('RECORDING', capturedMs: 0);
        return snapshot;
      case PlatformMethods.recordingCommand:
        final String command = arguments!['command']! as String;
        commands.add(command);
        await commandGate?.future;
        if (command == 'STOP' && sealFails) {
          publish('FAILED', errorCode: PlatformErrorCodes.sealFailed);
          throw PlatformException(code: PlatformErrorCodes.sealFailed, message: '封存失败');
        }
        publish(switch (command) {'PAUSE' => 'PAUSED', 'RESUME' => 'RECORDING', _ => 'IDLE'});
        return snapshot;
      default:
        throw StateError('未预期的方法：$method');
    }
  }

  Future<void> dispose() async {
    await sessionEvents.close();
    await amplitudeEvents.close();
  }
}
