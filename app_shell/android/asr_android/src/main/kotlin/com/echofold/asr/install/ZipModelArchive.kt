package com.echofold.asr.install

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** 安装/下载失败；消息面向用户与日志，必须包含失败阶段与可操作信息。 */
class ModelInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 模型工件解包（ZIP 容器）。
 *
 * 清单的 `compressedSize/extractedSize` 对应压缩包与解压后文件总字节；解包在独立临时目录完成，
 * 只有解压总大小与清单一致才允许原子安装。拒绝绝对路径、`..` 越界与重复条目，防止 ZIP Slip
 * 或覆盖。
 */
internal object ZipModelArchive {
    /** 解压到 [destination]（必须已存在），返回解压后文件字节总数。 */
    fun extract(archive: File, destination: File): Long {
        require(destination.isDirectory) { "解压目标目录不存在：$destination" }
        val destinationRoot = destination.canonicalFile
        var total = 0L
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.isBlank()) throw ModelInstallException("ZIP 条目名称为空")
                    val target = File(destinationRoot, name).canonicalFile
                    if (!isInside(destinationRoot, target)) {
                        throw ModelInstallException("ZIP 条目越界：$name")
                    }
                    if (entry.isDirectory) {
                        if (!target.isDirectory && !target.mkdirs() && !target.isDirectory) {
                            throw ModelInstallException("无法创建 ZIP 目录：$name")
                        }
                    } else {
                        val parent = target.parentFile
                        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                            throw ModelInstallException("无法创建 ZIP 目录：${parent.absolutePath}")
                        }
                        if (target.exists()) throw ModelInstallException("ZIP 条目重复：$name")
                        FileOutputStream(target).use { output ->
                            total += copyEntry(zip, output)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: ModelInstallException) {
            throw e
        } catch (e: Exception) {
            throw ModelInstallException("解包模型工件失败：${archive.name}", e)
        }
        return total
    }

    private fun isInside(root: File, target: File): Boolean {
        val rootPath = root.path
        return target.path == rootPath || target.path.startsWith(rootPath + File.separator)
    }

    private fun copyEntry(zip: ZipInputStream, output: FileOutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
        }
        output.flush()
        output.fd.sync()
        return written
    }
}
