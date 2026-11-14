---
id: REQ-live-transcript
version: 1
priority: normal
---

# 实时转写界面、滚动与动效

三页底部导航（录音列表、录音、设置）用状态保留机制，页面切换不暂停后台链路；顶部右进左出的真实 PCM 振幅条与最右大红录音按钮；GAP 以居中一行 `...` 展示且保留跳过区间；跟随底部/自由阅读互斥状态机、方向性展开折叠与阅读锚点；按会话内存维护 ReaderContext（滚动模式、锚点、手动展开锁定），三页切换保持、换会话或进程重启清除；单击逐级展开、双击最短档、长按段菜单；统一弹簧动效可打断并尊重减少动态效果；自动生成标题在条目第一次实际可见时播放一次动画并持久化。

## 成功标准

- 自由阅读时新内容不抢位置，程序锚点补偿不误触发自由阅读。
- 首展动画一次，未 revealed 时最新有效标题继承唯一首次展示资格。
- 见 `openspec/changes/add-live-semantic-transcript/specs/live-transcript/spec.md` 的 8 条 Requirement 与其 25 个 Scenario 为准。
