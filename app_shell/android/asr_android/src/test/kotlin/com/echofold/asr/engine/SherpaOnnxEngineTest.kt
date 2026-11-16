package com.echofold.asr.engine

import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.asr.engine.Resampler
import com.echofold.asr.testing.FakeSherpaRuntime
import com.echofold.asr.testing.asrConfig
import com.echofold.asr.testing.createModelDirectory
import com.echofold.asr.testing.defaultModelFiles
import com.echofold.asr.testing.engineFor
import com.echofold.asr.testing.testManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SherpaOnnxEngineTest {
    @TempDir
    lateinit var root: File

    private val manifest = testManifest()
    private val installationId = "install-1"
    private val runtime = FakeSherpaRuntime()

    private fun modelDirectory(
        files: Map<String, ByteArray> = defaultModelFiles(),
        snapshotManifest: com.echofold.asr.model.ModelManifest = manifest,
    ): File = createModelDirectory(root, installationId, snapshotManifest, files)

    private fun frame(
        samples: ShortArray,
        sampleRate: Int = 16_000,
        offset: Int = 0,
        length: Int = samples.size,
    ): PcmFrame =
        PcmFrame(
            sessionId = "session-1",
            frameIndex = 0L,
            monoNs = 1_000L,
            capturedStartMs = 0L,
            sampleRate = sampleRate,
            samples = samples,
            offset = offset,
            length = length,
        )

    @Test
    fun `模型目录缺失时初始化明确失败`() {
        val engine = engineFor(File(root, "missing"), runtime)
        val error = assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("不存在"))
        assertEquals(0, runtime.createCount)
    }

    @Test
    fun `安装目录缺少清单快照时失败`() {
        val directory = File(root, "no-snapshot")
        directory.mkdirs()
        defaultModelFiles().forEach { (name, content) -> File(directory, name).writeBytes(content) }
        val engine = engineFor(directory, runtime)

        val error = assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("缺少清单快照"))
    }

    @Test
    fun `清单快照身份不一致时失败`() {
        val otherManifest = manifest.copy(sha256 = "b".repeat(64))
        val directory = modelDirectory(snapshotManifest = otherManifest)
        val engine = engineFor(directory, runtime)

        val error = assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("身份不一致"))
        assertEquals(0, runtime.createCount)
    }

    @Test
    fun `运行时初始化失败包装为 AsrEngineException 且不回退`() {
        runtime.createFailure = IllegalStateException("native boom")
        val engine = engineFor(modelDirectory(), runtime)

        val error = assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("初始化失败"))
        assertEquals(1, runtime.createCount)
    }

    @Test
    fun `模型布局优先选择 int8 权重`() {
        val files =
            defaultModelFiles() +
                mapOf(
                    "encoder.onnx" to ByteArray(16),
                    "decoder.onnx" to ByteArray(16),
                    "joiner.onnx" to ByteArray(16),
                )
        val engine = engineFor(modelDirectory(files = files), runtime)

        engine.initialize(asrConfig(manifest, installationId))

        assertEquals("encoder.int8.onnx", runtime.createdPaths!!.encoder.name)
        assertEquals("decoder.int8.onnx", runtime.createdPaths!!.decoder.name)
        assertEquals("tokens.txt", runtime.createdPaths!!.tokens.name)
    }

    @Test
    fun `指定IB Robot bundle四工件目录可以加载`() {
        val directory = File(root, "bundle").apply { mkdirs() }
        val paths = listOf("encoder/encoder.int8.onnx", "decoder/decoder.onnx",
            "joiner/joiner.int8.onnx", "tokens/tokens.txt")
        for (path in paths) {
            File(directory, "artifacts/$path").apply { parentFile.mkdirs(); writeText("fixture") }
        }
        val resolved = SherpaModelLayout.resolve(directory)
        assertEquals(File(directory, "artifacts/encoder/encoder.int8.onnx"), resolved.encoder)
        assertEquals(File(directory, "artifacts/decoder/decoder.onnx"), resolved.decoder)
        assertEquals(File(directory, "artifacts/joiner/joiner.int8.onnx"), resolved.joiner)
        assertEquals(File(directory, "artifacts/tokens/tokens.txt"), resolved.tokens)
    }

    @Test
    fun `未初始化时拒绝 PCM 与冲刷`() {
        val engine = engineFor(modelDirectory(), runtime)
        assertThrows<AsrEngineException> { engine.acceptPcm(frame(shortArrayOf(1))) }
        assertThrows<AsrEngineException> { engine.flushSegment() }
    }

    @Test
    fun `PCM 帧按 offset 与 length 归一化为 float`() {
        val engine = engineFor(modelDirectory(), runtime)
        engine.initialize(asrConfig(manifest, installationId))
        val frame = frame(shortArrayOf(0, 32767, -32768, 0), offset = 1, length = 2)

        engine.acceptPcm(frame)

        val samples = runtime.recognizer.accepted.single()
        assertEquals(2, samples.size)
        assertEquals(1.0f, samples[0], 1e-4f)
        assertEquals(-1.0f, samples[1], 1e-6f)
        assertEquals(listOf(16_000), runtime.recognizer.acceptedRates)
    }

    @Test
    fun `采样率不一致时使用注入的重采样器`() {
        val resampler =
            object : Resampler {
                override fun resample(input: ShortArray, inputRate: Int, channels: Int): ShortArray =
                    ShortArray(input.size * 2) { input[it / 2] }
            }
        val engine = engineFor(modelDirectory(), runtime, resampler = resampler)
        engine.initialize(asrConfig(manifest, installationId))

        engine.acceptPcm(frame(shortArrayOf(100, 200, 300), sampleRate = 8_000))

        assertEquals(6, runtime.recognizer.accepted.single().size)
        assertEquals(listOf(16_000), runtime.recognizer.acceptedRates)
    }

    @Test
    fun `采样率不一致且无重采样器时明确失败`() {
        val engine = engineFor(modelDirectory(), runtime)
        engine.initialize(asrConfig(manifest, installationId))
        val error = assertThrows<AsrEngineException> {
            engine.acceptPcm(frame(shortArrayOf(1, 2), sampleRate = 8_000))
        }
        assertTrue(error.message!!.contains("重采样"))
    }

    @Test
    fun `逐帧 partial 只交付非空且变化的文本`() {
        val partials = mutableListOf<String>()
        val directory = modelDirectory()
        val engine = SherpaOnnxEngine(
            runtime = runtime,
            modelDirectoryResolver = { directory },
            onPartialText = { partials += it },
        )
        runtime.recognizer.endpoint = false
        engine.initialize(asrConfig(manifest, installationId))

        for (text in listOf("", "   ", "  你好  ", "你好", "你好世界", "你好世界")) {
            runtime.recognizer.text = text
            engine.acceptPcm(frame(shortArrayOf(1)))
        }

        assertEquals(listOf("你好", "你好世界"), partials)
        assertEquals(0, runtime.recognizer.resetCount)
        assertNull(engine.lastFinalText())
    }

    @Test
    fun `端点依次交付 partial 与 final 并复位且新段允许相同文本`() {
        val events = mutableListOf<String>()
        val directory = modelDirectory()
        val engine = SherpaOnnxEngine(
            runtime = runtime,
            modelDirectoryResolver = { directory },
            onPartialText = { events += "partial:$it" },
            onFinalText = {
                assertEquals(1, runtime.recognizer.resetCount)
                events += "final:$it"
            },
        )
        engine.initialize(asrConfig(manifest, installationId))
        runtime.recognizer.text = "  你好  "
        runtime.recognizer.endpoint = true

        engine.acceptPcm(frame(shortArrayOf(1)))

        assertEquals(listOf("partial:你好", "final:你好"), events)
        assertEquals("你好", engine.lastFinalText())
        assertEquals(1, runtime.recognizer.resetCount)

        runtime.recognizer.endpoint = false
        runtime.recognizer.text = ""
        engine.acceptPcm(frame(shortArrayOf(1)))
        runtime.recognizer.text = "你好"
        engine.acceptPcm(frame(shortArrayOf(1)))
        engine.acceptPcm(frame(shortArrayOf(1)))

        assertEquals(listOf("partial:你好", "final:你好", "partial:你好"), events)
        assertEquals(1, runtime.recognizer.resetCount)
    }

    @Test
    fun `空端点只复位而不交付 partial 或 final`() {
        val events = mutableListOf<String>()
        val directory = modelDirectory()
        val engine = SherpaOnnxEngine(
            runtime = runtime,
            modelDirectoryResolver = { directory },
            onPartialText = { events += "partial:$it" },
            onFinalText = { events += "final:$it" },
        )
        engine.initialize(asrConfig(manifest, installationId))
        runtime.recognizer.text = "   "
        runtime.recognizer.endpoint = true

        engine.acceptPcm(frame(shortArrayOf(1)))

        assertTrue(events.isEmpty())
        assertEquals(1, runtime.recognizer.resetCount)
        assertNull(engine.lastFinalText())
    }

    @Test
    fun `主动冲刷也清空 partial 去重缓存`() {
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val directory = modelDirectory()
        val engine = SherpaOnnxEngine(
            runtime = runtime,
            modelDirectoryResolver = { directory },
            onPartialText = { partials += it },
            onFinalText = { finals += it },
        )
        engine.initialize(asrConfig(manifest, installationId))
        runtime.recognizer.endpoint = false
        runtime.recognizer.text = "你好"

        engine.acceptPcm(frame(shortArrayOf(1)))
        engine.flushSegment()
        runtime.recognizer.text = "你好"
        engine.acceptPcm(frame(shortArrayOf(1)))
        engine.acceptPcm(frame(shortArrayOf(1)))

        assertEquals(listOf("你好", "你好"), partials)
        assertEquals(listOf("你好"), finals)
        assertEquals(1, runtime.recognizer.resetCount)
    }

    @Test
    fun `冲刷交付文本并复位识别流`() {
        val emitted = mutableListOf<String>()
        runtime.recognizer.text = "  你好世界  "
        val engine = engineFor(modelDirectory(), runtime, onFinalText = { emitted += it })
        engine.initialize(asrConfig(manifest, installationId))

        engine.flushSegment()

        assertEquals(listOf("你好世界"), emitted)
        assertEquals("你好世界", engine.lastFinalText())
        assertEquals(1, runtime.recognizer.resetCount)
    }

    @Test
    fun `冲刷空文本时不回调`() {
        val emitted = mutableListOf<String>()
        runtime.recognizer.text = "   "
        val engine = engineFor(modelDirectory(), runtime, onFinalText = { emitted += it })
        engine.initialize(asrConfig(manifest, installationId))

        engine.flushSegment()

        assertTrue(emitted.isEmpty())
        assertNull(engine.lastFinalText())
        assertEquals(1, runtime.recognizer.resetCount)
    }

    @Test
    fun `运行时接收失败包装为 AsrEngineException`() {
        runtime.recognizer.failOnAccept = IllegalStateException("decode failed")
        val engine = engineFor(modelDirectory(), runtime)
        engine.initialize(asrConfig(manifest, installationId))

        val error = assertThrows<AsrEngineException> { engine.acceptPcm(frame(shortArrayOf(1, 2))) }
        assertTrue(error.message!!.contains("运行时失败"))
    }

    @Test
    fun `重复初始化同一配置幂等，不同配置明确失败`() {
        val engine = engineFor(modelDirectory(), runtime)
        val config = asrConfig(manifest, installationId)
        engine.initialize(config)
        engine.initialize(config)
        assertEquals(1, runtime.createCount)

        val otherConfig = asrConfig(manifest, installationId).copy(threads = 2)
        assertThrows<AsrEngineException> { engine.initialize(otherConfig) }
    }

    @Test
    fun `关闭幂等且关闭后拒绝使用`() {
        val engine = engineFor(modelDirectory(), runtime)
        engine.initialize(asrConfig(manifest, installationId))

        engine.close()
        engine.close()

        assertEquals(1, runtime.recognizer.closeCount)
        assertThrows<AsrEngineException> { engine.acceptPcm(frame(shortArrayOf(1))) }
        assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
    }

    @Test
    fun `布局缺少必需文件时失败`() {
        val files = defaultModelFiles() - "tokens.txt"
        val engine = engineFor(modelDirectory(files = files), runtime)

        val error = assertThrows<AsrEngineException> { engine.initialize(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("tokens.txt"))
    }
}
