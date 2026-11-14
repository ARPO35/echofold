import 'package:flutter/material.dart';
import 'package:flutter/physics.dart' show SpringSimulation;

import 'echo_motion.dart';

/// 原生组件反馈不足时使用的被动按压反馈，不注册任何手势识别器。
///
/// [child] 保有原来的点击、拖动、焦点、语义和 Ink。禁用按钮也能表达触碰，
/// 但本组件不会触发业务动作。多指按压时，最后一根仍在区域内的手指离开才回弹。
/// 指定 [borderRadius] 时圆角随按压轻微变化，并裁剪到该形状；不指定时保留
/// 子组件原有外形，只叠加胶囊状态层。系统减少动态效果时仅即时显示状态层。
class EchoPress extends StatefulWidget {
  const EchoPress({
    super.key,
    required this.child,
    this.scale = 0.96,
    this.borderRadius,
  }) : assert(scale > 0 && scale <= 1);

  final Widget child;
  final double scale;
  final BorderRadius? borderRadius;

  @override
  State<EchoPress> createState() => _EchoPressState();
}

class _EchoPressState extends State<EchoPress>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller =
      AnimationController.unbounded(vsync: this);
  final Set<int> _pointers = <int>{};
  final Set<int> _inside = <int>{};
  EchoMotion _motion = EchoMotion.standard;
  bool _pressed = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final EchoMotion motion = EchoMotion.of(context);
    if (motion != _motion) {
      _motion = motion;
      _animateTo(_pressed ? 1 : 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _animateTo(double target) {
    final double velocity = _controller.isAnimating ? _controller.velocity : 0;
    final double current = _controller.value;
    _controller.stop();
    if (_motion.reduceMotion) {
      _controller.value = target;
      return;
    }
    _controller.animateWith(
      SpringSimulation(_motion.spring, current, target, velocity),
    );
  }

  void _syncPressed() {
    final bool pressed = _inside.isNotEmpty;
    if (_pressed == pressed) {
      return;
    }
    _pressed = pressed;
    _animateTo(pressed ? 1 : 0);
  }

  void _onDown(PointerDownEvent event) {
    _pointers.add(event.pointer);
    _inside.add(event.pointer);
    _syncPressed();
  }

  void _onMove(PointerMoveEvent event) {
    if (!_pointers.contains(event.pointer)) {
      return;
    }
    // Listener 位于缩放层之外，移出判定不随缩放改变，避免边缘反复跳变。
    final RenderBox box = context.findRenderObject()! as RenderBox;
    if ((Offset.zero & box.size).contains(event.localPosition)) {
      _inside.add(event.pointer);
    } else {
      _inside.remove(event.pointer);
    }
    _syncPressed();
  }

  void _onRelease(PointerEvent event) {
    _pointers.remove(event.pointer);
    _inside.remove(event.pointer);
    _syncPressed();
  }

  @override
  Widget build(BuildContext context) {
    final Color stateColor = Theme.of(context).colorScheme.onSurface;
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: _onDown,
      onPointerMove: _onMove,
      onPointerUp: _onRelease,
      onPointerCancel: _onRelease,
      child: AnimatedBuilder(
        animation: _controller,
        child: widget.child,
        builder: (BuildContext context, Widget? child) {
          final double progress = _controller.value.clamp(0.0, 1.0);
          final double scale = _motion.reduceMotion
              ? 1
              : 1 + (widget.scale - 1) * progress;
          final BorderRadius radius =
              (widget.borderRadius ?? BorderRadius.circular(999)) *
              (1 + (_motion.pressCornerFactor - 1) * progress);
          Widget feedback = Stack(
            fit: StackFit.passthrough,
            children: <Widget>[
              child!,
              Positioned.fill(
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: stateColor.withValues(
                        alpha: _motion.pressStateOpacity * progress,
                      ),
                      borderRadius: radius,
                    ),
                  ),
                ),
              ),
            ],
          );
          if (widget.borderRadius != null) {
            feedback = ClipRRect(borderRadius: radius, child: feedback);
          }
          return Transform.scale(
            scale: scale,
            transformHitTests: false,
            child: feedback,
          );
        },
      ),
    );
  }
}
