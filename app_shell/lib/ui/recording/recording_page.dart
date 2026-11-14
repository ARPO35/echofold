import 'dart:math' as math;

import 'package:flutter/gestures.dart' show DragStartBehavior;
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import '../../app/app_contract.dart';
import '../../domain/models.dart';
import '../motion/echo_motion.dart';
import 'amplitude_strip.dart';
import 'recording_controller.dart';

class RecordingPage extends StatefulWidget {
  const RecordingPage({super.key, required this.controller});
  final RecordingController controller;

  @override
  State<RecordingPage> createState() => _RecordingPageState();
}

class _RecordingPageState extends State<RecordingPage> with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _slide = AnimationController.unbounded(vsync: this)..addListener(_rebuildSlide);
  double _dragStart = 0;
  double _dragOffset = 0;
  double _trackWidth = 0;
  double _dragOrigin = 0;
  double _gestureTravel = 0;
  bool _confirming = false;
  bool _dragEnabled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    widget.controller.connect();
  }

  @override
  void didUpdateWidget(covariant RecordingPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
      widget.controller.connect();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (EchoMotion.of(context).reduceMotion && _slide.isAnimating) {
      _slide.stop();
      _slide.value = 0;
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) widget.controller.connect();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  void _rebuildSlide() {
    if (mounted) setState(() => _dragOffset = math.max(0, _slide.value));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_changed);
    _slide.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    if (_confirming || !widget.controller.canStart) return;
    setState(() => _confirming = true);
    final bool? confirmed = await showEchoDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('仅录音'),
        content: const Text('当前不使用转写模型。开始后只采集并保存音频。'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          FilledButton.icon(
            onPressed: () => Navigator.pop(context, true),
            icon: const Icon(Icons.mic),
            label: const Text('开始仅录音'),
          ),
        ],
      ),
    );
    if (mounted) setState(() => _confirming = false);
    if (confirmed == true && mounted) await widget.controller.startOnlyRecording();
  }

  Future<void> _mainAction() async {
    final RecordingController controller = widget.controller;
    if (controller.canStart) return _start();
    if (controller.canPause) return controller.pause();
    if (controller.canResume) return controller.resume();
  }

  void _animateBack(double velocity) {
    final EchoMotion motion = EchoMotion.of(context);
    _slide.stop();
    if (motion.reduceMotion) {
      _slide.value = 0;
      return;
    }
    _slide.value = _dragOffset;
    _slide.animateWith(SpringSimulation(motion.swipeSpring, _dragOffset, 0, velocity));
  }

  void _dragStartGesture(DragStartDetails details, bool enabled) {
    _slide.stop();
    _dragStart = details.globalPosition.dx;
    _dragOrigin = _dragOffset;
    _gestureTravel = 0;
    _dragEnabled = enabled;
  }

  void _dragUpdate(DragUpdateDetails details) {
    if (!_dragEnabled) return;
    _gestureTravel = _dragStart - details.globalPosition.dx;
    _slide.value = (_dragOrigin + _gestureTravel).clamp(0, _trackWidth);
  }

  void _dragEnd(DragEndDetails details, bool canStop) {
    final double threshold = math.min(96, _trackWidth * 0.4);
    final bool commit = _dragEnabled && canStop && _gestureTravel >= threshold;
    _dragEnabled = false;
    if (commit) widget.controller.stop();
    _animateBack(-details.velocity.pixelsPerSecond.dx);
  }

  String _statusLabel(RecordingUiState state) => switch (state.state) {
        SessionState.idle => '准备录音',
        SessionState.recording => '正在录音',
        SessionState.paused => '已暂停',
        SessionState.stopping => '正在保存',
        SessionState.lowStoragePaused => '存储不足，录音已暂停',
        SessionState.failed => '录音未能保存',
      };

  String _duration(int milliseconds) {
    final int seconds = milliseconds ~/ 1000;
    final String hours = (seconds ~/ 3600).toString().padLeft(2, '0');
    final String minutes = (seconds ~/ 60 % 60).toString().padLeft(2, '0');
    final String remainder = (seconds % 60).toString().padLeft(2, '0');
    return '$hours:$minutes:$remainder';
  }

  @override
  Widget build(BuildContext context) {
    final RecordingUiState state = widget.controller.state;
    final bool active = state.state == SessionState.recording || state.state == SessionState.paused || state.state == SessionState.lowStoragePaused || (state.state == SessionState.failed && state.sessionId != null);
    final bool canStop = widget.controller.canStop;
    final bool canMain = !_confirming && (widget.controller.canStart || widget.controller.canPause || widget.controller.canResume);
    final bool failedToSave = state.state == SessionState.failed && state.sessionId != null;
    final ColorScheme colors = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('EchoFold')),
      body: SafeArea(
        child: LayoutBuilder(builder: (BuildContext context, BoxConstraints constraints) {
          return SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: math.max(0, constraints.maxHeight - 48)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: <Widget>[
                const Text('录音采集', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                Text(_statusLabel(state), key: const Key('recording-status'), style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 20),
                LayoutBuilder(builder: (BuildContext context, BoxConstraints box) {
                  const double buttonSize = 96;
                  _trackWidth = math.max(0, box.maxWidth - buttonSize);
                  return SizedBox(
                    height: buttonSize,
                    child: Stack(alignment: Alignment.center, children: <Widget>[
                      Positioned(left: 0, right: buttonSize + 12, child: IgnorePointer(
                        child: AmplitudeStrip(
                          key: ValueKey<String?>(state.sessionId),
                          amplitudes: state.amplitudes,
                          frozen: widget.controller.waveformFrozen,
                          height: 72,
                        ),
                      )),
                      Positioned(right: _dragOffset.clamp(0, _trackWidth), child: GestureDetector(
                        key: const Key('recording-drag-button'),
                        dragStartBehavior: DragStartBehavior.down,
                        behavior: HitTestBehavior.opaque,
                        onHorizontalDragStart: (DragStartDetails details) => _dragStartGesture(details, canStop),
                        onHorizontalDragUpdate: _dragUpdate,
                        onHorizontalDragCancel: () { _dragEnabled = false; _animateBack(0); },
                        onHorizontalDragEnd: (DragEndDetails details) => _dragEnd(details, canStop),
                        child: Semantics(
                          button: true,
                          enabled: canMain,
                          label: state.state == SessionState.recording ? '暂停录音；向左拖动结束并保存' : state.state == SessionState.paused ? '继续录音；向左拖动结束并保存' : '开始仅录音',
                          child: SizedBox.square(dimension: buttonSize, child: FilledButton(
                            key: const Key('recording-main-action'),
                            style: FilledButton.styleFrom(shape: const CircleBorder(), backgroundColor: colors.error, foregroundColor: colors.onError),
                            onPressed: canMain ? _mainAction : null,
                            child: Icon(state.state == SessionState.recording ? Icons.pause : state.state == SessionState.paused ? Icons.play_arrow : Icons.mic, size: 42),
                          )),
                        ),
                      )),
                    ]),
                  );
                }),
                const SizedBox(height: 12),
                Text(canStop ? '向左拖动红色按钮，结束并保存' : state.state == SessionState.idle ? '点击红色按钮开始仅录音' : widget.controller.busy ? '正在确认录音状态…' : '请先处理下方提示', textAlign: TextAlign.right, style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: 22),
                Center(child: FittedBox(fit: BoxFit.scaleDown, child: Text(_duration(state.elapsedMs), key: const Key('recording-duration'), style: const TextStyle(fontSize: 38, fontFeatures: <FontFeature>[FontFeature.tabularFigures()])))),
                const SizedBox(height: 22),
                OutlinedButton.icon(
                  key: const Key('recording-stop-button'),
                  onPressed: canStop ? widget.controller.stop : null,
                  icon: const Icon(Icons.stop),
                  label: Text(widget.controller.recoveryRequired ? '录音待恢复' : failedToSave ? '重试保存' : '结束并保存'),
                ),
                if (state.storageWarning case final String warning) ...<Widget>[
                  const SizedBox(height: 12),
                  Text(warning, style: TextStyle(color: colors.error)),
                ],
                if (state.failure case final AppFailure failure) ...<Widget>[
                  const SizedBox(height: 12),
                  Text(failure.message, key: const Key('recording-error'), style: TextStyle(color: colors.error)),
                  if (widget.controller.permanentlyDenied)
                    Align(alignment: Alignment.centerLeft, child: TextButton.icon(
                      onPressed: widget.controller.busy ? null : widget.controller.openSettings,
                      icon: const Icon(Icons.settings), label: const Text('打开系统设置'),
                    )),
                  if (!widget.controller.connected || !widget.controller.ready)
                    Align(alignment: Alignment.centerLeft, child: TextButton.icon(
                      onPressed: widget.controller.busy ? null : widget.controller.connect,
                      icon: const Icon(Icons.sync), label: const Text('重新连接'),
                    )),
                ],
                const SizedBox(height: 24),
                Text(active ? '录音仅保存在此设备' : '尚未开始录音', textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
          );
        }),
      ),
    );
  }
}
