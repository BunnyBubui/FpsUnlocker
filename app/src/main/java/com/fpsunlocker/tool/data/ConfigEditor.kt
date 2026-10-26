package com.fpsunlocker.tool.data

enum class ConfigFormat { INI, JSON, XML }

data class ScanHit(val path: String, val key: String, val line: String)

class KeyNotFoundException(key: String) :
    Exception("ไม่พบคีย์ \"$key\" ในไฟล์ (ตรวจตัวพิมพ์เล็ก/ใหญ่ หรือกด Scan เพื่อดูคีย์ที่มีจริง)")

/**
 * อ่าน/แก้ค่าในไฟล์ config แบบแทนที่เฉพาะค่า (regex) ไม่ parse แล้วเขียนใหม่ทั้งไฟล์
 * ข้อดี: รูปแบบไฟล์เดิม (เว้นวรรค, ลำดับคีย์, คอมเมนต์) ไม่เปลี่ยน เกมจึงมีโอกาสอ่านไฟล์ได้เหมือนเดิม
 *
 * รองรับ
 *  INI / CFG / key=value :  FrameRateLimit=60
 *  JSON                  :  "FrameRateLimit": 60
 *  XML (SharedPreferences):  <int name="FrameRateLimit" value="60" />  หรือ  <string name="fps">60</string>
 */
object ConfigEditor {

    private val FPS_HINT =
        Regex("(?i)(fps|frame_?rate|framerate|frame_?limit|max_?frame|refresh_?rate|target_?frame)")

    val TEXT_EXTENSIONS = setOf("ini", "cfg", "conf", "config", "json", "xml", "txt", "properties", "lua")

    fun detectFormat(path: String, content: String): ConfigFormat {
        val head = content.trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            path.endsWith(".json", ignoreCase = true) -> ConfigFormat.JSON
            path.endsWith(".xml", ignoreCase = true) -> ConfigFormat.XML
            head.startsWith("{") || head.startsWith("[") -> ConfigFormat.JSON
            head.startsWith("<") -> ConfigFormat.XML
            else -> ConfigFormat.INI
        }
    }

    /** ไฟล์ที่มี byte 0 แทบแน่ใจว่าเป็น binary หรือถูกเข้ารหัส แก้ด้วย regex ไม่ได้ */
    fun isProbablyBinary(content: String): Boolean = content.contains('\u0000')

    private fun patterns(format: ConfigFormat, key: String): List<Regex> {
        val k = Regex.escape(key)
        return when (format) {
            ConfigFormat.INI -> listOf(
                Regex("""(?m)^(\s*$k\s*[=:]\s*)([^\r\n;#]*)""")
            )
            ConfigFormat.JSON -> listOf(
                Regex("""("$k"\s*:\s*)("[^"]*"|-?\d+(?:\.\d+)?|true|false|null)""")
            )
            ConfigFormat.XML -> listOf(
                Regex("""(<(?:int|long|float)\s+name="$k"\s+value=")([^"]*)(")"""),
                Regex("""(<string\s+name="$k">)([^<]*)(</string>)"""),
            )
        }
    }

    /** คืนค่าปัจจุบันของคีย์ หรือ null ถ้าไม่พบ */
    fun readValue(content: String, format: ConfigFormat, key: String): String? {
        for (regex in patterns(format, key)) {
            val match = regex.find(content) ?: continue
            return match.groupValues[2].trim().trim('"')
        }
        return null
    }

    /**
     * แทนค่าของคีย์ทุกตำแหน่งที่พบด้วย [value]
     * @return เนื้อหาใหม่ และจำนวนตำแหน่งที่แก้
     * @throws KeyNotFoundException ถ้าไม่พบคีย์เลย (จะไม่เพิ่มคีย์ใหม่เอง เพราะเกมอาจไม่รู้จัก)
     */
    fun applyValue(content: String, format: ConfigFormat, key: String, value: String): Pair<String, Int> {
        var result = content
        var count = 0
        for (regex in patterns(format, key)) {
            result = regex.replace(result) { m ->
                count++
                val old = m.groupValues[2]
                val newValue = if (format == ConfigFormat.JSON && old.trim().startsWith("\"")) "\"$value\"" else value
                val tail = m.groups[3]?.value.orEmpty()
                m.groupValues[1] + newValue + tail
            }
        }
        if (count == 0) throw KeyNotFoundException(key)
        return result to count
    }

    /** หาบรรทัดที่ดูเหมือนเกี่ยวกับ FPS เพื่อช่วยผู้ใช้หาคีย์ที่ถูกต้อง */
    fun findFpsCandidates(path: String, content: String): List<ScanHit> {
        val format = detectFormat(path, content)
        val keyExtractor = when (format) {
            ConfigFormat.XML -> Regex("""name="([^"]+)"""")
            ConfigFormat.JSON -> Regex(""""([^"]+)"\s*:""")
            ConfigFormat.INI -> Regex("""^\s*([^=:\s;#\[]+)\s*[=:]""")
        }
        return content.lineSequence()
            .filter { it.length < 300 && FPS_HINT.containsMatchIn(it) }
            .mapNotNull { line ->
                keyExtractor.findAll(line)
                    .map { it.groupValues[1] }
                    .firstOrNull { FPS_HINT.containsMatchIn(it) }
                    ?.let { key -> ScanHit(path, key, line.trim()) }
            }
            .distinctBy { it.key }
            .toList()
    }
}
