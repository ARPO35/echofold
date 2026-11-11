package com.echofold.asr.repository

import com.echofold.asr.testing.InMemoryLeaseStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelLeaseManagerTest {
    private val store = InMemoryLeaseStore()
    private var counter = 0
    private val manager =
        ModelLeaseManager(
            store = store,
            clock = { 500L },
            leaseIdFactory = { "lease-${counter++}" },
        )

    @Test
    fun `申请与释放维护引用计数`() {
        val first = manager.acquire("install-1", "session-a")
        val second = manager.acquire("install-1", "session-b")

        assertEquals(2, manager.refCount("install-1"))
        assertEquals(listOf("lease-0", "lease-1"), manager.leasesFor("install-1").map { it.leaseId })
        assertTrue(manager.release(first.leaseId))
        assertEquals(1, manager.refCount("install-1"))
        assertTrue(manager.release(second.leaseId))
        assertEquals(0, manager.refCount("install-1"))
        assertFalse(manager.release(second.leaseId))
    }

    @Test
    fun `租约与禁止标记跨实例持久化`() {
        manager.acquire("install-1", "session-a")
        manager.markNotAcquirable("install-2")

        val reloaded = ModelLeaseManager(store, clock = { 500L })
        assertEquals(1, reloaded.refCount("install-1"))
        assertFalse(reloaded.isAcquirable("install-2"))
        assertThrows<LeaseUnavailableException> { reloaded.acquire("install-2", "session-b") }
    }

    @Test
    fun `释放某租约所有者全部租约`() {
        manager.acquire("install-1", "session-a")
        manager.acquire("install-1", "session-a")
        manager.acquire("install-2", "session-b")

        assertEquals(2, manager.releaseAllForOwner("session-a"))
        assertEquals(0, manager.refCount("install-1"))
        assertEquals(1, manager.refCount("install-2"))
        assertEquals(0, manager.releaseAllForOwner("session-a"))
    }

    @Test
    fun `引用计数非零时拒绝物理删除`() {
        manager.markNotAcquirable("install-1")
        val lease = ModelLease("external", "install-1", "session-a", 1L)
        store.save(LeaseState(listOf(lease), setOf("install-1")))
        val reloaded = ModelLeaseManager(store)

        var deleted = false
        assertFalse(reloaded.deleteIfUnused("install-1") { deleted = true })
        assertFalse(deleted)
    }

    @Test
    fun `最后租约释放后允许物理删除`() {
        val lease = manager.acquire("install-1", "session-a")
        manager.markNotAcquirable("install-1")
        var deleted = false
        assertFalse(manager.deleteIfUnused("install-1") { deleted = true })

        assertTrue(manager.release(lease.leaseId))
        assertTrue(manager.deleteIfUnused("install-1") { deleted = true })
        assertTrue(deleted)
        assertEquals(0, manager.refCount("install-1"))
    }

    @Test
    fun `未标记不可获取时拒绝删除以防误删`() {
        assertThrows<IllegalArgumentException> { manager.deleteIfUnused("install-1") {} }
    }

    @Test
    fun `重启恢复释放非活跃持有者的租约并保留活跃租约`() {
        manager.acquire("install-1", "session-alive")
        manager.acquire("install-2", "session-dead")

        val report = manager.recover(setOf("session-alive"))

        assertEquals(listOf("session-dead"), report.released.map { it.ownerId })
        assertEquals(listOf("session-alive"), report.retained.map { it.ownerId })
        assertEquals(1, manager.refCount("install-1"))
        assertEquals(0, manager.refCount("install-2"))
    }

    @Test
    fun `恢复保留禁止获取标记`() {
        manager.markNotAcquirable("install-1")
        manager.recover(emptySet())

        val reloaded = ModelLeaseManager(store)
        assertFalse(reloaded.isAcquirable("install-1"))
    }

    @Test
    fun `租约记录包含安装与时间`() {
        val lease = manager.acquire("install-1", "session-a")
        assertEquals(500L, lease.acquiredAtUtcMs)
        assertEquals("install-1", lease.installationId)
        assertEquals("lease-0", lease.leaseId)
    }
}
