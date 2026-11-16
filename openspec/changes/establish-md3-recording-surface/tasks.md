# Tasks

本变更范围为全项目设计规范、现有录制界面和最小真实三页导航。实现已落地，主 agent 已完成本机 Flutter 分析、录音测试、Web 构建与 Edge 视觉验证；本轮 Android APK 构建、安装及部分真实录音交互已通过，严格结构校验与完整设备验收尚未完成。仅勾选已有完整证据的条目；复合条目仍缺覆盖时保留未勾选，不用局部结果替代整体通过。上轮离线失败保留为历史，本轮仅以临时 Gradle init 脚本使用 Google 官方 Maven Central 镜像，未改依赖、项目仓库配置或全局配置。

旧 TASK-04、TASK-01 和 `add-live-semantic-transcript/tasks.md` §1 的未完成状态保持不变；本变更不能替代完整历史、完整设置、实时 ASR、完整阅读、前置尖刺及发布出口。

2026-11-16 最新范围取代旧中央能力/状态说明和设置未接入说明要求。用户确认真机转写可用，但未确认全面验收；下列新增范围保持未勾选。§3.4 的勾选与 Evidence 仅保留为旧范围历史证据，不证明新设置要求通过。

## 1. Material 3 主题与组件基线

- [x] 1.1 复用并统一 Material 3 主题入口、Typography、形状、ColorScheme、组件主题与现有动效 token，优先采用 Flutter Material 组件库。验收记录标准按钮、导航、弹窗和状态提示的组件来源；新增自绘仅限真实波形或必要状态形状。
- [x] 1.2 以 Material ColorScheme 配色引擎生成语义颜色，ColorScheme.fromSeed 符合要求；surface、前景、主要操作与错误分别读取对应角色。验收覆盖无图片 RGB 硬编码、无独立品牌调色板；参考图深蓝灰/橙红仅表达明暗与强调关系，实际色相服从生成主题。系统壁纸配色若无既有接入不属于本次必须项，不要求安装 dynamic_color。
- [ ] 1.3 通过 Material 状态层与组件主题统一按压、焦点、禁用、选中和忙碌表现，并保留可访问状态。验收不能只覆盖默认静态截图，需覆盖实际组件状态及 Material 主题变化。

## 2. 真实录制界面与安全交互

- [ ] 2.1 落实顶部波形与唯一录音主控制、中央大字号 ASR 结果区、底部三项胶囊导航；删除“开始录音与转写”等重复按钮，开始/暂停/继续仅顶部控制。验收覆盖安全区、小屏和文字缩放，附属图标不新增按钮。
- [ ] 2.2 波形使用当前主题最亮前景角色，左右两端 alpha 连续渐隐，中部清晰；只遮罩波形。验收记录真实 PCM 与单调时间来源、最新右进历史左移、静音真实推进、暂停/保护冻结与空闲清空，禁止模拟样本或随机波形。
- [ ] 2.3 中央始终仅 ASR，空闲、仅录音、无模型和等待结果时留空；删除能力说明、模式、录音/模型状态及操作占位。无模型保留显式仅录音确认，必要错误与确认置于中央外；原生确认驱动开始/暂停/继续，覆盖取消不开始、失败证据保留、忙碌不重复提交。
- [ ] 2.4 保留按实际位移左滑结束、快速短拖不触发、慢拖达标触发、空闲不触发、未达标和取消回弹。验收覆盖封存成功后新空闲会话且不自动录音，封存失败保留旧会话、文件及诊断且不建替代会话。

## 3. 全局连续反馈与最小真实三页导航

- [ ] 3.1 为所有已接入操作在按下时立即启动缩放、Material 状态层或必要形状反馈；未触发、滑出、系统取消、业务拒绝和被其他手势接管时连续回弹。验收覆盖未成立不提交、视觉缩放不缩小命中区域。
- [ ] 3.2 形状、颜色、内容、布局、导航指示和弹窗变化复用统一连续过渡，可从当前值中断及反向，适用的几何运动继承速度。验收覆盖连续点击与反向不归零、不重播、不等待旧动画；反馈不得延迟错误或提前宣称业务成功。
- [ ] 3.3 实现列表/录音/设置真实切换并保留页面状态，初始中间页；切页不重建录音控制器、不暂停或结束业务链路。验收覆盖录制中往返、页面状态保留、同会话 ReaderContext 保留及换会话边界（适用于已接入上下文）。
- [x] 3.4 历史或完整设置后端未接入时明确能力状态，区分未接入、查询为空和失败；不使用假历史、假开关、无效入口或假成功。验收确认本切片只声明最小三页导航，不冒充旧 TASK-04 的完整列表/设置验收。
- [ ] 3.5 验证通用反馈不争抢录音左滑或 reading 滚动/单击/双击/长按。当前已接入交互须完整验收；将来接入的 reading 组件登记适用规则，不预填其功能通过。
- [ ] 3.6 模型导入、刷新及全部模型状态迁至设置，录音页不残留入口或状态；设置只保留真实可操作/可调整项及必要值、校验和错误，删除需求说明、外观动效介绍、当前模式与不可操作占位。逐项验证操作真实生效，不以旧 §3.4 证据代替本项验收。

## 4. 可访问性、证据与验收边界

- [ ] 4.1 验证至少 48dp 触控目标、可访问标签与状态、Material 焦点、普通文字 4.5:1/大字号及必要非文字 3:1 对比度、文字缩放和安全区；装饰图标不增加按钮焦点。波形渐隐不承担唯一状态说明。
- [ ] 4.2 验证系统减少动态效果时取消非必要位移、弹簧过冲与持续装饰运动，并保持即时或简短反馈及全部操作可用；真实位移门槛、确认、取消和错误恢复语义不变。
- [x] 4.3 登记现有 reading 组件的将来实施验收范围：统一主题与组件状态层、按压/取消、形状/颜色/内容/布局变化、阅读锚点、方向性折叠、手势竞争、GAP、ReaderContext 和减少动态效果。登记不代表当前未接入页面已完成迁移，不作为本轮功能完成证据。
- [ ] 4.4 汇总项目适用的 build、test、lint 及本变更 OpenSpec 严格结构校验证据，记录实际命令、工具版本、退出码与可用输出位置。当前结果见 Evidence：最终录音目录 41 项测试、Flutter analyzer 与 Android assembleDebug 通过，上轮 Web 构建通过；严格校验未运行，不能据此声明全部门禁通过。两份 delta 均使用 ADDED Requirements，不对空主规格使用 MODIFIED；不把结构书写方式当作 CLI 校验通过。
- [ ] 4.5 完成主题/组件状态、按下/取消/反向、导航、波形渐隐、字体缩放与减少动态效果的视觉证据。当前已有 Edge 两种视口的真实页面截图和部分 widget/像素测试，截图仅存 /tmp，未入库；不能替代完整交互录屏、字体缩放或设备验收。
- [ ] 4.6 本轮已连接 NX712J 并完成 APK 安装、真实采集、暂停/继续、录制中切页、短拖与达标左滑及音频解码验证；空闲时长残留和主按钮重复语义已修复并经真机复验。TalkBack、大字与系统减少动态效果尚未实机验收，部分通过不替代完整 Android 验收。
- [ ] 4.7 按实际范围维护证据和跟踪，保留未测项，不勾旧 TASK-04、TASK-01 或前置尖刺，不宣称完整历史/设置/转写/reading 已完成。当前完成本机记录但未完成整体出口；归档同名能力前仍需协调既有未归档来源并合并去重。

## Evidence

以下为主 agent 提供的 2026-11-15 实际执行结果；文档编写 agent 未重复执行命令。上轮记录按历史保留，本轮 Android 补充证据见下文。局部通过不等于本变更、最终 APK 或完整项目门禁通过。

| 项目 | 实际命令或操作 | 结果与范围 |
|---|---|---|
| Flutter analyzer | `flutter analyze --no-pub` | 最终修复后退出码 0，输出 `No issues found`。 |
| 录音测试 | `flutter test --no-pub test/recording` | 最终修复后退出码 0，输出 `+41 All tests passed`。覆盖原有录音错误、交互、波形与动效回归，并补充权威 IDLE 的显示归零；此为录音目录定向测试，不是全项目测试通过。 |
| 首轮失败及修复 | 同上录音测试，首轮 35 项通过、5 项失败 | 归因为 reduced 零时长 AnimatedSize 布局断言与 dispose 后拖动 cancel。修复为 reduced 直接返回 child，deactivate 取消不再访问 context 或启动动画；修复后上述 40 项全通过，不隐藏首轮失败。 |
| Web 构建 | 临时独立 Web 壳 symlink 真实 `app_shell/lib`，复用原 package_config；`flutter build web --no-pub --debug --no-web-resources-cdn` | 最终退出码 0。未用 fake 平台替换生产界面；临时壳只提供浏览器运行入口，不作为新增永久产品或 Android 构建证据。 |
| 浏览器视觉 | Edge，412×915 与 320×800，实际切换录制/设置/列表 | 两种视口页面正常，320×800 最终无溢出，浏览器 errors 为空。无原生通道时真实显示连接错误并禁用录音。截图仅存 /tmp、未入库，无永久截图链接；浏览器无法证明 Android 采集、录音服务或 TalkBack。 |
| 上轮 Android 构建（历史） | 在 `app_shell/android` 执行 `JAVA_HOME=/home/lzx/dev/tools/jdk21 ANDROID_HOME=/home/lzx/dev/tools/android-sdk ./gradlew --offline --console=plain :app:assembleDebug` | 退出码 1，Gradle 解析缺少 Kotlin 1.8.22、ASM 9.6 等缓存，当轮未下载或安装依赖。原始输出：`artifact://25`。本轮后续构建成功见下文。 |
| 上轮设备探测（历史） | `flutter devices` | 当时只列出 Linux，无 Android 连接；此为上轮环境记录，不代表本轮设备状态。 |
| OpenSpec 严格校验 | 未运行已知失效脚本 | CLI shim 引用的实际入口 `/home/lzx/.local/share/pnpm/global/v11/2c264e342c50dfd773bc36e243befe4059f649382bc1b067c18d8e33ef72e89d/node_modules/@fission-ai/openspec/bin/openspec.js` 不存在，未为复现已知失败重复运行。严格校验未通过；ADDED 结构与来源关联不能替代工具结果。 |

实现入口为 `app_shell/lib/ui/theme/echo_theme.dart` 的 Material 3 ThemeData/ColorScheme.fromSeed、统一组件主题，及 `ui/motion/` 的动效与按压反馈；标准控件优先复用 Material，新增自绘限于真实波形与必要状态形状。主题生成和组件来源支持 §1.1/§1.2 的限定验收，Edge 三页能力说明支持 §3.4；reading 将来实施范围已在 design/spec 中登记，支持 §4.3。其他条目含未覆盖的主题状态、字体缩放、完整取消/反向矩阵、可访问性、Android 原生或严格校验，保留未勾选。TASK-10 为 in-progress，旧任务与前置尖刺保持不变。

### 本轮 Android 构建与真机证据（含最终修复复验）

设备 NX712J，ADB `D123060104D2`，Android 14，1116×2480 物理像素、density 480、font_scale 1、animator 1；本轮开始时 git 基线干净。未修改手机系统设置，旧用户音频保留未清除。

原 Maven Central 地址返回 403 后，使用 `-I /tmp/echofold-task10-central-mirror.init.gradle` 将 Maven Central 映射至 Google 官方 `https://maven-central.storage-download.googleapis.com/maven2`，初版 `:app:assembleDebug` 输出 `BUILD SUCCESSFUL in 18s`，215 tasks（24 executed），`adb install -r` 返回 `Success`。临时脚本仅通过显式 `-I` 作用于对应调用，未改依赖、项目配置或全局配置。初版日志为本机临时文件 `/tmp/echofold-task10-assembleDebug.log`，初版 SHA-256 `cf28a9acb1342573aa47e8565d3319aa0edc9b71f9f8aaf517f3d38194e93bd3`。最终修复后同镜像 `-I :app:assembleDebug` 输出 `BUILD SUCCESSFUL in 7s`，215 tasks（24 executed），第二次 `adb install -r` 返回 `Success`。最终 APK 路径仍为 `app_shell/build/app/outputs/apk/debug/app-debug.apk`，SHA-256 `19edc09dce6bd9e575903361bafed283be95e10842adde5a7e449c1338b8d5af`。

- 取消仅录音确认前后的 sessions 索引经 `cmp` 比较一致，未创建录音会话。
- 新会话 `ee699bba-4219-4387-b47e-0ce34b766e9f` 真实录音，麦克风前台服务 type `0x80`；真机显示明亮前景波形，双端渐隐。录制中切换列表、设置并返回，仍为同一会话 `RECORDING`。
- 短拖 123 物理 px（41dp）、90ms 不结束；暂停时长 29.020s，时长冻结且控制显示三角继续；继续后慢拖 523 物理 px（174dp）、1200ms 触发结束，前台服务退出。
- 生成两块 `SEALED`，索引时长 29.020 + 1.260 = 30.280s；`ffprobe` 实测 AAC、单声道、48kHz，文件时长 29.013333 + 1.258667s，`ffmpeg` 两块完整解码均退出 0。
- 首轮发现保存后空闲时长残留 30s。修复为 controller 收到权威 IDLE 时归零当前时长并清波形，不改原生历史 snapshot；失败仍保留旧时长。最终真机重启 app，旧 snapshot 对应空闲显示为 0；再新录音并达标左滑保存，`11-fixed-saved.png` 确认“准备录音”及 `00:00:00`。

截图与原生索引证据位于本机临时目录 `/tmp/echofold-md3-device`，未入库，不提供伪造的永久链接。TalkBack、大字与系统减少动态效果尚未实机验证；完整复合条目继续未勾选，TASK-10 保持 in-progress。OpenSpec 已安装 shim 的实际入口缺失，strict 未执行，不安装全局工具。

补充真机观察：空闲达标左滑前后 sessions 经 `cmp` 完全一致。设置页权限入口实际打开 `com.android.settings/.applications.InstalledAppDetails`（`dumpsys mFocusedApp`），Back 返回，未修改权限。语义探测显示权限入口 48dp、导航 108×80dp、主按钮 64dp及中文标签/Tab 选中。首轮主按钮外层有 label 但不可点击、内层 `NAF=true` 无标签可点击且双 focusable；修复为排除子语义并显式提供 onTap，最终 `fixed-saved-semantics.xml` 确认只有一个 `android.widget.Button`，content-desc 为“开始仅录音”，`clickable=true`、`enabled=true`，重复 NAF 内按钮消失。uiautomator dump 开启可访问语义后，EchoMotion 因 accessibleNavigation 显示“已遵循系统：减少动态效果”，不能据此认定系统 reduce 已验收或录屏已证明标准动画流畅。最终系统只读检查为 font_scale 1、animator 1、accessibility_enabled 0、services null、reduce_motion null；TalkBack 未启动，未改系统配置。首轮 logcat 文件为空，不能据此宣称无异常日志。

文件完整性补充：旧 chunks 前后按 id 逐项比较，`jq all=true`，记录全部相等；新块 `sha256sum` 与原生索引 checksum 均吻合，块 0 为 `e1b5c0888abfdd309b850ac7d09be655646b1cb5b98549ad9ab6f90b95f008e6`，块 1 为 `07478c8a70f628e6db33ab1553005d554a7b0dd9f937d3b8383bc8edbca0eea9`。此为本次真实文件与索引完整性证据，不替代完整崩溃恢复验收。
