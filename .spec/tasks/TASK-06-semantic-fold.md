---
id: TASK-06-semantic-fold
status: todo
priority: normal
implements: REQ-semantic-zoom
blocked-by: [TASK-03-asr-stream, TASK-05-reading-scroll]
size: L
---

# 三级语义阅读、折叠与编辑

对应 `tasks.md` §6（6.1–6.10）。

## 描述

L0–L3 节点与版本模型（L0/GAP/跳过区间不可变、AI/USER/重识别新增版本）；有序 sourceFinalIds[] 与 `foldDueAt=max(sourceFinal.finalizedAt)+当前配置延迟`，配置变化重算未到期段、已到期不重置、多源不任取一个 FINAL 时间；L3→L2→L1 单击逐级展开与双击最短档（只提交一个语义操作）；手动展开写入 ReaderContext 并锁定、双击解除、换会话/重启清除；到期未就绪保持全文、结果迟到立即折叠、最终失败保留全文/GAP 与重试；长按菜单编辑/回听/复制/重新识别（复制标明层级，不伪装 AI 文本或 GAP 为原始 ASR）；L1 用户编辑、原始差异对照、用户版本优先、删除用户版本回退；编辑/重识别后按 sourceVersions[] 与规范化 sourceSetHash 选择性过期重建且拒绝旧任务晚到覆盖；引用定位、跨块回听与录音中「请先手动暂停」提示；版本/多源/引用/锁定/迟到结果/依赖/复制测试。

## 验收

- [ ] §6 全部 10 项完成，含 6.10 测试覆盖多 FINAL 最大 finalizedAt、配置变化、迟到立即折叠与失败回退。
- [ ] L0、GAP 和旧版本始终可回查。

## 说明

- 当前状态：tasks.md §6 共 10 项，0 项 `[x]`。
