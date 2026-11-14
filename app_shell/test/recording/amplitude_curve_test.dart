import 'dart:math' as math;
import 'dart:ui';

import 'package:echofold_app/app/app_contract.dart';
import 'package:echofold_app/ui/recording/amplitude_curve.dart';
import 'package:flutter_test/flutter_test.dart';

AmplitudePoint _point(int timeMs, double rms, {double peak = 0}) =>
    AmplitudePoint(
      monoNs: timeMs * 1000000,
      capturedEndMs: timeMs,
      rms: rms,
      peak: peak,
    );

List<Offset> _samplePath(Path path) {
  final List<Offset> points = <Offset>[];
  for (final PathMetric metric in path.computeMetrics()) {
    final int steps = math.max(1, (metric.length / 0.5).ceil());
    for (int step = 0; step <= steps; step++) {
      points.add(metric
          .getTangentForOffset(metric.length * step / steps)!
          .position);
    }
  }
  return points;
}

void _expectBounds(Path path, Rect expected) {
  final Rect actual = path.getBounds();
  expect(actual.left, closeTo(expected.left, 0.001));
  expect(actual.top, closeTo(expected.top, 0.001));
  expect(actual.right, closeTo(expected.right, 0.001));
  expect(actual.bottom, closeTo(expected.bottom, 0.001));
}

void main() {
  const double height = 80;

  test('静音包络全程落在中线', () {
    final Path path = buildAmplitudeEnvelope(
      <AmplitudePoint>[_point(0, 0), _point(100, 0), _point(200, 0)],
      pixelsPerMs: 1,
      height: height,
    );
    final List<Offset> samples = _samplePath(path);

    expect(samples, isNotEmpty);
    expect(samples.every((Offset p) => (p.dy - height / 2).abs() < 0.001),
        isTrue);
    _expectBounds(path, const Rect.fromLTRB(0, 40, 200, 40));
  });

  test('固定 dBFS 尺度不对安静批次自动增益，峰值按 0.6 加权', () {
    for (final (double rms, double peak, double radius) in
        <(double, double, double)>[
      (0.01, 0, height / 6),
      (0.1, 0, height / 3),
      (0, 1 / 6, height / 3),
      (1, 0, height / 2),
      (10, 0, height / 2),
      (0.001, 0, 0),
      (0.0001, 0, 0),
    ]) {
      final Path path = buildAmplitudeEnvelope(
        <AmplitudePoint>[
          _point(0, rms, peak: peak),
          _point(100, rms, peak: peak),
        ],
        pixelsPerMs: 1,
        height: height,
      );

      _expectBounds(path, Rect.fromLTRB(0, 40 - radius, 100, 40 + radius));
    }
  });

  test('真实时间间距将非等距极值放在对应横坐标', () {
    final Path path = buildAmplitudeEnvelope(
      <AmplitudePoint>[
        _point(100, 0),
        _point(200, 1),
        _point(500, 0),
      ],
      pixelsPerMs: 0.5,
      height: height,
    );

    _expectBounds(path, const Rect.fromLTRB(0, 0, 200, height));
    expect(path.computeMetrics().single.isClosed, isTrue);
    expect(path.contains(const Offset(50, 0.01)), isTrue);
    expect(path.contains(const Offset(50, 79.99)), isTrue);
    expect(path.contains(const Offset(1, 1)), isFalse);
    expect(path.contains(const Offset(199, 1)), isFalse);
  });

  test('急变与非等距采样的曲线每段不越过端点幅度且上下镜像', () {
    const List<int> times = <int>[0, 100, 101, 500, 600];
    const List<double> rms = <double>[0.001, 0.1, 0.01, 1, 0.01];
    const List<double> radii = <double>[0, height / 3, height / 6, 40, height / 6];
    final Path path = buildAmplitudeEnvelope(
      <AmplitudePoint>[
        for (int i = 0; i < times.length; i++) _point(times[i], rms[i]),
      ],
      pixelsPerMs: 1,
      height: height,
    );
    final List<Offset> samples = _samplePath(path);

    expect(samples, isNotEmpty);
    for (final Offset point in samples) {
      expect(point.dx.isFinite && point.dy.isFinite, isTrue);
      expect(point.dy, inInclusiveRange(-0.001, height + 0.001));
      // 两端闭合边跨过中线，不属于插值区间。
      if (point.dx <= 0.001 || point.dx >= 599.999) continue;
      final int segment = times.indexWhere((int time) => time > point.dx) - 1;
      final double radius = (point.dy - height / 2).abs();
      expect(radius, greaterThanOrEqualTo(
          math.min(radii[segment], radii[segment + 1]) - 0.01));
      expect(radius, lessThanOrEqualTo(
          math.max(radii[segment], radii[segment + 1]) + 0.01));
    }
    for (double x = 5; x < 600; x += 17) {
      for (double y = 1; y < height / 2; y += 3) {
        expect(path.contains(Offset(x, y)),
            path.contains(Offset(x, height - y)));
      }
    }
  });

  test('负值和非有限分量静音化，仍保留有效分量和首样本时间原点', () {
    final Path silence = buildAmplitudeEnvelope(
      <AmplitudePoint>[
        _point(100, double.nan, peak: double.infinity),
        _point(200, -1, peak: double.negativeInfinity),
        _point(300, 0),
      ],
      pixelsPerMs: 0.5,
      height: height,
    );
    _expectBounds(silence, const Rect.fromLTRB(0, 40, 100, 40));
    expect(_samplePath(silence).every((Offset p) => p.dy == height / 2), isTrue);

    final Path mixed = buildAmplitudeEnvelope(
      <AmplitudePoint>[
        _point(100, double.nan),
        _point(200, 0.1, peak: double.infinity),
        _point(300, double.nan, peak: 1 / 6),
      ],
      pixelsPerMs: 0.5,
      height: height,
    );
    _expectBounds(mixed,
        const Rect.fromLTRB(0, height / 6, 100, height * 5 / 6));
    expect(_samplePath(mixed).every((Offset p) => p.dx.isFinite && p.dy.isFinite),
        isTrue);
  });

  test('重复时间采用最后样本且不改变首样本时间原点', () {
    final Path path = buildAmplitudeEnvelope(
      <AmplitudePoint>[
        _point(100, 1),
        _point(100, 0),
        _point(200, 1),
        _point(200, 0.1),
        _point(300, 0),
      ],
      pixelsPerMs: 0.5,
      height: height,
    );

    _expectBounds(path,
        const Rect.fromLTRB(0, height / 6, 100, height * 5 / 6));
    expect(path.contains(const Offset(50, 14)), isTrue);
    expect(path.contains(const Offset(1, 1)), isFalse);
    expect(_samplePath(path).every((Offset p) => p.dx.isFinite && p.dy.isFinite),
        isTrue);
  });

  test('零个、一个或全在同一时刻的样本不生成胶囊', () {
    for (final List<AmplitudePoint> points in <List<AmplitudePoint>>[
      <AmplitudePoint>[],
      <AmplitudePoint>[_point(0, 0.5)],
      <AmplitudePoint>[_point(100, 1), _point(100, 0.5)],
    ]) {
      final Path path = buildAmplitudeEnvelope(
        points,
        pixelsPerMs: 1,
        height: height,
      );
      expect(path.computeMetrics(), isEmpty);
    }
  });

  test('无效绘制尺寸不产生非有限路径', () {
    for (final (double scale, double size) in <(double, double)>[
      (0, height),
      (-1, height),
      (double.nan, height),
      (double.infinity, height),
      (1, 0),
      (1, -1),
      (1, double.nan),
      (1, double.infinity),
      (double.maxFinite, height),
    ]) {
      final Path path = buildAmplitudeEnvelope(
        <AmplitudePoint>[_point(0, 1), _point(100, 1)],
        pixelsPerMs: scale,
        height: size,
      );
      expect(path.computeMetrics(), isEmpty);
    }
  });
}
