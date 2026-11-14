package com.echofold.capture.encoder

import android.system.Os
import com.echofold.core.chunk.SpaceBudget
import java.io.File
import java.io.RandomAccessFile

/** core 预算的设备实现：用独占 reserve 文件实际预分配磁盘块，封存前归还作为收尾空间。 */
class RecordingSpaceBudget(private val file: File) : SpaceBudget {
    private var held = 0L
    override fun reserve(bytes: Long): Boolean {
        require(bytes > 0)
        check(held == 0L) { "已有封存预留" }
        val parent = checkNotNull(file.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) return false
        if (!file.createNewFile()) throw RecordingStorageException("RESERVATION_FAILED", "封存预留文件已存在，禁止覆盖")
        try {
            RandomAccessFile(file, "rw").use {
                Os.posix_fallocate(it.fd, 0L, bytes)
                it.fd.sync()
            }
            held = bytes
            return true
        } catch (error: Exception) {
            if (!file.delete()) throw RecordingStorageException("RESERVATION_FAILED", "封存预留失败且无法清理预留文件", error)
            return false
        }
    }

    override fun release(bytes: Long) {
        if (held == 0L) return
        check(bytes == held) { "封存预留额度不匹配" }
        RandomAccessFile(file, "rw").use { it.setLength(0); it.fd.sync() }
        check(file.delete()) { "无法释放封存预留文件" }
        held = 0
    }
}
