---
id: TASK-01-foundation-spike
status: todo
priority: normal
implements: REQ-speech-transcription
blocked-by: []
size: L
---

# 工件与评测前置、技术尖刺和项目骨架

对应 `openspec/changes/add-live-semantic-transcript/tasks.md` §1（1.1–1.11）。

## 描述

建立 Flutter + Kotlin 多模块工程与静态检查/单测/真机基线；固定两台参考设备；在模型质量或速度决策前固定 X-ASR 实际工件清单（文件名、来源、revision、SHA-256、字节数、ABI、INT8 来源/转换链）；冻结完整评测协议（可复现 manifest、评分器版本、文本规范化、计时方法、预注册逐设备/类别 CER/WER 上限与资源/长测/崩溃尾部损失预算，速度基线为持续 RTF<1 与 p95 ASR 落后 ≤15 秒）；验证 sherpa-onnx 加载/流式/时间戳/中英混合、前台服务、单路 PCM 三路分发、MediaCodec/MediaMuxer 192kbps 与 30/60/300 秒分块对照、Media3 合并可解码性；两台设备各执行 30 分钟可行性尖刺并按预注册门槛作 go/no-go；建立 drift schema 与迁移测试、三时间域映射；评审后若关键假设不成立先更新 design/spec/tasks。

## 验收

- [ ] 1.1–1.11 在 `tasks.md` §1 全部按各自验证方式勾选完成。
- [ ] 预注册方案含哈希与配置，未在看到结果后移动门槛。
- [ ] 尖刺报告明确不替代两小时发布长测。

## 说明

- 当前状态：tasks.md §1 共 11 项，0 项 `[x]`。
- 不在本组范围内：完整实现（§2 起）、真机两小时发布长测（§9）。
- 权威验收口径：`docs/evaluation.md`、`docs/review-checklist.md`。

## 2026-11-14 接手范围与执行顺序

本轮只推进 OpenSpec §1.1 本地开发基线，保留 `status: todo` 及所有验收复选框。仓库已存在后续实现与勾选记录，但 §1 gate 未通过，不据此跳到 §2.8 或 UI，也不把当前代码存在视为前置评测通过。

1. 复用已有 Flutter/Kotlin 多模块结构，在根 Makefile 建立 `make build`、`make test`、`make lint` 入口；支持 `FLUTTER`、`JAVA_HOME`、`ANDROID_HOME` 覆盖，Flutter 工作目录为 `app_shell`、Android wrapper 位于 `app_shell/android`，Python 使用 `uv`。
2. 静态核实 SDK 与依赖版本，以本地 LICENSE/POM 原文记录已核验范围和未覆盖部分，不根据包名推断许可。
3. 统一入口及 Flutter 子目标的实际结果已记录：Flutter analyzer 与 30 项 Flutter 单元测试最终通过；Android 构建、JVM 测试、Android lint、Python 评测工具和真机验证仍未形成通过证据。未运行、受阻或失败项不预填通过。
4. 补齐许可缺口与真实设备安装/调试证据，再按 §1.2–1.11 冻结设备、模型工件与预注册评测，完成尖刺及评审；完整实现仍受 §1 gate 约束。

## 本轮子项证据与剩余出口

| §1.1 子项 | 已有可追溯证据 | 尚缺证据/限制 |
|---|---|---|
| 工程结构与本地入口 | `app_shell` 已有 Flutter、`core`、`capture_android`、`asr_android`；统一 Makefile 契约及工具覆盖见 [app_shell/README.md](../../app_shell/README.md)；Flutter analyzer 输出 `No issues found!`，30 项 Flutter 单测输出 `+30 All tests passed!` | Android 构建、JVM 测试、Android lint 及 Python 评测工具受离线缓存缺口阻塞，未验收；不构成整体门禁通过 |
| SDK 配置 | `app` minSdk 显式 29，compileSdk/targetSdk 经本机 Flutter 3.47.9 扩展解析均为 36；两个 Android library minSdk 29、compileSdk 36；`core` 为 JVM 17 | 当前为配置读取结果；未核对最终 APK manifest、设备兼容性及商店目标 API 政策；更换 Flutter SDK 须重核 |
| 直接依赖许可 | 已核对锁定版本的 16 个 hosted 直接 Pub 包 LICENSE、Flutter 顶层 LICENSE，以及版本目录 5 项 Maven 依赖的缓存 POM；具体版本及声明见[校验报告](../../docs/validation-report.md) | 未完成全部传递/平台/原生工件、构建工具与 SDK 第三方告知、最终 APK NOTICE、运行时/模型及评测数据许可审查 |
| 真机调试 | 接手时 `adb devices` 列表为空 | 未连接设备，安装/启动、权限、通知、后台服务、编码和 ASR 真机链路未通过 |
| OpenSpec 结构校验 | 2026-11-09 的固定版本通过结果保留为历史记录 | 接手全局 `openspec` / `pnpm` 启动脚本失效且未修复；本轮未重跑，不宣称当前结构通过 |

当前环境可用工具位置、配置依据和明确未测项已记录到现有文档，未下载新软件或更改全局工具。§1.1 的许可及真机出口未完成，§1.2–1.11 也无本轮通过证据：设备身份/环境、工件哈希与 ABI/INT8 来源、冻结 manifest/评分器/规范化、预注册门槛与试点、双设备 30 分钟尖刺及 go/no-go 均待完成；两小时发布长测属于后续独立出口。OpenSpec `tasks.md` §1.1 和本任务验收保持未勾选。

首轮 `make -k build test lint` 离线门禁返回退出码 2：Flutter 单测 30 项通过，analyzer 报告 73 个 issues；Android 路径因 `android_file_picker` 插件所需 AGP 8.5.2 / Kotlin 1.8.22 未缓存而停在配置阶段；Python 路径因 pytest 未缓存而解析失败，空 `tests` 目录未进入收集。修复 Drift 命名及 Flutter 类型/模型导入冲突后，第二轮仍因已有生成代码占位实现的 1 项 warning 失败；使用缓存 build_runner 生成真实 schema（`Built with build_runner/aot in 4s; wrote 14 outputs.`）后，第三轮 `make -k lint-flutter test-flutter FLUTTER=/home/lzx/dev/tools/flutter/bin/flutter` 返回退出码 0，输出 `No issues found!` 与 `+30 All tests passed!`。Android/Python 缓存阻塞仍存在，生成成功不等同于 §1.10 的迁移与恢复协议验收，真机与前置评测仍未验收。完整命令与各轮结果见[校验报告](../../docs/validation-report.md)。局部静态分析及单测通过不改变本任务或 §1.1 状态。
