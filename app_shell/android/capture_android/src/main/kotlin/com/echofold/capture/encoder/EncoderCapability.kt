package com.echofold.capture.encoder

import kotlin.math.abs

/** AAC 编码档次；本阶段只使用 AAC-LC。 */
enum class AacProfile { LC, HE, HE_V2 }

/**
 * 编码目标参数。
 *
 * 默认目标为契约规定的 192000 bit/s 单声道；实际能否达到由设备编码器决定，
 * 不得把文件虚标成目标码率。
 */
data class EncoderTarget(
    val sampleRate: Int,
    val channelCount: Int = 1,
    val bitRate: Int = DEFAULT_BIT_RATE,
    val mimeType: String = MIME_AAC,
    val aacProfile: AacProfile = AacProfile.LC,
) {
    init {
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(channelCount in 1..2) { "本阶段只支持 1 或 2 声道：$channelCount" }
        require(bitRate > 0) { "bitRate 必须为正数：$bitRate" }
        require(mimeType.isNotBlank()) { "mimeType 不能为空白字符串" }
    }
}

/**
 * 编码器**实际**上报的参数。
 *
 * [codecName] 来自平台编码器（`MediaCodec.name`），`sampleRate/bitRate/channelCount`
 * 优先取编码器输出格式；取不到时才回落到请求值，并在诊断中如实标记差异。
 */
data class EncoderCapability(
    val codecName: String,
    val mimeType: String,
    val sampleRate: Int,
    val bitRate: Int,
    val channelCount: Int,
) {
    init {
        require(mimeType.isNotBlank()) { "mimeType 不能为空白字符串" }
        require(sampleRate > 0) { "sampleRate 必须为正数：$sampleRate" }
        require(bitRate > 0) { "bitRate 必须为正数：$bitRate" }
        require(channelCount > 0) { "channelCount 必须为正数：$channelCount" }
    }
}

/**
 * 探测到的编码器能力范围，用于在配置前把目标参数夹到设备可接受区间，
 * 避免“配置失败”与“静默降级”两种坏结果。
 */
data class EncoderCaps(
    val codecName: String,
    val mimeType: String,
    val supportedSampleRates: Set<Int>,
    val bitRateRange: IntRange,
    val maxChannelCount: Int,
    val supportsAacLc: Boolean = true,
) {
    init {
        require(codecName.isNotBlank()) { "codecName 不能为空白字符串" }
        require(mimeType.isNotBlank()) { "mimeType 不能为空白字符串" }
        require(maxChannelCount > 0) { "maxChannelCount 必须为正数：$maxChannelCount" }
        require(bitRateRange.first > 0 && bitRateRange.first <= bitRateRange.last) {
            "bitRateRange 非法：$bitRateRange"
        }
    }
}

/** 与目标不一致的参数维度。 */
enum class DeviationField {
    SAMPLE_RATE,
    BIT_RATE,
    CHANNEL_COUNT,
    CODEC_PROFILE,
}

/** 一处参数偏差；[target] 是请求值，[actual] 是设备实际值。 */
data class EncoderDeviation(
    val dimension: DeviationField,
    val target: Int,
    val actual: Int,
) {
    /** 可直接展示给用户/写入诊断的中文说明。 */
    val message: String
        get() = when (dimension) {
            DeviationField.SAMPLE_RATE -> "采样率目标 $target Hz，实际 $actual Hz"
            DeviationField.BIT_RATE -> "码率目标 $target bit/s，实际 $actual bit/s"
            DeviationField.CHANNEL_COUNT -> "声道目标 $target，实际 $actual"
            DeviationField.CODEC_PROFILE -> "编码档次目标 $target，实际 $actual"
        }
}

/** 目标参数与设备实际参数的对照结果。 */
data class EncoderDiagnostics(
    val target: EncoderTarget,
    val actual: EncoderCapability,
    val deviations: List<EncoderDeviation>,
) {
    /** 实际参数是否完全达到目标。 */
    val matchesTarget: Boolean get() = deviations.isEmpty()

    /** 一行式诊断文本，可直接写入块元数据/健康上报。 */
    val summary: String
        get() = if (deviations.isEmpty()) {
            "编码参数与目标一致"
        } else {
            deviations.joinToString("；") { it.message }
        }
}

/** 夹取后的配置与相对原始目标的偏差。 */
data class EncoderResolution(
    val target: EncoderTarget,
    val deviations: List<EncoderDeviation>,
)

/**
 * 编码能力诊断纯函数。
 *
 * 分两步使用：
 * 1. [resolve] 在配置前把目标参数夹到探测到的能力范围内（避免配置必然失败）；
 * 2. [evaluate] 在编码器打开后用**实际**参数与目标比较，生成偏差诊断（不虚标）。
 */
object EncoderCapabilityDiagnostics {

    /**
     * 把 [target] 夹到 [caps] 允许的范围。
     *
     * @return 夹取后的目标与偏差；设备没有任何候选采样率时返回 null（调用方必须失败，不得猜测）。
     */
    fun resolve(target: EncoderTarget, caps: EncoderCaps): EncoderResolution? {
        if (caps.supportedSampleRates.isEmpty()) return null
        val sampleRate = if (target.sampleRate in caps.supportedSampleRates) {
            target.sampleRate
        } else {
            caps.supportedSampleRates.minWith(
                compareBy<Int> { abs(it - target.sampleRate) }.thenBy { it },
            )
        }
        val bitRate = target.bitRate.coerceIn(caps.bitRateRange.first, caps.bitRateRange.last)
        val channelCount = target.channelCount.coerceIn(1, caps.maxChannelCount)
        val deviations = buildList {
            if (sampleRate != target.sampleRate) {
                add(EncoderDeviation(DeviationField.SAMPLE_RATE, target.sampleRate, sampleRate))
            }
            if (bitRate != target.bitRate) {
                add(EncoderDeviation(DeviationField.BIT_RATE, target.bitRate, bitRate))
            }
            if (channelCount != target.channelCount) {
                add(EncoderDeviation(DeviationField.CHANNEL_COUNT, target.channelCount, channelCount))
            }
            if (!caps.supportsAacLc && target.aacProfile == AacProfile.LC) {
                // 编码器不支持 AAC-LC 时本阶段不能换成 HE 系列（会改变证据音频特征），只记录偏差。
                add(EncoderDeviation(DeviationField.CODEC_PROFILE, 2, 0))
            }
        }
        return EncoderResolution(
            target = target.copy(
                sampleRate = sampleRate,
                bitRate = bitRate,
                channelCount = channelCount,
            ),
            deviations = deviations,
        )
    }

    /** 用编码器实际参数与目标比较，返回完整诊断。 */
    fun evaluate(target: EncoderTarget, actual: EncoderCapability): EncoderDiagnostics {
        val deviations = buildList {
            if (actual.sampleRate != target.sampleRate) {
                add(EncoderDeviation(DeviationField.SAMPLE_RATE, target.sampleRate, actual.sampleRate))
            }
            if (actual.bitRate != target.bitRate) {
                add(EncoderDeviation(DeviationField.BIT_RATE, target.bitRate, actual.bitRate))
            }
            if (actual.channelCount != target.channelCount) {
                add(EncoderDeviation(DeviationField.CHANNEL_COUNT, target.channelCount, actual.channelCount))
            }
        }
        return EncoderDiagnostics(target = target, actual = actual, deviations = deviations)
    }
}

/** 契约规定的目标码率（bit/s）。 */
const val DEFAULT_BIT_RATE: Int = 192_000

/** AAC-LC 的 MIME 类型；与 `MediaFormat.MIMETYPE_AUDIO_AAC` 一致。 */
const val MIME_AAC: String = "audio/mp4a-latm"
