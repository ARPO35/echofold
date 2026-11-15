import 'dart:math' as math;

import 'package:flutter/gestures.dart' show DragStartBehavior;
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import '../../app/app_contract.dart';
import '../../domain/models.dart';
import '../motion/echo_motion.dart';
import '../motion/echo_press.dart';
import '../../native/asr_event_bridge.dart';
import '../reading/transcript_reader.dart';
import 'transcript_controller.dart';
import 'recording_action_shape.dart';
import 'amplitude_strip.dart';
import 'recording_controller.dart';

class RecordingPage extends StatefulWidget {
  const RecordingPage({super.key, required this.controller, this.asrBridge});
  final RecordingController controller;
  final AsrEventBridge? asrBridge;

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
  int _destination = 1;
  final Set<int> _visiblePages = <int>{1};
  late final TranscriptController _transcript = TranscriptController(
    bridge: widget.asrBridge ?? AsrEventBridge(),
  );
  bool _active = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    _transcript.setSession(widget.controller.state.sessionId);
    widget.controller.connect();
  }

  @override
  void didUpdateWidget(covariant RecordingPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
      widget.controller.connect();
      _transcript.setSession(widget.controller.state.sessionId);
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
    _transcript.setSession(widget.controller.state.sessionId);
    if (mounted) setState(() {});
  }

  void _rebuildSlide() {
    if (mounted) setState(() => _dragOffset = math.max(0, _slide.value));
  }

  @override
  void deactivate() {
    // 子手势识别器可能在卸载时才发送取消，此时不能再读 context 或启动动画。
    _active = false;
    super.deactivate();
  }

  @override
  void activate() {
    super.activate();
    _active = true;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_changed);
    _transcript.dispose();
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
          EchoPress(child: TextButton(
            onPressed: () => Navigator.pop(context, false), child: const Text('取消'),
          )),
          EchoPress(child: FilledButton.icon(
            onPressed: () => Navigator.pop(context, true),
            icon: const Icon(Icons.mic),
            label: const Text('开始仅录音'),
          )),
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
    if (!_active || !mounted) return;
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

  Widget _changingText(String text, {Key? key, TextStyle? style}) {
    final EchoMotion motion = EchoMotion.of(context);
    if (motion.reduceMotion) return Text(text, key: key, style: style);
    return KeyedSubtree(
      key: key,
      child: AnimatedSize(
        duration: motion.base,
        curve: motion.geometryCurve,
        alignment: Alignment.centerLeft,
        child: AnimatedSwitcher(
          duration: motion.fade,
          switchInCurve: motion.fadeCurve,
          switchOutCurve: motion.fadeCurve,
          child: Text(text, key: ValueKey<String>(text), style: style),
        ),
      ),
    );
  }

  Widget _readingPage({required Key key, required List<Widget> children}) =>
      EchoPress(
        scale: 0.996,
        borderRadius: BorderRadius.circular(24),
        child: SingleChildScrollView(
          key: key,
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: children,
          ),
        ),
      );

  Widget _errorPanel(RecordingUiState state) {
    final RecordingController controller = widget.controller;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final EchoMotion motion = EchoMotion.of(context);
    final AppFailure? failure = state.failure;
    final Widget panel = failure == null && state.storageWarning == null
        ? const SizedBox.shrink(key: ValueKey<String>('no-error'))
        : AnimatedContainer(
                key: ValueKey<String>('${failure?.code}:${state.storageWarning}'),
                duration: motion.base,
                padding: const EdgeInsets.all(16),
                margin: const EdgeInsets.only(top: 20),
                decoration: BoxDecoration(
                  color: colors.errorContainer,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    if (state.storageWarning case final String warning)
                      Text(warning, style: TextStyle(color: colors.onErrorContainer)),
                    if (failure != null)
                      Text(failure.message, key: const Key('recording-error'),
                          style: TextStyle(color: colors.onErrorContainer)),
                    if (controller.permanentlyDenied)
                      EchoPress(child: TextButton.icon(
                        onPressed: controller.busy ? null : controller.openSettings,
                        icon: const Icon(Icons.settings), label: const Text('打开系统设置'),
                      )),
                    if (!controller.connected || !controller.ready)
                      EchoPress(child: TextButton.icon(
                        onPressed: controller.busy ? null : controller.connect,
                        icon: const Icon(Icons.sync), label: const Text('重新连接'),
                      )),
                  ],
                ),
              );
    if (motion.reduceMotion) return panel;
    return AnimatedSize(
      duration: motion.base,
      curve: motion.geometryCurve,
      alignment: Alignment.topCenter,
      child: AnimatedSwitcher(duration: motion.fade, child: panel),
    );
  }

  @override
  Widget build(BuildContext context) {
    final RecordingController controller = widget.controller;
    final RecordingUiState state = controller.state;
    final bool canStop = controller.canStop;
    final bool canMain = !_confirming && (controller.canStart || controller.canPause || controller.canResume);
    final bool failedToSave = state.state == SessionState.failed && state.sessionId != null;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final EchoMotion motion = EchoMotion.of(context);
    final double phase = state.state == SessionState.recording ? 1 : state.state == SessionState.paused ? 2 : 0;
    final String actionLabel = state.state == SessionState.recording
        ? '暂停录音；向左拖动结束并保存'
        : state.state == SessionState.paused
            ? '继续录音；向左拖动结束并保存' : '开始仅录音';
    final String hint = canStop ? '向左拖动按钮，结束并保存'
        : state.state == SessionState.idle ? '点击按钮开始仅录音'
        : controller.busy ? '正在确认录音状态…' : '请先处理下方提示';

    final List<Widget> pages = <Widget>[
      _readingPage(key: const PageStorageKey<String>('recording-list-scroll'), children: <Widget>[
        Text('录音列表', style: text.headlineLarge),
        const SizedBox(height: 24),
        if (state.sessionId != null) ...<Widget>[
          Text('当前会话', style: text.titleMedium),
          const SizedBox(height: 12),
          SelectableText(state.sessionId!),
          const SizedBox(height: 12),
          Text('${_statusLabel(state)} · ${_duration(state.elapsedMs)}', style: text.titleLarge),
          const SizedBox(height: 12),
          Text('仅录音 · 音频保存在此设备', style: text.bodyLarge),
        ] else
          Text('当前没有进行中的录音', style: text.titleLarge),
        const SizedBox(height: 32),
        Text('历史记录尚未接入', style: text.titleLarge),
        const SizedBox(height: 12),
        Text('这里仅显示原生录音服务返回的当前会话，暂不提供历史录音列表或播放。', style: text.bodyLarge),
      ]),
      ListenableBuilder(
        listenable: _transcript,
        builder: (context, child) {
          final transcript = _transcript.transcript;
          if (transcript.segments.isNotEmpty || transcript.livePartialText != null) {
            return TranscriptReader(controller: _transcript);
          }
          return child!;
        },
        child: _readingPage(key: const PageStorageKey<String>('recording-transcript-scroll'), children: <Widget>[
        Text('只录下此刻，\n安心留在设备。', style: text.headlineLarge?.copyWith(height: 1.5)),
        const SizedBox(height: 28),
        Text('本地转写模型不可用', style: text.titleLarge),
        const SizedBox(height: 12),
        Text('当前尚未接入本地转写模型。你可以选择仅录音；已有数据不会因此删除，但历史浏览入口尚未接入。',
            style: text.bodyLarge?.copyWith(height: 1.8, color: colors.onSurfaceVariant)),
        const SizedBox(height: 16),
        Wrap(spacing: 8, runSpacing: 8, children: <Widget>[
          FilledButton.icon(
            key: const Key('model-only-recording'),
            onPressed: controller.canStart ? _start : null,
            icon: const Icon(Icons.mic),
            label: const Text('仅录音'),
          ),
          OutlinedButton.icon(
            key: const Key('model-reinstall-unavailable'),
            onPressed: null,
            icon: const Icon(Icons.download_outlined),
            label: const Text('重新安装（暂不可用）'),
          ),
          OutlinedButton.icon(
            key: const Key('model-rollback-unavailable'),
            onPressed: null,
            icon: const Icon(Icons.undo),
            label: const Text('回滚（暂不可用）'),
          ),
        ]),
        const SizedBox(height: 24),
        const Text('转写内容尚不可用；录音与 GAP 等已有证据不会因模型不可用而删除。'),
        const SizedBox(height: 24),
        EchoPress(child: OutlinedButton.icon(
          key: const Key('recording-stop-button'),
          onPressed: canStop ? controller.stop : null,
          icon: const Icon(Icons.stop_rounded),
          label: Text(controller.recoveryRequired ? '录音待恢复' : failedToSave ? '重试保存' : '结束并保存'),
        )),
        _errorPanel(state),
      ])),
      _readingPage(key: const PageStorageKey<String>('recording-settings-scroll'), children: <Widget>[
        Text('设置', style: text.headlineLarge),
        const SizedBox(height: 28),
        Text('当前模式', style: text.titleMedium),
        const SizedBox(height: 8),
        Text('仅录音', style: text.titleLarge),
        const SizedBox(height: 8),
        Text('本地转写模型尚未接入；没有可启用的转写开关。', style: text.bodyLarge),
        const SizedBox(height: 24),
        EchoPress(child: OutlinedButton.icon(
          key: const Key('recording-permission-settings'),
          onPressed: controller.busy ? null : controller.openSettings,
          icon: const Icon(Icons.mic_outlined),
          label: const Text('管理系统麦克风权限'),
        )),
        const SizedBox(height: 28),
        Text('外观与动效', style: text.titleMedium),
        const SizedBox(height: 12),
        Text('Material Design 3 · ${Theme.of(context).brightness == Brightness.dark ? '深色' : '浅色'}主题', style: text.bodyLarge),
        const SizedBox(height: 8),
        Text(motion.reduceMotion ? '已遵循系统：减少动态效果' : '动态效果已开启；遵循系统减少动态效果设置', style: text.bodyLarge),
      ]),
    ];

    return Scaffold(
      body: SafeArea(
        child: Column(children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
            child: Material(
              color: colors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(44),
              clipBehavior: Clip.antiAlias,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: LayoutBuilder(builder: (BuildContext context, BoxConstraints box) {
                  const double buttonSize = 64;
                  _trackWidth = math.max(0, box.maxWidth - buttonSize);
                  return SizedBox(height: buttonSize, child: Stack(
                    alignment: Alignment.center,
                    children: <Widget>[
                      Positioned(left: 8, right: buttonSize + 12,
                        child: EchoPress(scale: 0.996, child: AmplitudeStrip(
                          key: ValueKey<String?>(state.sessionId),
                          amplitudes: state.amplitudes,
                          frozen: controller.waveformFrozen,
                          height: 48,
                        )),
                      ),
                      Positioned(right: _dragOffset.clamp(0, _trackWidth), child: GestureDetector(
                        key: const Key('recording-drag-button'),
                        dragStartBehavior: DragStartBehavior.down,
                        behavior: HitTestBehavior.opaque,
                        onHorizontalDragStart: (DragStartDetails details) => _dragStartGesture(details, canStop),
                        onHorizontalDragUpdate: _dragUpdate,
                        onHorizontalDragCancel: () { _dragEnabled = false; _animateBack(0); },
                        onHorizontalDragEnd: (DragEndDetails details) => _dragEnd(details, canStop),
                        child: Semantics(
                          key: const Key('recording-main-semantics'),
                          excludeSemantics: true,
                          onTap: canMain ? _mainAction : null,
                          button: true, enabled: canMain, focusable: true, label: actionLabel,
                          child: EchoPress(borderRadius: BorderRadius.circular(32),
                            child: TweenAnimationBuilder<double>(
                              tween: Tween<double>(end: phase == 1 ? 22 : 32),
                              duration: motion.base,
                              curve: motion.geometryCurve,
                              builder: (BuildContext context, double radius, Widget? child) =>
                                  SizedBox.square(dimension: buttonSize, child: FilledButton(
                                    key: const Key('recording-main-action'),
                                    style: FilledButton.styleFrom(
                                      padding: EdgeInsets.zero,
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius)),
                                    ),
                                    onPressed: canMain ? _mainAction : null,
                                    child: child!,
                                  )),
                              child: RepaintBoundary(
                                key: const Key('recording-shape-image'),
                                child: RecordingActionShape(phase: phase,
                                    color: canMain ? colors.onPrimary : colors.onSurface.withValues(alpha: 0.38)),
                              ),
                            ),
                          ),
                        ),
                      )),
                    ],
                  ));
                }),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 12, 28, 0),
            child: Wrap(alignment: WrapAlignment.spaceBetween, spacing: 20, runSpacing: 4,
              children: <Widget>[
                Semantics(liveRegion: true, child: _changingText(_statusLabel(state),
                    key: const Key('recording-status'), style: text.bodyMedium)),
                Text(_duration(state.elapsedMs), key: const Key('recording-duration'),
                    style: text.bodyMedium?.copyWith(fontFeatures: const <FontFeature>[FontFeature.tabularFigures()])),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 8, 28, 0),
            child: Align(alignment: Alignment.centerLeft,
              child: _changingText(hint, style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant))),
          ),
          Expanded(child: Stack(fit: StackFit.expand, children: <Widget>[
            for (int i = 0; i < pages.length; i++)
              Offstage(
                offstage: !_visiblePages.contains(i),
                child: IgnorePointer(ignoring: i != _destination,
                  child: ExcludeSemantics(excluding: i != _destination,
                    child: TickerMode(enabled: _visiblePages.contains(i),
                      child: AnimatedOpacity(
                        opacity: i == _destination ? 1 : 0,
                        duration: motion.fade,
                        curve: motion.fadeCurve,
                        onEnd: () {
                          if (mounted && i != _destination) {
                            setState(() => _visiblePages.remove(i));
                          }
                        },
                        child: AnimatedSlide(
                          offset: i == _destination || motion.reduceMotion ? Offset.zero : Offset(i < _destination ? -0.035 : 0.035, 0),
                          duration: motion.base,
                          curve: motion.geometryCurve,
                          child: pages[i],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ])),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(40),
              child: EchoPress(
                scale: 0.99,
                child: NavigationBar(
                  selectedIndex: _destination,
                  onDestinationSelected: (int value) => setState(() {
                    if (motion.reduceMotion) _visiblePages.clear();
                    _visiblePages.add(value);
                    _destination = value;
                  }),
                  animationDuration: motion.base,
                  destinations: const <Widget>[
                    NavigationDestination(key: Key('recording-nav-list'), icon: Icon(Icons.view_list_outlined), selectedIcon: Icon(Icons.view_list_rounded), label: '列表'),
                    NavigationDestination(key: Key('recording-nav-record'), icon: Icon(Icons.mic_none_rounded), selectedIcon: Icon(Icons.mic_rounded), label: '录音'),
                    NavigationDestination(key: Key('recording-nav-settings'), icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings_rounded), label: '设置'),
                  ],
                ),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}
