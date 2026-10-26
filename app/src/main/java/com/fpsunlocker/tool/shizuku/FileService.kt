package com.fpsunlocker.tool.shizuku

import com.fpsunlocker.tool.IFileService
import java.io.File
import kotlin.system.exitProcess

/**
 * Shizuku UserService: Shizuku สร้างคลาสนี้ในโปรเซสแยกที่มีสิทธิ์ shell (uid 2000)
 * shell อ่าน/เขียน /storage/emulated/0/Android/data/<package> ของแอปอื่นได้ จึงใช้ java.io.File ตรงๆ ได้
 *
 * Binder ส่งต่อได้เฉพาะ exception บางชนิด (เช่น IllegalStateException)
 * จึงห่อ IOException ทุกตัวเป็น IllegalStateException เพื่อให้ข้อความ error กลับไปถึงแอป
 */
class FileService : IFileService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun readText(path: String?): String = wrap {
        File(requireNotNull(path)).readText(Charsets.UTF_8)
    }

    override fun writeText(path: String?, content: String?) = wrap {
        File(requireNotNull(path)).writeText(content.orEmpty(), Charsets.UTF_8)
    }

    override fun copy(src: String?, dst: String?) = wrap {
        File(requireNotNull(src)).copyTo(File(requireNotNull(dst)), overwrite = true)
        Unit
    }

    override fun exists(path: String?): Boolean = wrap {
        File(requireNotNull(path)).exists()
    }

    override fun size(path: String?): Long = wrap {
        val f = File(requireNotNull(path))
        if (f.isFile) f.length() else -1L
    }

    override fun listFiles(dir: String?, maxDepth: Int, limit: Int): MutableList<String> = wrap {
        val root = File(requireNotNull(dir))
        if (!root.isDirectory) throw IllegalStateException("ไม่พบโฟลเดอร์ $dir")
        root.walkTopDown()
            .maxDepth(maxDepth)
            .filter { it.isFile }
            .take(limit)
            .map { it.relativeTo(root).path }
            .toMutableList()
    }

    override fun exec(command: String?): String = wrap {
        val process = ProcessBuilder("sh", "-c", requireNotNull(command))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    }

    private inline fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("${e.javaClass.simpleName}: ${e.message}")
        }
}
