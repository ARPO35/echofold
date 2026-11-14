/// 段长按菜单。
///
/// GAP 段没有“编辑”，其余动作（播放、复制、重新识别）与普通段一致。
library;

import 'package:flutter/material.dart';

/// 长按菜单可执行的动作。
enum SegmentMenuAction {
  edit,
  play,
  copy,
  rerecognize,
}

/// 打开长按菜单；取消时返回空。
Future<SegmentMenuAction?> showSegmentMenu(
  BuildContext context, {
  required bool isGap,
  required String copyLabel,
  required bool recordingActive,
}) {
  return showModalBottomSheet<SegmentMenuAction>(
    context: context,
    useSafeArea: true,
    showDragHandle: true,
    builder: (BuildContext context) {
      return SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (!isGap)
              ListTile(
                key: const ValueKey<String>('segment-menu-edit'),
                leading: const Icon(Icons.edit_outlined),
                title: const Text('编辑文字'),
                subtitle: const Text('保存为 L1 校订，不修改 L0 证据'),
                onTap: () => Navigator.of(context).pop(SegmentMenuAction.edit),
              ),
            ListTile(
              key: const ValueKey<String>('segment-menu-play'),
              leading: const Icon(Icons.play_arrow_outlined),
              title: const Text('播放录音'),
              subtitle: Text(
                recordingActive ? '录音中只提示手动暂停，不会自动暂停' : '按该段实际音频区间回听',
              ),
              onTap: () => Navigator.of(context).pop(SegmentMenuAction.play),
            ),
            ListTile(
              key: const ValueKey<String>('segment-menu-copy'),
              leading: const Icon(Icons.copy_outlined),
              title: const Text('复制当前文字'),
              subtitle: Text(copyLabel),
              onTap: () => Navigator.of(context).pop(SegmentMenuAction.copy),
            ),
            ListTile(
              key: const ValueKey<String>('segment-menu-rerecognize'),
              leading: const Icon(Icons.autorenew),
              title: const Text('重新识别'),
              subtitle: const Text('使用本会话固定的本地模型，可离线运行'),
              onTap: () => Navigator.of(context).pop(SegmentMenuAction.rerecognize),
            ),
            const SizedBox(height: 8),
          ],
        ),
      );
    },
  );
}
