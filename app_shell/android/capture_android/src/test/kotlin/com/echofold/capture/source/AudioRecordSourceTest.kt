package com.echofold.capture.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AudioRecord 封装逻辑（时间轴、暂停/BLOCKED 门、错误分类、幂等释放）的 JVM 单测。 */
class AudioRecordSourceTest {

    /** 可编程的假 AudioRecord 句柄；不触碰任何 Android 运行期类。 */
    private class FakeHandle(
        override val sampleRate: Int = 48_000,
        override val channelCount: Int = 1,
    ) : AudioRecordHandle {
        var framesPerRead = 480
        var startCount = 0
        var stopCount = 0
        var releaseCount = 0
        var readErrorCode: Int? = null
        var timestampMonoNs: Long? = null
        var throwOnStart: Throwable? = null
        var throwOnRead: Throwable? = null

        override fun start() {
            throwOnStart?.let { throw it }
            startCount++
        }

        override fun read(destination: ShortArray, offset: Int, length: Int): Int {
            throwOnRead?.let { throw it }
            readErrorCode?.let { return it }
            val frames = minOf(framesPerRead, length)
            for (index in 0 until frames) {
                destination[offset + index] = 1
            }
            return frames
        }

        override fun stop() {
            stopCount++
        }

        override fun release() {
            releaseCount++
        }

        override fun latestTimestampMonoNs(): Long? = timestampMonoNs
    }

    private class FakeFactory(
        var handle: FakeHandle = FakeHandle(),
        var throwOnCreate: Throwable? = null,
    ) : AudioRecordFactory {
        var createCount = 0
        lateinit var lastConfig: AudioRecordConfig

        override fun create(config: AudioRecordConfig): AudioRecordHandle {
            lastConfig = config
            throwOnCreate?.let { throw it }
            createCount++
            return handle
        }
    }

    private var now: Long = 0L
    private val clock = MonotonicClock { now }

    private fun source(
        factory: FakeFactory = FakeFactory(),
        config: AudioRecordConfig = AudioRecordConfig(),
    ) = AudioRecordSource(factory = factory, config = config, clock = clock)

    @Test
    fun `打开后上报设备实际采样率与声道数`() {
        val factory = FakeFactory(handle = FakeHandle(sampleRate = 44_100))
        val source = source(factory)

        val opened = source.open()

        assertTrue(opened is SourceOpenResult.Opened)
        opened as SourceOpenResult.Opened
        assertEquals(44_100, opened.sampleRate)
        assertEquals(1, opened.channelCount)
        assertEquals(44_100, source.sampleRate)
        assertEquals(1, factory.handle.startCount)
        assertEquals(listOf(48_000, 44_100, 16_000), factory.lastConfig.preferredSampleRates)
    }

    @Test
    fun `重复打开是幂等的，不重复创建句柄`() {
        val factory = FakeFactory()
        val source = source(factory)
        source.open()
        source.open()

        assertEquals(1, factory.createCount)
        assertEquals(1, factory.handle.startCount)
    }

    @Test
    fun `缺少权限时打开失败且不启动采集`() {
        val factory = FakeFactory(throwOnCreate = SecurityException("RECORD_AUDIO denied"))
        val source = source(factory)

        val opened = source.open()

        assertTrue(opened is SourceOpenResult.Failed)
        assertEquals(
            AudioCaptureError.MIC_PERMISSION_DENIED,
            (opened as SourceOpenResult.Failed).failure.error,
        )
        assertFalse(source.isOpen)
        assertEquals(0, factory.handle.startCount)
    }

    @Test
    fun `设备被占用时分类为 DEVICE_IN_USE 且释放句柄`() {
        val factory = FakeFactory(throwOnCreate = IllegalStateException("mic busy"))
        val source = source(factory)

        val opened = source.open()

        assertEquals(
            AudioCaptureError.DEVICE_IN_USE,
            (opened as SourceOpenResult.Failed).failure.error,
        )
    }

    @Test
    fun `启动失败时释放已创建的句柄`() {
        val handle = FakeHandle()
        handle.throwOnStart = IllegalStateException("start failed")
        val factory = FakeFactory(handle = handle)
        val source = source(factory)

        val opened = source.open()

        assertTrue(opened is SourceOpenResult.Failed)
        assertEquals(1, handle.releaseCount)
    }

    @Test
    fun `读取推进 frameIndex 与有效音频偏移`() {
        val factory = FakeFactory(handle = FakeHandle(sampleRate = 48_000).apply { framesPerRead = 480 })
        val source = source(factory)
        source.open()
        val buffer = ShortArray(480)

        val first = source.read(buffer) as FrameReadResult.Read
        val second = source.read(buffer) as FrameReadResult.Read

        assertEquals(0L, first.frameIndex)
        assertEquals(480, first.frames)
        assertEquals(0L, first.capturedStartMs)
        assertEquals(1L, second.frameIndex)
        assertEquals(10L, second.capturedStartMs)
        assertEquals(960L, source.capturedFrames)
        assertEquals(2L, source.frameIndex)
    }

    @Test
    fun `暂停不推进时间轴并在继续后从暂停前偏移继续`() {
        val factory = FakeFactory(handle = FakeHandle(sampleRate = 1_000).apply { framesPerRead = 100 })
        val source = source(factory)
        source.open()
        val buffer = ShortArray(100)
        source.read(buffer)

        source.pause()
        now += 5_000_000_000L
        assertEquals(FrameReadResult.Paused, source.read(buffer))
        assertEquals(100L, source.capturedFrames)

        source.resume()
        val resumed = source.read(buffer) as FrameReadResult.Read

        assertEquals(100L, resumed.capturedStartMs)
        assertEquals(1, factory.handle.stopCount)
        assertEquals(2, factory.handle.startCount)
        assertEquals(200L, source.capturedFrames)
    }

    @Test
    fun `BLOCKED 门阻止读取并在解除后恢复`() {
        val factory = FakeFactory()
        val source = source(factory)
        source.open()
        val buffer = ShortArray(480)

        source.block()
        assertEquals(FrameReadResult.Blocked, source.read(buffer))
        assertEquals(0L, source.capturedFrames)

        source.unblock()
        assertTrue(source.read(buffer) is FrameReadResult.Read)
    }

    @Test
    fun `暂停状态下 BLOCKED 解除后仍保持暂停`() {
        val factory = FakeFactory()
        val source = source(factory)
        source.open()
        source.pause()
        source.block()

        source.unblock()

        assertEquals(FrameReadResult.Paused, source.read(ShortArray(480)))
        assertEquals(1, factory.handle.stopCount)
    }

    @Test
    fun `关闭是幂等的且重复调用只释放一次`() {
        val handle = FakeHandle()
        val source = source(FakeFactory(handle = handle))
        source.open()

        source.close()
        source.close()

        assertEquals(1, handle.releaseCount)
        assertFalse(source.isOpen)
        assertTrue(source.read(ShortArray(480)) is FrameReadResult.Failed)
    }

    @Test
    fun `未打开时读取返回 NotOpened`() {
        val source = source()

        assertEquals(FrameReadResult.NotOpened, source.read(ShortArray(16)))
    }

    @Test
    fun `设备时间戳断裂被标记但音频不丢弃`() {
        val handle = FakeHandle(sampleRate = 48_000).apply {
            framesPerRead = 480
            timestampMonoNs = 1_000_000_000L
        }
        val source = source(FakeFactory(handle = handle))
        source.open()
        val buffer = ShortArray(480)

        val first = source.read(buffer) as FrameReadResult.Read
        handle.timestampMonoNs = 2_000_000_000L
        val second = source.read(buffer) as FrameReadResult.Read

        assertFalse(first.timestampDiscontinuity)
        assertTrue(second.timestampDiscontinuity)
        assertEquals(480, second.frames)
        assertEquals(1L, source.timestampDiscontinuityCount)
        assertEquals(1_000_000_000L, first.firstMonoNs)
        assertEquals(2_000_000_000L, second.firstMonoNs)
    }

    @Test
    fun `设备时间戳不可用时回落到注入时钟`() {
        val source = source(FakeFactory(handle = FakeHandle().apply { timestampMonoNs = null }))
        source.open()
        now = 7_000_000_000L

        val read = source.read(ShortArray(480)) as FrameReadResult.Read

        assertEquals(7_000_000_000L, read.firstMonoNs)
        assertEquals(0L, source.timestampDiscontinuityCount)
    }

    @Test
    fun `读取错误码分类为设备占用且失败是粘性的`() {
        val handle = FakeHandle().apply { readErrorCode = -6 }
        val source = source(FakeFactory(handle = handle))
        source.open()

        val first = source.read(ShortArray(480)) as FrameReadResult.Failed
        val second = source.read(ShortArray(480)) as FrameReadResult.Failed
        assertEquals(AudioCaptureError.DEVICE_IN_USE, first.failure.error)
        assertEquals(AudioCaptureError.DEVICE_IN_USE, second.failure.error)
        assertEquals(0L, source.capturedFrames)
    }

    @Test
    fun `读取抛异常时分类为失败并停止句柄`() {
        val handle = FakeHandle().apply { throwOnRead = IllegalStateException("dead") }
        val source = source(FakeFactory(handle = handle))
        source.open()

        val result = source.read(ShortArray(480)) as FrameReadResult.Failed

        assertEquals(AudioCaptureError.DEVICE_IN_USE, result.failure.error)
        assertTrue(handle.stopCount >= 1)
    }

    @Test
    fun `零帧读取不推进时间轴`() {
        val handle = FakeHandle().apply { framesPerRead = 0 }
        val source = source(FakeFactory(handle = handle))
        source.open()

        val read = source.read(ShortArray(480)) as FrameReadResult.Read

        assertEquals(0, read.frames)
        assertEquals(0L, source.capturedFrames)
        assertEquals(0L, source.frameIndex)
    }

    @Test
    fun `关闭后读取返回失败`() {
        val source = source()
        source.open()
        source.close()

        val result = source.read(ShortArray(8))

        assertTrue(result is FrameReadResult.Failed)
        assertEquals(AudioCaptureError.READ_FAILED, (result as FrameReadResult.Failed).failure.error)
    }
}
