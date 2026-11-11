package com.echofold.capture.source

/**
 * AudioRecord 采集封装。
 *
 * 职责与约束：
 * - 打开时按候选采样率探测，优先 48kHz、单声道、PCM16，实际上报设备选中值；
 * - 每次读取记录单调递增的 `frameIndex` 与 `monoNs`（设备时间戳优先，缺失时用注入时钟）；
 * - `capturedStartMs` 只按**实际读到的帧数**累计，因此暂停间隙不会计入有效音频时间轴；
 * - 暂停（[pause]）与 `BLOCKED`（[block]）是两个独立门：前者是用户可见的暂停，
 *   后者是保存保护，两者都会让 [read] 立即返回而不推进时间轴；
 * - 读取失败是粘性的（进入 `FAULTED`），调用方必须关闭后按顺序重新打开；
 * - [close] 幂等，重复调用不会重复释放平台资源。
 *
 * 本类不引用任何 `android.*` 类型，可直接在 JVM 单测中用 fake 句柄验证全部逻辑。
 */
class AudioRecordSource(
    private val factory: AudioRecordFactory,
    private val config: AudioRecordConfig = AudioRecordConfig(),
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() },
) {
    private enum class State { NEW, RECORDING, PAUSED, BLOCKED, FAULTED, CLOSED }

    private var state: State = State.NEW
    private var handle: AudioRecordHandle? = null
    private var selectedSampleRate: Int = config.primarySampleRate

    /** 有效音频帧累计（不含暂停与 BLOCKED 期间）。 */
    var capturedFrames: Long = 0L
        private set

    /** 读取循环序号；每次有效读取（`read > 0`）自增。 */
    var frameIndex: Long = 0L
        private set

    /** 设备时间戳断裂次数（音频不丢弃，只做诊断计数）。 */
    var timestampDiscontinuityCount: Long = 0L
        private set

    /** 设备实际采样率；未打开时为配置首选值。 */
    val sampleRate: Int get() = selectedSampleRate

    /** 句柄是否仍被占用。 */
    val isOpen: Boolean
        get() = state != State.NEW && state != State.CLOSED

    private var lastTimestampMonoNs: Long? = null
    private var resumeStateAfterBlock: State = State.RECORDING

    /**
     * 打开并开始采集。
     *
     * 已打开时返回当前实际参数（幂等）；已关闭时返回 [AudioCaptureError.READ_FAILED]。
     */
    fun open(): SourceOpenResult {
        when (state) {
            State.RECORDING, State.PAUSED, State.BLOCKED ->
                return SourceOpenResult.Opened(selectedSampleRate, config.channelCount)

            State.CLOSED -> return SourceOpenResult.Failed(
                CaptureFailure(AudioCaptureError.READ_FAILED, "采集器已关闭，不能重新打开"),
            )

            State.NEW -> Unit

            State.FAULTED -> {
                // 失效句柄必须先释放，避免重新打开时泄漏平台资源。
                handle?.let { releaseQuietly(it) }
                handle = null
            }
        }

        val created = try {
            factory.create(config)
        } catch (error: SecurityException) {
            return permissionFailure(error)
        } catch (error: UnsupportedOperationException) {
            return unsupportedFailure(error)
        } catch (error: IllegalArgumentException) {
            return unsupportedFailure(error)
        } catch (error: IllegalStateException) {
            return SourceOpenResult.Failed(
                CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "麦克风被其他应用占用", error),
            )
        } catch (error: RuntimeException) {
            return SourceOpenResult.Failed(
                CaptureFailure(AudioCaptureError.READ_FAILED, "创建 AudioRecord 失败", error),
            )
        }

        try {
            created.start()
        } catch (error: SecurityException) {
            releaseQuietly(created)
            return permissionFailure(error)
        } catch (error: IllegalStateException) {
            releaseQuietly(created)
            return SourceOpenResult.Failed(
                CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "AudioRecord 启动失败：设备被占用", error),
            )
        } catch (error: RuntimeException) {
            releaseQuietly(created)
            return SourceOpenResult.Failed(
                CaptureFailure(AudioCaptureError.READ_FAILED, "AudioRecord 启动失败", error),
            )
        }

        handle = created
        selectedSampleRate = created.sampleRate
        state = State.RECORDING
        lastTimestampMonoNs = null
        return SourceOpenResult.Opened(selectedSampleRate, created.channelCount)
    }

    /**
     * 读取一批 PCM。
     *
     * 调用方提供复用缓冲区；返回的 `frames` 只是本次读到的帧数，时间轴推进由本类负责。
     */
    fun read(
        destination: ShortArray,
        offset: Int = 0,
        length: Int = destination.size,
    ): FrameReadResult {
        require(offset >= 0 && length > 0 && offset + length <= destination.size) {
            "非法的读取视图：offset=$offset length=$length size=${destination.size}"
        }
        when (state) {
            State.NEW -> return FrameReadResult.NotOpened
            State.CLOSED -> return FrameReadResult.Failed(
                CaptureFailure(AudioCaptureError.READ_FAILED, "采集器已关闭"),
            )

            State.PAUSED -> return FrameReadResult.Paused
            State.BLOCKED -> return FrameReadResult.Blocked
            State.FAULTED -> return FrameReadResult.Failed(lastFailure)
            State.RECORDING -> Unit
        }

        val active = handle ?: return FrameReadResult.Failed(
            CaptureFailure(AudioCaptureError.READ_FAILED, "采集句柄缺失"),
        )

        val readFrames: Int = try {
            active.read(destination, offset, length)
        } catch (error: SecurityException) {
            return fault(permissionFailure(error).failure)
        } catch (error: IllegalStateException) {
            return fault(CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "AudioRecord 读取时设备不可用", error))
        } catch (error: RuntimeException) {
            return fault(CaptureFailure(AudioCaptureError.READ_FAILED, "AudioRecord 读取失败", error))
        }

        if (readFrames < 0) {
            return fault(classifyReadError(readFrames))
        }
        if (readFrames == 0) {
            // 非阻塞读取暂无数据：不推进 frameIndex/时间轴，也不是错误。
            return FrameReadResult.Read(
                frames = 0,
                frameIndex = frameIndex,
                firstMonoNs = lastTimestampMonoNs ?: clock.nanoTime(),
                capturedStartMs = capturedMillis(),
                timestampDiscontinuity = false,
            )
        }

        val capturedStartMs = capturedMillis()
        val deviceStamp = active.latestTimestampMonoNs()
        val firstMonoNs = deviceStamp ?: clock.nanoTime()
        val discontinuity = isDiscontinuity(deviceStamp, readFrames)
        if (discontinuity) {
            timestampDiscontinuityCount++
        }
        lastTimestampMonoNs = firstMonoNs
        capturedFrames += readFrames
        val index = frameIndex
        frameIndex++

        return FrameReadResult.Read(
            frames = readFrames,
            frameIndex = index,
            firstMonoNs = firstMonoNs,
            capturedStartMs = capturedStartMs,
            timestampDiscontinuity = discontinuity,
        )
    }

    /** 用户暂停：停止录音并冻结时间轴；重复调用安全。 */
    fun pause() {
        if (state != State.RECORDING) return
        handle?.let { stopQuietly(it) }
        state = State.PAUSED
    }

    /** 用户继续：重新 start()，时间轴从暂停前的位置继续（暂停时长不计入）。 */
    fun resume() {
        if (state != State.PAUSED) return
        val active = handle ?: return
        try {
            active.start()
        } catch (error: SecurityException) {
            fault(permissionFailure(error).failure)
            return
        } catch (error: IllegalStateException) {
            fault(CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "恢复采集失败：设备不可用", error))
            return
        } catch (error: RuntimeException) {
            fault(CaptureFailure(AudioCaptureError.READ_FAILED, "恢复采集失败", error))
            return
        }
        state = State.RECORDING
    }

    /**
     * 进入 BLOCKED 门：立即阻止新的读取。
     *
     * 保存保护要求“停止读取新 PCM”但保留已接受的序号，因此这里只是关门，
     * 不释放句柄；恢复必须走 [unblock]（在块占位完成后）。
     */
    fun block() {
        when (state) {
            State.RECORDING -> {
                handle?.let { stopQuietly(it) }
                resumeStateAfterBlock = State.RECORDING
                state = State.BLOCKED
            }

            State.PAUSED -> {
                resumeStateAfterBlock = State.PAUSED
                state = State.BLOCKED
            }

            else -> Unit
        }
    }

    /** 退出 BLOCKED 门，回到进入前的状态。 */
    fun unblock() {
        if (state != State.BLOCKED) return
        if (resumeStateAfterBlock == State.PAUSED) {
            state = State.PAUSED
            return
        }
        val active = handle ?: run {
            state = State.FAULTED
            return
        }
        try {
            active.start()
        } catch (error: SecurityException) {
            fault(permissionFailure(error).failure)
            return
        } catch (error: IllegalStateException) {
            fault(CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "解除 BLOCKED 失败：设备不可用", error))
            return
        } catch (error: RuntimeException) {
            fault(CaptureFailure(AudioCaptureError.READ_FAILED, "解除 BLOCKED 失败", error))
            return
        }
        state = State.RECORDING
    }

    /** 释放资源；幂等。 */
    fun close() {
        val active = handle
        if (state == State.CLOSED) return
        if (active != null) {
            stopQuietly(active)
            releaseQuietly(active)
        }
        handle = null
        state = State.CLOSED
    }

    private var lastFailure: CaptureFailure =
        CaptureFailure(AudioCaptureError.READ_FAILED, "采集器尚未打开")

    private fun capturedMillis(): Long = capturedFrames * 1_000L / selectedSampleRate

    /**
     * 时间戳断裂判定：设备时间戳相对上一批的跨度远大于该批音频应有跨度
     * （4 倍音频跨度 + 100ms 余量）时视为断裂，只做标记，不丢弃音频。
     */
    private fun isDiscontinuity(deviceStamp: Long?, readFrames: Int): Boolean {
        if (deviceStamp == null) return false
        val previous = lastTimestampMonoNs ?: return false
        val audioSpanNs = readFrames * 1_000_000_000L / selectedSampleRate
        val allowedSpanNs = audioSpanNs * 4 + DISCONTINUITY_SLACK_NS
        return deviceStamp - previous > allowedSpanNs
    }

    private fun fault(failure: CaptureFailure): FrameReadResult {
        lastFailure = failure
        val active = handle
        if (active != null) {
            stopQuietly(active)
        }
        state = State.FAULTED
        return FrameReadResult.Failed(failure)
    }

    private fun permissionFailure(error: Throwable): SourceOpenResult.Failed =
        SourceOpenResult.Failed(
            CaptureFailure(AudioCaptureError.MIC_PERMISSION_DENIED, "缺少麦克风权限", error),
        )

    private fun unsupportedFailure(error: Throwable): SourceOpenResult.Failed =
        SourceOpenResult.Failed(
            CaptureFailure(
                AudioCaptureError.UNSUPPORTED_CONFIGURATION,
                "设备不支持候选采集配置：${config.preferredSampleRates}",
                error,
            ),
        )

    private fun classifyReadError(code: Int): CaptureFailure = when (code) {
        READ_ERROR_DEAD_OBJECT ->
            CaptureFailure(AudioCaptureError.DEVICE_IN_USE, "音频设备已断开或被占用（错误码 $code）")

        READ_ERROR_INVALID_OPERATION, READ_ERROR_BAD_VALUE ->
            CaptureFailure(AudioCaptureError.READ_FAILED, "AudioRecord 读取参数非法或状态错误（错误码 $code）")

        else -> CaptureFailure(AudioCaptureError.READ_FAILED, "AudioRecord 读取失败（错误码 $code）")
    }

    private fun stopQuietly(active: AudioRecordHandle) {
        try {
            active.stop()
        } catch (_: RuntimeException) {
            // 停止失败不影响后续释放；错误由 release 与上层状态机处理。
        }
    }

    private fun releaseQuietly(active: AudioRecordHandle) {
        try {
            active.release()
        } catch (_: RuntimeException) {
            // 释放是最后一步，平台异常不应把失败态覆盖成成功态。
        }
    }

    private companion object {
        /** 与 `AudioRecord.ERROR_INVALID_OPERATION` 对齐。 */
        const val READ_ERROR_INVALID_OPERATION: Int = -3

        /** 与 `AudioRecord.ERROR_BAD_VALUE` 对齐。 */
        const val READ_ERROR_BAD_VALUE: Int = -2

        /** 与 `AudioRecord.ERROR_DEAD_OBJECT` 对齐。 */
        const val READ_ERROR_DEAD_OBJECT: Int = -6

        /** 时间戳断裂判定余量。 */
        const val DISCONTINUITY_SLACK_NS: Long = 100_000_000L
    }
}
