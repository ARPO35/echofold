/// 录音界面唯一的平台边界；测试替换此边界，生产复用现有原生采集链路。
library;

import 'package:flutter/services.dart';

import '../../native/platform_contracts.dart';

abstract interface class RecordingPlatform {
  Stream<Object?> get sessions;
  Stream<Object?> get amplitudes;
  Future<Map<Object?, Object?>> invoke(String method, [Map<String, Object?>? arguments]);
}

final class ChannelRecordingPlatform implements RecordingPlatform {
  const ChannelRecordingPlatform();

  @override
  Stream<Object?> get sessions =>
      const EventChannel(sessionEventsChannel).receiveBroadcastStream();

  @override
  Stream<Object?> get amplitudes =>
      const EventChannel(amplitudeEventsChannel).receiveBroadcastStream();

  @override
  Future<Map<Object?, Object?>> invoke(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    final Object? result = await const MethodChannel(platformMethodsChannel)
        .invokeMethod<Object?>(method, arguments);
    if (result is! Map) {
      throw PlatformException(
        code: 'INVALID_RESPONSE',
        message: '录音服务返回了无效结果，请重新连接后重试。',
      );
    }
    return Map<Object?, Object?>.from(result);
  }
}
