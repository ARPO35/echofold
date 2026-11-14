package com.echofold.capture.encoder

import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.chunk.FileChunkRepository
import com.echofold.core.chunk.SpaceBudget
import com.echofold.core.model.ChunkState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/** 用真实持久化仓储与文件验证采集/封存边界，codec 是硬件边界替身。 */
class RecordingChunkSinkTest {
    @TempDir lateinit var root: File
    private class Budget : SpaceBudget {
        var held = 0L
        override fun reserve(bytes: Long): Boolean { check(held == 0L); held = bytes; return true }
        override fun release(bytes: Long) { check(held == bytes); held = 0 }
    }
    private class Encoder(private val failSeal: Boolean = false) : CaptureChunkEncoder {
        override var state = EncoderState.CREATED
        private lateinit var file: File
        private var count = 0L
        private lateinit var target: EncoderTarget
        override fun open(target: EncoderTarget, tempPath: String): EncoderOpenResult {
            this.target = target; file = File(tempPath); state = EncoderState.OPEN
            val capability = EncoderCapability("test.aac", MIME_AAC, target.sampleRate, 128_000, 1)
            return EncoderOpenResult.Opened(capability, EncoderCapabilityDiagnostics.evaluate(target, capability))
        }
        override fun write(samples: ShortArray, offset: Int, length: Int): EncoderWriteResult {
            file.appendBytes(ByteArray(length) { samples[offset + it].toByte() })
            count += length
            return EncoderWriteResult.Written
        }
        override fun closeAndSeal(): EncoderSealResult {
            if (failSeal) return EncoderSealResult.Failed(EncoderFailure(EncoderStage.FINALIZE, "muxer failure"), file.path)
            state = EncoderState.SEALED
            val capability = EncoderCapability("test.aac", MIME_AAC, target.sampleRate, 128_000, 1)
            val checksum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            return EncoderSealResult.Sealed(file.path, file.length(), checksum, count * 1000 / target.sampleRate,
                capability, EncoderCapabilityDiagnostics.evaluate(target, capability))
        }
        override fun abort() { state = EncoderState.FAILED }
    }
    private fun frame(index: Long, samples: ShortArray, monoNs: Long = index * 1_000_000_000L) =
        PcmFrame("session", index, monoNs, 0, 1000, samples)

    @Test fun `跨六十秒边界不丢样本且新实例可以读取已封存索引`() {
        val index = File(root, "chunks.json")
        val repository = FileChunkRepository(index)
        val budget = Budget()
        val sink = RecordingChunkSink("session", root, repository, CaptureChunkEncoderFactory { Encoder() }, {}, { Long.MAX_VALUE }, budget)
        val samples = ShortArray(60_500) { (it % 127).toShort() }
        sink.start(1000)
        sink.accept(frame(0, samples))
        sink.stop()
        val chunks = FileChunkRepository(index).listBySession("session")
        assertEquals(listOf(ChunkState.SEALED, ChunkState.SEALED), chunks.map { it.state })
        assertEquals(listOf(60_000L, 500L), chunks.map { it.durationMs })
        assertEquals(listOf(0L, 60_000L), chunks.map { it.capturedStartMs })
        assertEquals(listOf(60_000L, 60_500L), chunks.map { it.capturedEndMs })
        val actual = chunks.flatMap { File(root, checkNotNull(it.finalPath)).readBytes().toList() }.toByteArray()
        assertArrayEquals(ByteArray(samples.size) { samples[it].toByte() }, actual)
        assertEquals(60_500L, sink.capturedMs)
        assertEquals(0L, budget.held)
    }

    @Test fun `暂停封块继续新块并且暂停墙钟间隔不计有效时长`() {
        val repository = FileChunkRepository(File(root, "chunks.json"))
        val sink = RecordingChunkSink("session", root, repository, CaptureChunkEncoderFactory { Encoder() }, {}, { Long.MAX_VALUE }, Budget())
        sink.start(1000)
        sink.accept(frame(0, ShortArray(1250)))
        sink.pause()
        assertEquals(1250L, sink.capturedMs)
        sink.resume()
        sink.accept(frame(1, ShortArray(750), 100_000_000_000L))
        sink.stop()
        assertEquals(2000L, sink.capturedMs)
        assertEquals(listOf(1250L, 750L), repository.listBySession("session").map { it.durationMs })
        assertEquals(listOf(0L, 1250L), repository.listBySession("session").map { it.capturedStartMs })
    }

    @Test fun `暂停封存失败不能成功且停止返回不可重放原因并保护临时文件`() {
        val repository = FileChunkRepository(File(root, "chunks.json"))
        val sink = RecordingChunkSink("session", root, repository, CaptureChunkEncoderFactory { Encoder(true) }, {}, { Long.MAX_VALUE }, Budget())
        sink.start(1000)
        sink.accept(frame(0, ShortArray(100)))
        val failure = assertThrows(RecordingStorageException::class.java) { sink.pause() }
        assertEquals("SEAL_FAILED", failure.code)
        val failed = repository.listBySession("session").single()
        assertEquals(ChunkState.SEAL_FAILED, failed.state)
        assertTrue(File(root, failed.tempPath).isFile)
        assertNull(failed.finalPath)
        assertEquals("RECORDING_RECOVERY_REQUIRED", assertThrows(RecordingStorageException::class.java) { sink.stop() }.code)
        assertEquals(ChunkState.SEAL_FAILED, FileChunkRepository(File(root, "chunks.json")).listBySession("session").single().state)
    }
}
