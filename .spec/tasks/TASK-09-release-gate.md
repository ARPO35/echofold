---
id: TASK-09-release-gate
status: todo
priority: normal
implements: REQ-reliability-observability
blocked-by: [TASK-01-foundation-spike, TASK-03-asr-stream, TASK-05-reading-scroll, TASK-06-semantic-fold, TASK-07-byok-title, TASK-08-playback-export]
size: L
---

# 可靠性、质量与发布出口

对应 `tasks.md` §9（9.1–9.11）。

## 描述

崩溃后 PLACEHOLDER/OPEN/SEALING/SEAL_FAILED/RECOVERABLE 块、数据库状态与未完成任务扫描修复（只可解码校验通过才恢复索引，损失区间显式标记，不自动无感重启）；脱敏诊断摘要含所有队列容量/深度/oldest age/峰值、实际音频参数与损失区间；两台参考设备各至少两小时发布长测（后台、三页切换、BYOK 断续、自动折叠、ASR 过载/GAP、重识别、恢复），独立于 30 分钟尖刺；执行 `docs/evaluation.md` 完整故障矩阵；PCM 采集计数/AAC 可解码样本/块区间/校验和/事件日志对账证明音频零静默丢失，指定崩溃故障逐例量化尾部损失按预注册预算判定；长列表/GAP/展开折叠/手势打断/ReaderContext 边界/标题继承/减少动态效果的性能与可访问性测试；固定集 ASR 与重识别报告（精确工件/转换、manifest、评分器、设备、线程、温度、失败样例，未测项标待测）；人工语义支持规则验收（冻结评分指南、至少两名独立评审、第三人裁决、严重错误按阻塞缺陷）；依赖许可/隐私声明/模型许可/第三方 NOTICE 检查；按 `docs/evaluation.md` 全部关键出口逐项验收；更新任务状态并用固定精确版本 `@fission-ai/openspec@1.16.1` 严格校验通过后归档，`docs/validation-report.md` 区分结构校验/待复核项/真实功能语义验收。

## 验收

- [ ] §9 全部 11 项完成，30 分钟尖刺不代替发布长测，失败项不放行。
- [ ] `validate add-live-semantic-transcript --strict --no-interactive` 通过后再归档变更。

## 说明

- 当前状态：tasks.md §9 共 11 项，0 项 `[x]`。
- 这是全局出口，依赖其余任务组完成。
