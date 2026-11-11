package com.echofold.app

import io.flutter.embedding.android.FlutterActivity

/**
 * Flutter 容器入口。
 *
 * 原生采集与识别能力位于 `:capture_android` / `:asr_android`，通过 EventChannel
 * `com.echofold.app/asr_events` 向 Dart 投递事件。
 */
class MainActivity : FlutterActivity()
