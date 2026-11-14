/// 跨音频块回听计划器。
///
/// 文本/GAP 的 `capturedStartMs/capturedEndMs` 是会话有效音频 offset（不含暂停间隙），
/// 这里只按块的实际 offset 交集裁剪，绝不把墙钟差或暂停时长换算成音频位置。
library;

import '../domain/models.dart';

/// 一个可播放片段；`startMs/endMs` 是块内偏移。
final class PlaybackClip {
  const PlaybackClip({
    required this.chunkId,
    required this.path,
    required this.startMs,
    required this.endMs,
  });

  final String chunkId;
  final String path;
  final int startMs;
  final int endMs;

  int get durationMs => endMs - startMs;
}

/// 无法播放的区间；用于明确告诉用户证据不完整。
final class PlaybackGap {
  const PlaybackGap({
    required this.startMs,
    required this.endMs,
    required this.reason,
  });

  final int startMs;
  final int endMs;
  final String reason;
}

/// 播放计划。
final class PlaybackPlan {
  const PlaybackPlan({
    required this.clips,
    required this.unavailable,
    required this.complete,
  });

  final List<PlaybackClip> clips;
  final List<PlaybackGap> unavailable;

  /// 请求区间是否由可验证音频完整覆盖。
  final bool complete;

  int get totalDurationMs =>
      clips.fold(0, (int sum, PlaybackClip clip) => sum + clip.durationMs);
}

/// 从块索引生成播放计划。
final class PlaybackPlanner {
  const PlaybackPlanner._();

  static PlaybackPlan plan({
    required List<AudioChunkRecord> chunks,
    required int startMs,
    required int endMs,
  }) {
    if (startMs < 0 || endMs < startMs) {
      throw ArgumentError('播放区间非法：$startMs..$endMs');
    }
    final List<AudioChunkRecord> sorted = List<AudioChunkRecord>.of(chunks)
      ..sort((AudioChunkRecord a, AudioChunkRecord b) => a.chunkSeq.compareTo(b.chunkSeq));

    final List<PlaybackClip> clips = <PlaybackClip>[];
    final List<PlaybackGap> gaps = <PlaybackGap>[];
    int cursor = startMs;

    for (final AudioChunkRecord chunk in sorted) {
      final int? chunkStart = chunk.capturedStartMs;
      final int? chunkEnd = chunk.capturedEndMs;
      if (chunkStart == null || chunkEnd == null || chunkEnd <= chunkStart) {
        continue;
      }
      final int overlapStart = startMs > chunkStart ? startMs : chunkStart;
      final int overlapEnd = endMs < chunkEnd ? endMs : chunkEnd;
      if (overlapEnd <= overlapStart) {
        continue;
      }
      if (overlapStart > cursor) {
        gaps.add(
          PlaybackGap(
            startMs: cursor,
            endMs: overlapStart,
            reason: '该区间没有可验证的音频块',
          ),
        );
      }
      final String path = chunk.finalPath ?? chunk.tempPath;
      if (chunk.state == ChunkState.sealed || chunk.state == ChunkState.recoverable) {
        clips.add(
          PlaybackClip(
            chunkId: chunk.id,
            path: path,
            startMs: overlapStart - chunkStart,
            endMs: overlapEnd - chunkStart,
          ),
        );
        cursor = overlapEnd;
      } else {
        gaps.add(
          PlaybackGap(
            startMs: overlapStart,
            endMs: overlapEnd,
            reason: '音频块状态为 ${chunk.state.wireName}',
          ),
        );
        cursor = overlapEnd;
      }
      if (cursor >= endMs) {
        break;
      }
    }
    if (cursor < endMs) {
      gaps.add(
        PlaybackGap(
          startMs: cursor,
          endMs: endMs,
          reason: '请求区间末尾没有音频块索引',
        ),
      );
    }
    return PlaybackPlan(
      clips: List<PlaybackClip>.unmodifiable(clips),
      unavailable: List<PlaybackGap>.unmodifiable(gaps),
      complete: gaps.isEmpty && clips.isNotEmpty,
    );
  }
}
