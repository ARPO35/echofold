package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import java.io.IOException

/**
 * 测试夹具：内存仓储、内存文件系统、伪编码器、固定预算与容器探测器。
 *
 * 这些类型只服务于 JVM 单测，放在 test 源集，避免把 fake 混入生产代码。
 */

/**
 * 内存版块仓储：强制 `(sessionId, chunkSeq)` 与临时路径唯一，并记录调用顺序供断言。
 */
class InMemoryChunkRepository : ChunkRepository {
    private val rows = LinkedHashMap<String, AudioChunk>()

    /** 调用流水，形如 `allocatePlaceholder(s1,0)` / `update(s1-0,OPEN)`。 */
    val calls = mutableListOf<String>()

    /** 下一次 [allocatePlaceholder] 抛出的异常（一次性）。 */
    var failAllocate: Throwable? = null

    /** 下一次 [update] 抛出的异常（一次性）。 */
    var failUpdate: Throwable? = null

    override fun allocatePlaceholder(sessionId: String, chunkSeq: Int, tempPath: String, nowUtcMs: Long): AudioChunk {
        calls += "allocatePlaceholder($sessionId,$chunkSeq)"
        failAllocate?.let { failure ->
            failAllocate = null
            throw failure
        }
        check(rows.values.none { it.sessionId == sessionId && it.chunkSeq == chunkSeq }) {
            "重复的 (sessionId, chunkSeq)：($sessionId, $chunkSeq)"
        }
        check(rows.values.none { it.tempPath == tempPath }) { "重复的临时路径：$tempPath" }
        val chunk = AudioChunk(
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
        rows[chunk.id] = chunk
        return chunk
    }

    override fun update(chunk: AudioChunk) {
        calls += "update(${chunk.id},${chunk.state})"
        failUpdate?.let { failure ->
            failUpdate = null
            throw failure
        }
        check(rows.containsKey(chunk.id)) { "更新未登记的块：${chunk.id}" }
        rows[chunk.id] = chunk
    }

    override fun findActive(sessionId: String): AudioChunk? =
        rows.values
            .filter { it.sessionId == sessionId && it.state in ACTIVE_STATES }
            .maxByOrNull { it.chunkSeq }

    override fun listBySession(sessionId: String): List<AudioChunk> =
        rows.values.filter { it.sessionId == sessionId }.sortedBy { it.chunkSeq }

    fun find(id: String): AudioChunk? = rows[id]

    /** 直接写入一行（模拟重启后仓储里已有的历史块）。 */
    fun seed(chunk: AudioChunk) {
        rows[chunk.id] = chunk
    }

    private companion object {
        val ACTIVE_STATES = setOf(ChunkState.PLACEHOLDER, ChunkState.OPEN, ChunkState.SEALING)
    }
}

/**
 * 内存文件系统：只记录路径与字节数，支持注入独占创建失败与改名失败。
 */
class FakeFileSystem : ChunkFileSystem {
    private val files = LinkedHashMap<String, Long>()

    /** 调用流水，供断言协议顺序。 */
    val calls = mutableListOf<String>()

    /** `false` 表示下一次独占创建直接返回“已存在”。 */
    var createExclusiveResult: Boolean? = null

    /** 下一次原子改名抛出的异常（一次性）。 */
    var failRename: Throwable? = null

    override fun createExclusive(path: String): Boolean {
        calls += "createExclusive($path)"
        if (createExclusiveResult == false) return false
        if (files.containsKey(path)) return false
        files[path] = 0L
        return true
    }

    override fun size(path: String): Long = files[path] ?: throw IOException("文件不存在：$path")

    override fun exists(path: String): Boolean = files.containsKey(path)

    override fun renameAtomically(from: String, to: String) {
        calls += "rename($from->$to)"
        failRename?.let { failure ->
            failRename = null
            throw failure
        }
        val size = files.remove(from) ?: throw IOException("源文件不存在：$from")
        check(!files.containsKey(to)) { "目标已存在：$to" }
        files[to] = size
    }

    /** 模拟编码器向文件追加字节。 */
    fun append(path: String, bytes: ByteArray) {
        files[path] = (files[path] ?: 0L) + bytes.size
    }

    /** 直接设置文件尺寸。 */
    fun put(path: String, size: Long) {
        files[path] = size
    }

    fun remove(path: String) {
        files.remove(path)
    }

    fun paths(): List<String> = files.keys.toList()
}

/** 伪编码器：把写入累加进 [FakeFileSystem]，并可按需注入各阶段失败。 */
class FakeEncoder(
    private val tempPath: String,
    private val fileSystem: FakeFileSystem,
    private val sealResultFactory: (Long) -> SealResult = { written -> defaultSealResult(written) },
) : ChunkEncoderHandle {
    var initializeFailure: Throwable? = null
    var finishFailure: Throwable? = null
    var writeFailure: Throwable? = null

    var initialized: Boolean = false
        private set
    var finished: Boolean = false
        private set

    val writtenFrames = mutableListOf<Long>()
    private var bytesWritten = 0L

    override fun initialize() {
        initializeFailure?.let { throw it }
        initialized = true
    }

    override fun write(bytes: ByteArray, frameIndex: Long) {
        check(initialized) { "编码器未初始化，禁止写入" }
        writeFailure?.let { throw it }
        fileSystem.append(tempPath, bytes)
        bytesWritten += bytes.size
        writtenFrames += frameIndex
    }

    override fun finishAndSeal(): SealResult {
        finishFailure?.let { throw it }
        finished = true
        return sealResultFactory(bytesWritten)
    }

    companion object {
        /** 默认成功结果：合法 SHA-256 形状的校验和 + 48kHz/192kbps。 */
        fun defaultSealResult(bytesWritten: Long, durationMs: Long = 1_000L): SealResult =
            SealResult(
                bytesWritten = bytesWritten,
                checksum = "a".repeat(64),
                durationMs = durationMs,
                codec = "aac-lc",
                sampleRate = 48_000,
                bitRate = 192_000,
            )
    }
}

/** 固定容量预算：`reserve` 可失败，`release` 归还额度，并记录调用顺序。 */
class FixedSpaceBudget(private val capacity: Long) : SpaceBudget {
    var used: Long = 0L
        private set

    val calls = mutableListOf<String>()

    override fun reserve(bytes: Long): Boolean {
        calls += "reserve($bytes)"
        if (used + bytes > capacity) return false
        used += bytes
        return true
    }

    override fun release(bytes: Long) {
        calls += "release($bytes)"
        used = (used - bytes).coerceAtLeast(0L)
    }
}

/** 伪容器探测器：按路径返回预置报告，未登记路径视为不可解析。 */
class FakeProbe(private val reports: Map<String, ProbeReport> = emptyMap()) : ChunkContainerProbe {
    override fun probe(path: String): ProbeReport =
        reports[path] ?: ProbeReport.Unreadable("未登记探测结果：$path")
}

/** 组装测试用 [AudioChunk]，默认是一个 `OPEN` 块。 */
fun testChunk(
    id: String = "s1-0",
    sessionId: String = "s1",
    chunkSeq: Int = 0,
    state: ChunkState = ChunkState.OPEN,
    tempPath: String = ChunkPaths.tempPath(sessionId, chunkSeq),
    finalPath: String? = null,
    createdAtUtcMs: Long = 1_000L,
    sealedAtUtcMs: Long? = null,
    capturedStartMs: Long? = null,
    capturedEndMs: Long? = null,
    durationMs: Long? = null,
    checksum: String? = null,
    failureStage: String? = null,
    resourceEpoch: Long = 7L,
): AudioChunk = AudioChunk(
    id = id,
    sessionId = sessionId,
    chunkSeq = chunkSeq,
    state = state,
    tempPath = tempPath,
    finalPath = finalPath,
    createdAtUtcMs = createdAtUtcMs,
    sealedAtUtcMs = sealedAtUtcMs,
    captureStartedMonoNs = null,
    captureEndedMonoNs = null,
    capturedStartMs = capturedStartMs,
    capturedEndMs = capturedEndMs,
    codec = null,
    sampleRate = null,
    bitRate = null,
    durationMs = durationMs,
    checksum = checksum,
    failureStage = failureStage,
    resourceEpoch = resourceEpoch,
)
