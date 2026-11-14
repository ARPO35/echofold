---
id: TASK-10-md3-recording-surface
status: in-progress
priority: normal
implements: REQ-live-transcript
blocked-by: []
size: L
---

# Material 3 设计系统与真实录制界面

## Description

落实 [establish-md3-recording-surface](../../openspec/changes/establish-md3-recording-surface/proposal.md) 的设计系统、现有录制界面与最小真实三页导航，验收细项以该变更 [tasks.md](../../openspec/changes/establish-md3-recording-surface/tasks.md) 和两份 ADDED delta spec 为准。

用户已授权在现有真实录音切片上推进本项限定工作，`blocked-by: []` 仅表示此限定切片可独立推进，不表示旧 TASK-01 尖刺已通过或旧 TASK-04 已完成。两项旧任务及原变更 §1 的状态和验收均不变。

## Scope

- Material Design 3 为全项目基线，实际配色由 Material 3 ColorScheme 配色引擎及组件语义色生成，ColorScheme.fromSeed 符合要求；参考图仅提供布局、深蓝灰 surface 的暗色层次与橙红主控制的强调关系，禁止图片 RGB 硬编码与独立品牌调色板。系统壁纸配色无既有接入时不属于本次必须项，不要求安装 dynamic_color。
- 优先 Flutter Material 组件库、组件主题和状态层；新增自绘仅限真实波形或必要状态形状。
- 顶部波形与主控制胶囊、中央大字号真实状态、底部列表/录音/设置胶囊导航；附属右侧图标仅为状态示意。波形为主题最亮前景角色，左右 alpha 渐隐，源自真实 PCM 与单调时间。
- 所有已接入操作按下即反馈，形状/颜色/内容/布局连续、平滑、可打断；未触发、滑出与取消连续回弹，不争抢业务手势；系统减少动态效果保留全部功能。
- 最小三页真实切换与状态保留；历史和完整设置后端未接入时明确能力状态，不伪造数据、配置或功能。中央当前仅录音，不填假转写。
- 保留仅录音确认、RecordingController/原生确认、暂停/继续、按实际位移左滑、安全封存与失败证据。
- 现有 reading 组件纳入规范的将来实施验收，不将未接入组件或页面宣称为本轮已完成。

## Notes

- 不在本任务范围内：完整历史与设置后端、实时 ASR/语义阅读接线、前置模型评测与尖刺、完整发布出口。
- Flutter devices 只列出 Linux，当前无 Android 连接，本次设备交互、TalkBack 与真实采集仍待验收；历史真机记录和浏览器结果不能替代。
- 主 agent 已完成本机集成验证：录音目录测试最终 40 项通过，Web 构建通过，首轮 analyzer 无问题，Edge 两种视口三页导航正常；Android 离线构建缺缓存失败，OpenSpec CLI 入口缺失未运行严格校验。未下载或安装依赖，完整记录见变更 tasks.md 的 Evidence。
- 关联来源：旧 [TASK-04](TASK-04-flutter-ui.md)、[TASK-01](TASK-01-foundation-spike.md) 与尚未归档的 `add-live-semantic-transcript`；不改写其状态、门禁或完成声明。

## Acceptance

- [x] Material 3 主题、Material ColorScheme 生成方案、语义颜色、组件主题和状态层有实际证据，无图片 RGB 硬编码或独立品牌调色板；不以缺少系统壁纸取色判定本切片失败。
- [ ] 录制布局、最亮前景双端渐隐真实波形、仅录音能力说明和原生确认行为符合规格，不输出假转写或模拟波形。
- [ ] 按下、成立、未触发、滑出、取消、反向、形状/颜色/内容/布局过渡及减少动态效果均有覆盖，不抢业务手势。
- [ ] 三页真实切换且保留状态，录制不中断；未接入历史与完整设置能力明确，无伪造数据或无效操作。
- [ ] 实际位移左滑、空闲、快速短拖、成功新空闲与封存失败证据保留均无退化；可访问性、文字缩放与对比度有证据。
- [ ] build/test/lint、OpenSpec 结构校验、视觉与 Android 整体验收完成；当前仅定向测试/Web 构建及部分本机证据通过，Android 构建失败，严格校验和真机待验，未预填整体通过。
- [x] 完成声明仅覆盖本切片，旧 TASK-04、TASK-01、前置尖刺和未接入 reading 页面不被标为完成。

## Evidence

2026-11-15 主 agent 提供的完整命令、结果与限制见 [变更验收记录](../../openspec/changes/establish-md3-recording-surface/tasks.md#evidence)。`flutter test --no-pub test/recording` 最终退出码 0，40 项 `All tests passed`；首轮 35 项通过、5 项失败，修复 reduced 零时长 AnimatedSize 与 deactivate 拖动取消后通过。`flutter analyze --no-pub` 首轮退出码 0、`No issues found`，后续两处修复后未宣称再次分析通过。临时 Web 壳使用真实 lib 和原 package_config，最终 debug Web 构建退出码 0；Edge 412×915 与 320×800 的实际三页正常，浏览器 errors 为空，截图仅在 /tmp、未入库。

Web 无原生通道时显示真实连接错误并禁用录音，不证明 Android 采集。Android `:app:assembleDebug` 离线退出码 1，缺 Kotlin 1.8.22、ASM 9.6 等缓存，原始输出 `artifact://25`；没有成功 APK。Flutter devices 只列出 Linux；OpenSpec CLI shim 引用的实际入口不存在，未重跑已知失败脚本。未下载或安装依赖。Android、TalkBack、完整可访问性及严格结构校验等缺口使本任务继续为 in-progress，复合验收缺项时保持未勾选。
