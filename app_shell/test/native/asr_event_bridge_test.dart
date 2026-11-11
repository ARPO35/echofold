import 'dart:async';

import 'package:echofold_app/native/asr_event.dart';
import 'package:echofold_app/native/asr_event_bridge.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, Object?> _partial({int generation = 0, int seq = 1}) =>
    <String, Object?>{
      'eventId': 'session-1:$generation:partial:$seq',
      'kind': 'partial',
      'sessionId': 'session-1',
      'generation': generation,
      'seq': seq,
      'text': '临时文字',
      'startMs': 0,
      'endMs': 480,
      'modelId': 'x-asr-480ms-int8',
      'modelVersion': '1.2.0',
    };

Map<String, Object?> _final({int generation = 0, int seq = 2}) =>
    <String, Object?>{
      'eventId': 'session-1:$generation:final:$seq',
      'kind': 'final',
      'sessionId': 'session-1',
      'generation': generation,
      'seq': seq,
      'finalId': 'final-$seq',
      'text': '最终文字。',
      'finalizedAtUtcMs': 1700000000000,
      'captureStartedMonoNs': 1000000000,
      'captureEndedMonoNs': 2000000000,
      'capturedStartMs': 0,
      'capturedEndMs': 1000,
      'chunkIds': <String>['chunk-0'],
    };

Map<String, Object?> _health({int generation = 0, int seq = 3}) =>
    <String, Object?>{
      'eventId': 'session-1:$generation:health:$seq',
      'kind': 'health',
      'sessionId': 'session-1',
      'generation': generation,
      'seq': seq,
      'rtf': 0.5,
      'oldestUnprocessedAgeMs': 0,
      'threadCount': 1,
      'memoryBytes': 1024,
      'skipCount': 0,
      'queue': <String, Object?>{
        'capacity': 16,
        'depth': 0,
        'highWatermark': 0,
        'oldestEnqueuedMonoNs': null,
        'newestEnqueuedMonoNs': 0,
        'rejectedCount': 0,
        'blocked': false,
      },
      'errorCode': null,
      'errorMessage': null,
    };

void main() {
  test('通道名称与原生侧一致', () {
    expect(AsrEventBridge.channelName, 'com.echofold.app/asr_events');
    expect(asrEventChannelName, AsrEventBridge.channelName);
  });

  test('过滤重复、旧世代与无法解析的载荷', () async {
    final payloads = <Object?>[
      _partial(),
      _partial(), // 重复 eventId
      _final(generation: 1, seq: 2),
      _partial(generation: 0, seq: 4), // 旧世代，迟到结果
      <String, Object?>{'kind': 'unknown'}, // 无法解析
      'not-a-map',
      _health(generation: 1, seq: 5),
    ];
    final bridge = AsrEventBridge(eventStream: Stream<Object?>.fromIterable(payloads));
    addTearDown(bridge.dispose);

    final events = <AsrEvent>[];
    final malformed = <Object?>[];
    final subscription = bridge.events.listen(events.add);
    final malformedSubscription = bridge.malformedEvents.listen(malformed.add);
    addTearDown(subscription.cancel);
    addTearDown(malformedSubscription.cancel);

    await pumpEventQueue();

    expect(events.map((event) => event.kind), <AsrEventKind>[
      AsrEventKind.partial,
      AsrEventKind.finalEvent,
      AsrEventKind.health,
    ]);
    expect(events.map((event) => event.generation), <int>[0, 1, 1]);
    expect(bridge.generation, 1);
    expect(malformed.length, 2);
  });

  test('无订阅时不占用底层通道，dispose 后关闭流', () async {
    var listened = 0;
    final source = Stream<Object?>.fromIterable(<Object?>[_partial()]).transform(
      StreamTransformer<Object?, Object?>.fromHandlers(
        handleData: (data, sink) {
          listened += 1;
          sink.add(data);
        },
      ),
    );
    final bridge = AsrEventBridge(eventStream: source);

    expect(listened, 0, reason: '没有订阅者时不应触碰通道');

    final events = <AsrEvent>[];
    final subscription = bridge.events.listen(events.add);
    await pumpEventQueue();
    await subscription.cancel();
    await bridge.dispose();

    expect(listened, 1);
    expect(events, hasLength(1));
    expect(bridge.events.isBroadcast, isTrue);
  });
}
