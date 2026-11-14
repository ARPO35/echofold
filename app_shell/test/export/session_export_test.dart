import 'dart:convert';

import 'package:echofold_app/domain/models.dart';
import 'package:echofold_app/export/session_export.dart';
import 'package:flutter_test/flutter_test.dart';

SourceVersionRef _ref(String id, String version, SourceKind kind) =>
    SourceVersionRef(sourceId: id, versionId: version, sourceKind: kind);

SessionExportSnapshot _snapshot() {
  final SourceSet sources = SourceSet(<SourceVersionRef>[
    _ref('final-1', 'v-asr-1', SourceKind.finalSource),
  ]);
  final TranscriptVersion asr = TranscriptVersion(
    id: 'v-asr-1',
    segmentId: 'seg-1',
    kind: VersionKind.asr,
    layer: SemanticLayer.l0,
    text: '原始识别文字。',
    createdAtUtcMs: 5000,
    finalizedAtUtcMs: 5000,
    captureStartMs: 0,
    captureEndMs: 4000,
    modelId: 'x-asr',
    modelVersion: '1',
    sourceVersions: sources.ordered,
    isDefault: true,
  );
  final TranscriptVersion user = TranscriptVersion(
    id: 'v-user-1',
    segmentId: 'seg-1',
    kind: VersionKind.user,
    layer: SemanticLayer.l1,
    text: '用户校订文字。',
    createdAtUtcMs: 6000,
    finalizedAtUtcMs: 5000,
    captureStartMs: 0,
    captureEndMs: 4000,
    parentVersionId: 'v-asr-1',
    sourceVersions: sources.ordered,
  );
  final SemanticArtifact l2 = SemanticArtifact(
    id: 'art-2',
    sessionId: 's1',
    segmentId: 'seg-1',
    layer: SemanticLayer.l2,
    text: '段落要点。',
    state: ArtifactState.ready,
    sourceSet: SourceSet(<SourceVersionRef>[
      _ref('seg-1', 'v-user-1', SourceKind.user),
    ]),
    promptVersion: 'p1',
    model: 'test-model',
    createdAtUtcMs: 7000,
  );
  return SessionExportSnapshot(
    session: const SessionRecord(
      id: 's1',
      placeholderTitle: '2026-11-11 10:00',
      createdAtUtcMs: 1000,
      state: SessionState.idle,
      durationMs: 65_000,
      gapCount: 1,
      modelId: 'x-asr',
      modelVersion: '1',
    ),
    title: null,
    chunks: <AudioChunkRecord>[
      const AudioChunkRecord(
        id: 'c0',
        sessionId: 's1',
        chunkSeq: 0,
        state: ChunkState.sealed,
        createdAtUtcMs: 1000,
        tempPath: '/tmp/c0.part',
        finalPath: '/tmp/c0.m4a',
        capturedStartMs: 0,
        capturedEndMs: 60_000,
        durationMs: 60_000,
        checksum: 'abc',
      ),
    ],
    finals: <FinalSource>[
      const FinalSource(
        id: 'final-1',
        sessionId: 's1',
        text: '原始识别文字。',
        finalizedAtUtcMs: 5000,
        captureStartedMonoNs: 100,
        captureEndedMonoNs: 4000,
        capturedStartMs: 0,
        capturedEndMs: 4000,
        chunkIds: <String>['c0'],
        modelId: 'x-asr',
        modelVersion: '1',
      ),
    ],
    segments: <TranscriptSegment>[
      TranscriptSegment(
        id: 'seg-1',
        sessionId: 's1',
        kind: SegmentKind.text,
        ordinal: 0,
        capturedStartMs: 0,
        capturedEndMs: 4000,
        chunkIds: const <String>['c0'],
        sourceFinalIds: const <String>['final-1'],
        foldDueAtUtcMs: 50_000,
        defaultVersionId: 'v-asr-1',
      ),
      TranscriptSegment(
        id: 'seg-2',
        sessionId: 's1',
        kind: SegmentKind.gap,
        ordinal: 1,
        capturedStartMs: 60_000,
        capturedEndMs: 65_000,
        chunkIds: const <String>['c0'],
        gapReason: GapReason.asrOverload,
      ),
    ],
    versionsBySegment: <String, List<TranscriptVersion>>{
      'seg-1': <TranscriptVersion>[asr, user],
    },
    artifactsBySegment: <String, List<SemanticArtifact>>{
      'seg-1': <SemanticArtifact>[l2],
    },
  );
}

void main() {
  final SessionExportSnapshot snapshot = _snapshot();

  test('Markdown 明确区分层级并居中显示 GAP', () {
    final String markdown = exportSessionMarkdown(snapshot);
    expect(markdown, contains('L0 原始 ASR'));
    expect(markdown, contains('[用户]'));
    expect(markdown, contains('L2 要点'));
    expect(markdown, contains('<p align="center">...</p>'));
    expect(markdown, contains('ASR 过载缺口'));
    expect(markdown, contains('跳过音频 1:00–1:05'));
    expect(markdown, isNot(contains('**L0 原始 ASR**：...')));
  });

  test('JSON 保留来源身份、sourceSetHash 与跳过区间', () {
    final Map<String, Object?> json =
        jsonDecode(exportSessionJson(snapshot)) as Map<String, Object?>;
    expect(json['schema'], 'echofold.export.v1');
    final List<Object?> segments = json['segments']! as List<Object?>;
    final Map<String, Object?> gap = segments[1]! as Map<String, Object?>;
    expect(gap['kind'], 'GAP');
    expect(gap['gapReason'], 'ASR_OVERLOAD');
    expect(gap['capturedStartMs'], 60_000);
    expect(gap['capturedEndMs'], 65_000);

    final Map<String, Object?> version =
        ((segments[0]! as Map<String, Object?>)['versions']! as List<Object?>)
            .first! as Map<String, Object?>;
    final List<Object?> sources = version['sourceVersions']! as List<Object?>;
    expect(version['sourceSetHash'], isNotEmpty);
    expect(sources.single, isA<Map<String, Object?>>());
    expect(sources.single! as Map<String, Object?>, containsPair('sourceId', 'final-1'));
  });

  test('导出不包含密钥字段', () {
    final String raw = '${exportSessionJson(snapshot)}\n${exportSessionMarkdown(snapshot)}';
    expect(raw.toLowerCase(), isNot(contains('apikey')));
    expect(raw.toLowerCase(), isNot(contains('authorization')));
    expect(raw.toLowerCase(), isNot(contains('secret')));
  });
}
