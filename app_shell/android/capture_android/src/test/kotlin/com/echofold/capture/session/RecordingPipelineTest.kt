package com.echofold.capture.session

import com.echofold.capture.pcm.AmplitudeSampler
import com.echofold.capture.pcm.LifecycleEncoderSink
import com.echofold.capture.pipeline.CaptureLanes
import com.echofold.capture.pipeline.DefaultRecordingPipeline
import com.echofold.capture.source.*
import com.echofold.core.asr.engine.PcmFrame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RecordingPipelineTest {
    @Test fun `暂停等待在途编码后封块且不阻塞唯一采集线程`() {
        val reads = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val paused = CountDownLatch(1)
        val microphoneStopped = AtomicBoolean()
        val sealed = AtomicBoolean()
        val sink = object : LifecycleEncoderSink {
            override val capturedMs = 0L
            override fun start(sampleRate: Int) {}
            override fun resume() {}
            override fun stop() {}
            override fun pause() { sealed.set(true) }
            override fun accept(frame: PcmFrame) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        val handle = object : AudioRecordHandle {
            override val sampleRate = 1000
            override val channelCount = 1
            override fun start() { microphoneStopped.set(false) }
            override fun stop() { microphoneStopped.set(true) }
            override fun release() {}
            override fun latestTimestampMonoNs(): Long? = null
            override fun read(destination: ShortArray, offset: Int, length: Int): Int {
                if (reads.get() >= 3) return 0
                reads.incrementAndGet()
                return 10
            }
        }
        val pipeline = DefaultRecordingPipeline("session", AudioRecordConfig(listOf(1000)),
            object : AudioRecordFactory { override fun create(config: AudioRecordConfig) = handle },
            CaptureLanes(null, sink, AmplitudeSampler(), encoderCapacityFrames = 4))
        pipeline.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (reads.get() < 3 && System.nanoTime() < deadline) Thread.yield()
            assertEquals(3, reads.get(), "在途编码尚未返回时，采集仍继续接受后续帧")
            val command = Thread { pipeline.pause(); paused.countDown() }
            command.start()
            assertFalse(paused.await(100, TimeUnit.MILLISECONDS))
            assertFalse(sealed.get(), "在途accept未完成前不可报告封存")
            assertTrue(microphoneStopped.get(), "等待编码排空前就停止硬件采集")
            release.countDown()
            assertTrue(paused.await(5, TimeUnit.SECONDS))
            assertTrue(sealed.get())
            assertEquals(30L, pipeline.capturedMs)
            command.join()
        } finally { release.countDown(); pipeline.stopAndRelease() }
    }

    @Test fun `队列硬上限停止硬件且排空封存后仍返回原始故障`() {
        verifyFailureBoundary(consumerFails = false)
    }

    @Test fun `编码消费者失败不重放失败帧也不封存为完整文件`() {
        verifyFailureBoundary(consumerFails = true)
    }

    private fun verifyFailureBoundary(consumerFails: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val stopped = AtomicBoolean()
        val seals = AtomicInteger()
        val accepts = AtomicInteger()
        val reads = AtomicInteger()
        val sink = object : LifecycleEncoderSink {
            override val capturedMs = 0L
            override fun start(sampleRate: Int) {}
            override fun resume() {}
            override fun pause() { seals.incrementAndGet() }
            override fun stop() { seals.incrementAndGet() }
            override fun accept(frame: PcmFrame) {
                accepts.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                if (consumerFails) throw IllegalStateException("编码写入失败")
            }
        }
        val handle = object : AudioRecordHandle {
            override val sampleRate = 1000
            override val channelCount = 1
            override fun start() {}
            override fun stop() { stopped.set(true) }
            override fun release() {}
            override fun latestTimestampMonoNs(): Long? = null
            override fun read(destination: ShortArray, offset: Int, length: Int): Int {
                if (consumerFails && reads.get() >= 2) return 0
                reads.incrementAndGet()
                return 10
            }
        }
        val pipeline = DefaultRecordingPipeline("session", AudioRecordConfig(listOf(1000)),
            object : AudioRecordFactory { override fun create(config: AudioRecordConfig) = handle },
            CaptureLanes(null, sink, AmplitudeSampler(), encoderCapacityFrames = 4))
        pipeline.setStateListener { _, _ -> failed.countDown() }
        pipeline.start()
        val stopStarted = AtomicBoolean()
        val stopFailure = AtomicReference<Throwable?>()
        val stopThread = Thread({
            try { pipeline.stopAndRelease() } catch (error: Throwable) { stopFailure.set(error) }
        }, "recording-test-stop").apply { isDaemon = true }
        fun finishPipeline(): Throwable? {
            release.countDown()
            if (stopStarted.compareAndSet(false, true)) stopThread.start()
            stopThread.join(5_000L)
            assertFalse(stopThread.isAlive, "录音管线必须在有界时间内释放所有线程")
            return stopFailure.get()
        }
        var primaryFailure: Throwable? = null
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            if (consumerFails) release.countDown()
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertTrue(stopped.get())
            release.countDown()
            val error = checkNotNull(finishPipeline()) { "故障停止必须返回原始失败" }
            if (consumerFails) {
                assertEquals("编码写入失败", error.message)
                assertEquals(1, accepts.get(), "失败帧不可重复写入")
                assertEquals(0, seals.get(), "失败编码不可冒充完整封存")
            } else {
                assertEquals("PCM_BACKPRESSURE", (error as com.echofold.capture.encoder.RecordingStorageException).code)
                assertEquals(4, reads.get(), "满队列后不可再读第五帧")
                assertEquals(4, accepts.get(), "故障前全部已接受帧必须消费")
                assertEquals(1, seals.get(), "排空后保存故障前的音频")
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            try {
                finishPipeline()
            } catch (cleanupError: Throwable) {
                val primary = primaryFailure
                if (primary != null) primary.addSuppressed(cleanupError) else throw cleanupError
            }
        }
    }
}
