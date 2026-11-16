import 'dart:convert';

import 'package:echofold_app/domain/models.dart';
import 'package:echofold_app/export/session_export.dart';
import 'package:flutter_test/flutter_test.dart';

SourceVersionRef _ref(String id, String version, SourceKind kind) =>
    SourceVersionRef(sourceId: id, versionId: version, sourceKind: kind);

SessionExportSnapshot _snapshot({
  String placeholderTitle = '2026-11-11 10:00',
  String asrText = '原始识别文字。',
  String userText = '用户校订文字。',
  String artifactText = '段落要点。',
}) {
  final SourceSet sources = SourceSet(<SourceVersionRef>[
    _ref('final-1', 'v-asr-1', SourceKind.finalSource),
  ]);
  final TranscriptVersion asr = TranscriptVersion(
    id: 'v-asr-1',
    segmentId: 'seg-1',
    kind: VersionKind.asr,
    layer: SemanticLayer.l0,
    text: asrText,
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
    text: userText,
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
    text: artifactText,
    state: ArtifactState.ready,
    sourceSet: SourceSet(<SourceVersionRef>[
      _ref('seg-1', 'v-user-1', SourceKind.user),
    ]),
    promptVersion: 'p1',
    model: 'test-model',
    createdAtUtcMs: 7000,
  );
  return SessionExportSnapshot(
    session: SessionRecord(
      id: 's1',
      placeholderTitle: placeholderTitle,
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

  test('Markdown 将标题与转写内容作为原文导出而不是文档语法', () {
    final SessionExportSnapshot literal = _snapshot(
      placeholderTitle: '# 标题\r\n<script>标题</script>',
      asrText: '**原文** & <audio>\n# 第二行',
      userText: '[校订](https://example.com) `代码` \\路径',
      artifactText: '- 要点\r下一行 | ~~删除~~',
    );

    final String markdown = exportSessionMarkdown(literal);

    expect(
      markdown,
      startsWith('# \\# 标题<br>&lt;script&gt;标题&lt;/script&gt;\n'),
    );
    expect(
      markdown,
      contains('**L0 原始 ASR**：\\*\\*原文\\*\\* &amp; &lt;audio&gt;<br>\\# 第二行'),
    );
    expect(
      markdown,
      contains(r'\[校订\]\(https://example\.com\) \`代码\` \\路径'),
    );
    expect(markdown, contains(r'\- 要点<br>下一行 \| \~\~删除\~\~'));
    expect(markdown, isNot(contains('<script>')));
    expect(markdown, isNot(contains('\n# 第二行')));
    expect(markdown, contains('<p align="center">...</p>'));
  });

  test('Markdown 转义不改变 JSON 消费者拿到的原始内容', () {
    const String text = '😀 &lt;原文&gt;\n\n```\n![图片](file:///录音)\n```';
    final SessionExportSnapshot literal = _snapshot(
      placeholderTitle: text,
      asrText: text,
      userText: text,
      artifactText: text,
    );

    final String markdown = exportSessionMarkdown(literal);
    expect(markdown, contains('😀 &amp;lt;原文&amp;gt;<br><br>\\`\\`\\`<br>'));
    expect(markdown, contains(r'\!\[图片\]\(file:///录音\)<br>\`\`\`'));
    expect(markdown, isNot(contains('\n```')));

    final Map<String, Object?> json =
        jsonDecode(exportSessionJson(literal)) as Map<String, Object?>;
    expect((json['session']! as Map<String, Object?>)['title'], text);
    final Map<String, Object?> segment =
        (json['segments']! as List<Object?>).first! as Map<String, Object?>;
    final List<Object?> versions = segment['versions']! as List<Object?>;
    expect(versions, hasLength(2));
    for (final Object? version in versions) {
      expect((version! as Map<String, Object?>)['text'], text);
    }
    final Map<String, Object?> artifact =
        (segment['artifacts']! as List<Object?>).single! as Map<String, Object?>;
    expect(artifact['text'], text);
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

  test('混合快照只导出当前会话及对应段的证据', () {
    final SessionExportSnapshot mixed = SessionExportSnapshot(
      session: snapshot.session,
      title: const TitlePresentation(
        sessionId: 'other', placeholder: '外部标题',
        latestTitle: '外部标题', pendingFirstReveal: false,
      ),
      chunks: <AudioChunkRecord>[
        ...snapshot.chunks,
        const AudioChunkRecord(
          id: 'foreign-chunk', sessionId: 'other', chunkSeq: 0,
          state: ChunkState.sealed, createdAtUtcMs: 1000,
          tempPath: '/foreign/audio.part',
        ),
      ],
      finals: <FinalSource>[
        ...snapshot.finals,
        const FinalSource(
          id: 'foreign-final', sessionId: 'other', text: '外部原文',
          finalizedAtUtcMs: 5000, captureStartedMonoNs: 1,
          captureEndedMonoNs: 2, capturedStartMs: 0, capturedEndMs: 1,
          chunkIds: <String>['foreign-chunk'],
          modelId: 'model', modelVersion: '1',
        ),
      ],
      segments: <TranscriptSegment>[
        ...snapshot.segments,
        TranscriptSegment(
          id: 'foreign-segment', sessionId: 'other', kind: SegmentKind.text,
          ordinal: 0, capturedStartMs: 0, capturedEndMs: 1,
          chunkIds: const <String>['foreign-chunk'],
          sourceFinalIds: const <String>['foreign-final'],
        ),
      ],
      versionsBySegment: <String, List<TranscriptVersion>>{
        'seg-1': <TranscriptVersion>[
          ...snapshot.versionsOf('seg-1'),
          TranscriptVersion(
            id: 'foreign-version', segmentId: 'foreign-segment',
            kind: VersionKind.asr, layer: SemanticLayer.l0, text: '外部版本',
            createdAtUtcMs: 5000, finalizedAtUtcMs: 5000,
            captureStartMs: 0, captureEndMs: 1,
          ),
        ],
      },
      artifactsBySegment: <String, List<SemanticArtifact>>{
        'seg-1': <SemanticArtifact>[
          ...snapshot.artifactsOf('seg-1'),
          for (final bool foreignSession in <bool>[false, true])
            SemanticArtifact(
              id: 'foreign-artifact-$foreignSession',
              sessionId: foreignSession ? 'other' : 's1',
              segmentId: foreignSession ? 'seg-1' : 'foreign-segment',
              layer: SemanticLayer.l2, text: '外部产物',
              state: ArtifactState.ready,
              sourceSet: snapshot.artifactsOf('seg-1').single.sourceSet,
              promptVersion: 'p1', model: 'model', createdAtUtcMs: 7000,
            ),
        ],
      },
    );

    final String markdown = exportSessionMarkdown(mixed);
    final String rawJson = exportSessionJson(mixed);
    for (final String exported in <String>[markdown, rawJson]) {
      expect(exported, isNot(contains('foreign')));
      expect(exported, isNot(contains('外部')));
      expect(exported, contains('原始识别文字'));
      expect(exported, contains('段落要点'));
    }
    expect(markdown, contains('音频块：1'));
    final Map<String, Object?> json = jsonDecode(rawJson) as Map<String, Object?>;
    expect(json['titlePresentation'], isNull);
    expect(json['chunks'], hasLength(1));
    expect(json['finals'], hasLength(1));
    final List<Object?> segments = json['segments']! as List<Object?>;
    expect(segments, hasLength(2));
    final Map<String, Object?> segment = segments.first! as Map<String, Object?>;
    expect(segment['versions'], hasLength(2));
    expect(segment['artifacts'], hasLength(1));
  });

  test('导出不包含密钥字段', () {
    final String raw = '${exportSessionJson(snapshot)}\n${exportSessionMarkdown(snapshot)}';
    expect(raw.toLowerCase(), isNot(contains('apikey')));
    expect(raw.toLowerCase(), isNot(contains('authorization')));
    expect(raw.toLowerCase(), isNot(contains('secret')));
  });
}
