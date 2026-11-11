package com.echofold.capture.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * `MediaCodecList` 能力探测。
 *
 * 只返回真实支持 AAC-LC 编码的编码器：`supportedTypes` 命中 MIME，且
 * `profileLevels` 里包含 `AACObjectLC`。找不到返回 null，由 [AacChunkEncoder] 失败，
 * 不静默换用其它编码器或远端服务。
 */
class MediaCodecCapabilitySource : EncoderCapabilitySource {

    override fun probe(mimeType: String): EncoderCaps? {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mimeType, ignoreCase = true) }) continue
            val caps = try {
                info.getCapabilitiesForType(mimeType)
            } catch (_: IllegalArgumentException) {
                continue
            }
            val audio = caps.audioCapabilities
            val sampleRates = audio.supportedSampleRates.toSet()
            if (sampleRates.isEmpty()) continue
            val supportsAacLc = caps.profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.AACObjectLC
            }
            val platformRange = audio.bitrateRange
            return EncoderCaps(
                codecName = info.name,
                mimeType = mimeType,
                supportedSampleRates = sampleRates,
                bitRateRange = platformRange.lower..platformRange.upper,
                maxChannelCount = audio.maxInputChannelCount,
                supportsAacLc = supportsAacLc,
            )
        }
        return null
    }
}

/**
 * `MediaCodec` AAC 编码器包装。
 *
 * 关键点：输入缓冲按小端 PCM16 直接写入；输出格式变更时保存实际
 * `sampleRate/bitRate/channelCount/csd-0`，用于建轨与“不虚标”诊断。
 */
class MediaCodecAacEncoder : AudioEncoderCodec {

    private var codec: MediaCodec? = null
    private var requestedFormat: AudioEncoderFormat? = null
    private var outputFormat: AudioEncoderFormat? = null
    private var lastPresentationTimeUs: Long = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    override val codecName: String get() = codec?.name ?: ""

    override fun configure(format: AudioEncoderFormat) {
        val mediaFormat = MediaFormat.createAudioFormat(
            format.mimeType,
            format.sampleRate,
            format.channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, format.bitRate)
            if (format.aacProfile == AacProfile.LC) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            // 250ms 的单声道 PCM16 输入容量，足以吸收读取抖动。
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, format.sampleRate / 4 * 2)
        }
        val created = MediaCodec.createEncoderByType(format.mimeType)
        created.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec = created
        requestedFormat = format
    }

    override fun start() {
        requireCodec().start()
    }

    override fun queueInput(samples: ShortArray, offset: Int, length: Int, presentationTimeUs: Long) {
        val active = requireCodec()
        var index = -1
        for (attempt in 0 until INPUT_RETRY_COUNT) {
            index = active.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index >= 0) break
        }
        if (index < 0) {
            throw EncoderCodecException("编码器输入缓冲区持续不可用")
        }
        val buffer = active.getInputBuffer(index)
            ?: throw EncoderCodecException("编码器输入缓冲区为空")
        buffer.clear()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = buffer.asShortBuffer()
        shorts.put(samples, offset, length)
        active.queueInputBuffer(index, 0, length * BYTES_PER_SAMPLE, presentationTimeUs, 0)
        lastPresentationTimeUs = presentationTimeUs
    }

    override fun dequeueOutput(): EncodedBuffer? {
        val active = requireCodec()
        while (true) {
            val index = active.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return null

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = readFormat(active.outputFormat)
                }

                index >= 0 -> {
                    val buffer = active.getOutputBuffer(index)
                    val size = bufferInfo.size
                    val data = ByteArray(size)
                    if (buffer != null && size > 0) {
                        buffer.position(bufferInfo.offset)
                        buffer.get(data, 0, size)
                    }
                    val flags = bufferInfo.flags
                    active.releaseOutputBuffer(index, false)
                    return EncodedBuffer(
                        data = data,
                        offset = 0,
                        size = size,
                        presentationTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L),
                        flags = flags,
                        isCodecConfig = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0,
                    )
                }

                else -> return null
            }
        }
    }

    override fun signalEndOfStream() {
        val active = requireCodec()
        var index = -1
        for (attempt in 0 until INPUT_RETRY_COUNT) {
            index = active.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index >= 0) break
        }
        if (index < 0) {
            throw EncoderCodecException("无可用输入缓冲区以发送 EOS")
        }
        active.queueInputBuffer(
            index,
            0,
            0,
            lastPresentationTimeUs,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
        )
    }

    override fun actualFormat(): AudioEncoderFormat? = outputFormat ?: requestedFormat

    override fun release() {
        val active = codec ?: return
        codec = null
        runCatching { active.stop() }
        active.release()
    }

    private fun readFormat(mediaFormat: MediaFormat): AudioEncoderFormat {
        val requested = requestedFormat
            ?: throw EncoderCodecException("未配置编码格式就收到输出格式")
        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val bitRate = if (mediaFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE)
        } else {
            requested.bitRate
        }
        val csd = if (mediaFormat.containsKey(CSD_0)) {
            mediaFormat.getByteBuffer(CSD_0)?.let { buffer ->
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
        } else {
            null
        }
        return AudioEncoderFormat(
            mimeType = mediaFormat.getString(MediaFormat.KEY_MIME) ?: requested.mimeType,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitRate = bitRate,
            aacProfile = requested.aacProfile,
            codecSpecificData = csd,
        )
    }

    private fun requireCodec(): MediaCodec = codec ?: throw EncoderCodecException("编码器尚未配置")

    private companion object {
        const val CSD_0: String = "csd-0"
        const val BYTES_PER_SAMPLE: Int = 2
        const val INPUT_TIMEOUT_US: Long = 10_000L
        const val OUTPUT_TIMEOUT_US: Long = 10_000L
        const val INPUT_RETRY_COUNT: Int = 100
    }
}

/**
 * `MediaMuxer` M4A 封装器。
 *
 * 首个样本到达时用编码器实际格式建轨并启动封装器；[finalizeAndClose] 先 stop 再 release，
 * 保证 moov 索引落盘。异常上抛由 [AacChunkEncoder] 转成 `FINALIZE` 阶段失败。
 */
class MediaMuxerContainerWriter : AudioContainerWriter {

    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var started: Boolean = false
    private val bufferInfo = MediaCodec.BufferInfo()

    override fun open(path: String) {
        muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun writeSample(buffer: EncodedBuffer, format: AudioEncoderFormat) {
        val active = muxer ?: throw EncoderCodecException("封装器尚未创建")
        if (!started) {
            trackIndex = active.addTrack(format.toMediaFormat())
            active.start()
            started = true
        }
        bufferInfo.set(0, buffer.size, buffer.presentationTimeUs, buffer.flags)
        active.writeSampleData(trackIndex, ByteBuffer.wrap(buffer.data, buffer.offset, buffer.size), bufferInfo)
    }

    override fun finalizeAndClose() {
        val active = muxer ?: throw EncoderCodecException("封装器尚未创建")
        if (!started) {
            throw EncoderCodecException("没有任何编码样本，无法收尾 M4A")
        }
        active.stop()
        active.release()
        muxer = null
        started = false
        trackIndex = -1
    }

    override fun abort() {
        val active = muxer ?: return
        muxer = null
        started = false
        trackIndex = -1
        runCatching { active.stop() }
        runCatching { active.release() }
    }
}

/** 把采集侧格式转成 `MediaFormat`（AAC 需要把 csd-0 作为 ByteBuffer 写入）。 */
internal fun AudioEncoderFormat.toMediaFormat(): MediaFormat {
    val mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
    if (aacProfile == AacProfile.LC) {
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
    }
    codecSpecificData?.let { csd ->
        mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd))
    }
    return mediaFormat
}

/** 平台编码/封装调用失败的内部异常；由 [AacChunkEncoder] 归类到具体阶段。 */
class EncoderCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 生产用的块编码器工厂：每次 `create()` 都构造一套全新的 `MediaCodec` + `MediaMuxer`。
 *
 * 路径由块占位协议提供，工厂不生成、不复用临时路径。
 */
class AndroidAacChunkEncoderFactory(
    private val capabilitySource: EncoderCapabilitySource = MediaCodecCapabilitySource(),
) : CaptureChunkEncoderFactory {

    override fun create(): CaptureChunkEncoder = AacChunkEncoder(
        codec = MediaCodecAacEncoder(),
        writer = MediaMuxerContainerWriter(),
        capabilitySource = capabilitySource,
    )
}

/** 块编码器工厂：每次新块都要全新编码器，禁止复用已封存的实例。 */
fun interface CaptureChunkEncoderFactory {
    fun create(): CaptureChunkEncoder
}
