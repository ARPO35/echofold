---
id: REQ-speech-transcription
version: 1
priority: normal
---

# 本地 ASR、GAP 与重识别

ASR 仅在设备上运行，音频/PCM 不上传；X-ASR 480ms INT8 单模型候选，权重不进 APK，版本化清单、签名验证、SHA-256、防回滚、更新、回滚与租约/pending-delete；区分 Partial/Final，Final 为不可变 L0 并绑定 UTC/单调/实际音频时间与有序块引用；最旧未处理 PCM 排队时延超过 15000ms 时插入居中 `...` 的不可变 GAP 并跳到最新音频，只丢 ASR 识别副本不删证据音频；所有文本段与 GAP 可用会话固定同一模型离线重识别，成功新增版本且不覆盖原证据。

## 成功标准

- 模型决策前冻结精确工件/revision/hash 与预注册准确率、速度、资源预算。
- 15 秒跳读按单调时钟触发，GAP 保留跳过区间与 ASR_OVERLOAD 原因。
- 见 `openspec/changes/add-live-semantic-transcript/specs/speech-transcription/spec.md` 的 10 条 Requirement 与其 38 个 Scenario 为准。
