import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:echofold_app/domain/models.dart';
import 'package:echofold_app/native/platform_contracts.dart';
import 'package:echofold_app/ui/recording/recording_platform.dart';
import 'package:echofold_app/ui/recording/amplitude_strip.dart';
import 'package:echofold_app/ui/recording/recording_controller.dart';
import 'package:echofold_app/ui/recording/recording_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_recording_platform.dart';

const Key _main = Key('recording-main-action');
const Key _dragButton = Key('recording-drag-button');

Future<void> _mount(WidgetTester tester, RecordingController controller,
    {bool reduceMotion = false, double textScale = 1}) async {
  await tester.pumpWidget(MaterialApp(
    builder: (BuildContext context, Widget? child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        disableAnimations: reduceMotion,
        textScaler: TextScaler.linear(textScale),
      ),
      child: child!,
    ),
    home: RecordingPage(controller: controller),
  ));
  await tester.pumpAndSettle();
}

Future<void> _start(WidgetTester tester) async {
  await tester.tap(find.byKey(_main));
  await tester.pumpAndSettle();
  await tester.tap(find.widgetWithText(FilledButton, '开始仅录音'));
  await tester.pumpAndSettle();
}

Future<void> _swipe(WidgetTester tester, double distance, Duration duration) async {
  final Offset start = tester.getCenter(find.byKey(_dragButton));
  final TestGesture gesture = await tester.startGesture(start);
  for (int step = 1; step <= 8; step++) {
    await tester.pump(duration ~/ 8);
    await gesture.moveTo(start.translate(-distance * step / 8, 0));
  }
  await gesture.up();
  await tester.pumpAndSettle();
}

Future<Uint8List> _shapePixels(WidgetTester tester) async {
  final RenderRepaintBoundary boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(const Key('recording-shape-image')),
  );
  return (await tester.runAsync<Uint8List>(() async {
    final ui.Image image = await boundary.toImage(pixelRatio: 1);
    try {
      return (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!
          .buffer.asUint8List();
    } finally {
      image.dispose();
    }
  }))!;
}

final class _CountingPlatform implements RecordingPlatform {
  final FakeRecordingPlatform delegate = FakeRecordingPlatform();
  int snapshots = 0;

  @override
  Stream<Object?> get sessions => delegate.sessions;
  @override
  Stream<Object?> get amplitudes => delegate.amplitudes;

  @override
  Future<Map<Object?, Object?>> invoke(String method, [Map<String, Object?>? arguments]) {
    if (method == PlatformMethods.recordingSnapshot) snapshots++;
    return delegate.invoke(method, arguments);
  }
}

void main() {
  late FakeRecordingPlatform platform;
  late RecordingController controller;

  setUp(() {
    platform = FakeRecordingPlatform();
    controller = RecordingController(platform: platform);
  });
  tearDown(() async {
    controller.dispose();
    await platform.dispose();
  });

  testWidgets('开始必须显式确认仅录音，拒绝权限保持空闲并提供设置', (WidgetTester tester) async {
    platform.granted = false;
    platform.permanentlyDenied = true;
    await _mount(tester, controller);
    await tester.tap(find.byKey(_main));
    await tester.pumpAndSettle();
    expect(platform.permissions, 0);
    expect(platform.starts, 0);
    expect(find.text('仅录音'), findsOneWidget);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(platform.permissions, 0);
    await _start(tester);
    expect(platform.permissions, 1);
    expect(platform.starts, 0);
    expect(find.text('准备录音'), findsOneWidget);
    await tester.ensureVisible(find.text('打开系统设置'));
    await tester.tap(find.text('打开系统设置'));
    await tester.pumpAndSettle();
    expect(platform.settingsOpened, 1);
  });

  testWidgets('按钮开始暂停继续，时长和波形由真实事件推进', (WidgetTester tester) async {
    await _mount(tester, controller);
    await _start(tester);
    expect(find.text('正在录音'), findsOneWidget);
    platform.publish('RECORDING', capturedMs: 2345);
    platform.amplitude(1, 2345);
    await tester.pump();
    expect(find.text('00:00:02'), findsOneWidget);
    expect(tester.widget<AmplitudeStrip>(find.byType(AmplitudeStrip)).amplitudes, hasLength(1));
    await tester.tap(find.byKey(_main));
    await tester.pumpAndSettle();
    expect(find.text('已暂停'), findsOneWidget);
    platform.amplitude(2, 3000);
    await tester.pump(const Duration(seconds: 5));
    expect(find.text('00:00:02'), findsOneWidget);
    expect(tester.widget<AmplitudeStrip>(find.byType(AmplitudeStrip)).amplitudes, hasLength(1));
    await tester.tap(find.byKey(_main));
    await tester.pumpAndSettle();
    expect(find.text('正在录音'), findsOneWidget);
    expect(platform.commands, <String>['PAUSE', 'RESUME']);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(platform.commands, <String>['PAUSE', 'RESUME']);
  });
  testWidgets('主控制只有一个带标签的按钮语义，支持真实辅助操作并反映禁用状态', (WidgetTester tester) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    await _mount(tester, controller);
    final Finder action = find.byKey(const Key('recording-main-semantics'));
    SemanticsNode node = tester.getSemantics(action);
    expect(node, matchesSemantics(
      label: '开始仅录音', isButton: true, isFocusable: true,
      hasEnabledState: true, isEnabled: true, hasTapAction: true,
      hasScrollLeftAction: true, hasScrollRightAction: true,
    ));
    expect(node.debugListChildrenInOrder(DebugSemanticsDumpOrder.traversalOrder), isEmpty);
    node.owner!.performAction(node.id, SemanticsAction.tap);
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, '开始仅录音'));
    await tester.pumpAndSettle();
    node = tester.getSemantics(action);
    expect(node.label, '暂停录音；向左拖动结束并保存');
    expect(node.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);
    node.owner!.performAction(node.id, SemanticsAction.tap);
    await tester.pumpAndSettle();
    expect(platform.commands, <String>['PAUSE']);
    expect(tester.getSemantics(action).label, '继续录音；向左拖动结束并保存');
    platform.publish('FAILED', errorCode: 'RECORDING_RECOVERY_REQUIRED');
    await tester.pumpAndSettle();
    node = tester.getSemantics(action);
    expect(node, matchesSemantics(
      label: '开始仅录音', isButton: true, isFocusable: true,
      hasEnabledState: true, isEnabled: false, hasTapAction: false,
      hasScrollLeftAction: true, hasScrollRightAction: true,
    ));
    expect(node.debugListChildrenInOrder(DebugSemanticsDumpOrder.traversalOrder), isEmpty);
    handle.dispose();
  });


  testWidgets('短距离快速左滑不结束，慢速超过阈值才保存', (WidgetTester tester) async {
    await _mount(tester, controller);
    await _start(tester);
    platform.publish('RECORDING', capturedMs: 29020);
    platform.amplitude(1, 29020);
    await tester.pump();
    await tester.tap(find.byKey(_main));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(_main));
    await tester.pumpAndSettle();
    platform.publish('RECORDING', capturedMs: 30280);
    await tester.pump();
    expect(find.text('00:00:30'), findsOneWidget);
    await _swipe(tester, 48, const Duration(milliseconds: 80));
    expect(platform.commands, <String>['PAUSE', 'RESUME']);
    expect(controller.state.state, SessionState.recording);
    await _swipe(tester, 140, const Duration(seconds: 2));
    expect(platform.commands, <String>['PAUSE', 'RESUME', 'STOP']);
    expect(find.text('准备录音'), findsOneWidget);
    expect(platform.starts, 1);
    expect(find.text('00:00:00'), findsOneWidget);
    expect(tester.widget<AmplitudeStrip>(find.byType(AmplitudeStrip)).amplitudes, isEmpty);
    expect(tester.widget<OutlinedButton>(find.byKey(const Key('recording-stop-button'))).onPressed, isNull);
    await _start(tester);
    expect(find.text('00:00:00'), findsOneWidget);
    platform.publish('RECORDING', sessionId: 'session-2', capturedMs: 1260);
    await tester.pump();
    expect(find.text('00:00:01'), findsOneWidget);
  });

  testWidgets('空闲左滑无效，减少动画时手势仍可保存', (WidgetTester tester) async {
    await _mount(tester, controller, reduceMotion: true);
    await _swipe(tester, 140, const Duration(seconds: 1));
    expect(platform.commands, isEmpty);
    await _start(tester);
    await _swipe(tester, 140, const Duration(seconds: 1));
    expect(platform.commands, <String>['STOP']);
  });

  testWidgets('窄轨道使用可用宽度四成阈值，减少动画时按钮立即归位', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await _mount(tester, controller, reduceMotion: true);
    await _start(tester);
    final Offset rest = tester.getCenter(find.byKey(_dragButton));
    await _swipe(tester, 48, const Duration(milliseconds: 80));
    expect(tester.getCenter(find.byKey(_dragButton)), rest);
    expect(platform.commands, isEmpty);
    // 可用轨道四成小于 96dp，80dp 的实际位移足以提交。
    await _swipe(tester, 80, const Duration(seconds: 1));
    expect(platform.commands, <String>['STOP']);
    expect(tester.getCenter(find.byKey(_dragButton)), rest);
  });

  testWidgets('取消按压反馈会回弹且不开始、不暂停、不保存', (WidgetTester tester) async {
    await _mount(tester, controller);
    final Rect rest = tester.getRect(find.byKey(_main));
    TestGesture gesture = await tester.startGesture(rest.center);
    // 首帧建立 ticker 起点，随后才推进按下的可见形变。
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 70));
    expect(tester.getRect(find.byKey(_main)).width, lessThan(rest.width));
    await gesture.cancel();
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsNothing);
    expect(platform.starts, 0);
    expect(tester.getRect(find.byKey(_main)), rest);
    await _start(tester);
    gesture = await tester.startGesture(tester.getCenter(find.byKey(_main)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 70));
    await gesture.cancel();
    await tester.pumpAndSettle();
    expect(platform.commands, isEmpty);
    expect(controller.state.state, SessionState.recording);
  });

  testWidgets('导航保留会话和阅读位置，不重连或暂停，权限入口真实可操作', (WidgetTester tester) async {
    final _CountingPlatform counted = _CountingPlatform();
    final RecordingController retained = RecordingController(platform: counted);
    addTearDown(() async { retained.dispose(); await counted.delegate.dispose(); });
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await _mount(tester, retained, textScale: 1.5);
    await _start(tester);
    final int connections = counted.snapshots;
    await tester.drag(find.byKey(const PageStorageKey<String>('recording-transcript-scroll')), const Offset(0, -120));
    await tester.pumpAndSettle();
    final ScrollableState reading = tester.state<ScrollableState>(find.descendant(
      of: find.byKey(const PageStorageKey<String>('recording-transcript-scroll')),
      matching: find.byType(Scrollable),
    ));
    final double offset = reading.position.pixels;
    expect(offset, greaterThan(0));
    await tester.tap(find.byKey(const Key('recording-nav-list')));
    await tester.pumpAndSettle();
    counted.delegate.publish('RECORDING', capturedMs: 4567);
    await tester.pumpAndSettle();
    expect(find.text('session-1'), findsOneWidget);
    expect(find.textContaining('00:00:04'), findsNWidgets(2));
    await tester.tap(find.byKey(const Key('recording-nav-settings')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('recording-permission-settings')));
    await tester.tap(find.byKey(const Key('recording-permission-settings')));
    await tester.pumpAndSettle();
    expect(counted.delegate.settingsOpened, 1);
    await tester.tap(find.byKey(const Key('recording-nav-record')));
    await tester.pumpAndSettle();
    expect(reading.position.pixels, offset);
    expect(retained.state.sessionId, 'session-1');
    expect(counted.snapshots, connections);
    expect(counted.delegate.commands, isEmpty);
    expect(counted.delegate.starts, 1);
  });

  testWidgets('状态轮廓连续变形并支持快速反向，减少动画立即到终态', (WidgetTester tester) async {
    await _mount(tester, controller);
    await _start(tester);
    final Uint8List recording = await _shapePixels(tester);
    platform.publish('PAUSED');
    await tester.pump();
    expect(await _shapePixels(tester), orderedEquals(recording));
    await tester.pump(const Duration(milliseconds: 60));
    final Uint8List intermediate = await _shapePixels(tester);
    expect(intermediate, isNot(orderedEquals(recording)));
    platform.publish('RECORDING');
    await tester.pump();
    expect(await _shapePixels(tester), orderedEquals(intermediate));
    await tester.pumpAndSettle();
    expect(await _shapePixels(tester), orderedEquals(recording));
    await _mount(tester, controller, reduceMotion: true);
    platform.publish('PAUSED');
    await tester.pump();
    final Uint8List paused = await _shapePixels(tester);
    expect(paused, isNot(orderedEquals(recording)));
    await tester.pump(const Duration(seconds: 1));
    expect(await _shapePixels(tester), orderedEquals(paused));
    expect(tester.binding.transientCallbackCount, 0);
  });

  testWidgets('拖动波形不会结束录音', (WidgetTester tester) async {
    await _mount(tester, controller);
    await _start(tester);
    expect(controller.state.state, SessionState.recording);
    expect(controller.canStop, isTrue);
    final Rect waveform = tester.getRect(find.byType(AmplitudeStrip));
    final Rect button = tester.getRect(find.byKey(_dragButton));
    final Offset start = waveform.centerRight - const Offset(24, 0);
    expect(waveform.contains(start), isTrue);
    expect(button.contains(start), isFalse);
    final TestGesture gesture = await tester.startGesture(start);
    await gesture.moveBy(const Offset(-160, 0));
    await gesture.up();
    await tester.pumpAndSettle();
    expect(platform.commands, isEmpty);
    expect(controller.state.state, SessionState.recording);
    expect(controller.canStop, isTrue);
    expect(find.text('正在录音'), findsOneWidget);
  });

  testWidgets('需要恢复时显示保留文件提示，禁用保存和新建', (WidgetTester tester) async {
    await _mount(tester, controller);
    await _start(tester);
    platform.publish('FAILED', errorCode: 'RECORDING_RECOVERY_REQUIRED');
    await tester.pumpAndSettle();
    expect(find.text('录音待恢复'), findsOneWidget);
    expect(find.textContaining('此版本尚未提供恢复入口'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byKey(_main)).onPressed, isNull);
    expect(tester.widget<OutlinedButton>(find.byKey(const Key('recording-stop-button'))).onPressed, isNull);
    expect(find.text('重试保存'), findsNothing);
    expect(platform.commands, isEmpty);
    expect(platform.starts, 1);
  });

  testWidgets('失败封存保留旧会话并允许明确重试，不产生新录音', (WidgetTester tester) async {
    platform.sealFails = true;
    await _mount(tester, controller);
    await _start(tester);
    platform.publish('RECORDING', capturedMs: 30280);
    await tester.pump();
    await _swipe(tester, 140, const Duration(seconds: 1));
    expect(find.text('录音未能保存'), findsOneWidget);
    expect(find.textContaining('保存失败，当前录音仍保留'), findsOneWidget);
    expect(controller.state.sessionId, 'session-1');
    expect(find.text('00:00:30'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byKey(_main)).onPressed, isNull);
    platform.sealFails = false;
    await tester.ensureVisible(find.byKey(const Key('recording-stop-button')));
    await tester.tap(find.byKey(const Key('recording-stop-button')));
    await tester.pumpAndSettle();
    expect(find.text('准备录音'), findsOneWidget);
    expect(find.text('00:00:00'), findsOneWidget);
    expect(platform.starts, 1);
  });

  testWidgets('窄屏大字仍可读取错误并操作按钮', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    platform.granted = false;
    await _mount(tester, controller, textScale: 2);
    await _start(tester);
    await tester.ensureVisible(find.byKey(const Key('recording-error')));
    expect(find.textContaining('未获得麦克风权限'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
