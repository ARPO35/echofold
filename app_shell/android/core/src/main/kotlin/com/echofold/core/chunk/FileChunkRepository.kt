package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 文件版块仓储：单文件 JSON + 临时文件改名，满足 [ChunkRepository] 的原子占位约束。
 *
 * 事务模型：所有写操作在同一把锁内完成“构造新快照 → fsync 临时文件 → 原子改名 → 更新内存快照”。
 * 任一步失败时内存与磁盘都保持旧值，调用方按占位协议保持 `BLOCKED`，不会出现“内存已占位、磁盘未落盘”。
 *
 * 唯一性约束由本类在事务内强制：
 * - `(sessionId, chunkSeq)` 全局唯一；
 * - 临时路径全局唯一；
 * - 同一会话最多一个 `PLACEHOLDER/OPEN/SEALING` 块。
 *
 * 本类不删除任何文件：块文件的生命周期由封存流水线与恢复流程负责。
 */
class FileChunkRepository(
    private val file: File,
) : ChunkRepository {
    private val lock = Any()
    private var rows: List<AudioChunk> = loadInitial()

    /** 全部块（按会话与序号排序），供恢复扫描与诊断使用。 */
    fun listAll(): List<AudioChunk> = synchronized(lock) {
        rows.sortedWith(compareBy({ it.sessionId }, { it.chunkSeq }))
    }

    override fun allocatePlaceholder(
        sessionId: String,
        chunkSeq: Int,
        tempPath: String,
        nowUtcMs: Long,
    ): AudioChunk = synchronized(lock) {
        require(sessionId.isNotBlank()) { "sessionId 不能为空白字符串" }
        require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
        if (rows.any { it.sessionId == sessionId && it.chunkSeq == chunkSeq }) {
            throw IllegalStateException("块占位已存在：session=$sessionId seq=$chunkSeq")
        }
        if (rows.any { it.tempPath == tempPath }) {
            throw IllegalStateException("临时路径已被占用：$tempPath")
        }
        findActiveLocked(sessionId)?.let {
            throw IllegalStateException("会话已有活动块：${it.id}（${it.state}）")
        }
        val placeholder = AudioChunk(
            id = "$sessionId-$chunkSeq",
            sessionId = sessionId,
            chunkSeq = chunkSeq,
            state = ChunkState.PLACEHOLDER,
            tempPath = tempPath,
            finalPath = null,
            createdAtUtcMs = nowUtcMs,
            sealedAtUtcMs = null,
            captureStartedMonoNs = null,
            captureEndedMonoNs = null,
            capturedStartMs = null,
            capturedEndMs = null,
            codec = null,
            sampleRate = null,
            bitRate = null,
            durationMs = null,
            checksum = null,
            failureStage = null,
            resourceEpoch = 0L,
        )
        persistLocked(rows + placeholder)
        placeholder
    }

    override fun update(chunk: AudioChunk): Unit = synchronized(lock) {
        val index = rows.indexOfFirst { it.id == chunk.id }
        require(index >= 0) { "块不存在，不能更新：${chunk.id}" }
        if (rows[index] == chunk) return
        val updated = rows.toMutableList().also { it[index] = chunk }
        val active = updated.filter { it.sessionId == chunk.sessionId && it.isActive() }
        if (active.size > 1) {
            throw IllegalStateException(
                "会话 ${chunk.sessionId} 同时存在多个活动块：${active.map { "${it.chunkSeq}:${it.state}" }}",
            )
        }
        persistLocked(updated)
    }

    override fun findActive(sessionId: String): AudioChunk? = synchronized(lock) { findActiveLocked(sessionId) }

    override fun listBySession(sessionId: String): List<AudioChunk> = synchronized(lock) {
        rows.filter { it.sessionId == sessionId }.sortedBy { it.chunkSeq }
    }

    private fun findActiveLocked(sessionId: String): AudioChunk? =
        rows.firstOrNull { it.sessionId == sessionId && it.isActive() }

    private fun AudioChunk.isActive(): Boolean =
        state == ChunkState.PLACEHOLDER || state == ChunkState.OPEN || state == ChunkState.SEALING

    private fun loadInitial(): List<AudioChunk> {
        if (!file.isFile) return emptyList()
        val text =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                throw IllegalStateException("读取块仓储失败：$file", e)
            }
        if (text.isBlank()) return emptyList()
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                // 损坏的仓储必须 fail closed：把空列表当成“没有块”会静默复用序号与路径。
                throw IllegalStateException("块仓储不是合法 JSON：$file", e)
            }
        val schema = (root["schema"] as? JsonPrimitive)?.content
        if (schema != SCHEMA) throw IllegalStateException("块仓储 schema 不支持：$schema")
        val decoded =
            root["chunks"]?.jsonArray?.map { decodeChunk(it.jsonObject) } ?: emptyList()
        requireUnique(decoded)
        return decoded
    }

    private fun persistLocked(next: List<AudioChunk>) {
        requireUnique(next)
        val payload =
            buildJsonObject {
                put("schema", JsonPrimitive(SCHEMA))
                put("chunks", JsonArray(next.map { encodeChunk(it) }))
            }.toString()
        val parent = file.absoluteFile.parentFile
            ?: throw IOException("无法确定块仓储父目录：$file")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("无法创建块仓储目录：$parent")
        }
        val temp = File(parent, "${file.name}.tmp-${System.nanoTime()}")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            moveAtomically(temp, file)
        } finally {
            if (temp.exists()) temp.delete()
        }
        rows = next
    }

    private fun requireUnique(chunks: List<AudioChunk>) {
        val identities = chunks.map { it.sessionId to it.chunkSeq }
        require(identities.distinct().size == identities.size) { "块仓储存在重复 (sessionId, chunkSeq)" }
        require(chunks.map { it.id }.distinct().size == chunks.size) { "块仓储存在重复 id" }
        require(chunks.map { it.tempPath }.distinct().size == chunks.size) { "块仓储存在重复临时路径" }
    }

    private fun encodeChunk(chunk: AudioChunk): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(chunk.id))
            put("sessionId", JsonPrimitive(chunk.sessionId))
            put("chunkSeq", JsonPrimitive(chunk.chunkSeq))
            put("state", JsonPrimitive(chunk.state.name))
            put("tempPath", JsonPrimitive(chunk.tempPath))
            putNullable("finalPath", chunk.finalPath)
            put("createdAtUtcMs", JsonPrimitive(chunk.createdAtUtcMs))
            putNullable("sealedAtUtcMs", chunk.sealedAtUtcMs)
            putNullable("captureStartedMonoNs", chunk.captureStartedMonoNs)
            putNullable("captureEndedMonoNs", chunk.captureEndedMonoNs)
            putNullable("capturedStartMs", chunk.capturedStartMs)
            putNullable("capturedEndMs", chunk.capturedEndMs)
            putNullable("codec", chunk.codec)
            putNullable("sampleRate", chunk.sampleRate)
            putNullable("bitRate", chunk.bitRate)
            putNullable("durationMs", chunk.durationMs)
            putNullable("checksum", chunk.checksum)
            putNullable("failureStage", chunk.failureStage)
            put("resourceEpoch", JsonPrimitive(chunk.resourceEpoch))
        }

    private fun decodeChunk(obj: JsonObject): AudioChunk =
        AudioChunk(
            id = obj.requireString("id"),
            sessionId = obj.requireString("sessionId"),
            chunkSeq = obj.requireInt("chunkSeq"),
            state =
                ChunkState.entries.firstOrNull { it.name == obj.requireString("state") }
                    ?: throw IllegalStateException("块状态非法：${obj.requireString("state")}"),
            tempPath = obj.requireString("tempPath"),
            finalPath = obj.optionalString("finalPath"),
            createdAtUtcMs = obj.requireLong("createdAtUtcMs"),
            sealedAtUtcMs = obj.optionalLong("sealedAtUtcMs"),
            captureStartedMonoNs = obj.optionalLong("captureStartedMonoNs"),
            captureEndedMonoNs = obj.optionalLong("captureEndedMonoNs"),
            capturedStartMs = obj.optionalLong("capturedStartMs"),
            capturedEndMs = obj.optionalLong("capturedEndMs"),
            codec = obj.optionalString("codec"),
            sampleRate = obj.optionalInt("sampleRate"),
            bitRate = obj.optionalInt("bitRate"),
            durationMs = obj.optionalLong("durationMs"),
            checksum = obj.optionalString("checksum"),
            failureStage = obj.optionalString("failureStage"),
            resourceEpoch = obj.requireLong("resourceEpoch"),
        )

    private fun JsonObject.requireString(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: throw IllegalStateException("块仓储缺少 $key")
        if (!primitive.isString) throw IllegalStateException("块仓储 $key 必须是字符串")
        return primitive.content
    }

    private fun JsonObject.optionalString(key: String): String? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("块仓储 $key 必须是字符串")
        if (!primitive.isString) throw IllegalStateException("块仓储 $key 必须是字符串")
        return primitive.content
    }

    private fun JsonObject.requireLong(key: String): Long =
        optionalLong(key) ?: throw IllegalStateException("块仓储缺少 $key")

    private fun JsonObject.requireInt(key: String): Int {
        val value = requireLong(key)
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) throw IllegalStateException("块仓储 $key 超出 Int 范围：$value")
        return value.toInt()
    }

    private fun JsonObject.optionalInt(key: String): Int? {
        val value = optionalLong(key) ?: return null
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) throw IllegalStateException("块仓储 $key 超出 Int 范围：$value")
        return value.toInt()
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("块仓储 $key 必须是数字")
        if (!primitive.isString) {
            return primitive.longOrNull ?: throw IllegalStateException("块仓储 $key 必须是整数")
        }
        return primitive.content.toLongOrNull() ?: throw IllegalStateException("块仓储 $key 必须是整数")
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Any?) {
        if (value == null) {
            put(key, JsonNull)
        } else if (value is Long) {
            put(key, JsonPrimitive(value))
        } else if (value is Int) {
            put(key, JsonPrimitive(value))
        } else {
            put(key, JsonPrimitive(value.toString()))
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val SCHEMA = "echofold.audio-chunks.v1"
    }
}
