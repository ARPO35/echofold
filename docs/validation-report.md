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

## 2026-11-14 Android 构建与占位首页真机检查

后续联网构建通过显式 `-I /tmp/echofold-central-mirror-20261114.init.gradle` 将 Maven Central 请求指向 Google 官方镜像，仅影响该次 Gradle 调用，没有修改全局仓库配置。`:app:assembleDebug` 输出 `BUILD SUCCESSFUL in 5m31s`（194 tasks），debug APK 为 `app_shell/build/app/outputs/flutter-apk/app-debug.apk`，180113004 字节。构建过程中 Gradle 自动安装了 `/home/lzx/dev/tools/android-sdk` 下的 Android SDK Platform 35 revision 2，已向用户披露；该环境增量不代表新增许可审查通过。

设备为 nubia Z50 Ultra / NX712J，Android 14 / API 34，ARM64，ADB 序列号 `D123060104D2`。用户手动完成安装后，`pm path com.echofold.app` 确认安装在 User 0，版本为 1.0 / versionCode 1，最终包 minSdk 29、targetSdk 36。流式和 push 方式的 ADB 安装均曾因厂商安装会话 `-1` 返回 `SecurityException`，未修改手机安装安全设置。

占位首页检查：`am start -W -n com.echofold.app/.MainActivity` 返回 `Status: ok`，前台 Activity 与进程存活；截图显示“EchoFold / 录音与转写界面将在后续切片接入”。停止进程后的再次启动返回 `LaunchState: COLD`、`TotalTime: 1527`、`Status: ok`。采集的 AndroidRuntime/Flutter 错误日志为空。本记录只证明当时应用外壳的安装与启动，不代表录音、通知、编码、ASR 或 §1 gate 验收通过；此前“未连接设备”的文字是接手时历史状态。

用户随后要求开发录音采集界面，本轮实施范围和仍未通过的依赖见 [TASK-04](../.spec/tasks/TASK-04-flutter-ui.md)。采集界面新增实现的验证须另列实际结果，不能沿用占位首页结果。

## 录音采集界面本地验证

首页接入范围为录音采集页：用户明确确认仅录音、系统麦克风授权、原生确认后开始/暂停/继续/结束、顶部真实振幅、红按钮实际位移左滑和可访问结束按钮、失败会话保护与重连。没有宣称三页导航或 ASR 已接通。

首轮 `make -k lint-flutter test-flutter FLUTTER=/home/lzx/dev/tools/flutter/bin/flutter`：46 项测试通过，analyzer 因新增录音文件的 3 项缺少花括号及 1 项多余导入而失败；其中一个波形拖动测试出现未命中警告，随后改为实际坐标手势验证，未压制警告。补充不可重复 EOS 封存的恢复状态回归后，再执行相同命令，退出码 0，analyzer 输出 `No issues found!`，48 项 Flutter 测试全部通过（原有 30 项，新增录音控制器/widget 18 项），无上述命中警告。

覆盖范围包括权限拒绝不假录音、永久拒绝设置入口、命令防重、原生状态确认、暂停与继续、保存失败保留旧会话、需要恢复时禁止新建、真实振幅单调/容量/时间窗口、暂停冻结、重连/dispose，以及短快/慢长/空闲左滑、窄轨道、减少动画、波形区域不触发结束和窄屏大字。此为 fake 平台边界下的 Dart/widget 证据，不能替代 Android 编码、前台服务、手机实际画面或真实音频解码结果；原生与真机验证另记。

### 2026-11-14 录音采集真机验证（用户手动安装 EchoFold-debug.apk，sha256 f9d22510…，lastUpdateTime 22:02:57）

以下均为真机实际观察，非推断：

- 启动与界面：新录音页显示正常（标题、状态、波形区、96dp 红色按钮、提示文案）。已发现两处体验瑕疵，见下方"已知问题"。
- 开始录音：点击红按钮 → 仅录音确认对话框 → 录音开始；权限此前已授予，系统弹窗路径未在本轮重现（权限拒绝 UI 已有 fake 平台测试覆盖）。
- 前台服务：`dumpsys activity services` 确认 `isForeground=true`、`types=00000080`（FOREGROUND_SERVICE_MICROPHONE），通知渠道 `echofold.recording`（录音状态）存在并绑定前台通知。
- 60 秒分块：受控会话 `d72601e8…` 录 128.06s，产生 3 块 SEALED：[0,60000)、[60000,119520)、[119520,128060)，与索引 capturedStartMs/capturedEndMs 完全一致。
- 暂停冻结：暂停后 22:08:11 与 22:08:14 两次截图均为 00:01:59，波形冻结为灰色；继续后新块从 119520ms 开始，暂停期间未计入时长。
- 左滑结束：红按钮左滑触发保存，回到"准备录音"空闲态。
- 文件与解码：`run-as` 拉取全部 6 个 M4A（三会话），`ffprobe` 均为 AAC-LC、48kHz、单声道；`ffmpeg` 全部完整解码无错误；实测码率约 194kbps（索引 bitRate 192000 为配置值，非实测）；实测响度 RMS -36~-48dB、峰值 -6~-17dB（除一块峰值 +1.4/+3.1dB），非静音。
- 时长对账：受控会话界面 00:02:08 = 三块合计 128.06s（按实际接受 PCM 帧数累计）；后台会话块 40.98s 与操作时间线一致。
- 后台采集：录音中按 HOME 回桌面 15 秒后返回，时长持续增长（00:00:24），波形持续更新；该会话最终单块 40.98s 解码正常，确认前台服务在后台持续采集。

已知问题（不阻塞本轮交付，待后续切片处理）：

1. 空闲态时长残留：安装后首次进入（或一次录音保存后返回空闲态）时，时长显示残留上次会话值（如 00:01:28、00:02:08）而非 00:00:00。疑似 UI 层未在 IDLE 快照时重置本地显示，待查 `recording_page.dart`/`recording_controller.dart` 的时长来源。
2. 首个峰值超 0dBFS 的块（4b09486e-0 峰值 +1.4dB、d72601e8-0 峰值 +3.1dB）来自第一块；后两块峰值均 ≤-6dB。可能与用户试录时近距离说话有关，但也提示编码链路可能缺增益控制，记录待观察，本轮不修。

以上验证覆盖仅录音主路径；三页导航、ASR、录音列表、损坏块恢复入口仍未实现，OpenSpec 任务保持未验收。测试产生的 3 个会话文件保留在设备上未清理。

### Android 验证尝试与当前存储边界

首次原生门禁使用 `JAVA_HOME=/home/lzx/dev/tools/jdk21 ANDROID_HOME=/home/lzx/dev/tools/android-sdk` 执行 `./gradlew --offline --console=plain :app:assembleDebug :capture_android:testDebugUnitTest :core:test`，在第三方插件 classpath 依赖解析阶段失败，尚未进入新增 Kotlin 编译。随后复用临时镜像脚本的调用因脚本已不存在而立即失败；重新在 `/tmp` 创建仅通过 `-I` 显式加载的脚本后继续构建，最终结果须另记，不将重试写成通过。

当前仅录音组装复用已有 `FileSessionStore`、`FileChunkRepository`、占位/封存协议与 AAC/MediaMuxer，实现新增应用到采集模块的依赖。配置修改前保留 `/tmp/echofold-app-build.gradle.kts.capture-native.bak`、`/tmp/echofold-AndroidManifest.xml.capture-native.bak`；对比结果仅有 `implementation(project(":capture_android"))`，manifest 无差异。没有修改全局配置、Pub 缓存或手机安装安全设置。

文件仓储仍属于当前采集切片的实际持久化边界，最终 Drift/SQLite 事务与迁移未接入、未验收。封存空间使用 core `SpaceBudget` 的设备实现实际预分配；元数据码率来自编码器格式或配置回退，不等于测得的码流码率。实际音频质量、可解码性和分块/暂停时间对账必须以设备文件验证为准。不可重放的 EOS/封装失败保留原会话和文件并进入待恢复状态，当前没有用户恢复入口，不宣称实现 §9.1。

恢复临时脚本后，执行 `./gradlew -I /tmp/echofold-central-mirror-20261114.init.gradle --console=plain :app:assembleDebug :capture_android:testDebugUnitTest :core:test`（上述 JDK/SDK 环境）返回 `BUILD SUCCESSFUL in 49s`，222 tasks（53 executed / 169 up-to-date）。JUnit XML 汇总：采集模块 88 项、Core 202 项，failure/error/skipped 均为 0。APK 当轮为 202852429 字节；`aapt dump badging` 确认 minSdk 29、targetSdk 36，并包含 RECORD_AUDIO、FOREGROUND_SERVICE_MICROPHONE 等原有采集 manifest 合并声明。

构建输出仍有既有 Kotlin Gradle 插件迁移和 SDK XML 工具版本警告；另发现编码能力可空访问及 service 多余 safe-call 警告，随后定点修正，并补充故障测试的失败清理，最终重建结果须另记。以上 JUnit 证据覆盖的是 JVM/fake 设备接口，不代表真实 MediaCodec、手机后台策略、通知可见性或 60 秒设备分块已验收。

最终定点修订后执行 `:app:assembleDebug :capture_android:testDebugUnitTest :app:lintDebug :capture_android:lintDebug`（同一临时镜像与 JDK/SDK）返回 `BUILD SUCCESSFUL in 1m 37s`，394 tasks（197 executed / 197 up-to-date）。最终 APK 为 202847480 字节，已通过 ADB push 到设备 `/sdcard/Download/EchoFold-debug.apk`，并发出打开系统下载列表的 Intent。配置事前备份在差异核对、构建和 lint 通过后已清理。

手机尚未确认更新安装时的截图为全黑；随后用户手动完成更新安装（22:02:57），真机验证结果见上方"2026-11-14 录音采集真机验证"一节。

### 2026-11-14 连续振幅曲线与逐帧移动

用户指出逐点胶囊条不符合预期，要求归一化后连接前后采样点、曲线插值，并尽量按屏幕刷新率移动。本轮将绘制改为固定 −60～0dBFS 归一化的连续对称包络，三次 Bézier 控制点限制在段端点范围内，避免插值过冲；不把 RMS/peak 伪装成带正负相位的原始 PCM。保留真实音频时间轴和批量投递，Flutter vsync 驱动画布平移，缓存路径不逐帧重算；暂停、后台、TickerMode 关闭、减少动态效果或断流追到最后真实样本时停止持续刷新。

实际验证：Flutter analyzer 输出 `No issues found!`；曲线几何 8 项通过，动画与录音页面 17 项通过，包含 100Hz/120Hz 帧间隔像素向左变化、批次间不回跳、暂停冻结、恢复不计暂停墙钟、后台/TickerMode、减少动态效果及断流停止。首轮像素测试因图像读取留在 fake async 中等待而中断，改为 `tester.runAsync` 后上述 17 项全部通过。未执行无关 Core/Android 单元测试或全仓门禁。

profile 构建首次离线失败于未缓存的 profile embedding/AndroidX 依赖，随后使用既有单次 Maven 镜像联网构建通过（25s），定点修复 analyzer 提示后最终离线 `:app:assembleProfile -Ptarget-platform=android-arm64` 通过（19s）。用户报告 USB 安装已恢复可用，实际 `adb install -r app-profile.apk` 返回 `Success`，冷启动返回 `Status: ok`、`TotalTime: 767`。已在设备录音页面实际查看，用户反馈“我看了，效果可以”。设备支持 60/120Hz，但本轮没有取得稳定 120fps 的 profile 帧耗时/提交间隔测量；vsync 驱动与像素测试不等于真机跑满 120Hz，不能宣称该性能指标已通过。没有修改全局配置、手机安全策略或安装新软件；TASK-01/TASK-04 整体状态保持未验收。
