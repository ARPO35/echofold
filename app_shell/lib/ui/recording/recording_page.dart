import 'dart:async';
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
    unawaited(_connectAndRefreshModel());
  }

  /// 连接成功后立即异步预读模型状态：打开应用即触发，不等用户点击。
  Future<void> _connectAndRefreshModel() async {
    final RecordingController controller = widget.controller;
    await controller.connect();
    if (!mounted || controller != widget.controller || !controller.connected) return;
    unawaited(controller.refreshModel());
  }

  @override
  void didUpdateWidget(covariant RecordingPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
      unawaited(_connectAndRefreshModel());
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
    if (state == AppLifecycleState.resumed) {
      unawaited(_connectAndRefreshModel());
    }
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

  Future<void> _importModel() async {
    if (_confirming || !widget.controller.canImportModel) return;
    final RecordingController controller = widget.controller;
    setState(() => _confirming = true);
    try {
      final confirmed = await showEchoDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('导入本地模型'),
          content: const Text('请选择包含 artifacts/ 的 X-ASR 模型根目录。固定四文件共约 169 MB，导入另需约 510 MB 暂存空间。校验大小与 SHA-256，源目录保持不变。此导入由你确认，未经发布者签名。'),
          actions: <Widget>[
            EchoPress(child: TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消'))),
            EchoPress(child: FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('确认并选择目录'))),
          ],
        ),
      );
      if (confirmed == true && mounted && controller == widget.controller) {
        await controller.importModel();
      }
    } finally {
      if (mounted) setState(() => _confirming = false);
    }
  }

  Future<void> _start() async {
    if (_confirming || !widget.controller.canStart) return;
    if (widget.controller.modelReady) {
      await widget.controller.start();
      return;
    }
    final RecordingController controller = widget.controller;
    setState(() => _confirming = true);
    try {
      final bool? confirmed = await showEchoDialog<bool>(
        context: context,
        builder: (BuildContext context) => AlertDialog(
          title: const Text('仅录音'),
          content: const Text('本地转写模型尚不可用。开始后只采集并保存音频，不生成转写。'),
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
      if (confirmed == true && mounted && controller == widget.controller) {
        await controller.startOnlyRecording();
      }
    } finally {
      if (mounted) setState(() => _confirming = false);
    }
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
        SessionState.recording => widget.controller.onlyRecording ? '正在录音' : '正在录音与转写',
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

  Widget _sessionActions(RecordingUiState state) {
    final RecordingController controller = widget.controller;
    final bool failedToSave = state.state == SessionState.failed && state.sessionId != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        EchoPress(child: OutlinedButton.icon(
          key: const Key('recording-stop-button'),
          onPressed: controller.canStop ? controller.stop : null,
          icon: const Icon(Icons.stop_rounded),
          label: Text(controller.recoveryRequired ? '录音待恢复' : failedToSave ? '重试保存' : '结束并保存'),
        )),
        _errorPanel(state),
      ],
    );
  }

  /// 中央底部会话动作：有会话（含失败重试）时显示，纯空闲且无错误时不显示。
  bool get _showSessionActions {
    final RecordingUiState state = widget.controller.state;
    if (state.sessionId != null) return true;
    return state.state != SessionState.idle ||
        widget.controller.state.failure != null ||
        state.storageWarning != null;
  }

  @override
  Widget build(BuildContext context) {
    final RecordingController controller = widget.controller;
    final RecordingUiState state = controller.state;
    final bool canStop = controller.canStop;
    final bool canMain = !_confirming && (controller.canStart || controller.canPause || controller.canResume);
    final bool hasSession = state.sessionId != null;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final EchoMotion motion = EchoMotion.of(context);
    final double phase = state.state == SessionState.recording ? 1 : state.state == SessionState.paused ? 2 : 0;
    final String startLabel = controller.modelReady ? '开始录音与转写' : '开始仅录音';
    final String modeLabel = state.sessionId != null
        ? controller.onlyRecording ? '仅录音' : '录音与本地转写'
        : controller.modelReady ? '录音与本地转写' : '仅录音（开始前需确认）';
    final String actionLabel = state.state == SessionState.recording
        ? '暂停录音；向左拖动结束并保存'
        : state.state == SessionState.paused
            ? '继续录音；向左拖动结束并保存' : startLabel;
    final String hint = canStop ? '向左拖动按钮，结束并保存'
        : state.state == SessionState.idle ? '点击按钮$startLabel'
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
          Text('$modeLabel · 音频保存在此设备', style: text.bodyLarge),
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
          if (transcript.segments.isNotEmpty || transcript.livePartialText != null || transcript.failure != null) {
            return Column(children: <Widget>[
              Expanded(child: TranscriptReader(controller: _transcript)),
              if (hasSession) ConstrainedBox(
                constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.3),
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
                  child: _sessionActions(state),
                ),
              ),
            ]);
          }
          return child!;
        },
        // 中央仅承载真实 ASR 结果；无结果时留空，仅保留必要会话动作。
        child: _readingPage(key: const PageStorageKey<String>('recording-transcript-scroll'),
            children: <Widget>[
              if (_showSessionActions) _sessionActions(state),
            ]),
      ),
      _readingPage(key: const PageStorageKey<String>('recording-settings-scroll'), children: <Widget>[
        Text('设置', style: text.headlineLarge),
        const SizedBox(height: 28),
        Text('本地转写模型', style: text.titleMedium),
        const SizedBox(height: 8),
        Text(controller.modelMessage, key: const Key('recording-model-status'),
            style: text.bodyLarge?.copyWith(color: colors.onSurfaceVariant)),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: <Widget>[
          EchoPress(child: OutlinedButton.icon(
            key: const Key('model-import'),
            onPressed: !_confirming && controller.canImportModel ? _importModel : null,
            icon: const Icon(Icons.folder_open),
            label: const Text('导入本地模型'),
          )),
          EchoPress(child: TextButton.icon(
            key: const Key('model-refresh'),
            onPressed: !_confirming && !controller.busy ? controller.refreshModel : null,
            icon: const Icon(Icons.refresh),
            label: const Text('刷新模型状态'),
          )),
        ]),
        const SizedBox(height: 28),
        EchoPress(child: OutlinedButton.icon(
          key: const Key('recording-permission-settings'),
          onPressed: controller.busy ? null : controller.openSettings,
          icon: const Icon(Icons.mic_outlined),
          label: const Text('管理系统麦克风权限'),
        )),
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
