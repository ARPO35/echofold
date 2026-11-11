package com.echofold.asr.engine

import com.echofold.asr.model.ModelSource
import com.echofold.asr.repository.InstallationState
import com.echofold.asr.repository.InstalledModel
import com.echofold.asr.repository.ModelIndex
import com.echofold.asr.repository.ModelLeaseManager
import com.echofold.asr.repository.ModelRepository
import com.echofold.asr.testing.FakeSherpaRuntime
import com.echofold.asr.testing.InMemoryLeaseStore
import com.echofold.asr.testing.InMemoryModelIndexStore
import com.echofold.asr.testing.asrConfig
import com.echofold.asr.testing.createModelDirectory
import com.echofold.asr.testing.installedModel
import com.echofold.asr.testing.testManifest
import com.echofold.core.asr.engine.AsrEngineException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AsrEngineProviderTest {
    @TempDir
    lateinit var root: File

    private val manifest = testManifest()
    private val installationId = "install-1"
    private val indexStore = InMemoryModelIndexStore()
    private val repository = ModelRepository(indexStore, ModelLeaseManager(InMemoryLeaseStore(), clock = { 10L }))
    private val runtime = FakeSherpaRuntime()

    private fun provider(
        runtimeVersion: String = "1.10.0",
        deviceAbis: List<String> = listOf("arm64-v8a"),
    ): AsrEngineProvider =
        AsrEngineProvider(
            repository = repository,
            runtimeProvider = { runtime },
            runtimeVersion = runtimeVersion,
            deviceAbis = deviceAbis,
        )

    private fun register(
        installedManifest: com.echofold.asr.model.ModelManifest = manifest,
        id: String = installationId,
        state: InstallationState = InstallationState.ACTIVE,
    ): InstalledModel {
        val directory = createModelDirectory(root, id, installedManifest)
        val installed = installedModel(directory, installedManifest, installationId = id).copy(state = state)
        repository.install(installed, select = true)
        return installed
    }

    @Test
    fun `按会话固定模型创建并初始化引擎`() {
        register()
        val engine = provider().create(asrConfig(manifest, installationId))

        assertEquals(1, runtime.createCount)
        assertTrue(runtime.createdPaths!!.encoder.name.startsWith("encoder"))
        engine.close()
    }

    @Test
    fun `模型安装不存在时明确失败`() {
        val error = assertThrows<AsrEngineException> {
            provider().create(asrConfig(manifest, "missing-install"))
        }
        assertTrue(error.message!!.contains("不存在"))
        assertEquals(0, runtime.createCount)
    }

    @Test
    fun `模型身份不一致时失败`() {
        register()
        val other = manifest.copy(sha256 = "b".repeat(64))
        val error = assertThrows<AsrEngineException> { provider().create(asrConfig(other, installationId)) }
        assertTrue(error.message!!.contains("身份不一致"))
        assertEquals(0, runtime.createCount)
    }

    @Test
    fun `ABI 不兼容时失败`() {
        register(installedManifest = manifest.copy(abi = listOf("x86_64")))
        val error = assertThrows<AsrEngineException> { provider().create(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("ABI"))
    }

    @Test
    fun `运行时版本低于清单下限时失败`() {
        register()
        val error = assertThrows<AsrEngineException> {
            provider(runtimeVersion = "1.9.9").create(asrConfig(manifest, installationId))
        }
        assertTrue(error.message!!.contains("运行时版本过低"))
    }

    @Test
    fun `pending-delete 且无活跃租约时不能用于新会话`() {
        register(state = InstallationState.PENDING_DELETE)
        val error = assertThrows<AsrEngineException> { provider().create(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("pending-delete"))
        assertEquals(0, runtime.createCount)
    }

    @Test
    fun `pending-delete 但持有租约时仍可用固定模型`() {
        register()
        val lease = repository.acquireLease(installationId, "session-1")
        repository.requestDelete(installationId)

        val engine = provider().create(asrConfig(manifest, installationId))
        assertEquals(1, runtime.createCount)

        engine.close()
        repository.releaseLease(lease.leaseId)
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun `初始化失败时关闭引擎并抛出明确异常`() {
        register()
        runtime.createFailure = IllegalStateException("native boom")
        val error = assertThrows<AsrEngineException> { provider().create(asrConfig(manifest, installationId)) }
        assertTrue(error.message!!.contains("初始化失败"))
    }

    @Test
    fun `索引被替换后 provider 仍固定创建时解析的目录`() {
        register()
        val engine = provider().create(asrConfig(manifest, installationId))
        val installed = repository.find(installationId)!!
        assertEquals(ModelSource.REMOTE, installed.source)
        engine.close()
        assertEquals(1, indexStore.index.installations.size)
    }
}
