"""分位数与延迟统计。

p50/p95 使用 nearest-rank 定义：排序后取第 ``ceil(q * n)`` 个值。样本数
和缺失数始终与分位数一起输出；没有样本时返回 ``None``，不返回 0。
"""

from __future__ import annotations

import math
from typing import Iterable


def percentile(values: Iterable[float], quantile: float) -> float | None:
    ordered = sorted(values)
    if not ordered:
        return None
    if not 0 < quantile <= 1:
        raise ValueError("quantile 必须在 (0, 1] 内")
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return ordered[index]


def latency_stats(values: Iterable[float], missing_count: int = 0) -> dict[str, object]:
    ordered = sorted(values)
    if not ordered:
        return {
            "p50": None,
            "p95": None,
            "max": None,
            "sampleCount": 0,
            "missingCount": missing_count,
            "status": "unavailable",
        }
    return {
        "p50": percentile(ordered, 0.50),
        "p95": percentile(ordered, 0.95),
        "max": ordered[-1],
        "sampleCount": len(ordered),
        "missingCount": missing_count,
        "status": "partial" if missing_count else "measured",
    }
