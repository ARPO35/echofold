package com.echofold.asr.repository

import com.echofold.asr.internal.AtomicFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.util.UUID

/** 一份模型租约：某个会话/任务在 [installationId] 上的引用计数。 */
data class ModelLease(
    val leaseId: String,
    val installationId: String,
    val ownerId: String,
    val acquiredAtUtcMs: Long,
) {
    init {
        require(leaseId.isNotBlank()) { "leaseId 不能为空白字符串" }
        require(installationId.isNotBlank()) { "installationId 不能为空白字符串" }
        require(ownerId.isNotBlank()) { "ownerId 不能为空白字符串" }
        require(acquiredAtUtcMs >= 0L) { "acquiredAtUtcMs 不能为负数：$acquiredAtUtcMs" }
    }
}

/** 租约持久化状态：租约列表 + 禁止新租约的安装集合（pending-delete）。 */
data class LeaseState(
    val leases: List<ModelLease> = emptyList(),
    val notAcquirable: Set<String> = emptySet(),
)

/** 租约持久化抽象；实现必须原子写入。 */
interface LeaseStore {
    fun load(): LeaseState

    fun save(state: LeaseState)
}

/** 申请租约失败（安装不存在、处于 pending-delete 或持久化失败）。 */
class LeaseUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 租约 JSON 编解码。 */
internal object LeaseStateJson {
    private const val SCHEMA = "echofold.model-leases.v1"

    fun encode(state: LeaseState): String =
        buildJsonObject {
            put("schema", JsonPrimitive(SCHEMA))
            put(
                "leases",
                JsonArray(
                    state.leases.map { lease ->
                        buildJsonObject {
                            put("leaseId", JsonPrimitive(lease.leaseId))
                            put("installationId", JsonPrimitive(lease.installationId))
                            put("ownerId", JsonPrimitive(lease.ownerId))
                            put("acquiredAtUtcMs", JsonPrimitive(lease.acquiredAtUtcMs))
                        }
                    },
                ),
            )
            put("notAcquirable", JsonArray(state.notAcquirable.sorted().map { JsonPrimitive(it) }))
        }.toString()

    fun decode(text: String): LeaseState {
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalStateException("租约文件不是合法 JSON", e)
            }
        val leases =
            root["leases"]?.jsonArray?.map { element ->
                val obj = element.jsonObject
                ModelLease(
                    leaseId = stringValue(obj["leaseId"], "leaseId"),
                    installationId = stringValue(obj["installationId"], "installationId"),
                    ownerId = stringValue(obj["ownerId"], "ownerId"),
                    acquiredAtUtcMs = (obj["acquiredAtUtcMs"] as? JsonPrimitive)?.longOrNull
                        ?: throw IllegalStateException("租约缺少 acquiredAtUtcMs"),
                )
            } ?: emptyList()
        val notAcquirable =
            root["notAcquirable"]?.jsonArray?.mapTo(LinkedHashSet()) { element ->
                val primitive = element as? JsonPrimitive ?: throw IllegalStateException("notAcquirable 条目必须是字符串")
                if (!primitive.isString) throw IllegalStateException("notAcquirable 条目必须是字符串")
                primitive.content
            } ?: emptySet()
        return LeaseState(leases, notAcquirable)
    }

    private fun stringValue(element: kotlinx.serialization.json.JsonElement?, field: String): String {
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("租约缺少 $field")
        if (!primitive.isString) throw IllegalStateException("租约 $field 必须是字符串")
        return primitive.content
    }
}

/** 文件版租约存储。 */
class FileLeaseStore(
    private val file: File,
) : LeaseStore {
    private val lock = Any()

    override fun load(): LeaseState = synchronized(lock) {
        if (!file.isFile) return LeaseState()
        val text =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                throw IllegalStateException("读取租约文件失败：$file", e)
            }
        LeaseStateJson.decode(text)
    }

    override fun save(state: LeaseState) = synchronized(lock) {
        try {
            AtomicFiles.writeText(file, LeaseStateJson.encode(state))
        } catch (e: IOException) {
            throw IllegalStateException("写入租约文件失败：$file", e)
        }
    }
}

/** 重启恢复结果。 */
data class LeaseRecoveryReport(
    /** 因所属会话/任务已不存在而释放的租约。 */
    val released: List<ModelLease>,
    /** 所属会话/任务仍活跃而保留的租约。 */
    val retained: List<ModelLease>,
)

/**
 * 模型租约管理器。
 *
 * 所有变更都在同一把锁内完成并立即持久化，保证“引用计数为 0 且没有进行中的获取事务”这一删除
 * 前提不被并发申请绕过：[deleteIfUnused] 与 [acquire] 互斥，物理删除在锁内执行。
 */
class ModelLeaseManager(
    private val store: LeaseStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val leaseIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private var state: LeaseState = loadInitialState()

    /** 当前全部租约。 */
    fun leases(): List<ModelLease> = synchronized(lock) { state.leases.toList() }

    /** 某安装的引用计数。 */
    fun refCount(installationId: String): Int = synchronized(lock) { state.leases.count { it.installationId == installationId } }

    /** 某安装的租约明细。 */
    fun leasesFor(installationId: String): List<ModelLease> =
        synchronized(lock) { state.leases.filter { it.installationId == installationId } }

    /** 该安装是否已禁止新租约（pending-delete）。 */
    fun isAcquirable(installationId: String): Boolean = synchronized(lock) { installationId !in state.notAcquirable }

    /**
     * 申请租约。
     *
     * @throws LeaseUnavailableException 安装已标记 pending-delete
     */
    fun acquire(installationId: String, ownerId: String): ModelLease = synchronized(lock) {
        require(installationId.isNotBlank()) { "installationId 不能为空白字符串" }
        require(ownerId.isNotBlank()) { "ownerId 不能为空白字符串" }
        if (installationId in state.notAcquirable) {
            throw LeaseUnavailableException("模型安装处于 pending-delete，不能取得新租约：$installationId")
        }
        val lease = ModelLease(leaseIdFactory(), installationId, ownerId, clock())
        val next = state.copy(leases = state.leases + lease)
        persist(next)
        state = next
        lease
    }

    /** 释放租约；重复释放返回 `false`。 */
    fun release(leaseId: String): Boolean = synchronized(lock) {
        val nextLeases = state.leases.filterNot { it.leaseId == leaseId }
        if (nextLeases.size == state.leases.size) return false
        val next = state.copy(leases = nextLeases)
        persist(next)
        state = next
        true
    }

    /** 释放某会话/任务持有的全部租约，返回释放数量。 */
    fun releaseAllForOwner(ownerId: String): Int {
        return synchronized(lock) {
            val retained = state.leases.filterNot { it.ownerId == ownerId }
            val releasedCount = state.leases.size - retained.size
            if (releasedCount == 0) {
                0
            } else {
                val next = state.copy(leases = retained)
                persist(next)
                state = next
                releasedCount
            }
        }
    }

    /** 标记安装不可再取得租约；调用方随后负责物理删除或清理。 */
    fun markNotAcquirable(installationId: String) {
        synchronized(lock) {
            if (installationId in state.notAcquirable) return
            val next = state.copy(notAcquirable = state.notAcquirable + installationId)
            persist(next)
            state = next
        }
    }

    /** 安装已彻底移除后撤销不可获取标记。 */
    fun markAcquirable(installationId: String) {
        synchronized(lock) {
            if (installationId !in state.notAcquirable) return
            val next = state.copy(notAcquirable = state.notAcquirable - installationId)
            persist(next)
            state = next
        }
    }

    /**
     * 重启恢复：释放所属会话/任务已不存在的租约，保留活跃持有者。
     *
     * `notAcquirable` 标记原样保留，由仓库按索引中的 pending-delete 状态重新确认。
     */
    fun recover(activeOwnerIds: Set<String>): LeaseRecoveryReport = synchronized(lock) {
        val retained = state.leases.filter { it.ownerId in activeOwnerIds }
        val released = state.leases.filterNot { it.ownerId in activeOwnerIds }
        if (released.isNotEmpty()) {
            val next = state.copy(leases = retained)
            persist(next)
            state = next
        }
        LeaseRecoveryReport(released, retained)
    }

    /**
     * 引用计数为 0 时在锁内执行物理删除。
     *
     * @return `true` 表示删除执行完成；`false` 表示仍有租约，未删除
     * @throws IllegalArgumentException 安装未标记为不可获取（防止误删仍可租用的安装）
     */
    fun deleteIfUnused(installationId: String, deleter: () -> Unit): Boolean = synchronized(lock) {
        require(installationId in state.notAcquirable) { "物理删除前必须先将安装标记为不可获取：$installationId" }
        if (state.leases.any { it.installationId == installationId }) return false
        deleter()
        true
    }

    private fun loadInitialState(): LeaseState {
        val loaded = store.load()
        require(loaded.leases.map { it.leaseId }.distinct().size == loaded.leases.size) { "租约文件存在重复 leaseId" }
        return loaded
    }

    private fun persist(next: LeaseState) {
        try {
            store.save(next)
        } catch (e: IllegalStateException) {
            throw LeaseUnavailableException("租约持久化失败：${e.message}", e)
        }
    }
}
