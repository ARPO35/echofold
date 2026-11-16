---
id: REQ-live-transcript
version: 1
priority: normal
---

# 实时转写界面、滚动与动效

三页底部导航（录音列表、录音、设置）保留状态，切页不暂停后台链路；顶部右进左出的真实 PCM 振幅条及唯一录音主控制，颜色遵循 Material 3 ColorScheme；中央始终仅真实 ASR 结果，空闲、仅录音、无模型或无结果时留空，不放能力、模式、录音/模型状态或操作占位。删除重复“开始录音与转写”等开始按钮，开始/暂停/继续仅顶部控制。必要错误与安全确认在中央外。模型导入、刷新与全部模型状态集中设置；设置仅真实操作/调整项及必要值与错误，删除需求说明、外观动效介绍、当前模式与不可操作占位。

真实 GAP 保留跳过区间并居中展示 `...`；真实内容继续遵循跟随底部/自由阅读、方向性展开折叠与阅读锚点、会话内 ReaderContext、单击/双击/长按、可打断动效与减少动态效果、标题首次实际可见动画及持久化契约。2026-11-16 最新范围取代旧中央能力/状态说明和设置未接入说明要求；用户确认真机转写可用并非全面验收，新增要求仍待实现与验收，既有证据与未验收状态保持原义。

## 成功标准

- 自由阅读时新内容不抢位置，程序锚点补偿不误触发自由阅读。
- 首展动画一次，未 revealed 时最新有效标题继承唯一首次展示资格。
- 以 `openspec/changes/add-live-semantic-transcript/specs/live-transcript/spec.md` 及 `openspec/changes/establish-md3-recording-surface/specs/live-transcript/spec.md`、对应 design-system 规格为准；中央仅 ASR、唯一顶部控制、设置真实项目的最新范围优先于旧界面说明。
