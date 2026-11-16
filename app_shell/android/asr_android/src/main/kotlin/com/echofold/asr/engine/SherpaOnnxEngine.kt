package com.echofold.asr.engine

import com.echofold.asr.repository.ModelSnapshotJson
import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame
import com.echofold.core.asr.engine.Resampler
import java.io.File

/**
 * sherpa-onnx 流式识别引擎（单模型，X-ASR 480ms 原型候选）。
 *
 * 契约要点：
 * - 模型目录来自会话固定的 [ModelRef]，并与安装目录内的清单快照交叉核对 `modelId/version/sha256`；
 *   任何缺失或不一致都抛 [AsrEngineException]，**不回退**到其他模型、版本或远端 ASR；
 * - [acceptPcm] 归一化、必要时重采样并送入运行时；非空文本变化时同步交付 partial，端点时冲刷分段；
 * - [flushSegment] 取当前文本并复位流、清空 partial 去重缓存，再交付非空 final；[close] 幂等。
 *
 * `AsrConfig.enableVad` 由上层队列/静音判定使用；本引擎不为它额外加载 VAD 模型，端点仍由
 * sherpa-onnx 的 endpoint 规则控制（避免引入第二模型路线）。
 */
class SherpaOnnxEngine(
    private val runtime: SherpaRuntime,
    private val modelDirectoryResolver: (ModelRef) -> File?,
    private val resampler: Resampler? = null,
    private val onFinalText: (String) -> Unit = {},
    private val onPartialText: (String) -> Unit = {},
) : AsrEngine, ReusableAsrEngine {
    private val initLock = Any()

    @Volatile
    private var activeConfig: AsrConfig? = null

    @Volatile
    private var recognizer: SherpaRecognizer? = null

    @Volatile
    private var closed = false

    @Volatile
    private var lastFinalTextValue: String? = null

    private var lastPartialText: String? = null

    override fun initialize(config: AsrConfig) {
        synchronized(initLock) {
            if (closed) throw AsrEngineException("引擎已关闭，不能重新初始化")
            val current = activeConfig
            if (current != null) {
                if (current == config) return
                throw AsrEngineException("引擎已用另一份 AsrConfig 初始化，必须重建引擎")
            }
            val directory = modelDirectoryResolver(config.model)
                ?: throw AsrEngineException("模型安装不存在：${config.model.installationId}")
            if (!directory.isDirectory) throw AsrEngineException("模型安装目录不存在：$directory")
            val snapshot =
                try {
                    ModelSnapshotJson.read(directory)
                } catch (e: Exception) {
                    throw AsrEngineException("模型清单快照不可读：$directory", e)
                } ?: throw AsrEngineException("模型安装目录缺少清单快照：$directory")
            requireIdentity(config.model, snapshot, directory)
            val paths = SherpaModelLayout.resolve(directory)
            val created =
                try {
                    runtime.createRecognizer(paths, config.threads, PROVIDER)
                } catch (e: AsrEngineException) {
                    throw e
                } catch (e: Throwable) {
                    throw AsrEngineException("sherpa-onnx 初始化失败：${e.message ?: e.javaClass.simpleName}", e)
                }
            recognizer = created
            activeConfig = config
        }
    }

    override fun acceptPcm(frame: PcmFrame) {
        val active = activeConfig ?: throw AsrEngineException("引擎未初始化，不能接收 PCM")
        val current = recognizer ?: throw AsrEngineException("识别器不可用")
        val samples = toModelSamples(frame, active)
        try {
            current.acceptWaveform(samples, active.sampleRate)
            val text = current.resultText().trim()
            if (text.isNotEmpty() && text != lastPartialText) {
                lastPartialText = text
                onPartialText(text)
            }
            if (current.isEndpoint()) flushSegment()
        } catch (e: AsrEngineException) {
            throw e
        } catch (e: Exception) {
            throw AsrEngineException("识别运行时失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    override fun flushSegment() {
        activeConfig ?: throw AsrEngineException("引擎未初始化，不能冲刷分段")
        val current = recognizer ?: throw AsrEngineException("识别器不可用")
        val text =
            try {
                current.resultText().trim()
            } catch (e: AsrEngineException) {
                throw e
            } catch (e: Exception) {
                throw AsrEngineException("读取识别结果失败：${e.message ?: e.javaClass.simpleName}", e)
            }
        try {
            current.reset()
        } catch (e: AsrEngineException) {
            throw e
        } catch (e: Exception) {
            throw AsrEngineException("复位识别流失败：${e.message ?: e.javaClass.simpleName}", e)
        }
        lastPartialText = null
        if (text.isNotEmpty()) {
            lastFinalTextValue = text
            onFinalText(text)
        }
    }

    override fun close() {
        val current =
            synchronized(initLock) {
                if (closed) return
                closed = true
                val value = recognizer
                recognizer = null
                activeConfig = null
                value
            }
        if (current != null) {
            try {
                current.close()
            } catch (e: Exception) {
                // 释放 native 资源失败没有可恢复动作；不阻断会话清理。
            }
        }
    }

    /** 会话边界重置识别流并清空去重缓存；同一权重可被下一会话复用，不继承旧会话状态。 */
    override fun resetStream() {
        synchronized(initLock) {
            if (closed) throw AsrEngineException("引擎已关闭，不能重置识别流")
            lastPartialText = null
            lastFinalTextValue = null
            val current = recognizer ?: return
            try {
                current.reset()
            } catch (e: AsrEngineException) {
                throw e
            } catch (e: Exception) {
                throw AsrEngineException("复位识别流失败：${e.message ?: e.javaClass.simpleName}", e)
            }
        }
    }

    /** 最近一次 [flushSegment] 交付的非空文本（诊断/测试用）。 */
    fun lastFinalText(): String? = lastFinalTextValue

    private fun requireIdentity(model: ModelRef, snapshot: com.echofold.asr.repository.ModelSnapshot, directory: File) {
        if (snapshot.installationId != model.installationId) {
            throw AsrEngineException(
                "模型安装身份不一致：期望 installationId=${model.installationId}，实际 ${snapshot.installationId}（$directory）",
            )
        }
        val manifest = snapshot.manifest
        if (manifest.modelId != model.modelId || manifest.version != model.modelVersion || manifest.sha256 != model.sha256) {
            throw AsrEngineException(
                "模型身份不一致：期望 ${model.modelId}@${model.modelVersion}/${model.sha256}，" +
                    "实际 ${manifest.modelId}@${manifest.version}/${manifest.sha256}（$directory）",
            )
        }
    }

    private fun toModelSamples(frame: PcmFrame, config: AsrConfig): FloatArray {
        val start = frame.offset
        val end = frame.offset + frame.length
        val raw = frame.samples.copyOfRange(start, end)
        val data =
            if (frame.sampleRate == config.sampleRate) {
                raw
            } else {
                val resampler = this.resampler
                    ?: throw AsrEngineException(
                        "PCM 采样率 ${frame.sampleRate} 与模型 ${config.sampleRate} 不一致，且未注入重采样器",
                    )
                try {
                    resampler.resample(raw, frame.sampleRate)
                } catch (e: Exception) {
                    throw AsrEngineException("重采样失败：${e.message ?: e.javaClass.simpleName}", e)
                }
            }
        return FloatArray(data.size) { data[it] / PCM_SCALE }
    }

    private companion object {
        const val PROVIDER = "cpu"

        /** 16-bit PCM 归一化除数（2^15），使 -32768 映射到 -1.0。 */
        const val PCM_SCALE = 32_768f
    }
}
