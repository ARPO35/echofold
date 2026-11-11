package com.echofold.asr.repository

import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource

/** 安装状态：`PENDING_DELETE` 表示已请求删除但仍有租约或删除事务未完成。 */
enum class InstallationState {
    ACTIVE,
    PENDING_DELETE,
}

/**
 * 一份已安装模型。
 *
 * 身份（`modelId/version/sha256`）来自受信清单或手动导入时记录的清单，运行期不再重新解析远端；
 * [directoryPath] 是安装完成后的最终目录，安装器只在 SHA-256 与解压大小校验通过后原子改名到这里。
 */
data class InstalledModel(
    val installationId: String,
    val manifest: ModelManifest,
    val source: ModelSource,
    val userConfirmed: Boolean,
    val installedAtUtcMs: Long,
    val directoryPath: String,
    val state: InstallationState = InstallationState.ACTIVE,
) {
    init {
        require(installationId.isNotBlank()) { "installationId 不能为空白字符串" }
        require(installedAtUtcMs >= 0L) { "installedAtUtcMs 不能为负数：$installedAtUtcMs" }
        require(directoryPath.isNotBlank()) { "directoryPath 不能为空白字符串" }
        if (source == ModelSource.MANUAL) {
            require(userConfirmed) { "MANUAL 来源必须记录用户确认" }
        }
    }

    val modelId: String get() = manifest.modelId
    val modelVersion: String get() = manifest.version
    val sha256: String get() = manifest.sha256
    val abi: List<String> get() = manifest.abi
    val runtimeMinVersion: String get() = manifest.runtimeMinVersion
}

/**
 * 安装索引。
 *
 * [selectedInstallationId] 是当前默认模型；[previousInstallationId] 仅用于显式回滚，
 * 不参与自动选择。
 */
data class ModelIndex(
    val selectedInstallationId: String? = null,
    val previousInstallationId: String? = null,
    val installations: List<InstalledModel> = emptyList(),
) {
    init {
        val ids = installations.map { it.installationId }
        require(ids.distinct().size == ids.size) { "安装索引中存在重复 installationId：$ids" }
        selectedInstallationId?.let { require(it in ids) { "selectedInstallationId 不在安装列表中：$it" } }
        previousInstallationId?.let { require(it in ids) { "previousInstallationId 不在安装列表中：$it" } }
    }

    /** 查找安装。 */
    fun find(installationId: String): InstalledModel? = installations.firstOrNull { it.installationId == installationId }

    /** 当前默认安装。 */
    fun selected(): InstalledModel? = selectedInstallationId?.let { find(it) }
}

/** 安装索引持久化抽象；实现必须原子写入（临时文件 + 改名）。 */
interface ModelIndexStore {
    fun load(): ModelIndex

    fun save(index: ModelIndex)
}
