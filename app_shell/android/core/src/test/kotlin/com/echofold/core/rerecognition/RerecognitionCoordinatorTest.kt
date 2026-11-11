package com.echofold.core.rerecognition

import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.model.SourceKind
import com.echofold.core.model.SourceSet
import com.echofold.core.model.SourceVersionRef
import com.echofold.core.model.TranscriptSegment
import com.echofold.core.model.TranscriptVersion
import com.echofold.core.model.VersionKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** [RerecognitionCoordinator] 的端到端行为：成功/失败/取消、幂等与并发、迟到与换源、GAP 折叠重建。 */
class RerecognitionCoordinatorTest {
    private val store = InMemoryTranscriptVersionStore()
    private val sink = RecordingFoldDueSink()
    private val engine = RecordingEngine()
    private val pool = Executors.newFixedThreadPool(4)

    @AfterEach
    fun tearDown() {
        pool.shutdownNow()
    }

    // ---- 成功路径 ----

    @Test
    fun `文本段成功新增 ASR 版本并切换默认且保留父版本`() {
        val parent = seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span(text = "重识别文字")) }
        val coordinator = coordinator(transcriber)

        val result = coordinator.request(Fixtures.request()).await()

        val success = assertInstanceOf(RerecognitionResult.Success::class.java, result)
        assertEquals("重识别文字", success.version.text)
        assertEquals(VersionKind.ASR, success.version.kind)
        assertEquals(parent.id, success.version.parentVersionId)
        assertEquals(Fixtures.MODEL.modelId, success.version.modelId)
        assertEquals(Fixtures.MODEL.modelVersion, success.version.modelVersion)
        assertEquals(Fixtures.CAPTURE_START_MS, success.version.captureStartMs)
        assertEquals(Fixtures.CAPTURE_END_MS, success.version.captureEndMs)
        assertEquals(success.version.id, store.currentDefault(Fixtures.SEGMENT)?.id)
        assertEquals(listOf(parent.id, success.version.id), store.versions(Fixtures.SEGMENT).map { it.id })
        assertEquals(parent, store.versions(Fixtures.SEGMENT).first())
        // 文本段的来源 FINAL 未变化：不得触发折叠到期重建。
        assertTrue(sink.calls.isEmpty())
        // 离线路径只使用注入引擎：同一实例、恰好冲刷一次、不初始化其他模型。
        assertSame(engine, transcriber.engines.single())
        assertEquals(1, engine.flushCalls.get())
        assertEquals(1, transcriber.calls.get())
    }

    @Test
    fun `成功后重复请求返回同一句柄且不再执行`() {
        seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span()) }
        val coordinator = coordinator(transcriber)

        val first = coordinator.request(Fixtures.request())
        val second = coordinator.request(Fixtures.request())

        assertSame(first, second)
        assertEquals(1, transcriber.calls.get())
        assertEquals(2, store.versions(Fixtures.SEGMENT).size)
        assertEquals(first.result, second.result)
    }

    @Test
    fun `成功后取消返回原有成功结果`() {
        seedTextSegment()
        val coordinator = coordinator(RecordingTranscriber { listOf(Fixtures.span()) })

        val handle = coordinator.request(Fixtures.request())

        assertInstanceOf(RerecognitionResult.Success::class.java, handle.result)
        assertEquals(handle.result, coordinator.cancel(handle))
        assertEquals(2, store.versions(Fixtures.SEGMENT).size)
    }

    // ---- 失败与校验 ----

    @Test
    fun `引擎异常时失败且保留原默认`() {
        val parent = seedTextSegment()
        val coordinator = coordinator(RecordingTranscriber { throw AsrEngineException("模型加载失败") })

        val result = coordinator.request(Fixtures.request()).await()

        assertEquals(
            RerecognitionFailure.ENGINE_FAILURE,
            assertInstanceOf(RerecognitionResult.Failed::class.java, result).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf(parent.id), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    @Test
    fun `结果区间越界时失败且保留原默认`() {
        val parent = seedTextSegment()
        val transcriber = RecordingTranscriber {
            listOf(Fixtures.span(capturedStartMs = 900, capturedEndMs = 1_500))
        }
        val coordinator = coordinator(transcriber)

        val result = coordinator.request(Fixtures.request()).await()

        assertEquals(
            RerecognitionFailure.RANGE_OUT_OF_BOUNDS,
            assertInstanceOf(RerecognitionResult.Failed::class.java, result).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `结果模型不一致时失败且不写入版本`() {
        val parent = seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span(model = Fixtures.OTHER_MODEL)) }
        val coordinator = coordinator(transcriber)

        val result = coordinator.request(Fixtures.request()).await()

        assertEquals(
            RerecognitionFailure.RESULT_MODEL_MISMATCH,
            assertInstanceOf(RerecognitionResult.Failed::class.java, result).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `无识别文字时失败且保留原默认`() {
        val parent = seedTextSegment()
        val coordinator = coordinator(RecordingTranscriber { emptyList() })

        val result = coordinator.request(Fixtures.request()).await()

        assertEquals(
            RerecognitionFailure.EMPTY_TEXT,
            assertInstanceOf(RerecognitionResult.Failed::class.java, result).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `结果顺序倒退或 FINAL 重复时失败`() {
        val parent = seedTextSegment()

        val overlapping = coordinator(
            RecordingTranscriber {
                listOf(
                    Fixtures.span(finalId = "final-a", capturedStartMs = 0, capturedEndMs = 500),
                    Fixtures.span(finalId = "final-b", capturedStartMs = 400, capturedEndMs = 900),
                )
            },
        )
        assertEquals(
            RerecognitionFailure.INVALID_RESULT,
            assertInstanceOf(
                RerecognitionResult.Failed::class.java,
                overlapping.request(Fixtures.request()).await(),
            ).reason,
        )

        val duplicated = coordinator(
            RecordingTranscriber {
                listOf(
                    Fixtures.span(finalId = "final-a", capturedStartMs = 0, capturedEndMs = 500),
                    Fixtures.span(finalId = "final-a", capturedStartMs = 500, capturedEndMs = 900),
                )
            },
        )
        assertEquals(
            RerecognitionFailure.INVALID_RESULT,
            assertInstanceOf(
                RerecognitionResult.Failed::class.java,
                duplicated.request(Fixtures.request()).await(),
            ).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    // ---- 取消 ----

    @Test
    fun `取消任务保留原默认并释放同段活动位`() {
        val parent = seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val handle = coordinator.request(Fixtures.request())
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")
        assertSame(handle, coordinator.activeTask(Fixtures.SEGMENT))

        val cancelled = coordinator.cancel(handle)

        assertEquals(RerecognitionResult.Cancelled(handle.key), cancelled)
        assertNull(coordinator.activeTask(Fixtures.SEGMENT))

        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")
        assertEquals(cancelled, handle.await(2_000))
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf(parent.id), store.versions(Fixtures.SEGMENT).map { it.id })

        // 取消后同段可以重新提交；成功提交只发生一次。
        val retry = coordinator.request(Fixtures.request()).await(2_000)
        assertInstanceOf(RerecognitionResult.Success::class.java, retry)
        assertEquals(2, store.versions(Fixtures.SEGMENT).size)
    }

    // ---- 幂等与并发 ----

    @Test
    fun `运行中重复请求返回同一句柄且只执行一次`() {
        seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val handle = coordinator.request(Fixtures.request())
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")
        val duplicate = coordinator.request(Fixtures.request())

        assertSame(handle, duplicate)
        assertEquals(1, transcriber.calls.get())

        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")
        assertInstanceOf(RerecognitionResult.Success::class.java, handle.await(2_000))
        assertSame(handle.result, duplicate.result)
        assertEquals(2, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `并发同段请求只执行一次并返回同一句柄`() {
        seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span()) }
        val coordinator = coordinator(transcriber)
        val threadCount = 8
        val barrier = CyclicBarrier(threadCount)
        val handles = arrayOfNulls<RerecognitionHandle>(threadCount)
        val threads = (0 until threadCount).map { index ->
            Thread {
                barrier.await(2, TimeUnit.SECONDS)
                handles[index] = coordinator.request(Fixtures.request())
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        val distinct = handles.filterNotNull().toSet()
        assertEquals(threadCount, handles.count { it != null })
        assertEquals(1, distinct.size, "并发同段请求必须收敛到同一个句柄")
        assertEquals(1, transcriber.calls.get())
        assertEquals(2, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `同段其他输入在运行时被拒绝`() {
        seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val running = coordinator.request(Fixtures.request())
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")

        val busy = coordinator.request(Fixtures.request(sourceFinalIds = listOf("f1")))

        assertEquals(
            RerecognitionRejection.SEGMENT_BUSY,
            assertInstanceOf(RerecognitionResult.Rejected::class.java, busy.await()).reason,
        )
        assertEquals(1, transcriber.calls.get())

        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")
        assertInstanceOf(RerecognitionResult.Success::class.java, running.await(2_000))
    }

    // ---- 迟到与换源 ----

    @Test
    fun `迟到世代结果不覆盖默认展示`() {
        val parent = seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val handle = coordinator.request(Fixtures.request(generation = 5))
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")
        val duplicate = coordinator.request(Fixtures.request(generation = 6))

        assertSame(handle, duplicate)
        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")

        assertEquals(
            RerecognitionRejection.LATE_GENERATION,
            assertInstanceOf(
                RerecognitionResult.Rejected::class.java,
                handle.await(2_000),
            ).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf(parent.id), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    @Test
    fun `换源后完成结果被拒绝`() {
        val parent = seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val handle = coordinator.request(Fixtures.request())
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")
        store.registerSourceSet(Fixtures.SEGMENT, SourceSet(listOf(Fixtures.finalRef("f1"))))
        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")

        assertEquals(
            RerecognitionRejection.SOURCE_SET_CHANGED,
            assertInstanceOf(
                RerecognitionResult.Rejected::class.java,
                handle.await(2_000),
            ).reason,
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `默认版本已被取代时结果被拒绝`() {
        val parent = seedTextSegment()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val transcriber = RecordingTranscriber {
            started.countDown()
            release.await()
            returned.countDown()
            listOf(Fixtures.span())
        }
        val coordinator = coordinator(transcriber, pool)

        val handle = coordinator.request(Fixtures.request())
        assertTrue(started.await(2, TimeUnit.SECONDS), "任务未开始执行")
        val edited = store.commit(
            Fixtures.version(id = "v-user", kind = VersionKind.USER, text = "用户编辑", parentVersionId = parent.id),
            Fixtures.textSources(),
        )
        release.countDown()
        assertTrue(returned.await(2, TimeUnit.SECONDS), "引擎侧未返回")

        assertEquals(
            RerecognitionRejection.SUPERSEDED,
            assertInstanceOf(
                RerecognitionResult.Rejected::class.java,
                handle.await(2_000),
            ).reason,
        )
        assertEquals(edited, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf(parent.id, edited.id), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    // ---- 会话固定模型 ----

    @Test
    fun `请求模型与会话固定模型不一致时拒绝且不切换模型`() {
        val parent = seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span()) }
        val coordinator = coordinator(transcriber)

        val result = coordinator.request(Fixtures.request(model = Fixtures.OTHER_MODEL)).await()

        assertEquals(
            RerecognitionRejection.SESSION_MODEL_MISMATCH,
            assertInstanceOf(RerecognitionResult.Rejected::class.java, result).reason,
        )
        assertEquals(0, transcriber.calls.get())
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `会话或配置哈希不一致时拒绝`() {
        seedTextSegment()
        val transcriber = RecordingTranscriber { listOf(Fixtures.span()) }
        val coordinator = coordinator(transcriber)

        val wrongSession = coordinator.request(Fixtures.request(sessionId = "sess-2")).await()
        val wrongConfig = coordinator.request(Fixtures.request(asrConfigHash = "cfg-other")).await()

        assertEquals(
            RerecognitionRejection.SESSION_MISMATCH,
            assertInstanceOf(RerecognitionResult.Rejected::class.java, wrongSession).reason,
        )
        assertEquals(
            RerecognitionRejection.SESSION_MODEL_MISMATCH,
            assertInstanceOf(RerecognitionResult.Rejected::class.java, wrongConfig).reason,
        )
        assertEquals(0, transcriber.calls.get())
    }

    // ---- GAP 折叠重建 ----

    @Test
    fun `GAP 重识别成功重建折叠到期并保留缺口证据`() {
        val gapSegment = "seg-gap"
        val gapVersionId = seedGapSegment(gapSegment)
        val transcriber = RecordingTranscriber {
            listOf(
                Fixtures.span(
                    finalId = "final-gap-1",
                    text = "被跳过区间的识别结果",
                    finalizedAtUtcMs = Fixtures.FINALIZED_AT_UTC_MS + 900,
                ),
            )
        }
        val coordinator = coordinator(transcriber)

        val result = coordinator.request(
            Fixtures.request(segmentId = gapSegment, sourceFinalIds = emptyList(), chunkIds = listOf("chunk-1")),
        ).await()

        val success = assertInstanceOf(RerecognitionResult.Success::class.java, result)
        assertEquals(VersionKind.ASR, success.version.kind)
        assertEquals("被跳过区间的识别结果", success.version.text)
        assertEquals(gapVersionId, success.version.parentVersionId)
        assertEquals(success.version.id, store.currentDefault(gapSegment)?.id)
        assertEquals(
            listOf(gapVersionId, success.version.id),
            store.versions(gapSegment).map { it.id },
        )
        assertEquals(
            RecordingFoldDueSink.Call(
                segmentId = gapSegment,
                sourceFinalIds = listOf("final-gap-1"),
                latestFinalizedAtUtcMs = Fixtures.FINALIZED_AT_UTC_MS + 900,
            ),
            sink.calls.single(),
        )
    }

    @Test
    fun `GAP 二次重识别以首个版本为证据并链到上次结果`() {
        val gapSegment = "seg-gap"
        val gapVersionId = seedGapSegment(gapSegment)
        val transcriber = RecordingTranscriber { request ->
            listOf(
                Fixtures.span(
                    finalId = "final-gap-${request.capturedEndMs}",
                    text = "缺口识别${request.capturedEndMs}",
                    finalizedAtUtcMs = Fixtures.FINALIZED_AT_UTC_MS + request.capturedEndMs,
                ),
            )
        }
        val coordinator = coordinator(transcriber)

        val first = assertInstanceOf(
            RerecognitionResult.Success::class.java,
            coordinator.request(
                Fixtures.request(segmentId = gapSegment, sourceFinalIds = emptyList(), chunkIds = listOf("chunk-1")),
            ).await(),
        )
        val second = assertInstanceOf(
            RerecognitionResult.Success::class.java,
            coordinator.request(
                Fixtures.request(
                    segmentId = gapSegment,
                    sourceFinalIds = emptyList(),
                    capturedEndMs = 1_001,
                    chunkIds = listOf("chunk-1"),
                    generation = 6,
                ),
            ).await(),
        )

        assertEquals(gapVersionId, first.version.parentVersionId)
        assertEquals(first.version.id, second.version.parentVersionId)
        assertEquals(
            listOf(gapVersionId, first.version.id, second.version.id),
            store.versions(gapSegment).map { it.id },
        )
        assertEquals(
            listOf(listOf("final-gap-1000"), listOf("final-gap-1001")),
            sink.calls.map { it.sourceFinalIds },
        )
        assertEquals(2, transcriber.calls.get())
    }

    @Test
    fun `无来源证据的段拒绝提交`() {
        val transcriber = RecordingTranscriber { listOf(Fixtures.span()) }
        val coordinator = coordinator(transcriber)
        val segment = "seg-gap-unregistered"

        val result = coordinator.request(
            Fixtures.request(segmentId = segment, sourceFinalIds = emptyList(), chunkIds = listOf("chunk-1")),
        ).await()

        assertEquals(
            RerecognitionRejection.MISSING_SOURCES,
            assertInstanceOf(RerecognitionResult.Rejected::class.java, result).reason,
        )
        assertEquals(emptyList<TranscriptVersion>(), store.versions(segment))
        assertTrue(sink.calls.isEmpty())
    }

    // ---- 离线约束 ----

    @Test
    fun `重识别包不引用任何网络客户端`() {
        val sourceDir = File("src/main/kotlin/com/echofold/core/rerecognition")
        assertTrue(sourceDir.isDirectory, "缺少源码目录：${sourceDir.absolutePath}")
        val forbidden = listOf(
            "java.net",
            "http://",
            "https://",
            "HttpURLConnection",
            "URLConnection",
            "okhttp",
            "OkHttp",
            "retrofit",
            "Retrofit",
            "InetAddress",
        )
        val offenders = mutableListOf<String>()
        sourceDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                forbidden.firstOrNull { token -> line.contains(token) }?.let { token ->
                    offenders += "${file.name}:${index + 1} 命中 $token"
                }
            }
        }

        assertEquals(emptyList<String>(), offenders, "重识别包只允许调用本地引擎")
    }

    private fun seedTextSegment(): TranscriptVersion {
        store.registerSourceSet(Fixtures.SEGMENT, Fixtures.textSources())
        return store.commit(Fixtures.version(id = "v-l0"), Fixtures.textSources())
    }

    private fun seedGapSegment(gapSegment: String): String {
        val gapVersionId = "v-gap-l0"
        val gapSources = SourceSet(listOf(SourceVersionRef(gapSegment, gapVersionId, SourceKind.GAP)))
        store.registerSourceSet(gapSegment, gapSources)
        store.commit(
            Fixtures.version(
                id = gapVersionId,
                segmentId = gapSegment,
                kind = VersionKind.GAP,
                text = TranscriptSegment.GAP_DISPLAY_TEXT,
            ),
            gapSources,
        )
        return gapVersionId
    }

    private fun coordinator(
        transcriber: RerecognitionTranscriber,
        executor: Executor = Fixtures.DIRECT_EXECUTOR,
    ): RerecognitionCoordinator = RerecognitionCoordinator(
        sessionId = Fixtures.SESSION,
        sessionModel = Fixtures.MODEL,
        sessionAsrConfigHash = Fixtures.CONFIG_HASH,
        engine = engine,
        transcriber = transcriber,
        store = store,
        foldDueSink = sink,
        clock = { Fixtures.NOW_UTC_MS },
        executor = executor,
    )
}
