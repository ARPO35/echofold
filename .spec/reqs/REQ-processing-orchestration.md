---
id: REQ-processing-orchestration
version: 1
priority: normal
---

# BYOK、多源预处理与自动标题

FINAL 足够成段即立即预处理，不等折叠延迟；Android 直连用户配置的 OpenAI-compatible BYOK，无中继无远端 ASR，首版仅 HTTPS，跨域重定向不转发 Authorization/API Key；只发送稳定文字与有限上下文，不上传音频、GAP 字面量伪文字、无关会话或设备秘密；要求结构化输出并在客户端校验 schema、有序 sourceVersions[]、规范化 sourceSetHash 与引用范围；幂等键含任务类型、sourceSetHash、有序来源、提示词/模型/参数，缓存命中与提交逐项重查来源版本与顺序；有界队列按音频/ASR > 实时 UI > 用户操作 > 标题 > L1/L2 > L3 优先级；自动标题与首次展示分离，未 revealed 时最新有效版本继承 pendingFirstReveal。

## 成功标准

- 过期结果不默认提交，BYOK 关闭后零生成请求，网络失败不阻塞音频/ASR/实时文字。
- 请求、响应回显、缓存键与提交均逐项核对有序来源，不只比单个聚合哈希。
- 见 `openspec/changes/add-live-semantic-transcript/specs/processing-orchestration/spec.md` 的 8 条 Requirement 与其 28 个 Scenario 为准。
