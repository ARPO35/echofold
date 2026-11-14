---
id: TASK-03-asr-stream
status: todo
priority: normal
implements: REQ-speech-transcription
blocked-by: [TASK-02-session-model]
size: L
---

# 本地 ASR、缺口与实时数据流

对应 `tasks.md` §3（3.1–3.10、3.11）。

## 描述

PCM 重采样 16kHz 的有界 ASR 队列，采集线程不等待推理；Partial/FinalCandidate/Final/Health 原生到 Flutter 事件协议与稳定事件 ID；活跃段替换与 FINAL 不可变版本提交（模型工件/revision/hash、会话配置、三时间域、有序 sourceFinalIds[]）；15 秒过载 GAP 协议（单调时钟 oldest-pending-audio 落后、居中 `...`、记录 reason=ASR_OVERLOAD 与跳过区间、清理积压并跳最新音频）；GAP 只跳过 ASR 输入不删证据音频，过载时 AAC/持久化最高优先级；普通段与 GAP 的有界幂等离线重识别（同会话固定模型/配置、无第二模型/远端/联网回退）；重识别版本提交（新增带来源/父版本、仅成功校验通过改默认展示、原始与历史永不覆盖、重复请求不追加）；VAD/端点/标点/长度语义段归并；重复乱序/队列饱和/零静默丢失测试与按冻结 manifest/评分器/预注册门槛的可复现 ASR 基准报告。

## 验收

- [ ] §3 全部 11 项完成，含 3.11（可复现 ASR 基准报告，分栏列作者基准/官方证据/本项目实测）。
- [ ] 注入 >15 秒慢推理验证 GAP 居中、跳过区间、最新音频续识、音频可回听与 p95 指标。
- [ ] 不同硬件数值不混排排名，未实测数值不写成结果。

## 说明

- 当前状态：tasks.md §3 共 11 项，10 项 `[x]`，未完成 3.11。
- 评测工具：`tool/benchmark/`（uv 工作区）。
