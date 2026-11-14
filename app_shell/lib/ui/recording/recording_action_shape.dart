import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../motion/echo_motion.dart';

/// 同一组轮廓节点连续变形：录制圆点 → 暂停双条 → 继续三角。
/// 控制器从当前值反向，快速暂停/继续不会重置形状。
class RecordingActionShape extends StatefulWidget {
  const RecordingActionShape({super.key, required this.phase, required this.color});

  final double phase;
  final Color color;

  @override
  State<RecordingActionShape> createState() => _RecordingActionShapeState();
}

class _RecordingActionShapeState extends State<RecordingActionShape>
    with SingleTickerProviderStateMixin {
  late final AnimationController _phase = AnimationController(
    vsync: this, lowerBound: 0, upperBound: 2, value: widget.phase,
  );

  void _update() {
    final EchoMotion motion = EchoMotion.of(context);
    if (motion.reduceMotion) {
      _phase.stop();
      _phase.value = widget.phase;
    } else if (_phase.value != widget.phase) {
      _phase.animateTo(widget.phase,
          duration: motion.base, curve: motion.geometryCurve);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _update();
  }

  @override
  void didUpdateWidget(covariant RecordingActionShape oldWidget) {
    super.didUpdateWidget(oldWidget);
    _update();
  }

  @override
  Widget build(BuildContext context) => CustomPaint(
    key: const Key('recording-action-shape'),
    size: const Size.square(28),
    painter: _ActionPainter(_phase, widget.color),
  );

  @override
  void dispose() {
    _phase.dispose();
    super.dispose();
  }
}

final class _ActionPainter extends CustomPainter {
  _ActionPainter(this.phase, this.color) : super(repaint: phase);

  final Animation<double> phase;
  final Color color;
  final Paint _paint = Paint();
  final Path _path = Path();

  // 只在首次使用时采样；绘制时复用路径，不逐帧创建节点集合。
  static final List<List<Offset>> _contours = <List<Offset>>[
    for (int side = 0; side < 2; side++)
      <Offset>[
        for (int i = 0; i < 16; i++)
          Offset(math.cos(math.pi / 2 + side * math.pi + i * math.pi / 15) * 0.7,
              math.sin(math.pi / 2 + side * math.pi + i * math.pi / 15) * 0.7),
      ],
    _sample(const <Offset>[Offset(-0.65, -0.75), Offset(-0.22, -0.75), Offset(-0.22, 0.75), Offset(-0.65, 0.75)]),
    _sample(const <Offset>[Offset(0.22, 0.75), Offset(0.22, -0.75), Offset(0.65, -0.75), Offset(0.65, 0.75)]),
    _sample(const <Offset>[Offset(-0.55, -0.8), Offset(0, -0.45), Offset(0, 0.45), Offset(-0.55, 0.8)]),
    _sample(const <Offset>[Offset(0, 0.45), Offset(0, -0.45), Offset(0.75, 0), Offset(0.75, 0)]),
  ];

  static List<Offset> _sample(List<Offset> vertices) => <Offset>[
    for (int i = 0; i < 16; i++)
      Offset.lerp(vertices[i ~/ 4], vertices[(i ~/ 4 + 1) % 4], i % 4 / 4)!,
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final double value = phase.value;
    final int start = value < 1 ? 0 : 2;
    final double t = value < 1 ? value : value - 1;
    final double half = math.min(size.width, size.height) / 2;
    _paint.color = color;
    _path.reset();
    for (int side = 0; side < 2; side++) {
      final List<Offset> from = _contours[start + side];
      final List<Offset> to = _contours[start + 2 + side];
      for (int i = 0; i < 16; i++) {
        final double x = size.width / 2 + (from[i].dx + (to[i].dx - from[i].dx) * t) * half;
        final double y = size.height / 2 + (from[i].dy + (to[i].dy - from[i].dy) * t) * half;
        if (i == 0) {
          _path.moveTo(x, y);
        } else {
          _path.lineTo(x, y);
        }
      }
      _path.close();
    }
    canvas.drawPath(_path, _paint);
  }

  @override
  bool shouldRepaint(_ActionPainter oldDelegate) => oldDelegate.color != color;
}
