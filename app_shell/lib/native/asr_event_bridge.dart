import 'dart:async';

import 'package:flutter/services.dart';

import 'asr_event.dart';

/// 原生 ASR 事件通道名称；Kotlin 侧必须使用同名 EventChannel。
const String asrEventChannelName = 'com.echofold.app/asr_events';

/// ASR 事件桥：EventChannel 的薄封装。
///
/// 只做三件事：把载荷 Map 解析成 [AsrEvent]、用 [AsrEventGate] 做过期世代与重复事件门控、
/// 按订阅懒启动底层通道。解析失败的载荷进入 [malformedEvents] 供诊断，不进入 [events]。
/// 通过 [eventStream] 注入任意 `Stream<Object?>` 即可在无设备环境下测试整条链路。
final class AsrEventBridge {
  AsrEventBridge({
    EventChannel? channel,
    Stream<Object?>? eventStream,
    int maxTrackedEventIds = AsrEventGate.defaultMaxTrackedEventIds,
  })  : _source = eventStream ??
            (channel ?? const EventChannel(asrEventChannelName))
                .receiveBroadcastStream(),
        _maxTrackedEventIds = maxTrackedEventIds {
    if (maxTrackedEventIds <= 0) {
      throw ArgumentError.value(maxTrackedEventIds, 'maxTrackedEventIds', '必须为正数');
    }
  }

  /// 事件通道名称。
  static const String channelName = asrEventChannelName;

  final Stream<Object?> _source;
  final int _maxTrackedEventIds;

  late final StreamController<AsrEvent> _events =
      StreamController<AsrEvent>.broadcast(
    onListen: _subscribe,
    onCancel: _cancelIfIdle,
  );
  late final StreamController<Object?> _malformedEvents =
      StreamController<Object?>.broadcast(
    onListen: _subscribe,
    onCancel: _cancelIfIdle,
  );

  StreamSubscription<Object?>? _subscription;
  int _generation = 0;
  Set<String> _seenEventIds = <String>{};
  String? _sessionId;
  bool _sessionSelected = false;

  /// Select the recording session before accepting its first event.
  void setSession(String? sessionId) {
    _sessionSelected = true;
    if (_sessionId == sessionId) return;
    _sessionId = sessionId;
    _generation = 0;
    _seenEventIds = <String>{};
  }
  bool _disposed = false;

  /// 已解析且通过门控的事件流；重复 `eventId` 与旧世代事件被静默丢弃。
  Stream<AsrEvent> get events => _events.stream;

  /// 无法解析的原始载荷流，用于诊断协议漂移。
  Stream<Object?> get malformedEvents => _malformedEvents.stream;

  /// 当前世代，由已接受事件推进；跳读后旧世代事件不会再被接受。
  int get generation => _generation;

  /// 订阅底层通道。`EventChannel.receiveBroadcastStream()` 本身也是广播流，重复订阅无副作用。
  void _subscribe() {
    if (_disposed || _subscription != null) {
      return;
    }
    _subscription = _source.listen(
      _handlePayload,
      onError: _events.addError,
    );
  }

  void _cancelIfIdle() {
    if (_events.hasListener || _malformedEvents.hasListener) {
      return;
    }
    final subscription = _subscription;
    _subscription = null;
    if (subscription != null) {
      unawaited(subscription.cancel());
    }
  }

  void _handlePayload(Object? payload) {
    final AsrEvent event;
    try {
      event = AsrEvent.fromJson(_asStringKeyedMap(payload));
    } on FormatException {
      _malformedEvents.add(payload);
      return;
    }
    if (_sessionSelected && event.sessionId != _sessionId) return;
    if (!_sessionSelected && event.sessionId != _sessionId) {
      _sessionId = event.sessionId;
      _generation = 0;
      _seenEventIds = <String>{};
    }
    final result = AsrEventGate.evaluate(
      event: event,
      generation: _generation,
      seenEventIds: _seenEventIds,
      maxTrackedEventIds: _maxTrackedEventIds,
    );
    if (!result.accepted) {
      return;
    }
    _generation = result.generation;
    _seenEventIds = result.seenEventIds;
    _events.add(event);
  }

  /// 释放底层通道订阅与流控制器；重复调用安全。
  Future<void> dispose() async {
    if (_disposed) {
      return;
    }
    _disposed = true;
    final subscription = _subscription;
    _subscription = null;
    if (subscription != null) {
      await subscription.cancel();
    }
    await _events.close();
    await _malformedEvents.close();
  }
}

/// EventChannel 的载荷是 `Map<Object?, Object?>`，统一转成字符串键的 Map。
Map<String, Object?> _asStringKeyedMap(Object? payload) {
  if (payload is Map) {
    return payload.map((key, value) => MapEntry(key.toString(), value));
  }
  throw FormatException('ASR 事件载荷必须是 Map，实际为 ${payload.runtimeType}');
}
