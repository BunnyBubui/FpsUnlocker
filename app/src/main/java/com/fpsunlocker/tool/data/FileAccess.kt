package com.fpsunlocker.tool.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fpsunlocker.tool.IFileService
import java.io.FileNotFoundException

/**
 * ช่องทางเข้าถึงไฟล์ในโฟลเดอร์ Android/data/<package> ของเกม
 * ทุก path เป็น path แบบ relative จากโฟลเดอร์นั้น เช่น "files/config/GameUserSettings.ini"
 * เมธอดทั้งหมดเป็น blocking ต้องเรียกจาก Dispatchers.IO
 */
data class FileEntry(val path: String, val size: Long)

interface FileAccess {
    val label: String
    fun readText(rel: String): String
    fun writeText(rel: String, content: String)
    fun copy(srcRel: String, dstRel: String)
    fun exists(rel: String): Boolean
    fun size(rel: String): Long
    /** รายชื่อไฟล์ขนาด 1..maxSize ใต้ [subDir] (path ที่คืนนับจากโฟลเดอร์ของเกม) */
    fun listFiles(subDir: String, maxDepth: Int, limit: Int, maxSize: Long): List<FileEntry>

    /** ชื่อโฟลเดอร์ย่อยชั้นเดียวใต้ [rel] */
    fun listDirs(rel: String): List<String>
}

/** ใช้ได้ทุก Android 11+ ต้องเปิด Shizuku และอนุญาตแอปนี้ก่อน */
class ShizukuFileAccess(
    private val service: IFileService,
    private val rootDir: String,
) : FileAccess {
    override val label = "Shizuku"

    private fun abs(rel: String) = "$rootDir/${rel.trimStart('/')}"

    override fun readText(rel: String): String = service.readText(abs(rel))
    override fun writeText(rel: String, content: String) = service.writeText(abs(rel), content)
    override fun copy(srcRel: String, dstRel: String) = service.copy(abs(srcRel), abs(dstRel))
    override fun exists(rel: String): Boolean = service.exists(abs(rel))
    override fun size(rel: String): Long = service.size(abs(rel))
    override fun listFiles(subDir: String, maxDepth: Int, limit: Int, maxSize: Long): List<FileEntry> {
        val prefix = subDir.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return service.listFiles(abs(subDir), maxDepth, limit, maxSize).map { line ->
            FileEntry(prefix + line.substringBeforeLast('\t'), line.substringAfterLast('\t').toLongOrNull() ?: -1L)
        }
    }

    override fun listDirs(rel: String): List<String> = service.listDirs(abs(rel))

    fun exec(command: String): String = service.exec(command)
}

/**
 * ใช้ได้เฉพาะ Android 11–12 (API 30–32)
 * ตั้งแต่ Android 13 ตัวเลือกโฟลเดอร์ของระบบไม่ยอมให้เลือก Android/data แล้ว
 */
class SafFileAccess(
    private val context: Context,
    treeUri: Uri,
) : FileAccess {
    override val label = "SAF"

    private val root: DocumentFile = DocumentFile.fromTreeUri(context, treeUri)
        ?: throw IllegalStateException("เปิดโฟลเดอร์ที่ได้สิทธิ์ไว้ไม่ได้ ลองขอสิทธิ์ใหม่")

    val rootName: String? get() = root.name

    private fun segments(rel: String) = rel.split('/').filter { it.isNotEmpty() }

    private fun find(rel: String): DocumentFile? {
        var current = root
        for (seg in segments(rel)) {
            current = current.findFile(seg) ?: return null
        }
        return current
    }

    private fun require(rel: String) = find(rel) ?: throw FileNotFoundException(rel)

    private fun createFile(rel: String): DocumentFile {
        val parts = segments(rel)
        val parent = if (parts.size > 1) require(parts.dropLast(1).joinToString("/")) else root
        return parent.createFile("application/octet-stream", parts.last())
            ?: throw IllegalStateException("สร้างไฟล์ $rel ไม่ได้")
    }

    private fun readBytes(rel: String): ByteArray =
        context.contentResolver.openInputStream(require(rel).uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("เปิดไฟล์ $rel ไม่ได้")

    private fun writeBytes(rel: String, bytes: ByteArray) {
        val file = find(rel) ?: createFile(rel)
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("เขียนไฟล์ $rel ไม่ได้")
    }

    override fun readText(rel: String) = readBytes(rel).toString(Charsets.UTF_8)
    override fun writeText(rel: String, content: String) = writeBytes(rel, content.toByteArray(Charsets.UTF_8))
    override fun copy(srcRel: String, dstRel: String) = writeBytes(dstRel, readBytes(srcRel))
    override fun exists(rel: String) = find(rel)?.exists() == true
    override fun size(rel: String) = find(rel)?.takeIf { it.isFile }?.length() ?: -1L

    override fun listDirs(rel: String): List<String> =
        find(rel)?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.sorted() ?: emptyList()

    override fun listFiles(subDir: String, maxDepth: Int, limit: Int, maxSize: Long): List<FileEntry> {
        val start = find(subDir)?.takeIf { it.isDirectory } ?: return emptyList()
        val out = mutableListOf<FileEntry>()
        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            if (depth > maxDepth || out.size >= limit) return
            for (child in dir.listFiles()) {
                if (out.size >= limit) return
                val name = child.name ?: continue
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    walk(child, path, depth + 1)
                } else if (child.length() in 1..maxSize) {
                    out += FileEntry(path, child.length())
                }
            }
        }
        walk(start, subDir.trim('/'), 1)
        return out
    }
}
