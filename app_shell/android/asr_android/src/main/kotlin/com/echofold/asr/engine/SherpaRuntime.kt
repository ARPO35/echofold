package com.echofold.asr.engine

import com.echofold.core.asr.engine.AsrEngineException
import java.io.File

/**
 * 解析后的 sherpa-onnx transducer 模型文件（X-ASR 为 encoder/decoder/joiner + tokens）。
 */
data class SherpaModelPaths(
    val encoder: File,
    val decoder: File,
    val joiner: File,
    val tokens: File,
)

/**
 * sherpa-onnx 运行时抽象。
 *
 * 生产实现 [ReflectiveSherpaRuntime] 通过反射调用 AAR 中的 Kotlin API；单测注入 fake，
 * 因此引擎逻辑（模型目录校验、PCM 归一化、端点冲刷、失败语义）可完全脱离 native 运行时验证。
 */
interface SherpaRuntime {
    /**
     * 创建识别器；失败必须抛 [AsrEngineException]，不得返回降级实现。
     */
    fun createRecognizer(paths: SherpaModelPaths, threads: Int, provider: String): SherpaRecognizer
}

/** 单流识别器句柄；[close] 必须幂等。 */
interface SherpaRecognizer : AutoCloseable {
    /** 送入 16kHz 单声道 float PCM（[-1, 1]）。 */
    fun acceptWaveform(samples: FloatArray, sampleRate: Int)

    /** 当前流是否到达端点。 */
    fun isEndpoint(): Boolean

    /** 当前流识别文本（可能为空）。 */
    fun resultText(): String

    /** 复位当前流，开始新段。 */
    fun reset()

    override fun close()
}

/**
 * 安装目录内的模型文件布局解析。
 *
 * 同一目录可能同时存在 `int8` 与 `fp32` 权重；X-ASR 基线优先选择文件名含 `int8` 的权重，
 * 否则按文件名排序取第一个。缺少任一必需文件都明确失败，绝不回退到别的目录或模型。
 */
object SherpaModelLayout {
    /** 按约定解析模型目录。 */
    fun resolve(directory: File): SherpaModelPaths {
        if (!directory.isDirectory) throw AsrEngineException("模型安装目录不存在：$directory")
        // 上游手动导入为平铺布局，指定 IB-Robot bundle 使用固定四工件目录。
        val artifactDirectory = File(directory, "artifacts")
        val files = if (artifactDirectory.isDirectory) {
            listOf(
                File(artifactDirectory, "encoder/encoder.int8.onnx"),
                File(artifactDirectory, "decoder/decoder.onnx"),
                File(artifactDirectory, "joiner/joiner.int8.onnx"),
                File(artifactDirectory, "tokens/tokens.txt"),
            ).filter { it.isFile }
        } else {
            directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        }
        if (files.isEmpty()) throw AsrEngineException("模型安装目录为空：$directory")
        val encoder = pick(files, ENCODER_PREFIX, directory)
        val decoder = pick(files, DECODER_PREFIX, directory)
        val joiner = pick(files, JOINER_PREFIX, directory)
        val tokens =
            files.firstOrNull { it.name.equals(TOKENS_FILE, ignoreCase = true) }
                ?: throw AsrEngineException("模型安装目录缺少 $TOKENS_FILE：$directory")
        return SherpaModelPaths(encoder, decoder, joiner, tokens)
    }

    private fun pick(files: List<File>, prefix: String, directory: File): File {
        val candidates = files.filter { it.name.startsWith(prefix, ignoreCase = true) && it.name.endsWith(".onnx", ignoreCase = true) }
        if (candidates.isEmpty()) {
            throw AsrEngineException("模型安装目录缺少 $prefix*.onnx：$directory")
        }
        val int8 = candidates.filter { it.name.contains("int8", ignoreCase = true) }
        return (int8.ifEmpty { candidates }).first()
    }

    private const val ENCODER_PREFIX = "encoder"
    private const val DECODER_PREFIX = "decoder"
    private const val JOINER_PREFIX = "joiner"
    private const val TOKENS_FILE = "tokens.txt"
}
