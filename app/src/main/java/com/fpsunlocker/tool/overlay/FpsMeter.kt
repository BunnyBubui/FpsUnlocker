package com.fpsunlocker.tool.overlay

import com.fpsunlocker.tool.IFileService

/**
 * วัด FPS ด้วยข้อมูลจาก SurfaceFlinger (ตัวประกอบภาพของ Android) ผ่านสิทธิ์ shell ของ Shizuku
 *
 * วิธีที่ 1 (แม่นกว่า วัดเฉพาะเกม):
 *   dumpsys SurfaceFlinger --list            → หา layer ที่เกมวาดภาพลงไป (มักเป็น SurfaceView ของเกม)
 *   dumpsys SurfaceFlinger --latency <layer> → เวลาที่แต่ละเฟรมขึ้นจอจริง (ล่าสุดประมาณ 127 เฟรม)
 *   FPS = (จำนวนเฟรมใน 1 วินาทีล่าสุด - 1) / ช่วงเวลาระหว่างเฟรมแรกกับเฟรมสุดท้าย
 *
 * วิธีที่ 2 (สำรอง วัดทั้งจอ): service call SurfaceFlinger 1013 = จำนวนเฟรมที่จอวาดสะสม
 *   FPS = ผลต่างของตัวนับ / เวลาที่ผ่านไป
 *
 * Android บางรุ่นหรือบางยี่ห้อปิดคำสั่งเหล่านี้ไว้ ถ้าใช้ไม่ได้ทั้งสองวิธีจะคืนค่า null
 */
class FpsMeter(private val service: IFileService, private val packageName: String?) {

    var method: String = "—"
        private set

    private var layer: String? = null
    private var layerLookupAt = 0L
    private var lastPresent = 0L
    private var zeroStreak = 0

    private var flipCount: Long? = null
    private var flipAt = 0L

    fun sample(): Float? {
        val pkg = packageName
        if (pkg != null) {
            val now = System.nanoTime()
            if (layer == null || now - layerLookupAt > LAYER_REFRESH_NS) {
                layer = runCatching { findLayer(pkg) }.getOrNull()
                layerLookupAt = now
            }
            layer?.let { name ->
                val fps = runCatching { latencyFps(name) }.getOrNull()
                if (fps != null && fps > 0f) {
                    zeroStreak = 0
                    method = "เฉพาะเกม"
                    return fps
                }
                // ได้ 0 ติดกันหลายครั้ง อาจเลือก layer ผิด → หา layer ใหม่ และใช้วิธีวัดทั้งจอไปก่อน
                if (fps == 0f && ++zeroStreak >= 3) {
                    layer = null
                    zeroStreak = 0
                }
            }
        }
        return runCatching { pageFlipFps() }.getOrNull()?.also { method = "ทั้งจอ" }
    }

    private fun findLayer(pkg: String): String? {
        val lines = service.exec("dumpsys SurfaceFlinger --list")
            .lines()
            .map { it.trim() }
            .filter { it.contains(pkg) && !it.contains("Background") }
        return lines.firstOrNull { it.contains("SurfaceView") && it.contains("BLAST") }
            ?: lines.firstOrNull { it.contains("SurfaceView") }
            ?: lines.firstOrNull { it.contains("BLAST") }
            ?: lines.lastOrNull()
    }

    private fun latencyFps(layerName: String): Float? {
        val safe = layerName.replace("'", "")
        val stamps = service.exec("dumpsys SurfaceFlinger --latency '$safe'")
            .lines()
            .drop(1) // บรรทัดแรกคือ refresh period
            .mapNotNull { line ->
                val parts = line.trim().split(WHITESPACE)
                if (parts.size >= 3) parts[1].toLongOrNull() else null // คอลัมน์ 2 = เวลาที่ขึ้นจอจริง
            }
            .filter { it > 0 && it != Long.MAX_VALUE }
        if (stamps.size < 2) return null

        val last = stamps.max()
        if (last == lastPresent) return 0f // ไม่มีเฟรมใหม่ (เกมหยุด หรืออยู่หน้าโหลด)
        lastPresent = last

        val window = stamps.filter { it > last - ONE_SECOND_NS }.sorted()
        if (window.size < 2) return null
        val span = window.last() - window.first()
        if (span <= 0) return null
        return (window.size - 1) * 1_000_000_000f / span
    }

    private fun pageFlipFps(): Float? {
        val out = service.exec("service call SurfaceFlinger 1013")
        val match = PARCEL.find(out) ?: return null
        val count = match.groupValues[2].toLong(16)
        val now = System.nanoTime()
        val previous = flipCount
        val previousAt = flipAt
        flipCount = count
        flipAt = now
        if (previous == null || count < previous) return null
        val elapsed = now - previousAt
        if (elapsed <= 0) return null
        return (count - previous) * 1_000_000_000f / elapsed
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PARCEL = Regex("""Parcel\(\s*([0-9a-fA-F]{8})\s+([0-9a-fA-F]{8})""")
        const val ONE_SECOND_NS = 1_000_000_000L
        const val LAYER_REFRESH_NS = 5_000_000_000L
    }
}
