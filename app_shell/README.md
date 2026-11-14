# echofold_app

EchoFold 的 Flutter 外壳（`com.echofold.app`），承载录音与实时转写界面，并通过 EventChannel
接收原生 ASR 事件。

## 目录与职责

| 路径 | 说明 |
|---|---|
| `lib/native/asr_event.dart` | 与 Kotlin `com.echofold.core.asr.AsrEvent` 字段一一对应的事件模型、解析与门控 [AsrEventGate] |
| `lib/native/asr_event_bridge.dart` | EventChannel `com.echofold.app/asr_events` 的薄封装，可注入 `Stream` 便于测试 |
| `android/core` | 纯 JVM Kotlin 模块：共享模型、`sourceSetHash`、事件协议、块接口与引擎契约 |
| `android/capture_android` | 采集侧 Android library：AudioRecord、PCM 分发、AAC 分块、前台服务与命令总线 |
| `android/asr_android` | 识别侧 Android library：模型清单信任、安装、租约与引擎适配 |

## 本地开发入口

在仓库根目录使用统一的 Makefile 契约：

```bash
make build
make test
make lint
```

`make build` 构建 Android 应用及采集/ASR 两个 library 的 debug 产物；`make test` 运行 Flutter 单测、`:core:test` 和根目录的 Python pytest；`make lint` 运行 Flutter analyzer 与三个 Android 模块的 debug lint。Flutter 工作目录为 `app_shell`，Android wrapper 位于 `app_shell/android/gradlew`，Python 检查通过 `uv` 执行，不依赖全局 Python 包。入口不额外执行工具安装命令；默认 `uv run` 会按项目配置解析并同步依赖，Gradle 会解析依赖，wrapper 在分发版未缓存时可能下载 Gradle，均可能联网。本轮使用 `GRADLE_FLAGS='--offline --console=plain'`、`UV_FLAGS='--offline --python 3.12'` 和已有 wrapper 缓存，没有下载依赖或安装包，uv 仅创建本地 `.venv`。Gradle 的 `--offline` 不替代 wrapper 分发版预缓存。命令存在或执行成功均不能代替真机与前置评测验收。

`FLUTTER`、`JAVA_HOME`、`ANDROID_HOME` 可覆盖本机工具位置。当前接手环境的 Flutter/Java 不在 PATH 中，已有缓存的离线门禁可在仓库根目录按下例执行；`-k` 会继续尝试不依赖失败项的其他目标，便于收集各项结果，任一失败仍使总命令失败：

```bash
make -k build test lint \
  FLUTTER=/home/lzx/dev/tools/flutter/bin/flutter \
  JAVA_HOME=/home/lzx/dev/tools/jdk21 \
  ANDROID_HOME=/home/lzx/dev/tools/android-sdk \
  GRADLE_FLAGS='--offline --console=plain' \
  UV_FLAGS='--offline --python 3.12'
```

接手工具记录为 Flutter 3.47.9、Dart 3.13.7、JDK 21、`/usr/bin/uv`、`/usr/bin/make`；Gradle wrapper 固定为 9.3.1，AGP 为 9.1.0，Kotlin 插件为 2.4.0，源码 JVM 目标为 17。`android/local.properties` 的 `sdk.dir` 与 `flutter.sdk` 也必须指向有效安装；此文件已被 `android/.gitignore` 忽略，不入库。更换 `FLUTTER` 时应同步核对 `flutter.sdk`，它决定 Gradle 加载的 Flutter 插件。

修改 `lib/data/echo_database.dart` 中的 drift schema 后，应使用已准备的项目依赖重新生成代码，再执行静态检查和测试：

```bash
cd app_shell && dart run build_runner build
```

`dart` 应来自与 `FLUTTER` 一致的 Flutter SDK；未加入 PATH 时可使用 `/home/lzx/dev/tools/flutter/bin/dart`。默认 Dart 项目依赖解析机制可能同步依赖或联网，不能把此命令本身视为离线保证。本轮使用已缓存的 build_runner 成功生成真实 schema，输出为 `Built with build_runner/aot in 4s; wrote 14 outputs.`，未执行 `pub get` 或下载、安装依赖。代码生成成功不等同于数据库迁移、文件/数据库恢复提交协议或 OpenSpec §1.10 验收通过；这些验证及真机验收仍未完成。

## Android 配置与验收边界

2026-11-14 静态读取配置的结果如下，尚未作为构建产物或真机兼容性结论：

| 模块 | minSdk | compileSdk | targetSdk |
|---|---|---|---|
| `android/app` | 29（显式配置） | 36（当前 Flutter SDK 默认值） | 36（当前 Flutter SDK 默认值） |
| `android/capture_android`、`android/asr_android` | 29 | 36（显式配置） | 未单独配置，由应用决定最终目标 |
| `android/core` | 不适用，纯 JVM | 不适用 | 不适用 |

应用的 compileSdk/targetSdk 使用 `flutter.compileSdkVersion` / `flutter.targetSdkVersion`，不是工程内固定常量；换 Flutter SDK 后须重新确认。依赖许可已核对的具体版本、原始证据和未覆盖范围见[校验报告](../docs/validation-report.md)。这次核对没有完成全部传递依赖、原生工件或发布告知审查，也没有核验商店目标 API 政策。

接手时 `adb devices` 无已连接设备，未建立安装、调试、麦克风权限、前台服务或实际编码/ASR 链路的真机基线；两台参考设备、模型工件、预注册评测和 30 分钟可行性尖刺均未通过。§1.1 与 TASK-01 保持未完成，继续工作范围见 [TASK-01](../.spec/tasks/TASK-01-foundation-spike.md)。

全局 `openspec` 与 `pnpm` 启动脚本在接手环境中失效且未修复；本次未重跑 OpenSpec 结构校验，历史通过记录不代表当前工作树通过。已执行的统一门禁命令、实际结果与未测项见[校验报告](../docs/validation-report.md)。
