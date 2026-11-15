/// 应用层状态与动作契约。
///
/// 界面只依赖本文件，不直接访问数据库、HTTP 或平台通道。这样 widget 测试可以注入
/// 只实现公开契约的 fake，而生产实现负责把事件、持久化与网络编排串起来。
library;

import 'package:flutter/foundation.dart';

import '../domain/models.dart';

/// 可展示的失败；`code` 用于诊断，`message` 为用户可操作文案。
final class AppFailure {
  const AppFailure({required this.code, required this.message});

  final String code;
  final String message;
}

/// 一段振幅样本；来自真实 PCM 的 RMS，不是随机数。
final class AmplitudePoint {
  const AmplitudePoint({
    required this.monoNs,
    required this.capturedEndMs,
    required this.rms,
    required this.peak,
  });

  final int monoNs;
  final int capturedEndMs;
  final double rms;
  final double peak;
}

/// 录音页状态。
final class RecordingUiState {
  const RecordingUiState({
    required this.state,
    required this.amplitudes,
    this.sessionId,
    this.sessionTitle,
    this.elapsedMs = 0,
    this.gapCount = 0,
    this.transcriptAvailable = true,
    this.storageWarning,
    this.failure,
    this.isDispatching = false,
  });

  const RecordingUiState.idle()
      : this(state: SessionState.idle, amplitudes: const <AmplitudePoint>[]);

  final SessionState state;
  final List<AmplitudePoint> amplitudes;
  final String? sessionId;
  final String? sessionTitle;
  final int elapsedMs;
  final int gapCount;
  final bool transcriptAvailable;
  final String? storageWarning;
  final AppFailure? failure;

  /// 命令已提交、原生结果尚未返回；按钮据此防重复点击但不假装已录音。
  final bool isDispatching;

  bool get canStart => state == SessionState.idle;
  bool get canPause => state == SessionState.recording;
  bool get canResume => state == SessionState.paused;
  bool get canStop =>
      state == SessionState.recording ||
      state == SessionState.paused ||
      state == SessionState.lowStoragePaused;
  bool get isActive =>
      state == SessionState.recording ||
      state == SessionState.paused ||
      state == SessionState.stopping;
  bool get waveformFrozen => state != SessionState.recording;
}

/// 录音列表条目。
final class SessionCardView {
  const SessionCardView({
    required this.session,
    required this.title,
    required this.titleNeedsAnimation,
  });

  final SessionRecord session;
  final TitlePresentation title;
  final bool titleNeedsAnimation;
}

/// 阅读段展示。
final class SegmentView {
  const SegmentView({
    required this.segment,
    required this.l0Text,
    required this.visibleText,
    required this.visibleLayer,
    required this.isFolded,
    required this.manualExpanded,
    this.l1Text,
    this.l2Text,
    this.processingFailed = false,
    this.pendingProcessing = false,
    this.rerecognizing = false,
    this.rerecognitionFailure,
  });

  final TranscriptSegment segment;
  final String l0Text;
  final String? l1Text;
  final String? l2Text;
  final String visibleText;
  final SemanticLayer visibleLayer;
  final bool isFolded;
  final bool manualExpanded;
  final bool processingFailed;
  final bool pendingProcessing;
  final bool rerecognizing;
  final AppFailure? rerecognitionFailure;

  bool get isGap => segment.isGap;
  bool get canExpand => !isGap && visibleLayer.level < SemanticLayer.l2.level;
  bool get isLocked => manualExpanded;
}

/// 阅读模式。
enum ReadingMode {
  followBottom,
  freeReading,
}

/// 阅读页状态。
final class TranscriptUiState {
  const TranscriptUiState({
    required this.sessionId,
    required this.segments,
    required this.mode,
    this.livePartialText,
    this.livePartialStartMs,
    this.livePartialEndMs,
    this.asrLagMs = 0,
    this.failure,
  });

  const TranscriptUiState.empty()
      : this(
          sessionId: '',
          segments: const <SegmentView>[],
          mode: ReadingMode.followBottom,
        );

  final String sessionId;
  final List<SegmentView> segments;
  final ReadingMode mode;
  final String? livePartialText;
  final int? livePartialStartMs;
  final int? livePartialEndMs;
  final int asrLagMs;
  final AppFailure? failure;

  bool get isFreeReading => mode == ReadingMode.freeReading;
}

/// BYOK 配置视图；密钥永不出现在这里。
final class ByokUiState {
  const ByokUiState({
    required this.enabled,
    required this.baseUrl,
    required this.model,
    required this.hasKey,
    required this.hostConsentGranted,
    this.lastCheckFailure,
    this.checking = false,
  });

  const ByokUiState.disabled()
      : this(
          enabled: false,
          baseUrl: '',
          model: '',
          hasKey: false,
          hostConsentGranted: false,
        );

  final bool enabled;
  final String baseUrl;
  final String model;
  final bool hasKey;
  final bool hostConsentGranted;
  final AppFailure? lastCheckFailure;
  final bool checking;

  bool get configured =>
      enabled && baseUrl.isNotEmpty && model.isNotEmpty && hasKey && hostConsentGranted;
}

/// 设置页状态。
final class SettingsUiState {
  const SettingsUiState({
    required this.foldDelay,
    required this.byok,
    required this.modelStatus,
    required this.storageWarning,
  });

  final Duration foldDelay;
  final ByokUiState byok;
  final String modelStatus;
  final String? storageWarning;
}

/// 诊断摘要；默认不含正文与秘密。
final class DiagnosticsUiState {
  const DiagnosticsUiState({
    required this.entries,
    required this.queueRows,
    required this.gapRanges,
  });

  const DiagnosticsUiState.empty()
      : this(
          entries: const <String>[],
          queueRows: const <QueueRow>[],
          gapRanges: const <String>[],
        );

  final List<String> entries;
  final List<QueueRow> queueRows;
  final List<String> gapRanges;
}

/// 队列指标行。
final class QueueRow {
  const QueueRow({
    required this.name,
    required this.capacity,
    required this.depth,
    required this.highWatermark,
    required this.oldestAgeMs,
  });

  final String name;
  final int capacity;
  final int depth;
  final int highWatermark;
  final int oldestAgeMs;
}

/// Narrow read-only surface consumed by the live transcript reader.
abstract class TranscriptReaderController extends ChangeNotifier {
  TranscriptUiState get transcript;

  Future<void> setFreeReading(bool free);
}

/// 应用动作与状态。实现必须是 [ChangeNotifier]：界面用 [ListenableBuilder] 重建。
abstract class EchoAppController extends TranscriptReaderController {
  RecordingUiState get recording;

  @override
  TranscriptUiState get transcript;

  List<SessionCardView> get sessions;

  SettingsUiState get settings;

  DiagnosticsUiState get diagnostics;

  String? get selectedSessionId;

  /// 用户点击大红按钮：空闲时开始，录音中暂停，暂停中继续。
  Future<void> toggleRecording();

  /// 结束当前会话；成功后停留在空闲新会话但不自动录音。
  Future<void> stopRecording();

  /// 左滑达标后结束旧会话并创建空闲新会话。
  Future<void> endSessionBySwipe();

  Future<void> selectSession(String sessionId);

  Future<void> createIdleSession();

  Future<void> deleteSession(String sessionId);

  /// 单击折叠段逐级展开；双击由 [collapseSegment] 最短档。
  Future<void> expandSegment(String segmentId);

  Future<void> collapseSegment(String segmentId);

  Future<void> toggleSegmentLock(String segmentId);

  Future<void> rerecognizeSegment(String segmentId);

  Future<void> editL1(String segmentId, String text);

  Future<void> deleteUserL1(String segmentId);

  Future<void> playSegment(String segmentId);

  Future<void> requestPlaybackWhileRecording(String segmentId);

  Future<void> copySegment(String segmentId);

  @override
  Future<void> setFreeReading(bool free);

  Future<void> setFoldDelay(Duration delay);

  Future<void> saveByok({
    required String baseUrl,
    required String model,
    required String apiKey,
    required bool enabled,
    required bool hostConsent,
  });

  Future<void> checkByok();

  Future<void> retryProcessing(String segmentId);

  Future<void> exportSession({required String sessionId, required ExportFormat format});

  Future<void> importModel();

  Future<void> retryPlaceholder();

  Future<void> setOnlyRecording(bool onlyRecording);

  Future<void> markTitleRevealed({
    required String sessionId,
    required String versionId,
  });

  Future<void> refreshDiagnostics();

  Future<void> disposeController();
}

/// 导出格式。
enum ExportFormat {
  markdown,
  json,
  audio,
}
