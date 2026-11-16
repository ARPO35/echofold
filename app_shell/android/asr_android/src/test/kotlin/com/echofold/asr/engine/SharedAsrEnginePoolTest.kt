package com.echofold.asr.engine

import com.echofold.asr.repository.ModelLeaseManager
import com.echofold.asr.repository.ModelRepository
import com.echofold.asr.testing.FakeSherpaRuntime
import com.echofold.asr.testing.InMemoryLeaseStore
import com.echofold.asr.testing.InMemoryModelIndexStore
import com.echofold.asr.testing.asrConfig
import com.echofold.asr.testing.createModelDirectory
import com.echofold.asr.testing.installedModel
import com.echofold.asr.testing.testManifest
import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.PcmFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 进程内权重复用契约：同一安装+配置只创建一次引擎；显式切换/运行时失效才重建；
 * 预热失败可查且不崩溃；各会话识别流状态隔离。
 */
class SharedAsrEnginePoolTest {
    @TempDir
    lateinit var root: File

    private val manifest = testManifest()
    private val indexStore = InMemoryModelIndexStore()
    private val repository = ModelRepository(indexStore, ModelLeaseManager(InMemoryLeaseStore(), clock = { 10L }))
    private val runtime = FakeSherpaRuntime()

    private fun register(
        installedManifest: com.echofold.asr.model.ModelManifest = manifest,
        id: String = com.echofold.asr.install.InstallationIds.of(installedManifest),
    ) {
        val directory = createModelDirectory(root, id, installedManifest)
        repository.install(installedModel(directory, installedManifest, installationId = id), select = true)
    }

    private fun pool(): SharedAsrEnginePool = SharedAsrEnginePool { config, callbacks ->
        AsrEngineProvider(
            repository = repository,
            runtimeProvider = { runtime },
            runtimeVersion = "1.10.0",
            deviceAbis = listOf("arm64-v8a"),
            onFinalText = { callbacks.onFinalText(it) },
            onPartialText = { callbacks.onPartialText(it) },
        ).create(config)
    }

    private fun frame(sessionId: String) = PcmFrame(
        sessionId = sessionId,
        frameIndex = 0L,
        monoNs = 1_000L,
        capturedStartMs = 0L,
        sampleRate = 16_000,
        samples = shortArrayOf(1),
    )

    @Test
    fun `同模型两次会话只创建一次引擎`() {
        register()
        val config = asrConfig(manifest)
        val pool = pool()

        val finals1 = mutableListOf<String>()
        val session1 = pool.acquire(config, finals1::add, {})
        session1.acceptPcm(frame("session-1"))
        session1.close()

        val session2 = pool.acquire(config, {}, {})
        assertEquals(1, runtime.createCount, "两次会话必须复用同一引擎实例")

        // 会话隔离：新会话识别流已重置，flush 不交付上一会话的文本。
        runtime.recognizer.endpoint = false
        session2.acceptPcm(frame("session-2"))
        runtime.recognizer.endpoint = true
        session2.flushSegment()
        assertEquals(1, finals1.size, "上一会话的 FINAL 不进入新会话回调")
        session2.close()
    }

    @Test
    fun `显式切换模型后重建引擎`() {
        val manifest2 = testManifest(version = "2.0.0")
        val id2 = com.echofold.asr.install.InstallationIds.of(manifest2)
        register()
        register(installedManifest = manifest2, id = id2)
        val pool = pool()

        val config1 = asrConfig(manifest)
        val session1 = pool.acquire(config1, {}, {})
        pool.invalidate("model.select")
        repository.select(id2)

        val session2 = pool.acquire(asrConfig(manifest2), {}, {})
        assertEquals(2, runtime.createCount, "显式切换后必须重建引擎")
        assertEquals("model.select", pool.rebuildReason)

        // 旧会话仍可完成（stale 条目随最后引用销毁），不静默换模型。
        session1.acceptPcm(frame("session-1"))
        session1.close()
        session2.close()
        assertEquals(2, runtime.createCount)
        assertEquals(1, runtime.recognizer.closeCount, "被替换且失去引用的旧权重已销毁；新权重按会话结束保留")
    }

    @Test
    fun `暂停继续不重建引擎`() {
        register()
        val config = asrConfig(manifest)
        val pool = pool()

        // 暂停/继续期间 binding 持续持有；冲刷与继续送入 PCM 不得触发重建。
        val session = pool.acquire(config, {}, {})
        session.acceptPcm(frame("session-1"))
        val resetsBeforeFlush = runtime.recognizer.resetCount
        session.flushSegment() // 模拟暂停前的分段冲刷
        session.acceptPcm(frame("session-1")) // 模拟继续后的音频
        assertEquals(1, runtime.createCount)
        assertTrue(runtime.recognizer.resetCount > resetsBeforeFlush, "冲刷与继续不得重建引擎")

        // 会话结束后新会话依然复用权重。
        val resetsBeforeBoundary = runtime.recognizer.resetCount
        session.close()
        pool.acquire(config, {}, {}).close()
        assertEquals(1, runtime.createCount, "新会话不得重新加载权重")
        assertTrue(runtime.recognizer.resetCount > resetsBeforeBoundary, "每个会话边界都重置识别流")
    }

    @Test
    fun `预热失败可查且后续会话可重建`() {
        register()
        val config = asrConfig(manifest)
        val pool = pool()

        runtime.createFailure = AsrEngineException("native boom")
        val error = assertThrows<AsrEngineException> { pool.warmup(config) }
        assertEquals("native boom", error.message)
        assertEquals(1, runtime.createCount, "失败的预热只消耗一次创建尝试")

        // 失败不缓存半初始化状态；恢复后预热与会话均可用。
        runtime.createFailure = null
        pool.warmup(config)
        assertEquals(2, runtime.createCount)
        val session = pool.acquire(config, {}, {})
        session.acceptPcm(frame("session-1"))
        assertEquals(2, runtime.createCount, "预热成功的条目被会话复用，不再创建")
        session.close()
    }

    @Test
    fun `运行时失效后下一次会话重建`() {
        register()
        val config = asrConfig(manifest)
        val pool = pool()

        pool.warmup(config)
        assertEquals(1, runtime.createCount, "预热创建引擎")
        pool.acquire(config, {}, {}).close() // 会话结束只减引用，权重保留
        assertEquals(0, runtime.recognizer.closeCount, "会话结束不得销毁权重")

        pool.invalidate("ASR 运行时失效")
        assertEquals(1, runtime.recognizer.closeCount, "invalidate 立即销毁空闲权重")
        assertEquals("ASR 运行时失效", pool.rebuildReason)

        pool.acquire(config, {}, {}).close()
        assertEquals(2, runtime.createCount, "失效后重建")
        assertEquals(1, runtime.recognizer.closeCount, "重建后的权重随会话结束保留")
    }
}
