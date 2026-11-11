package com.echofold.asr.install

import com.echofold.asr.model.ModelManifest

/**
 * 安装 ID 生成规则。
 *
 * 以工件 SHA-256 前 16 位为主键，叠加清洗后的 `modelId/version` 便于人工识别；同一工件
 * 重复安装得到同一 ID，从而可与安装索引做幂等对账。
 */
object InstallationIds {
    /** 生成 installationId。 */
    fun of(manifest: ModelManifest): String {
        val model = sanitize(manifest.modelId)
        val version = sanitize(manifest.version)
        return "${manifest.sha256.take(16)}-$model-$version"
    }

    private fun sanitize(value: String): String =
        value
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
            .ifBlank { "model" }
}
