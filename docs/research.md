# 调研与依据

## 1. OpenSpec 与 TextFold

OpenSpec 默认 `spec-driven` 工作流将一次变更放入 `openspec/changes/<change-id>/`，由 `proposal.md`、`specs/<capability>/spec.md`、可选 `design.md` 和 `tasks.md` 构成。新能力使用 `## ADDED Requirements`，每项需求至少有一个可验证场景。设计描述 HOW，规格描述 WHAT，任务描述实现与验证。[1][2]

TextFold 官方 README 将长文组织为全文、简述、关键句、要点、主题等层级，支持全局切层、单段展开、原文对照与导出；低层偏向原文选句，高层才由 AI 概括。[3]

本项目不照搬五层，而采用已确认的三级先行：校订全文、段落要点、会话摘要，另设 L0 原始 ASR 证据。保留的核心思想是**层级、原位展开、原文回查、局部重生成**，并新增 ASR 版本、音频时间和引用校验。

## 2. 本地 ASR 模型对比

> 证据必须分为三类：**模型作者基准**、**官方示例或第三方测试**、**本项目实测**。下表前两类只用于提出假设；测试集、工件、量化方式、硬件和计时口径不同，不得直接排名，也不得写成本项目结果。建议配置仅是尖刺起点，不是最低要求或预算。RTF 越低越快，但 `RTF < 1` 只是实时性的必要条件，不代表端到端延迟或准确率达标。

| 模型 | 非本项目证据及其类别 | 建议测试配置 | 特点 | 公开大小信息与限制 |
|---|---|---|---|---|
| **X-ASR 480ms** | **作者基准**：模型卡报告 LibriSpeech clean/other 3.14/7.57%，WenetSpeech meeting 9.31%；不是 Android INT8 或本项目实测 | ARM64，6GB RAM 起步，中端以上 SoC；均需实测 | 真流式、中英混合、标点；480ms 是块长，不是完整首字延迟 | 早期记录的“压缩包约 134MB、权重负载约 169MB”**未证实**，不得用于下载、存储或内存预算 |
| **Streaming Zipformer 中英 2023 INT8** | **官方示例**：2 线程 RTF 0.108；页面未注明统一硬件与 CER，不是本项目实测 | ARM64，4–6GB RAM；均需实测 | 真流式、集成成熟、模型较旧；标点/时间戳另验证 | 页面所列三个主要文件合计约 190MiB；预算须复核实际工件 |
| **Streaming Paraformer 中英 INT8** | **官方示例**：fp32 RTF 0.21，不是手机 INT8 成绩 | ARM64，6GB RAM；均需实测 | 真流式、中英及部分方言；不支持时间戳 | 页面所列编码器与解码器约 226MiB；预算须复核实际工件 |
| **SenseVoice Small INT8** | **第三方测试**：Galaxy S10 英文 30 秒样本 RTF 0.06，未测 CER，不是本项目实测 | ARM64，6GB RAM；均需实测 | 中英日韩粤、标点/ITN、时间戳；需 VAD 分段，停顿后出整段 | 页面所列解压约 228MiB，另需 VAD；预算须复核实际工件 |
| **FireRedASR2 CTC INT8** | **官方示例**：单线程 RTF 0.173，硬件未注明，不是本项目实测 | 建议 8GB RAM，重点监测热降频；均需实测 | 中英及多种方言/口音；CTC 较快，需 VAD 模拟流式 | 页面所列约 740MiB，首版偏重；预算须复核实际工件 |
| **Whisper Base / Small** | **第三方测试**：同一 Galaxy S10 英文速度样本 RTF 0.13/0.41，不是本项目实测 | Base 6GB；Small 建议 8GB；均需实测 | 多语言、生态广；非原生流式，需重叠切片去重 | 公开参考 Base 约 160MB、Small 约 490MB；预算须复核实际工件 |

来源见 [4]–[10]。截至本次文档修订，没有可写入报告的本项目实测结果，也没有据此通过的 X-ASR Android INT8 模型决策。

首版以 **X-ASR 480ms 单模型路线**为待验证候选，SenseVoice 仅作基准对照，不进入双模型管线。模型决策前必须先完成以下可复现前置：

1. 固定实际获取的每个模型工件的精确文件名、上游来源、revision、SHA-256、字节数、运行时与 ABI；只有实际取得的工件才能称为 INT8。
2. 若上游没有符合要求的 INT8 工件，记录源工件、量化工具及版本、完整转换命令、校准/配置输入和输出 SHA-256；转换产物与作者发布物分开标识，不能沿用作者基准冒充转换后成绩。
3. 冻结数据集 manifest、参考转写、CER/WER 评分器及版本、设备与运行参数；先按试点分布预注册最低 CER/WER，再执行模型 go/no-go 决策。
4. 正常目标为 RTF 持续 `< 1` 且 p95 ASR 落后不超过 15 秒；前者不是充分条件。最低 CER/WER 是独立质量门槛，不得为速度降级放宽，具体数值由冻结的试点结果产生，而不是在本调研中虚构。
5. 在相同已冻结协议下复测中文会议、讲座、噪声、中英混合等固定集；报告分别列出设备、工件和失败样例。

## 3. Flutter 与依赖复用

| 领域 | 优先采用 | 边界与验证 |
|---|---|---|
| 本地 ASR | sherpa-onnx 官方 Android/Flutter 能力 | X-ASR 模型兼容性、线程、内存与长时运行必须做尖刺 |
| 后台任务 | `flutter_foreground_task` 或薄 Kotlin 服务 | 包存在接口不等于长时可靠；核心录音优先原生可控实现 |
| 音频采集/编码 | AudioRecord、MediaCodec、MediaMuxer | 单路 PCM 分发 ASR、AAC 和振幅；避免两个采集器争用麦克风 |
| 波形 | Flutter 自绘轻量采样条，可参考 `audio_waveforms` | 不复用其单文件录音控制器作为核心架构 |
| 回听 | `just_audio`、`audio_session`，必要时 Media3 | 验证跨块 seek、音频焦点和暂停状态 |
| BYOK/模型下载 | `dio` | 默认只允许 HTTPS；重定向逐跳检查，跨域重定向 MUST NOT 转发 `Authorization`；支持取消、超时、有界重试和断点下载，不记录敏感正文 |
| 密钥 | `flutter_secure_storage` + Android Keystore | Key 不进 drift、日志和导出 |
| 本地数据 | `drift` | 响应式查询、事务和迁移；加密方案单独验证 |
| 列表/动画 | Flutter 内建 CustomScrollView、AnimationController、SpringSimulation | 暂不引入大型动画框架或自写物理引擎 |
| 音频合并 | Media3 Transformer | 同格式优先转封装，避免重复编码；失败时再评估重编码 |

## 4. Android 平台与音频约束

Android 官方文档说明，麦克风类型前台服务用于录音器等从后台继续采集的场景，需要对应类型、权限和 `RECORD_AUDIO` 运行时授权；新版本 Android 还检查“使用期间”权限，通常必须从可见 Activity 启动。[11][12]

因此本方案以用户显式记录会话为边界，不做开机即录或隐蔽常驻，并承认系统可能终止进程。持续通知展示状态，短分块和恢复扫描降低损失。

Android `MediaRecorder.setAudioEncodingBitRate` 文档说明平台可能裁剪传入码率；AAC 支持范围依赖编码器和格式。[13] 因此 192kbps 是目标参数，必须记录真实编码结果。

Media3 Transformer 在输入格式已符合输出设置时可转封装，即复制压缩样本而不重新编码，适合评估同参数 AAC 块合并。[14][15]

## 5. 弹簧动画依据

Apple 弹簧参数可用阻尼比表示：

\[
\zeta=\frac{c}{2\sqrt{km}}
\]

运动方程为：

\[
m\ddot{x}+c\dot{x}+k(x-x_t)=0
\]

欠阻尼解为：

\[
x(t)-x_t=e^{-\zeta\omega_nt}\left[y_0\cos(\omega_dt)+
\frac{v_0+\zeta\omega_ny_0}{\omega_d}\sin(\omega_dt)\right]
\]

其中 \(\omega_n=\sqrt{k/m}\)，\(\omega_d=\omega_n\sqrt{1-\zeta^2}\)。Flutter 原生提供 `SpringDescription` 与 `SpringSimulation`，可继承当前速度并中断，不需要手写物理求解器。[16][17]

落地约束：几何用接近临界阻尼弹簧，透明度用短缓动；手势接管继承当前位置和速度；系统减少动态效果时取消弹簧与位移。

## 6. 已确认决策与待验证项的区分

已确认：Flutter 前端、仅本地单模型 ASR、X-ASR 为待验证候选、模型外置、BYOK 手机直连、三级阅读、Final 开始折叠计时、用户校订版本、音频长期保留、目标 192kbps、自动分块、左滑后新建空闲会话、录音中回听需先手动暂停。

必须实现的可靠性行为包括：新音频块先原子持久化占位再初始化编码器；所有 PCM/AAC/持久化/LLM 队列有界，音频不得静默丢弃；ASR 待处理音频落后超过 15 秒时插入显式、不可变 L0 缺口段并跳到最新音频；缺口可用会话固定的同一本地模型离线重识别，结果新增版本而不改写缺口；多源产物保存有序 `sourceVersions` 与规范化 `sourceSetHash`；`ReaderContext` 仅在当前会话和进程内保留；未首次展示的标题由最新有效标题版本继承待展示资格。

待技术验证：真实设备 ASR 准确率/RTF/功耗、AAC 参数支持、30/60/300 秒分块权衡、Media3 跨块无缝合并、插件组合长时稳定性、具体弹簧参数。它们不能写成已有实测结果；冻结前的数值只可作为预注册条件，不得冒充实测。

## 7. 非目标与法律边界

应用只提供用户可见、可停止的记录工具，不提供绕过系统指示、隐蔽采集或通话录音能力。使用者需遵守所在地法律并获得必要的被记录者同意；应用提示不能替代真实授权。

## 8. 参考来源

1. [OpenSpec：spec-driven 工作流与产物格式](https://openspec.dev/docs/schemas/spec-driven)，访问于 2026-11-09。
2. [OpenSpec：变更元数据](https://openspec.dev/docs/configuration/change-metadata)，访问于 2026-11-09。
3. [TextFold 官方中文 README](https://github.com/chenxiachan/textfold/blob/main/README.zh-CN.md)，访问于 2026-11-09；Apache-2.0。
4. [X-ASR 模型卡](https://huggingface.co/GilgameshWind/X-ASR-zh-en/blob/main/README.md)，访问于 2026-11-09。
5. [sherpa-onnx Zipformer 官方文档](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html)，访问于 2026-11-09。
6. [sherpa-onnx Paraformer 官方文档](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-paraformer/paraformer-models.html)，访问于 2026-11-09。
7. [SenseVoice 官方文档](https://csukuangfj.github.io/sherpa/onnx/sense-voice/pretrained.html)，访问于 2026-11-09。
8. [FireRedASR 官方文档](https://csukuangfj.github.io/sherpa/onnx/FireRedAsr/pretrained.html)，访问于 2026-11-09。
9. [whisper.cpp 仓库](https://github.com/ggml-org/whisper.cpp)，访问于 2026-11-09。
10. [第三方 Android 离线识别速度测试](https://voiceping.net/en/blog/research-offline-speech-transcription-benchmark)，访问于 2026-11-09；样本有限，仅作参考。
11. [Android：前台服务类型——麦克风](https://developer.android.com/develop/background-work/services/fg-service-types?hl=zh-cn)，访问于 2026-11-09。
12. [Android：从后台启动前台服务的限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)，访问于 2026-11-09。
13. [Android MediaRecorder API](https://developer.android.com/reference/android/media/MediaRecorder)，访问于 2026-11-09。
14. [Android Media3 Transformer 转换](https://developer.android.com/media/media3/transformer/transformations)，访问于 2026-11-09。
15. [Android Media3 支持格式](https://developer.android.com/media/media3/transformer/supported-formats)，访问于 2026-11-09。
16. [Apple UISpringTimingParameters](https://developer.apple.com/documentation/uikit/uispringtimingparameters/1649764-init)，访问于 2026-11-09。
17. [Flutter SpringSimulation](https://api.flutter.dev/flutter/physics/SpringSimulation-class.html)，访问于 2026-11-09。
