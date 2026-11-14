/// 脱敏诊断摘要。
///
/// 只接受结构化指标与错误类别，不接受原始音频、完整转写、API Key、Authorization 或请求正文；
/// 因此调用方无法通过本模块生成包含秘密的导出。
library;

import 'dart:convert';

import '../domain/models.dart';

/// 队列指标。
final class DiagnosticQueue {
  const DiagnosticQueue({
    required this.name,
    required this.capacity,
    required this.depth,
    required this.highWatermark,
    required this.oldestAgeMs,
    required this.rejected,
  });

  final String name;
  final int capacity;
  final int depth;
  final int highWatermark;
  final int oldestAgeMs;
  final int rejected;
}

/// 结构化诊断快照。
final class DiagnosticSnapshot {
  const DiagnosticSnapshot({
    required this.appVersion,
    required this.deviceLabel,
    required this.generatedAtUtcMs,
    required this.sessions,
    required this.chunks,
    required this.queues,
    required this.gapRanges,
    required this.lossRanges,
    required this.errors,
    this.modelStatus,
    this.encoding,
    this.storageFreeBytes,
  });

  final String appVersion;
  final String deviceLabel;
  final int generatedAtUtcMs;
  final List<SessionRecord> sessions;
  final List<AudioChunkRecord> chunks;
  final List<DiagnosticQueue> queues;
  final List<String> gapRanges;
  final List<String> lossRanges;
  final List<String> errors;
  final String? modelStatus;
  final String? encoding;
  final int? storageFreeBytes;
}

/// Markdown 摘要；便于用户人工查看与粘贴到 issue。
String diagnosticMarkdown(DiagnosticSnapshot snapshot) {
  final StringBuffer out = StringBuffer()
    ..writeln('# EchoFold 诊断摘要')
    ..writeln()
    ..writeln('- 版本：${snapshot.appVersion}')
    ..writeln('- 设备：${snapshot.deviceLabel}')
    ..writeln('- 生成：${formatSessionDate(snapshot.generatedAtUtcMs)}');
  if (snapshot.modelStatus != null) {
    out.writeln('- 模型：${snapshot.modelStatus}');
  }
  if (snapshot.encoding != null) {
    out.writeln('- 编码：${snapshot.encoding}');
  }
  if (snapshot.storageFreeBytes != null) {
    out.writeln('- 可用存储：${_bytes(snapshot.storageFreeBytes!)}');
  }
  out
    ..writeln()
    ..writeln('## 队列')
    ..writeln()
    ..writeln('| 队列 | 容量 | 深度 | 峰值 | 最旧等待(ms) | 拒绝 |')
    ..writeln('|---|---:|---:|---:|---:|---:|');
  for (final DiagnosticQueue queue in snapshot.queues) {
    out.writeln(
      '| ${queue.name} | ${queue.capacity} | ${queue.depth} | '
      '${queue.highWatermark} | ${queue.oldestAgeMs} | ${queue.rejected} |',
    );
  }
  out
    ..writeln()
    ..writeln('## 音频块')
    ..writeln()
    ..writeln('| 会话 | 序号 | 状态 | 时长(ms) | 码率 | 失败阶段 |')
    ..writeln('|---|---:|---|---:|---:|---|');
  for (final AudioChunkRecord chunk in snapshot.chunks) {
    out.writeln(
      '| ${chunk.sessionId} | ${chunk.chunkSeq} | ${chunk.state.wireName} | '
      '${chunk.durationMs ?? '-'} | ${chunk.bitRate ?? '-'} | '
      '${chunk.failureStage ?? '-'} |',
    );
  }
  out
    ..writeln()
    ..writeln('## 缺口与损失')
    ..writeln()
    ..writeln('- ASR 跳读区间：${snapshot.gapRanges.isEmpty ? '无' : snapshot.gapRanges.join('；')}')
    ..writeln('- 证据音频损失：${snapshot.lossRanges.isEmpty ? '无' : snapshot.lossRanges.join('；')}');
  if (snapshot.errors.isNotEmpty) {
    out
      ..writeln()
      ..writeln('## 错误类别')
      ..writeln();
    for (final String error in snapshot.errors) {
      out.writeln('- $error');
    }
  }
  return out.toString();
}

/// JSON 摘要。
String diagnosticJson(DiagnosticSnapshot snapshot) {
  final Map<String, Object?> root = <String, Object?>{
    'schema': 'echofold.diagnostics.v1',
    'appVersion': snapshot.appVersion,
    'deviceLabel': snapshot.deviceLabel,
    'generatedAtUtcMs': snapshot.generatedAtUtcMs,
    'modelStatus': snapshot.modelStatus,
    'encoding': snapshot.encoding,
    'storageFreeBytes': snapshot.storageFreeBytes,
    'sessions': snapshot.sessions
        .map(
          (SessionRecord session) => <String, Object?>{
            'id': session.id,
            'state': session.state.wireName,
            'createdAtUtcMs': session.createdAtUtcMs,
            'durationMs': session.durationMs,
            'gapCount': session.gapCount,
            'failureLabel': session.failureLabel,
          },
        )
        .toList(growable: false),
    'chunks': snapshot.chunks
        .map(
          (AudioChunkRecord chunk) => <String, Object?>{
            'id': chunk.id,
            'sessionId': chunk.sessionId,
            'chunkSeq': chunk.chunkSeq,
            'state': chunk.state.wireName,
            'durationMs': chunk.durationMs,
            'bitRate': chunk.bitRate,
            'sampleRate': chunk.sampleRate,
            'failureStage': chunk.failureStage,
          },
        )
        .toList(growable: false),
    'queues': snapshot.queues
        .map(
          (DiagnosticQueue queue) => <String, Object?>{
            'name': queue.name,
            'capacity': queue.capacity,
            'depth': queue.depth,
            'highWatermark': queue.highWatermark,
            'oldestAgeMs': queue.oldestAgeMs,
            'rejected': queue.rejected,
          },
        )
        .toList(growable: false),
    'gapRanges': snapshot.gapRanges,
    'lossRanges': snapshot.lossRanges,
    'errors': snapshot.errors,
  };
  return const JsonEncoder.withIndent('  ').convert(root);
}

String _bytes(int bytes) {
  if (bytes >= 1 << 30) {
    return '${(bytes / (1 << 30)).toStringAsFixed(1)} GiB';
  }
  if (bytes >= 1 << 20) {
    return '${(bytes / (1 << 20)).toStringAsFixed(1)} MiB';
  }
  return '$bytes B';
}
