/// EchoFold 共享领域模型。
///
/// 这些类型同时被持久化层、语义编排和界面消费；任何字段语义变更都必须同步修改
/// Kotlin 侧 `com.echofold.core.model` 与原生事件桥，禁止只在 Dart 侧悄悄改协议。
library;

import 'dart:convert';

import 'package:crypto/crypto.dart';

/// 录音会话状态；与 Kotlin `SessionStatus` 的同名语义一致。
enum SessionState {
  idle('IDLE'),
  recording('RECORDING'),
  paused('PAUSED'),
  stopping('STOPPING'),
  lowStoragePaused('LOW_STORAGE_PAUSED'),
  failed('FAILED');

  const SessionState(this.wireName);

  final String wireName;

  static SessionState fromWireName(String value) => values.firstWhere(
        (SessionState state) => state.wireName == value,
        orElse: () => throw FormatException('未知的会话状态：$value'),
      );
}

/// 音频块状态；与 Kotlin `ChunkState` 一致。
enum ChunkState {
  placeholder('PLACEHOLDER'),
  open('OPEN'),
  sealing('SEALING'),
  sealed('SEALED'),
  sealFailed('SEAL_FAILED'),
  recoverable('RECOVERABLE'),
  corrupt('CORRUPT');

  const ChunkState(this.wireName);

  final String wireName;
}

/// 转写段类型。
enum SegmentKind {
  text('TEXT'),
  gap('GAP');

  const SegmentKind(this.wireName);

  final String wireName;
}

/// 转写缺口原因；首版只允许 ASR 排队过载。
enum GapReason {
  asrOverload('ASR_OVERLOAD');

  const GapReason(this.wireName);

  final String wireName;
}

/// 版本来源类型；参与规范来源集哈希。
enum SourceKind {
  finalSource('FINAL'),
  ai('AI'),
  user('USER'),
  gap('GAP');

  const SourceKind(this.wireName);

  final String wireName;
}

/// 不可变版本类型。
enum VersionKind {
  asr('ASR'),
  ai('AI'),
  user('USER'),
  gap('GAP');

  const VersionKind(this.wireName);

  final String wireName;
}

/// 阅读层级。L0 是证据，不是生成层。
enum SemanticLayer {
  l0(0),
  l1(1),
  l2(2),
  l3(3);

  const SemanticLayer(this.level);

  final int level;
}

/// 生成物状态。
enum ArtifactState {
  processing('PROCESSING'),
  ready('READY'),
  failed('FAILED'),
  stale('STALE');

  const ArtifactState(this.wireName);

  final String wireName;
}

/// 处理任务状态。
enum JobState {
  queued('QUEUED'),
  running('RUNNING'),
  succeeded('SUCCEEDED'),
  failed('FAILED'),
  cancelled('CANCELLED'),
  superseded('SUPERSEDED');

  const JobState(this.wireName);

  final String wireName;
}

/// 一条有序来源版本引用。
final class SourceVersionRef {
  const SourceVersionRef({
    required this.sourceId,
    required this.versionId,
    required this.sourceKind,
  });

  final String sourceId;
  final String versionId;
  final SourceKind sourceKind;

  Map<String, Object?> toJson() => <String, Object?>{
        'sourceId': sourceId,
        'versionId': versionId,
        'sourceKind': sourceKind.wireName,
      };

  static SourceVersionRef fromJson(Object? value) {
    if (value is! Map) {
      throw const FormatException('sourceVersions 元素必须是对象');
    }
    final Object? sourceId = value['sourceId'];
    final Object? versionId = value['versionId'];
    final Object? sourceKind = value['sourceKind'];
    if (sourceId is! String || sourceId.isEmpty) {
      throw const FormatException('sourceId 不能为空');
    }
    if (versionId is! String || versionId.isEmpty) {
      throw const FormatException('versionId 不能为空');
    }
    if (sourceKind is! String) {
      throw const FormatException('sourceKind 必须是字符串');
    }
    return SourceVersionRef(
      sourceId: sourceId,
      versionId: versionId,
      sourceKind: SourceKind.values.firstWhere(
        (SourceKind kind) => kind.wireName == sourceKind,
        orElse: () => throw FormatException('未知的 sourceKind：$sourceKind'),
      ),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is SourceVersionRef &&
      other.sourceId == sourceId &&
      other.versionId == versionId &&
      other.sourceKind == sourceKind;

  @override
  int get hashCode => Object.hash(sourceId, versionId, sourceKind);
}

/// 规范来源集模式版本；变更会改变所有哈希。
const String sourceSetSchema = 'echofold.source-set.v1';

/// 有序来源版本集合。
///
/// 顺序就是语义输入顺序：禁止排序或去重；同一 `sourceId` 出现两次视为非法。
final class SourceSet {
  SourceSet(List<SourceVersionRef> ordered)
      : ordered = List<SourceVersionRef>.unmodifiable(ordered) {
    if (ordered.isEmpty) {
      throw ArgumentError.value(ordered, 'ordered', '多源产物至少需要一个来源版本');
    }
    final Set<String> seen = <String>{};
    for (final SourceVersionRef ref in ordered) {
      if (!seen.add(ref.sourceId)) {
        throw ArgumentError.value(
          ordered,
          'ordered',
          'sourceId 重复：${ref.sourceId}；数组顺序有语义，禁止去重',
        );
      }
    }
  }

  final List<SourceVersionRef> ordered;

  /// 规范 JSON：固定键顺序、无无意义空白、数组顺序保留。
  String get canonicalJson {
    final StringBuffer buffer = StringBuffer('{"schema":')
      ..write(_quote(sourceSetSchema))
      ..write(',"sources":[');
    for (int index = 0; index < ordered.length; index++) {
      if (index > 0) {
        buffer.write(',');
      }
      final SourceVersionRef ref = ordered[index];
      buffer
        ..write('{"sourceId":')
        ..write(_quote(ref.sourceId))
        ..write(',"versionId":')
        ..write(_quote(ref.versionId))
        ..write(',"sourceKind":')
        ..write(_quote(ref.sourceKind.wireName))
        ..write('}');
    }
    buffer.write(']}');
    return buffer.toString();
  }

  late final String hash = sha256.convert(utf8.encode(canonicalJson)).toString();

  List<Map<String, Object?>> toJson() =>
      ordered.map((SourceVersionRef ref) => ref.toJson()).toList(growable: false);

  static SourceSet fromJson(Object? value, {String? expectedHash}) {
    if (value is! List) {
      throw const FormatException('sourceVersions 必须是数组');
    }
    final SourceSet set = SourceSet(
      value.map(SourceVersionRef.fromJson).toList(growable: false),
    );
    if (expectedHash != null && expectedHash != set.hash) {
      throw FormatException(
        'sourceSetHash 不匹配：$expectedHash != ${set.hash}',
      );
    }
    return set;
  }

  /// 逐项比较：哈希相同时仍比较所有来源的 ID、版本、顺序与类型。
  bool matches(SourceSet other) {
    if (hash != other.hash || ordered.length != other.ordered.length) {
      return false;
    }
    for (int index = 0; index < ordered.length; index++) {
      if (ordered[index] != other.ordered[index]) {
        return false;
      }
    }
    return true;
  }
}

String _quote(String value) => jsonEncode(value);

/// 一段不可变转写版本。
final class TranscriptVersion {
  TranscriptVersion({
    required this.id,
    required this.segmentId,
    required this.kind,
    required this.layer,
    required this.text,
    required this.createdAtUtcMs,
    required this.finalizedAtUtcMs,
    required this.captureStartMs,
    required this.captureEndMs,
    this.parentVersionId,
    this.modelId,
    this.modelVersion,
    List<SourceVersionRef> sourceVersions = const <SourceVersionRef>[],
    this.isDefault = false,
  }) : sourceSet = sourceVersions.isEmpty ? null : SourceSet(sourceVersions) {
    if (id.isEmpty) {
      throw ArgumentError.value(id, 'id', '不能为空');
    }
    if (segmentId.isEmpty) {
      throw ArgumentError.value(segmentId, 'segmentId', '不能为空');
    }
    if (captureStartMs < 0 || captureEndMs < captureStartMs) {
      throw ArgumentError('音频区间非法：$captureStartMs..$captureEndMs');
    }
    if (foldable && text.trim().isEmpty) {
      throw ArgumentError('TEXT 版本的正文不能为空白');
    }
    if (kind == VersionKind.gap && text != TranscriptSegment.gapDisplayText) {
      throw ArgumentError('GAP 版本的展示字面量必须为 ...');
    }
    if (parentVersionId == id) {
      throw ArgumentError('parentVersionId 不能指向自身');
    }
  }

  final String id;
  final String segmentId;
  final VersionKind kind;
  final SemanticLayer layer;
  final String text;
  final String? parentVersionId;
  final String? modelId;
  final String? modelVersion;
  final int createdAtUtcMs;
  final int finalizedAtUtcMs;
  final int captureStartMs;
  final int captureEndMs;
  final SourceSet? sourceSet;
  final bool isDefault;

  bool get foldable => kind != VersionKind.gap && layer == SemanticLayer.l0;

  String? get sourceSetHash => sourceSet?.hash;

  List<SourceVersionRef> get sourceVersions =>
      sourceSet?.ordered ?? const <SourceVersionRef>[];

  TranscriptVersion copyWith({bool? isDefault}) => TranscriptVersion(
        id: id,
        segmentId: segmentId,
        kind: kind,
        layer: layer,
        text: text,
        createdAtUtcMs: createdAtUtcMs,
        finalizedAtUtcMs: finalizedAtUtcMs,
        captureStartMs: captureStartMs,
        captureEndMs: captureEndMs,
        parentVersionId: parentVersionId,
        modelId: modelId,
        modelVersion: modelVersion,
        sourceVersions: sourceVersions,
        isDefault: isDefault ?? this.isDefault,
      );
}

/// 一个不可变转写段。
final class TranscriptSegment {
  TranscriptSegment({
    required this.id,
    required this.sessionId,
    required this.kind,
    required this.ordinal,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.chunkIds,
    this.sourceFinalIds = const <String>[],
    this.gapReason,
    this.foldDueAtUtcMs,
    this.defaultVersionId,
  }) {
    if (id.isEmpty || sessionId.isEmpty) {
      throw ArgumentError('段 ID 与会话 ID 不能为空');
    }
    if (capturedStartMs < 0 || capturedEndMs < capturedStartMs) {
      throw ArgumentError('段音频区间非法：$capturedStartMs..$capturedEndMs');
    }
    if (kind == SegmentKind.text) {
      if (sourceFinalIds.isEmpty) {
        throw ArgumentError('TEXT 段的 sourceFinalIds 不能为空');
      }
      if (sourceFinalIds.toSet().length != sourceFinalIds.length) {
        throw ArgumentError('TEXT 段的 sourceFinalIds 不能重复');
      }
      if (gapReason != null) {
        throw ArgumentError('TEXT 段不得带 gapReason');
      }
    } else {
      if (sourceFinalIds.isNotEmpty) {
        throw ArgumentError('GAP 段的 sourceFinalIds 必须为空');
      }
      if (gapReason != GapReason.asrOverload) {
        throw ArgumentError('GAP 段的 gapReason 必须为 ASR_OVERLOAD');
      }
      if (foldDueAtUtcMs != null) {
        throw ArgumentError('GAP 段不得带 foldDueAtUtcMs');
      }
    }
  }

  static const String gapDisplayText = '...';

  final String id;
  final String sessionId;
  final SegmentKind kind;
  final int ordinal;
  final List<String> sourceFinalIds;
  final int capturedStartMs;
  final int capturedEndMs;
  final List<String> chunkIds;
  final GapReason? gapReason;
  final int? foldDueAtUtcMs;
  final String? defaultVersionId;

  bool get isGap => kind == SegmentKind.gap;

  TranscriptSegment copyWith({
    String? defaultVersionId,
    int? foldDueAtUtcMs,
    bool clearFoldDue = false,
  }) =>
      TranscriptSegment(
        id: id,
        sessionId: sessionId,
        kind: kind,
        ordinal: ordinal,
        capturedStartMs: capturedStartMs,
        capturedEndMs: capturedEndMs,
        chunkIds: chunkIds,
        sourceFinalIds: sourceFinalIds,
        gapReason: gapReason,
        foldDueAtUtcMs: clearFoldDue ? null : foldDueAtUtcMs ?? this.foldDueAtUtcMs,
        defaultVersionId: defaultVersionId ?? this.defaultVersionId,
      );
}

/// 当前会话的原始 FINAL 证据。
final class FinalSource {
  const FinalSource({
    required this.id,
    required this.sessionId,
    required this.text,
    required this.finalizedAtUtcMs,
    required this.captureStartedMonoNs,
    required this.captureEndedMonoNs,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.chunkIds,
    required this.modelId,
    required this.modelVersion,
  });

  final String id;
  final String sessionId;
  final String text;
  final int finalizedAtUtcMs;
  final int captureStartedMonoNs;
  final int captureEndedMonoNs;
  final int capturedStartMs;
  final int capturedEndMs;
  final List<String> chunkIds;
  final String modelId;
  final String modelVersion;
}

/// 会话摘要。
final class SessionRecord {
  const SessionRecord({
    required this.id,
    required this.placeholderTitle,
    required this.createdAtUtcMs,
    required this.state,
    this.title,
    this.modelId,
    this.modelVersion,
    this.installationId,
    this.asrConfigHash,
    this.durationMs = 0,
    this.gapCount = 0,
    this.failureLabel,
    this.audioBytes = 0,
  });

  final String id;
  final String placeholderTitle;
  final int createdAtUtcMs;
  final SessionState state;
  final String? title;
  final String? modelId;
  final String? modelVersion;
  final String? installationId;
  final String? asrConfigHash;
  final int durationMs;
  final int gapCount;
  final String? failureLabel;
  final int audioBytes;

  String get displayTitle => title ?? placeholderTitle;

  bool get isActive =>
      state == SessionState.recording ||
      state == SessionState.paused ||
      state == SessionState.stopping;
}

/// 音频块索引；只保存元数据，音频文件本身由原生层管理。
final class AudioChunkRecord {
  const AudioChunkRecord({
    required this.id,
    required this.sessionId,
    required this.chunkSeq,
    required this.state,
    required this.createdAtUtcMs,
    required this.tempPath,
    this.finalPath,
    this.sealedAtUtcMs,
    this.capturedStartMs,
    this.capturedEndMs,
    this.durationMs,
    this.codec,
    this.sampleRate,
    this.bitRate,
    this.checksum,
    this.failureStage,
  });

  final String id;
  final String sessionId;
  final int chunkSeq;
  final ChunkState state;
  final int createdAtUtcMs;
  final String tempPath;
  final String? finalPath;
  final int? sealedAtUtcMs;
  final int? capturedStartMs;
  final int? capturedEndMs;
  final int? durationMs;
  final String? codec;
  final int? sampleRate;
  final int? bitRate;
  final String? checksum;
  final String? failureStage;
}

/// 处理任务；`idempotencyKey` 唯一。
final class ProcessingJob {
  const ProcessingJob({
    required this.id,
    required this.idempotencyKey,
    required this.sessionId,
    required this.targetId,
    required this.layer,
    required this.state,
    required this.sourceSet,
    required this.promptVersion,
    required this.model,
    required this.createdAtUtcMs,
    required this.updatedAtUtcMs,
    this.attempts = 0,
    this.errorCategory,
  });

  final String id;
  final String idempotencyKey;
  final String sessionId;
  final String targetId;
  final SemanticLayer layer;
  final JobState state;
  final SourceSet sourceSet;
  final String promptVersion;
  final String model;
  final int createdAtUtcMs;
  final int updatedAtUtcMs;
  final int attempts;
  final String? errorCategory;
}

/// 语义产物（L1/L2/L3 或标题）。
final class SemanticArtifact {
  const SemanticArtifact({
    required this.id,
    required this.sessionId,
    required this.layer,
    required this.text,
    required this.state,
    required this.sourceSet,
    required this.promptVersion,
    required this.model,
    required this.createdAtUtcMs,
    this.segmentId,
    this.parentVersionId,
    this.citations = const <ArtifactCitation>[],
    this.errorCategory,
  });

  final String id;
  final String sessionId;
  final String? segmentId;
  final SemanticLayer layer;
  final String text;
  final ArtifactState state;
  final SourceSet sourceSet;
  final String promptVersion;
  final String model;
  final int createdAtUtcMs;
  final String? parentVersionId;
  final List<ArtifactCitation> citations;
  final String? errorCategory;

  String get sourceSetHash => sourceSet.hash;

  SemanticArtifact copyWith({ArtifactState? state, String? errorCategory}) =>
      SemanticArtifact(
        id: id,
        sessionId: sessionId,
        segmentId: segmentId,
        layer: layer,
        text: text,
        state: state ?? this.state,
        sourceSet: sourceSet,
        promptVersion: promptVersion,
        model: model,
        createdAtUtcMs: createdAtUtcMs,
        parentVersionId: parentVersionId,
        citations: citations,
        errorCategory: errorCategory ?? this.errorCategory,
      );
}

/// 产物中对来源文字的引用范围。
final class ArtifactCitation {
  const ArtifactCitation({
    required this.sourceId,
    required this.versionId,
    required this.startChar,
    required this.endChar,
  });

  final String sourceId;
  final String versionId;
  final int startChar;
  final int endChar;

  Map<String, Object?> toJson() => <String, Object?>{
        'sourceId': sourceId,
        'versionId': versionId,
        'startChar': startChar,
        'endChar': endChar,
      };

  static ArtifactCitation fromJson(Object? value) {
    if (value is! Map) {
      throw const FormatException('citation 必须是对象');
    }
    return ArtifactCitation(
      sourceId: value['sourceId'] as String,
      versionId: value['versionId'] as String,
      startChar: value['startChar'] as int,
      endChar: value['endChar'] as int,
    );
  }
}

/// 标题展示状态；首次展示与后续更新严格分离。
final class TitlePresentation {
  const TitlePresentation({
    required this.sessionId,
    required this.placeholder,
    required this.pendingFirstReveal,
    this.latestTitle,
    this.latestVersionId,
    this.revealedAtUtcMs,
    this.sourceSet,
    this.generatedAtUtcMs,
  });

  final String sessionId;
  final String placeholder;
  final bool pendingFirstReveal;
  final String? latestTitle;
  final String? latestVersionId;
  final int? revealedAtUtcMs;
  final SourceSet? sourceSet;
  final int? generatedAtUtcMs;

  String get displayTitle => latestTitle ?? placeholder;

  bool get needsFirstReveal => latestTitle != null && revealedAtUtcMs == null;

  /// 未 revealed 时，新有效标题继承唯一首次展示资格；已 revealed 后不再获得。
  TitlePresentation withLatestTitle({
    required String title,
    required String versionId,
    required SourceSet sources,
    required int generatedAtUtcMs,
  }) =>
      TitlePresentation(
        sessionId: sessionId,
        placeholder: placeholder,
        pendingFirstReveal: revealedAtUtcMs == null || pendingFirstReveal,
        latestTitle: title,
        latestVersionId: versionId,
        revealedAtUtcMs: revealedAtUtcMs,
        sourceSet: sources,
        generatedAtUtcMs: generatedAtUtcMs,
      );

  TitlePresentation reveal({required String versionId, required int revealedAtUtcMs}) =>
      TitlePresentation(
        sessionId: sessionId,
        placeholder: placeholder,
        pendingFirstReveal: false,
        latestTitle: latestTitle,
        latestVersionId: latestTitle == null ? latestVersionId : versionId,
        revealedAtUtcMs: revealedAtUtcMs,
        sourceSet: sourceSet,
        generatedAtUtcMs: generatedAtUtcMs,
      );
}

/// 排序/展示用的本地时间标签。
String formatSessionDate(int utcMs) {
  final DateTime local = DateTime.fromMillisecondsSinceEpoch(utcMs, isUtc: true).toLocal();
  String two(int value) => value.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}';
}

/// 毫秒时长的人类可读格式。
String formatDuration(int ms) {
  final int totalSeconds = ms ~/ 1000;
  final int hours = totalSeconds ~/ 3600;
  final int minutes = (totalSeconds % 3600) ~/ 60;
  final int seconds = totalSeconds % 60;
  if (hours > 0) {
    return '$hours:${minutes.toString().padLeft(2, '0')}:'
        '${seconds.toString().padLeft(2, '0')}';
  }
  return '$minutes:${seconds.toString().padLeft(2, '0')}';
}
