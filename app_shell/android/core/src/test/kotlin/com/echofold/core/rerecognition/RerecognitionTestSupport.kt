package com.echofold.core.rerecognition

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.SourceKind
import com.echofold.core.model.SourceSet
import com.echofold.core.model.SourceVersionRef
import com.echofold.core.model.TranscriptVersion
import com.echofold.core.model.VersionKind
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** 重识别切片的测试夹具：固定会话、请求、FINAL 视图与记录型 fake。 */
internal object Fixtures {
    val MODEL = ModelRef(
        modelId = "x-asr-480-int8",
        modelVersion = "1.0.0",
        installationId = "install-0001",
        sha256 = "a".repeat(64),
    )
    val OTHER_MODEL = ModelRef(
        modelId = "x-asr-480-int8",
        modelVersion = "1.1.0",
        installationId = "install-0001",
        sha256 = "b".repeat(64),
    )

    const val SESSION: String = "sess-1"
    const val SEGMENT: String = "seg-1"
    const val CONFIG_HASH: String = "cfg-2026-11"
    const val NOW_UTC_MS: Long = 1_800_000_000_000L
    const val FINALIZED_AT_UTC_MS: Long = 1_700_000_000_000L

    /** 同步执行器：请求返回时任务已终结，便于确定性断言。 */
    val DIRECT_EXECUTOR: Executor = Executor { command -> command.run() }

    const val CAPTURE_START_MS: Long = 0L
    const val CAPTURE_END_MS: Long = 1_000L

    fun finalRef(finalId: String): SourceVersionRef = SourceVersionRef(finalId, finalId, SourceKind.FINAL)

    /** 文本段的权威来源集合：两个 FINAL，顺序即证据顺序。 */
    fun textSources(): SourceSet = SourceSet(listOf(finalRef("f1"), finalRef("f2")))

    fun request(
        segmentId: String = SEGMENT,
        sessionId: String = SESSION,
        sourceFinalIds: List<String> = listOf("f1", "f2"),
        capturedStartMs: Long = CAPTURE_START_MS,
        capturedEndMs: Long = CAPTURE_END_MS,
        chunkIds: List<String> = listOf("chunk-1", "chunk-2"),
        model: ModelRef = MODEL,
        asrConfigHash: String = CONFIG_HASH,
        generation: Long = 5L,
    ): RerecognitionRequest = RerecognitionRequest(
        segmentId = segmentId,
        sessionId = sessionId,
        sourceFinalIds = sourceFinalIds,
        capturedStartMs = capturedStartMs,
        capturedEndMs = capturedEndMs,
        chunkIds = chunkIds,
        model = model,
        asrConfigHash = asrConfigHash,
        generation = generation,
    )

    fun span(
        finalId: String = "final-new-1",
        text: String = "重识别文字",
        capturedStartMs: Long = CAPTURE_START_MS,
        capturedEndMs: Long = CAPTURE_END_MS,
        finalizedAtUtcMs: Long = FINALIZED_AT_UTC_MS,
        model: ModelRef = MODEL,
    ): RecognizedSpan = RecognizedSpan(
        finalId = finalId,
        text = text,
        capturedStartMs = capturedStartMs,
        capturedEndMs = capturedEndMs,
        finalizedAtUtcMs = finalizedAtUtcMs,
        modelId = model.modelId,
        modelVersion = model.modelVersion,
    )

    fun version(
        id: String,
        segmentId: String = SEGMENT,
        kind: VersionKind = VersionKind.ASR,
        text: String = "原始识别",
        parentVersionId: String? = null,
        finalizedAtUtcMs: Long = FINALIZED_AT_UTC_MS,
        createdAtUtcMs: Long = NOW_UTC_MS,
    ): TranscriptVersion = TranscriptVersion(
        id = id,
        segmentId = segmentId,
        kind = kind,
        text = text,
        parentVersionId = parentVersionId,
        modelId = MODEL.modelId,
        modelVersion = MODEL.modelVersion,
        finalizedAtUtcMs = finalizedAtUtcMs,
        createdAtUtcMs = createdAtUtcMs,
        captureStartMs = CAPTURE_START_MS,
        captureEndMs = CAPTURE_END_MS,
    )
}

/** 记录调用次数的假引擎：证明离线路径只接触注入引擎，不发生任何网络访问。 */
internal class RecordingEngine : AsrEngine {
    val initializeCalls = AtomicInteger()
    val acceptedFrames = AtomicInteger()
    val flushCalls = AtomicInteger()
    val closeCalls = AtomicInteger()

    override fun initialize(config: AsrConfig) {
        initializeCalls.incrementAndGet()
    }

    override fun acceptPcm(frame: PcmFrame) {
        acceptedFrames.incrementAndGet()
    }

    override fun flushSegment() {
        flushCalls.incrementAndGet()
    }

    override fun close() {
        closeCalls.incrementAndGet()
    }
}

/** 记录每次调用的假执行器实现。 */
internal class RecordingTranscriber(
    private val outcome: (RerecognitionRequest) -> List<RecognizedSpan>,
) : RerecognitionTranscriber {
    val calls = AtomicInteger()
    val engines: MutableList<AsrEngine> = CopyOnWriteArrayList()

    override fun transcribe(engine: AsrEngine, request: RerecognitionRequest): List<RecognizedSpan> {
        calls.incrementAndGet()
        engines += engine
        engine.flushSegment()
        return outcome(request)
    }
}

/** 记录折叠到期回调的假实现。 */
internal class RecordingFoldDueSink : FoldDueSink {
    data class Call(
        val segmentId: String,
        val sourceFinalIds: List<String>,
        val latestFinalizedAtUtcMs: Long,
    )

    val calls: MutableList<Call> = CopyOnWriteArrayList()

    override fun onNewFinal(segmentId: String, sourceFinalIds: List<String>, latestFinalizedAtUtcMs: Long) {
        calls += Call(segmentId, sourceFinalIds, latestFinalizedAtUtcMs)
    }
}
