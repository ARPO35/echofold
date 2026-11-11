import 'package:flutter/material.dart';

void main() {
  runApp(const EchoFoldApp());
}

/// EchoFold 应用外壳。
///
/// 本阶段只验证 Flutter 容器可运行、原生事件桥可接入；录音、阅读与设置页面属于后续切片。
class EchoFoldApp extends StatelessWidget {
  /// 创建应用根组件。
  const EchoFoldApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EchoFold',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFB3261E)),
        useMaterial3: true,
      ),
      home: const Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text('EchoFold', style: TextStyle(fontSize: 28)),
              SizedBox(height: 8),
              Text('录音与转写界面将在后续切片接入'),
            ],
          ),
        ),
      ),
    );
  }
}
