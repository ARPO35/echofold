---
id: REQ-reliability-observability
version: 1
priority: normal
---

# 长时可靠性、降级与可追溯验收

两台已登记参考设备各完成至少两小时发布长测，30 分钟可行性尖刺不得替代，全部数值门槛测试前按固定数据集预注册；进程被杀/服务异常/重启后扫描临时块、数据库事务、模型租约、GAP、重识别与未完成任务，只恢复可证明一致的数据，不自动无感重启麦克风；ASR 提交、GAP、重识别、块状态、BYOK 任务与标题展示用稳定标识与幂等恢复；LLM/网络/模型/编码失败分别显式降级，不使已有音频文字不可用；持续记录各队列固定容量、深度、oldest age、峰值与拒绝原因，超预算采取可解释背压；ASR/GAP/重识别/校订/要点/摘要/标题保留可复现评测输入与精确工件版本，未测项标待测，目标指标不得写成已有结果；提供不含秘密、分区块/ASR/重识别/租约/BYOK/存储/导出状态的诊断。

## 成功标准

- 完整故障矩阵逐例记录设备、工件版本、损失区间与恢复结果，不靠通用降级说明放行。
- PCM 计数、AAC 可解码样本、块区间、校验和与事件日志可对账证明零静默丢失。
- 见 `openspec/changes/add-live-semantic-transcript/specs/reliability-observability/spec.md` 的 7 条 Requirement 与其 20 个 Scenario 为准。
