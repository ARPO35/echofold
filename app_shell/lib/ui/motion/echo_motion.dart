/// 统一动效 token。
///
/// 几何（高度、位移）与透明度使用不同曲线：几何接近临界阻尼弹簧，可被新手势
/// 从当前位置与速度接续；透明度使用短缓动，避免文字持续晃动。系统开启“减少动态
/// 效果”时所有时长归零、位移取消，但功能保持可用。
library;

import 'package:flutter/material.dart';

/// 动效参数集合；通过 [EchoMotion.of] 取得当前环境适用的 token。
@immutable
final class EchoMotion {
  const EchoMotion({
    required this.reduceMotion,
    required this.fade,
    required this.quick,
    required this.base,
    required this.slow,
    required this.fadeCurve,
    required this.geometryCurve,
    required this.spring,
    required this.swipeSpring,
    required this.titleShift,
  });

  /// 标准环境。
  static const EchoMotion standard = EchoMotion(
    reduceMotion: false,
    fade: Duration(milliseconds: 140),
    quick: Duration(milliseconds: 160),
    base: Duration(milliseconds: 240),
    slow: Duration(milliseconds: 360),
    fadeCurve: Curves.easeOut,
    geometryCurve: Curves.easeOutCubic,
    // ζ = c / (2√(km)) ≈ 0.88，接近临界阻尼，允许极小过冲。
    spring: SpringDescription(mass: 1, stiffness: 220, damping: 26),
    swipeSpring: SpringDescription(mass: 1, stiffness: 320, damping: 30),
    titleShift: 6,
  );

  /// 减少动态效果环境：只保留即时状态。
  static const EchoMotion reduced = EchoMotion(
    reduceMotion: true,
    fade: Duration.zero,
    quick: Duration.zero,
    base: Duration.zero,
    slow: Duration.zero,
    fadeCurve: Curves.linear,
    geometryCurve: Curves.linear,
    spring: SpringDescription(mass: 1, stiffness: 400, damping: 40),
    swipeSpring: SpringDescription(mass: 1, stiffness: 400, damping: 40),
    titleShift: 0,
  );

  final bool reduceMotion;
  final Duration fade;
  final Duration quick;
  final Duration base;
  final Duration slow;
  final Curve fadeCurve;
  final Curve geometryCurve;
  final SpringDescription spring;
  final SpringDescription swipeSpring;

  /// 标题替换时的轻微纵向位移（逻辑像素）。
  final double titleShift;

  /// 读取当前 `MediaQuery`；缺少 `MediaQuery` 时按标准动效处理。
  static EchoMotion of(BuildContext context) {
    final MediaQueryData? data = MediaQuery.maybeOf(context);
    final bool reduce =
        (data?.disableAnimations ?? false) || (data?.accessibleNavigation ?? false);
    return reduce ? reduced : standard;
  }
}

/// 统一弹窗入口：复用 [EchoMotion] 的淡入与轻微缩放，减少动态效果时直接显示。
Future<T?> showEchoDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
}) {
  final EchoMotion motion = EchoMotion.of(context);
  return showGeneralDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    barrierLabel: '关闭',
    barrierColor: Colors.black54,
    transitionDuration: motion.quick,
    pageBuilder: (BuildContext _, Animation<double> _, Animation<double> _) =>
        builder(context),
    transitionBuilder:
        (BuildContext context, Animation<double> animation, Animation<double> _, Widget child) {
      if (motion.reduceMotion) {
        return child;
      }
      final Animation<double> curved =
          CurvedAnimation(parent: animation, curve: motion.fadeCurve);
      return FadeTransition(
        opacity: curved,
        child: ScaleTransition(
          scale: Tween<double>(begin: 0.97, end: 1).animate(curved),
          child: child,
        ),
      );
    },
  );
}
