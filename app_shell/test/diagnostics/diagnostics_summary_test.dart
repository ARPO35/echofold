import 'package:echofold_app/diagnostics/diagnostics_summary.dart';
import 'package:echofold_app/domain/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('诊断摘要只包含结构化指标和错误类别', () {
    final DiagnosticSnapshot snapshot = DiagnosticSnapshot(
      appVersion: '1.0.0+1',
      deviceLabel: 'test-arm64',
      generatedAtUtcMs: 1762800000000,
      sessions: const <SessionRecord>[
        SessionRecord(
          id: 's1',
          placeholderTitle: '2026-11-11',
          createdAtUtcMs: 1762800000000,
          state: SessionState.recording,
          durationMs: 12000,
          gapCount: 1,
        ),
      ],
      chunks: const <AudioChunkRecord>[
        AudioChunkRecord(
          id: 'c0',
          sessionId: 's1',
          chunkSeq: 0,
          state: ChunkState.sealed,
          createdAtUtcMs: 1762800000000,
          tempPath: '/data/c0.part',
          finalPath: '/data/c0.m4a',
          durationMs: 60000,
          bitRate: 192000,
          sampleRate: 48000,
        ),
      ],
      queues: const <DiagnosticQueue>[
        DiagnosticQueue(
          name: 'asr',
          capacity: 100,
          depth: 20,
          highWatermark: 50,
          oldestAgeMs: 1200,
          rejected: 0,
        ),
      ],
      gapRanges: const <String>['0:60-0:75'],
      lossRanges: const <String>['0:75-0:76 SEAL_FAILED'],
      errors: const <String>['BYOK_TIMEOUT host=example.com'],
      modelStatus: 'x-asr 1 (SEALED)',
      encoding: 'AAC-LC 48000Hz 192000bps',
      storageFreeBytes: 1024 * 1024 * 1024,
    );

    final String markdown = diagnosticMarkdown(snapshot);
    final String json = diagnosticJson(snapshot);
    for (final String raw in <String>[markdown, json]) {
      expect(raw, contains('asr'));
      expect(raw, contains('SEALED'));
      expect(raw, contains('0:60-0:75'));
      expect(raw, contains('BYOK_TIMEOUT'));
      expect(raw.toLowerCase(), isNot(contains('apikey')));
      expect(raw.toLowerCase(), isNot(contains('authorization')));
      expect(raw.toLowerCase(), isNot(contains('secret')));
      expect(raw, isNot(contains('完整转写')));
    }
    expect(markdown, contains('| asr | 100 | 20 | 50 | 1200 | 0 |'));
  });
}
