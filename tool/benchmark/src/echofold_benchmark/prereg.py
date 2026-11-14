"""预注册门槛的冻结与校验。

预注册记录把数据集 manifest 哈希、设备、CER/WER 上限、RTF、落后分位数
和资源预算绑定成一个带自哈希的 JSON。评分与报告只能引用同一哈希；缺失
时状态为“未冻结/待决策”，不一致时直接拒绝。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from .errors import PreregistrationError
from .manifest import load_manifest
from .normalize import normalization_description
from .util import (
    CATEGORY_ORDER,
    SCHEMA_VERSION,
    SCORER_NAME,
    SCORER_VERSION,
    hash_excluding,
    normalize_timestamp,
    read_json,
    write_json,
)

PREREG_HASH_FIELD = "preregistrationSha256"
BUDGET_FIELDS = (
    "memoryMaxMb",
    "temperatureMaxC",
    "queueMaxDepth",
    "queueOldestAgeMsMax",
)
CATEGORY_THRESHOLD_FIELDS = ("cerMaxByCategory", "werMaxByCategory")
REQUIRED_DEVICE_FIELDS = ("deviceId", "model", "soc", "ramGb")


def _number(
    value: Any,
    name: str,
    *,
    minimum: float | None = None,
    maximum: float | None = None,
    allow_none: bool = False,
) -> float | None:
    if value is None:
        if allow_none:
            return None
        raise PreregistrationError(f"预注册缺少数值门槛：{name}")
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise PreregistrationError(f"预注册门槛 {name} 必须是数值")
    number = float(value)
    if minimum is not None and number < minimum:
        raise PreregistrationError(f"预注册门槛 {name} 不能小于 {minimum}")
    if maximum is not None and number > maximum:
        raise PreregistrationError(f"预注册门槛 {name} 不能大于 {maximum}")
    return number


def _category_map(value: Any, name: str, categories: set[str]) -> dict[str, float]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise PreregistrationError(f"{name} 必须是对象")
    result: dict[str, float] = {}
    for category, raw in value.items():
        if category not in CATEGORY_ORDER:
            raise PreregistrationError(f"{name} 含未知类别：{category}")
        if category not in categories:
            raise PreregistrationError(
                f"{name} 含 manifest 中不存在的类别：{category}"
            )
        result[category] = float(
            _number(raw, f"{name}.{category}", minimum=0.0, maximum=1.0)
        )
    return result


def _normalize_device(
    raw: dict[str, Any], categories: set[str]
) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raise PreregistrationError("devices 项必须是对象")
    for field in REQUIRED_DEVICE_FIELDS:
        if field not in raw:
            raise PreregistrationError(f"设备预注册缺少字段：{field}")
    device_id = str(raw["deviceId"]).strip()
    if not device_id:
        raise PreregistrationError("deviceId 不能为空")
    thresholds_raw = raw.get("thresholds")
    if not isinstance(thresholds_raw, dict):
        raise PreregistrationError(f"设备 {device_id} 缺少 thresholds 对象")

    cer_map = _category_map(
        thresholds_raw.get("cerMaxByCategory"), "cerMaxByCategory", categories
    )
    wer_map = _category_map(
        thresholds_raw.get("werMaxByCategory"), "werMaxByCategory", categories
    )
    if not cer_map and not wer_map:
        raise PreregistrationError(f"设备 {device_id} 至少需要一个 CER/WER 类别门槛")

    rtf_max = _number(
        thresholds_raw.get("rtfMax"), "rtfMax", minimum=0.0, maximum=1.0
    )
    p95_lag = _number(
        thresholds_raw.get("p95AsrLagMsMax"),
        "p95AsrLagMsMax",
        minimum=1.0,
        maximum=15000.0,
    )
    p95_partial = _number(
        thresholds_raw.get("p95PartialLatencyMsMax"),
        "p95PartialLatencyMsMax",
        minimum=1.0,
        allow_none=True,
    )
    memory = _number(
        thresholds_raw.get("memoryMaxMb"),
        "memoryMaxMb",
        minimum=1.0,
        allow_none=True,
    )
    temperature = _number(
        thresholds_raw.get("temperatureMaxC"),
        "temperatureMaxC",
        minimum=1.0,
        allow_none=True,
    )
    queue_depth = _number(
        thresholds_raw.get("queueMaxDepth"),
        "queueMaxDepth",
        minimum=1.0,
        allow_none=True,
    )
    queue_age = _number(
        thresholds_raw.get("queueOldestAgeMsMax"),
        "queueOldestAgeMsMax",
        minimum=1.0,
        allow_none=True,
    )

    thresholds = {
        "cerMaxByCategory": cer_map,
        "werMaxByCategory": wer_map,
        "rtfMax": rtf_max,
        "p95AsrLagMsMax": p95_lag,
        "p95PartialLatencyMsMax": p95_partial,
        "memoryMaxMb": memory,
        "temperatureMaxC": temperature,
        "queueMaxDepth": queue_depth,
        "queueOldestAgeMsMax": queue_age,
    }
    complete = all(thresholds[field] is not None for field in BUDGET_FIELDS)
    complete = complete and all(category in cer_map or category in wer_map for category in categories)

    return {
        "deviceId": device_id,
        "model": str(raw["model"]),
        "soc": str(raw["soc"]),
        "ramGb": float(raw["ramGb"]),
        "os": None if raw.get("os") is None else str(raw["os"]),
        "build": None if raw.get("build") is None else str(raw["build"]),
        "thresholds": thresholds,
        "complete": complete,
    }


def create_preregistration(
    *,
    manifest_path: Path,
    config_path: Path,
    out: Path,
    force: bool = False,
    generated_at: str | None = None,
) -> dict[str, Any]:
    manifest = load_manifest(manifest_path)
    dataset = manifest["dataset"]
    categories = {
        sample["category"] for sample in manifest["samples"] if isinstance(sample, dict)
    }

    config = read_json(config_path)
    if not isinstance(config, dict):
        raise PreregistrationError("预注册配置顶层必须是对象")
    if config.get("schemaVersion") != SCHEMA_VERSION:
        raise PreregistrationError("预注册配置 schemaVersion 不受支持")
    decision_id = config.get("decisionId")
    if not isinstance(decision_id, str) or not decision_id.strip():
        raise PreregistrationError("预注册配置缺少 decisionId")
    devices_raw = config.get("devices")
    if not isinstance(devices_raw, list) or not devices_raw:
        raise PreregistrationError("预注册配置至少需要一台设备")

    devices = []
    seen_devices: set[str] = set()
    for raw_device in devices_raw:
        device = _normalize_device(raw_device, categories)
        if device["deviceId"] in seen_devices:
            raise PreregistrationError(f"设备重复：{device['deviceId']}")
        seen_devices.add(device["deviceId"])
        devices.append(device)

    if out.exists() and not force:
        raise PreregistrationError(
            f"预注册文件已存在：{out}（拒绝覆盖；如需重建请用 --force）"
        )

    preregistration: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "createdAt": normalize_timestamp(generated_at),
        "decisionId": decision_id.strip(),
        "manifest": {
            "manifestFile": manifest_path.name,
            "sha256": manifest["manifestSha256"],
            "revision": dataset["revision"],
            "datasetId": dataset["datasetId"],
            "split": dataset["split"],
            "sampleCount": dataset["sampleCount"],
            "categoryOrder": dataset["categoryOrder"],
        },
        "devices": devices,
        "complete": all(device["complete"] for device in devices),
        "protocol": {
            "scorer": {"name": SCORER_NAME, "version": SCORER_VERSION},
            "normalization": normalization_description(False),
            "timing": "延迟字段由系统输出提供；评分器不推算",
            "latencyPercentiles": "nearest-rank p50/p95",
        },
        "notes": config.get("notes"),
    }
    preregistration[PREREG_HASH_FIELD] = hash_excluding(
        preregistration, PREREG_HASH_FIELD
    )
    write_json(out, preregistration, overwrite=True, backup=force and out.exists())
    return preregistration


def validate_preregistration_hash(
    preregistration: dict[str, Any], *, source: str = "preregistration"
) -> str:
    stored = preregistration.get(PREREG_HASH_FIELD)
    if not isinstance(stored, str) or len(stored) != 64:
        raise PreregistrationError(f"{source} 缺少合法的 {PREREG_HASH_FIELD}")
    computed = hash_excluding(preregistration, PREREG_HASH_FIELD)
    if computed != stored:
        raise PreregistrationError(
            f"{source} 自哈希不一致：记录 {stored}，复算 {computed}（文件被篡改？）"
        )
    return stored


def load_preregistration(
    path: Path, manifest: dict[str, Any] | None = None
) -> dict[str, Any]:
    preregistration = read_json(path)
    if not isinstance(preregistration, dict):
        raise PreregistrationError("预注册文件顶层必须是对象")
    if preregistration.get("schemaVersion") != SCHEMA_VERSION:
        raise PreregistrationError("预注册 schemaVersion 不受支持")
    validate_preregistration_hash(preregistration, source=str(path))
    devices = preregistration.get("devices")
    if not isinstance(devices, list) or not devices:
        raise PreregistrationError("预注册没有设备")
    manifest_info = preregistration.get("manifest")
    if not isinstance(manifest_info, dict):
        raise PreregistrationError("预注册缺少 manifest 绑定信息")

    if manifest is not None:
        expected = manifest["manifestSha256"]
        recorded = manifest_info.get("sha256")
        if recorded != expected:
            raise PreregistrationError(
                "预注册引用的 manifest 哈希与当前 manifest 不一致："
                f"{recorded} != {expected}"
            )
        if manifest_info.get("revision") != manifest["dataset"]["revision"]:
            raise PreregistrationError("预注册 revision 与当前 manifest 不一致")
        if manifest_info.get("split") != manifest["dataset"]["split"]:
            raise PreregistrationError(
                "预注册数据集划分（pilot/final）与当前 manifest 不一致"
            )
        if manifest_info.get("datasetId") != manifest["dataset"]["datasetId"]:
            raise PreregistrationError("预注册 datasetId 与当前 manifest 不一致")
    return preregistration


def preregistration_device(
    preregistration: dict[str, Any], device_id: str
) -> dict[str, Any] | None:
    for device in preregistration.get("devices", []):
        if device.get("deviceId") == device_id:
            return device
    return None
