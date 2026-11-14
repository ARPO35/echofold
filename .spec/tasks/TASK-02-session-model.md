---
id: TASK-02-session-model
status: todo
priority: normal
implements: REQ-audio-capture
blocked-by: [TASK-01-foundation-spike]
size: L
---

# 会话、录音与模型管理

对应 `tasks.md` §2（2.1–2.10）。

## 描述

实现 RecordingSession/AudioChunk 与录音状态机；新块 BLOCKED/占位协议（先停分发、原子持久化 PLACEHOLDER、再初始化编码器、成功后恢复分发）；OPEN/SEALING/SEALED/SEAL_FAILED/RECOVERABLE/CORRUPT 转换与正常封存空间预留；开始/暂停/继续/停止的幂等原生命令；AAC 目标 192kbps、默认 60 秒自动分块、实际参数持久化与偏差诊断；版本化模型清单、HTTPS 下载、内置公钥签名验证、签名代数防回滚、手动导入、断点续传、SHA-256、原子安装；运行时/ABI 兼容检查、更新/回滚/删除租约与 pending-delete；容量预警、硬下限封块暂停、恢复后等待用户明确继续，永不自动删除旧音频；生命周期/三时间域/占位/封存故障点测试。

## 验收

- [ ] §2 全部 10 项完成，含 2.8（无模型/损坏/初始化失败的显式选择）。
- [ ] 失败状态不冒充正常保存；暂停间隙不计入 capturedStartMs/capturedEndMs 或回听 offset。
- [ ] 活动会话/重识别固定并租用同一模型版本，删除请求只转 pending-delete。

## 说明

- 当前状态：tasks.md §2 共 10 项，9 项 `[x]`，未完成 2.8。
- `[x]` 是文档标记，不等于功能/语义验收通过。
- 代码位置：`app_shell/lib/`、`app_shell/android/`。
