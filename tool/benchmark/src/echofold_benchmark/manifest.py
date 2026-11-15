"""不可变数据集 manifest 的创建与校验。"""

from __future__ import annotations

import csv
import json
import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .errors import ManifestError
from .normalize import normalization_description
from .util import (
    CATEGORY_ORDER,
    LANGUAGES,
    SCHEMA_VERSION,
    SCORER_NAME,
    SCORER_VERSION,
    SPLITS,
    hash_excluding,
    normalize_timestamp,
    read_json,
    sha256_file,
    sha256_text,
    write_json,
)

MANIFEST_HASH_FIELD = "manifestSha256"
SAMPLE_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_REFERENCE_FIELDS = (
    "sampleId",
    "category",
    "text",
    "language",
    "file",
    "speaker",
    "accent",
    "source",
)
SUPPORTED_REFERENCE_SUFFIXES = (".csv", ".jsonl", ".ndjson", ".json")


@dataclass
class FreezeResult:
    manifest: dict[str, Any]
    manifest_path: Path
    backup_path: Path | None


def _read_reference_rows(path: Path) -> list[dict[str, Any]]:
    suffix = path.suffix.lower()
    if suffix not in SUPPORTED_REFERENCE_SUFFIXES:
        raise ManifestError(
            f"不支持的参考转写格式：{suffix}（支持 CSV/JSONL/JSON 数组）"
        )
    if not path.exists():
        raise ManifestError(f"参考转写文件不存在：{path}")
    if not path.is_file():
        raise ManifestError(f"参考转写路径不是文件：{path}")

    if suffix == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                raise ManifestError(f"CSV 缺少表头：{path}")
            missing = [
                field
                for field in REQUIRED_REFERENCE_FIELDS
                if field not in reader.fieldnames
            ]
            if missing:
                raise ManifestError(f"CSV 缺少必需列：{', '.join(missing)}")
            rows = [dict(row) for row in reader]
    elif suffix in (".jsonl", ".ndjson"):
        rows = []
        with path.open("r", encoding="utf-8-sig") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise ManifestError(
                        f"JSONL 第 {line_number} 行解析失败：{exc}"
                    ) from exc
                if not isinstance(row, dict):
                    raise ManifestError(f"JSONL 第 {line_number} 行不是对象")
                rows.append(row)
    else:
        raw = read_json(path)
        if not isinstance(raw, list):
            raise ManifestError("JSON 参考转写必须是对象数组")
        rows = raw

    if not rows:
        raise ManifestError("参考转写为空；空数据不能生成可评测 manifest")
    return rows


def _string_field(row: dict[str, Any], field: str, *, allow_empty: bool = False) -> str:
    value = row.get(field)
    if not isinstance(value, str):
        raise ManifestError(f"字段 {field} 必须是字符串（样本 {row.get('sampleId')!r}）")
    value = value.strip()
    if not value and not allow_empty:
        raise ManifestError(f"字段 {field} 不能为空（样本 {row.get('sampleId')!r}）")
    return value


def _resolve_audio_path(audio_dir: Path, relative: str) -> Path:
    candidate = Path(relative)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ManifestError(f"音频路径必须是音频目录内的相对路径：{relative}")
    resolved = (audio_dir / candidate).resolve()
    root = audio_dir.resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ManifestError(f"音频路径越出音频目录：{relative}") from exc
    if not resolved.is_file():
        raise ManifestError(f"音频文件不存在：{resolved}")
    return resolved


def _probe_tool(ffprobe: str | None) -> tuple[str | None, str | None]:
    tool = ffprobe or shutil.which("ffprobe")
    if not tool:
        return None, None
    version: str | None = None
    try:
        completed = subprocess.run(
            [tool, "-version"],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if completed.returncode == 0 and completed.stdout:
            version = completed.stdout.splitlines()[0].strip() or None
    except (OSError, subprocess.SubprocessError):
        pass
    return tool, version


def _probe_audio(
    path: Path,
    *,
    tool: str | None,
    version: str | None,
    enabled: bool,
) -> dict[str, Any]:
    if not enabled or not tool:
        return {
            "durationMs": None,
            "sampleRate": None,
            "channels": None,
            "probeStatus": "unavailable",
            "probeTool": None,
            "probeVersion": None,
            "probeError": "未启用 ffprobe 或 ffprobe 不可用；时长/采样率待测",
        }
    command = [
        tool,
        "-v",
        "error",
        "-show_entries",
        "format=duration:stream=codec_type,sample_rate,channels",
        "-of",
        "json",
        str(path),
    ]
    try:
        completed = subprocess.run(
            command, check=False, capture_output=True, text=True, timeout=60
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return {
            "durationMs": None,
            "sampleRate": None,
            "channels": None,
            "probeStatus": "ffprobe-error",
            "probeTool": "ffprobe",
            "probeVersion": version,
            "probeError": str(exc)[:300],
        }
    if completed.returncode != 0:
        return {
            "durationMs": None,
            "sampleRate": None,
            "channels": None,
            "probeStatus": "ffprobe-error",
            "probeTool": "ffprobe",
            "probeVersion": version,
            "probeError": (completed.stderr or "ffprobe 失败").strip()[:300],
        }
    try:
        payload = json.loads(completed.stdout or "{}")
    except json.JSONDecodeError as exc:
        return {
            "durationMs": None,
            "sampleRate": None,
            "channels": None,
            "probeStatus": "ffprobe-error",
            "probeTool": "ffprobe",
            "probeVersion": version,
            "probeError": f"ffprobe JSON 解析失败：{exc}",
        }

    duration_ms: int | None = None
    sample_rate: int | None = None
    channels: int | None = None
    duration_raw = (payload.get("format") or {}).get("duration")
    if duration_raw not in (None, "N/A", ""):
        try:
            duration_ms = int(round(float(duration_raw) * 1000))
        except (TypeError, ValueError):
            duration_ms = None
    for stream in payload.get("streams") or []:
        if stream.get("codec_type") != "audio":
            continue
        try:
            sample_rate = int(stream.get("sample_rate"))
        except (TypeError, ValueError):
            sample_rate = None
        try:
            channels = int(stream.get("channels"))
        except (TypeError, ValueError):
            channels = None
        break
    return {
        "durationMs": duration_ms,
        "sampleRate": sample_rate,
        "channels": channels,
        "probeStatus": "ffprobe",
        "probeTool": "ffprobe",
        "probeVersion": version,
        "probeError": None,
    }


def freeze_manifest(
    *,
    audio_dir: Path,
    reference: Path,
    out: Path,
    dataset_id: str,
    revision: str,
    split: str,
    default_license: str | None = None,
    probe: bool = True,
    ffprobe: str | None = None,
    force: bool = False,
    generated_at: str | None = None,
) -> FreezeResult:
    """扫描音频与参考转写，生成带自哈希的不可变 manifest。"""

    if not dataset_id.strip():
        raise ManifestError("--dataset-id 不能为空")
    if not revision.strip():
        raise ManifestError("--revision 不能为空（参考转写 revision 必须冻结）")
    if split not in SPLITS:
        raise ManifestError(f"--split 必须是 {SPLITS} 之一：{split}")
    if not audio_dir.is_dir():
        raise ManifestError(f"音频目录不存在：{audio_dir}")
    if out.exists() and not force:
        raise ManifestError(f"manifest 已存在：{out}（拒绝覆盖；如需重建请用 --force）")
    if default_license is not None:
        default_license = default_license.strip() or None

    rows = _read_reference_rows(reference)
    tool, tool_version = _probe_tool(ffprobe) if probe else (None, None)
    audio_root = audio_dir.resolve()
    samples: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    seen_files: set[str] = set()

    for row in rows:
        sample_id = _string_field(row, "sampleId")
        if not SAMPLE_ID_PATTERN.match(sample_id):
            raise ManifestError(f"sampleId 含非法字符：{sample_id!r}")
        if sample_id in seen_ids:
            raise ManifestError(f"sampleId 重复：{sample_id}")
        seen_ids.add(sample_id)

        category = _string_field(row, "category")
        if category not in CATEGORY_ORDER:
            raise ManifestError(
                f"未知类别 {category!r}；允许：{', '.join(CATEGORY_ORDER)}"
            )
        language = _string_field(row, "language")
        if language not in LANGUAGES:
            raise ManifestError(
                f"未知语言 {language!r}；允许：{', '.join(LANGUAGES)}"
            )
        relative_file = _string_field(row, "file")
        if relative_file in seen_files:
            raise ManifestError(f"同一音频文件被多个样本引用：{relative_file}")
        seen_files.add(relative_file)
        audio_path = _resolve_audio_path(audio_root, relative_file)

        text = _string_field(row, "text", allow_empty=True)
        if not text and category != "silence":
            raise ManifestError(
                f"样本 {sample_id} 的参考转写为空；仅 silence 类别允许空参考"
            )
        speaker = _string_field(row, "speaker")
        accent = _string_field(row, "accent")
        source = _string_field(row, "source")
        license_value = (row.get("license") or "").strip() or default_license
        if not license_value:
            raise ManifestError(
                f"样本 {sample_id} 缺少 license（行内或 --license 默认值）"
            )

        row_revision = (row.get("referenceRevision") or "").strip()
        if row_revision and row_revision != revision:
            raise ManifestError(
                f"样本 {sample_id} 的 referenceRevision={row_revision!r} "
                f"与冻结 revision={revision!r} 不一致"
            )

        probe_result = _probe_audio(
            audio_path, tool=tool, version=tool_version, enabled=probe
        )
        samples.append(
            {
                "sampleId": sample_id,
                "category": category,
                "language": language,
                "file": relative_file,
                "sha256": sha256_file(audio_path),
                "bytes": audio_path.stat().st_size,
                "durationMs": probe_result["durationMs"],
                "sampleRate": probe_result["sampleRate"],
                "channels": probe_result["channels"],
                "probeStatus": probe_result["probeStatus"],
                "probeTool": probe_result["probeTool"],
                "probeVersion": probe_result["probeVersion"],
                "probeError": probe_result["probeError"],
                "speaker": speaker,
                "accent": accent,
                "source": source,
                "license": license_value,
                "referenceRevision": revision,
                "reference": text,
                "referenceSha256": sha256_text(text),
            }
        )

    category_counts = {category: 0 for category in CATEGORY_ORDER}
    for sample in samples:
        category_counts[sample["category"]] += 1

    manifest: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": normalize_timestamp(generated_at),
        "dataset": {
            "datasetId": dataset_id.strip(),
            "revision": revision.strip(),
            "split": split,
            "defaultLicense": default_license,
            "categoryOrder": list(CATEGORY_ORDER),
            "categoryCounts": category_counts,
            "referenceFile": reference.name,
            "referenceFormat": reference.suffix.lower().lstrip("."),
            "sampleCount": len(samples),
        },
        "protocol": {
            "scorer": {"name": SCORER_NAME, "version": SCORER_VERSION},
            "normalization": normalization_description(False),
            "cer": "Unicode 字符编辑距离 / 参考字符数；剔除空白；标点由 score 参数决定",
            "wer": "空白 token 编辑距离 / 参考 token 数；小写；标点由 score 参数决定",
            "timing": (
                "asrLagMs、partialLatencyMs、oldestPendingAudioLagMs 必须由"
                "采集/系统输出提供；评分器不推算、不补零"
            ),
            "rtf": (
                "优先 inferenceTimeMs / 音频时长；否则取系统显式 rtf；"
                "两者皆缺为 null"
            ),
            "gap": "gapIntervals 缺失记为 null（待测）；空数组表示实测 0 个缺口",
            "command": (
                "uv run echofold-benchmark score --manifest <manifest.json> "
                "--system <run.json> --preregistration <preregistration.json>"
            ),
        },
        "samples": samples,
    }
    manifest[MANIFEST_HASH_FIELD] = hash_excluding(manifest, MANIFEST_HASH_FIELD)
    backup_path = write_json(
        out, manifest, overwrite=True, backup=force and out.exists()
    )
    return FreezeResult(manifest=manifest, manifest_path=out, backup_path=backup_path)


def validate_manifest_hash(manifest: dict[str, Any], *, source: str = "manifest") -> str:
    stored = manifest.get(MANIFEST_HASH_FIELD)
    if not isinstance(stored, str) or not SHA256_PATTERN.match(stored):
        raise ManifestError(f"{source} 缺少合法的 {MANIFEST_HASH_FIELD}")
    computed = hash_excluding(manifest, MANIFEST_HASH_FIELD)
    if computed != stored:
        raise ManifestError(
            f"{source} 哈希不一致：记录 {stored}，复算 {computed}（文件被篡改？）"
        )
    return stored


def load_manifest(path: Path) -> dict[str, Any]:
    manifest = read_json(path)
    if not isinstance(manifest, dict):
        raise ManifestError("manifest 顶层必须是对象")
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ManifestError(
            f"不支持的 manifest schemaVersion：{manifest.get('schemaVersion')!r}"
        )
    validate_manifest_hash(manifest, source=str(path))

    dataset = manifest.get("dataset")
    samples = manifest.get("samples")
    if not isinstance(dataset, dict) or not isinstance(samples, list):
        raise ManifestError("manifest 缺少 dataset 或 samples")
    if not samples:
        raise ManifestError("manifest 没有样本；空数据不能通过评测")
    revision = dataset.get("revision")
    if not isinstance(revision, str) or not revision:
        raise ManifestError("manifest 缺少 dataset.revision")
    if dataset.get("split") not in SPLITS:
        raise ManifestError("manifest 的 dataset.split 无效")

    seen_ids: set[str] = set()
    for sample in samples:
        if not isinstance(sample, dict):
            raise ManifestError("manifest.samples 含非对象项")
        sample_id = sample.get("sampleId")
        if not isinstance(sample_id, str) or not sample_id:
            raise ManifestError("manifest 样本缺少 sampleId")
        if sample_id in seen_ids:
            raise ManifestError(f"manifest 样本 ID 重复：{sample_id}")
        seen_ids.add(sample_id)
        if sample.get("category") not in CATEGORY_ORDER:
            raise ManifestError(f"manifest 样本类别无效：{sample.get('category')!r}")
        if sample.get("language") not in LANGUAGES:
            raise ManifestError(f"manifest 样本语言无效：{sample.get('language')!r}")
        sha = sample.get("sha256")
        if not isinstance(sha, str) or not SHA256_PATTERN.match(sha):
            raise ManifestError(f"manifest 样本 {sample_id} 的 sha256 非法")
        ref_sha = sample.get("referenceSha256")
        if not isinstance(ref_sha, str) or not SHA256_PATTERN.match(ref_sha):
            raise ManifestError(f"manifest 样本 {sample_id} 的 referenceSha256 非法")
        if sample.get("referenceRevision") != revision:
            raise ManifestError(
                f"manifest 样本 {sample_id} 的 referenceRevision 与 dataset.revision 不一致"
            )
    return manifest
