package com.echofold.core.rerecognition

import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.model.SourceKind
import com.echofold.core.model.SourceSet
import com.echofold.core.model.SourceVersionRef
import com.echofold.core.model.TranscriptVersion
import com.echofold.core.model.VersionKind
import com.echofold.core.model.requireNotBlank
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 手动本地重识别的任务协调器。
 *
 * 行为约定（对应共享契约第 6 节与 speech-transcription 的“所有文本段可手动本地重识别”）：
 *
 * - **同段单活动任务**：同一段同一时刻只有一个活动任务；同键重复请求返回同一个句柄且只执行一次，
 *   不同键的新请求返回 [RerecognitionRejection.SEGMENT_BUSY]，调用方可先 [cancel] 再提交；
 * - **成功**：结果必须落在请求锁定的音频区间内、模型与会话固定模型一致且文字非空，随后新增
 *   `VersionKind.ASR` 版本（父版本 = 提交时的当前默认版本）并成为该段默认展示；旧版本全部保留；
 * - **失败/取消/校验失败**：不新增任何版本，默认展示与原 L0 证据不变；
 * - **迟到**：完成时若该段已观察到更新世代（[RerecognitionRejection.LATE_GENERATION]）或来源集合
 *   已变化（[RerecognitionRejection.SOURCE_SET_CHANGED]）或默认版本已被取代
 *   （[RerecognitionRejection.SUPERSEDED]），结果不写版本表，只以 [RerecognitionResult.Rejected] 回传；
 * - **模型固定**：请求的模型或配置哈希与会话固定值不一致时直接拒绝，绝不切换模型或改用其他版本；
 * - **完全离线**：只调用注入的 [engine]（经 [transcriber] 驱动）与本地版本表，不依赖任何网络类型。
 *
 * 并发模型：任务在注入的 [executor] 上串行执行，同一个 [AsrEngine] 实例因此不会被并发驱动；
 * 状态机、校验与提交事务都在同一把锁内完成，取消与提交互斥，迟到结果无法穿插进已终结的任务。
 */
class RerecognitionCoordinator(
    private val sessionId: String,
    private val sessionModel: ModelRef,
    private val sessionAsrConfigHash: String,
    private val engine: AsrEngine,
    private val transcriber: RerecognitionTranscriber,
    private val store: TranscriptVersionStore,
    private val foldDueSink: FoldDueSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: Executor = newRerecognitionExecutor(),
) {
    init {
        requireNotBlank(sessionId, "sessionId")
        requireNotBlank(sessionAsrConfigHash, "sessionAsrConfigHash")
    }

    private val stateLock = Any()

    /** 段 → 唯一活动任务。 */
    private val activeBySegment = HashMap<String, RerecognitionHandle>()

    /** 幂等键 → 已成功终结的句柄；成功后同键请求不再重复执行。 */
    private val succeededByKey = HashMap<RerecognitionKey, RerecognitionHandle>()

    /** 段 → 已观察到的最新跳读世代；用于判定迟到结果。 */
    private val observedGenerationBySegment = HashMap<String, Long>()

    /**
     * 提交（或复用）一次重识别任务。
     *
     * 请求级别的拒绝（会话/模型/配置不一致、同段其他任务在跑）同步返回已终结的句柄；
     * 其余情况返回的任务句柄在 [executor] 上执行，等待 [RerecognitionHandle.await] 即可。
     */
    fun request(request: RerecognitionRequest): RerecognitionHandle {
        val key = RerecognitionKey.of(request)
        var submit = false
        val handle = synchronized(stateLock) {
            val rejection = requestRejection(request)
            if (rejection != null) {
                rejected(request, key, rejection)
            } else {
                observeGeneration(request)
                val active = activeBySegment[request.segmentId]
                val succeeded = succeededByKey[key]
                when {
                    active != null && active.key == key -> active
                    active != null -> rejected(request, key, RerecognitionRejection.SEGMENT_BUSY)
                    succeeded != null -> succeeded
                    else -> {
                        val fresh = RerecognitionHandle(
                            key = key,
                            segmentId = request.segmentId,
                            generation = request.generation,
                            // 请求时锁定默认版本作为父版本：之后被取代时提交事务会拒绝，避免覆盖新选择。
                            baseVersionId = store.currentDefault(request.segmentId)?.id,
                        )
                        activeBySegment[request.segmentId] = fresh
                        submit = true
                        fresh
                    }
                }
            }
        }
        if (submit) {
            try {
                executor.execute { runTask(handle, request) }
            } catch (e: RejectedExecutionException) {
                synchronized(stateLock) {
                    completeIfPending(handle, RerecognitionResult.Rejected(key, RerecognitionRejection.DISPATCH_FAILED))
                }
            }
        }
        return handle
    }

    /**
     * 取消一个尚未终结的任务。
     *
     * 已经终结的句柄返回其原有结果（取消不会改写成功或失败）。取消立即终结句柄并释放该段的活动位，
     * 之后才返回的识别结果会被丢弃，既不写版本也不改变默认展示。
     */
    fun cancel(handle: RerecognitionHandle): RerecognitionResult = synchronized(stateLock) {
        handle.result ?: run {
            val cancelled = RerecognitionResult.Cancelled(handle.key)
            completeIfPending(handle, cancelled)
            cancelled
        }
    }

    /** 该段当前的活动任务；无活动任务时为 `null`（用于诊断与“同段单任务”断言）。 */
    fun activeTask(segmentId: String): RerecognitionHandle? = synchronized(stateLock) {
        activeBySegment[segmentId]
    }

    /** 请求级校验：会话、固定模型与配置哈希必须与本协调器一致。 */
    private fun requestRejection(request: RerecognitionRequest): RerecognitionRejection? = when {
        request.sessionId != sessionId -> RerecognitionRejection.SESSION_MISMATCH
        request.model != sessionModel -> RerecognitionRejection.SESSION_MODEL_MISMATCH
        request.asrConfigHash != sessionAsrConfigHash -> RerecognitionRejection.SESSION_MODEL_MISMATCH
        else -> null
    }

    /** 只推进该段观察到的最新世代，不因复用句柄而回退。 */
    private fun observeGeneration(request: RerecognitionRequest) {
        val observed = observedGenerationBySegment[request.segmentId]
        if (observed == null || request.generation > observed) {
            observedGenerationBySegment[request.segmentId] = request.generation
        }
    }

    /** 在 [executor] 上执行一次任务；引擎失败与结果校验都由 [evaluate] 收敛成终结结果。 */
    private fun runTask(handle: RerecognitionHandle, request: RerecognitionRequest) {
        val spans = try {
            transcriber.transcribe(engine, request)
        } catch (e: AsrEngineException) {
            finishAsFailure(handle, RerecognitionFailure.ENGINE_FAILURE)
            return
        } catch (e: Exception) {
            // 执行器内的任何异常都视为本次识别失败：不写版本，默认展示保持原样。
            finishAsFailure(handle, RerecognitionFailure.ENGINE_FAILURE)
            return
        }

        synchronized(stateLock) {
            // 已取消或已终结：迟到结果一律丢弃，绝不覆盖默认展示。
            if (handle.isDone) return
            completeIfPending(handle, evaluate(handle, request, spans))
        }
    }

    /** 校验 + 提交 + 折叠到期回调，整体在状态锁内完成，保证取消与提交互斥。 */
    private fun evaluate(
        handle: RerecognitionHandle,
        request: RerecognitionRequest,
        spans: List<RecognizedSpan>,
    ): RerecognitionResult {
        val observed = observedGenerationBySegment[request.segmentId]
        if (observed != null && handle.generation < observed) {
            return RerecognitionResult.Rejected(handle.key, RerecognitionRejection.LATE_GENERATION)
        }

        validateSpans(request, spans)?.let { failure ->
            return RerecognitionResult.Failed(handle.key, failure)
        }

        return try {
            commitValidated(handle, request, spans)
        } catch (e: VersionConflictException) {
            when (e.conflict) {
                VersionConflict.PARENT_MISMATCH, VersionConflict.DUPLICATE_VERSION ->
                    RerecognitionResult.Rejected(handle.key, RerecognitionRejection.SUPERSEDED)
                VersionConflict.SOURCE_SET_NOT_REGISTERED, VersionConflict.SOURCE_MISMATCH ->
                    RerecognitionResult.Rejected(handle.key, RerecognitionRejection.SOURCE_SET_CHANGED)
            }
        } catch (e: RuntimeException) {
            RerecognitionResult.Failed(handle.key, RerecognitionFailure.STORE_FAILURE)
        }
    }

    /**
     * 结果校验：区间必须落在请求区间内且按采集顺序不重叠，模型必须是会话固定模型。
     *
     * 空结果或空白文字一律不得成为版本；FINAL ID 重复会使来源集合失去身份，故同样拒绝。
     */
    private fun validateSpans(
        request: RerecognitionRequest,
        spans: List<RecognizedSpan>,
    ): RerecognitionFailure? {
        if (spans.isEmpty()) return RerecognitionFailure.EMPTY_TEXT
        val seenFinalIds = HashSet<String>(spans.size)
        var previousEndMs = request.capturedStartMs
        for (span in spans) {
            if (span.capturedStartMs < request.capturedStartMs || span.capturedEndMs > request.capturedEndMs) {
                return RerecognitionFailure.RANGE_OUT_OF_BOUNDS
            }
            if (span.capturedStartMs < previousEndMs) return RerecognitionFailure.INVALID_RESULT
            if (span.modelId != sessionModel.modelId || span.modelVersion != sessionModel.modelVersion) {
                return RerecognitionFailure.RESULT_MODEL_MISMATCH
            }
            if (!seenFinalIds.add(span.finalId)) return RerecognitionFailure.INVALID_RESULT
            previousEndMs = span.capturedEndMs
        }
        return null
    }

    /**
     * 构造新版本并原子提交；成功后仅在来源 FINAL 集合被重建（GAP 段）时回调折叠到期重建。
     *
     * 父版本取请求时锁定的 [RerecognitionHandle.baseVersionId]，由 [TranscriptVersionStore.commit]
     * 校验它仍是当前默认版本；期间被取代时提交被拒，结果只以
     * [RerecognitionRejection.SUPERSEDED] 返回。
     */
    private fun commitValidated(
        handle: RerecognitionHandle,
        request: RerecognitionRequest,
        spans: List<RecognizedSpan>,
    ): RerecognitionResult {
        val expectedSources = expectedSources(request)
            ?: return RerecognitionResult.Rejected(handle.key, RerecognitionRejection.MISSING_SOURCES)

        val version = TranscriptVersion(
            id = VERSION_ID_PREFIX + handle.key.stableId,
            segmentId = request.segmentId,
            kind = VersionKind.ASR,
            text = spans.joinToString(separator = "") { it.text },
            parentVersionId = handle.baseVersionId,
            modelId = sessionModel.modelId,
            modelVersion = sessionModel.modelVersion,
            finalizedAtUtcMs = spans.maxOf { it.finalizedAtUtcMs },
            createdAtUtcMs = clock(),
            captureStartMs = request.capturedStartMs,
            captureEndMs = request.capturedEndMs,
        )
        val committed = store.commit(version, expectedSources)
        if (request.sourceFinalIds.isEmpty()) {
            notifyFoldDue(request.segmentId, spans)
        }
        return RerecognitionResult.Success(handle.key, committed)
    }

    /**
     * 由请求构造提交时声明的有序来源集合。
     *
     * 文本段：按请求顺序把每个来源 FINAL 映射为 `SourceVersionRef(finalId, finalId, FINAL)`。
     * GAP 段：来源是 GAP 自身的不可变 L0 证据版本。版本表只追加，因此该段首个提交版本就是 L0 证据；
     * 后续重识别的默认版本已是 ASR 版本，但来源仍是这个 L0 GAP。
     * 没有任何证据版本时返回 `null`，由调用方以 [RerecognitionRejection.MISSING_SOURCES] 拒绝。
     */
    private fun expectedSources(request: RerecognitionRequest): SourceSet? {
        if (request.sourceFinalIds.isNotEmpty()) {
            return SourceSet(
                request.sourceFinalIds.map { finalId ->
                    SourceVersionRef(finalId, finalId, SourceKind.FINAL)
                },
            )
        }
        val evidence = store.versions(request.segmentId).firstOrNull() ?: return null
        return SourceSet(listOf(SourceVersionRef(request.segmentId, evidence.id, SourceKind.GAP)))
    }

    /** 回调折叠到期重建；回调整体是派生算术，抛异常不得回滚已提交版本或吞掉任务结果。 */
    private fun notifyFoldDue(segmentId: String, spans: List<RecognizedSpan>) {
        try {
            foldDueSink.onNewFinal(
                segmentId = segmentId,
                sourceFinalIds = spans.map { it.finalId },
                latestFinalizedAtUtcMs = spans.maxOf { it.finalizedAtUtcMs },
            )
        } catch (e: RuntimeException) {
            // 版本已提交且已切换默认；折叠计时属于下游派生状态，由宿主下次重建。
        }
    }

    /** 引擎侧失败同样只在未终结时生效，取消后的失败不会改写 [RerecognitionResult.Cancelled]。 */
    private fun finishAsFailure(handle: RerecognitionHandle, failure: RerecognitionFailure) {
        synchronized(stateLock) {
            completeIfPending(handle, RerecognitionResult.Failed(handle.key, failure))
        }
    }

    /** 写入终结结果并维护活动表与成功缓存；调用方必须持有 [stateLock]。 */
    private fun completeIfPending(handle: RerecognitionHandle, result: RerecognitionResult) {
        if (!handle.complete(result)) return
        if (result is RerecognitionResult.Success) {
            succeededByKey[result.key] = handle
        }
        if (activeBySegment[handle.segmentId] === handle) {
            activeBySegment.remove(handle.segmentId)
        }
    }

    /** 构造已终结的拒绝句柄；请求级拒绝不占用该段的活动位，也不进入成功缓存。 */
    private fun rejected(
        request: RerecognitionRequest,
        key: RerecognitionKey,
        rejection: RerecognitionRejection,
    ): RerecognitionHandle {
        val handle = RerecognitionHandle(
            key = key,
            segmentId = request.segmentId,
            generation = request.generation,
            baseVersionId = null,
        )
        handle.complete(RerecognitionResult.Rejected(key, rejection))
        return handle
    }

    private companion object {
        /** 重识别版本 ID 前缀；后接幂等键的稳定哈希，使崩溃恢复后的重试得到同一 ID。 */
        const val VERSION_ID_PREFIX: String = "asr-"
    }
}

/** 串行执行重识别任务的后台线程；任务为离线低优先级工作，daemon 线程随进程结束。 */
private fun newRerecognitionExecutor(): Executor =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "echofold-rerecognition").apply { isDaemon = true }
    }
