package com.echofold.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.echofold.asr.engine.AsrEngineProvider
import com.echofold.asr.engine.ReflectiveSherpaRuntime
import com.echofold.asr.engine.SharedAsrEnginePool
import com.echofold.asr.install.InstallationIds
import com.echofold.asr.install.JdkHttpRangeClient
import com.echofold.asr.install.ModelInstaller
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.repository.FileLeaseStore
import com.echofold.asr.repository.FileModelIndexStore
import com.echofold.asr.repository.ModelLeaseManager
import com.echofold.asr.repository.ModelRepository
import com.echofold.asr.trust.ManifestVerifier
import com.echofold.asr.trust.TrustedKeyRegistry
import com.echofold.asr.internal.sha256Hex
import com.echofold.capture.pcm.SessionAsrBinding
import com.echofold.capture.pcm.SessionAsrFactory
import com.echofold.capture.pcm.SessionAsrSink
import com.echofold.core.asr.LinearResampler
import com.echofold.core.asr.engine.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 本地 X-ASR 工件清单与既有 MANUAL 安装协议适配。 */
internal class BuiltinXAsrBundle(private val context: Context) {
    private val root = File(context.filesDir, "asr")
    val repository = ModelRepository(
        FileModelIndexStore(File(root, "echofold-model-index.json")),
        ModelLeaseManager(FileLeaseStore(File(root, "echofold-model-leases.json"))),
    )
    private val installer = ModelInstaller(root, JdkHttpRangeClient(), android.os.Build.SUPPORTED_ABIS.first(), RUNTIME_VERSION)
    private val verifier = ManifestVerifier(TrustedKeyRegistry.empty(),
        com.echofold.asr.trust.FileAcceptedGenerationStore(File(root, "accepted-generations.json")))

    /** 进程内共享引擎池：同一安装+配置复用已加载权重，会话结束不销毁。 */
    private val enginePool = SharedAsrEnginePool { config, callbacks ->
        provider({ callbacks.onFinalText(it) }, { callbacks.onPartialText(it) }).create(config)
    }
    private val warmup = ModelWarmupState()
    private val warmupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "echofold-model-warmup").apply { isDaemon = true }
    }

    fun status(): Map<String, Any?> {
        val selected = repository.current()
        return mapOf(
            "available" to (selected != null),
            "selectedInstallationId" to selected?.installationId,
            "modelId" to selected?.manifest?.modelId,
            "version" to selected?.manifest?.version,
            "source" to selected?.source?.name,
            "models" to repository.list().map { model ->
                mapOf("installationId" to model.installationId, "modelId" to model.modelId,
                    "version" to model.modelVersion, "source" to model.source.name,
                    "selected" to (model.installationId == selected?.installationId),
                    "state" to model.state.name)
            },
            "runtimeVersion" to RUNTIME_VERSION,
            "warmState" to (warmup.toStatusMap() + ("rebuildReason" to enginePool.rebuildReason)),
            "message" to if (selected == null) "尚未安装模型；可以继续仅录音，或导入已授权的 X-ASR 模型。" else "X-ASR ${selected.manifest.version} 已就绪",
        )
    }

    fun provider(onFinalText: (String) -> Unit, onPartialText: (String) -> Unit = {}): AsrEngineProvider = AsrEngineProvider.forDevice(
        repository = repository,
        runtimeProvider = { ReflectiveSherpaRuntime() },
        runtimeVersion = RUNTIME_VERSION,
        resampler = LinearResampler(),
        onFinalText = onFinalText,
        onPartialText = onPartialText,
    )

    val sessionFactory = SessionAsrFactory { sessionId, onFrame, onText, onPartialText, onFailure ->
        val selected = repository.current() ?: throw AsrEngineException("尚未选择可用模型")
        val lease = repository.acquireLease(selected.installationId, sessionId)
        try {
            val model = ModelRef(selected.modelId, selected.modelVersion, selected.installationId, selected.sha256)
            val config = AsrConfig(model)
            val session = enginePool.acquire(config, onText, onPartialText)
            val tracked = object : AsrEngine {
                override fun initialize(config: AsrConfig) = session.initialize(config)
                override fun acceptPcm(frame: PcmFrame) { onFrame(frame); session.acceptPcm(frame) }
                override fun flushSegment() = session.flushSegment()
                override fun close() = session.close()
            }
            SessionAsrBinding(model, config, SessionAsrSink(sessionId, config, tracked, { error ->
                // 运行时失效：丢弃共享权重，下一次会话/预热显式重建，不静默沿用旧实例。
                enginePool.invalidate(error.message ?: error.javaClass.simpleName)
                onFailure(error)
            })) {
                repository.releaseLease(lease.leaseId)
            }
        } catch (error: Throwable) {
            repository.releaseLease(lease.leaseId)
            throw error
        }
    }

    /** 启动预热：后台线程初始化当前已选模型，不阻塞 UI 线程，不改变 status()["available"] 判定。 */
    fun warmupAsync() {
        warmupExecutor.execute {
            val selected = repository.current()
            if (selected == null) {
                warmup.markIdle("尚未安装模型，无预热目标")
                return@execute
            }
            warmup.markLoading(selected.installationId)
            try {
                val model = ModelRef(selected.modelId, selected.modelVersion, selected.installationId, selected.sha256)
                enginePool.warmup(AsrConfig(model))
                warmup.markReady(selected.installationId)
            } catch (error: Throwable) {
                // 失败不静默降级也不假启动：原因保留在 warmState，供设置查询与重试。
                warmup.markFailed(selected.installationId, error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /** 显式切换模型等场景：丢弃进程内共享权重，下一次会话/预热重建，原因可查。 */
    fun invalidateEngine(reason: String) {
        enginePool.invalidate(reason)
    }

    private val importing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val importExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "echofold-model-import").apply { isDaemon = true }
    }

    fun importBundleTreeAsync(tree: Uri, completion: (Result<com.echofold.asr.repository.InstalledModel>) -> Unit) {
        check(importing.compareAndSet(false, true)) { "模型导入正在进行" }
        importExecutor.execute {
            val result = runCatching { importBundleTree(tree) }
            importing.set(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post { completion(result) }
        }
    }

    private fun importBundleTree(tree: Uri): com.echofold.asr.repository.InstalledModel {
        val archive = File(context.cacheDir, "x-asr-${System.nanoTime()}.zip")
        var verifiedSize = 0L
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                for (spec in FILES.values) {
                    zip.putNextEntry(ZipEntry(spec.path).apply { time = 0L })
                    val document = findDocument(tree, spec.path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    context.contentResolver.openInputStream(document)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= spec.size) { "${spec.path} 超过固定大小，拒绝导入。" }
                            digest.update(buffer, 0, count)
                            zip.write(buffer, 0, count)
                        }
                    } ?: error("无法读取 ${spec.path}；源文件未修改。")
                    require(bytes == spec.size && digest.digest().joinToString("") { "%02x".format(it) } == spec.sha256) {
                        "${spec.path} 大小或 SHA-256 与固定 revision 不符；源文件未修改。"
                    }
                    // 仅全部工件校验通过后，才允许安装已生成的归档。
                    verifiedSize += bytes
                    zip.closeEntry()
                }
            }
            val manifest = ModelManifest(
                modelId = MODEL_ID, version = MODEL_VERSION, signatureGeneration = 1,
                channel = ModelChannel.MANUAL, runtimeMinVersion = RUNTIME_VERSION, abi = listOf("arm64-v8a"),
                url = "https://huggingface.co/openEuler/sherpa-onnx-asr/resolve/$REVISION/bundle.metadata",
                sha256 = archive.sha256Hex(), compressedSize = archive.length(), extractedSize = verifiedSize,
                license = "Apache-2.0", capabilities = listOf("streaming", "zh", "en", "cpu"),
            )
            val verified = verifier.verifyManual(manifest, archive.sha256Hex(), userConfirmed = true)
            val installed = installer.importManual(verified, archive)
            repository.install(installed, select = true)
            return checkNotNull(repository.find(InstallationIds.of(manifest)))
        } finally { archive.delete() }
    }

    private fun findDocument(tree: Uri, path: String): Uri {
        var parent = DocumentsContract.getTreeDocumentId(tree)
        for (part in path.split('/')) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            parent = context.contentResolver.query(children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                var found: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == part) { found = cursor.getString(0); break }
                }
                found
            } ?: error("模型根目录缺少 $path；请选择包含 artifacts/ 的目录。")
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, parent)
    }

    companion object {
        const val RUNTIME_VERSION = "1.13.6"
        const val MODEL_ID = "x-asr-480ms-int8"
        const val MODEL_VERSION = "ef45908b6d6d293fc4100797488e95b2b08662ba"
        const val REVISION = MODEL_VERSION
        val FILES = linkedMapOf(
            "encoder.int8.onnx" to FileSpec("artifacts/encoder/encoder.int8.onnx", 155278641L, "7cbe8ee82e9124b30f7b4ca3df0d35b1e72ab7d45feed4143119b173e674de4e"),
            "decoder.onnx" to FileSpec("artifacts/decoder/decoder.onnx", 11309084L, "fd2ac10c5936a1411f7a68e669e8cfec882b17791dd2ca44ab9baf69bb8ab855"),
            "joiner.int8.onnx" to FileSpec("artifacts/joiner/joiner.int8.onnx", 2581422L, "82e88dad29301e29144565cd3b7347fa163f07ddb30ea60346b7c4c536adf4ba"),
            "tokens.txt" to FileSpec("artifacts/tokens/tokens.txt", 59240L, "ad0df596d9cc60c90795409549ccf7d1c4f901bdc060dd6b2b067d9ef2fddfaf"),
        )
    }

    data class FileSpec(val path: String, val size: Long, val sha256: String)
}