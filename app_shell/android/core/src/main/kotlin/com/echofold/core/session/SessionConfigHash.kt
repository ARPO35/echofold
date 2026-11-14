package com.echofold.core.session

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.ModelRef
import java.security.MessageDigest

/**
 * 会话固定识别配置的稳定哈希。
 *
 * 会话开始时把 `ModelRef + AsrConfig` 序列化为规范字符串并取 SHA-256；实时识别与所有重识别任务
 * 都用同一哈希对账。字段顺序固定，版本号风格与 [com.echofold.core.model.SourceSet] 一致。
 */
object SessionConfigHash {
    /** 计算配置哈希；无模型时返回 `null`（仅录音会话）。 */
    fun of(model: ModelRef?, config: AsrConfig?): String? {
        if (model == null || config == null) return null
        require(config.model == model) { "AsrConfig.model 必须与会话固定 ModelRef 一致" }
        val canonical =
            listOf(
                "modelId=${model.modelId}",
                "modelVersion=${model.modelVersion}",
                "installationId=${model.installationId}",
                "sha256=${model.sha256}",
                "sampleRate=${config.sampleRate}",
                "chunkMs=${config.chunkMs}",
                "threads=${config.threads}",
                "enableVad=${config.enableVad}",
            ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
