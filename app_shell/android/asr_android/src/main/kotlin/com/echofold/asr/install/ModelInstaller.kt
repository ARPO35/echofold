package com.echofold.asr.install

import com.echofold.asr.internal.AtomicFiles
import com.echofold.asr.internal.sha256Hex
import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import com.echofold.asr.model.VersionNumber
import com.echofold.asr.repository.InstalledModel
import com.echofold.asr.repository.ModelSnapshot
import com.echofold.asr.repository.ModelSnapshotJson
import com.echofold.asr.trust.VerifiedManifest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/** 下载进度回调；`totalBytes` 为清单声明大小。 */
fun interface DownloadProgress {
    fun onProgress(downloadedBytes: Long, totalBytes: Long)
}

/**
 * 模型安装器。
 *
 * 安全边界：
 * - 只接受经过 [com.echofold.asr.trust.ManifestVerifier] 校验的清单（REMOTE）或经用户确认的
 *   MANUAL 导入；
 * - 逐跳校验重定向（最多 [redirectLimit] 次），拒绝 HTTPS 降级，跨源不转发 `Authorization`；
 * - 下载写入 `.part` 临时文件并支持 Range 续传；
 * - 只有完整文件 SHA-256 与解压总大小都匹配时才从 staging 目录原子改名为最终安装目录；
 * - 任何失败都不会触碰当前安装，也不会留下半装目录。
 */
class ModelInstaller(
    private val rootDir: File,
    private val httpClient: HttpRangeClient,
    /** 设备主 ABI，例如 `arm64-v8a`。 */
    private val deviceAbi: String,
    /** 当前 sherpa-onnx 运行时版本，如 `1.10.0`。 */
    private val runtimeVersion: String,
    private val redirectLimit: Int = DEFAULT_REDIRECT_LIMIT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(deviceAbi.isNotBlank()) { "deviceAbi 不能为空白字符串" }
        require(runtimeVersion.isNotBlank()) { "runtimeVersion 不能为空白字符串" }
        require(redirectLimit >= 0) { "redirectLimit 不能为负数：$redirectLimit" }
    }

    /** 从受信清单下载并安装；返回登记所需的安装条目。 */
    fun install(
        verified: VerifiedManifest,
        authorization: String? = null,
        onProgress: DownloadProgress? = null,
    ): InstalledModel {
        if (verified.source != ModelSource.REMOTE) {
            throw ModelInstallException("受信安装只接受 REMOTE 清单；MANUAL 请使用 importManual")
        }
        val manifest = verified.manifest
        if (manifest.channel == ModelChannel.MANUAL) {
            throw ModelInstallException("MANUAL 渠道清单不得走受信安装路径")
        }
        requireCompatible(manifest)
        val installationId = InstallationIds.of(manifest)
        val target = File(modelsDir(), installationId)
        existingInstallation(target, installationId, manifest)?.let { return it }
        val partFile = File(downloadsDir(), "$installationId.part")
        download(manifest, partFile, authorization, onProgress)
        val size = partFile.length()
        if (size != manifest.compressedSize) {
            if (size > manifest.compressedSize) partFile.delete()
            throw ModelInstallException(
                "下载大小 $size 与清单 compressedSize ${manifest.compressedSize} 不一致" +
                    if (size < manifest.compressedSize) "（可重试续传）" else "",
            )
        }
        val actualSha = partFile.sha256Hex()
        if (actualSha != manifest.sha256) {
            partFile.delete()
            throw ModelInstallException("SHA-256 校验失败：期望 ${manifest.sha256}，实际 $actualSha")
        }
        val installed = installFromArchive(partFile, manifest, installationId, ModelSource.REMOTE, userConfirmed = false)
        partFile.delete()
        return installed
    }

    /**
     * 手动导入：归档已经由用户确认，SHA-256 与清单一致（由验签器保证）；安装目录最终标记
     * [ModelSource.MANUAL]，不得被描述为受信远程来源。
     */
    fun importManual(verified: VerifiedManifest, archive: File): InstalledModel {
        if (verified.source != ModelSource.MANUAL || !verified.userConfirmed) {
            throw ModelInstallException("手动导入需要 MANUAL 来源与用户确认")
        }
        if (verified.manifest.channel != ModelChannel.MANUAL) {
            throw ModelInstallException("手动导入清单必须声明 MANUAL 渠道：${verified.manifest.channel}")
        }
        if (!archive.isFile) throw ModelInstallException("手动导入文件不存在：$archive")
        val manifest = verified.manifest
        requireCompatible(manifest)
        val actualSize = archive.length()
        if (actualSize != manifest.compressedSize) {
            throw ModelInstallException("手动导入文件大小 $actualSize 与清单 compressedSize ${manifest.compressedSize} 不一致")
        }
        val actualSha = archive.sha256Hex()
        if (actualSha != manifest.sha256) {
            throw ModelInstallException("手动导入文件 SHA-256 与清单不一致：$actualSha != ${manifest.sha256}")
        }
        val installationId = InstallationIds.of(manifest)
        val target = File(modelsDir(), installationId)
        existingInstallation(target, installationId, manifest)?.let { return it }
        return installFromArchive(archive, manifest, installationId, ModelSource.MANUAL, userConfirmed = true)
    }

    /** 根目录（调试/迁移用）。 */
    fun rootDirectory(): File = rootDir

    private fun installFromArchive(
        archive: File,
        manifest: ModelManifest,
        installationId: String,
        source: ModelSource,
        userConfirmed: Boolean,
    ): InstalledModel {
        val stagingRoot = stagingDir()
        val staging = File(stagingRoot, "$installationId-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw ModelInstallException("无法创建临时安装目录：$staging")
        try {
            val extracted = ZipModelArchive.extract(archive, staging)
            if (extracted != manifest.extractedSize) {
                throw ModelInstallException("解压总大小 $extracted 与清单 extractedSize ${manifest.extractedSize} 不一致")
            }
            val snapshot = ModelSnapshot(installationId, manifest, source, userConfirmed, clock())
            ModelSnapshotJson.write(staging, snapshot)
            val target = File(modelsDir(), installationId)
            if (!modelsDir().isDirectory && !modelsDir().mkdirs() && !modelsDir().isDirectory) {
                throw ModelInstallException("无法创建模型安装根目录：${modelsDir()}")
            }
            if (target.exists()) {
                staging.deleteRecursively()
                return existingInstallation(target, installationId, manifest)
                    ?: throw ModelInstallException("安装目标已存在但无法读取快照：$target")
            }
            AtomicFiles.moveDirectoryAtomically(staging, target)
            return snapshot.toInstalledModel(target)
        } catch (e: ModelInstallException) {
            staging.deleteRecursively()
            throw e
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw ModelInstallException("安装模型失败：${manifest.modelId}@${manifest.version}", e)
        }
    }

    private fun existingInstallation(target: File, installationId: String, manifest: ModelManifest): InstalledModel? {
        if (!target.exists()) return null
        if (!target.isDirectory) throw ModelInstallException("安装目标已存在且不是目录：$target")
        val snapshot =
            try {
                ModelSnapshotJson.read(target)
            } catch (e: IllegalStateException) {
                throw ModelInstallException("安装目录快照损坏：$target", e)
            } ?: throw ModelInstallException("安装目录已存在但缺少清单快照：$target")
        if (snapshot.installationId != installationId || snapshot.manifest.sha256 != manifest.sha256) {
            throw ModelInstallException("安装目录快照与清单不一致：$target")
        }
        return snapshot.toInstalledModel(target)
    }

    private fun requireCompatible(manifest: ModelManifest) {
        if (deviceAbi !in manifest.abi) {
            throw ModelInstallException("模型 ABI 不兼容：清单支持 ${manifest.abi}，设备 $deviceAbi")
        }
        val runtimeCompatible =
            try {
                VersionNumber.atLeast(runtimeVersion, manifest.runtimeMinVersion)
            } catch (e: IllegalArgumentException) {
                throw ModelInstallException("运行时版本号非法：$runtimeVersion", e)
            }
        if (!runtimeCompatible) {
            throw ModelInstallException("运行时版本过低：$runtimeVersion < ${manifest.runtimeMinVersion}")
        }
    }

    private fun download(
        manifest: ModelManifest,
        partFile: File,
        authorization: String?,
        onProgress: DownloadProgress?,
    ) {
        val parent = partFile.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw ModelInstallException("无法创建下载目录：$parent")
        }
        if (partFile.isFile && partFile.length() >= manifest.compressedSize) return
        val originalOrigin = Origin.parse(manifest.url)
            ?: throw ModelInstallException("清单 URL 非法：${manifest.url}")
        var currentUrl = manifest.url
        var redirects = 0
        while (true) {
            requireHttps(currentUrl)
            val currentOrigin = Origin.parse(currentUrl)
                ?: throw ModelInstallException("重定向后的 URL 非法：$currentUrl")
            val existing = if (partFile.isFile) partFile.length() else 0L
            if (existing > manifest.compressedSize) {
                partFile.delete()
                continue
            }
            if (existing >= manifest.compressedSize) return
            val headers = LinkedHashMap<String, String>()
            // 跨源重定向不转发凭据；同源（https + host + 端口）才附带。
            if (authorization != null && currentOrigin == originalOrigin) {
                headers[AUTHORIZATION] = authorization
            }
            val request = HttpRequest(
                url = currentUrl,
                headers = headers,
                range = if (existing > 0L) ByteRange(existing) else null,
            )
            val exchange =
                try {
                    httpClient.open(request)
                } catch (e: IOException) {
                    throw ModelInstallException("下载请求失败：$currentUrl（已下载 $existing 字节，可续传）", e)
                }
            try {
                val status = exchange.statusCode
                if (status in 300..399) {
                    val location = exchange.location
                    if (location.isNullOrBlank()) throw ModelInstallException("重定向缺少 Location：$currentUrl")
                    redirects += 1
                    if (redirects > redirectLimit) {
                        throw ModelInstallException("重定向次数超过上限 $redirectLimit：$currentUrl")
                    }
                    val next = resolveRedirect(currentUrl, location)
                    requireHttps(next)
                    currentUrl = next
                    continue
                }
                when (status) {
                    200 -> writeBody(exchange.body, partFile, append = false, startOffset = 0L, totalBytes = manifest.compressedSize, onProgress = onProgress, sourceUrl = currentUrl)
                    206 -> {
                        val contentRange = exchange.contentRange
                            ?: throw ModelInstallException("服务器返回 206 但缺少合法 Content-Range：$currentUrl")
                        if (existing <= 0L) throw ModelInstallException("未请求续传却收到 206 响应：$currentUrl")
                        if (contentRange.startInclusive != existing) {
                            throw ModelInstallException(
                                "续传起点不一致：期望 $existing，实际 ${contentRange.startInclusive}",
                            )
                        }
                        if (contentRange.totalLength != null && contentRange.totalLength != manifest.compressedSize) {
                            throw ModelInstallException(
                                "服务器返回总大小 ${contentRange.totalLength} 与清单 ${manifest.compressedSize} 不一致",
                            )
                        }
                        writeBody(
                            exchange.body,
                            partFile,
                            append = true,
                            startOffset = existing,
                            totalBytes = manifest.compressedSize,
                            onProgress = onProgress,
                            sourceUrl = currentUrl,
                        )
                    }
                    else -> throw ModelInstallException("下载返回 HTTP $status：$currentUrl")
                }
            } finally {
                try {
                    exchange.close()
                } catch (e: IOException) {
                    // 关闭失败不影响已写入的数据；不覆盖主流程结果。
                }
            }
            return
        }
    }

    private fun writeBody(
        input: InputStream,
        partFile: File,
        append: Boolean,
        startOffset: Long,
        totalBytes: Long,
        onProgress: DownloadProgress?,
        sourceUrl: String,
    ) {
        try {
            FileOutputStream(partFile, append).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var offset = startOffset
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    offset += read
                    onProgress?.onProgress(offset, totalBytes)
                }
                output.flush()
                output.fd.sync()
            }
        } catch (e: IOException) {
            throw ModelInstallException("写入下载文件失败：$sourceUrl", e)
        }
    }

    private fun requireHttps(url: String) {
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw ModelInstallException("模型 URL 非法：$url", e)
            }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw ModelInstallException("只允许 HTTPS 下载：$url")
        }
        if (uri.host.isNullOrBlank()) throw ModelInstallException("模型 URL 缺少主机：$url")
    }

    private fun resolveRedirect(currentUrl: String, location: String): String {
        val resolved =
            try {
                URI(currentUrl).resolve(location)
            } catch (e: Exception) {
                throw ModelInstallException("重定向地址非法：$location", e)
            }
        if (resolved.scheme == null || resolved.host.isNullOrBlank()) {
            throw ModelInstallException("重定向地址必须是绝对 URL：$location")
        }
        return resolved.toString()
    }

    private fun modelsDir(): File = File(rootDir, MODELS_DIR)

    private fun stagingDir(): File = File(rootDir, STAGING_DIR)

    private fun downloadsDir(): File = File(rootDir, DOWNLOADS_DIR)

    private data class Origin(val scheme: String, val host: String, val port: Int) {
        companion object {
            fun parse(url: String): Origin? =
                try {
                    val uri = URI(url)
                    val scheme = uri.scheme?.lowercase() ?: return null
                    val host = uri.host?.lowercase() ?: return null
                    val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
                    Origin(scheme, host, port)
                } catch (e: URISyntaxException) {
                    null
                }
        }
    }

    companion object {
        /** 默认最多跟随的重定向跳数。 */
        const val DEFAULT_REDIRECT_LIMIT = 5

        const val MODELS_DIR = "models"
        const val STAGING_DIR = "staging"
        const val DOWNLOADS_DIR = "downloads"

        private const val AUTHORIZATION = "Authorization"
        private const val BUFFER_BYTES = 64 * 1024
    }
}
