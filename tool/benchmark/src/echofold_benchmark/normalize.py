"""文本规范化。

CER 的单位是 Unicode 字符（NFKC 后），始终剔除空白；可选剔除 Unicode
标点类别（P*）。WER 的单位是空白分隔的 token，可选标点不敏感模式会先
剔除标点再切分。规范化规则由评分结果记录，不能只写在文档里。
"""

from __future__ import annotations

import unicodedata


def _normalize_common(text: str, punctuation_insensitive: bool) -> list[str]:
    chars: list[str] = []
    for char in unicodedata.normalize("NFKC", text):
        if char.isspace():
            continue
        if punctuation_insensitive and unicodedata.category(char).startswith("P"):
            continue
        chars.append(char)
    return chars


def cer_characters(text: str | None, *, punctuation_insensitive: bool) -> list[str]:
    """返回 CER 使用的字符序列；``None`` 视为无输出，由评分器标记缺失。"""

    if text is None:
        return []
    return _normalize_common(text, punctuation_insensitive)


def wer_tokens(text: str | None, *, punctuation_insensitive: bool) -> list[str]:
    """返回 WER 使用的 token 序列（小写、按空白切分）。"""

    if text is None:
        return []
    normalized = unicodedata.normalize("NFKC", text).lower()
    if punctuation_insensitive:
        normalized = "".join(
            " " if unicodedata.category(char).startswith("P") else char
            for char in normalized
        )
    return normalized.split()


def normalization_description(punctuation_insensitive: bool) -> dict[str, str]:
    punctuation = "剔除 Unicode P* 类标点" if punctuation_insensitive else "保留标点"
    return {
        "unicode": "NFKC",
        "cerUnit": "unicode-character",
        "werUnit": "whitespace-token",
        "whitespace": "剔除",
        "punctuation": punctuation,
        "case": "WER 小写化",
    }
