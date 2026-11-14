"""Markdown 基准报告生成。

报告固定分成三栏：作者基准、官方或第三方证据、本项目实测。本项目结果
按设备逐表展示，绝不生成跨设备平均或排名；任何缺失值都写成“待测”。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from .errors import ReportError
from .prereg import load_preregistration
from .util import SCHEMA_VERSION, hash_excluding, normalize_timestamp, read_json, write_text_atomic

STATUS_LABELS = {
    "PASS": "通过",
    "FAIL": "未通过",
    "PENDING": "待测/待决策",
    "UNFROZEN": "未冻结/待决策",
    "NO_DATA": "无数据（待测）",
}
CHECK_LABELS = {
    "pass": "通过",
    "fail": "失败",
    "pending": "待测",
}
EVIDENCE_CLASSES = {
    "author": "作者基准",
    "official": "官方证据",
    "third-party": "第三方证据",
}


def _md(value: Any) -> str:
    if value is None or value == "":
        return "null（待测）"
    text = str(value)
    return text.replace("|", "\\|").replace("\n", "<br>").replace("\r", "")


def _fmt_float(value: Any, digits: int = 4) -> str:
    if value is None:
        return "null（待测）"
    if isinstance(value, bool):
        return "是" if value else "否"
    if isinstance(value, int):
        return str(value)
    number = float(value)
    if number.is_integer():
        return str(int(number))
    return f"{number:.{digits}f}".rstrip("0").rstrip(".")


def _fmt_rate(rate: Any, edits: Any, units: Any) -> str:
    if rate is None:
        return "null（待测）"
    return f"{float(rate):.4f}（{float(rate) * 100:.2f}%；{edits}/{units}）"


def _fmt_latency(stats: dict[str, Any]) -> str:
    if not stats or stats.get("p50") is None:
        return "null（待测）"
    text = (
        f"{_fmt_float(stats['p50'], 1)} / "
        f"{_fmt_float(stats['p95'], 1)} / "
        f"{_fmt_float(stats['max'], 1)}"
    )
    if stats.get("missingCount"):
        text += f"（缺 {stats['missingCount']}）"
    return text


def _fmt_gap(gap: dict[str, Any]) -> tuple[str, str]:
    if not gap or gap.get("count") is None:
        if gap and gap.get("status") == "partial":
            return (
                f"null（待测；已知 {gap['knownCount']} 次）",
                f"null（待测；已知 {_fmt_float(gap['knownDurationMs'], 0)}）",
            )
        return "null（待测）", "null（待测）"
    return str(gap["count"]), _fmt_float(gap.get("durationMs"), 0)


def _table(headers: list[str], rows: list[list[str]]) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "|" + "|".join("---" for _ in headers) + "|",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def _load_score(path: Path) -> dict[str, Any]:
    raw = read_json(path)
    if not isinstance(raw, dict):
        raise ReportError("score 文件顶层必须是对象")
    if raw.get("schemaVersion") != SCHEMA_VERSION:
        raise ReportError("score schemaVersion 不受支持")
    stored = raw.get("scoreSha256")
    if not isinstance(stored, str):
        raise ReportError("score 缺少 scoreSha256，拒绝生成报告")
    computed = hash_excluding(raw, "scoreSha256")
    if computed != stored:
        raise ReportError(
            f"score 自哈希不一致：记录 {stored}，复算 {computed}（文件被篡改？）"
        )
    if not isinstance(raw.get("devices"), list):
        raise ReportError("score 缺少 devices 数组")
    return raw


def _load_evidence(path: Path) -> list[dict[str, Any]]:
    raw = read_json(path)
    if not isinstance(raw, dict) or not isinstance(raw.get("entries"), list):
        raise ReportError("evidence 文件必须包含 entries 数组")
    entries = raw["entries"]
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ReportError(f"evidence.entries[{index}] 必须是对象")
        if entry.get("evidenceClass") not in EVIDENCE_CLASSES:
            raise ReportError(
                f"evidence.entries[{index}].evidenceClass 必须是 "
                f"{', '.join(EVIDENCE_CLASSES)}"
            )
    return entries


def _evidence_table(entries: list[dict[str, Any]]) -> str:
    rows = []
    for entry in entries:
        rows.append(
            [
                _md(entry.get("source")),
                _md(entry.get("model")),
                _md(entry.get("artifact")),
                _md(entry.get("revision")),
                _md(entry.get("sha256")),
                _md(entry.get("testSet")),
                _md(entry.get("conditions")),
                _md(entry.get("metric")),
                _md(entry.get("value")),
                _md(entry.get("note")),
            ]
        )
    return _table(
        [
            "来源",
            "模型",
            "工件",
            "revision",
            "SHA-256",
            "测试集",
            "条件",
            "指标",
            "数值",
            "备注",
        ],
        rows,
    )


def _device_spec_rows(device: dict[str, Any], model: dict[str, Any], run: dict[str, Any]) -> list[list[str]]:
    fields = [
        ("设备 ID", device.get("deviceId")),
        ("设备型号", device.get("model")),
        ("SoC", device.get("soc")),
        ("RAM (GB)", device.get("ramGb")),
        ("OS", device.get("os")),
        ("系统 build", device.get("build")),
        ("线程", device.get("threads") or run.get("threads")),
        ("运行时/ABI", device.get("runtime") or model.get("runtime")),
        ("ABI", device.get("abi") or model.get("abi")),
        ("供电状态", run.get("powerState") or device.get("powerState")),
        ("厂商电池策略", device.get("batteryPolicy")),
        ("环境温度 (°C)", device.get("ambientTempC")),
        ("观测窗口", run.get("window")),
        ("开始时间", run.get("startedAt")),
        ("结束时间", run.get("endedAt")),
        ("运行样本数", run.get("sampleCount")),
        ("内存峰值 (MB)", run.get("memoryPeakMb")),
        ("温度峰值 (°C)", run.get("temperaturePeakC")),
        ("队列最大深度", run.get("queueMaxDepth")),
        ("队列 oldest age (ms)", run.get("queueOldestAgeMs")),
    ]
    return [[label, _md(value)] for label, value in fields]


def _artifact_rows(model: dict[str, Any]) -> list[list[str]]:
    fields = [
        ("模型文件", model.get("file")),
        ("上游来源", model.get("source")),
        ("revision", model.get("revision")),
        ("SHA-256", model.get("sha256")),
        ("字节数", model.get("bytes")),
        ("量化工具", model.get("quantizationTool")),
        ("量化工具版本", model.get("quantizationToolVersion")),
        ("量化命令", model.get("quantizationCommand")),
        ("校准/配置输入", model.get("calibrationInput")),
        ("运行时/ABI", model.get("runtime") or model.get("abi")),
    ]
    return [[label, _md(value)] for label, value in fields]


def _metrics_rows(score_device: dict[str, Any], manifest: dict[str, Any]) -> list[list[str]]:
    rows = []
    category_order = manifest.get("categoryOrder") or []
    entries = [(category, score_device["categories"].get(category, {})) for category in category_order]
    entries.append(("总体（本设备）", score_device["overall"]))
    for label, aggregate in entries:
        if not aggregate:
            rows.append([_md(label)] + ["null（待测）"] * 13)
            continue
        gap_count, gap_duration = _fmt_gap(aggregate.get("gap", {}))
        rows.append(
            [
                _md(label),
                str(aggregate.get("sampleCount", 0)),
                f"{aggregate.get('scoredCount', 0)} / {aggregate.get('missingCount', 0)}",
                _fmt_rate(
                    aggregate.get("cer"),
                    aggregate.get("cerEdits"),
                    aggregate.get("cerRefUnits"),
                ),
                _fmt_rate(
                    aggregate.get("wer"),
                    aggregate.get("werEdits"),
                    aggregate.get("werRefUnits"),
                ),
                _fmt_float(aggregate.get("rtf")),
                _md(aggregate.get("rtfMethod")),
                _fmt_latency(aggregate.get("asrLagMs", {})),
                _fmt_latency(aggregate.get("partialLatencyMs", {})),
                _fmt_latency(aggregate.get("oldestPendingAudioLagMs", {})),
                gap_count,
                gap_duration,
                str(aggregate.get("failureCount", 0)),
            ]
        )
    return rows


def _gate_rows(gate: dict[str, Any]) -> list[list[str]]:
    rows = []
    for check in gate.get("checks", []):
        rows.append(
            [
                _md(check.get("name")),
                _md(check.get("threshold")),
                _md(check.get("measured")),
                CHECK_LABELS.get(check.get("status", ""), _md(check.get("status"))),
                _md(check.get("note")),
            ]
        )
    if not rows:
        rows.append(["预注册门槛", "null（待测）", "null（待测）", "待测", "未冻结/待决策"])
    return rows


def _failure_rows(score_device: dict[str, Any]) -> list[list[str]]:
    rows = []
    for failure in score_device.get("failures", []):
        rows.append(
            [
                _md(failure.get("sampleId")),
                _md(failure.get("category")),
                _md(failure.get("error")),
            ]
        )
    return rows


def _gap_rows(score_device: dict[str, Any]) -> list[list[str]]:
    rows = []
    for sample in score_device.get("samples", []):
        for interval in sample.get("gapIntervals") or []:
            rows.append(
                [
                    _md(sample.get("sampleId")),
                    _fmt_float(interval.get("startMs"), 0),
                    _fmt_float(interval.get("endMs"), 0),
                    _fmt_float(interval.get("endMs", 0) - interval.get("startMs", 0), 0),
                    _md(interval.get("reason")),
                    _md(interval.get("chunkSeq")),
                    _md(interval.get("modelRevision")),
                ]
            )
    return rows


def _missing_summary(score_device: dict[str, Any]) -> list[list[str]]:
    counts: dict[str, int] = {}
    affected: dict[str, list[str]] = {}
    for sample in score_device.get("samples", []):
        for field in sample.get("missingFields", []):
            counts[field] = counts.get(field, 0) + 1
            affected.setdefault(field, []).append(sample["sampleId"])
    rows = []
    for field in sorted(counts):
        rows.append([_md(field), str(counts[field]), _md(", ".join(affected[field]))])
    return rows


def build_report(
    *,
    score_path: Path,
    evidence_path: Path | None = None,
    preregistration_path: Path | None = None,
    generated_at: str | None = None,
) -> str:
    score = _load_score(score_path)
    if preregistration_path is not None:
        preregistration = load_preregistration(preregistration_path)
        score_prereg = score.get("preregistration")
        if not isinstance(score_prereg, dict):
            raise ReportError("score 未引用预注册，不能用另一份预注册生成报告")
        if score_prereg.get("sha256") != preregistration["preregistrationSha256"]:
            raise ReportError(
                "预注册哈希不一致：score 引用 "
                f"{score_prereg.get('sha256')}，报告参引 "
                f"{preregistration['preregistrationSha256']}"
            )
    else:
        preregistration = None

    evidence = _load_evidence(evidence_path) if evidence_path is not None else []
    author_entries = [entry for entry in evidence if entry["evidenceClass"] == "author"]
    other_entries = [entry for entry in evidence if entry["evidenceClass"] != "author"]

    manifest = score.get("manifest") or {}
    scoring = score.get("scoring") or {}
    prereg_info = score.get("preregistration")
    status = str(score.get("status", "PENDING"))
    status_label = STATUS_LABELS.get(status, status)

    lines: list[str] = []
    lines.append("# ASR / 重识别基准报告")
    lines.append("")
    lines.append(f"> 生成时间：{normalize_timestamp(generated_at)}")
    lines.append(">")
    lines.append(
        f"> 数据集 manifest：`{_md(manifest.get('manifestFile'))}`，"
        f"SHA-256 `{_md(manifest.get('sha256'))}`，"
        f"revision `{_md(manifest.get('revision'))}`，"
        f"划分 `{_md(manifest.get('split'))}`，"
        f"样本数 {_md(manifest.get('sampleCount'))}"
    )
    if prereg_info:
        lines.append(
            f"> 预注册：`{_md(prereg_info.get('sha256'))}`，"
            f"决策 `{_md(prereg_info.get('decisionId'))}`，"
            f"完整性 `{_md(prereg_info.get('complete'))}`"
        )
    else:
        lines.append("> 预注册：未冻结/待决策（score 未引用预注册）")
    lines.append(
        f"> 评分器：{_md((scoring.get('scorer') or {}).get('name'))} "
        f"`{_md((scoring.get('scorer') or {}).get('version'))}`，"
        f"标点不敏感：{_md(scoring.get('punctuationInsensitive'))}"
    )
    lines.append(f"> 结论：**{status_label}**（score status=`{status}`，passed=`{score.get('passed')}`）")
    lines.append("")
    lines.append(
        "> 规则：作者基准、官方/第三方证据和本项目实测分栏；本项目结果按设备分表，"
        "禁止跨设备平均、排名或用一台设备替代另一台。缺失数据一律标“待测”，不当作 0。"
    )
    lines.append("")

    lines.append("## 1. 作者基准")
    lines.append("")
    if author_entries:
        lines.append(_evidence_table(author_entries))
    else:
        lines.append("待测（未录入作者基准证据；不得用作者基准冒充本项目实测）。")
    lines.append("")

    lines.append("## 2. 官方或第三方证据")
    lines.append("")
    if other_entries:
        rows = []
        for entry in other_entries:
            rows.append(
                [
                    EVIDENCE_CLASSES[entry["evidenceClass"]],
                    _md(entry.get("source")),
                    _md(entry.get("model")),
                    _md(entry.get("artifact")),
                    _md(entry.get("revision")),
                    _md(entry.get("sha256")),
                    _md(entry.get("testSet")),
                    _md(entry.get("conditions")),
                    _md(entry.get("metric")),
                    _md(entry.get("value")),
                    _md(entry.get("note")),
                ]
            )
        lines.append(
            _table(
                [
                    "类别",
                    "来源",
                    "模型",
                    "工件",
                    "revision",
                    "SHA-256",
                    "测试集",
                    "条件",
                    "指标",
                    "数值",
                    "备注",
                ],
                rows,
            )
        )
    else:
        lines.append("待测（未录入官方或第三方证据）。")
    lines.append("")

    lines.append("## 3. 本项目实测")
    lines.append("")
    lines.append(
        "以下数据只来自本项目在指定设备、工件和协议下的运行；不同设备分表，"
        "不计算跨设备平均值或排名。"
    )
    lines.append("")
    devices = score.get("devices") or []
    if not devices:
        lines.append("待测（score 中没有设备运行数据）。")
    else:
        summary_rows = []
        for device in devices:
            gate = device.get("gate") or {}
            summary_rows.append(
                [
                    _md(device.get("deviceId")),
                    _md((device.get("device") or {}).get("model")),
                    _md(STATUS_LABELS.get(gate.get("status"), gate.get("status"))),
                    str((device.get("overall") or {}).get("sampleCount", 0)),
                    _md((device.get("run") or {}).get("window")),
                ]
            )
        lines.append(
            _table(["设备", "型号", "门槛判定", "样本数", "观测窗口"], summary_rows)
        )
    lines.append("")

    for index, device in enumerate(devices, start=1):
        device_info = device.get("device") or {}
        model = device.get("model") or {}
        run = device.get("run") or {}
        gate = device.get("gate") or {}
        lines.append(f"### 3.{index} 设备 `{_md(device.get('deviceId'))}`")
        lines.append("")
        lines.append(
            f"门槛判定：**{STATUS_LABELS.get(gate.get('status'), gate.get('status'))}**"
            f"（status=`{gate.get('status')}`，passed=`{gate.get('passed')}`）；"
            f"预注册哈希 `{_md(device.get('preregistrationSha256'))}`"
        )
        lines.append("")
        lines.append("#### 设备与运行条件")
        lines.append("")
        lines.append(_table(["项目", "值"], _device_spec_rows(device_info, model, run)))
        lines.append("")
        lines.append("#### 精确工件")
        lines.append("")
        lines.append(_table(["项目", "值"], _artifact_rows(model)))
        lines.append("")
        lines.append("#### 指标（逐类别）")
        lines.append("")
        lines.append(
            _table(
                [
                    "范围",
                    "样本数",
                    "已评/缺失",
                    "CER",
                    "WER",
                    "RTF",
                    "RTF 方法",
                    "ASR落后 p50/p95/max (ms)",
                    "Partial p50/p95/max (ms)",
                    "oldest-pending p50/p95/max (ms)",
                    "GAP 次数",
                    "GAP 总时长 (ms)",
                    "失败样本",
                ],
                _metrics_rows(device, manifest),
            )
        )
        lines.append("")
        lines.append("#### 门槛判定")
        lines.append("")
        lines.append(
            _table(
                ["门槛", "预注册值", "实测", "判定", "备注"],
                _gate_rows(gate),
            )
        )
        lines.append("")
        lines.append("#### 失败样例")
        lines.append("")
        failures = _failure_rows(device)
        if failures:
            lines.append(_table(["样本", "类别", "错误"], failures))
        else:
            lines.append("无失败样本（0 个；仍以门槛判定为准）。")
        lines.append("")
        lines.append("#### GAP 区间")
        lines.append("")
        gap_rows = _gap_rows(device)
        if gap_rows:
            lines.append(
                _table(
                    ["样本", "startMs", "endMs", "时长 ms", "reason", "chunkSeq", "modelRevision"],
                    gap_rows,
                )
            )
        else:
            gap_status = (device.get("overall") or {}).get("gap", {}).get("status")
            if gap_status == "measured":
                lines.append("实测 0 个 GAP 区间。")
            else:
                lines.append("待测（GAP 字段缺失；不能当作 0）。")
        lines.append("")
        lines.append("#### 缺失字段（待测）")
        lines.append("")
        missing = _missing_summary(device)
        if missing:
            lines.append(_table(["字段", "缺失样本数", "样本"], missing))
        else:
            lines.append("无缺失字段。")
        lines.append("")

    lines.append("## 4. 未测项与真实数据缺口")
    lines.append("")
    warnings = score.get("warnings") or []
    if warnings:
        for warning in warnings:
            lines.append(f"- {_md(warning)}")
    else:
        lines.append("- 无评分器警告；仍请核对逐设备表中所有“待测”项。")
    if not evidence:
        lines.append("- 作者基准/官方/第三方证据未录入：待测。")
    if not prereg_info:
        lines.append("- 预注册未冻结：门槛与通过结论均为未冻结/待决策。")
    lines.append(
        "- 30 分钟尖刺与两小时发布长测必须分别执行、分别报告；本报告不能替代任一未执行的长测。"
    )
    lines.append("")
    return "\n".join(lines)


def write_report(
    *,
    score_path: Path,
    out: Path,
    evidence_path: Path | None = None,
    preregistration_path: Path | None = None,
    generated_at: str | None = None,
) -> str:
    content = build_report(
        score_path=score_path,
        evidence_path=evidence_path,
        preregistration_path=preregistration_path,
        generated_at=generated_at,
    )
    write_text_atomic(out, content)
    return content
