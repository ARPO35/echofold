package com.echofold.capture.source

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder

/**
 * 生产环境的 AudioRecord 工厂。
 *
 * 按 [AudioRecordConfig.preferredSampleRates] 的顺序（默认 48kHz → 44.1kHz → 16kHz）探测：
 * `getMinBufferSize` 必须为正、`AudioRecord` 必须初始化成功；全部失败时抛出最后一次的平台异常，
 * 由 [AudioRecordSource] 统一分类。本类不吞异常、不擅自换用未登记的配置。
 */
class AndroidAudioRecordFactory : AudioRecordFactory {

    override fun create(config: AudioRecordConfig): AudioRecordHandle {
        var lastError: Throwable? = null
        for (sampleRate in config.preferredSampleRates) {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                channelMask(config.channelCount),
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes <= 0) {
                lastError = UnsupportedOperationException(
                    "采样率 $sampleRate 不可用（minBufferSize=$minBufferBytes）",
                )
                continue
            }
            val requestedBytes = config.readBufferFrames?.let {
                it * BYTES_PER_SAMPLE * config.channelCount
            } ?: minBufferBytes
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(config.source.toPlatformSource())
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask(config.channelCount))
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(requestedBytes, minBufferBytes))
                    .build()
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    lastError = IllegalStateException("AudioRecord 初始化失败：$sampleRate Hz")
                    continue
                }
                return AndroidAudioRecordHandle(record, sampleRate, config.channelCount)
            } catch (error: SecurityException) {
                // 权限问题与配置无关，直接上抛交给分类，不再尝试其它采样率。
                throw error
            } catch (error: IllegalArgumentException) {
                lastError = error
            } catch (error: UnsupportedOperationException) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("没有可用的 AudioRecord 配置")
    }

    private fun channelMask(channelCount: Int): Int = when (channelCount) {
        1 -> AudioFormat.CHANNEL_IN_MONO
        2 -> AudioFormat.CHANNEL_IN_STEREO
        else -> throw IllegalArgumentException("不支持的声道数：$channelCount")
    }

    private fun AudioSourceKind.toPlatformSource(): Int = when (this) {
        AudioSourceKind.MIC -> MediaRecorder.AudioSource.MIC
        AudioSourceKind.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
    }
}

/** [AudioRecord] 的平台句柄包装；异常语义与 [AudioRecordHandle] 一致。 */
private class AndroidAudioRecordHandle(
    private val record: AudioRecord,
    override val sampleRate: Int,
    override val channelCount: Int,
) : AudioRecordHandle {

    private val timestamp = AudioTimestamp()

    override fun start() {
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "AudioRecord 未进入录音状态"
        }
    }

    override fun read(destination: ShortArray, offset: Int, length: Int): Int =
        record.read(destination, offset, length, AudioRecord.READ_NON_BLOCKING)

    override fun stop() {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            record.stop()
        }
    }

    override fun release() {
        record.release()
    }

    override fun latestTimestampMonoNs(): Long? =
        if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
            timestamp.nanoTime
        } else {
            null
        }
}
