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
