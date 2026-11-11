import 'package:echofold_app/native/asr_event.dart';
import 'package:flutter_test/flutter_test.dart';

/// 与 Kotlin `AsrEventJson` 输出严格同形的载荷，用于验证字段名与类型一一对应。
Map<String, Object?> _partialJson() => <String, Object?>{
      'eventId': 'session-1:0:partial:1',
      'kind': 'partial',
      'sessionId': 'session-1',
      'generation': 0,
      'seq': 1,
      'text': '你好',
      'startMs': 0,
      'endMs': 480,
      'modelId': 'x-asr-480ms-int8',
      'modelVersion': '1.2.0',
    };

Map<String, Object?> _finalCandidateJson() => <String, Object?>{
      'eventId': 'session-1:0:finalCandidate:2',
      'kind': 'finalCandidate',
      'sessionId': 'session-1',
      'generation': 0,
      'seq': 2,
      'text': '你好世界',
      'stablePrefixChars': 2,
      'startMs': 0,
      'endMs': 1200,
    };

Map<String, Object?> _finalJson() => <String, Object?>{
      'eventId': 'session-1:2:final:7',
      'kind': 'final',
      'sessionId': 'session-1',
      'generation': 2,
      'seq': 7,
      'finalId': 'final-7',
      'text': '这是最终结果。',
      'finalizedAtUtcMs': 1700000000000,
      'captureStartedMonoNs': 1000000000,
      'captureEndedMonoNs': 2500000000,
      'capturedStartMs': 1500,
      'capturedEndMs': 3000,
      'chunkIds': <String>['chunk-0', 'chunk-1'],
    };

Map<String, Object?> _healthJson() => <String, Object?>{
      'eventId': 'session-1:2:health:8',
      'kind': 'health',
      'sessionId': 'session-1',
      'generation': 2,
      'seq': 8,
      'rtf': 0.42,
      'oldestUnprocessedAgeMs': 3000,
      'threadCount': 2,
      'memoryBytes': 104857600,
      'skipCount': 1,
      'queue': <String, Object?>{
        'capacity': 512,
        'depth': 128,
        'highWatermark': 256,
        'oldestEnqueuedMonoNs': 1000000000,
        'newestEnqueuedMonoNs': 4000000000,
        'rejectedCount': 0,
        'blocked': false,
      },
      'errorCode': null,
      'errorMessage': null,
    };

void main() {
  group('AsrEvent.fromJson', () {
    test('解析 Partial 并映射全部字段', () {
      final event = AsrEvent.fromJson(_partialJson());

      expect(event, isA<AsrPartial>());
      final partial = event as AsrPartial;
      expect(partial.kind, AsrEventKind.partial);
      expect(partial.sessionId, 'session-1');
      expect(partial.generation, 0);
      expect(partial.seq, 1);
      expect(partial.eventId, 'session-1:0:partial:1');
      expect(partial.text, '你好');
      expect(partial.startMs, 0);
      expect(partial.endMs, 480);
      expect(partial.modelId, 'x-asr-480ms-int8');
      expect(partial.modelVersion, '1.2.0');
    });

    test('解析 FinalCandidate 并校验稳定前缀长度', () {
      final event = AsrEvent.fromJson(_finalCandidateJson());

      expect(event, isA<AsrFinalCandidate>());
      final candidate = event as AsrFinalCandidate;
      expect(candidate.kind, AsrEventKind.finalCandidate);
      expect(candidate.text, '你好世界');
      expect(candidate.stablePrefixChars, 2);
      expect(candidate.startMs, 0);
      expect(candidate.endMs, 1200);
    });

    test('解析 Final 并保留有序 chunkIds', () {
      final event = AsrEvent.fromJson(_finalJson());

      expect(event, isA<AsrFinal>());
      final finalEvent = event as AsrFinal;
      expect(finalEvent.kind, AsrEventKind.finalEvent);
      expect(finalEvent.finalId, 'final-7');
      expect(finalEvent.text, '这是最终结果。');
      expect(finalEvent.finalizedAtUtcMs, 1700000000000);
      expect(finalEvent.captureStartedMonoNs, 1000000000);
      expect(finalEvent.captureEndedMonoNs, 2500000000);
      expect(finalEvent.capturedStartMs, 1500);
      expect(finalEvent.capturedEndMs, 3000);
      expect(finalEvent.chunkIds, <String>['chunk-0', 'chunk-1']);
    });

    test('解析 Health 与队列指标', () {
      final event = AsrEvent.fromJson(_healthJson());

      expect(event, isA<AsrHealth>());
      final health = event as AsrHealth;
      expect(health.rtf, 0.42);
      expect(health.oldestUnprocessedAgeMs, 3000);
      expect(health.threadCount, 2);
      expect(health.memoryBytes, 104857600);
      expect(health.skipCount, 1);
      expect(health.queue.capacity, 512);
      expect(health.queue.depth, 128);
      expect(health.queue.highWatermark, 256);
      expect(health.queue.oldestEnqueuedMonoNs, 1000000000);
      expect(health.queue.newestEnqueuedMonoNs, 4000000000);
      expect(health.queue.rejectedCount, 0);
      expect(health.queue.blocked, isFalse);
      expect(health.errorCode, isNull);
      expect(health.errorMessage, isNull);
    });
  });

  group('AsrEvent.toJson', () {
    test('四种事件均可与原载荷往返', () {
      final fixtures = <Map<String, Object?>>[
        _partialJson(),
        _finalCandidateJson(),
        _finalJson(),
        _healthJson(),
      ];

      for (final fixture in fixtures) {
        final event = AsrEvent.fromJson(fixture);
        expect(event.toJson(), equals(fixture), reason: '${fixture['kind']} 往返不一致');
      }
    });

    test('构造后派生 eventId 与原生规则一致', () {
      final event = AsrPartial(
        sessionId: 'session-9',
        generation: 3,
        seq: 4,
        text: '测试',
        startMs: 0,
        endMs: 100,
        modelId: 'x-asr',
        modelVersion: '1.0.0',
      );

      expect(event.eventId, 'session-9:3:partial:4');
      expect(event.toJson()['eventId'], 'session-9:3:partial:4');
    });
  });

  group('AsrEvent 非法载荷', () {
    test('未知 kind 抛 FormatException', () {
      final json = _partialJson()..['kind'] = 'unknownKind';
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('缺少必填字段抛 FormatException', () {
      final json = _partialJson()..remove('text');
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('eventId 与派生规则不一致抛 FormatException', () {
      final json = _partialJson()..['eventId'] = 'session-1:0:partial:99';
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('区间终点早于起点抛 FormatException', () {
      final json = _partialJson()
        ..['startMs'] = 480
        ..['endMs'] = 100;
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('Final 的 chunkIds 重复抛 FormatException', () {
      final json = _finalJson()..['chunkIds'] = <String>['chunk-0', 'chunk-0'];
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('Health 队列深度超过容量抛 FormatException', () {
      final json = _healthJson();
      (json['queue']! as Map<String, Object?>)['depth'] = 4096;
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });

    test('数值字段类型不符抛 FormatException', () {
      final json = _finalJson()..['finalizedAtUtcMs'] = 'not-a-number';
      expect(() => AsrEvent.fromJson(json), throwsFormatException);
    });
  });
}
