---
id: REQ-privacy-portability
version: 1
priority: normal
---

# 权限、密钥、保留与导出

录音前明示权限，首次 BYOK 请求前说明发送数据类型与目标主机，音频不隐含离机；API Key 用 Android Keystore 支持的安全存储保存，不进明文数据库、日志、分析事件或导出；音频/文字默认长期保留至用户明确删除，不为继续录音自动删除最早音频；导出按 chunkSeq 合并源音频且源块不变，同编码优先转封装不重复编码；文本导出区分 ASR/AI/USER/GAP/重识别层级并保留引用与三时间域映射；删除仅在用户明确确认后执行并处理进行中任务与文件引用；诊断信息脱敏，默认排除完整转写、音频内容与秘密。

## 成功标准

- 密钥、认证头、完整敏感正文不出现在日志/数据库/导出。
- 半成品导出不标为成功，活动会话与模型租约不被误删。
- 见 `openspec/changes/add-live-semantic-transcript/specs/privacy-portability/spec.md` 的 7 条 Requirement 与其 13 个 Scenario 为准。
