"""评分引擎：读 manifest + 系统输出，逐样本/类别/设备汇总并判定门槛。

设计约束：
- 只使用系统输出显式提供的延迟、RTF、GAP 和资源数据；缺失为 ``None``。
- 逐设备独立汇总，任何顶层对象都不含跨设备平均值。
- 预注册缺失时状态为 UNFROZEN；预注册不一致直接拒绝。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .errors import ScoringError
from .manifest import SHA256_PATTERN, load_manifest
from .normalize import normalization_description
from .prereg import load_preregistration, preregistration_device, validate_preregistration_hash
from .scoring import ErrorRate, character_error_rate, word_error_rate
from .stats import latency_stats
from .util import (
    CATEGORY_ORDER,
    SCHEMA_VERSION,
    SCORER_NAME,
    SCORER_VERSION,
    hash_excluding,
    normalize_timestamp,
    read_json,
)

RESULT_FIELDS = (
    "sampleId",
    "hypothesis",
    "asrLagMs",
    "partialLatencyMs",
    "oldestPendingAudioLagMs",
    "rtf",
    "inferenceTimeMs",
    "gapIntervals",
    "failed",
    "error",
)


@dataclass
class RunData:
    source: Path
    device: dict[str, Any]
    model: dict[str, Any]
    run_meta: dict[str, Any]
    results: list[dict[str, Any]]
    reference_revision: str | None = None
    manifest_sha256: str | None = None


def _optional_number(result: dict[str, Any], field: str) -> float | None:
    if field not in result or result[field] is None:
        return None
    value = result[field]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ScoringError(f"{result.get('sampleId')}: {field} 必须是数值或 null")
    if value < 0:
        raise ScoringError(f"{result.get('sampleId')}: {field} 不能为负")
    return float(value)


def _optional_string(result: dict[str, Any], field: str) -> str | None:
    if field not in result or result[field] is None:
        return None
    value = result[field]
    if not isinstance(value, str):
        raise ScoringError(f"{result.get('sampleId')}: {field} 必须是字符串或 null")
    return value


def _parse_gap_intervals(
    result: dict[str, Any],
) -> tuple[list[dict[str, Any]] | None, list[str]]:
    sample_id = result.get("sampleId")
    if "gapIntervals" not in result or result["gapIntervals"] is None:
        return None, ["gapIntervals"]
    raw = result["gapIntervals"]
    if not isinstance(raw, list):
        raise ScoringError(f"{sample_id}: gapIntervals 必须是数组或 null")
    intervals: list[dict[str, Any]] = []
    previous_end = 0.0
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            raise ScoringError(f"{sample_id}: gapIntervals[{index}] 必须是对象")
        for field in ("startMs", "endMs"):
            value = item.get(field)
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ScoringError(
                    f"{sample_id}: gapIntervals[{index}].{field} 必须是数值"
                )
            if value < 0:
                raise ScoringError(
                    f"{sample_id}: gapIntervals[{index}].{field} 不能为负"
                )
        start = float(item["startMs"])
        end = float(item["endMs"])
        if end < start:
            raise ScoringError(
                f"{sample_id}: gapIntervals[{index}] 的 endMs 小于 startMs"
            )
        if start < previous_end:
            raise ScoringError(f"{sample_id}: gapIntervals 区间重叠或未排序")
        previous_end = end
        reason = item.get("reason")
        if not isinstance(reason, str) or not reason.strip():
            raise ScoringError(f"{sample_id}: gapIntervals[{index}].reason 不能为空")
        interval = {"startMs": start, "endMs": end, "reason": reason.strip()}
        for optional in ("chunkSeq", "modelRevision"):
            if optional in item and item[optional] is not None:
                interval[optional] = item[optional]
        intervals.append(interval)
    return intervals, []


def _load_json_object(path: Path) -> dict[str, Any]:
    raw = read_json(path)
    if not isinstance(raw, dict):
        raise ScoringError(f"系统输出顶层必须是对象：{path}")
    return raw


def load_runs(path: Path) -> list[RunData]:
    """读取一个运行结果文件，返回按设备分组的数据。"""

    if not path.exists():
        raise ScoringError(f"系统输出不存在：{path}")
    suffix = path.suffix.lower()
    if suffix in (".jsonl", ".ndjson"):
        return _load_jsonl_runs(path)
    if suffix != ".json":
        raise ScoringError(f"不支持的系统输出格式：{suffix}（使用 .json 或 .jsonl）")

    payload = _load_json_object(path)
    results = payload.get("results")
    device = payload.get("device")
    if not isinstance(results, list):
        raise ScoringError(f"{path.name}: 缺少 results 数组")
    if not isinstance(device, dict):
        raise ScoringError(f"{path.name}: 缺少 device 对象")
    if not isinstance(device.get("deviceId"), str) or not device["deviceId"].strip():
        raise ScoringError(f"{path.name}: device.deviceId 不能为空")
    model = payload.get("model") if isinstance(payload.get("model"), dict) else {}
    run_meta = payload.get("run") if isinstance(payload.get("run"), dict) else {}
    reference_revision = payload.get("referenceRevision")
    manifest_sha256 = payload.get("manifestSha256")
    if reference_revision is not None and not isinstance(reference_revision, str):
        raise ScoringError(f"{path.name}: referenceRevision 必须是字符串")
    if manifest_sha256 is not None and not isinstance(manifest_sha256, str):
        raise ScoringError(f"{path.name}: manifestSha256 必须是字符串")
    return [
        RunData(
            source=path,
            device=device,
            model=model,
            run_meta=run_meta,
            results=results,
            reference_revision=reference_revision,
            manifest_sha256=manifest_sha256,
        )
    ]


def _load_jsonl_runs(path: Path) -> list[RunData]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8-sig") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ScoringError(
                    f"{path.name}: 第 {line_number} 行 JSON 解析失败：{exc}"
                ) from exc
            if not isinstance(row, dict):
                raise ScoringError(f"{path.name}: 第 {line_number} 行不是对象")
            rows.append(row)
    if not rows:
        raise ScoringError(f"{path.name}: 空文件，没有系统输出")

    meta = rows[0] if rows[0].get("type") == "meta" else None
    if meta is not None:
        device = meta.get("device")
        if not isinstance(device, dict) or not isinstance(device.get("deviceId"), str):
            raise ScoringError(f"{path.name}: meta 行缺少 device.deviceId")
        results = [row for row in rows[1:] if row.get("type") != "meta"]
        for row in results:
            row_device = row.get("deviceId")
            if row_device is not None and row_device != device["deviceId"]:
                raise ScoringError(
                    f"{path.name}: 结果行 deviceId 与 meta 不一致：{row_device}"
                )
        return [
            RunData(
                source=path,
                device=device,
                model=meta.get("model") if isinstance(meta.get("model"), dict) else {},
                run_meta=meta.get("run") if isinstance(meta.get("run"), dict) else {},
                results=results,
                reference_revision=meta.get("referenceRevision"),
                manifest_sha256=meta.get("manifestSha256"),
            )
        ]

    grouped: dict[str, RunData] = {}
    for row in rows:
        device_id = row.get("deviceId")
        if not isinstance(device_id, str) or not device_id.strip():
            raise ScoringError(
                f"{path.name}: JSONL 结果行缺少 deviceId（或首行缺少 type=meta）"
            )
        run = grouped.get(device_id)
        if run is None:
            device = row.get("device") if isinstance(row.get("device"), dict) else {}
            device = {**device, "deviceId": device_id}
            run = RunData(
                source=path,
                device=device,
                model=row.get("model") if isinstance(row.get("model"), dict) else {},
                run_meta=row.get("run") if isinstance(row.get("run"), dict) else {},
                results=[],
            )
            grouped[device_id] = run
        run.results.append(row)
    return list(grouped.values())


def _metric_summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    cer_edits = 0
    cer_units = 0
    cer_undefined = 0
    wer_edits = 0
    wer_units = 0
    wer_undefined = 0
    for sample in samples:
        if sample["cer"]["status"] == "measured":
            cer_edits += sample["cer"]["edits"]
            cer_units += sample["cer"]["refUnits"]
        elif sample["cer"]["status"] == "undefined-empty-reference":
            cer_undefined += 1
        if sample["wer"]["status"] == "measured":
            wer_edits += sample["wer"]["edits"]
            wer_units += sample["wer"]["refUnits"]
        elif sample["wer"]["status"] == "undefined-empty-reference":
            wer_undefined += 1
    return {
        "cer": (cer_edits / cer_units) if cer_units else None,
        "cerEdits": cer_edits,
        "cerRefUnits": cer_units,
        "cerUndefinedCount": cer_undefined,
        "wer": (wer_edits / wer_units) if wer_units else None,
        "werEdits": wer_edits,
        "werRefUnits": wer_units,
        "werUndefinedCount": wer_undefined,
    }


def _rtf_summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    weighted_time = 0.0
    weighted_duration = 0.0
    reported: list[float] = []
    missing = 0
    for sample in samples:
        if sample["rtf"] is None:
            missing += 1
            continue
        if (
            sample["inferenceTimeMs"] is not None
            and sample["audioDurationMs"] is not None
            and sample["audioDurationMs"] > 0
        ):
            weighted_time += sample["inferenceTimeMs"]
            weighted_duration += sample["audioDurationMs"]
        elif sample["rtfSource"] == "reported":
            reported.append(sample["rtf"])
    if weighted_duration > 0:
        return {
            "rtf": weighted_time / weighted_duration,
            "method": "audio-weighted",
            "sampleCount": len(samples) - missing,
            "missingCount": missing,
        }
    if reported:
        return {
            "rtf": sum(reported) / len(reported),
            "method": "mean-of-reported",
            "sampleCount": len(reported),
            "missingCount": missing,
        }
    return {
        "rtf": None,
        "method": "unavailable",
        "sampleCount": 0,
        "missingCount": missing,
    }


def _gap_summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    available = [sample for sample in samples if sample["gapIntervals"] is not None]
    missing = len(samples) - len(available)
    known_count = sum(sample["gapCount"] or 0 for sample in available)
    known_duration = sum(sample["gapDurationMs"] or 0.0 for sample in available)
    if not samples:
        return {
            "count": None,
            "durationMs": None,
            "knownCount": 0,
            "knownDurationMs": 0.0,
            "missingCount": 0,
            "status": "unavailable",
        }
    if missing:
        return {
            "count": None,
            "durationMs": None,
            "knownCount": known_count,
            "knownDurationMs": known_duration,
            "missingCount": missing,
            "status": "partial",
        }
    return {
        "count": known_count,
        "durationMs": known_duration,
        "knownCount": known_count,
        "knownDurationMs": known_duration,
        "missingCount": 0,
        "status": "measured",
    }


def _latency_field(
    samples: list[dict[str, Any]], field: str
) -> dict[str, Any]:
    values = [
        sample[field] for sample in samples if sample.get(field) is not None
    ]
    missing = len(samples) - len(values)
    return latency_stats(values, missing_count=missing)


def _aggregate(samples: list[dict[str, Any]]) -> dict[str, Any]:
    scored = [sample for sample in samples if sample["hypothesis"] is not None]
    failures = [sample for sample in samples if sample["failed"] or sample["error"]]
    metrics = _metric_summary(scored)
    languages = sorted({sample["language"] for sample in samples})
    return {
        "sampleCount": len(samples),
        "scoredCount": len(scored),
        "missingCount": len(samples) - len(scored),
        "failureCount": len(failures),
        "languages": languages,
        **metrics,
        "rtf": _rtf_summary(scored)["rtf"],
        "rtfMethod": _rtf_summary(scored)["method"],
        "rtfSampleCount": _rtf_summary(scored)["sampleCount"],
        "rtfMissingCount": _rtf_summary(scored)["missingCount"],
        "asrLagMs": _latency_field(samples, "asrLagMs"),
        "partialLatencyMs": _latency_field(samples, "partialLatencyMs"),
        "oldestPendingAudioLagMs": _latency_field(
            samples, "oldestPendingAudioLagMs"
        ),
        "gap": _gap_summary(samples),
        "failures": [
            {
                "sampleId": sample["sampleId"],
                "category": sample["category"],
                "error": sample["error"],
            }
            for sample in failures
        ],
    }


def _score_sample(
    sample: dict[str, Any],
    result: dict[str, Any],
    *,
    punctuation_insensitive: bool,
    manifest_sha256: str,
    revision: str,
    device_id: str,
) -> dict[str, Any]:
    sample_id = sample["sampleId"]
    hypothesis = _optional_string(result, "hypothesis")
    missing_fields: list[str] = []
    if "hypothesis" not in result or result["hypothesis"] is None:
        missing_fields.append("hypothesis")

    if result.get("referenceRevision") is not None and result["referenceRevision"] != revision:
        raise ScoringError(
            f"{sample_id}: referenceRevision={result.get('referenceRevision')!r} "
            f"与 manifest revision={revision!r} 不一致"
        )
    if (
        result.get("manifestSha256") is not None
        and result["manifestSha256"] != manifest_sha256
    ):
        raise ScoringError(f"{sample_id}: manifestSha256 与当前 manifest 不一致")
    if result.get("audioSha256") is not None and result["audioSha256"] != sample["sha256"]:
        raise ScoringError(f"{sample_id}: audioSha256 与 manifest 样本哈希不一致")
    if (
        result.get("referenceSha256") is not None
        and result["referenceSha256"] != sample["referenceSha256"]
    ):
        raise ScoringError(f"{sample_id}: referenceSha256 与 manifest 参考哈希不一致")

    asr_lag = _optional_number(result, "asrLagMs")
    partial_latency = _optional_number(result, "partialLatencyMs")
    oldest_pending = _optional_number(result, "oldestPendingAudioLagMs")
    for field, value in (
        ("asrLagMs", asr_lag),
        ("partialLatencyMs", partial_latency),
        ("oldestPendingAudioLagMs", oldest_pending),
    ):
        if value is None:
            missing_fields.append(field)

    reported_rtf = _optional_number(result, "rtf")
    inference_time = _optional_number(result, "inferenceTimeMs")
    audio_duration = sample.get("durationMs")
    rtf: float | None = None
    rtf_source = "unavailable"
    if (
        inference_time is not None
        and isinstance(audio_duration, (int, float))
        and audio_duration > 0
    ):
        rtf = inference_time / audio_duration
        rtf_source = "inference-time/duration"
    elif reported_rtf is not None:
        rtf = reported_rtf
        rtf_source = "reported"
    elif inference_time is not None:
        rtf_source = "undefined-audio-duration"
    if rtf is None:
        missing_fields.append("rtf")

    gap_intervals, gap_missing = _parse_gap_intervals(result)
    missing_fields.extend(gap_missing)
    gap_count = None if gap_intervals is None else len(gap_intervals)
    gap_duration = (
        None
        if gap_intervals is None
        else sum(item["endMs"] - item["startMs"] for item in gap_intervals)
    )

    failed = result.get("failed") is True
    error = _optional_string(result, "error")
    if error is not None:
        failed = True
    if result.get("failed") is not None and not isinstance(result.get("failed"), bool):
        raise ScoringError(f"{sample_id}: failed 必须是布尔值")

    cer: ErrorRate = character_error_rate(
        sample["reference"],
        hypothesis,
        punctuation_insensitive=punctuation_insensitive,
    )
    wer: ErrorRate = word_error_rate(
        sample["reference"],
        hypothesis,
        punctuation_insensitive=punctuation_insensitive,
    )

    return {
        "sampleId": sample_id,
        "deviceId": device_id,
        "category": sample["category"],
        "language": sample["language"],
        "reference": sample["reference"],
        "referenceSha256": sample["referenceSha256"],
        "hypothesis": hypothesis,
        "cer": cer.to_dict(),
        "wer": wer.to_dict(),
        "asrLagMs": asr_lag,
        "partialLatencyMs": partial_latency,
        "oldestPendingAudioLagMs": oldest_pending,
        "rtf": rtf,
        "rtfSource": rtf_source,
        "inferenceTimeMs": inference_time,
        "audioDurationMs": audio_duration if isinstance(audio_duration, (int, float)) else None,
        "gapIntervals": gap_intervals,
        "gapCount": gap_count,
        "gapDurationMs": gap_duration,
        "failed": failed,
        "error": error,
        "missingFields": missing_fields,
    }


def _check(
    name: str,
    *,
    threshold: Any,
    measured: Any,
    status: str,
    note: str | None = None,
) -> dict[str, Any]:
    return {
        "name": name,
        "threshold": threshold,
        "measured": measured,
        "status": status,
        "note": note,
    }


def _gate_device(
    *,
    device_id: str,
    categories: dict[str, dict[str, Any]],
    overall: dict[str, Any],
    run_meta: dict[str, Any],
    preregistration: dict[str, Any] | None,
    manifest: dict[str, Any],
) -> dict[str, Any]:
    checks: list[dict[str, Any]] = []
    if preregistration is None:
        return {
            "status": "UNFROZEN",
            "passed": False,
            "checks": [
                _check(
                    "预注册",
                    threshold=None,
                    measured=None,
                    status="pending",
                    note="未冻结/待决策：score 未提供预注册文件",
                )
            ],
            "preregistrationSha256": None,
        }

    prereg_device = preregistration_device(preregistration, device_id)
    prereg_hash = preregistration["preregistrationSha256"]
    if prereg_device is None:
        return {
            "status": "PENDING",
            "passed": False,
            "checks": [
                _check(
                    "设备预注册",
                    threshold=None,
                    measured=device_id,
                    status="pending",
                    note="设备未包含在预注册记录中",
                )
            ],
            "preregistrationSha256": prereg_hash,
        }

    thresholds = prereg_device["thresholds"]
    for category in manifest["dataset"]["categoryOrder"]:
        aggregate = categories[category]
        if aggregate["sampleCount"] == 0:
            checks.append(
                _check(
                    f"{category}.样本",
                    threshold=">0",
                    measured=0,
                    status="pending",
                    note="类别无样本，不能判定",
                )
            )
            continue
        languages = set(aggregate["languages"])
        if languages & {"zh", "mixed"}:
            threshold = thresholds["cerMaxByCategory"].get(category)
            if threshold is None:
                checks.append(
                    _check(
                        f"{category}.CER",
                        threshold=None,
                        measured=aggregate["cer"],
                        status="pending",
                        note="未冻结该类别 CER 门槛",
                    )
                )
            elif aggregate["cer"] is None:
                checks.append(
                    _check(
                        f"{category}.CER",
                        threshold=threshold,
                        measured=None,
                        status="pending",
                        note="无有效 CER 数据（待测）",
                    )
                )
            else:
                status = "pass" if aggregate["cer"] <= threshold else "fail"
                checks.append(
                    _check(
                        f"{category}.CER",
                        threshold=threshold,
                        measured=aggregate["cer"],
                        status=status,
                    )
                )
        if languages & {"en", "mixed"}:
            threshold = thresholds["werMaxByCategory"].get(category)
            if threshold is None:
                checks.append(
                    _check(
                        f"{category}.WER",
                        threshold=None,
                        measured=aggregate["wer"],
                        status="pending",
                        note="未冻结该类别 WER 门槛",
                    )
                )
            elif aggregate["wer"] is None:
                checks.append(
                    _check(
                        f"{category}.WER",
                        threshold=threshold,
                        measured=None,
                        status="pending",
                        note="无有效 WER 数据（待测）",
                    )
                )
            else:
                status = "pass" if aggregate["wer"] <= threshold else "fail"
                checks.append(
                    _check(
                        f"{category}.WER",
                        threshold=threshold,
                        measured=aggregate["wer"],
                        status=status,
                    )
                )
        if aggregate["missingCount"]:
            checks.append(
                _check(
                    f"{category}.缺失输出",
                    threshold=0,
                    measured=aggregate["missingCount"],
                    status="pending",
                    note="样本输出缺失，不能视为通过",
                )
            )
        if aggregate["failureCount"]:
            checks.append(
                _check(
                    f"{category}.失败样本",
                    threshold=0,
                    measured=aggregate["failureCount"],
                    status="fail",
                )
            )

    rtf_threshold = thresholds["rtfMax"]
    if aggregate_rtf := overall["rtf"]:
        status = "pass" if aggregate_rtf < rtf_threshold else "fail"
        checks.append(
            _check(
                "RTF",
                threshold=f"< {rtf_threshold}",
                measured=aggregate_rtf,
                status=status,
            )
        )
    else:
        checks.append(
            _check("RTF", threshold=f"< {rtf_threshold}", measured=None, status="pending", note="RTF 待测")
        )

    p95_lag = overall["asrLagMs"]["p95"]
    if p95_lag is None:
        checks.append(
            _check(
                "ASR落后 p95",
                threshold=f"<= {thresholds['p95AsrLagMsMax']} ms",
                measured=None,
                status="pending",
                note="延迟字段缺失（待测）",
            )
        )
    else:
        status = "pass" if p95_lag <= thresholds["p95AsrLagMsMax"] else "fail"
        checks.append(
            _check(
                "ASR落后 p95",
                threshold=f"<= {thresholds['p95AsrLagMsMax']} ms",
                measured=p95_lag,
                status=status,
            )
        )

    partial_threshold = thresholds.get("p95PartialLatencyMsMax")
    if partial_threshold is not None:
        p95_partial = overall["partialLatencyMs"]["p95"]
        if p95_partial is None:
            checks.append(
                _check(
                    "Partial延迟 p95",
                    threshold=f"<= {partial_threshold} ms",
                    measured=None,
                    status="pending",
                    note="Partial 延迟字段缺失（待测）",
                )
            )
        else:
            status = "pass" if p95_partial <= partial_threshold else "fail"
            checks.append(
                _check(
                    "Partial延迟 p95",
                    threshold=f"<= {partial_threshold} ms",
                    measured=p95_partial,
                    status=status,
                )
            )

    budget_specs = (
        ("memoryMaxMb", "memoryPeakMb", "内存峰值"),
        ("temperatureMaxC", "temperaturePeakC", "温度峰值"),
        ("queueMaxDepth", "queueMaxDepth", "队列最大深度"),
        ("queueOldestAgeMsMax", "queueOldestAgeMs", "队列 oldest age"),
    )
    for threshold_field, measured_field, label in budget_specs:
        threshold = thresholds.get(threshold_field)
        measured = run_meta.get(measured_field)
        if isinstance(measured, bool) or not isinstance(measured, (int, float)):
            measured = None
        if threshold is None:
            checks.append(
                _check(
                    label,
                    threshold=None,
                    measured=measured,
                    status="pending",
                    note="预算未冻结/待决策",
                )
            )
        elif measured is None:
            checks.append(
                _check(
                    label,
                    threshold=f"<= {threshold}",
                    measured=None,
                    status="pending",
                    note="运行数据未提供该项（待测）",
                )
            )
        else:
            status = "pass" if measured <= threshold else "fail"
            checks.append(
                _check(label, threshold=f"<= {threshold}", measured=measured, status=status)
            )

    if overall["missingCount"]:
        checks.append(
            _check(
                "总体缺失输出",
                threshold=0,
                measured=overall["missingCount"],
                status="pending",
                note="缺失输出不能按 0 处理",
            )
        )
    if overall["failureCount"]:
        checks.append(
            _check(
                "失败样本",
                threshold=0,
                measured=overall["failureCount"],
                status="fail",
            )
        )

    if overall["scoredCount"] == 0:
        status = "NO_DATA"
    elif any(check["status"] == "fail" for check in checks):
        status = "FAIL"
    elif any(check["status"] == "pending" for check in checks):
        status = "PENDING"
    else:
        status = "PASS"
    return {
        "status": status,
        "passed": status == "PASS",
        "checks": checks,
        "preregistrationSha256": prereg_hash,
    }


def _score_device(
    run: RunData,
    *,
    manifest: dict[str, Any],
    samples_by_id: dict[str, dict[str, Any]],
    preregistration: dict[str, Any] | None,
    punctuation_insensitive: bool,
) -> dict[str, Any]:
    device_id = run.device["deviceId"]
    if run.reference_revision is not None and run.reference_revision != manifest["dataset"]["revision"]:
        raise ScoringError(
            f"{run.source.name}: referenceRevision={run.reference_revision!r} "
            f"与 manifest revision={manifest['dataset']['revision']!r} 不一致"
        )
    if (
        run.manifest_sha256 is not None
        and run.manifest_sha256 != manifest["manifestSha256"]
    ):
        raise ScoringError(f"{run.source.name}: manifestSha256 与当前 manifest 不一致")

    scored_samples: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for result in run.results:
        if not isinstance(result, dict):
            raise ScoringError(f"{run.source.name}: results 含非对象项")
        sample_id = result.get("sampleId")
        if not isinstance(sample_id, str) or not sample_id:
            raise ScoringError(f"{run.source.name}: 结果缺少 sampleId")
        if sample_id in seen_ids:
            raise ScoringError(f"{run.source.name}: 样本 {sample_id} 重复")
        seen_ids.add(sample_id)
        manifest_sample = samples_by_id.get(sample_id)
        if manifest_sample is None:
            raise ScoringError(f"{run.source.name}: 未在 manifest 中的样本：{sample_id}")
        scored_samples.append(
            _score_sample(
                manifest_sample,
                result,
                punctuation_insensitive=punctuation_insensitive,
                manifest_sha256=manifest["manifestSha256"],
                revision=manifest["dataset"]["revision"],
                device_id=device_id,
            )
        )

    categories: dict[str, dict[str, Any]] = {}
    for category in manifest["dataset"]["categoryOrder"]:
        category_samples = [
            sample for sample in scored_samples if sample["category"] == category
        ]
        categories[category] = _aggregate(category_samples)
    overall = _aggregate(scored_samples)
    gate = _gate_device(
        device_id=device_id,
        categories=categories,
        overall=overall,
        run_meta=run.run_meta,
        preregistration=preregistration,
        manifest=manifest,
    )
    return {
        "deviceId": device_id,
        "device": run.device,
        "model": run.model,
        "run": run.run_meta,
        "source": run.source.name,
        "overall": overall,
        "categories": categories,
        "samples": scored_samples,
        "failures": overall["failures"],
        "gate": gate,
        "preregistrationSha256": gate["preregistrationSha256"],
    }


def score(
    *,
    manifest_path: Path,
    system_paths: list[Path],
    preregistration_path: Path | None = None,
    punctuation_insensitive: bool = False,
    generated_at: str | None = None,
) -> dict[str, Any]:
    """生成逐样本、逐类别、逐设备的评分结果。"""

    if not system_paths:
        raise ScoringError("至少需要一个系统输出文件")
    manifest = load_manifest(manifest_path)
    samples_by_id = {
        sample["sampleId"]: sample for sample in manifest["samples"]
    }

    preregistration: dict[str, Any] | None = None
    if preregistration_path is not None:
        preregistration = load_preregistration(
            preregistration_path, manifest=manifest
        )
        validate_preregistration_hash(preregistration, source=str(preregistration_path))

    runs: list[RunData] = []
    for path in system_paths:
        runs.extend(load_runs(path))

    seen_devices: set[str] = set()
    devices = []
    for run in runs:
        device_id = run.device["deviceId"]
        if device_id in seen_devices:
            raise ScoringError(f"设备 {device_id} 在多个运行文件中重复；禁止混排")
        seen_devices.add(device_id)
        devices.append(
            _score_device(
                run,
                manifest=manifest,
                samples_by_id=samples_by_id,
                preregistration=preregistration,
                punctuation_insensitive=punctuation_insensitive,
            )
        )

    warnings: list[str] = []
    if preregistration is None:
        warnings.append("未提供预注册：门槛状态为未冻结/待决策，不能判定通过")
    else:
        for device in devices:
            if device["gate"]["status"] == "PENDING":
                warnings.append(
                    f"设备 {device['deviceId']} 存在待测项：{device['gate']['status']}"
                )
    unknown_durations = sum(
        1 for sample in manifest["samples"] if sample.get("durationMs") is None
    )
    if unknown_durations:
        warnings.append(
            f"{unknown_durations} 个样本时长/采样率待测（manifest probeStatus 非 ffprobe）"
        )

    if not devices:
        status = "NO_DATA"
    elif preregistration is None:
        status = "UNFROZEN"
    elif any(device["gate"]["status"] == "FAIL" for device in devices):
        status = "FAIL"
    elif all(device["gate"]["status"] == "PASS" for device in devices):
        status = "PASS"
    elif any(device["gate"]["status"] == "NO_DATA" for device in devices):
        status = "NO_DATA"
    else:
        status = "PENDING"

    result: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": normalize_timestamp(generated_at),
        "manifest": {
            "manifestFile": manifest_path.name,
            "sha256": manifest["manifestSha256"],
            "revision": manifest["dataset"]["revision"],
            "datasetId": manifest["dataset"]["datasetId"],
            "split": manifest["dataset"]["split"],
            "sampleCount": manifest["dataset"]["sampleCount"],
            "categoryOrder": manifest["dataset"]["categoryOrder"],
            "generatedAt": manifest["generatedAt"],
        },
        "scoring": {
            "scorer": {"name": SCORER_NAME, "version": SCORER_VERSION},
            "punctuationInsensitive": punctuation_insensitive,
            "normalization": normalization_description(punctuation_insensitive),
            "latencyPercentiles": "nearest-rank p50/p95",
            "rtf": "inferenceTimeMs/音频时长优先；否则系统显式 rtf；缺失为 null",
        },
        "preregistration": None
        if preregistration is None
        else {
            "sha256": preregistration["preregistrationSha256"],
            "decisionId": preregistration["decisionId"],
            "createdAt": preregistration["createdAt"],
            "complete": preregistration["complete"],
            "deviceCount": len(preregistration["devices"]),
        },
        "status": status,
        "passed": status == "PASS",
        "devices": devices,
        "warnings": warnings,
    }
    result["scoreSha256"] = hash_excluding(result, "scoreSha256")
    return result
