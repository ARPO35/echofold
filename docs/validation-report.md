# 文档校验报告

接手记录日期：2026-11-14；历史结构校验日期：2026-11-09。本报告区分历史记录、当前配置与本地依赖元数据核对，以及尚未执行的验证，不是功能、性能或完整语义一致性报告。

## 历史环境与范围

- 历史执行环境记录：`@fission-ai/openspec` 1.16.1，Schema 为 `spec-driven`，Change 为 `add-live-semantic-transcript`。
- 历史命令使用了动态标签 `@latest`，不能据标签本身保证未来复现；后续复核必须显式固定为 `@fission-ai/openspec@1.16.1` 或记录新的精确版本。
- 历史检查只覆盖当时的 OpenSpec 结构和部分文档静态检查，没有执行实现、真实设备、ASR 准确率、语义内容或完整跨文档一致性验收。

## 历史结构校验记录

当时执行：

```bash
pnpm dlx @fission-ai/openspec@latest validate add-live-semantic-transcript --strict --no-interactive
```

当时记录的结果：`Change 'add-live-semantic-transcript' is valid`。

当时补充静态检查记录：

- 7 个能力规格，49 项 Requirement，106 个 Scenario。
- `tasks.md` 当时有 83 项未完成任务；该数量不代表本轮修订后的任务数量或完成度。
- 当时 Markdown 内部相对链接检查未发现缺失。
- 当时检查并移除早期草稿中的远端 ASR、默认 24 小时删除、五级首版和左滑自动开始新录音等旧决策。

以上均为历史记录，不表示本轮修订后已重新执行，也不证明模型选择、性能预算或实施可行性已经通过。

## 2026-11-09 固定版本结构校验（历史）

执行日期：2026-11-09。环境：Node.js 22.23.1、pnpm 11.25.0、`@fission-ai/openspec` 1.16.1、Schema `spec-driven`。

执行：

```bash
pnpm dlx @fission-ai/openspec@1.16.1 validate add-live-semantic-transcript --strict --no-interactive
```

实际结果：

```text
Change 'add-live-semantic-transcript' is valid
```

补充静态检查：

- 7 个能力规格，58 项 Requirement，181 个 Scenario。
- `tasks.md` 共 91 项未完成任务。
- 项目内 15 份 Markdown 的 7 个相对链接均存在，缺失 0。
- `openspec status --change add-live-semantic-transcript` 显示 proposal/specs/design/tasks 4/4 规划产物完整。

以上只证明 2026-11-09 当时的固定版本 OpenSpec 结构校验和所列静态检查通过。**该轮未执行实现、真实设备、ASR 准确率、语义内容或完整跨文档行为验收，不产生任何本项目实测结果。** 预注册门槛、技术尖刺、功能测试和真实设备性能仍为待完成项；设计进入完整实现前必须先完成预注册冻结、双设备 30 分钟可行性尖刺和相关评审。

## 2026-11-09 修订内容（历史）

已同步研究证据分级、预注册门槛、ASR 15 秒 GAP 与逐段重识别、原子音频块占位、所有队列有界、三时间域、多源身份、ReaderContext、标题继承、HTTPS/重定向、模型租约、低存储停止顺序、双设备长测和完整故障矩阵。`docs/evaluation.md` 是相应质量、性能与发布出口的权威验收基线；本报告不替代它。

## 2026-11-14 接手基线与当前验证范围

本轮优先恢复 OpenSpec §1.1 的本地开发入口与可追溯记录。仓库已有 Flutter/Kotlin 模块及部分后续实现勾选，不能因此反推 §1 前置 gate 已通过；本轮不推进 §2.8 或 UI。TASK-01 仍为 `todo`，§1 的 1.1–1.11 均未勾选。

接手环境记录：Flutter 3.47.9（`/home/lzx/dev/tools/flutter/bin/flutter`）、Dart 3.13.7、Android SDK（`/home/lzx/dev/tools/android-sdk`）、JDK 21（`/home/lzx/dev/tools/jdk21`）、`/usr/bin/uv`、`/usr/bin/make`。PATH 中没有 Flutter/Java；本机 `app_shell/android/local.properties` 已记录 `sdk.dir` 与 `flutter.sdk`。全局 `pnpm`、`openspec` 启动脚本失效，本轮未修复、未重装、未修改全局工具，也未下载新软件。**本轮没有执行 OpenSpec 严格结构校验，不能沿用历史通过状态。**

根 Makefile 的共享契约为 `make build`、`make test`、`make lint`，允许覆盖 `FLUTTER`、`JAVA_HOME`、`ANDROID_HOME`；Flutter 在 `app_shell` 工作，Android 使用 `app_shell/android/gradlew`，Python 通过 `uv` 执行。入口不额外执行工具安装命令，但默认 `uv run` 仍会解析并同步项目依赖，Gradle 仍会解析依赖，wrapper 在分发版未缓存时可能下载 Gradle，因此默认命令可能联网。本轮使用离线参数与已有 wrapper 缓存，没有下载依赖或安装包，uv 仅创建本地 `.venv`；Gradle `--offline` 不控制 wrapper 分发版下载。已执行门禁的命令与实际结果见下文，未执行项不预填通过。

### Android SDK 与工具配置证据

| 范围 | 当前配置 | 读取依据与限制 |
|---|---|---|
| 应用 `app` | minSdk 29；compileSdk 36；targetSdk 36 | `app_shell/android/app/build.gradle.kts` 显式 minSdk 29；compile/target 引用 Flutter 扩展；本机 Flutter SDK 的 `packages/flutter_tools/gradle/src/main/kotlin/FlutterExtension.kt` 默认值均为 36。不是 APK manifest 实测结果，换 SDK 后须重核 |
| `capture_android`、`asr_android` | minSdk 29；compileSdk 36；未单独设置 targetSdk | 各模块 `build.gradle.kts`；最终应用 targetSdk 由 `app` 决定 |
| `core` | 纯 JVM，无 Android SDK 配置；JVM 目标 17 | `app_shell/android/core/build.gradle.kts`；其他模块源码 JVM 目标也为 17 |
| 构建插件 | Gradle 9.3.1；AGP 9.1.0；Kotlin 2.4.0 | `gradle/wrapper/gradle-wrapper.properties` 与 `settings.gradle.kts`；本轮只是读取配置，不代表兼容性验证 |

尚未核验商店当前目标 API 政策、完整插件平台约束或构建产物合并后的 manifest；API 数值不构成 Android 10 及以上设备均兼容的结论。

### 依赖许可：已核对的本地证据

直接依赖范围来自 `app_shell/pubspec.yaml`，精确版本来自 `app_shell/pubspec.lock`，Android 库来自 `app_shell/android/gradle/libs.versions.toml` 与各模块依赖声明。lockfile 的版本/哈希本身没有许可含义；下表只依据已读取的本地 LICENSE 文本或缓存 POM 的 `<licenses>` 字段，不按包名推断，不代表完整发布许可审查通过。

Pub 证据位置统一为本机 `~/.pub-cache/hosted/pub.dev/<包名>-<锁定版本>/LICENSE`；表内版本均与 lockfile 对齐。覆盖全部 13 个直接 hosted 运行依赖和 3 个直接 hosted 开发依赖：

| 包与锁定版本 | 本地 LICENSE 文本核对结果 |
|---|---|
| `audio_session` 0.2.4、`cupertino_icons` 1.0.9、`dio` 5.11.1、`drift` 2.35.4、`drift_flutter` 0.3.1、`file_picker` 13.3.3、`uuid` 4.6.0 | MIT 文本（保留版权及许可告知） |
| `just_audio` 0.10.6 | 包自身 MIT；同一 LICENSE 还明确包含 ExoPlayer 的 Apache-2.0 文本，不能仅标作 MIT |
| `crypto` 3.0.8、`flutter_secure_storage` 11.2.0、`intl` 0.20.3、`path_provider` 2.1.6、`share_plus` 13.3.1 | BSD-3-Clause 文本（源码/二进制告知及不得背书条款） |
| `drift_dev` 2.35.3 | MIT 文本；开发依赖 |
| `build_runner` 2.17.1、`flutter_lints` 6.0.0 | BSD-3-Clause 文本；开发依赖 |
| Flutter SDK 顶层 `/home/lzx/dev/tools/flutter/LICENSE` | BSD-3-Clause 文本；直接 SDK 依赖 `flutter` / `flutter_test` 未使用 hosted 版本；此顶层证据不覆盖 SDK 内所有第三方工件 |

Gradle 证据位置为本机 `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<缓存哈希>/<artifact>-<version>.pom`；已读取下列 5 个版本目录直接声明对应 POM 的许可字段：

| Maven 坐标与版本 | POM 声明 | 用途 |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | Apache-2.0 | `core` 运行依赖 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` | Apache-2.0 | 单元测试 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` | Apache-2.0 | `core`、`asr_android` 运行依赖 |
| `org.junit.jupiter:junit-jupiter:5.13.4` | Eclipse Public License v2.0（EPL-2.0） | 单元测试聚合依赖 |
| `org.junit.platform:junit-platform-launcher:1.13.4` | Eclipse Public License v2.0（EPL-2.0） | 单元测试运行时 |

POM 原文同时给出 Apache 的 `https://www.apache.org/licenses/LICENSE-2.0.txt` 或 Eclipse 的 `https://www.eclipse.org/legal/epl-v20.html`，本轮未联网复核。上述是发布元数据声明核对，不等同于 JAR/AAR 全部内嵌 LICENSE/NOTICE 或最终解析图核验。

### 许可缺口与明确未测项

- 未逐项审查 Pub 传递依赖、各平台实现包及其原生二进制/下载工件；例如 lockfile 中的 `sqlite3` 3.5.2、`sqlcipher_flutter_libs` 0.7.0+eol、`sqlite3_flutter_libs` 0.6.0+eol 不能用 drift 的 MIT 文本代替核验，也未据名称认定底层数据库许可。
- 未核对 Gradle 最终解析依赖图、JVM 变体与全部传递依赖的许可、AGP/Kotlin/Gradle 等构建工具的完整第三方告知、Flutter engine/字体等 SDK 第三方许可、最终 APK 的归属/NOTICE 收集与再分发义务。
- sherpa-onnx 实际运行时、X-ASR 工件及可能的量化工具链/转换输出尚未冻结，未进行相应许可核验；评测音频/参考转写的数据来源与许可也未冻结。不能用适配层代码存在代替工件来源/许可证据。
- 接手 `adb devices` 列表为空；未连接设备，未安装或启动应用，真机调试、权限拒绝/撤销、通知/后台前台服务、录音恢复、MediaCodec/MediaMuxer 实际编码参数与 ASR 加载/时间戳均未通过验收。
- 双设备身份与环境、工件哈希/ABI/INT8 来源、评测 manifest/评分器/规范化/预注册门槛、冻结试点、逐设备 30 分钟尖刺与 go/no-go 均未通过；没有 CER/WER、RTF、p95 落后、温度或功耗的本项目实测结论。两小时发布长测也未执行。

以上缺口要求 §1.1 与整个 TASK-01 保持未完成。当前文档及计划见 [Flutter 本地开发说明](../app_shell/README.md)和 [TASK-01](../.spec/tasks/TASK-01-foundation-spike.md)；正式验收仍以[评测与验收](evaluation.md)及[评审决策清单](review-checklist.md)为准。

### 本地统一门禁与 Flutter 修复复核

首轮统一门禁执行以下命令：

```bash
make -k build test lint \
  JAVA_HOME=/home/lzx/dev/tools/jdk21 \
  ANDROID_HOME=/home/lzx/dev/tools/android-sdk \
  FLUTTER=/home/lzx/dev/tools/flutter/bin/flutter \
  UV_FLAGS='--offline --python 3.12' \
  GRADLE_FLAGS='--offline --console=plain'
```

首轮整体退出码为 2。Flutter 单元测试 30 项通过；`flutter analyze` 报告 73 个 issues，包含 `echo_database.dart` 的 `Table.text` 命名冲突、`spring_fold.dart` 缺失物理/渲染类型，以及 `transcript_reader.dart` 的 `SegmentView` / `SegmentViewData` 冲突。

Android 构建、`:core:test` 与 lint 均在 `android_file_picker` 插件配置阶段因离线缓存缺少 AGP 8.5.2 / Kotlin 1.8.22 受阻，没有形成 Android 测试或构建通过结论。Python 路径使用 `uv --offline` 与现有 Python 3.12，但 pytest 未缓存导致依赖解析失败；仅创建本地 `.venv`，没有安装包，`tests` 目录为空且未进入测试收集。未据失败输出安装或下载依赖。这些初始门禁结果不消除许可、真机与前置评测缺口。

随后修复 Drift 表定义命名冲突及 Flutter 物理/渲染类型和界面模型的导入冲突。第二轮 `make -k lint-flutter test-flutter` 中，Flutter 30 项测试通过，analyzer 从 73 项降至 1 项，但已有 `echo_database.g.dart` 占位代码仍有 `unused_field` warning，退出码仍为 2；没有以压制 warning 或删除字段代替真实生成。

使用已有缓存依赖执行 `dart run build_runner build`，实际输出为：

```text
Built with build_runner/aot in 4s; wrote 14 outputs.
```

该步骤生成真实 Drift schema 代码，未执行 `pub get` 或下载、安装依赖。生成命令本身的默认依赖解析机制可能同步依赖或联网，本次成功不能泛化为离线保证，也不能视为迁移、可恢复提交协议或 §1.10 验收通过。

第三轮执行：

```bash
make -k lint-flutter test-flutter FLUTTER=/home/lzx/dev/tools/flutter/bin/flutter
```

实际退出码为 0，关键输出为：

```text
No issues found!
+30 All tests passed!
```

最终通过范围仅为 Flutter analyzer 与现有 30 项 Flutter 单元测试。Android 构建、JVM 测试、Android lint 及 Python 评测工具仍因上述缓存缺口未通过，真机、许可完整性、数据库迁移与前置评测也未验收；不能将第三轮 Flutter 子目标通过写成根 `make build/test/lint` 全部通过或 TASK-01 完成。
