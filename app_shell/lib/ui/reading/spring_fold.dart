/// 可打断的折叠/展开高度动画。
///
/// 子组件自然高度变化时，高度用弹簧逼近新目标；目标在动画中途反转时，从当前
/// 高度与速度接续，而不是从头播放。系统减少动态效果时直接落位。宽度变化、
/// 字体缩放导致的自然高度变化同样被测量。
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart' show SpringSimulation;
import 'package:flutter/rendering.dart' show PaintingContext, RenderProxyBox;
import 'package:flutter/scheduler.dart';

import '../motion/echo_motion.dart';

/// 用弹簧驱动高度变化的容器。
class SpringFold extends StatefulWidget {
  const SpringFold({
    super.key,
    required this.motion,
    required this.child,
  });

  final EchoMotion motion;
  final Widget child;

  @override
  State<SpringFold> createState() => _SpringFoldState();
}

class _SpringFoldState extends State<SpringFold> with SingleTickerProviderStateMixin {
  late final AnimationController _controller =
      AnimationController.unbounded(vsync: this);

  /// 子组件的自然高度。
  double? _measured;

  /// 当前用于布局的高度；为空表示尚未测量，先按自然高度显示。
  double? _height;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_handleTick);
  }

  @override
  void didUpdateWidget(SpringFold oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.motion.reduceMotion && _controller.isAnimating) {
      final double target = _measured ?? _height ?? 0;
      _controller.stop();
      _controller.value = target;
      if (_height != target) {
        setState(() => _height = target);
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleTick() {
    final double next = math.max(0, _controller.value);
    if (_height == next) {
      return;
    }
    setState(() => _height = next);
  }

  void _handleChildHeight(double measured) {
    if (!mounted) {
      return;
    }
    if (_measured != null && (_measured! - measured).abs() < 0.01) {
      return;
    }
    _measured = measured;
    if (_height == null) {
      _controller.value = measured;
      setState(() => _height = measured);
      return;
    }
    _animateTo(measured);
  }

  void _animateTo(double target) {
    if (widget.motion.reduceMotion) {
      _controller.stop();
      _controller.value = target;
      if (_height != target) {
        setState(() => _height = target);
      }
      return;
    }
    final double current = _height ?? target;
    final double velocity = _controller.isAnimating ? _controller.velocity : 0;
    _controller.stop();
    _controller.value = current;
    _controller.animateWith(
      SpringSimulation(widget.motion.spring, current, target, velocity),
    );
  }

  @override
  Widget build(BuildContext context) {
    return _SpringHeightBox(
      height: _height,
      onChildHeight: _handleChildHeight,
      child: widget.child,
    );
  }
}

class _SpringHeightBox extends SingleChildRenderObjectWidget {
  const _SpringHeightBox({
    required this.height,
    required this.onChildHeight,
    super.child,
  });

  final double? height;
  final ValueChanged<double> onChildHeight;

  @override
  _RenderSpringHeight createRenderObject(BuildContext context) =>
      _RenderSpringHeight(height: height, onChildHeight: onChildHeight);

  @override
  void updateRenderObject(BuildContext context, covariant _RenderSpringHeight renderObject) {
    renderObject
      ..height = height
      ..onChildHeight = onChildHeight;
  }
}

class _RenderSpringHeight extends RenderProxyBox {
  _RenderSpringHeight({required this.onChildHeight, this.height});

  ValueChanged<double> onChildHeight;
  double? height;
  double? _reportedHeight;

  @override
  void performLayout() {
    final RenderBox? child = this.child;
    if (child == null) {
      size = constraints.smallest;
      return;
    }
    child.layout(
      constraints.copyWith(minHeight: 0, maxHeight: double.infinity),
      parentUsesSize: true,
    );
    final double width = constraints.hasBoundedWidth ? constraints.maxWidth : child.size.width;
    size = constraints.constrain(Size(width, height ?? child.size.height));
    final double measured = child.size.height;
    if (_reportedHeight == null || (_reportedHeight! - measured).abs() >= 0.01) {
      _reportedHeight = measured;
      final ValueChanged<double> callback = onChildHeight;
      SchedulerBinding.instance.addPostFrameCallback((Duration _) {
        callback(measured);
      });
    }
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final RenderBox? child = this.child;
    if (child == null) {
      return;
    }
    if (size.height + 0.01 < child.size.height) {
      context.pushClipRect(
        needsCompositing,
        offset,
        Offset.zero & size,
        (PaintingContext inner, Offset innerOffset) => inner.paintChild(child, innerOffset),
      );
    } else {
      context.paintChild(child, offset);
    }
  }

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final RenderBox? child = this.child;
    if (child == null) {
      return constraints.smallest;
    }
    final Size childSize = child.getDryLayout(
      constraints.copyWith(minHeight: 0, maxHeight: double.infinity),
    );
    final double width = constraints.hasBoundedWidth ? constraints.maxWidth : childSize.width;
    return constraints.constrain(Size(width, height ?? childSize.height));
  }

  @override
  double computeMinIntrinsicWidth(double height) => child?.getMinIntrinsicWidth(height) ?? 0;

  @override
  double computeMaxIntrinsicWidth(double height) => child?.getMaxIntrinsicWidth(height) ?? 0;

  @override
  double computeMinIntrinsicHeight(double width) => child?.getMinIntrinsicHeight(width) ?? 0;

  @override
  double computeMaxIntrinsicHeight(double width) => child?.getMaxIntrinsicHeight(width) ?? 0;
}
