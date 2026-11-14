/// 录音页顶部振幅条。
///
/// 只用原生上报的真实振幅样本；游标在样本批次之间平滑推进，但不会越过真实数据。
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../../app/app_contract.dart';
import '../motion/echo_motion.dart';
import 'amplitude_curve.dart';

/// 顶部实时振幅曲线。
class AmplitudeStrip extends StatefulWidget {
  const AmplitudeStrip({
    super.key,
    required this.amplitudes,
    required this.frozen,
    this.window = const Duration(seconds: 8),
    this.height = 72,
  });

  final List<AmplitudePoint> amplitudes;
  final bool frozen;
  final Duration window;
  final double height;

  @override
  State<AmplitudeStrip> createState() => _AmplitudeStripState();
}

class _AmplitudeStripState extends State<AmplitudeStrip>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  static const int _displayLagMs = 100;

  late final Ticker _ticker;
  final ValueNotifier<double> _cursorMs = ValueNotifier<double>(0);
  Path _path = Path();
  Size _pathSize = Size.zero;
  bool _reduceMotion = false;
  bool _tickerModeEnabled = true;
  bool _appResumed = true;
  double _targetCursorMs = 0;
  Duration _lastTickElapsed = Duration.zero;
  int? _firstCapturedMs;
  int? _latestCapturedMs;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker(_onTick);
    final AppLifecycleState? lifecycle = WidgetsBinding.instance.lifecycleState;
    _appResumed = lifecycle == null || lifecycle == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    _updateSamples(reset: true);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reduceMotion = EchoMotion.of(context).reduceMotion;
    _tickerModeEnabled = TickerMode.valuesOf(context).enabled;
    _syncTicker();
  }

  @override
  void didUpdateWidget(covariant AmplitudeStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.amplitudes, widget.amplitudes)) {
      _updateSamples();
      _rebuildPath(_pathSize);
    }
    if (oldWidget.window != widget.window || oldWidget.height != widget.height) {
      _rebuildPath(_pathSize);
    }
    if (oldWidget.frozen != widget.frozen) _syncTicker();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _appResumed = state == AppLifecycleState.resumed;
    _syncTicker();
  }

  void _updateSamples({bool reset = false}) {
    final List<AmplitudePoint> points = widget.amplitudes;
    if (points.isEmpty) {
      _firstCapturedMs = null;
      _latestCapturedMs = null;
      _targetCursorMs = 0;
      _cursorMs.value = 0;
      _syncTicker();
      return;
    }

    final int first = points.first.capturedEndMs;
    final int latest = points.last.capturedEndMs;
    reset = reset ||
        _latestCapturedMs == null ||
        first < _firstCapturedMs! ||
        latest < _latestCapturedMs!;
    _firstCapturedMs = first;
    _latestCapturedMs = latest;
    _targetCursorMs = latest.toDouble();
    if (reset) {
      // 保留一批真实采样余量；后续按帧推进，断流后最多追到最后真实样本。
      _ticker.stop();
      _cursorMs.value = (latest - _displayLagMs).clamp(first, latest).toDouble();
    }
    _syncTicker();
  }

  void _onTick(Duration elapsed) {
    final double current = _cursorMs.value;
    final double deltaMs = (elapsed - _lastTickElapsed).inMicroseconds / 1000;
    _lastTickElapsed = elapsed;
    final double next = (current + deltaMs).clamp(current, _targetCursorMs);
    if (next != current) _cursorMs.value = next;
    if (next >= _targetCursorMs) _ticker.stop();
  }

  void _syncTicker() {
    if (!mounted) return;
    if (_reduceMotion && !widget.frozen && _tickerModeEnabled && _appResumed) {
      _cursorMs.value = _targetCursorMs;
    }
    final bool canAnimate = !widget.frozen &&
        !_reduceMotion &&
        _tickerModeEnabled &&
        _appResumed &&
        _cursorMs.value < _targetCursorMs;
    if (canAnimate) {
      if (!_ticker.isActive) {
        // 每次恢复都从零累计；暂停及后台经过的墙钟时间不参与推进。
        _lastTickElapsed = Duration.zero;
        _ticker.start();
      }
    } else if (_ticker.isActive) {
      _ticker.stop();
    }
  }

  void _rebuildPath(Size size) {
    if (!size.width.isFinite ||
        !size.height.isFinite ||
        size.width <= 0 ||
        size.height <= 0) {
      _path = Path();
      _pathSize = size;
      return;
    }
    final int windowMs = widget.window.inMilliseconds.clamp(1, 1 << 31);
    _path = buildAmplitudeEnvelope(
      widget.amplitudes,
      pixelsPerMs: size.width / windowMs,
      height: size.height,
    );
    _pathSize = size;
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final Color color = widget.frozen ? scheme.onSurfaceVariant : scheme.error;
    final double height = widget.height.isFinite && widget.height > 0
        ? widget.height
        : 0;
    return Semantics(
      label: widget.frozen ? '振幅已冻结' : '实时振幅',
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            final double width = constraints.hasBoundedWidth
                ? constraints.maxWidth
                : 0;
            final Size size = Size(width, height);
            if (_pathSize != size) _rebuildPath(size);
            return RepaintBoundary(
              child: ClipRect(
                child: CustomPaint(
                  size: size,
                  painter: _AmplitudePainter(
                    path: _path,
                    cursor: _cursorMs,
                    firstCapturedMs: _firstCapturedMs,
                    windowMs: widget.window.inMilliseconds,
                    color: color,
                    frozen: widget.frozen,
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ticker.dispose();
    _cursorMs.dispose();
    super.dispose();
  }
}

final class _AmplitudePainter extends CustomPainter {
  _AmplitudePainter({
    required this.path,
    required this.cursor,
    required this.firstCapturedMs,
    required this.windowMs,
    required this.color,
    required this.frozen,
  }) : baseline = (Paint()
         ..color = color.withValues(alpha: frozen ? 0.12 : 0.22)
         ..strokeWidth = 1),
       envelope = (Paint()
         ..color = color.withValues(alpha: frozen ? 0.32 : 0.9)
         ..style = PaintingStyle.fill),
       super(repaint: cursor);

  final Path path;
  final ValueListenable<double> cursor;
  final int? firstCapturedMs;
  final int windowMs;
  final Color color;
  final bool frozen;
  final Paint baseline;
  final Paint envelope;

  @override
  void paint(Canvas canvas, Size size) {
    final double mid = size.height / 2;
    canvas.drawLine(Offset(0, mid), Offset(size.width, mid), baseline);
    if (firstCapturedMs == null) return;

    final double pixelsPerMs = size.width / windowMs.clamp(1, 1 << 31);
    final double translation =
        size.width - (cursor.value - firstCapturedMs!) * pixelsPerMs;
    canvas.save();
    canvas.translate(translation, 0);
    canvas.drawPath(path, envelope);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_AmplitudePainter oldDelegate) =>
      !identical(oldDelegate.path, path) ||
      oldDelegate.firstCapturedMs != firstCapturedMs ||
      oldDelegate.windowMs != windowMs ||
      oldDelegate.color != color ||
      oldDelegate.frozen != frozen;
}
