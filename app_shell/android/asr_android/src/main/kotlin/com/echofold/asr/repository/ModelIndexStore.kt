package com.echofold.asr.repository

import com.echofold.asr.internal.AtomicFiles
import com.echofold.asr.model.ManifestFormatException
import com.echofold.asr.model.ManifestJson
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/** 安装索引 JSON 编解码。 */
internal object ModelIndexJson {
    private const val SCHEMA = "echofold.model-index.v1"

    fun encode(index: ModelIndex): String {
        val root =
            buildJsonObject {
                put("schema", JsonPrimitive(SCHEMA))
                index.selectedInstallationId?.let { put("selectedInstallationId", JsonPrimitive(it)) }
                index.previousInstallationId?.let { put("previousInstallationId", JsonPrimitive(it)) }
                put("installations", kotlinx.serialization.json.buildJsonArray { index.installations.forEach { add(encodeInstalled(it)) } })
            }
        return root.toString()
    }

    fun decode(text: String): ModelIndex {
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalStateException("安装索引不是合法 JSON", e)
            }
        val installations =
            root["installations"]?.jsonArray?.map { element ->
                val obj = element.jsonObject
                decodeInstalled(obj)
            } ?: emptyList()
        return ModelIndex(
            selectedInstallationId = root["selectedInstallationId"]?.let { stringValue(it, "selectedInstallationId") },
            previousInstallationId = root["previousInstallationId"]?.let { stringValue(it, "previousInstallationId") },
            installations = installations,
        )
    }

    private fun encodeInstalled(installed: InstalledModel): JsonObject =
        buildJsonObject {
            put("installationId", JsonPrimitive(installed.installationId))
            put("source", JsonPrimitive(installed.source.name))
            put("userConfirmed", JsonPrimitive(installed.userConfirmed))
            put("installedAtUtcMs", JsonPrimitive(installed.installedAtUtcMs))
            put("directoryPath", JsonPrimitive(installed.directoryPath))
            put("state", JsonPrimitive(installed.state.name))
            put("manifest", ManifestJson.toJsonObject(installed.manifest))
        }

    private fun decodeInstalled(obj: JsonObject): InstalledModel {
        val installationId = stringValue(obj["installationId"] ?: error("安装条目缺少 installationId"), "installationId")
        val source =
            ModelSource.entries.firstOrNull { it.name == stringValue(obj["source"] ?: error("安装条目缺少 source"), "source") }
                ?: throw IllegalStateException("安装条目 source 非法：$installationId")
        val state =
            InstallationState.entries.firstOrNull { it.name == stringValue(obj["state"] ?: error("安装条目缺少 state"), "state") }
                ?: throw IllegalStateException("安装条目 state 非法：$installationId")
        val manifestObject = obj["manifest"]?.jsonObject ?: throw IllegalStateException("安装条目缺少 manifest：$installationId")
        val manifest: ModelManifest =
            try {
                ManifestJson.parseManifestObject(manifestObject)
            } catch (e: ManifestFormatException) {
                throw IllegalStateException("安装条目清单非法：$installationId：${e.message}", e)
            }
        val userConfirmed = obj["userConfirmed"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
        val installedAt = obj["installedAtUtcMs"]?.let { (it as? JsonPrimitive)?.longOrNull }
            ?: throw IllegalStateException("安装条目缺少 installedAtUtcMs：$installationId")
        val directoryPath = stringValue(obj["directoryPath"] ?: error("安装条目缺少 directoryPath"), "directoryPath")
        return InstalledModel(
            installationId = installationId,
            manifest = manifest,
            source = source,
            userConfirmed = userConfirmed,
            installedAtUtcMs = installedAt,
            directoryPath = directoryPath,
            state = state,
        )
    }

    private fun stringValue(element: kotlinx.serialization.json.JsonElement, field: String): String {
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("$field 必须是字符串")
        if (!primitive.isString) throw IllegalStateException("$field 必须是字符串")
        return primitive.content
    }
}

/** 文件版安装索引：`echofold-model-index.json`，原子写入；文件不存在视为空索引。 */
class FileModelIndexStore(
    private val file: File,
) : ModelIndexStore {
    private val lock = Any()

    override fun load(): ModelIndex = synchronized(lock) {
        if (!file.isFile) return ModelIndex()
        val text =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                throw IllegalStateException("读取安装索引失败：$file", e)
            }
        ModelIndexJson.decode(text)
    }

    override fun save(index: ModelIndex) = synchronized(lock) {
        try {
            AtomicFiles.writeText(file, ModelIndexJson.encode(index))
        } catch (e: IOException) {
            throw IllegalStateException("写入安装索引失败：$file", e)
        }
    }
}
