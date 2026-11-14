# Proposal

## Why

现有真实录音采集界面已经接入原生确认、暂停/继续、真实振幅和左滑安全结束，但全项目尚缺统一的 Material Design 3 设计与交互基线。参考图提供了顶部波形与控制、中央大字号文字、底部胶囊导航的布局和明暗关系；需要将其转译为可访问、连续响应且与真实业务状态一致的界面。

图片中的深蓝灰背景与橙红控制表达暗色层次和主要操作的强调关系。实际色彩必须由 Material 3 ColorScheme 配色引擎与组件语义色生成，例如使用 ColorScheme.fromSeed，不采样图片 RGB 或建立硬编码品牌调色板。系统壁纸配色若无既有接入，不属于本次必须项，不要求为此安装 dynamic_color。波形使用当前主题最亮的前景角色，并在两端以 alpha 渐隐。

## What Changes

- 确立 Material Design 3 为全项目设计基线，统一 ColorScheme、Typography、形状、组件主题、Material 状态层与动效。优先 Flutter Material 组件库，仅为真实波形或必要的状态形状新增自绘。
- 在现有录制界面落实顶部宽波形胶囊及右侧主控制、中央大字号内容区域、底部列表/录音/设置三项胶囊导航。参考图中的附属右侧图标仅表达状态，不增加额外按钮。
- 将参考图的深蓝灰 surface、橙红控制转为暗色 surface 层级与主要操作语义；具体色值完全服从 Material ColorScheme，最亮前景波形不采用控制强调色。
- 为所有应用内操作及形状、颜色、内容、布局变化建立连续、平滑、可打断的反馈；按下立即有缩放、Material 状态层或形状反馈，未触发、滑出与取消也平滑回弹。
- 建立真实可切换且保留页面状态的最小三页导航。历史与完整设置后端尚未接入的部分明确展示能力状态，不填充假条目、假配置或无效操作。
- 保留现有 RecordingController 与原生确认的录音安全契约、显式“仅录音”选择、暂停冻结、真实位移左滑阈值及封存失败证据。中央当前只呈现真实录音状态，不能用假转写填充参考布局。
- 将设计规范覆盖到现有 reading 组件的将来实施验收，包括阅读锚点、手势竞争和减少动态效果；本次不宣称尚未接入页面或完整阅读功能已经完成。

## Capabilities

### New Capabilities

- `design-system`: 全项目 Material Design 3 语义配色、组件与状态层、连续反馈、减少动态效果及可访问性基线。
- `live-transcript`: 在主规格尚为空时新增录制界面的布局、真实最小三页导航、波形视觉与交互兼容性契约；与既有未归档变更同名能力关联，归档前需合并去重。

### Modified Capabilities

无。`openspec/specs/` 当前尚无对应主规格；本变更的两个 delta spec 均使用 `ADDED Requirements`，不对空主规格写 `MODIFIED Requirements`。同名的既有未归档规格是关联来源，不表示它已经归档或通过功能验收。

## Impact

- 设计与 Flutter 界面：应用主题、组件主题、通用反馈、现有录制页及页面保留外壳；复用现有动效 token 与业务控制器，不另建平行约定。
- 后端与数据：不新增录音、历史查询、设置持久化或 ASR 的伪实现；不修改既有音频块、会话、不可变证据和版本语义。
- 可访问性：语义标签、状态播报、触控目标、对比度、文字缩放、安全区及系统减少动态效果均作为验收条件。
- 验证：实现已落地，主 agent 的录音目录测试最终 40 项通过、Web 构建通过，首轮 Flutter analyzer 无问题；Edge 两种视口验证三页布局与导航。Android 离线构建因缺少缓存失败，OpenSpec 严格校验未运行，详见 [实际证据](tasks.md#evidence)。未下载或安装依赖，不以局部验证宣称完整门禁通过。
- 环境：Flutter devices 只列出 Linux，当前无 Android 连接，本次真机界面、TalkBack 与真实采集交互仍待验收。浏览器无原生通道，正确显示连接错误并禁用录音；浏览器及历史真机证据均不能替代本次 Android 验收。
- 任务边界：不修改或勾选旧 TASK-04，不将前置 TASK-01 尖刺、原变更 §1、完整三页功能或完整转写标为完成。

## Out of Scope

- 完整历史查询/回听/导出链路、完整设置持久化、模型管理与 BYOK 后端接入。
- 实时 ASR 与三级语义阅读的完整接线，以及所有现有 reading 组件的本轮迁移声明。
- 从图片逐像素临摹颜色、自建品牌色常量、为标准按钮和导航重写一套自绘 Material 组件。
- 以样例 PCM、随机波形、预写转写、假历史数据或会自动改变但不保存的设置制造完成感。

## References

- [既有录制与阅读契约](../add-live-semantic-transcript/specs/live-transcript/spec.md)。
- [既有采集与证据安全契约](../add-live-semantic-transcript/specs/audio-capture/spec.md)。
- [既有完整实施任务](../add-live-semantic-transcript/tasks.md)，尤其 §1、§4、§5。
- [TASK-04 限定录音切片](../../../.spec/tasks/TASK-04-flutter-ui.md)及 [TASK-01 前置尖刺](../../../.spec/tasks/TASK-01-foundation-spike.md)。
- [本变更设计](design.md)、[实施与验收任务](tasks.md)。
