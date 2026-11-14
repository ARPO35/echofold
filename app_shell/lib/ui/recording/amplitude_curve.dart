import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui';

import '../../app/app_contract.dart';

/// 生成围绕中线、上下镜像的闭合振幅包络。
///
/// 横坐标为 `(capturedEndMs - points.first.capturedEndMs) * pixelsPerMs`。
/// 振幅使用固定的 -60..0 dBFS；重复时间保留最后一个样本，不移动时间原点。
Path buildAmplitudeEnvelope(
  List<AmplitudePoint> points, {
  required double pixelsPerMs,
  required double height,
}) {
  final Path path = Path();
  if (points.length < 2 ||
      !pixelsPerMs.isFinite ||
      pixelsPerMs <= 0 ||
      !height.isFinite ||
      height <= 0) {
    return path;
  }

  // 每个节点依次保存 x、上边界 y、出段的两个控制点 y，避免逐点分配对象。
  final Float64List nodes = Float64List(points.length * 4);
  final int firstTime = points.first.capturedEndMs;
  final double halfHeight = height / 2;
  int count = 0;
  for (final AmplitudePoint point in points) {
    final double x = (point.capturedEndMs - firstTime) * pixelsPerMs;
    if (!x.isFinite) return path;
    final double magnitude = math.max(
      _finiteMagnitude(point.rms),
      _finiteMagnitude(point.peak) * 0.6,
    );
    final double db = magnitude <= 0
        ? -60
        : 20 * math.log(magnitude) / math.ln10;
    final double level = ((db + 60) / 60).clamp(0.0, 1.0);
    // 列表已按采样时间排序；相同时刻覆盖节点，避免除以零。
    if (count > 0 && x == nodes[(count - 1) * 4]) count--;
    final int index = count * 4;
    nodes[index] = x;
    nodes[index + 1] = halfHeight - level * halfHeight;
    count++;
  }
  if (count < 2) return path;

  // 相邻割线异号时切线归零；否则取较小斜率，保持 C1 连续和单调。
  double startSlope = _slope(nodes, 0, count);
  for (int i = 0; i < count - 1; i++) {
    final int index = i * 4;
    final double startY = nodes[index + 1];
    final double endY = nodes[index + 5];
    final double thirdWidth = (nodes[index + 4] - nodes[index]) / 3;
    final double endSlope = _slope(nodes, i + 1, count);
    final double low = math.min(startY, endY);
    final double high = math.max(startY, endY);
    // 显式限制控制点，利用 Bézier 凸包性质保证每段不超过端点范围。
    nodes[index + 2] = (startY + startSlope * thirdWidth).clamp(low, high);
    nodes[index + 3] = (endY - endSlope * thirdWidth).clamp(low, high);
    startSlope = endSlope;
  }

  path.moveTo(nodes[0], nodes[1]);
  for (int i = 0; i < count - 1; i++) {
    final int index = i * 4;
    final double thirdWidth = (nodes[index + 4] - nodes[index]) / 3;
    path.cubicTo(
      nodes[index] + thirdWidth,
      nodes[index + 2],
      nodes[index + 4] - thirdWidth,
      nodes[index + 3],
      nodes[index + 4],
      nodes[index + 5],
    );
  }
  final int last = (count - 1) * 4;
  path.lineTo(nodes[last], height - nodes[last + 1]);
  for (int i = count - 2; i >= 0; i--) {
    final int index = i * 4;
    final double thirdWidth = (nodes[index + 4] - nodes[index]) / 3;
    // 复用上边界的控制点，以逆序镜像，保证两侧完全对称。
    path.cubicTo(
      nodes[index + 4] - thirdWidth,
      height - nodes[index + 3],
      nodes[index] + thirdWidth,
      height - nodes[index + 2],
      nodes[index],
      height - nodes[index + 1],
    );
  }
  path.close();
  return path;
}

double _slope(Float64List nodes, int index, int count) {
  final int offset = index * 4;
  if (index == 0) {
    return (nodes[5] - nodes[1]) / (nodes[4] - nodes[0]);
  }
  final double before = (nodes[offset + 1] - nodes[offset - 3]) /
      (nodes[offset] - nodes[offset - 4]);
  if (index == count - 1) return before;
  final double after = (nodes[offset + 5] - nodes[offset + 1]) /
      (nodes[offset + 4] - nodes[offset]);
  if (before == 0 || after == 0 || before.sign != after.sign) return 0;
  return before.sign * math.min(before.abs(), after.abs());
}

double _finiteMagnitude(double value) =>
    value.isFinite && value > 0 ? value : 0;
