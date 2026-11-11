package com.echofold.asr.repository

import com.echofold.asr.internal.AtomicFiles
import com.echofold.asr.model.ManifestJson
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 安装目录内的清单快照（`echofold-model.json`）。
 *
 * 引擎初始化时用它交叉核对 [com.echofold.core.asr.engine.ModelRef] 的身份，避免把“别的目录”
 * 或已被替换的权重当成会话固定模型；安装器在原子改名之前写入该文件。
 */
data class ModelSnapshot(
    val installationId: String,
    val manifest: ModelManifest,
    val source: ModelSource,
    val userConfirmed: Boolean,
    val installedAtUtcMs: Long,
) {
    /** 生成带安装目录的索引条目。 */
    fun toInstalledModel(directory: File): InstalledModel =
        InstalledModel(
            installationId = installationId,
            manifest = manifest,
            source = source,
            userConfirmed = userConfirmed,
            installedAtUtcMs = installedAtUtcMs,
            directoryPath = directory.absolutePath,
            state = InstallationState.ACTIVE,
        )
}

/** 清单快照读写。 */
object ModelSnapshotJson {
    /** 快照文件名。 */
    const val FILE_NAME = "echofold-model.json"

    private const val SCHEMA = "echofold.model-snapshot.v1"

    /** 写入安装目录；目录必须已存在。 */
    fun write(directory: File, snapshot: ModelSnapshot) {
        require(directory.isDirectory) { "安装目录不存在：$directory" }
        val root =
            buildJsonObject {
                put("schema", JsonPrimitive(SCHEMA))
                put("installationId", JsonPrimitive(snapshot.installationId))
                put("source", JsonPrimitive(snapshot.source.name))
                put("userConfirmed", JsonPrimitive(snapshot.userConfirmed))
                put("installedAtUtcMs", JsonPrimitive(snapshot.installedAtUtcMs))
                put("manifest", ManifestJson.toJsonObject(snapshot.manifest))
            }
        AtomicFiles.writeText(File(directory, FILE_NAME), root.toString())
    }

    /** 读取快照；文件不存在返回 `null`，损坏时抛 [IllegalStateException]。 */
    fun read(directory: File): ModelSnapshot? {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) return null
        val root =
            try {
                Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            } catch (e: Exception) {
                throw IllegalStateException("模型清单快照不是合法 JSON：$file", e)
            }
        val installationId = stringValue(root["installationId"], "installationId")
        val source =
            ModelSource.entries.firstOrNull { it.name == stringValue(root["source"], "source") }
                ?: throw IllegalStateException("模型清单快照 source 非法：$file")
        val userConfirmed = (root["userConfirmed"] as? JsonPrimitive)?.booleanOrNull ?: false
        val installedAt = (root["installedAtUtcMs"] as? JsonPrimitive)?.longOrNull
            ?: throw IllegalStateException("模型清单快照缺少 installedAtUtcMs：$file")
        val manifestObject = root["manifest"]?.jsonObject ?: throw IllegalStateException("模型清单快照缺少 manifest：$file")
        return ModelSnapshot(
            installationId = installationId,
            manifest = ManifestJson.parseManifestObject(manifestObject),
            source = source,
            userConfirmed = userConfirmed,
            installedAtUtcMs = installedAt,
        )
    }

    private fun stringValue(element: kotlinx.serialization.json.JsonElement?, field: String): String {
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("模型清单快照缺少 $field")
        if (!primitive.isString) throw IllegalStateException("模型清单快照 $field 必须是字符串")
        return primitive.content
    }
}
