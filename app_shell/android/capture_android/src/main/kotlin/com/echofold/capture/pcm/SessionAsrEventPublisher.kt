package com.echofold.capture.pcm

import com.echofold.core.asr.AsrEvent
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState

/** 将识别器的真实文字回调编码为有序、带音频来源的会话事件。 */
class SessionAsrEventPublisher(
    private val sessionId: String,
    private val model: ModelRef,
    private val chunksFor: (Long, Long) -> List<AudioChunk>,
    private val offer: (AsrEvent) -> Boolean,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {
    private var nextSeq = 0L
    private var latestFrame: PcmFrame? = null
    private var segmentStart: PcmFrame? = null
    private var lastPartial: String? = null

    @Synchronized
    fun onPcmFrame(frame: PcmFrame) {
        check(frame.sessionId == sessionId) { "ASR 事件 PCM 会话不匹配" }
        latestFrame = frame
        if (segmentStart == null) segmentStart = frame
    }

    @Synchronized
    fun partial(text: String) {
        if (text.isBlank() || text == lastPartial) return
        val frame = latestFrame ?: return
        val startMs = segmentStart!!.capturedStartMs
        val endMs = maxOf(startMs, frameEndMs(frame))
        val event = AsrEvent.Partial(
            sessionId = sessionId,
            generation = 0,
            seq = nextSeq,
            text = text,
            startMs = startMs,
            endMs = endMs,
            modelId = model.modelId,
            modelVersion = model.modelVersion,
        )
        if (offer(event)) {
            nextSeq++
            lastPartial = text
        }
    }

    @Synchronized
    fun final(text: String) {
        val frame = latestFrame ?: return
        if (text.isBlank()) {
            resetSegment()
            return
        }
        val startFrame = segmentStart ?: frame
        val startMs = startFrame.capturedStartMs
        val endMs = maxOf(startMs, frameEndMs(frame))
        val referencedChunks = chunksFor(startMs, endMs)
            .filter { chunk ->
                val chunkStart = chunk.capturedStartMs
                val chunkEnd = chunk.capturedEndMs
                when {
                    chunkStart != null && chunkEnd != null -> chunkStart <= endMs && chunkEnd >= startMs
                    chunk.state == ChunkState.PLACEHOLDER || chunk.state == ChunkState.OPEN -> true
                    else -> false
                }
            }
            .map(AudioChunk::id)
            .distinct()
        check(referencedChunks.isNotEmpty()) { "识别结果没有可引用的音频块：$startMs..$endMs" }
        val seq = nextSeq
        val event = AsrEvent.Final(
            sessionId = sessionId,
            generation = 0,
            seq = seq,
            finalId = "$sessionId:final:$seq",
            text = text,
            finalizedAtUtcMs = wallClockMs(),
            captureStartedMonoNs = startFrame.monoNs,
            captureEndedMonoNs = maxOf(startFrame.monoNs, frameEndMonoNs(frame)),
            capturedStartMs = startMs,
            capturedEndMs = endMs,
            chunkIds = referencedChunks,
        )
        val accepted = offer(event)
        // EventDelivery 返回 false 时保留唯一待发 FINAL；同步抛错终止该生产者。
        nextSeq++
        resetSegment()
        check(accepted) { "ASR 事件队列已满，FINAL 已暂存并停止会话" }
    }

    private fun resetSegment() {
        latestFrame = null
        segmentStart = null
        lastPartial = null
    }

    private fun frameEndMs(frame: PcmFrame): Long =
        frame.capturedStartMs + frame.length * 1000L / frame.sampleRate

    private fun frameEndMonoNs(frame: PcmFrame): Long =
        frame.monoNs + frame.length * 1_000_000_000L / frame.sampleRate
}
