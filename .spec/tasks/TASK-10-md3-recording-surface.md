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
- 本轮已连接 NX712J（ADB `D123060104D2`，Android 14），APK 构建和 `adb install -r` 成功；真实录音、暂停/继续、录制中三页往返、短拖不触发及慢长拖封存已有设备证据。TalkBack、大字和系统减少动态效果仍未实机验证，复合条目保持未勾选。
- 主 agent 已完成本机集成验证：录音目录测试最终 40 项通过，Web 构建通过，首轮 analyzer 无问题，Edge 两种视口三页导航正常。上轮 Android 离线缺缓存失败为历史，本轮以单次临时 Gradle init 镜像构建成功，未改依赖、项目配置或全局配置；OpenSpec CLI 入口缺失仍未运行严格校验。完整记录见变更 tasks.md 的 Evidence。
- 关联来源：旧 [TASK-04](TASK-04-flutter-ui.md)、[TASK-01](TASK-01-foundation-spike.md) 与尚未归档的 `add-live-semantic-transcript`；不改写其状态、门禁或完成声明。

## Acceptance

- [x] Material 3 主题、Material ColorScheme 生成方案、语义颜色、组件主题和状态层有实际证据，无图片 RGB 硬编码或独立品牌调色板；不以缺少系统壁纸取色判定本切片失败。
- [ ] 录制布局、最亮前景双端渐隐真实波形、仅录音能力说明和原生确认行为符合规格，不输出假转写或模拟波形。
- [ ] 按下、成立、未触发、滑出、取消、反向、形状/颜色/内容/布局过渡及减少动态效果均有覆盖，不抢业务手势。
- [ ] 三页真实切换且保留状态，录制不中断；未接入历史与完整设置能力明确，无伪造数据或无效操作。
- [ ] 实际位移左滑、空闲、快速短拖、成功新空闲与封存失败证据保留均无退化；可访问性、文字缩放与对比度有证据。
- [ ] build/test/lint、OpenSpec 结构校验、视觉与 Android 整体验收完成；最终录音目录 41 项测试、analyzer、Android APK 构建安装及部分真机验证通过，空闲时长与主按钮语义修复已复验；严格校验和完整可访问性未完成，未预填整体通过。
- [x] 完成声明仅覆盖本切片，旧 TASK-04、TASK-01、前置尖刺和未接入 reading 页面不被标为完成。

## Evidence

2026-11-15 主 agent 提供的完整命令、结果与限制见 [变更验收记录](../../openspec/changes/establish-md3-recording-surface/tasks.md#evidence)。首轮录音测试 35 项通过、5 项失败，修复 reduced 零时长 AnimatedSize 与 deactivate 拖动取消后曾通过 40 项；本轮最终修复后 `flutter test --no-pub test/recording` 输出 `+41 All tests passed`，`flutter analyze --no-pub` 输出 `No issues found`，均退出 0。临时 Web 壳使用真实 lib 和原 package_config，debug Web 构建退出码 0；Edge 412×915 与 320×800 的实际三页正常，浏览器 errors 为空，截图仅在 /tmp、未入库。

上轮 Web 无原生通道时显示真实连接错误并禁用录音，Android 离线构建退出码 1、缺 Kotlin 1.8.22/ASM 9.6 等缓存，`flutter devices` 只列 Linux，均为当轮历史记录。本轮 `:app:assembleDebug` 通过临时 `-I /tmp/echofold-task10-central-mirror.init.gradle` 使用 Google 官方 Maven Central 镜像，初版输出 `BUILD SUCCESSFUL in 18s`、215 tasks（24 executed），安装成功；最终修复重建输出 `BUILD SUCCESSFUL in 7s`、215 tasks（24 executed），第二次 `adb install -r` 返回 `Success`。APK 为 `app_shell/build/app/outputs/apk/debug/app-debug.apk`，最终 SHA-256 `19edc09dce6bd9e575903361bafed283be95e10842adde5a7e449c1338b8d5af`。初版日志 `/tmp/echofold-task10-assembleDebug.log`、截图与索引 `/tmp/echofold-md3-device` 均为本机临时证据，未入库；未改依赖、项目配置或全局配置。

设备 1116×2480、density 480、font_scale 1、animator 1，git 初始基线干净，未改手机系统设置或清除旧用户音频。取消确认及空闲达标左滑前后 sessions 均经 `cmp` 确认完全一致。真实会话 `ee699bba-4219-4387-b47e-0ce34b766e9f` 的麦克风前台服务 type `0x80`，波形明亮且双端渐隐；录制中列表/设置/返回保持同一 `RECORDING`。短拖 123px（41dp）/90ms 不结束；暂停 29.020s 冻结并显示三角继续，恢复后慢拖 523px（174dp）/1200ms 结束且服务退出。两块 SEALED 索引合计 30.280s；ffprobe 为 AAC mono 48kHz、29.013333 + 1.258667s，ffmpeg 两块完整解码退出 0。

首轮保存后空闲时长残留 30s，已修复为权威 IDLE 归零当前时长并清波形，不改原生历史 snapshot，失败仍保留旧时长。最终真机重启 app 后旧 snapshot 的空闲显示为 0，新录音达标滑保存后 `11-fixed-saved.png` 确认“准备录音 00:00:00”。首轮主按钮双 focusable、内层 NAF 无标签的问题已通过排除子语义和显式 onTap 修复；`fixed-saved-semantics.xml` 确认单一 `android.widget.Button`，content-desc“开始仅录音”，clickable/enabled 均 true，重复 NAF 内按钮消失。其他已探测目标为权限入口 48dp、导航 108×80dp、主按钮 64dp，具有中文标签/Tab 选中；权限入口实际打开系统应用详情并 Back 返回，未改权限。uiautomator dump 触发 accessibleNavigation 后 EchoMotion 显示减少动态效果，不能据此宣称标准动画流畅或系统 reduce 已验收。最终只读系统值为 font_scale 1、animator 1、accessibility_enabled 0、services null、reduce_motion null；TalkBack 未启动，未改系统配置。首轮 logcat 文件为空，不作为无异常日志证据。大字、系统 reduce、TalkBack 与严格 OpenSpec 校验仍未完成；CLI 实际入口缺失，未安装全局工具，TASK-10 保持 in-progress。
