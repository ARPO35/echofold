package com.echofold.capture.pcm

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionAsrSinkTest {
    private val config = AsrConfig(ModelRef("model", "1.0", "install", "a".repeat(64)))

    private class Engine : AsrEngine {
        val frames = mutableListOf<PcmFrame>()
        var closed = 0
        var failure: Exception? = null
        override fun initialize(config: AsrConfig) {}
        override fun acceptPcm(frame: PcmFrame) {
            failure?.let { throw it }
            frames += frame
        }
        override fun flushSegment() {}
        override fun close() { closed++ }
    }

    private fun frame(session: String = "s", rate: Int = 16_000) = PcmFrame(
        sessionId = session, frameIndex = 0, monoNs = 10, capturedStartMs = 0,
        sampleRate = rate, samples = shortArrayOf(10, 20),
    )

    @Test
    fun `接受的PCM帧到达固定引擎且会话关闭释放引擎`() {
        val engine = Engine()
        val sink = SessionAsrSink("s", config, engine)
        val accepted = frame()
        sink.accept(accepted)
        assertSame(accepted, engine.frames.single())
        sink.close()
        sink.close()
        assertEquals(1, engine.closed)
        assertThrows(AsrEngineException::class.java) { sink.accept(frame()) }
    }

    @Test
    fun `拒绝其他会话并保留原始采样率交给引擎重采样`() {
        val engine = Engine()
        val sink = SessionAsrSink("s", config, engine)
        assertThrows(AsrEngineException::class.java) { sink.accept(frame(session = "other")) }
        val original = frame(rate = 48_000)
        sink.accept(original)
        assertSame(original, engine.frames.single())
    }

    @Test
    fun `推理故障显式报告且向分发器抛出`() {
        val engine = Engine().apply { failure = IllegalStateException("runtime stopped") }
        val reported = AtomicInteger()
        val sink = SessionAsrSink("s", config, engine) { reported.incrementAndGet() }
        val error = assertThrows(AsrEngineException::class.java) { sink.accept(frame()) }
        assertEquals("ASR PCM 处理失败：runtime stopped", error.message)
        assertEquals(1, reported.get())
    }

    @Test
    fun `引擎初始化失败作为不可用返回而不生成识别结果`() {
        val result = SessionAsrSinkFactory.create("s", config, {
            object : AsrEngine {
                override fun initialize(config: AsrConfig) { throw AsrEngineException("model missing") }
                override fun acceptPcm(frame: PcmFrame) = error("不可调用")
                override fun flushSegment() = error("不可调用")
                override fun close() {}
            }
        })
        assertTrue(result is SessionAsrCreation.Unavailable)
        assertEquals("ASR_UNAVAILABLE", (result as SessionAsrCreation.Unavailable).status.code)
        assertEquals("model missing", result.status.message)
    }
}
