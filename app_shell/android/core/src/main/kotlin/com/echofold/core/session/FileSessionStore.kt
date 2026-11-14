package com.echofold.core.session

import com.echofold.core.model.ClockAnchor
import com.echofold.core.model.ClockQuality
import com.echofold.core.model.DispatchState
import com.echofold.core.model.RecordingSession
import com.echofold.core.model.SessionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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
 * 文件版会话仓储：`session.create` 持久化会话身份与固定模型，重启后可继续对账。
 *
 * 会话 ID 由 Dart 侧生成；同一 ID 的重复创建是幂等返回既有记录，不会改写已固定的模型身份。
 * 写入采用“临时文件 + fsync + 原子改名”，避免读到半个 JSON。
 */
class FileSessionStore(
    private val file: File,
) {
    private val lock = Any()
    private var sessions: List<RecordingSession> = loadInitial()

    /** 全部会话，按创建时间升序。 */
    fun list(): List<RecordingSession> = synchronized(lock) { sessions.sortedBy { it.createdAtUtcMs } }

    /** 按 ID 查找。 */
    fun find(sessionId: String): RecordingSession? = synchronized(lock) {
        sessions.firstOrNull { it.id == sessionId }
    }

    /**
     * 创建会话；同一 ID 重试返回既有记录。
     *
     * @throws IllegalStateException 同一 ID 已存在但固定模型/锚点不同
     */
    fun create(session: RecordingSession): RecordingSession = synchronized(lock) {
        sessions.firstOrNull { it.id == session.id }?.let { existing ->
            if (!existing.sameIdentity(session)) {
                throw IllegalStateException("会话 ${session.id} 已存在且固定模型/锚点不同，不能覆盖")
            }
            return existing
        }
        persistLocked(sessions + session)
        session
    }

    /** 整体更新已存在会话；不存在时抛异常。 */
    fun update(session: RecordingSession): Unit = synchronized(lock) {
        val index = sessions.indexOfFirst { it.id == session.id }
        require(index >= 0) { "会话不存在，不能更新：${session.id}" }
        if (sessions[index] == session) return
        persistLocked(sessions.toMutableList().also { it[index] = session })
    }

    /** 更新状态与分发标记，保留其余身份字段。 */
    fun updateState(sessionId: String, status: SessionStatus, dispatchState: DispatchState): RecordingSession =
        synchronized(lock) {
            val index = sessions.indexOfFirst { it.id == sessionId }
            require(index >= 0) { "会话不存在，不能更新状态：$sessionId" }
            val updated = sessions[index].copy(status = status, dispatchState = dispatchState)
            if (updated == sessions[index]) return sessions[index]
            persistLocked(sessions.toMutableList().also { it[index] = updated })
            updated
        }

    /** 更新当前活动块序号；`activeChunkSeq` 只增不减。 */
    fun updateActiveChunk(sessionId: String, chunkSeq: Int): RecordingSession = synchronized(lock) {
        require(chunkSeq >= 0) { "chunkSeq 不能为负数：$chunkSeq" }
        val index = sessions.indexOfFirst { it.id == sessionId }
        require(index >= 0) { "会话不存在，不能更新活动块：$sessionId" }
        val current = sessions[index]
        if (current.activeChunkSeq == chunkSeq) return current
        val updated = current.copy(activeChunkSeq = maxOf(current.activeChunkSeq, chunkSeq))
        persistLocked(sessions.toMutableList().also { it[index] = updated })
        updated
    }

    /** 仍未结束的会话（录音/暂停/停止中），用于租约恢复。 */
    fun activeSessionIds(): Set<String> = synchronized(lock) {
        sessions
            .filter { it.status == SessionStatus.RECORDING || it.status == SessionStatus.PAUSED || it.status == SessionStatus.STOPPING }
            .map { it.id }
            .toSet()
    }

    private fun RecordingSession.sameIdentity(other: RecordingSession): Boolean =
        modelId == other.modelId &&
            modelVersion == other.modelVersion &&
            installationId == other.installationId &&
            asrConfigHash == other.asrConfigHash &&
            clockAnchor == other.clockAnchor

    private fun loadInitial(): List<RecordingSession> {
        if (!file.isFile) return emptyList()
        val text =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                throw IllegalStateException("读取会话仓储失败：$file", e)
            }
        if (text.isBlank()) return emptyList()
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalStateException("会话仓储不是合法 JSON：$file", e)
            }
        val schema = (root["schema"] as? JsonPrimitive)?.content
        if (schema != SCHEMA) throw IllegalStateException("会话仓储 schema 不支持：$schema")
        val decoded = root["sessions"]?.jsonArray?.map { decodeSession(it.jsonObject) } ?: emptyList()
        require(decoded.map { it.id }.distinct().size == decoded.size) { "会话仓储存在重复 ID" }
        return decoded
    }

    private fun persistLocked(next: List<RecordingSession>) {
        require(next.map { it.id }.distinct().size == next.size) { "会话仓储存在重复 ID" }
        val payload =
            buildJsonObject {
                put("schema", JsonPrimitive(SCHEMA))
                put("sessions", JsonArray(next.map { encodeSession(it) }))
            }.toString()
        val parent = file.absoluteFile.parentFile ?: throw IOException("无法确定会话仓储父目录：$file")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("无法创建会话仓储目录：$parent")
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
        sessions = next
    }

    private fun encodeSession(session: RecordingSession): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(session.id))
            put("status", JsonPrimitive(session.status.name))
            put("dispatchState", JsonPrimitive(session.dispatchState.name))
            put("createdAtUtcMs", JsonPrimitive(session.createdAtUtcMs))
            putNullable("modelId", session.modelId)
            putNullable("modelVersion", session.modelVersion)
            putNullable("installationId", session.installationId)
            putNullable("asrConfigHash", session.asrConfigHash)
            put("activeChunkSeq", JsonPrimitive(session.activeChunkSeq))
            put(
                "clockAnchor",
                buildJsonObject {
                    put("utcMs", JsonPrimitive(session.clockAnchor.utcMs))
                    put("monoNs", JsonPrimitive(session.clockAnchor.monoNs))
                    put("quality", JsonPrimitive(session.clockAnchor.quality.name))
                },
            )
        }

    private fun decodeSession(obj: JsonObject): RecordingSession {
        val anchor = obj["clockAnchor"]?.jsonObject ?: throw IllegalStateException("会话缺少 clockAnchor")
        val id = obj.requireString("id")
        return RecordingSession(
            id = id,
            status = enumValue(obj.requireString("status"), SessionStatus.entries, "status"),
            dispatchState = enumValue(obj.requireString("dispatchState"), DispatchState.entries, "dispatchState"),
            createdAtUtcMs = obj.requireLong("createdAtUtcMs"),
            modelId = obj.optionalString("modelId"),
            modelVersion = obj.optionalString("modelVersion"),
            installationId = obj.optionalString("installationId"),
            asrConfigHash = obj.optionalString("asrConfigHash"),
            activeChunkSeq = obj.requireInt("activeChunkSeq"),
            clockAnchor = ClockAnchor(
                sessionId = id,
                utcMs = anchor.requireLong("utcMs"),
                monoNs = anchor.requireLong("monoNs"),
                quality = enumValue(anchor.requireString("quality"), ClockQuality.entries, "clockAnchor.quality"),
            ),
        )
    }

    private fun <T : Enum<T>> enumValue(raw: String, values: List<T>, field: String): T =
        values.firstOrNull { it.name == raw } ?: throw IllegalStateException("会话仓储 $field 非法：$raw")

    private fun JsonObject.requireString(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: throw IllegalStateException("会话仓储缺少 $key")
        if (!primitive.isString) throw IllegalStateException("会话仓储 $key 必须是字符串")
        return primitive.content
    }

    private fun JsonObject.optionalString(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("会话仓储 $key 必须是字符串")
        if (!primitive.isString) throw IllegalStateException("会话仓储 $key 必须是字符串")
        return primitive.content
    }

    private fun JsonObject.requireLong(key: String): Long =
        optionalLong(key) ?: throw IllegalStateException("会话仓储缺少 $key")

    private fun JsonObject.requireInt(key: String): Int {
        val value = requireLong(key)
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) throw IllegalStateException("会话仓储 $key 超出 Int 范围：$value")
        return value.toInt()
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: throw IllegalStateException("会话仓储 $key 必须是数字")
        if (!primitive.isString) {
            return primitive.longOrNull ?: throw IllegalStateException("会话仓储 $key 必须是整数")
        }
        return primitive.content.toLongOrNull() ?: throw IllegalStateException("会话仓储 $key 必须是整数")
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
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
        const val SCHEMA = "echofold.sessions.v1"
    }
}
