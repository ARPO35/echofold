package com.echofold.capture.encoder

import com.echofold.capture.pcm.LifecycleEncoderSink
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.chunk.*
import com.echofold.core.model.AudioChunk
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 生命周期操作与 accept 均由管线串行调用；只封存实际接受的 PCM，不含暂停间隙。 */
class RecordingChunkSink(
    private val sessionId: String,
    private val root: File,
    private val repository: ChunkRepository,
    private val encoderFactory: CaptureChunkEncoderFactory,
    private val onChunk: (AudioChunk) -> Unit,
    private val availableBytes: () -> Long = { root.usableSpace },
    private val budget: SpaceBudget = RecordingSpaceBudget(File(root, "reserves/$sessionId.reserve")),
) : LifecycleEncoderSink {
    private val files = RecordingChunkFiles(root)
    private val gate = ChunkDispatchGate()
    private val placeholders = PlaceholderProtocol(repository, files, gate)
    private var reserved = false
    private var sequence = repository.listBySession(sessionId).maxOfOrNull { it.chunkSeq }?.plus(1) ?: 0
    private var sampleRate = 0
    private var encoder: CaptureChunkEncoder? = null
    private var sealing: SealingPipeline? = null
    private var firstMonoNs: Long? = null
    private var endMonoNs = 0L
    private var startMs = 0L
    private var chunkFrames = 0L
    private var totalFrames = 0L
    private var failure: Exception? = null
    override val capturedMs: Long get() = if (sampleRate == 0) 0 else totalFrames * 1000L / sampleRate
    override val framesUntilBoundary: Long get() = sampleRate * 60L - chunkFrames
    override fun prepareNextChunk() { if (sealing == null) openChunk() }

    override fun start(sampleRate: Int) {
        this.sampleRate = sampleRate
        openChunk()
    }

    override fun resume() {
        failure?.let { throw terminalFailure(it) }
        openChunk()
    }

    override fun pause() = seal()
    override fun stop() = seal()

    override fun accept(frame: PcmFrame) {
        failure?.let { throw terminalFailure(it) }
        check(frame.sampleRate == sampleRate) { "采集采样率变化，拒绝错误速率编码" }
        var offset = frame.offset
        var remaining = frame.length
        try {
            while (remaining > 0) {
                if (sealing == null) openChunk()
                val take = minOf(remaining.toLong(), sampleRate * 60L - chunkFrames).toInt()
                val active = checkNotNull(encoder)
                if (firstMonoNs == null) {
                    firstMonoNs = frame.monoNs + (offset - frame.offset) * 1_000_000_000L / sampleRate
                    startMs = capturedMs
                }
                when (val result = active.write(frame.samples, offset, take)) {
                    EncoderWriteResult.Written -> Unit
                    is EncoderWriteResult.Failed -> throw RecordingStorageException("ENCODE_FAILED", result.failure.message, result.failure.cause)
                }
                totalFrames += take
                chunkFrames += take
                offset += take
                remaining -= take
                endMonoNs = frame.monoNs + (offset - frame.offset) * 1_000_000_000L / sampleRate
                if (chunkFrames == sampleRate * 60L) seal()
            }
        } catch (error: Exception) {
            failure = error
            encoder?.abort()
            throw error
        }
    }

    fun seal() {
        failure?.let { throw terminalFailure(it) }
        val pipeline = sealing ?: return
        try {
            if (reserved) { budget.release(SEAL_RESERVE_BYTES); reserved = false }
            val timed = pipeline.snapshot.copy(
                captureStartedMonoNs = firstMonoNs,
                captureEndedMonoNs = if (firstMonoNs == null) null else endMonoNs,
                capturedStartMs = startMs,
                capturedEndMs = capturedMs,
            )
            repository.update(timed)
            val outcome = SealingPipeline(timed, repository, files, checkNotNull(handle)).seal()
            when (outcome) {
                is SealOutcome.Sealed -> {
                    sealing = null
                    encoder = null
                    handle = null
                    onChunk(outcome.chunk)
                }
                is SealOutcome.Failed -> throw RecordingStorageException("SEAL_FAILED", "音频封存失败（${outcome.stage}），已保留原文件", outcome.cause)
            }
        } catch (error: Exception) {
            failure = error
            encoder?.abort()
            throw error
        }
    }

    private var handle: ChunkEncoderHandle? = null

    private fun openChunk() {
        check(sealing == null) { "已有活动音频块" }
        if (availableBytes() < MIN_FREE_BYTES) throw RecordingStorageException("STORAGE_LOW", "存储空间不足，请释放空间后重试")
        if (!budget.reserve(SEAL_RESERVE_BYTES)) throw RecordingStorageException("STORAGE_LOW", "无法预留音频封存空间，请释放存储后重试")
        reserved = true
        val seq = sequence++
        chunkFrames = 0
        firstMonoNs = null
        startMs = capturedMs
        val path = ChunkPaths.tempPath(sessionId, seq)
        val outcome = placeholders.open(sessionId, seq, path, seq.toLong() + 1) { chunk ->
            val created = encoderFactory.create()
            encoder = created
            object : ChunkEncoderHandle {
                override fun initialize() {
                    when (val result = created.open(EncoderTarget(sampleRate), files.resolve(chunk.tempPath).absolutePath)) {
                        is EncoderOpenResult.Failed -> throw RecordingStorageException("PLACEHOLDER_FAILED", result.failure.message, result.failure.cause)
                        is EncoderOpenResult.Opened -> if (result.capability.sampleRate != sampleRate || result.capability.channelCount != 1) {
                            created.abort()
                            throw RecordingStorageException("PIPELINE_UNAVAILABLE", "编码器不支持实际麦克风采样率或单声道")
                        }
                    }
                }
                override fun write(bytes: ByteArray, frameIndex: Long) {
                    error("录音管线仅接受 PCM16 样本视图")
                }
                override fun finishAndSeal(): SealResult {
                    return when (val result = created.closeAndSeal()) {
                        is EncoderSealResult.Failed -> throw RecordingStorageException("SEAL_FAILED", result.failure.message, result.failure.cause)
                        is EncoderSealResult.Sealed -> {
                            RandomAccessFile(files.resolve(chunk.tempPath), "rw").use { it.fd.sync() }
                            SealResult(result.sizeBytes, result.sha256, result.durationMs,
                                "AAC-LC/${result.capability.codecName}", result.capability.sampleRate, result.capability.bitRate)
                        }
                    }
                }
            }.also { handle = it }
        }
        when (outcome) {
            is PlaceholderOutcome.Open -> {
                sealing = SealingPipeline(outcome.chunk, repository, files, outcome.encoder)
                try { onChunk(outcome.chunk) } catch (error: Exception) { encoder?.abort(); throw error }
            }
            is PlaceholderOutcome.Failed -> {
                if (reserved) { budget.release(SEAL_RESERVE_BYTES); reserved = false }
                encoder?.abort()
                throw RecordingStorageException("PLACEHOLDER_FAILED", "创建音频块失败（${outcome.stage}），请检查存储", outcome.cause)
            }
        }
    }

    private fun terminalFailure(error: Exception) = RecordingStorageException(
        "RECORDING_RECOVERY_REQUIRED", "该音频块已发生不可重放的编码或封存失败，不能重新结束编码；原文件已保留，需要恢复检查：${error.message}", error)

    companion object {
        const val MIN_FREE_BYTES = 16L * 1024 * 1024
        const val SEAL_RESERVE_BYTES = 4L * 1024 * 1024
    }
}

class RecordingStorageException(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause)

/** 所有音频路径都在应用私有目录，独占创建且最终文件禁止覆盖。 */
class RecordingChunkFiles(private val root: File) : ChunkFileSystem {
    fun resolve(path: String): File = File(root, path).also {
        require(it.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "非法音频路径" }
    }
    override fun createExclusive(path: String): Boolean {
        val file = resolve(path)
        val parent = checkNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "无法创建录音目录" }
        return file.createNewFile()
    }
    override fun exists(path: String): Boolean = resolve(path).isFile
    override fun size(path: String): Long = resolve(path).let { check(it.isFile); it.length() }
    override fun renameAtomically(from: String, to: String) {
        check(!resolve(to).exists()) { "最终文件已存在，禁止覆盖" }
        Files.move(resolve(from).toPath(), resolve(to).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
