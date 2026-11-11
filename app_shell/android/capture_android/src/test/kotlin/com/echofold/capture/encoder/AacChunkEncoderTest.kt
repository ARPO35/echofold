package com.echofold.capture.encoder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** AAC/M4A 编码封装流程（fake codec + fake 封装器，真实临时文件）的 JVM 单测。 */
class AacChunkEncoderTest {

    @TempDir
    lateinit var tempDir: File

    /** 假编码器：按输入产出一段固定字节输出，可编程失败点。 */
    private class FakeCodec : AudioEncoderCodec {
        override var codecName: String = "fake.aac.encoder"
        var configuredFormat: AudioEncoderFormat? = null
        var reportedBitRate: Int? = null
        var started = false
        var releaseCount = 0
        var totalInputFrames = 0L
        var failOnConfigure: Throwable? = null
        var failOnStart: Throwable? = null
        var failOnQueue: Throwable? = null

        private val pending = ArrayDeque<EncodedBuffer>()
        private var lastPresentationTimeUs = 0L

        override fun configure(format: AudioEncoderFormat) {
            failOnConfigure?.let { throw it }
            configuredFormat = format
        }

        override fun start() {
            failOnStart?.let { throw it }
            started = true
        }

        override fun queueInput(samples: ShortArray, offset: Int, length: Int, presentationTimeUs: Long) {
            failOnQueue?.let { throw it }
            totalInputFrames += length
            lastPresentationTimeUs = presentationTimeUs
            val payload = byteArrayOf((length and 0xff).toByte(), (length shr 8 and 0xff).toByte())
            pending.addLast(
                EncodedBuffer(
                    data = payload,
                    offset = 0,
                    size = payload.size,
                    presentationTimeUs = presentationTimeUs,
                    flags = EncodedBufferFlags.KEY_FRAME,
                    isCodecConfig = false,
                ),
            )
        }

        override fun dequeueOutput(): EncodedBuffer? = pending.removeFirstOrNull()

        override fun signalEndOfStream() {
            pending.addLast(
                EncodedBuffer(
                    data = ByteArray(0),
                    offset = 0,
                    size = 0,
                    presentationTimeUs = lastPresentationTimeUs,
                    flags = EncodedBufferFlags.END_OF_STREAM,
                    isCodecConfig = false,
                ),
            )
        }

        override fun actualFormat(): AudioEncoderFormat? = configuredFormat?.let { format ->
            reportedBitRate?.let { format.copy(bitRate = it) } ?: format
        }

        override fun release() {
            releaseCount++
        }
    }

    /** 假封装器：把样本字节按顺序写入真实文件，便于校验 SHA-256。 */
    private class FakeWriter : AudioContainerWriter {
        var openedPath: String? = null
        var startFormat: AudioEncoderFormat? = null
        var finalized = false
        var aborted = false
        var failOnFinalize: Throwable? = null
        var failOnWrite: Throwable? = null
        val written = mutableListOf<EncodedBuffer>()

        private var stream: FileOutputStream? = null

        override fun open(path: String) {
            openedPath = path
            stream = FileOutputStream(path)
        }

        override fun writeSample(buffer: EncodedBuffer, format: AudioEncoderFormat) {
            failOnWrite?.let { throw it }
            if (startFormat == null) startFormat = format
            stream?.write(buffer.data, buffer.offset, buffer.size)
            written += buffer
        }

        override fun finalizeAndClose() {
            failOnFinalize?.let { throw it }
            stream?.flush()
            stream?.close()
            finalized = true
        }

        override fun abort() {
            aborted = true
            runCatching { stream?.close() }
        }
    }

    private fun target(sampleRate: Int = 16_000) = EncoderTarget(sampleRate = sampleRate)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(file.readBytes())
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test
    fun `打开后上报实际参数与偏差诊断`() {
        val codec = FakeCodec().apply { reportedBitRate = 128_000 }
        val encoder = AacChunkEncoder(codec = codec, writer = FakeWriter())

        val opened = encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)

        assertTrue(opened is EncoderOpenResult.Opened)
        opened as EncoderOpenResult.Opened
        assertEquals(EncoderState.OPEN, encoder.state)
        assertEquals("fake.aac.encoder", opened.capability.codecName)
        assertEquals(128_000, opened.capability.bitRate)
        assertEquals(1, opened.diagnostics.deviations.size)
        assertEquals(DeviationField.BIT_RATE, opened.diagnostics.deviations.single().dimension)
        assertFalse(opened.diagnostics.matchesTarget)
        assertTrue(opened.diagnostics.summary.contains("128000"))
    }

    @Test
    fun `写入并封存产生校验和与真实时长`() {
        val codec = FakeCodec()
        val writer = FakeWriter()
        val path = File(tempDir, "chunk.m4a").absolutePath
        val encoder = AacChunkEncoder(codec = codec, writer = writer)
        encoder.open(target(sampleRate = 16_000), path)

        val samples = ShortArray(2_048) { 7 }
        assertEquals(EncoderWriteResult.Written, encoder.write(samples))
        val sealed = encoder.closeAndSeal()

        assertTrue(sealed is EncoderSealResult.Sealed)
        sealed as EncoderSealResult.Sealed
        assertEquals(EncoderState.SEALED, encoder.state)
        assertEquals(path, sealed.tempPath)
        val file = File(path)
        assertEquals(file.length(), sealed.sizeBytes)
        assertEquals(sha256(file), sealed.sha256)
        assertEquals(128L, sealed.durationMs)
        assertTrue(writer.finalized)
        assertEquals(1, writer.written.size)
        assertEquals(1, codec.releaseCount)
    }

    @Test
    fun `送 PCM 失败返回 WRITE 阶段并保留临时文件`() {
        val codec = FakeCodec()
        val writer = FakeWriter()
        val path = File(tempDir, "chunk.m4a").absolutePath
        val encoder = AacChunkEncoder(codec = codec, writer = writer)
        encoder.open(target(), path)
        codec.failOnQueue = IllegalStateException("codec died")

        val result = encoder.write(ShortArray(16))

        assertTrue(result is EncoderWriteResult.Failed)
        assertEquals(EncoderStage.WRITE, (result as EncoderWriteResult.Failed).failure.stage)
        assertEquals(EncoderState.FAILED, encoder.state)
        assertTrue(File(path).exists())
        assertTrue(writer.aborted)
    }

    @Test
    fun `封装器收尾失败返回 FINALIZE 阶段并保留临时文件`() {
        val codec = FakeCodec()
        val writer = FakeWriter()
        val path = File(tempDir, "chunk.m4a").absolutePath
        val encoder = AacChunkEncoder(codec = codec, writer = writer)
        encoder.open(target(), path)
        encoder.write(ShortArray(16))
        writer.failOnFinalize = IllegalStateException("muxer stop failed")

        val result = encoder.closeAndSeal()

        assertTrue(result is EncoderSealResult.Failed)
        result as EncoderSealResult.Failed
        assertEquals(EncoderStage.FINALIZE, result.failure.stage)
        assertEquals(path, result.tempPath)
        assertEquals(EncoderState.FAILED, encoder.state)
        assertTrue(File(path).exists())
    }

    @Test
    fun `写入容器失败返回 WRITE 阶段`() {
        val codec = FakeCodec()
        val writer = FakeWriter()
        val encoder = AacChunkEncoder(codec = codec, writer = writer)
        encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)
        writer.failOnWrite = IllegalStateException("disk full")

        val result = encoder.write(ShortArray(16))

        assertEquals(EncoderStage.WRITE, (result as EncoderWriteResult.Failed).failure.stage)
    }

    @Test
    fun `未打开时写入与封存都返回结构化失败`() {
        val encoder = AacChunkEncoder(codec = FakeCodec(), writer = FakeWriter())

        val write = encoder.write(ShortArray(8))
        val seal = encoder.closeAndSeal()

        assertEquals(EncoderStage.WRITE, (write as EncoderWriteResult.Failed).failure.stage)
        assertEquals(EncoderStage.FINALIZE, (seal as EncoderSealResult.Failed).failure.stage)
        assertEquals(EncoderState.CREATED, encoder.state)
    }

    @Test
    fun `编码器配置失败返回 CONFIGURE 阶段`() {
        val codec = FakeCodec().apply { failOnConfigure = IllegalArgumentException("bad format") }
        val encoder = AacChunkEncoder(codec = codec, writer = FakeWriter())

        val result = encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)

        assertEquals(EncoderStage.CONFIGURE, (result as EncoderOpenResult.Failed).failure.stage)
        assertEquals(EncoderState.FAILED, encoder.state)
    }

    @Test
    fun `编码器启动失败返回 START 阶段`() {
        val codec = FakeCodec().apply { failOnStart = IllegalStateException("start failed") }
        val encoder = AacChunkEncoder(codec = codec, writer = FakeWriter())

        val result = encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)

        assertEquals(EncoderStage.START, (result as EncoderOpenResult.Failed).failure.stage)
        assertEquals(1, codec.releaseCount)
    }

    @Test
    fun `探测不到编码器时打开失败`() {
        val encoder = AacChunkEncoder(
            codec = FakeCodec(),
            writer = FakeWriter(),
            capabilitySource = { null },
        )

        val result = encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)

        assertEquals(EncoderStage.OPEN, (result as EncoderOpenResult.Failed).failure.stage)
        assertEquals(EncoderState.FAILED, encoder.state)
    }

    @Test
    fun `能力候选为空时打开失败`() {
        val encoder = AacChunkEncoder(
            codec = FakeCodec(),
            writer = FakeWriter(),
            capabilitySource = {
                EncoderCaps(
                    codecName = "broken",
                    mimeType = MIME_AAC,
                    supportedSampleRates = emptySet(),
                    bitRateRange = 8_000..32_000,
                    maxChannelCount = 1,
                )
            },
        )

        val result = encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)

        assertEquals(EncoderStage.OPEN, (result as EncoderOpenResult.Failed).failure.stage)
    }

    @Test
    fun `探测能力会把目标码率夹到设备区间并传入编码器`() {
        val codec = FakeCodec()
        val encoder = AacChunkEncoder(
            codec = codec,
            writer = FakeWriter(),
            capabilitySource = {
                EncoderCaps(
                    codecName = "c2.android.aac.encoder",
                    mimeType = MIME_AAC,
                    supportedSampleRates = setOf(48_000),
                    bitRateRange = 8_000..128_000,
                    maxChannelCount = 1,
                )
            },
        )

        val opened = encoder.open(
            EncoderTarget(sampleRate = 48_000, bitRate = 192_000),
            File(tempDir, "chunk.m4a").absolutePath,
        )

        assertEquals(128_000, codec.configuredFormat?.bitRate)
        assertEquals(48_000, codec.configuredFormat?.sampleRate)
        assertEquals(1, codec.configuredFormat?.channelCount)
        assertTrue((opened as EncoderOpenResult.Opened).diagnostics.deviations.isNotEmpty())
    }

    @Test
    fun `重复打开返回失败而不静默重开`() {
        val encoder = AacChunkEncoder(codec = FakeCodec(), writer = FakeWriter())
        val path = File(tempDir, "chunk.m4a").absolutePath
        encoder.open(target(), path)

        val second = encoder.open(target(), path)

        assertEquals(EncoderStage.OPEN, (second as EncoderOpenResult.Failed).failure.stage)
        assertEquals(EncoderState.OPEN, encoder.state)
    }

    @Test
    fun `放弃编码不删除临时文件`() {
        val path = File(tempDir, "chunk.m4a").absolutePath
        val encoder = AacChunkEncoder(codec = FakeCodec(), writer = FakeWriter())
        encoder.open(target(), path)

        encoder.abort()
        encoder.abort()

        assertEquals(EncoderState.FAILED, encoder.state)
        assertTrue(File(path).exists())
    }

    @Test
    fun `封存后再次写入被拒绝`() {
        val encoder = AacChunkEncoder(codec = FakeCodec(), writer = FakeWriter())
        encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)
        encoder.write(ShortArray(16))
        encoder.closeAndSeal()

        val result = encoder.write(ShortArray(16))

        assertEquals(EncoderStage.WRITE, (result as EncoderWriteResult.Failed).failure.stage)
    }

    @Test
    fun `封存时容器收到编码器实际格式`() {
        val codec = FakeCodec().apply { reportedBitRate = 160_000 }
        val writer = FakeWriter()
        val encoder = AacChunkEncoder(codec = codec, writer = writer)
        encoder.open(target(), File(tempDir, "chunk.m4a").absolutePath)
        encoder.write(ShortArray(16))

        encoder.closeAndSeal()

        assertEquals(160_000, writer.startFormat?.bitRate)
        assertEquals(16_000, writer.startFormat?.sampleRate)
        assertEquals(1, writer.startFormat?.channelCount)
    }
}
