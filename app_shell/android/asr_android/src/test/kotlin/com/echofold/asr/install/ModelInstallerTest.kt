package com.echofold.asr.install

import com.echofold.asr.model.ModelChannel
import com.echofold.asr.model.ModelManifest
import com.echofold.asr.model.ModelSource
import com.echofold.asr.repository.InstallationState
import com.echofold.asr.repository.ModelSnapshotJson
import com.echofold.asr.testing.FakeHttpRangeClient
import com.echofold.asr.testing.FakeHttpRangeClient.ScriptedResponse
import com.echofold.asr.testing.manifestForArtifact
import com.echofold.asr.testing.testManifest
import com.echofold.asr.testing.verifiedManual
import com.echofold.asr.testing.verifiedRemote
import com.echofold.asr.testing.zipBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelInstallerTest {
    @TempDir
    lateinit var root: File

    private val deviceAbi = "arm64-v8a"
    private val runtimeVersion = "1.10.0"

    private fun installer(
        client: FakeHttpRangeClient,
        abi: String = deviceAbi,
        runtime: String = runtimeVersion,
        redirectLimit: Int = ModelInstaller.DEFAULT_REDIRECT_LIMIT,
    ): ModelInstaller =
        ModelInstaller(
            rootDir = root,
            httpClient = client,
            deviceAbi = abi,
            runtimeVersion = runtime,
            redirectLimit = redirectLimit,
            clock = { 1_234L },
        )

    private fun artifact(
        extraFiles: Map<String, ByteArray> = emptyMap(),
        baseManifest: ModelManifest = testManifest(),
    ): Triple<ByteArray, Map<String, ByteArray>, ModelManifest> {
        val files =
            mapOf(
                "encoder.onnx" to ByteArray(64) { 1 },
                "tokens.txt" to "a 0\nb 1\n".toByteArray(),
            ) + extraFiles
        val zip = zipBytes(files)
        return Triple(zip, files, manifestForArtifact(zip, files, baseManifest))
    }

    @Test
    fun `远程安装成功写入模型目录与快照`() {
        val (zip, _, manifest) = artifact()
        val client = FakeHttpRangeClient().enqueue(manifest.url, ScriptedResponse.Body(zip))

        val installed = installer(client).install(verifiedRemote(manifest))

        val directory = File(installed.directoryPath)
        assertTrue(directory.isDirectory)
        assertTrue(File(directory, "encoder.onnx").isFile)
        assertEquals(ModelSource.REMOTE, installed.source)
        assertEquals(InstallationState.ACTIVE, installed.state)
        val snapshot = ModelSnapshotJson.read(directory)
        assertNotNull(snapshot)
        assertEquals(manifest.sha256, snapshot!!.manifest.sha256)
        assertFalse(File(root, "downloads/${installed.installationId}.part").exists())
    }

    @Test
    fun `SHA-256 不匹配时不安装且保留当前安装`() {
        val (zip, _, manifest) = artifact()
        val client = FakeHttpRangeClient().enqueue(manifest.url, ScriptedResponse.Body(zip))
        val installer = installer(client)
        val existing = installer.install(verifiedRemote(manifest))
        assertEquals(1, File(root, ModelInstaller.MODELS_DIR).listFiles()!!.size)

        val (otherZip, _, otherManifest) = artifact(extraFiles = mapOf("second.txt" to ByteArray(8)))
        val tampered = otherZip.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        val client2 = FakeHttpRangeClient().enqueue(otherManifest.url, ScriptedResponse.Body(tampered))

        val error = assertThrows<ModelInstallException> { installer(client2).install(verifiedRemote(otherManifest)) }
        assertTrue(error.message!!.contains("SHA-256"))
        assertTrue(File(existing.directoryPath).isDirectory)
        assertEquals(1, File(root, ModelInstaller.MODELS_DIR).listFiles()!!.size)
        assertFalse(File(root, "downloads/${InstallationIds.of(otherManifest)}.part").exists())
    }

    @Test
    fun `断点续传按 Range 追加并拼接出完整工件`() {
        val (zip, _, manifest) = artifact()
        val installationId = InstallationIds.of(manifest)
        val half = zip.size / 2
        val partFile = File(root, "downloads/$installationId.part")
        partFile.parentFile!!.mkdirs()
        partFile.writeBytes(zip.copyOfRange(0, half))
        val client =
            FakeHttpRangeClient().enqueue(
                manifest.url,
                ScriptedResponse.Body(
                    bytes = zip.copyOfRange(half, zip.size),
                    status = 206,
                    contentRange = ContentRange(half.toLong(), (zip.size - 1).toLong(), zip.size.toLong()),
                ),
            )

        val installed = installer(client).install(verifiedRemote(manifest))

        assertEquals(half.toLong(), client.requests.single().range?.startInclusive)
        assertTrue(File(installed.directoryPath, "encoder.onnx").isFile)
    }

    @Test
    fun `服务器忽略 Range 时从头覆盖下载`() {
        val (zip, _, manifest) = artifact()
        val installationId = InstallationIds.of(manifest)
        val partFile = File(root, "downloads/$installationId.part")
        partFile.parentFile!!.mkdirs()
        partFile.writeBytes(zip.copyOfRange(0, zip.size / 3))
        val client = FakeHttpRangeClient().enqueue(manifest.url, ScriptedResponse.Body(zip))

        val installed = installer(client).install(verifiedRemote(manifest))

        assertEquals((zip.size / 3).toLong(), client.requests.single().range?.startInclusive)
        assertTrue(File(installed.directoryPath).isDirectory)
    }

    @Test
    fun `跨源重定向不转发 Authorization`() {
        val (zip, _, manifest) = artifact()
        val origin = manifest.url
        val redirected = "https://cdn.echofold.example/artifact.zip"
        val client =
            FakeHttpRangeClient()
                .enqueue(origin, ScriptedResponse.Redirect(redirected))
                .enqueue(redirected, ScriptedResponse.Body(zip))

        installer(client).install(verifiedRemote(manifest), authorization = "Bearer secret")

        assertEquals("Bearer secret", client.requests[0].headers["Authorization"])
        assertFalse(client.requests[1].headers.containsKey("Authorization"))
    }

    @Test
    fun `同源重定向保留 Authorization`() {
        val (zip, _, manifest) = artifact()
        val redirected = "https://models.echofold.example/redirected.zip"
        val client =
            FakeHttpRangeClient()
                .enqueue(manifest.url, ScriptedResponse.Redirect(redirected))
                .enqueue(redirected, ScriptedResponse.Body(zip))

        installer(client).install(verifiedRemote(manifest), authorization = "Bearer secret")

        assertEquals("Bearer secret", client.requests[1].headers["Authorization"])
    }

    @Test
    fun `重定向次数超过上限时失败`() {
        val (zip, _, manifest) = artifact()
        val client = FakeHttpRangeClient()
        val urls = (0..4).map { "https://models.echofold.example/hop-$it.zip" }
        client.enqueue(manifest.url, ScriptedResponse.Redirect(urls[0]))
        for (index in 0 until 4) {
            client.enqueue(urls[index], ScriptedResponse.Redirect(urls[index + 1]))
        }
        client.enqueue(urls[4], ScriptedResponse.Body(zip))

        val error = assertThrows<ModelInstallException> { installer(client, redirectLimit = 3).install(verifiedRemote(manifest)) }
        assertTrue(error.message!!.contains("重定向次数超过上限"))
        assertFalse(File(root, ModelInstaller.MODELS_DIR).exists())
    }

    @Test
    fun `拒绝重定向到 HTTP 的降级`() {
        val (_, _, manifest) = artifact()
        val client =
            FakeHttpRangeClient().enqueue(
                manifest.url,
                ScriptedResponse.Redirect("http://models.echofold.example/insecure.zip"),
            )

        val error = assertThrows<ModelInstallException> { installer(client).install(verifiedRemote(manifest)) }
        assertTrue(error.message!!.contains("HTTPS"))
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `拒绝非 HTTPS 的初始 URL`() {
        val manifest = testManifest(url = "http://models.echofold.example/a.zip")
        val client = FakeHttpRangeClient()

        val error = assertThrows<ModelInstallException> { installer(client).install(verifiedRemote(manifest)) }
        assertTrue(error.message!!.contains("HTTPS"))
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `ABI 不兼容时在下载前失败`() {
        val (_, _, manifest) = artifact(baseManifest = testManifest(abi = listOf("x86_64")))
        val client = FakeHttpRangeClient()

        val error = assertThrows<ModelInstallException> { installer(client).install(verifiedRemote(manifest)) }
        assertTrue(error.message!!.contains("ABI"))
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `运行时版本过低时在下载前失败`() {
        val (_, _, manifest) = artifact(baseManifest = testManifest(runtimeMinVersion = "9.0.0"))
        val client = FakeHttpRangeClient()

        val error = assertThrows<ModelInstallException> {
            installer(client, runtime = "1.10.0").install(verifiedRemote(manifest))
        }
        assertTrue(error.message!!.contains("运行时版本过低"))
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `解压总大小不一致时失败且不安装`() {
        val (zip, files, manifest) = artifact()
        val wrongManifest = manifest.copy(extractedSize = files.values.sumOf { it.size.toLong() } + 1)
        val client = FakeHttpRangeClient().enqueue(wrongManifest.url, ScriptedResponse.Body(zip))

        val error = assertThrows<ModelInstallException> { installer(client).install(verifiedRemote(wrongManifest)) }
        assertTrue(error.message!!.contains("解压总大小"))
        assertFalse(File(root, ModelInstaller.MODELS_DIR).exists())
    }

    @Test
    fun `拒绝 ZIP 路径越界`() {
        val files = mapOf("../escape.txt" to "evil".toByteArray())
        val zip = zipBytes(files)
        val manifest = manifestForArtifact(zip, files)
        val client = FakeHttpRangeClient().enqueue(manifest.url, ScriptedResponse.Body(zip))

        val error = assertThrows<ModelInstallException> { installer(client).install(verifiedRemote(manifest)) }
        assertTrue(error.message!!.contains("越界"))
        assertFalse(File(root, "escape.txt").exists())
    }

    @Test
    fun `手动导入标记 MANUAL 并记录用户确认`() {
        val (zip, _, manifest) = artifact(baseManifest = testManifest(channel = ModelChannel.MANUAL))
        val archive = File(root, "manual-model.zip")
        archive.writeBytes(zip)

        val installed = installer(FakeHttpRangeClient()).importManual(verifiedManual(manifest), archive)

        assertEquals(ModelSource.MANUAL, installed.source)
        assertTrue(installed.userConfirmed)
        val snapshot = ModelSnapshotJson.read(File(installed.directoryPath))!!
        assertEquals(ModelSource.MANUAL, snapshot.source)
        assertTrue(snapshot.userConfirmed)
    }

    @Test
    fun `手动导入要求 MANUAL 渠道`() {
        val (zip, _, manifest) = artifact()
        val archive = File(root, "manual-model.zip")
        archive.writeBytes(zip)

        val error = assertThrows<ModelInstallException> {
            installer(FakeHttpRangeClient()).importManual(verifiedManual(manifest), archive)
        }
        assertTrue(error.message!!.contains("MANUAL"))
    }

    @Test
    fun `受信安装拒绝 MANUAL 渠道清单`() {
        val (_, _, manifest) = artifact(baseManifest = testManifest(channel = ModelChannel.MANUAL))
        val error = assertThrows<ModelInstallException> {
            installer(FakeHttpRangeClient()).install(verifiedRemote(manifest))
        }
        assertTrue(error.message!!.contains("MANUAL"))
    }

    @Test
    fun `手动导入拒绝远程来源`() {
        val (zip, _, manifest) = artifact()
        val archive = File(root, "manual-model.zip")
        archive.writeBytes(zip)

        val error = assertThrows<ModelInstallException> {
            installer(FakeHttpRangeClient()).importManual(verifiedRemote(manifest), archive)
        }
        assertTrue(error.message!!.contains("MANUAL"))
        assertFalse(File(root, ModelInstaller.MODELS_DIR).exists())
    }

    @Test
    fun `手动导入文件 SHA-256 不一致时拒绝`() {
        val (zip, _, manifest) = artifact(baseManifest = testManifest(channel = ModelChannel.MANUAL))
        val archive = File(root, "manual-model.zip")
        archive.writeBytes(zip)
        archive.writeBytes(zip.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() })

        val error = assertThrows<ModelInstallException> {
            installer(FakeHttpRangeClient()).importManual(verifiedManual(manifest), archive)
        }
        assertTrue(error.message!!.contains("SHA-256"))
    }
}
