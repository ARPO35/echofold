import 'package:echofold_app/domain/models.dart';
import 'package:echofold_app/playback/playback_planner.dart';
import 'package:flutter_test/flutter_test.dart';

AudioChunkRecord _chunk({
  required int seq,
  required int start,
  required int end,
  ChunkState state = ChunkState.sealed,
}) =>
    AudioChunkRecord(
      id: 'chunk-$seq',
      sessionId: 's1',
      chunkSeq: seq,
      state: state,
      createdAtUtcMs: 1000 + seq,
      tempPath: '/tmp/$seq.m4a.part',
      finalPath: state == ChunkState.sealed || state == ChunkState.recoverable
          ? '/tmp/$seq.m4a'
          : null,
      capturedStartMs: start,
      capturedEndMs: end,
    );

void main() {
  group('PlaybackPlanner', () {
    test('跨块区间按实际 offset 裁剪，不插入暂停静音', () {
      final PlaybackPlan plan = PlaybackPlanner.plan(
        chunks: <AudioChunkRecord>[
          _chunk(seq: 0, start: 0, end: 60_000),
          _chunk(seq: 1, start: 60_000, end: 120_000),
        ],
        startMs: 50_000,
        endMs: 70_000,
      );

      expect(plan.complete, isTrue);
      expect(plan.clips, hasLength(2));
      expect(plan.clips[0].startMs, 50_000);
      expect(plan.clips[0].endMs, 60_000);
      expect(plan.clips[1].startMs, 0);
      expect(plan.clips[1].endMs, 10_000);
      expect(plan.totalDurationMs, 20_000);
    });

    test('损坏块形成明确缺口而不是静默缩短', () {
      final PlaybackPlan plan = PlaybackPlanner.plan(
        chunks: <AudioChunkRecord>[
          _chunk(seq: 0, start: 0, end: 10_000),
          _chunk(seq: 1, start: 10_000, end: 20_000, state: ChunkState.corrupt),
        ],
        startMs: 0,
        endMs: 20_000,
      );

      expect(plan.complete, isFalse);
      expect(plan.clips, hasLength(1));
      expect(plan.unavailable.single.startMs, 10_000);
      expect(plan.unavailable.single.reason, contains('CORRUPT'));
    });

    test('末尾无索引时标记缺口，不伪造音频', () {
      final PlaybackPlan plan = PlaybackPlanner.plan(
        chunks: <AudioChunkRecord>[_chunk(seq: 0, start: 0, end: 10_000)],
        startMs: 0,
        endMs: 15_000,
      );

      expect(plan.complete, isFalse);
      expect(plan.unavailable.single.startMs, 10_000);
      expect(plan.unavailable.single.endMs, 15_000);
    });

    test('无音频返回空计划', () {
      final PlaybackPlan plan = PlaybackPlanner.plan(
        chunks: const <AudioChunkRecord>[],
        startMs: 0,
        endMs: 1000,
      );
      expect(plan.clips, isEmpty);
      expect(plan.complete, isFalse);
    });
  });
}
