"""通用常量、规范化 JSON 与哈希工具。

所有哈希都基于 UTF-8、排序键、无多余空白的规范化 JSON；这样 manifest、
预注册记录和评分结果可以在不同机器上复算。
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .errors import ValidationError

SCHEMA_VERSION = 1
SCORER_NAME = "echofold-benchmark"
SCORER_VERSION = "0.1.0"

# 与 docs/evaluation.md 的类别表一致，顺序即报告顺序。
CATEGORY_ORDER = (
    "zh-clear",
    "zh-en-mix",
    "noise",
    "accent",
    "long-session",
    "silence",
)
LANGUAGES = ("zh", "en", "mixed")
SPLITS = ("pilot", "final")


def canonical_json(value: Any) -> str:
    """返回可复算的规范化 JSON 字符串。"""

    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def hash_excluding(obj: dict[str, Any], field: str) -> str:
    """对去掉自哈希字段后的对象计算 SHA-256。"""

    payload = {key: value for key, value in obj.items() if key != field}
    return sha256_text(canonical_json(payload))


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def normalize_timestamp(value: str | None) -> str:
    """接受 UTC ISO-8601 时间并统一成 ``YYYY-MM-DDTHH:MM:SSZ``。"""

    if value is None:
        return utc_now_iso()
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValidationError(f"时间格式无效（需要 UTC ISO-8601）：{value}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def read_json(path: Path) -> Any:
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except FileNotFoundError as exc:
        raise ValidationError(f"文件不存在：{path}") from exc
    except json.JSONDecodeError as exc:
        raise ValidationError(f"JSON 解析失败：{path}: {exc}") from exc


def write_json(
    path: Path,
    value: Any,
    *,
    overwrite: bool = True,
    backup: bool = False,
) -> Path | None:
    """原子写入 JSON；可选保留旧文件备份，返回备份路径。"""

    path.parent.mkdir(parents=True, exist_ok=True)
    backup_path: Path | None = None
    if path.exists():
        if not overwrite:
            raise ValidationError(f"拒绝覆盖已有文件：{path}（使用 --force 显式覆盖）")
        if backup:
            stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            candidate = path.with_name(f"{path.name}.bak-{stamp}")
            index = 1
            while candidate.exists():
                candidate = path.with_name(f"{path.name}.bak-{stamp}-{index}")
                index += 1
            path.replace(candidate)
            backup_path = candidate

    fd, tmp_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent)
    )
    tmp_path = Path(tmp_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()
    return backup_path


def write_text_atomic(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent)
    )
    tmp_path = Path(tmp_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()
