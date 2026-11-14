"""评测工具的统一异常类型。"""

from __future__ import annotations


class BenchmarkError(Exception):
    """所有可预期错误的基类；CLI 统一转成非零退出码。"""

    exit_code = 2


class ManifestError(BenchmarkError):
    """manifest 创建或校验失败。"""


class ScoringError(BenchmarkError):
    """评分输入校验失败。"""


class PreregistrationError(BenchmarkError):
    """预注册记录创建或校验失败。"""


class ReportError(BenchmarkError):
    """报告生成失败。"""
