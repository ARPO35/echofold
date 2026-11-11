package com.echofold.asr.internal

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * 同卷原子文件操作。
 *
 * 持久化状态（安装索引、租约、已接受代数）必须“写临时文件 → fsync → 改名”，
 * 崩溃后才能只看到旧值或新值，而不是半个 JSON。
 */
object AtomicFiles {
    /** 以 UTF-8 原子写入文本文件。 */
    @Throws(IOException::class)
    fun writeText(file: File, text: String) {
        val parent = file.absoluteFile.parentFile ?: throw IOException("无法确定文件父目录：$file")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("无法创建目录：$parent")
        }
        val temp = File(parent, "${file.name}.tmp-${UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            moveAtomically(temp, file, replaceExisting = true)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** 文件级原子改名；同卷改名是原子的，跨卷实现会退化为复制（由调用方保证同卷）。 */
    @Throws(IOException::class)
    fun moveAtomically(source: File, target: File, replaceExisting: Boolean) {
        val options =
            if (replaceExisting) {
                arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else {
                arrayOf(StandardCopyOption.ATOMIC_MOVE)
            }
        try {
            Files.move(source.toPath(), target.toPath(), *options)
        } catch (e: AtomicMoveNotSupportedException) {
            val fallback =
                if (replaceExisting) {
                    arrayOf(StandardCopyOption.REPLACE_EXISTING)
                } else {
                    emptyArray()
                }
            Files.move(source.toPath(), target.toPath(), *fallback)
        }
    }

    /** 目录原子改名；目标必须不存在。 */
    @Throws(IOException::class)
    fun moveDirectoryAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }
}

/** 计算文件 SHA-256，输出 64 位小写十六进制。 */
@Throws(IOException::class)
fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val bytes = digest.digest()
    val builder = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        builder.append(HEX[value shr 4]).append(HEX[value and 0xF])
    }
    return builder.toString()
}

private const val HEX = "0123456789abcdef"
