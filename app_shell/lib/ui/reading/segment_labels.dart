/// 阅读层与缺口原因的展示标签。
library;

import '../../domain/models.dart';

/// 语义层简称（用于段标签与复制提示）。
String semanticLayerLabel(SemanticLayer layer) => switch (layer) {
      SemanticLayer.l0 => 'L0 原始识别',
      SemanticLayer.l1 => 'L1 校订',
      SemanticLayer.l2 => 'L2 要点',
      SemanticLayer.l3 => 'L3 摘要',
    };

/// 缺口原因的中文说明；首版只有 ASR 排队过载。
String gapReasonLabel(GapReason reason) => switch (reason) {
      GapReason.asrOverload => '识别排队过载',
    };

/// 段的音频区间标签。
String segmentRangeLabel(TranscriptSegment segment) =>
    '${formatDuration(segment.capturedStartMs)}–${formatDuration(segment.capturedEndMs)}';
