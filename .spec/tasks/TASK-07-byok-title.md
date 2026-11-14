---
id: TASK-07-byok-title
status: todo
priority: normal
implements: REQ-processing-orchestration
blocked-by: [TASK-03-asr-stream]
size: L
---

# BYOK、多源预处理与自动标题

对应 `tasks.md` §7（7.1–7.12）。

## 描述

dio 实现 OpenAI-compatible 配置/连接检查/超时/取消/有界重试/错误分类（默认仅 HTTPS、逐跳检查重定向、跨域不转发 Authorization、密钥不发非目标主机）；flutter_secure_storage/Android Keystore 保护 API Key；首次文字离机说明、目标主机确认、BYOK 关闭零生成请求；L1/L2/L3 请求只含稳定文字与有限上下文、字符/token 预算；structured output 能力探测、严格 JSON 适配、schema 与引用范围校验（格式修复有界，协议声明不作语义正确证明）；SemanticArtifact/ProcessingJob 有序 sourceVersions[] 与可重算规范化 sourceSetHash；幂等键/缓存/源版本提交检查/有界 LLM 队列/优先级/退避（网络失败合并推迟可重建任务，不阻塞音频/ASR/实时文字/用户操作）；足够成段即 L1/L2 预处理、暂停停止冲刷、用户立即整理、相同幂等键不重复、过期结果不默认提交；自动标题与 TitlePresentation（未 revealed 时最新有效版本继承 pendingFirstReveal）；条目实际可见时一次交叉淡化/轻微位移动画，完成后才提交 revealedAt，重启/离开保留待展示、三页切换与列表重建不重播；标题多次更新/展示中离开重启/已展示后更新/旧 sourceSet 晚到测试；脱敏契约样例测试（无效 JSON、越界引用、有序来源/hash 不一致、401/429/超时/断网/HTTP 拒绝、同域/跨域 HTTPS 重定向）。

## 验收

- [ ] §7 全部 12 项完成，含 7.11、7.12 契约与标题测试。
- [ ] 跨域 HTTPS 重定向不转发 Authorization 有契约测试。

## 说明

- 当前状态：tasks.md §7 共 12 项，0 项 `[x]`。
