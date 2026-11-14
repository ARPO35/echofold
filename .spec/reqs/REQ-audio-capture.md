---
id: REQ-audio-capture
version: 1
priority: normal
---

# 音频采集与证据分块

显式启动的 Android 麦克风前台服务，单路 PCM 有界分发到本地 ASR、振幅采样与 AAC 编码；AAC-LC/M4A 单声道目标 192kbps、默认 60 秒自动分块，新块先原子占位再初始化编码器；三时间域（UTC 创建/封存、单调采集时钟、实际内容 offset）分离；暂停间隙不计入回听位置；块状态显式区分 PLACEHOLDER/OPEN/SEALING/SEALED/SEAL_FAILED/RECOVERABLE/CORRUPT；空间不足永不自动删除旧音频；左滑以实际位移阈值封存旧会话并新建空闲会话，不自动录音。

## 成功标准

- 正常录制音频零静默丢失，采集/ASR/AAC/持久化队列均有界且可观测。
- 占位失败不伪装录音，封存失败不被报告为完整保存。
- 见 `openspec/changes/add-live-semantic-transcript/specs/audio-capture/spec.md` 的 8 条 Requirement 与其 26 个 Scenario 为准。
