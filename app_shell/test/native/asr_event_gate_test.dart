import 'package:echofold_app/native/asr_event.dart';
import 'package:flutter_test/flutter_test.dart';

AsrPartial _partial({
  String sessionId = 'session-1',
  int generation = 0,
  int seq = 1,
}) =>
    AsrPartial(
      sessionId: sessionId,
      generation: generation,
      seq: seq,
      text: '文字',
      startMs: 0,
      endMs: 100,
      modelId: 'x-asr-480ms-int8',
      modelVersion: '1.2.0',
    );

void main() {
  group('AsrEventGate', () {
    test('同世代首次事件被接受并记录 eventId', () {
      final event = _partial();
      final result = AsrEventGate.evaluate(
        event: event,
        generation: 0,
        seenEventIds: <String>{},
      );

      expect(result.accepted, isTrue);
      expect(result.reason, isNull);
      expect(result.generation, 0);
      expect(result.seenEventIds, <String>{event.eventId});
    });

    test('重复 eventId 被丢弃', () {
      final event = _partial();
      final first = AsrEventGate.evaluate(
        event: event,
        generation: 0,
        seenEventIds: <String>{},
      );
      final second = AsrEventGate.evaluate(
        event: event,
        generation: first.generation,
        seenEventIds: first.seenEventIds,
      );

      expect(second.accepted, isFalse);
      expect(second.reason, AsrEventRejection.duplicateEventId);
      expect(second.seenEventIds, first.seenEventIds);
    });

    test('旧世代事件被拒绝且不改写状态', () {
      final stale = _partial(generation: 1, seq: 5);
      final seen = <String>{'session-1:2:partial:1'};
      final result = AsrEventGate.evaluate(
        event: stale,
        generation: 2,
        seenEventIds: seen,
      );

      expect(result.accepted, isFalse);
      expect(result.reason, AsrEventRejection.staleGeneration);
      expect(result.generation, 2);
      expect(result.seenEventIds, same(seen));
    });

    test('新世代事件推进 generation 并丢弃旧世代去重记录', () {
      final result = AsrEventGate.evaluate(
        event: _partial(generation: 3, seq: 9),
        generation: 2,
        seenEventIds: <String>{'session-1:2:partial:1'},
      );

      expect(result.accepted, isTrue);
      expect(result.generation, 3);
      expect(result.seenEventIds, <String>{'session-1:3:partial:9'});
    });

    test('去重集合按上限淘汰最旧 eventId', () {
      var generation = 0;
      var seen = <String>{};
      for (var seq = 1; seq <= 3; seq++) {
        final result = AsrEventGate.evaluate(
          event: _partial(seq: seq),
          generation: generation,
          seenEventIds: seen,
          maxTrackedEventIds: 2,
        );
        expect(result.accepted, isTrue);
        generation = result.generation;
        seen = result.seenEventIds;
      }

      expect(seen.length, 2);
      expect(seen, isNot(contains('session-1:0:partial:1')));
      expect(seen, containsAll(<String>['session-1:0:partial:2', 'session-1:0:partial:3']));
    });

    test('非法上限抛 ArgumentError', () {
      expect(
        () => AsrEventGate.evaluate(
          event: _partial(),
          generation: 0,
          seenEventIds: <String>{},
          maxTrackedEventIds: 0,
        ),
        throwsArgumentError,
      );
    });
  });
}
