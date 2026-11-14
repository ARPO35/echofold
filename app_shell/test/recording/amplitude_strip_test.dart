import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:echofold_app/app/app_contract.dart';
import 'package:echofold_app/ui/recording/amplitude_strip.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

List<AmplitudePoint> _points(int latestMs) => <AmplitudePoint>[
  for (int time = 0; time <= latestMs; time += 20)
    AmplitudePoint(
      monoNs: time * 1000000,
      capturedEndMs: time,
      rms: time % 60 == 0 ? 0.2 : 0.8,
      peak: 0.8,
    ),
];

Future<void> _mount(
  WidgetTester tester,
  List<AmplitudePoint> points, {
  bool frozen = false,
  bool reduceMotion = false,
  bool tickerEnabled = true,
  String session = 'session-a',
}) async {
  await tester.pumpWidget(MaterialApp(
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduceMotion),
      child: TickerMode(
        enabled: tickerEnabled,
        child: Center(
          child: SizedBox(
            width: 400,
            child: AmplitudeStrip(
              key: ValueKey<String>(session),
              amplitudes: points,
              frozen: frozen,
              window: const Duration(seconds: 1),
            ),
          ),
        ),
      ),
    ),
  ));
}

Future<Uint8List> _pixels(WidgetTester tester) async {
  final Uint8List? pixels = await tester.runAsync<Uint8List>(() async {
    final Finder boundaryFinder = find.descendant(
      of: find.byType(AmplitudeStrip),
      matching: find.byType(RepaintBoundary),
    );
    final RenderRepaintBoundary boundary =
        tester.renderObject<RenderRepaintBoundary>(boundaryFinder);
    final ui.Image image = await boundary.toImage(pixelRatio: 1);
    try {
      final ByteData data = (await image.toByteData(
        format: ui.ImageByteFormat.rawRgba,
      ))!;
      return data.buffer.asUint8List();
    } finally {
      image.dispose();
    }
  });
  expect(pixels, isNotNull);
  return pixels!;
}

// 在基线之外查包络左缘；只比较位置，冻结状态的配色与透明度不会影响判断。
Future<int> _leftEdge(WidgetTester tester) async {
  final Uint8List bytes = await _pixels(tester);
  for (int x = 0; x < 400; x++) {
    if (bytes[(18 * 400 + x) * 4 + 3] > 0) return x;
  }
  return 400;
}

void main() {
  for (final int frameMicros in <int>[10000, 8333]) {
    testWidgets('相邻 $frameMicrosµs 帧之间真实像素向左移动，路径不重建',
        (WidgetTester tester) async {
      final List<AmplitudePoint> points = _points(400);
      await _mount(tester, points);
      await tester.pump();
      final CustomPainter painter = tester.widget<CustomPaint>(
        find.descendant(
          of: find.byType(AmplitudeStrip),
          matching: find.byType(CustomPaint),
        ),
      ).painter!;
      final Uint8List before = await _pixels(tester);
      final int firstEdge = await _leftEdge(tester);
      await tester.pump(Duration(microseconds: frameMicros));
      final int secondEdge = await _leftEdge(tester);
      expect(await _pixels(tester), isNot(orderedEquals(before)));
      expect(secondEdge, lessThan(firstEdge));
      expect(firstEdge - secondEdge, inInclusiveRange(2, 5));
      expect(tester.widget<CustomPaint>(
        find.descendant(
          of: find.byType(AmplitudeStrip),
          matching: find.byType(CustomPaint),
        ),
      ).painter, same(painter));

      // 新批次只扩展真实数据，旧曲线不能向右回跳。
      await _mount(tester, _points(500));
      final int batchEdge = await _leftEdge(tester);
      expect(batchEdge, lessThanOrEqualTo(secondEdge));
      await tester.pump(Duration(microseconds: frameMicros));
      expect(await _leftEdge(tester), lessThan(batchEdge));
      await tester.pumpAndSettle();
      expect(tester.binding.transientCallbackCount, 0);
    });
  }

  testWidgets('暂停冻结像素，长时间后恢复只推进当前帧',
      (WidgetTester tester) async {
    final List<AmplitudePoint> points = _points(400);
    await _mount(tester, points);
    await tester.pump(const Duration(milliseconds: 20));
    await _mount(tester, points, frozen: true);
    final Uint8List frozen = await _pixels(tester);
    final int edge = await _leftEdge(tester);
    await tester.pump(const Duration(minutes: 2));
    expect(await _pixels(tester), orderedEquals(frozen));
    expect(tester.binding.transientCallbackCount, 0);
    await _mount(tester, points);
    await tester.pump();
    expect(await _leftEdge(tester), edge);
    await tester.pump(const Duration(microseconds: 8333));
    expect(edge - await _leftEdge(tester), inInclusiveRange(2, 5));
    await tester.pumpAndSettle();
  });

  testWidgets('TickerMode 关闭时冻结，重新开启不计入等待时间',
      (WidgetTester tester) async {
    final List<AmplitudePoint> points = _points(400);
    await _mount(tester, points);
    await tester.pump(const Duration(milliseconds: 20));
    await _mount(tester, points, tickerEnabled: false);
    final Uint8List before = await _pixels(tester);
    final int edge = await _leftEdge(tester);
    await tester.pump(const Duration(minutes: 1));
    expect(await _pixels(tester), orderedEquals(before));
    expect(tester.binding.transientCallbackCount, 0);
    await _mount(tester, points);
    await tester.pump();
    expect(await _leftEdge(tester), edge);
    await tester.pump(const Duration(milliseconds: 10));
    expect(edge - await _leftEdge(tester), inInclusiveRange(3, 5));
    await tester.pumpAndSettle();
  });

  testWidgets('进入后台冻结，返回前台无墙钟跳跃',
      (WidgetTester tester) async {
    await _mount(tester, _points(400));
    await tester.pump(const Duration(milliseconds: 20));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    final Uint8List before = await _pixels(tester);
    final int edge = await _leftEdge(tester);
    await tester.pump(const Duration(minutes: 1));
    expect(await _pixels(tester), orderedEquals(before));
    expect(tester.binding.transientCallbackCount, 0);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(await _leftEdge(tester), edge);
    await tester.pump(const Duration(milliseconds: 10));
    expect(edge - await _leftEdge(tester), inInclusiveRange(3, 5));
    await tester.pumpAndSettle();
  });

  testWidgets('断流追到最后真实样本后停止，空列表不会持续调度',
      (WidgetTester tester) async {
    await _mount(tester, _points(400));
    await tester.pumpAndSettle();
    final Uint8List last = await _pixels(tester);
    await tester.pump(const Duration(seconds: 5));
    expect(await _pixels(tester), orderedEquals(last));
    expect(tester.binding.transientCallbackCount, 0);
    await _mount(tester, const <AmplitudePoint>[]);
    await tester.pumpAndSettle();
    expect(tester.binding.transientCallbackCount, 0);
    final Uint8List empty = await _pixels(tester);
    await tester.pump(const Duration(seconds: 5));
    expect(await _pixels(tester), orderedEquals(empty));
  });

  testWidgets('减少动态效果只在数据变化时更新，没有持续帧',
      (WidgetTester tester) async {
    await _mount(tester, _points(400), reduceMotion: true);
    await tester.pumpAndSettle();
    expect(tester.binding.transientCallbackCount, 0);
    final Uint8List before = await _pixels(tester);
    await tester.pump(const Duration(seconds: 1));
    expect(await _pixels(tester), orderedEquals(before));
    await _mount(tester, _points(500), reduceMotion: true);
    await tester.pumpAndSettle();
    expect(await _pixels(tester), isNot(orderedEquals(before)));
    expect(tester.binding.transientCallbackCount, 0);
  });

  testWidgets('清空或换会话后游标重置，卸载会取消尚未完成的帧',
      (WidgetTester tester) async {
    final List<AmplitudePoint> points = _points(400);
    await _mount(tester, points);
    final int initialEdge = await _leftEdge(tester);
    await tester.pumpAndSettle();
    expect(await _leftEdge(tester), lessThan(initialEdge));
    await _mount(tester, const <AmplitudePoint>[]);
    await _mount(tester, points);
    expect(await _leftEdge(tester), initialEdge);
    await tester.pumpAndSettle();
    await _mount(tester, points, session: 'session-b');
    expect(await _leftEdge(tester), initialEdge);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.binding.transientCallbackCount, 0);
    expect(tester.takeException(), isNull);
  });
}
