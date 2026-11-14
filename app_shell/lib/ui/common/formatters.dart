/// 界面层共用的小型格式化函数；不改变领域模型语义。
library;

import '../../domain/models.dart';

/// 字节容量的人类可读表示。
String formatBytes(int bytes) {
  if (bytes < 1024) {
    return '$bytes B';
  }
  const int kilo = 1024;
  if (bytes < kilo * kilo) {
    return '${(bytes / kilo).toStringAsFixed(1)} KB';
  }
  if (bytes < kilo * kilo * kilo) {
    return '${(bytes / (kilo * kilo)).toStringAsFixed(1)} MB';
  }
  return '${(bytes / (kilo * kilo * kilo)).toStringAsFixed(2)} GB';
}

/// 录音状态的中文标签。
String sessionStateLabel(SessionState state) => switch (state) {
      SessionState.idle => '空闲',
      SessionState.recording => '录音中',
      SessionState.paused => '已暂停',
      SessionState.stopping => '正在结束',
      SessionState.lowStoragePaused => '存储不足已暂停',
      SessionState.failed => '录音失败',
    };
