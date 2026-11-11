package com.echofold.capture.encoder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 编码能力夹取与“实际参数 vs 目标参数”偏差诊断的纯函数单测。 */
class EncoderCapabilityDiagnosticsTest {

    private val target = EncoderTarget(sampleRate = 48_000, channelCount = 1, bitRate = 192_000)

    private fun caps(
        sampleRates: Set<Int> = setOf(48_000),
        bitRateRange: IntRange = 8_000..320_000,
        maxChannelCount: Int = 2,
        supportsAacLc: Boolean = true,
    ) = EncoderCaps(
        codecName = "c2.android.aac.encoder",
        mimeType = MIME_AAC,
        supportedSampleRates = sampleRates,
        bitRateRange = bitRateRange,
        maxChannelCount = maxChannelCount,
        supportsAacLc = supportsAacLc,
    )

    @Test
    fun `目标在能力范围内时没有偏差`() {
        val resolution = EncoderCapabilityDiagnostics.resolve(target, caps())!!

        assertEquals(target, resolution.target)
        assertTrue(resolution.deviations.isEmpty())
    }

    @Test
    fun `码率超出设备上限时夹取并记录偏差`() {
        val resolution = EncoderCapabilityDiagnostics.resolve(target, caps(bitRateRange = 8_000..128_000))!!

        assertEquals(128_000, resolution.target.bitRate)
        val deviation = resolution.deviations.single()
        assertEquals(DeviationField.BIT_RATE, deviation.dimension)
        assertEquals(192_000, deviation.target)
        assertEquals(128_000, deviation.actual)
        assertTrue(deviation.message.contains("192000"))
        assertTrue(deviation.message.contains("128000"))
    }

    @Test
    fun `码率低于设备下限时夹取到下限`() {
        val lowTarget = target.copy(bitRate = 4_000)
        val resolution = EncoderCapabilityDiagnostics.resolve(lowTarget, caps(bitRateRange = 8_000..320_000))!!

        assertEquals(8_000, resolution.target.bitRate)
        assertEquals(8_000, resolution.deviations.single().actual)
    }

    @Test
    fun `采样率不支持时选择最接近的候选`() {
        val resolution = EncoderCapabilityDiagnostics.resolve(
            target,
            caps(sampleRates = setOf(16_000, 44_100)),
        )!!

        assertEquals(44_100, resolution.target.sampleRate)
        assertEquals(DeviationField.SAMPLE_RATE, resolution.deviations.single().dimension)
    }

    @Test
    fun `声道数超过设备上限时夹取到单声道`() {
        val stereo = target.copy(channelCount = 2)
        val resolution = EncoderCapabilityDiagnostics.resolve(stereo, caps(maxChannelCount = 1))!!

        assertEquals(1, resolution.target.channelCount)
        assertEquals(DeviationField.CHANNEL_COUNT, resolution.deviations.single().dimension)
    }

    @Test
    fun `不支持 AAC-LC 时记录编码档次偏差而不换档次`() {
        val resolution = EncoderCapabilityDiagnostics.resolve(target, caps(supportsAacLc = false))!!

        assertEquals(AacProfile.LC, resolution.target.aacProfile)
        assertEquals(DeviationField.CODEC_PROFILE, resolution.deviations.single().dimension)
    }

    @Test
    fun `设备没有任何采样率候选时返回 null`() {
        assertNull(EncoderCapabilityDiagnostics.resolve(target, caps(sampleRates = emptySet())))
    }

    @Test
    fun `实际码率低于目标时诊断不虚标`() {
        val actual = EncoderCapability(
            codecName = "c2.android.aac.encoder",
            mimeType = MIME_AAC,
            sampleRate = 48_000,
            bitRate = 96_000,
            channelCount = 1,
        )

        val diagnostics = EncoderCapabilityDiagnostics.evaluate(target, actual)

        assertFalse(diagnostics.matchesTarget)
        assertEquals(1, diagnostics.deviations.size)
        assertEquals(DeviationField.BIT_RATE, diagnostics.deviations.single().dimension)
        assertTrue(diagnostics.summary.contains("96000"))
        assertEquals(actual, diagnostics.actual)
        assertEquals(target, diagnostics.target)
    }

    @Test
    fun `实际参数完全一致时诊断为空`() {
        val actual = EncoderCapability(
            codecName = "c2.android.aac.encoder",
            mimeType = MIME_AAC,
            sampleRate = 48_000,
            bitRate = 192_000,
            channelCount = 1,
        )

        val diagnostics = EncoderCapabilityDiagnostics.evaluate(target, actual)

        assertTrue(diagnostics.matchesTarget)
        assertTrue(diagnostics.deviations.isEmpty())
        assertEquals("编码参数与目标一致", diagnostics.summary)
    }

    @Test
    fun `多维度不一致时逐项上报`() {
        val actual = EncoderCapability(
            codecName = "vendor.aac",
            mimeType = MIME_AAC,
            sampleRate = 44_100,
            bitRate = 128_000,
            channelCount = 2,
        )

        val diagnostics = EncoderCapabilityDiagnostics.evaluate(target, actual)

        assertEquals(
            setOf(DeviationField.SAMPLE_RATE, DeviationField.BIT_RATE, DeviationField.CHANNEL_COUNT),
            diagnostics.deviations.map { it.dimension }.toSet(),
        )
    }
}
