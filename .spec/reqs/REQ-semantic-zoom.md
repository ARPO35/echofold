---
id: REQ-semantic-zoom
version: 1
priority: normal
---

# 三级语义阅读、折叠与编辑

L0 原始 ASR/GAP/时间戳/音频引用为不可变证据；L1 校订全文、L2 段落要点、L3 会话摘要，L0 是独立证据视图而非第四生成层；GAP 是显式证据而非空白校订；语义段持有按段序去重的有序 sourceFinalIds[]，`foldDueAt = max(sourceFinal.finalizedAt) + 当前配置延迟`，配置变化只重算未到期段、已到期不重置、迟到结果就绪即折叠；手动展开锁定在 ReaderContext 内不被自动收起、双击解除；L1 操作须校验 schema/来源版本/范围/顺序/类型，人工语义验收边界明确；用户编辑产生 L1 用户版本并只使受影响 L2/L3/标题过期；引用可回到原始版本与音频时间。

## 成功标准

- L0、GAP、跳过区间与历史版本永不覆盖，用户版本优先。
- 复制标明当前层级，GAP 不被伪装为可编辑普通文字。
- 见 `openspec/changes/add-live-semantic-transcript/specs/semantic-zoom/spec.md` 的 10 条 Requirement 与其 31 个 Scenario 为准。
