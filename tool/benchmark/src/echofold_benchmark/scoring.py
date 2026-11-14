"""CER/WER 评分器。

错误率使用编辑距离：CER 按 Unicode 字符，WER 按 token。空参考且空输出
记为 0（确实没有可比较内容）；空参考但出现输出时错误率未定义，保留插入
数量并标记 ``undefined-empty-reference``，绝不把插入当成 0。
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Sequence

from .normalize import cer_characters, wer_tokens


@dataclass(frozen=True)
class ErrorRate:
    rate: float | None
    edits: int
    ref_units: int
    hyp_units: int
    insertions: int
    status: str

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


def levenshtein(reference: Sequence[str], hypothesis: Sequence[str]) -> int:
    """标准编辑距离，只用两行内存。"""

    if not reference:
        return len(hypothesis)
    if not hypothesis:
        return len(reference)
    previous = list(range(len(hypothesis) + 1))
    for ref_index, ref_item in enumerate(reference, start=1):
        current = [ref_index]
        for hyp_index, hyp_item in enumerate(hypothesis, start=1):
            insertion = current[hyp_index - 1] + 1
            deletion = previous[hyp_index] + 1
            substitution = previous[hyp_index - 1] + (ref_item != hyp_item)
            current.append(min(insertion, deletion, substitution))
        previous = current
    return previous[-1]


def _rate(reference: Sequence[str], hypothesis: Sequence[str]) -> ErrorRate:
    ref_units = len(reference)
    hyp_units = len(hypothesis)
    edits = levenshtein(reference, hypothesis)
    if ref_units == 0:
        if hyp_units == 0:
            return ErrorRate(0.0, 0, 0, 0, 0, "measured-empty-reference")
        return ErrorRate(
            None, edits, 0, hyp_units, hyp_units, "undefined-empty-reference"
        )
    return ErrorRate(edits / ref_units, edits, ref_units, hyp_units, 0, "measured")


def character_error_rate(
    reference: str,
    hypothesis: str | None,
    *,
    punctuation_insensitive: bool,
) -> ErrorRate:
    if hypothesis is None:
        return ErrorRate(None, 0, 0, 0, 0, "missing-hypothesis")
    return _rate(
        cer_characters(reference, punctuation_insensitive=punctuation_insensitive),
        cer_characters(hypothesis, punctuation_insensitive=punctuation_insensitive),
    )


def word_error_rate(
    reference: str,
    hypothesis: str | None,
    *,
    punctuation_insensitive: bool,
) -> ErrorRate:
    if hypothesis is None:
        return ErrorRate(None, 0, 0, 0, 0, "missing-hypothesis")
    return _rate(
        wer_tokens(reference, punctuation_insensitive=punctuation_insensitive),
        wer_tokens(hypothesis, punctuation_insensitive=punctuation_insensitive),
    )
