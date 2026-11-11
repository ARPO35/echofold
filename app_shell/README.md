# echofold_app

EchoFold 的 Flutter 外壳（`com.echofold.app`），承载录音与实时转写界面，并通过 EventChannel
接收原生 ASR 事件。

## 目录与职责

| 路径 | 说明 |
|---|---|
| `lib/native/asr_event.dart` | 与 Kotlin `com.echofold.core.asr.AsrEvent` 字段一一对应的事件模型、解析与门控 [AsrEventGate] |
| `lib/native/asr_event_bridge.dart` | EventChannel `com.echofold.app/asr_events` 的薄封装，可注入 `Stream` 便于测试 |
| `android/core` | 纯 JVM Kotlin 模块：共享模型、`sourceSetHash`、事件协议、块接口与引擎契约 |
| `android/capture_android` | 采集侧 Android library（阶段骨架，minSdk 29 / compileSdk 36） |
| `android/asr_android` | 识别侧 Android library（阶段骨架，minSdk 29 / compileSdk 36） |

## 本地校验

```bash
flutter analyze
flutter test
cd android && ./gradlew :core:test :capture_android:assembleDebug :asr_android:assembleDebug
```

`android/local.properties` 记录本机 `sdk.dir` 与 `flutter.sdk`，已被 `android/.gitignore` 忽略，不入库。

