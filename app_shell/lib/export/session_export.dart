/// 会话文本/结构化导出。
///
/// 只接受领域模型，不接受配置或密钥对象；导出内容明确标注 ASR/AI/USER/GAP/重识别层级，
/// GAP 保留跳过区间与原因，不把字面 `...` 伪装成说话内容。
library;

import 'dart:convert';

import '../domain/models.dart';

/// 一次导出的完整只读快照。
final class SessionExportSnapshot {
  const SessionExportSnapshot({
    required this.session,
    required this.title,
    required this.chunks,
    required this.finals,
    required this.segments,
    required this.versionsBySegment,
    required this.artifactsBySegment,
  });

  final SessionRecord session;
  final TitlePresentation? title;
  final List<AudioChunkRecord> chunks;
  final List<FinalSource> finals;
  final List<TranscriptSegment> segments;
  final Map<String, List<TranscriptVersion>> versionsBySegment;
  final Map<String, List<SemanticArtifact>> artifactsBySegment;

  List<TranscriptVersion> versionsOf(String segmentId) =>
      versionsBySegment[segmentId] ?? const <TranscriptVersion>[];

  List<SemanticArtifact> artifactsOf(String segmentId) =>
      artifactsBySegment[segmentId] ?? const <SemanticArtifact>[];
}

/// Markdown 导出。
String exportSessionMarkdown(SessionExportSnapshot snapshot) {
  final StringBuffer out = StringBuffer();
  final String title = snapshot.title?.latestTitle ?? snapshot.session.displayTitle;
  out
    ..writeln('# $title')
    ..writeln()
    ..writeln('- 会话：`${snapshot.session.id}`')
    ..writeln('- 创建：${formatSessionDate(snapshot.session.createdAtUtcMs)}')
    ..writeln('- 时长：${formatDuration(snapshot.session.durationMs)}')
    ..writeln('- 状态：${snapshot.session.state.wireName}')
    ..writeln('- 音频块：${snapshot.chunks.length}');
  if (snapshot.session.gapCount > 0) {
    out.writeln('- ASR 过载缺口：${snapshot.session.gapCount} 处');
  }
  out.writeln();

  for (final TranscriptSegment segment in snapshot.segments) {
    if (segment.isGap) {
      out
        ..writeln('<p align="center">...</p>')
        ..writeln()
        ..writeln(
          '> ASR 过载缺口，跳过音频 ${_time(segment.capturedStartMs)}'
          '–${_time(segment.capturedEndMs)}；原始证据不可变。',
        )
        ..writeln();
      _writeVersions(out, snapshot.versionsOf(segment.id), indent: '');
      continue;
    }

    final List<TranscriptVersion> versions = snapshot.versionsOf(segment.id);
    final TranscriptVersion? defaultVersion = _defaultVersion(segment, versions);
    final List<TranscriptVersion> l1 =
        versions.where((TranscriptVersion v) => v.layer == SemanticLayer.l1).toList();
    final List<TranscriptVersion> l2 =
        versions.where((TranscriptVersion v) => v.layer == SemanticLayer.l2).toList();
    out
      ..writeln('### ${_time(segment.capturedStartMs)}–${_time(segment.capturedEndMs)}')
      ..writeln();
    if (defaultVersion != null) {
      out
        ..writeln('**${_layerLabel(defaultVersion)}**：${defaultVersion.text}')
        ..writeln();
    }
    for (final TranscriptVersion version in l1) {
      out.writeln('- L1 ${_originLabel(version)}：${version.text}');
    }
    for (final TranscriptVersion version in l2) {
      out.writeln('- L2 ${_originLabel(version)}：${version.text}');
    }
    final List<SemanticArtifact> artifacts = snapshot.artifactsOf(segment.id);
    for (final SemanticArtifact artifact in artifacts) {
      final String layer = switch (artifact.layer) {
        SemanticLayer.l1 => 'L1',
        SemanticLayer.l2 => 'L2 要点',
        SemanticLayer.l3 => 'L3 摘要',
        SemanticLayer.l0 => 'L0',
      };
      final String state = artifact.state == ArtifactState.ready
          ? ''
          : '（${artifact.state.wireName}）';
      out.writeln('- $layer$state：${artifact.text}');
    }
    out.writeln();
    _writeVersions(out, versions, indent: '');
    out.writeln();
  }

  return '${out.toString().trimRight()}\n';
}

/// 结构化 JSON 导出；保持稳定标识与三时间域映射。
String exportSessionJson(SessionExportSnapshot snapshot) {
  final Map<String, Object?> root = <String, Object?>{
    'schema': 'echofold.export.v1',
    'session': <String, Object?>{
      'id': snapshot.session.id,
      'title': snapshot.session.displayTitle,
      'placeholderTitle': snapshot.session.placeholderTitle,
      'createdAtUtcMs': snapshot.session.createdAtUtcMs,
      'state': snapshot.session.state.wireName,
      'durationMs': snapshot.session.durationMs,
      'gapCount': snapshot.session.gapCount,
      'modelId': snapshot.session.modelId,
      'modelVersion': snapshot.session.modelVersion,
      'installationId': snapshot.session.installationId,
      'asrConfigHash': snapshot.session.asrConfigHash,
    },
    'titlePresentation': snapshot.title == null
        ? null
        : <String, Object?>{
            'latestTitle': snapshot.title!.latestTitle,
            'latestVersionId': snapshot.title!.latestVersionId,
            'pendingFirstReveal': snapshot.title!.pendingFirstReveal,
            'revealedAtUtcMs': snapshot.title!.revealedAtUtcMs,
            'sourceVersions': snapshot.title!.sourceSet?.toJson(),
            'sourceSetHash': snapshot.title!.sourceSet?.hash,
          },
    'chunks': snapshot.chunks
        .map(
          (AudioChunkRecord chunk) => <String, Object?>{
            'id': chunk.id,
            'chunkSeq': chunk.chunkSeq,
            'state': chunk.state.wireName,
            'createdAtUtcMs': chunk.createdAtUtcMs,
            'sealedAtUtcMs': chunk.sealedAtUtcMs,
            'capturedStartMs': chunk.capturedStartMs,
            'capturedEndMs': chunk.capturedEndMs,
            'durationMs': chunk.durationMs,
            'codec': chunk.codec,
            'sampleRate': chunk.sampleRate,
            'bitRate': chunk.bitRate,
            'checksum': chunk.checksum,
            'failureStage': chunk.failureStage,
            'path': chunk.finalPath ?? chunk.tempPath,
          },
        )
        .toList(growable: false),
    'finals': snapshot.finals
        .map(
          (FinalSource source) => <String, Object?>{
            'id': source.id,
            'text': source.text,
            'finalizedAtUtcMs': source.finalizedAtUtcMs,
            'captureStartedMonoNs': source.captureStartedMonoNs,
            'captureEndedMonoNs': source.captureEndedMonoNs,
            'capturedStartMs': source.capturedStartMs,
            'capturedEndMs': source.capturedEndMs,
            'chunkIds': source.chunkIds,
            'modelId': source.modelId,
            'modelVersion': source.modelVersion,
          },
        )
        .toList(growable: false),
    'segments': snapshot.segments
        .map(
          (TranscriptSegment segment) => <String, Object?>{
            'id': segment.id,
            'ordinal': segment.ordinal,
            'kind': segment.kind.wireName,
            'sourceFinalIds': segment.sourceFinalIds,
            'capturedStartMs': segment.capturedStartMs,
            'capturedEndMs': segment.capturedEndMs,
            'chunkIds': segment.chunkIds,
            'gapReason': segment.gapReason?.wireName,
            'foldDueAtUtcMs': segment.foldDueAtUtcMs,
            'defaultVersionId': segment.defaultVersionId,
            'versions': snapshot
                .versionsOf(segment.id)
                .map(_versionJson)
                .toList(growable: false),
            'artifacts': snapshot
                .artifactsOf(segment.id)
                .map(_artifactJson)
                .toList(growable: false),
          },
        )
        .toList(growable: false),
  };
  return const JsonEncoder.withIndent('  ').convert(root);
}

Map<String, Object?> _versionJson(TranscriptVersion version) => <String, Object?>{
      'id': version.id,
      'kind': version.kind.wireName,
      'layer': version.layer.level,
      'text': version.text,
      'parentVersionId': version.parentVersionId,
      'modelId': version.modelId,
      'modelVersion': version.modelVersion,
      'finalizedAtUtcMs': version.finalizedAtUtcMs,
      'createdAtUtcMs': version.createdAtUtcMs,
      'capturedStartMs': version.captureStartMs,
      'capturedEndMs': version.captureEndMs,
      'isDefault': version.isDefault,
      'sourceVersions': version.sourceSet?.toJson(),
      'sourceSetHash': version.sourceSetHash,
    };

Map<String, Object?> _artifactJson(SemanticArtifact artifact) => <String, Object?>{
      'id': artifact.id,
      'layer': artifact.layer.level,
      'state': artifact.state.wireName,
      'text': artifact.text,
      'promptVersion': artifact.promptVersion,
      'model': artifact.model,
      'parentVersionId': artifact.parentVersionId,
      'sourceVersions': artifact.sourceSet.toJson(),
      'sourceSetHash': artifact.sourceSetHash,
      'citations': artifact.citations
          .map((ArtifactCitation citation) => citation.toJson())
          .toList(growable: false),
      'errorCategory': artifact.errorCategory,
    };

TranscriptVersion? _defaultVersion(
  TranscriptSegment segment,
  List<TranscriptVersion> versions,
) {
  for (final TranscriptVersion version in versions) {
    if (version.id == segment.defaultVersionId) {
      return version;
    }
  }
  for (final TranscriptVersion version in versions) {
    if (version.isDefault) {
      return version;
    }
  }
  return null;
}

void _writeVersions(
  StringBuffer out,
  List<TranscriptVersion> versions, {
  required String indent,
}) {
  if (versions.isEmpty) {
    return;
  }
  out.writeln('$indent来源版本：');
  for (final TranscriptVersion version in versions) {
    final String? hash = version.sourceSetHash;
    final String source = hash == null ? '' : '，sourceSetHash=$hash';
    out.writeln(
      '$indent- `${version.id}` ${_layerLabel(version)} ${_originLabel(version)}'
      '（父：${version.parentVersionId ?? '无'}$source）',
    );
  }
}

String _layerLabel(TranscriptVersion version) {
  switch (version.layer) {
    case SemanticLayer.l0:
      return version.kind == VersionKind.gap ? 'L0 GAP' : 'L0 原始 ASR';
    case SemanticLayer.l1:
      return 'L1 校订';
    case SemanticLayer.l2:
      return 'L2 要点';
    case SemanticLayer.l3:
      return 'L3 摘要';
  }
}

String _originLabel(TranscriptVersion version) {
  switch (version.kind) {
    case VersionKind.asr:
      return version.parentVersionId == null ? '[ASR]' : '[重识别 ASR]';
    case VersionKind.ai:
      return '[AI]';
    case VersionKind.user:
      return '[用户]';
    case VersionKind.gap:
      return '[GAP]';
  }
}

String _time(int ms) => formatDuration(ms);
