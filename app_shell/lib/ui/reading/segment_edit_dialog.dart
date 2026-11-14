/// 编辑 L1 校订的对话框。
///
/// 只返回用户意图，保存/删除由调用方转交控制器；不直接修改展示状态。
library;

import 'package:flutter/material.dart';

import '../../app/app_contract.dart' as contract;
import '../motion/echo_motion.dart';

/// 编辑对话框结果。
final class SegmentEditResult {
  const SegmentEditResult.save(this.text) : deleteUserVersion = false;

  const SegmentEditResult.delete()
      : text = null,
        deleteUserVersion = true;

  final String? text;
  final bool deleteUserVersion;
}

/// 打开编辑对话框。
Future<SegmentEditResult?> showSegmentEditDialog(
  BuildContext context, {
  required contract.SegmentView segment,
}) {
  return showEchoDialog<SegmentEditResult>(
    context: context,
    builder: (BuildContext context) => _SegmentEditDialog(segment: segment),
  );
}

class _SegmentEditDialog extends StatefulWidget {
  const _SegmentEditDialog({required this.segment});

  final contract.SegmentView segment;

  @override
  State<_SegmentEditDialog> createState() => _SegmentEditDialogState();
}

class _SegmentEditDialogState extends State<_SegmentEditDialog> {
  late final TextEditingController _text =
      TextEditingController(text: widget.segment.l1Text ?? widget.segment.visibleText);
  String? _error;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  void _save() {
    final String value = _text.text.trim();
    if (value.isEmpty) {
      setState(() => _error = '校订文字不能为空');
      return;
    }
    Navigator.of(context).pop(SegmentEditResult.save(value));
  }

  @override
  Widget build(BuildContext context) {
    final contract.SegmentView segment = widget.segment;
    final ThemeData theme = Theme.of(context);
    return AlertDialog(
      title: const Text('编辑 L1 校订'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('L0 原始识别（不可修改）', style: theme.textTheme.labelMedium),
            const SizedBox(height: 4),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(segment.l0Text, style: theme.textTheme.bodyMedium),
            ),
            const SizedBox(height: 12),
            TextField(
              key: const ValueKey<String>('edit-l1-field'),
              controller: _text,
              autofocus: true,
              minLines: 2,
              maxLines: 6,
              decoration: InputDecoration(
                labelText: 'L1 校订文字',
                errorText: _error,
                border: const OutlineInputBorder(),
              ),
            ),
          ],
        ),
      ),
      actions: <Widget>[
        if (segment.l1Text != null)
          TextButton(
            key: const ValueKey<String>('edit-delete-user-version'),
            onPressed: () =>
                Navigator.of(context).pop(const SegmentEditResult.delete()),
            child: const Text('删除用户版本'),
          ),
        TextButton(
          key: const ValueKey<String>('edit-cancel'),
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('取消'),
        ),
        FilledButton(
          key: const ValueKey<String>('edit-save'),
          onPressed: _save,
          child: const Text('保存'),
        ),
      ],
    );
  }
}
