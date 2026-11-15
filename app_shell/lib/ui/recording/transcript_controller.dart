import 'dart:async';

import '../../app/app_contract.dart';
import '../../domain/models.dart';
import '../../native/asr_event.dart';
import '../../native/asr_event_bridge.dart';

/// 将真实原生识别事件映射为当前会话的阅读状态。
final class TranscriptController extends TranscriptReaderController {
  TranscriptController({required AsrEventBridge bridge}) : _bridge = bridge {
    _subscription = bridge.events.listen(_receive, onError: _receiveError);
  }

  final AsrEventBridge _bridge;
  late final StreamSubscription<AsrEvent> _subscription;
  TranscriptUiState _state = const TranscriptUiState.empty();
  String? _sessionId;
  int _generation = -1;
  int _lastSeq = -1;
  int _asrLagMs = 0;
  AppFailure? _failure;
  final Set<String> _committedIds = <String>{};
  ReadingMode _mode = ReadingMode.followBottom;
  final List<SegmentView> _segments = <SegmentView>[];
  String? _partial;
  int? _partialStartMs;
  int? _partialEndMs;
  bool _disposed = false;

  @override
  TranscriptUiState get transcript => _state;

  void setSession(String? sessionId) {
    final String next = sessionId ?? '';
    if (next == _sessionId) return;
    _sessionId = next;
    _bridge.setSession(sessionId);
    _generation = -1;
    _lastSeq = -1;
    _asrLagMs = 0;
    _failure = null;
    _committedIds.clear();
    _segments.clear();
    _partial = null;
    _partialStartMs = null;
    _partialEndMs = null;
    _mode = ReadingMode.followBottom;
    _publish();
  }

  @override
  Future<void> setFreeReading(bool free) async {
    final ReadingMode next = free ? ReadingMode.freeReading : ReadingMode.followBottom;
    if (_mode == next) return;
    _mode = next;
    _publish();
  }

  void _receive(AsrEvent event) {
    if (_disposed || _sessionId == null || event.sessionId != _sessionId) return;
    if (event.generation < _generation) return;
    if (event.generation > _generation) {
      _generation = event.generation;
      _lastSeq = -1;
      _partial = null;
      _partialStartMs = null;
      _partialEndMs = null;
    }
    if (event.seq <= _lastSeq) return;
    _lastSeq = event.seq;
    switch (event) {
      case AsrPartial():
        _partial = event.text.isEmpty ? null : event.text;
        _partialStartMs = event.startMs;
        _partialEndMs = event.endMs;
        break;
      case AsrFinal():
        if (!_committedIds.add(event.finalId)) break;
        final TranscriptSegment segment = TranscriptSegment(
          id: event.finalId,
          sessionId: event.sessionId,
          kind: SegmentKind.text,
          ordinal: _segments.length,
          capturedStartMs: event.capturedStartMs,
          capturedEndMs: event.capturedEndMs,
          chunkIds: event.chunkIds,
          sourceFinalIds: <String>[event.finalId],
        );
        _segments.add(SegmentView(
          segment: segment,
          l0Text: event.text,
          visibleText: event.text,
          visibleLayer: SemanticLayer.l0,
          isFolded: false,
          manualExpanded: false,
        ));
        _partial = null;
        _partialStartMs = null;
        _partialEndMs = null;
        break;
      case AsrGap():
        if (!_committedIds.add(event.segmentId)) break;
        _segments.add(SegmentView(
          segment: TranscriptSegment(
            id: event.segmentId,
            sessionId: event.sessionId,
            kind: SegmentKind.gap,
            ordinal: _segments.length,
            capturedStartMs: event.capturedStartMs,
            capturedEndMs: event.capturedEndMs,
            chunkIds: event.chunkIds,
            gapReason: GapReason.asrOverload,
          ),
          l0Text: AsrGap.displayText,
          visibleText: AsrGap.displayText,
          visibleLayer: SemanticLayer.l0,
          isFolded: false,
          manualExpanded: false,
        ));
        _partial = null;
        _partialStartMs = null;
        _partialEndMs = null;
        _asrLagMs = event.lagMs;
        break;
      case AsrFinalCandidate():
        break;
      case AsrHealth():
        _asrLagMs = event.oldestUnprocessedAgeMs;
        _failure = event.errorCode == null && event.errorMessage == null
            ? null
            : AppFailure(code: event.errorCode ?? 'ASR_ERROR',
                message: event.errorMessage ?? '转写暂不可用，录音状态请查看录音提示。');
        break;
    }
    _publish();
  }

  void _receiveError(Object error, StackTrace stackTrace) {
    if (_disposed) return;
    _failure = const AppFailure(code: 'ASR_CHANNEL_ERROR', message: '转写连接中断，录音状态请查看录音提示。');
    _publish();
  }

  void _publish() {
    _state = TranscriptUiState(
      sessionId: _sessionId ?? '',
      segments: List<SegmentView>.unmodifiable(_segments),
      mode: _mode,
      livePartialText: _partial,
      livePartialStartMs: _partialStartMs,
      livePartialEndMs: _partialEndMs,
      asrLagMs: _asrLagMs,
      failure: _failure,
    );
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_subscription.cancel());
    unawaited(_bridge.dispose());
    super.dispose();
  }
}
