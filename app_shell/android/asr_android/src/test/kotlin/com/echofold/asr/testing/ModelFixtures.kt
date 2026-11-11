package com.echofold.asr.testing

import com.echofold.asr.engine.SherpaModelPaths
import com.echofold.asr.engine.SherpaOnnxEngine
import com.echofold.asr.engine.SherpaRecognizer
import com.echofold.asr.engine.SherpaRuntime
import com.echofold.asr.install.InstallationIds
import com.echofold.asr.internal.sha256Hex
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import com.echofold.asr.repository.InstalledModel
import com.echofold.asr.repository.ModelSnapshot
import com.echofold.asr.repository.ModelSnapshotJson
import com.echofold.asr.trust.VerifiedManifest
import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.ModelRef
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 构造 ZIP 工件（顺序固定，内容即文件字节）。 */
fun zipBytes(files: Map<String, ByteArray>): ByteArray {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return buffer.toByteArray()
}

/** 默认模型文件集合（内容任意，布局解析只看文件名）。 */
fun defaultModelFiles(): Map<String, ByteArray> =
    mapOf(
        "encoder.int8.onnx" to ByteArray(16) { 1 },
        "decoder.int8.onnx" to ByteArray(16) { 2 },
        "joiner.int8.onnx" to ByteArray(16) { 3 },
        "tokens.txt" to "a 0\nb 1\n".toByteArray(),
    )

/** 把模型文件与清单快照写入目录，返回目录。 */
fun createModelDirectory(
    root: File,
    installationId: String,
    manifest: ModelManifest,
    files: Map<String, ByteArray> = defaultModelFiles(),
    source: ModelSource = ModelSource.REMOTE,
    userConfirmed: Boolean = false,
    installedAtUtcMs: Long = 1_000L,
): File {
    val directory = File(root, installationId)
    check(directory.mkdirs() || directory.isDirectory) { "无法创建测试模型目录：$directory" }
    files.forEach { (name, content) ->
        val file = File(directory, name)
        file.parentFile?.mkdirs()
        file.writeBytes(content)
    }
    ModelSnapshotJson.write(directory, ModelSnapshot(installationId, manifest, source, userConfirmed, installedAtUtcMs))
    return directory
}

/** 构造索引条目（目录必须已存在）。 */
fun installedModel(
    directory: File,
    manifest: ModelManifest,
    installationId: String = InstallationIds.of(manifest),
    source: ModelSource = ModelSource.REMOTE,
    userConfirmed: Boolean = false,
    installedAtUtcMs: Long = 1_000L,
): InstalledModel =
    ModelSnapshot(installationId, manifest, source, userConfirmed, installedAtUtcMs).toInstalledModel(directory)

/** 会话固定引用。 */
fun modelRef(manifest: ModelManifest, installationId: String = InstallationIds.of(manifest)): ModelRef =
    ModelRef(
        modelId = manifest.modelId,
        modelVersion = manifest.version,
        installationId = installationId,
        sha256 = manifest.sha256,
    )

/** 运行时配置。 */
fun asrConfig(manifest: ModelManifest, installationId: String = InstallationIds.of(manifest)): AsrConfig =
    AsrConfig(model = modelRef(manifest, installationId))

/** 直接构造已验证清单（安装器测试用，省略真实签名流程）。 */
fun verifiedRemote(manifest: ModelManifest): VerifiedManifest =
    VerifiedManifest(manifest, ModelSource.REMOTE, signatureKeyId = "test-key", verifiedAtUtcMs = 0L, userConfirmed = false)

/** 手动导入的已验证清单。 */
fun verifiedManual(manifest: ModelManifest): VerifiedManifest =
    VerifiedManifest(manifest, ModelSource.MANUAL, signatureKeyId = null, verifiedAtUtcMs = 0L, userConfirmed = true)

/** 清单大小/哈希与工件保持一致。 */
fun manifestForArtifact(
    artifactBytes: ByteArray,
    files: Map<String, ByteArray>,
    base: ModelManifest = testManifest(),
): ModelManifest {
    val temp = File.createTempFile("echofold-manifest", ".zip")
    try {
        temp.writeBytes(artifactBytes)
        return base.copy(
            sha256 = temp.sha256Hex(),
            compressedSize = artifactBytes.size.toLong(),
            extractedSize = files.values.sumOf { it.size.toLong() },
        )
    } finally {
        temp.delete()
    }
}

/** 记录调用的 fake 识别器。 */
class FakeSherpaRecognizer : SherpaRecognizer {
    val accepted = mutableListOf<FloatArray>()
    val acceptedRates = mutableListOf<Int>()
    var text: String = "你好世界"
    var endpoint: Boolean = true
    var failOnAccept: Exception? = null
    var resetCount: Int = 0
    var closeCount: Int = 0

    override fun acceptWaveform(samples: FloatArray, sampleRate: Int) {
        failOnAccept?.let { throw it }
        accepted += samples
        acceptedRates += sampleRate
    }

    override fun isEndpoint(): Boolean = endpoint

    override fun resultText(): String = text

    override fun reset() {
        resetCount += 1
    }

    override fun close() {
        closeCount += 1
        if (closeCount > 2) throw IllegalStateException("close 非幂等")
    }
}

/** 记录创建参数的 fake 运行时。 */
class FakeSherpaRuntime : SherpaRuntime {
    val recognizer = FakeSherpaRecognizer()
    var createdPaths: SherpaModelPaths? = null
    var createdThreads: Int? = null
    var createdProvider: String? = null
    var createCount: Int = 0
    var createFailure: Throwable? = null

    override fun createRecognizer(paths: SherpaModelPaths, threads: Int, provider: String): SherpaRecognizer {
        createCount += 1
        createFailure?.let { throw it }
        createdPaths = paths
        createdThreads = threads
        createdProvider = provider
        return recognizer
    }
}

/** 引擎测试便捷构造。 */
fun engineFor(
    directory: File,
    runtime: SherpaRuntime,
    resampler: com.echofold.core.asr.engine.Resampler? = null,
    onFinalText: (String) -> Unit = {},
): SherpaOnnxEngine = SherpaOnnxEngine(runtime = runtime, modelDirectoryResolver = { directory }, resampler = resampler, onFinalText = onFinalText)
