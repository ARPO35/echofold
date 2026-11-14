---
id: TASK-08-playback-export
status: todo
priority: normal
implements: REQ-privacy-portability
blocked-by: [TASK-02-session-model, TASK-06-semantic-fold]
size: M
---

# 回听、导出与删除

对应 `tasks.md` §8（8.1–8.7）。

## 描述

just_audio/audio_session 或 Media3 播放桥，支持跨块及 GAP 跳过区间定位、暂停状态播放、音频焦点，录音中只提示手动暂停；Media3 优先转封装的会话音频合并（临时文件、时长校验、原子产物提交、不插入暂停静音、源块不变）；不兼容编码时明确重编码路径与产物参数展示，不默认牺牲音质；有界导出队列、导出前空间检查、取消、失败清理、源块保护，半成品不标成功；Markdown 与 JSON 导出区分 ASR/AI/USER/GAP/重识别层级，保留父版本、有序 sourceFinalIds/sourceVersions、sourceSetHash、跳过区间、引用与三时间域音频映射，不含密钥；用户明确删除、活动会话保护、任务/模型租约处理与关联文件清理（活动模型按 pending-delete 处理）；跨块/GAP 播放、合并边界、空间不足、导出取消、密钥排除、来源身份导出与级联删除测试。

## 验收

- [ ] §8 全部 7 项完成，含 8.7 测试验证源证据保护及活动数据不被误删。

## 说明

- 当前状态：tasks.md §8 共 7 项，0 项 `[x]`。
