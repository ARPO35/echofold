/// 录音页顶部振幅条。
///
/// 只使用原生上报的真实 RMS 采样与 `capturedEndMs`：最新样本从右侧进入，历史样本
/// 向左移出。没有随机数，也没有与音频时间无关的装饰动画；暂停/阻塞时冻结并降低强调。
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../app/app_contract.dart';

/// 顶部实时振幅条。
class AmplitudeStrip extends StatelessWidget {
  const AmplitudeStrip({
    super.key,
    required this.amplitudes,
    required this.frozen,
    this.window = const Duration(seconds: 8),
    this.height = 72,
  });

  /// 按 `monoNs` 递增的样本；允许为空（新会话/空闲）。
  final List<AmplitudePoint> amplitudes;

  /// 冻结时保留最后画面但降低强调。
  final bool frozen;

  /// 横轴显示的时间窗口。
  final Duration window;

  final double height;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final Color color = frozen ? scheme.onSurfaceVariant : scheme.error;
    return Semantics(
      label: frozen ? '振幅已冻结' : '实时振幅',
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            return CustomPaint(
              size: Size(constraints.maxWidth, height),
              painter: _AmplitudePainter(
                points: amplitudes,
                windowMs: math.max(1, window.inMilliseconds),
                color: color,
                frozen: frozen,
              ),
            );
          },
        ),
      ),
    );
  }
}

final class _AmplitudePainter extends CustomPainter {
  const _AmplitudePainter({
    required this.points,
    required this.windowMs,
    required this.color,
    required this.frozen,
  });

  final List<AmplitudePoint> points;
  final int windowMs;
  final Color color;
  final bool frozen;

  @override
  void paint(Canvas canvas, Size size) {
    final double mid = size.height / 2;
    final Paint baseline = Paint()
      ..color = color.withValues(alpha: frozen ? 0.12 : 0.22)
      ..strokeWidth = 1;
    canvas.drawLine(Offset(0, mid), Offset(size.width, mid), baseline);

    if (points.isEmpty) {
      return;
    }

    // 用单调时钟找最新样本；横轴位置仍按音频实际时间 `capturedEndMs` 计算。
    AmplitudePoint latest = points.first;
    for (final AmplitudePoint point in points) {
      if (point.monoNs > latest.monoNs) {
        latest = point;
      }
    }

    final Paint bar = Paint()
      ..strokeCap = StrokeCap.round
      ..strokeWidth = 2.5;

    for (final AmplitudePoint point in points) {
      final int ageMs = latest.capturedEndMs - point.capturedEndMs;
      if (ageMs < 0 || ageMs > windowMs) {
        continue;
      }
      final double ratio = ageMs / windowMs;
      final double x = size.width * (1 - ratio);
      final double rms = point.rms.isFinite ? point.rms.clamp(0.0, 1.0) : 0;
      final double peak = point.peak.isFinite ? point.peak.clamp(0.0, 1.0) : rms;
      final double amplitude = math.max(rms, peak * 0.6);
      final double halfHeight = 1.5 + amplitude * (size.height * 0.42);
      final double alpha = frozen ? 0.32 : (0.35 + 0.65 * (1 - ratio)).clamp(0.0, 1.0);
      bar.color = color.withValues(alpha: alpha);
      canvas.drawLine(Offset(x, mid - halfHeight), Offset(x, mid + halfHeight), bar);
    }
  }

  @override
  bool shouldRepaint(_AmplitudePainter oldDelegate) =>
      !identical(oldDelegate.points, points) ||
      oldDelegate.windowMs != windowMs ||
      oldDelegate.color != color ||
      oldDelegate.frozen != frozen;
}
