import 'package:flutter/material.dart';

import 'ui/recording/recording_controller.dart';
import 'ui/recording/recording_page.dart';
import 'ui/theme/echo_theme.dart';
void main() {
  runApp(const EchoFoldApp());
}

class EchoFoldApp extends StatefulWidget {
  const EchoFoldApp({super.key});

  @override
  State<EchoFoldApp> createState() => _EchoFoldAppState();
}

class _EchoFoldAppState extends State<EchoFoldApp> {
  late final RecordingController _controller = RecordingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EchoFold',
      theme: EchoTheme.dark,
      home: RecordingPage(controller: _controller),
    );
  }
}
