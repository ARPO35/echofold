/// ASR 事件模型：与 Kotlin `com.echofold.core.asr.AsrEvent` 的 JSON 字段一一对应。
///
/// 原生侧通过 EventChannel 投递 camelCase 字段的 Map；本文件负责严格解析、校验不变量并
/// 提供同形 `toJson()`，方便单测与日志复现。字段名或语义变更必须同时修改 Kotlin 侧。
library;

/// 事件类型，取值与 Kotlin `AsrEventKind.wireName` 完全一致。
enum AsrEventKind {
  /// 可覆盖的临时结果，不得充当已保存证据。
  partial('partial'),

  /// 已出现稳定前缀的端点候选，仍不是不可变证据。
  finalCandidate('finalCandidate'),

  /// 已提交的不可变 FINAL 证据。
  finalEvent('final'),

  health('health'),

  /// ASR 积压跳读产生的不可变缺口证据。
  gap('gap');

  const AsrEventKind(this.wireName);

  /// JSON `kind` 字段取值。
  final String wireName;

  /// 按 `kind` 解析；未知取值抛 [FormatException]。
  static AsrEventKind fromWireName(String wireName) {
    for (final kind in AsrEventKind.values) {
      if (kind.wireName == wireName) {
        return kind;
      }
    }
    throw FormatException('未知的 ASR 事件类型 kind=$wireName');
  }
}

/// 原生 ASR 事件基类。
///
/// 每个事件带 `generation`（跳读世代）与 `seq`（采集顺序号），`eventId` 必须等于
/// `sessionId:generation:kind:seq`；接收方按 `eventId` 去重，并按 `generation` 拒绝旧世代事件。
sealed class AsrEvent {
  AsrEvent({
    required this.sessionId,
    required this.generation,
    required this.seq,
    required this.kind,
    String? eventId,
  }) : eventId = eventId ??
            deriveEventId(
              sessionId: sessionId,
              generation: generation,
              kind: kind,
              seq: seq,
            ) {
    if (sessionId.isEmpty) {
      throw ArgumentError.value(sessionId, 'sessionId', '不能为空');
    }
    if (generation < 0) {
      throw ArgumentError.value(generation, 'generation', '不能为负数');
    }
    if (seq < 0) {
      throw ArgumentError.value(seq, 'seq', '不能为负数');
    }
    final expected = deriveEventId(
      sessionId: sessionId,
      generation: generation,
      kind: kind,
      seq: seq,
    );
    if (this.eventId != expected) {
      throw ArgumentError.value(
        this.eventId,
        'eventId',
        '必须由 sessionId/generation/kind/seq 派生为 $expected',
      );
    }
  }

  /// 所属录音会话。
  final String sessionId;

  /// 跳读世代；旧世代结果不得提交。
  final int generation;

  /// 同一世代内单调递增的事件序号。
  final int seq;

  /// 事件类型。
  final AsrEventKind kind;

  /// 稳定事件 ID。
  final String eventId;

  /// 按原生规则构造稳定事件 ID。
  static String deriveEventId({
    required String sessionId,
    required int generation,
    required AsrEventKind kind,
    required int seq,
  }) =>
      '$sessionId:$generation:${kind.wireName}:$seq';

  /// 从 EventChannel 投递的 Map 解析；字段缺失、类型不符或违反不变量时抛 [FormatException]。
  static AsrEvent fromJson(Map<String, Object?> json) {
    try {
      final kind = AsrEventKind.fromWireName(_requireString(json, 'kind'));
      return switch (kind) {
        AsrEventKind.partial => AsrPartial._fromJson(json),
        AsrEventKind.finalCandidate => AsrFinalCandidate._fromJson(json),
        AsrEventKind.finalEvent => AsrFinal._fromJson(json),
        AsrEventKind.health => AsrHealth._fromJson(json),
        AsrEventKind.gap => AsrGap._fromJson(json),
      };
    } on ArgumentError catch (error) {
      throw FormatException('ASR 事件字段不合法：${error.message}');
    }
  }

  /// 序列化为与原协议同形的 Map（含 `kind` 与派生 `eventId`）。
  Map<String, Object?> toJson();

  /// 公共头部字段，子类拼接自身负载时复用。
  Map<String, Object?> baseJson() => <String, Object?>{
        'eventId': eventId,
        'kind': kind.wireName,
        'sessionId': sessionId,
        'generation': generation,
        'seq': seq,
      };
}

/// ASR 积压跳读产生的不可变 GAP 证据。
final class AsrGap extends AsrEvent {
  AsrGap({
    required super.sessionId,
    required super.generation,
    required super.seq,
    required this.segmentId,
    required this.finalizedAtUtcMs,
    required this.captureStartedMonoNs,
    required this.captureEndedMonoNs,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.gapReason,
    required this.chunkIds,
    required this.modelId,
    required this.modelVersion,
    required this.lagMs,
    super.eventId,
  }) : super(kind: AsrEventKind.gap) {
    if (segmentId.isEmpty || modelId.isEmpty || modelVersion.isEmpty) {
      throw ArgumentError('GAP segmentId/modelId/modelVersion 不能为空');
    }
    if (gapReason != 'ASR_OVERLOAD' || chunkIds.isEmpty ||
        chunkIds.any((String id) => id.trim().isEmpty) ||
        chunkIds.toSet().length != chunkIds.length) {
      throw ArgumentError('GAP reason/chunkIds 非法');
    }
    if (finalizedAtUtcMs < 0 || captureStartedMonoNs < 0 ||
        captureEndedMonoNs < captureStartedMonoNs || capturedStartMs < 0 ||
        capturedEndMs <= capturedStartMs || lagMs < 0) {
      throw ArgumentError('GAP 时间区间或 lagMs 非法');
    }
  }

  factory AsrGap._fromJson(Map<String, Object?> json) => AsrGap(
        sessionId: _requireString(json, 'sessionId'),
        generation: _requireInt(json, 'generation'),
        seq: _requireInt(json, 'seq'),
        eventId: _requireString(json, 'eventId'),
        segmentId: _requireString(json, 'segmentId'),
        finalizedAtUtcMs: _requireInt(json, 'finalizedAtUtcMs'),
        captureStartedMonoNs: _requireInt(json, 'captureStartedMonoNs'),
        captureEndedMonoNs: _requireInt(json, 'captureEndedMonoNs'),
        capturedStartMs: _requireInt(json, 'capturedStartMs'),
        capturedEndMs: _requireInt(json, 'capturedEndMs'),
        gapReason: _requireString(json, 'gapReason'),
        chunkIds: _requireStringList(json, 'chunkIds'),
        modelId: _requireString(json, 'modelId'),
        modelVersion: _requireString(json, 'modelVersion'),
        lagMs: _requireInt(json, 'lagMs'),
      );

  static const String displayText = '...';
  final String segmentId;
  final int finalizedAtUtcMs;
  final int captureStartedMonoNs;
  final int captureEndedMonoNs;
  final int capturedStartMs;
  final int capturedEndMs;
  final String gapReason;
  final List<String> chunkIds;
  final String modelId;
  final String modelVersion;
  final int lagMs;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
        ...baseJson(),
        'segmentId': segmentId,
        'finalizedAtUtcMs': finalizedAtUtcMs,
        'captureStartedMonoNs': captureStartedMonoNs,
        'captureEndedMonoNs': captureEndedMonoNs,
        'capturedStartMs': capturedStartMs,
        'capturedEndMs': capturedEndMs,
        'gapReason': gapReason,
        'chunkIds': chunkIds,
        'modelId': modelId,
        'modelVersion': modelVersion,
        'lagMs': lagMs,
      };
}

/// 可覆盖的临时识别结果。
final class AsrPartial extends AsrEvent {
  AsrPartial({
    required super.sessionId,
    required super.generation,
    required super.seq,
    required this.text,
    required this.startMs,
    required this.endMs,
    required this.modelId,
    required this.modelVersion,
    super.eventId,
  }) : super(kind: AsrEventKind.partial) {
    if (startMs < 0 || endMs < startMs) {
      throw ArgumentError('Partial 区间非法：startMs=$startMs, endMs=$endMs');
    }
    if (modelId.isEmpty || modelVersion.isEmpty) {
      throw ArgumentError('Partial 的 modelId/modelVersion 不能为空');
    }
  }

  factory AsrPartial._fromJson(Map<String, Object?> json) => AsrPartial(
        sessionId: _requireString(json, 'sessionId'),
        generation: _requireInt(json, 'generation'),
        seq: _requireInt(json, 'seq'),
        eventId: _requireString(json, 'eventId'),
        text: _requireString(json, 'text'),
        startMs: _requireInt(json, 'startMs'),
        endMs: _requireInt(json, 'endMs'),
        modelId: _requireString(json, 'modelId'),
        modelVersion: _requireString(json, 'modelVersion'),
      );

  /// 临时文字，可为空串（段刚建立尚无输出）。
  final String text;

  /// 段起点（有效音频 offset，毫秒）。
  final int startMs;

  /// 段终点（有效音频 offset，毫秒）。
  final int endMs;

  /// 会话固定模型 ID。
  final String modelId;

  /// 会话固定模型版本。
  final String modelVersion;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
        ...baseJson(),
        'text': text,
        'startMs': startMs,
        'endMs': endMs,
        'modelId': modelId,
        'modelVersion': modelVersion,
      };
}

/// 已出现稳定前缀的端点候选。
final class AsrFinalCandidate extends AsrEvent {
  AsrFinalCandidate({
    required super.sessionId,
    required super.generation,
    required super.seq,
    required this.text,
    required this.stablePrefixChars,
    required this.startMs,
    required this.endMs,
    super.eventId,
  }) : super(kind: AsrEventKind.finalCandidate) {
    if (startMs < 0 || endMs < startMs) {
      throw ArgumentError('FinalCandidate 区间非法：startMs=$startMs, endMs=$endMs');
    }
    if (stablePrefixChars < 0 || stablePrefixChars > text.length) {
      throw ArgumentError(
        'stablePrefixChars($stablePrefixChars) 必须落在 0..${text.length}',
      );
    }
  }

  factory AsrFinalCandidate._fromJson(Map<String, Object?> json) =>
      AsrFinalCandidate(
        sessionId: _requireString(json, 'sessionId'),
        generation: _requireInt(json, 'generation'),
        seq: _requireInt(json, 'seq'),
        eventId: _requireString(json, 'eventId'),
        text: _requireString(json, 'text'),
        stablePrefixChars: _requireInt(json, 'stablePrefixChars'),
        startMs: _requireInt(json, 'startMs'),
        endMs: _requireInt(json, 'endMs'),
      );

  /// 候选文字。
  final String text;

  /// 不会再被修改的前缀长度。
  final int stablePrefixChars;

  /// 段起点（有效音频 offset，毫秒）。
  final int startMs;

  /// 段终点（有效音频 offset，毫秒）。
  final int endMs;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
        ...baseJson(),
        'text': text,
        'stablePrefixChars': stablePrefixChars,
        'startMs': startMs,
        'endMs': endMs,
      };
}

/// 已提交的不可变 FINAL 证据。
final class AsrFinal extends AsrEvent {
  AsrFinal({
    required super.sessionId,
    required super.generation,
    required super.seq,
    required this.finalId,
    required this.text,
    required this.finalizedAtUtcMs,
    required this.captureStartedMonoNs,
    required this.captureEndedMonoNs,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.chunkIds,
    super.eventId,
  }) : super(kind: AsrEventKind.finalEvent) {
    if (finalId.isEmpty) {
      throw ArgumentError.value(finalId, 'finalId', '不能为空');
    }
    if (text.trim().isEmpty) {
      throw ArgumentError('FINAL 的 text 不能为空白');
    }
    if (finalizedAtUtcMs < 0) {
      throw ArgumentError.value(finalizedAtUtcMs, 'finalizedAtUtcMs', '不能为负数');
    }
    if (captureStartedMonoNs < 0 || captureEndedMonoNs < captureStartedMonoNs) {
      throw ArgumentError(
        '单调时钟区间非法：captureStartedMonoNs=$captureStartedMonoNs, '
        'captureEndedMonoNs=$captureEndedMonoNs',
      );
    }
    if (capturedStartMs < 0 || capturedEndMs < capturedStartMs) {
      throw ArgumentError(
        '有效音频区间非法：capturedStartMs=$capturedStartMs, capturedEndMs=$capturedEndMs',
      );
    }
    if (chunkIds.isEmpty || chunkIds.any((id) => id.isEmpty)) {
      throw ArgumentError('chunkIds 必须非空且不含空串');
    }
    if (chunkIds.toSet().length != chunkIds.length) {
      throw ArgumentError('chunkIds 不能包含重复值：$chunkIds');
    }
  }

  factory AsrFinal._fromJson(Map<String, Object?> json) => AsrFinal(
        sessionId: _requireString(json, 'sessionId'),
        generation: _requireInt(json, 'generation'),
        seq: _requireInt(json, 'seq'),
        eventId: _requireString(json, 'eventId'),
        finalId: _requireString(json, 'finalId'),
        text: _requireString(json, 'text'),
        finalizedAtUtcMs: _requireInt(json, 'finalizedAtUtcMs'),
        captureStartedMonoNs: _requireInt(json, 'captureStartedMonoNs'),
        captureEndedMonoNs: _requireInt(json, 'captureEndedMonoNs'),
        capturedStartMs: _requireInt(json, 'capturedStartMs'),
        capturedEndMs: _requireInt(json, 'capturedEndMs'),
        chunkIds: _requireStringList(json, 'chunkIds'),
      );

  /// FINAL 稳定 ID，作为 `sourceFinalIds` 的元素。
  final String finalId;

  /// 不可变转写文字。
  final String text;

  /// UTC 提交时间，是自动折叠计时依据。
  final int finalizedAtUtcMs;

  /// 采集单调时钟起点（纳秒）。
  final int captureStartedMonoNs;

  /// 采集单调时钟终点（纳秒）。
  final int captureEndedMonoNs;

  /// 有效音频区间起点（毫秒，排除暂停间隙）。
  final int capturedStartMs;

  /// 有效音频区间终点（毫秒，排除暂停间隙）。
  final int capturedEndMs;

  /// 按采集顺序排列的音频块引用。
  final List<String> chunkIds;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
        ...baseJson(),
        'finalId': finalId,
        'text': text,
        'finalizedAtUtcMs': finalizedAtUtcMs,
        'captureStartedMonoNs': captureStartedMonoNs,
        'captureEndedMonoNs': captureEndedMonoNs,
        'capturedStartMs': capturedStartMs,
        'capturedEndMs': capturedEndMs,
        'chunkIds': chunkIds,
      };
}

/// ASR 队列指标，字段顺序与 Kotlin `QueueMetrics` 一致。
final class AsrQueueMetrics {
  AsrQueueMetrics({
    required this.capacity,
    required this.depth,
    required this.highWatermark,
    required this.oldestEnqueuedMonoNs,
    required this.newestEnqueuedMonoNs,
    required this.rejectedCount,
    required this.blocked,
  }) {
    if (capacity < 0 || depth < 0 || depth > capacity) {
      throw ArgumentError('队列深度非法：capacity=$capacity, depth=$depth');
    }
    if (highWatermark < depth || highWatermark > capacity) {
      throw ArgumentError(
        'highWatermark($highWatermark) 必须落在 $depth..$capacity',
      );
    }
    if (newestEnqueuedMonoNs < 0 || rejectedCount < 0) {
      throw ArgumentError('newestEnqueuedMonoNs/rejectedCount 不能为负数');
    }
    final oldest = oldestEnqueuedMonoNs;
    if (depth == 0 && oldest != null) {
      throw ArgumentError('队列为空时 oldestEnqueuedMonoNs 必须为 null');
    }
    if (depth > 0 && (oldest == null || oldest > newestEnqueuedMonoNs)) {
      throw ArgumentError('队列非空时 oldestEnqueuedMonoNs 必须存在且不晚于 newestEnqueuedMonoNs');
    }
  }

  factory AsrQueueMetrics._fromJson(Map<String, Object?> json) => AsrQueueMetrics(
        capacity: _requireInt(json, 'capacity'),
        depth: _requireInt(json, 'depth'),
        highWatermark: _requireInt(json, 'highWatermark'),
        oldestEnqueuedMonoNs: _optionalInt(json, 'oldestEnqueuedMonoNs'),
        newestEnqueuedMonoNs: _requireInt(json, 'newestEnqueuedMonoNs'),
        rejectedCount: _requireInt(json, 'rejectedCount'),
        blocked: _requireBool(json, 'blocked'),
      );

  /// 固定容量。
  final int capacity;

  /// 当前深度。
  final int depth;

  /// 最高水位。
  final int highWatermark;

  /// 最旧入队帧的单调时间；队列为空时为 null。
  final int? oldestEnqueuedMonoNs;

  /// 最新入队帧的单调时间。
  final int newestEnqueuedMonoNs;

  /// 因队列满被拒绝的次数。
  final int rejectedCount;

  /// 是否处于背压状态。
  final bool blocked;

  /// 序列化为与原协议同形的 Map。
  Map<String, Object?> toJson() => <String, Object?>{
        'capacity': capacity,
        'depth': depth,
        'highWatermark': highWatermark,
        'oldestEnqueuedMonoNs': oldestEnqueuedMonoNs,
        'newestEnqueuedMonoNs': newestEnqueuedMonoNs,
        'rejectedCount': rejectedCount,
        'blocked': blocked,
      };
}

/// 运行健康采样。
final class AsrHealth extends AsrEvent {
  AsrHealth({
    required super.sessionId,
    required super.generation,
    required super.seq,
    required this.rtf,
    required this.oldestUnprocessedAgeMs,
    required this.threadCount,
    required this.memoryBytes,
    required this.skipCount,
    required this.queue,
    this.errorCode,
    this.errorMessage,
    super.eventId,
  }) : super(kind: AsrEventKind.health) {
    if (rtf.isNaN || rtf < 0) {
      throw ArgumentError.value(rtf, 'rtf', '必须为非负有限值');
    }
    if (oldestUnprocessedAgeMs < 0) {
      throw ArgumentError.value(oldestUnprocessedAgeMs, 'oldestUnprocessedAgeMs', '不能为负数');
    }
    if (threadCount <= 0) {
      throw ArgumentError.value(threadCount, 'threadCount', '必须为正数');
    }
    if (memoryBytes < 0 || skipCount < 0) {
      throw ArgumentError('memoryBytes/skipCount 不能为负数');
    }
  }

  factory AsrHealth._fromJson(Map<String, Object?> json) => AsrHealth(
        sessionId: _requireString(json, 'sessionId'),
        generation: _requireInt(json, 'generation'),
        seq: _requireInt(json, 'seq'),
        eventId: _requireString(json, 'eventId'),
        rtf: _requireDouble(json, 'rtf'),
        oldestUnprocessedAgeMs: _requireInt(json, 'oldestUnprocessedAgeMs'),
        threadCount: _requireInt(json, 'threadCount'),
        memoryBytes: _requireInt(json, 'memoryBytes'),
        skipCount: _requireInt(json, 'skipCount'),
        queue: AsrQueueMetrics._fromJson(_requireMap(json, 'queue')),
        errorCode: _optionalString(json, 'errorCode'),
        errorMessage: _optionalString(json, 'errorMessage'),
      );

  /// 实时系数（识别耗时 / 音频时长）。
  final double rtf;

  /// 最旧未处理 PCM 的单调排队时延（毫秒），超过 15000 才触发跳读。
  final int oldestUnprocessedAgeMs;

  /// 推理线程数。
  final int threadCount;

  /// 运行时内存占用（字节）。
  final int memoryBytes;

  /// 累计跳读次数。
  final int skipCount;

  /// ASR 队列指标。
  final AsrQueueMetrics queue;

  /// 错误类别；正常时为 null。
  final String? errorCode;

  /// 脱敏错误描述；正常时为 null。
  final String? errorMessage;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
        ...baseJson(),
        'rtf': rtf,
        'oldestUnprocessedAgeMs': oldestUnprocessedAgeMs,
        'threadCount': threadCount,
        'memoryBytes': memoryBytes,
        'skipCount': skipCount,
        'queue': queue.toJson(),
        'errorCode': errorCode,
        'errorMessage': errorMessage,
      };
}

String _requireString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is String) {
    return value;
  }
  throw FormatException('字段 $key 必须是字符串，实际为 ${value.runtimeType}');
}

String? _optionalString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value == null || value is String) {
    return value as String?;
  }
  throw FormatException('字段 $key 必须是字符串或 null，实际为 ${value.runtimeType}');
}

int _requireInt(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is int) {
    return value;
  }
  if (value is double && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException('字段 $key 必须是整数，实际为 ${value.runtimeType}');
}

int? _optionalInt(Map<String, Object?> json, String key) {
  if (json[key] == null) {
    return null;
  }
  return _requireInt(json, key);
}

double _requireDouble(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is num) {
    return value.toDouble();
  }
  throw FormatException('字段 $key 必须是数值，实际为 ${value.runtimeType}');
}

bool _requireBool(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is bool) {
    return value;
  }
  throw FormatException('字段 $key 必须是布尔值，实际为 ${value.runtimeType}');
}

List<String> _requireStringList(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is List && value.every((element) => element is String)) {
    return List<String>.unmodifiable(value.cast<String>());
  }
  throw FormatException('字段 $key 必须是字符串数组，实际为 ${value.runtimeType}');
}

Map<String, Object?> _requireMap(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is Map) {
    return value.map((mapKey, mapValue) => MapEntry(mapKey.toString(), mapValue));
  }
  throw FormatException('字段 $key 必须是对象，实际为 ${value.runtimeType}');
}

/// 事件被门控拒绝的原因。
enum AsrEventRejection {
  /// 同一 `eventId` 已经处理过。
  duplicateEventId,

  /// 事件所属世代早于当前世代（跳读后迟到的旧结果）。
  staleGeneration,
}

/// 门控判定结果；不可变，不修改入参。
final class AsrEventGateResult {
  const AsrEventGateResult({
    required this.accepted,
    required this.reason,
    required this.generation,
    required this.seenEventIds,
  });

  /// 是否接受该事件。
  final bool accepted;

  /// 被拒绝的原因；接受时为 null。
  final AsrEventRejection? reason;

  /// 处理该事件后的当前世代（新世代事件会把它推进）。
  final int generation;

  /// 处理该事件后的已见 `eventId` 集合（只保留当前世代）。
  final Set<String> seenEventIds;
}

/// ASR 事件门控：纯函数实现，调用方持有 `generation` 与 `seenEventIds` 状态。
///
/// 规则（与共享契约第 3 节一致）：
/// 1. 事件世代小于当前世代 → 拒绝（旧世代迟到结果不得写入 GAP 或其他段）；
/// 2. 世代更大 → 接受，并把当前世代推进到该值，同时丢弃旧世代的去重记录；
/// 3. 世代相同 → 按 `eventId` 去重，重复事件丢弃。
///
/// 去重集合有上限，超出时按插入顺序淘汰最旧的 ID，避免长会话内存无限增长；
/// 同一世代内事件 ID 由单调递增的 `seq` 保证唯一，淘汰不影响正确性。
final class AsrEventGate {
  const AsrEventGate._();

  /// 默认保留的去重事件 ID 数量上限。
  static const int defaultMaxTrackedEventIds = 4096;

  /// 计算该事件是否被接受，并返回下一个门控状态。
  ///
  /// 本函数不修改 [seenEventIds]；被拒绝时原样返回该集合。
  static AsrEventGateResult evaluate({
    required AsrEvent event,
    required int generation,
    required Set<String> seenEventIds,
    int maxTrackedEventIds = defaultMaxTrackedEventIds,
  }) {
    if (maxTrackedEventIds <= 0) {
      throw ArgumentError.value(maxTrackedEventIds, 'maxTrackedEventIds', '必须为正数');
    }
    if (event.generation < generation) {
      return AsrEventGateResult(
        accepted: false,
        reason: AsrEventRejection.staleGeneration,
        generation: generation,
        seenEventIds: seenEventIds,
      );
    }
    if (event.generation > generation) {
      // 跳读产生新世代：旧世代去重记录不再有意义。
      return AsrEventGateResult(
        accepted: true,
        reason: null,
        generation: event.generation,
        seenEventIds: Set<String>.unmodifiable(<String>{event.eventId}),
      );
    }
    if (seenEventIds.contains(event.eventId)) {
      return AsrEventGateResult(
        accepted: false,
        reason: AsrEventRejection.duplicateEventId,
        generation: generation,
        seenEventIds: seenEventIds,
      );
    }
    final next = Set<String>.of(seenEventIds)..add(event.eventId);
    while (next.length > maxTrackedEventIds) {
      next.remove(next.first);
    }
    return AsrEventGateResult(
      accepted: true,
      reason: null,
      generation: generation,
      seenEventIds: Set<String>.unmodifiable(next),
    );
  }
}
