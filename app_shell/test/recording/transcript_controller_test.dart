import 'dart:async';

import 'package:echofold_app/native/asr_event_bridge.dart';
import 'package:echofold_app/ui/recording/transcript_controller.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, Object?> _partial(String session, int seq, String text) => <String, Object?>{
      'eventId': '$session:0:partial:$seq',
      'kind': 'partial',
      'sessionId': session,
      'generation': 0,
      'seq': seq,
      'text': text,
      'startMs': 0,
      'endMs': 480,
      'modelId': 'model',
      'modelVersion': '1',
    };

Map<String, Object?> _final(String session, int seq, String text) => <String, Object?>{
      'eventId': '$session:0:final:$seq',
      'kind': 'final',
      'sessionId': session,
      'generation': 0,
      'seq': seq,
      'finalId': 'final-$session-$seq',
      'text': text,
      'finalizedAtUtcMs': 1700000000000,
      'captureStartedMonoNs': 1000000000,
      'captureEndedMonoNs': 2000000000,
      'capturedStartMs': 0,
      'capturedEndMs': 1000,
      'chunkIds': <String>['chunk-$seq'],
    };

Map<String, Object?> _health(String session, int seq, int lag) => <String, Object?>{
      'eventId': '$session:0:health:$seq',
      'kind': 'health',
      'sessionId': session,
      'generation': 0,
      'seq': seq,
      'rtf': 0.5,
      'oldestUnprocessedAgeMs': lag,
      'threadCount': 1,
      'memoryBytes': 0,
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
  test('partial 被替换，final 固化且清除 partial，health 更新落后', () async {
    final StreamController<Object?> source = StreamController<Object?>();
    final AsrEventBridge bridge = AsrEventBridge(eventStream: source.stream);
    final TranscriptController transcript = TranscriptController(bridge: bridge)
      ..setSession('s1');

    source.add(_partial('s1', 1, '真实临时文字'));
    await Future<void>.delayed(Duration.zero);
    expect(transcript.transcript.livePartialText, '真实临时文字');

    source.add(_partial('s1', 2, '更新临时文字'));
    source.add(_final('s1', 3, '真实最终文字'));
    source.add(_health('s1', 4, 16000));
    await Future<void>.delayed(Duration.zero);
    expect(transcript.transcript.livePartialText, isNull);
    expect(transcript.transcript.segments.single.visibleText, '真实最终文字');
    expect(transcript.transcript.asrLagMs, 16000);

    transcript.dispose();
    await bridge.dispose();
    await source.close();
  });

  test('会话切换清空已有文字并忽略旧会话事件', () async {
    final StreamController<Object?> source = StreamController<Object?>();
    final AsrEventBridge bridge = AsrEventBridge(eventStream: source.stream);
    final TranscriptController transcript = TranscriptController(bridge: bridge)
      ..setSession('s1');
    source.add(_final('s1', 1, '旧会话文字'));
    await Future<void>.delayed(Duration.zero);

    transcript.setSession('s2');
    source.add(_final('s1', 2, '迟到旧文字'));
    source.add(_partial('s2', 1, '新会话临时文字'));
    await Future<void>.delayed(Duration.zero);

    expect(transcript.transcript.sessionId, 's2');
    expect(transcript.transcript.segments, isEmpty);
    expect(transcript.transcript.livePartialText, '新会话临时文字');
    expect(transcript.transcript.asrLagMs, 0);

    transcript.dispose();
    await bridge.dispose();
    await source.close();
  });

  test('无匹配会话事件时保持空 transcript，不制造文字或 GAP', () async {
    final StreamController<Object?> source = StreamController<Object?>();
    final AsrEventBridge bridge = AsrEventBridge(eventStream: source.stream);
    final TranscriptController transcript = TranscriptController(bridge: bridge)
      ..setSession('s1');
    source.add(_partial('other', 1, '不应出现'));
    await Future<void>.delayed(Duration.zero);

    expect(transcript.transcript.segments, isEmpty);
    expect(transcript.transcript.livePartialText, isNull);

    transcript.dispose();
    await bridge.dispose();
    await source.close();
  });

  test('GAP保留区间且换会话重置高世代门控', () async {
    final source = StreamController<Object?>();
    final bridge = AsrEventBridge(eventStream: source.stream);
    final controller = TranscriptController(bridge: bridge)..setSession('s1');
    source.add(<String, Object?>{
      'eventId': 's1:4:gap:1', 'kind': 'gap', 'sessionId': 's1',
      'generation': 4, 'seq': 1, 'segmentId': 'gap-1',
      'finalizedAtUtcMs': 1000, 'captureStartedMonoNs': 1,
      'captureEndedMonoNs': 2, 'capturedStartMs': 0, 'capturedEndMs': 480,
      'gapReason': 'ASR_OVERLOAD', 'chunkIds': <String>['chunk'],
      'modelId': 'model', 'modelVersion': '1', 'lagMs': 16000,
    });
    await Future<void>.delayed(Duration.zero);
    expect(controller.transcript.segments.single.visibleText, '...');
    expect(controller.transcript.segments.single.segment.capturedEndMs, 480);
    controller.setSession('s2');
    source.add(_partial('s2', 1, '新会话'));
    await Future<void>.delayed(Duration.zero);
    expect(controller.transcript.segments, isEmpty);
    expect(controller.transcript.livePartialText, '新会话');
    controller.dispose();
    await source.close();
  });

  test('健康事件先到时不丢弃迟到最终证据', () async {
    final source = StreamController<Object?>();
    final bridge = AsrEventBridge(eventStream: source.stream);
    final controller = TranscriptController(bridge: bridge)..setSession('s1');
    source.add(_health('s1', 3, 0));
    source.add(_final('s1', 2, '迟到但已提交的文字'));
    source.add(_final('s1', 2, '迟到但已提交的文字'));
    await Future<void>.delayed(Duration.zero);
    expect(controller.transcript.segments.single.visibleText, '迟到但已提交的文字');
    controller.dispose();
    await source.close();
  });
}
