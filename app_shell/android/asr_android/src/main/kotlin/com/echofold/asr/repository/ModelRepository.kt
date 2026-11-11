package com.echofold.asr.repository

import java.io.File

/** 重启恢复报告。 */
data class RepositoryRecoveryReport(
    /** 因会话/任务不存在而释放的租约。 */
    val releasedLeases: List<ModelLease>,
    /** 恢复时发现目录缺失的安装。 */
    val missingInstallations: List<String>,
    /** 恢复期间实际清理掉的安装。 */
    val deletedInstallations: List<String>,
)

/**
 * 模型安装仓库：安装索引、当前选择、更新、回滚、pending-delete 与租约协调。
 *
 * 线程模型：所有公开方法互斥；租约管理器与索引存储自身也加锁，二者嵌套顺序固定为
 * 仓库锁 → 租约锁，避免死锁。
 */
class ModelRepository(
    private val indexStore: ModelIndexStore,
    private val leases: ModelLeaseManager,
) {
    private val lock = Any()

    /** 全部安装（含 pending-delete）。 */
    fun list(): List<InstalledModel> = synchronized(lock) { indexStore.load().installations }

    /** 当前默认安装。 */
    fun current(): InstalledModel? = synchronized(lock) { indexStore.load().selected() }

    /** 按 installationId 查找。 */
    fun find(installationId: String): InstalledModel? = synchronized(lock) { indexStore.load().find(installationId) }

    /** 租约管理器（供会话层释放租约）。 */
    fun leaseManager(): ModelLeaseManager = leases

    /**
     * 登记一份已安装模型。
     *
     * 同一 installationId 已存在时直接返回既有条目（幂等），不回退 pending-delete 状态。
     * [select] 为真时将其设为当前默认，原默认转入回滚位。
     */
    fun install(installed: InstalledModel, select: Boolean = true): InstalledModel = synchronized(lock) {
        if (!File(installed.directoryPath).isDirectory) {
            throw IllegalArgumentException("安装目录不存在：${installed.directoryPath}")
        }
        val index = indexStore.load()
        index.find(installed.installationId)?.let { return it }
        var next =
            index.copy(
                installations = index.installations + installed,
                previousInstallationId = if (select && index.selectedInstallationId != null) index.selectedInstallationId else index.previousInstallationId,
                selectedInstallationId = if (select) installed.installationId else index.selectedInstallationId,
            )
        if (installed.state == InstallationState.PENDING_DELETE) {
            leases.markNotAcquirable(installed.installationId)
        }
        next = normalizeSelection(next)
        indexStore.save(next)
        installed
    }

    /** 选择当前默认安装；目标必须存在且为 ACTIVE。 */
    fun select(installationId: String): InstalledModel = synchronized(lock) {
        val index = indexStore.load()
        val target = index.find(installationId)
            ?: throw IllegalArgumentException("模型安装不存在：$installationId")
        if (target.state != InstallationState.ACTIVE) {
            throw IllegalArgumentException("模型安装处于 pending-delete，不能设为当前模型：$installationId")
        }
        if (index.selectedInstallationId == installationId) return target
        val next =
            index.copy(
                selectedInstallationId = installationId,
                previousInstallationId = index.selectedInstallationId?.takeIf { it != installationId } ?: index.previousInstallationId,
            )
        indexStore.save(next)
        target
    }

    /**
     * 回滚到上一个默认安装。
     *
     * 目标必须存在且为 ACTIVE；当前默认转入回滚位，便于再次前进。
     */
    fun rollback(): InstalledModel = synchronized(lock) {
        val index = indexStore.load()
        val targetId = index.previousInstallationId
            ?: throw IllegalStateException("没有可回滚的上一个模型安装")
        val target = index.find(targetId)
            ?: throw IllegalStateException("可回滚的模型安装已不存在：$targetId")
        if (target.state != InstallationState.ACTIVE) {
            throw IllegalStateException("可回滚的模型安装处于 pending-delete：$targetId")
        }
        val next =
            index.copy(
                selectedInstallationId = targetId,
                previousInstallationId = index.selectedInstallationId,
            )
        indexStore.save(next)
        target
    }

    /**
     * 请求删除：先标 pending-delete、禁止新租约，再尝试在零引用时物理清理。
     *
     * 有租约时不会 unlink/覆盖任何权重；调用方应在租约释放后再次触发清理。
     */
    fun requestDelete(installationId: String) = synchronized(lock) {
        var index = indexStore.load()
        val installed = index.find(installationId)
            ?: throw IllegalArgumentException("模型安装不存在：$installationId")
        if (installed.state != InstallationState.PENDING_DELETE) {
            index =
                index.copy(
                    installations =
                        index.installations.map {
                            if (it.installationId == installationId) it.copy(state = InstallationState.PENDING_DELETE) else it
                        },
                )
        }
        leases.markNotAcquirable(installationId)
        if (index.selectedInstallationId == installationId) {
            index = index.copy(selectedInstallationId = newestActiveId(index.installations, exclude = installationId))
        }
        if (index.previousInstallationId == installationId) {
            index = index.copy(previousInstallationId = null)
        }
        index = normalizeSelection(index)
        indexStore.save(index)
        finalizePendingDeletesLocked()
    }

    /** 取得新会话/任务的模型租约；pending-delete 或不存在时明确失败。 */
    fun acquireLease(installationId: String, ownerId: String): ModelLease = synchronized(lock) {
        val installed = indexStore.load().find(installationId)
            ?: throw LeaseUnavailableException("模型安装不存在：$installationId")
        if (installed.state != InstallationState.ACTIVE) {
            throw LeaseUnavailableException("模型安装处于 pending-delete，不能取得新租约：$installationId")
        }
        leases.acquire(installationId, ownerId)
    }

    /** 释放租约；若该安装处于 pending-delete 且引用归零，立即清理。 */
    fun releaseLease(leaseId: String): Boolean = synchronized(lock) {
        val released = leases.release(leaseId)
        if (released) finalizePendingDeletesLocked()
        released
    }

    /** 释放某会话/任务全部租约，并尝试清理 pending-delete。 */
    fun releaseAllForOwner(ownerId: String): Int = synchronized(lock) {
        val count = leases.releaseAllForOwner(ownerId)
        if (count > 0) finalizePendingDeletesLocked()
        count
    }

    /**
     * 清理所有租约归零的 pending-delete 安装。
     *
     * @return 实际被物理删除的 installationId
     */
    fun finalizePendingDeletes(): List<String> = synchronized(lock) { finalizePendingDeletesLocked() }

    /**
     * 进程重启恢复。
     *
     * 顺序：释放不属于活跃会话/任务的租约 → 按索引重新确认 pending-delete 禁止租约 →
     * 目录缺失的 ACTIVE 安装标为 pending-delete → 修正当前/回滚选择 → 清理零引用待删除安装。
     */
    fun recover(activeOwnerIds: Set<String>): RepositoryRecoveryReport = synchronized(lock) {
        var index = indexStore.load()
        val leaseReport = leases.recover(activeOwnerIds)
        val missing = index.installations.filter { !File(it.directoryPath).isDirectory }.map { it.installationId }
        index =
            index.copy(
                installations =
                    index.installations.map {
                        if (it.installationId in missing) it.copy(state = InstallationState.PENDING_DELETE) else it
                    },
            )
        index.installations
            .filter { it.state == InstallationState.PENDING_DELETE }
            .forEach { leases.markNotAcquirable(it.installationId) }
        index = normalizeSelection(index)
        indexStore.save(index)
        val deleted = finalizePendingDeletesLocked()
        RepositoryRecoveryReport(leaseReport.released, missing, deleted)
    }

    private fun finalizePendingDeletesLocked(): List<String> {
        var index = indexStore.load()
        val deleted = mutableListOf<String>()
        for (installed in index.installations.filter { it.state == InstallationState.PENDING_DELETE }) {
            // 索引与租约分别持久化，崩溃可能造成“索引已 pending、租约标记未落盘”；这里补齐。
            leases.markNotAcquirable(installed.installationId)
            val directory = File(installed.directoryPath)
            val removed =
                leases.deleteIfUnused(installed.installationId) {
                    if (directory.exists()) {
                        val canonical = directory.canonicalFile
                        if (canonical.name != installed.installationId) {
                            throw IllegalStateException("拒绝对非安装目录执行物理删除：$canonical")
                        }
                        if (!canonical.deleteRecursively()) {
                            throw IllegalStateException("物理删除模型安装失败：$canonical")
                        }
                    }
                }
            if (removed) {
                deleted += installed.installationId
                leases.markAcquirable(installed.installationId)
            }
        }
        if (deleted.isEmpty()) return deleted
        index = index.copy(installations = index.installations.filterNot { it.installationId in deleted })
        index = normalizeSelection(index)
        indexStore.save(index)
        return deleted
    }

    /** 修正选择指向：目标必须存在且 ACTIVE，否则回退到最新的 ACTIVE 安装。 */
    private fun normalizeSelection(index: ModelIndex): ModelIndex {
        val activeIds = index.installations.filter { it.state == InstallationState.ACTIVE }.map { it.installationId }.toSet()
        val selected = index.selectedInstallationId?.takeIf { it in activeIds } ?: newestActiveId(index.installations)
        val previous = index.previousInstallationId?.takeIf { it in activeIds && it != selected }
        return index.copy(selectedInstallationId = selected, previousInstallationId = previous)
    }

    private fun newestActiveId(installations: List<InstalledModel>, exclude: String? = null): String? =
        installations
            .filter { it.state == InstallationState.ACTIVE && it.installationId != exclude }
            .maxByOrNull { it.installedAtUtcMs }
            ?.installationId
}
