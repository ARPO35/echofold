package com.echofold.asr.repository

import com.echofold.asr.model.ModelSource
import com.echofold.asr.testing.InMemoryLeaseStore
import com.echofold.asr.testing.InMemoryModelIndexStore
import com.echofold.asr.testing.testManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelRepositoryTest {
    @TempDir
    lateinit var root: File

    private val indexStore = InMemoryModelIndexStore()
    private val leaseStore = InMemoryLeaseStore()
    private val leases = ModelLeaseManager(leaseStore, clock = { 100L })
    private val repository = ModelRepository(indexStore, leases)

    private fun install(name: String, installedAtUtcMs: Long = 100L): InstalledModel {
        val directory = File(root, name)
        check(directory.mkdirs() || directory.isDirectory)
        val installed =
            InstalledModel(
                installationId = name,
                manifest = testManifest(),
                source = ModelSource.REMOTE,
                userConfirmed = false,
                installedAtUtcMs = installedAtUtcMs,
                directoryPath = directory.absolutePath,
            )
        return repository.install(installed)
    }

    @Test
    fun `安装新模型后当前选择更新并可回滚`() {
        val first = install("install-1", installedAtUtcMs = 100L)
        val second = install("install-2", installedAtUtcMs = 200L)

        assertEquals(second.installationId, repository.current()!!.installationId)
        assertEquals(first.installationId, repository.rollback().installationId)
        assertEquals(first.installationId, repository.current()!!.installationId)
        assertEquals(second.installationId, repository.rollback().installationId)
    }

    @Test
    fun `没有上一个安装时回滚明确失败`() {
        install("install-1")
        assertThrows<IllegalStateException> { repository.rollback() }
    }

    @Test
    fun `索引跨仓库实例持久化`() {
        install("install-1")
        val reloaded = ModelRepository(indexStore, ModelLeaseManager(leaseStore))
        assertEquals("install-1", reloaded.current()!!.installationId)
    }

    @Test
    fun `重复登记同一 installationId 幂等`() {
        val first = install("install-1")
        val again = repository.install(first.copy(directoryPath = File(root, "install-1").absolutePath))
        assertEquals(first, again)
        assertEquals(1, repository.list().size)
    }

    @Test
    fun `pending-delete 在有租约时保留文件并阻止新租约`() {
        install("install-1")
        val lease = repository.acquireLease("install-1", "session-1")

        repository.requestDelete("install-1")

        val pending = repository.find("install-1")!!
        assertEquals(InstallationState.PENDING_DELETE, pending.state)
        assertTrue(File(pending.directoryPath).isDirectory)
        assertNull(repository.current())
        assertThrows<LeaseUnavailableException> { repository.acquireLease("install-1", "session-2") }

        assertTrue(repository.releaseLease(lease.leaseId))
        assertNull(repository.find("install-1"))
        assertFalse(File(pending.directoryPath).exists())
    }

    @Test
    fun `最后租约释放才触发物理删除`() {
        install("install-1")
        val first = repository.acquireLease("install-1", "session-1")
        val second = repository.acquireLease("install-1", "session-2")
        repository.requestDelete("install-1")

        repository.releaseLease(first.leaseId)
        assertTrue(File(root, "install-1").isDirectory)

        repository.releaseLease(second.leaseId)
        assertFalse(File(root, "install-1").exists())
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun `无租约时请求删除立即清理`() {
        install("install-1")
        repository.requestDelete("install-1")
        assertTrue(repository.list().isEmpty())
        assertFalse(File(root, "install-1").exists())
    }

    @Test
    fun `pending-delete 安装不能被选为当前模型`() {
        install("install-1")
        install("install-2", installedAtUtcMs = 200L)
        repository.requestDelete("install-2")
        assertNull(repository.find("install-2"))
        assertThrows<IllegalArgumentException> { repository.select("install-2") }
        assertEquals("install-1", repository.current()!!.installationId)
    }

    @Test
    fun `重启恢复释放失效租约并清理 pending-delete`() {
        val directory = File(root, "install-1")
        directory.mkdirs()
        val installed =
            InstalledModel(
                installationId = "install-1",
                manifest = testManifest(),
                source = ModelSource.REMOTE,
                userConfirmed = false,
                installedAtUtcMs = 1L,
                directoryPath = directory.absolutePath,
                state = InstallationState.PENDING_DELETE,
            )
        indexStore.index = ModelIndex(installations = listOf(installed))
        leaseStore.state =
            LeaseState(
                leases = listOf(ModelLease("lease-old", "install-1", "session-old", 1L)),
                notAcquirable = setOf("install-1"),
            )
        val recovered = ModelRepository(indexStore, ModelLeaseManager(leaseStore))

        val report = recovered.recover(emptySet())

        assertEquals(listOf("lease-old"), report.releasedLeases.map { it.leaseId })
        assertEquals(listOf("install-1"), report.deletedInstallations)
        assertFalse(directory.exists())
        assertTrue(recovered.list().isEmpty())
    }

    @Test
    fun `重启恢复保留活跃会话租约与 pending-delete 文件`() {
        val directory = File(root, "install-1")
        directory.mkdirs()
        indexStore.index =
            ModelIndex(
                installations =
                    listOf(
                        InstalledModel(
                            installationId = "install-1",
                            manifest = testManifest(),
                            source = ModelSource.REMOTE,
                            userConfirmed = false,
                            installedAtUtcMs = 1L,
                            directoryPath = directory.absolutePath,
                            state = InstallationState.PENDING_DELETE,
                        ),
                    ),
            )
        leaseStore.state =
            LeaseState(
                leases = listOf(ModelLease("lease-alive", "install-1", "session-alive", 1L)),
                notAcquirable = setOf("install-1"),
            )
        val recovered = ModelRepository(indexStore, ModelLeaseManager(leaseStore))

        val report = recovered.recover(setOf("session-alive"))

        assertTrue(report.releasedLeases.isEmpty())
        assertTrue(report.deletedInstallations.isEmpty())
        assertTrue(directory.isDirectory)
        assertNotNull(recovered.find("install-1"))
    }

    @Test
    fun `重启恢复标记目录缺失的安装并清理索引`() {
        val directory = File(root, "install-missing")
        directory.mkdirs()
        indexStore.index =
            ModelIndex(
                selectedInstallationId = "install-missing",
                installations =
                    listOf(
                        InstalledModel(
                            installationId = "install-missing",
                            manifest = testManifest(),
                            source = ModelSource.REMOTE,
                            userConfirmed = false,
                            installedAtUtcMs = 1L,
                            directoryPath = directory.absolutePath,
                        ),
                    ),
            )
        directory.deleteRecursively()
        val recovered = ModelRepository(indexStore, ModelLeaseManager(leaseStore))

        val report = recovered.recover(emptySet())

        assertEquals(listOf("install-missing"), report.missingInstallations)
        assertEquals(listOf("install-missing"), report.deletedInstallations)
        assertTrue(recovered.list().isEmpty())
    }

    @Test
    fun `删除某所有者全部租约后清理 pending-delete`() {
        install("install-1")
        repository.acquireLease("install-1", "session-1")
        repository.acquireLease("install-1", "session-1")
        repository.requestDelete("install-1")

        assertEquals(2, repository.releaseAllForOwner("session-1"))
        assertTrue(repository.list().isEmpty())
        assertFalse(File(root, "install-1").exists())
    }
}
