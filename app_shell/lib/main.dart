import 'package:flutter/material.dart';

import 'ui/recording/recording_controller.dart';
import 'ui/recording/recording_page.dart';

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
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFB3261E)),
        useMaterial3: true,
      ),
      home: RecordingPage(controller: _controller),
    );
  }
}
