/// 单个阅读段。
///
/// 普通段在 L0/L1/L2 间展开收起，GAP 段只展示居中的字面 `...` 与缺口区间入口。
/// 单击逐级展开，双击收最短档并解除锁定，长按打开菜单；三种手势只有一个生效。
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_contract.dart' as contract;
import '../../domain/models.dart';
import '../motion/echo_motion.dart';
import 'segment_edit_dialog.dart';
import 'segment_labels.dart';
import 'segment_menu.dart';
import 'spring_fold.dart';

/// 阅读段视图；和契约里的 [contract.SegmentView] 一一对应。
class SegmentView extends StatefulWidget {
  const SegmentView({
    super.key,
    required this.segment,
    required this.controller,
  });

  final contract.SegmentView segment;
  final contract.EchoAppController controller;

  @override
  State<SegmentView> createState() => _SegmentViewState();
}

class _SegmentViewState extends State<SegmentView> {
  contract.SegmentView get _segment => widget.segment;

  contract.EchoAppController get _controller => widget.controller;

  void _handleTap() {
    if (_segment.isGap) {
      unawaited(_showGapDetails());
      return;
    }
    if (_segment.canExpand) {
      unawaited(_controller.expandSegment(_segment.segment.id));
    }
  }

  void _handleDoubleTap() {
    if (_segment.isGap) {
      unawaited(_showGapDetails());
      return;
    }
    if (_segment.isLocked) {
      unawaited(_controller.toggleSegmentLock(_segment.segment.id));
    }
    unawaited(_controller.collapseSegment(_segment.segment.id));
  }

  Future<void> _handleLongPress() async {
    final contract.SegmentView segment = _segment;
    final SegmentMenuAction? action = await showSegmentMenu(
      context,
      isGap: segment.isGap,
      copyLabel: _copyLabel(),
      recordingActive: _controller.recording.state == SessionState.recording,
    );
    if (!mounted || action == null) {
      return;
    }
    switch (action) {
      case SegmentMenuAction.edit:
        await _edit();
      case SegmentMenuAction.play:
        await _play();
      case SegmentMenuAction.copy:
        await _copy();
      case SegmentMenuAction.rerecognize:
        await _controller.rerecognizeSegment(segment.segment.id);
    }
  }

  Future<void> _edit() async {
    final SegmentEditResult? result = await showSegmentEditDialog(
      context,
      segment: _segment,
    );
    if (!mounted || result == null) {
      return;
    }
    if (result.deleteUserVersion) {
      await _controller.deleteUserL1(_segment.segment.id);
      return;
    }
    final String? text = result.text;
    if (text != null) {
      await _controller.editL1(_segment.segment.id, text);
    }
  }

  Future<void> _play() async {
    if (_controller.recording.state == SessionState.recording) {
      await _controller.requestPlaybackWhileRecording(_segment.segment.id);
      return;
    }
    await _controller.playSegment(_segment.segment.id);
  }

  Future<void> _copy() async {
    final contract.SegmentView segment = _segment;
    final String text =
        segment.isGap ? TranscriptSegment.gapDisplayText : segment.visibleText;
    await Clipboard.setData(ClipboardData(text: text));
    await _controller.copySegment(segment.segment.id);
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(content: Text('已复制${_copyLabel()}')),
    );
  }

  String _copyLabel() {
    if (_segment.isGap) {
      return 'GAP 缺口（…）';
    }
    return semanticLayerLabel(_segment.visibleLayer);
  }

  Future<void> _showGapDetails() {
    final TranscriptSegment segment = _segment.segment;
    final GapReason? reason = segment.gapReason;
    return showEchoDialog<void>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('缺口详情'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('区间：${segmentRangeLabel(segment)}'),
            const SizedBox(height: 4),
            Text('原因：${reason == null ? '未知' : gapReasonLabel(reason)}'),
            const SizedBox(height: 8),
            const Text('缺口对应的音频证据仍保留，可回听或逐段重新识别。'),
          ],
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('关闭'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final EchoMotion motion = EchoMotion.of(context);
    final contract.SegmentView segment = _segment;

    return GestureDetector(
      key: ValueKey<String>('segment-tap-${segment.segment.id}'),
      behavior: HitTestBehavior.opaque,
      onTap: _handleTap,
      onDoubleTap: _handleDoubleTap,
      onLongPress: _handleLongPress,
      child: Container(
        width: double.infinity,
        margin: const EdgeInsets.symmetric(vertical: 6),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            _header(context, motion),
            const SizedBox(height: 6),
            if (segment.isGap)
              _gapBody(theme)
            else
              SpringFold(
                motion: motion,
                child: AnimatedSwitcher(
                  duration: motion.fade,
                  switchInCurve: motion.fadeCurve,
                  switchOutCurve: motion.fadeCurve,
                  child: Text(
                    segment.visibleText,
                    key: ValueKey<String>(
                      'segment-text-${segment.segment.id}-'
                      '${segment.visibleLayer.level}-${segment.visibleText.hashCode}',
                    ),
                    style: theme.textTheme.bodyLarge?.copyWith(height: 1.5),
                  ),
                ),
              ),
            ..._statusRows(theme),
          ],
        ),
      ),
    );
  }

  Widget _header(BuildContext context, EchoMotion motion) {
    final ThemeData theme = Theme.of(context);
    final contract.SegmentView segment = _segment;
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: <Widget>[
        _Badge(
          key: ValueKey<String>('segment-layer-${segment.segment.id}'),
          text: segment.isGap ? 'GAP' : semanticLayerLabel(segment.visibleLayer),
          color: segment.isGap
              ? theme.colorScheme.tertiaryContainer
              : theme.colorScheme.secondaryContainer,
        ),
        if (segment.isLocked)
          _Badge(text: '已锁定', color: theme.colorScheme.primaryContainer),
        if (segment.pendingProcessing)
          const _Badge(text: '处理中', color: Color(0x1F000000)),
        if (segment.rerecognizing)
          const _Badge(text: '重新识别中', color: Color(0x1F000000)),
      ],
    );
  }

  Widget _gapBody(ThemeData theme) {
    final TranscriptSegment segment = _segment.segment;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Text(
          TranscriptSegment.gapDisplayText,
          key: ValueKey<String>('gap-text-${segment.id}'),
          textAlign: TextAlign.center,
          style: theme.textTheme.titleLarge?.copyWith(
            letterSpacing: 4,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 2),
        Wrap(
          alignment: WrapAlignment.center,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: <Widget>[
            TextButton.icon(
              key: ValueKey<String>('gap-details-${segment.id}'),
              onPressed: () => unawaited(_showGapDetails()),
              icon: const Icon(Icons.info_outline, size: 16),
              label: Text(
                '缺口 ${segmentRangeLabel(segment)} · '
                '${segment.gapReason == null ? '未知原因' : gapReasonLabel(segment.gapReason!)}',
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ),
      ],
    );
  }

  List<Widget> _statusRows(ThemeData theme) {
    final contract.SegmentView segment = _segment;
    final List<Widget> rows = <Widget>[];
    if (segment.processingFailed) {
      rows.add(_statusRow(
        theme,
        icon: Icons.error_outline,
        text: '处理失败，仍保留原始文字',
        action: TextButton(
          key: ValueKey<String>('segment-retry-processing-${segment.segment.id}'),
          onPressed: () => unawaited(_controller.retryProcessing(segment.segment.id)),
          child: const Text('重试'),
        ),
      ));
    }
    final contract.AppFailure? failure = segment.rerecognitionFailure;
    if (failure != null) {
      rows.add(_statusRow(
        theme,
        icon: Icons.warning_amber_outlined,
        text: '重新识别失败：${failure.message}（${failure.code}）',
        action: TextButton(
          key: ValueKey<String>('segment-retry-rerecognition-${segment.segment.id}'),
          onPressed: () => unawaited(_controller.rerecognizeSegment(segment.segment.id)),
          child: const Text('重试'),
        ),
      ));
    }
    return rows;
  }

  Widget _statusRow(
    ThemeData theme, {
    required IconData icon,
    required String text,
    required Widget action,
  }) {
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Icon(icon, size: 16, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 6),
          Expanded(
            child: Text(text, style: theme.textTheme.bodySmall),
          ),
          action,
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({super.key, required this.text, required this.color});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(text, style: theme.textTheme.labelSmall),
    );
  }
}
