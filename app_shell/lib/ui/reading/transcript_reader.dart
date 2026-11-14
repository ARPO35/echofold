/// 实时阅读流。
///
/// - 普通段与 GAP 段按顺序渲染；Partial 只作为可替换的临时行，不写入段列表。
/// - 跟随底部时始终保持底边可见；手指按下或用户滚动立即进入自由阅读，程序滚动
///   不会被误判。自由阅读下新内容不抢位置，通过顶边锚点补偿保持阅读位置。
/// - 回到最新/手动划到底恢复跟随。
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/app_contract.dart';
import '../../domain/models.dart';
import '../motion/echo_motion.dart';
import '../reading/segment_view.dart';

/// 阅读区。
class TranscriptReader extends StatefulWidget {
  const TranscriptReader({
    super.key,
    required this.controller,
  });

  final EchoAppController controller;

  @override
  State<TranscriptReader> createState() => _TranscriptReaderState();
}

class _TranscriptReaderState extends State<TranscriptReader> {
  final ScrollController _scroll = ScrollController();
  final GlobalKey _viewportKey = GlobalKey();
  final Map<String, GlobalKey> _segmentKeys = <String, GlobalKey>{};

  List<SegmentViewData> _lastSegments = const <SegmentViewData>[];
  String? _lastPartialText;
  String _lastSessionId = '';
  bool _userScrolling = false;

  EchoAppController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_handleControllerChanged);
    _lastSessionId = _controller.transcript.sessionId;
    _lastSegments = _controller.transcript.segments;
    _lastPartialText = _controller.transcript.livePartialText;
  }

  @override
  void didUpdateWidget(TranscriptReader oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_handleControllerChanged);
      widget.controller.addListener(_handleControllerChanged);
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_handleControllerChanged);
    _scroll.dispose();
    super.dispose();
  }

  void _handleControllerChanged() {
    final TranscriptUiState state = _controller.transcript;
    final bool sessionChanged = state.sessionId != _lastSessionId;
    final bool contentChanged = sessionChanged ||
        !identical(state.segments, _lastSegments) ||
        state.livePartialText != _lastPartialText;
    final _ReaderAnchor? anchor =
        contentChanged && !sessionChanged ? _captureAnchor() : null;
    if (sessionChanged) {
      _segmentKeys.clear();
    }
    _lastSessionId = state.sessionId;
    _lastSegments = state.segments;
    _lastPartialText = state.livePartialText;
    if (!mounted) {
      return;
    }
    setState(() {});
    if (!contentChanged) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((Duration _) {
      if (!mounted) {
        return;
      }
      _afterContentChanged(anchor);
    });
  }

  void _afterContentChanged(_ReaderAnchor? anchor) {
    if (!_scroll.hasClients) {
      return;
    }
    if (_controller.transcript.mode == ReadingMode.followBottom) {
      _scrollToBottom();
      return;
    }
    if (anchor != null) {
      _restoreAnchor(anchor);
    }
  }

  void _scrollToBottom() {
    if (!_scroll.hasClients) {
      return;
    }
    final ScrollPosition position = _scroll.position;
    if (!position.hasContentDimensions) {
      return;
    }
    final double target = position.maxScrollExtent;
    if ((position.pixels - target).abs() < 0.5) {
      return;
    }
    final EchoMotion motion = EchoMotion.of(context);
    if (motion.reduceMotion) {
      position.jumpTo(target);
      return;
    }
    unawaited(_scroll.animateTo(
      target,
      duration: motion.base,
      curve: motion.geometryCurve,
    ));
  }

  _ReaderAnchor? _captureAnchor() {
    final RenderObject? viewportObject = _viewportKey.currentContext?.findRenderObject();
    if (viewportObject is! RenderBox || !viewportObject.attached) {
      return null;
    }
    final double viewportTop = viewportObject.localToGlobal(Offset.zero).dy;
    _ReaderAnchor? best;
    double bestTop = double.infinity;
    for (final MapEntry<String, GlobalKey> entry in _segmentKeys.entries) {
      final RenderObject? object = entry.value.currentContext?.findRenderObject();
      if (object is! RenderBox || !object.attached) {
        continue;
      }
      final double top = object.localToGlobal(Offset.zero).dy - viewportTop;
      if (top + object.size.height <= 0) {
        continue;
      }
      if (top < bestTop) {
        bestTop = top;
        best = _ReaderAnchor(entry.key, top);
      }
    }
    return best;
  }

  void _restoreAnchor(_ReaderAnchor anchor) {
    if (!_scroll.hasClients) {
      return;
    }
    final RenderObject? viewportObject = _viewportKey.currentContext?.findRenderObject();
    final RenderObject? object =
        _segmentKeys[anchor.segmentId]?.currentContext?.findRenderObject();
    if (viewportObject is! RenderBox ||
        object is! RenderBox ||
        !viewportObject.attached ||
        !object.attached) {
      return;
    }
    final double top = object.localToGlobal(Offset.zero).dy -
        viewportObject.localToGlobal(Offset.zero).dy;
    final double delta = top - anchor.viewportTop;
    if (delta.abs() < 0.5) {
      return;
    }
    final ScrollPosition position = _scroll.position;
    final double target = (position.pixels + delta)
        .clamp(position.minScrollExtent, position.maxScrollExtent);
    if ((target - position.pixels).abs() < 0.5) {
      return;
    }
    position.jumpTo(target);
  }

  void _enterFreeReading() {
    if (_controller.transcript.isFreeReading) {
      return;
    }
    unawaited(_controller.setFreeReading(true));
  }

  bool _handleScrollNotification(ScrollNotification notification) {
    if (notification.depth != 0) {
      return false;
    }
    switch (notification) {
      case ScrollStartNotification(dragDetails: final DragStartDetails? details)
          when details != null:
        _userScrolling = true;
        _enterFreeReading();
      case ScrollUpdateNotification(dragDetails: final DragUpdateDetails? details)
          when details != null:
        _userScrolling = true;
        _enterFreeReading();
      case ScrollEndNotification():
        final bool wasUserScrolling = _userScrolling;
        _userScrolling = false;
        if (wasUserScrolling &&
            _controller.transcript.isFreeReading &&
            _scroll.hasClients &&
            _scroll.position.pixels >= _scroll.position.maxScrollExtent - 1) {
          unawaited(_controller.setFreeReading(false));
        }
      default:
        break;
    }
    return false;
  }

  void _returnToLatest() {
    if (_controller.transcript.isFreeReading) {
      unawaited(_controller.setFreeReading(false));
    }
    if (!_scroll.hasClients) {
      return;
    }
    final ScrollPosition position = _scroll.position;
    if (!position.hasContentDimensions) {
      return;
    }
    final double target = position.maxScrollExtent;
    final EchoMotion motion = EchoMotion.of(context);
    if (motion.reduceMotion || (position.pixels - target).abs() < 0.5) {
      position.jumpTo(target);
      return;
    }
    unawaited(_scroll.animateTo(
      target,
      duration: motion.base,
      curve: motion.geometryCurve,
    ));
  }

  GlobalKey _keyFor(String id) => _segmentKeys.putIfAbsent(
        id,
        () => GlobalKey(debugLabel: 'segment-$id'),
      );

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final TranscriptUiState state = _controller.transcript;
    final bool hasPartial = state.livePartialText != null && state.livePartialText!.isNotEmpty;
    final bool isEmpty = state.segments.isEmpty && !hasPartial;

    return Stack(
      children: <Widget>[
        Positioned.fill(
          child: Listener(
            onPointerDown: (PointerDownEvent _) => _enterFreeReading(),
            child: NotificationListener<ScrollNotification>(
              onNotification: _handleScrollNotification,
              child: Scrollbar(
                controller: _scroll,
                child: ListView.builder(
                  key: _viewportKey,
                  controller: _scroll,
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
                  itemCount: state.segments.length + (hasPartial ? 1 : 0),
                  itemBuilder: (BuildContext context, int index) {
                    if (index < state.segments.length) {
                      final SegmentViewData segment = state.segments[index];
                      return SegmentView(
                        key: _keyFor(segment.segment.id),
                        segment: segment,
                        controller: _controller,
                      );
                    }
                    return _PartialRow(
                      text: state.livePartialText!,
                      startMs: state.livePartialStartMs,
                      endMs: state.livePartialEndMs,
                    );
                  },
                ),
              ),
            ),
          ),
        ),
        if (isEmpty)
          Positioned.fill(
            child: IgnorePointer(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(
                    '还没有可阅读的文字',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
            ),
          ),
        if (state.isFreeReading)
          Positioned(
            right: 16,
            bottom: 16,
            child: SafeArea(
              child: FilledButton.tonalIcon(
                key: const ValueKey<String>('reading-back-to-latest'),
                onPressed: _returnToLatest,
                icon: const Icon(Icons.vertical_align_bottom),
                label: const Text('回到最新'),
              ),
            ),
          ),
      ],
    );
  }
}

/// Partial 临时行；单独渲染，不进入段列表。
class _PartialRow extends StatelessWidget {
  const _PartialRow({
    required this.text,
    required this.startMs,
    required this.endMs,
  });

  final String text;
  final int? startMs;
  final int? endMs;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String range = startMs != null && endMs != null
        ? '${formatDuration(startMs!)}–${formatDuration(endMs!)}'
        : '识别中';
    return Container(
      key: const ValueKey<String>('partial-row'),
      margin: const EdgeInsets.symmetric(vertical: 6),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Row(
            children: <Widget>[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.tertiaryContainer,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text('临时', style: theme.textTheme.labelSmall),
              ),
              const SizedBox(width: 8),
              Text(range, style: theme.textTheme.labelSmall),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            text,
            style: theme.textTheme.bodyLarge?.copyWith(
              fontStyle: FontStyle.italic,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

final class _ReaderAnchor {
  const _ReaderAnchor(this.segmentId, this.viewportTop);

  final String segmentId;
  final double viewportTop;
}
