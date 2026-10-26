package com.fpsunlocker.tool.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fpsunlocker.tool.data.ConfigEditor
import com.fpsunlocker.tool.data.FileAccess
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.data.SafFileAccess
import com.fpsunlocker.tool.data.ScanHit
import com.fpsunlocker.tool.data.ShizukuFileAccess
import com.fpsunlocker.tool.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FpsOption(val label: String, val value: String?) {
    FPS_60("60", "60"),
    FPS_90("90", "90"),
    FPS_120("120", "120"),
    UNLOCKED("Max", null), // ใช้ค่าที่ผู้ใช้กรอกเอง เพราะแต่ละเกมใช้ค่าต่างกัน (0, -1, 999 ...)
}

data class UiState(
    val shizukuStatus: ShizukuManager.Status = ShizukuManager.Status.NOT_RUNNING,
    val shizukuBound: Boolean = false,
    val safGranted: Boolean = false,
    val packageName: String = "",
    val gameCandidates: List<String> = emptyList(),
    val configPath: String = "",
    val fpsKey: String = "",
    val fps: FpsOption = FpsOption.FPS_60,
    val unlockedValue: String = "999",
    val currentValue: String? = null,
    val backupExists: Boolean = false,
    val scanHits: List<ScanHit> = emptyList(),
    val textFiles: List<FileEntry> = emptyList(),
    val preview: Pair<String, String>? = null, // (path, เนื้อหาที่ตัดมาแสดง)
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
) {
    val accessLabel: String
        get() = when {
            shizukuBound -> "Shizuku"
            safGranted -> "SAF"
            else -> "ยังไม่มีสิทธิ์"
        }
    val canEdit: Boolean
        get() = (shizukuBound || safGranted) && packageName.isNotBlank() &&
            configPath.isNotBlank() && fpsKey.isNotBlank() && !busy
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val shizuku = ShizukuManager()

    private val _ui = MutableStateFlow(
        UiState(
            packageName = prefs.getString(KEY_PKG, "").orEmpty(),
            configPath = prefs.getString(KEY_PATH, "").orEmpty(),
            fpsKey = prefs.getString(KEY_FPS_KEY, "").orEmpty(),
            unlockedValue = prefs.getString(KEY_UNLOCKED, "999").orEmpty(),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        shizuku.start()
        updateSafState()
        viewModelScope.launch {
            combine(shizuku.status, shizuku.service) { status, service -> status to (service != null) }
                .collect { (status, bound) ->
                    _ui.update { it.copy(shizukuStatus = status, shizukuBound = bound) }
                    refreshFileInfo()
                }
        }
    }

    override fun onCleared() {
        shizuku.stop()
    }

    // ---------- Shizuku / SAF ----------

    fun refreshShizuku() = shizuku.refresh()
    fun requestShizukuPermission() = shizuku.requestPermission()

    fun onTreeGranted(uri: Uri) {
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        updateSafState()
        log("ได้สิทธิ์โฟลเดอร์ผ่าน SAF แล้ว")
        refreshFileInfo()
    }

    private fun updateSafState() {
        _ui.update { it.copy(safGranted = safAccess() != null) }
    }

    private fun safAccess(): SafFileAccess? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return null
        val uri = prefs.getString(KEY_TREE, null) ?: return null
        val pkg = _ui.value.packageName.trim()
        return runCatching { SafFileAccess(getApplication(), Uri.parse(uri)) }
            .getOrNull()
            ?.takeIf { it.rootName == pkg } // สิทธิ์ต้องตรงกับโฟลเดอร์ของเกมที่เลือก
    }

    /** เลือกช่องทาง: Shizuku ก่อน ถ้าไม่มีค่อยใช้ SAF */
    private fun fileAccess(): FileAccess? {
        val pkg = _ui.value.packageName.trim()
        if (pkg.isEmpty()) return null
        shizuku.service.value?.let { return ShizukuFileAccess(it, "/storage/emulated/0/Android/data/$pkg") }
        return safAccess()
    }

    // ---------- input ----------

    fun onPackageChange(v: String) {
        _ui.update { it.copy(packageName = v.trim(), scanHits = emptyList(), textFiles = emptyList(), preview = null) }
        prefs.edit().putString(KEY_PKG, v.trim()).apply()
        updateSafState()
        refreshFileInfo()
    }

    fun onPathChange(v: String) {
        _ui.update { it.copy(configPath = v.trim()) }
        prefs.edit().putString(KEY_PATH, v.trim()).apply()
        refreshFileInfo()
    }

    fun onKeyChange(v: String) {
        _ui.update { it.copy(fpsKey = v.trim()) }
        prefs.edit().putString(KEY_FPS_KEY, v.trim()).apply()
        refreshFileInfo()
    }

    fun onFpsSelected(option: FpsOption) = _ui.update { it.copy(fps = option) }

    fun onUnlockedValueChange(v: String) {
        val clean = v.filter { it.isDigit() || it == '-' }.take(5)
        _ui.update { it.copy(unlockedValue = clean) }
        prefs.edit().putString(KEY_UNLOCKED, clean).apply()
    }

    fun useScanHit(hit: ScanHit) {
        onPathChange(hit.path)
        onKeyChange(hit.key)
        log("เลือก ${hit.key} ใน ${hit.path}")
    }

    // ---------- actions ----------

    /** หาแพ็กเกจที่น่าจะเป็นเกม (ต้องใช้ Shizuku) */
    fun findGamePackages() {
        val access = fileAccess() as? ShizukuFileAccess
            ?: shizuku.service.value?.let { ShizukuFileAccess(it, "/") }
        if (access == null) {
            log("✗ ต้องเชื่อม Shizuku ก่อนจึงจะค้นหาแพ็กเกจได้ (หรือพิมพ์ชื่อแพ็กเกจเอง)")
            return
        }
        launchIo("ค้นหาเกม") {
            val packages = access.exec("pm list packages")
                .lineSequence()
                .map { it.removePrefix("package:").trim() }
                .filter { p -> GAME_HINTS.any { p.contains(it, ignoreCase = true) } }
                .sorted()
                .toList()
            _ui.update { it.copy(gameCandidates = packages) }
            if (packages.isEmpty()) "ไม่พบแพ็กเกจของ NetEase ในเครื่อง" else "พบ ${packages.size} แพ็กเกจ แตะเพื่อเลือก"
        }
    }

    /**
     * ไล่หาไฟล์ config ในโฟลเดอร์ของเกม
     * อ่านเฉพาะไฟล์เล็ก (ไฟล์ตั้งค่ามักเล็กมาก ส่วนไฟล์ resource ของเกมใหญ่) และไล่โฟลเดอร์ที่น่าจะมี config ก่อน
     * ไม่กรองนามสกุล เพราะไฟล์ของเกมนี้หลายไฟล์ไม่มีนามสกุล เช่น files/netease_data
     */
    fun scan() = withAccess("Scan") { fa ->
        val found = LinkedHashMap<String, FileEntry>()
        var truncated = false
        for (dir in SCAN_DIRS) {
            val entries = runCatching { fa.listFiles(dir, maxDepth = 12, limit = LIST_LIMIT, maxSize = MAX_READ_SIZE) }
                .getOrDefault(emptyList())
            if (entries.size >= LIST_LIMIT) truncated = true
            entries.forEach { found.putIfAbsent(it.path, it) }
        }
        val candidates = found.values.take(MAX_READS)

        val hits = mutableListOf<ScanHit>()
        val textFiles = mutableListOf<FileEntry>()
        var binary = 0
        for (entry in candidates) {
            val content = runCatching { fa.readText(entry.path) }.getOrNull() ?: continue
            if (ConfigEditor.isProbablyBinary(content)) { binary++; continue }
            textFiles += entry
            if (hits.size < 100) hits += ConfigEditor.findFpsCandidates(entry.path, content)
        }
        _ui.update { it.copy(scanHits = hits, textFiles = textFiles.take(300)) }
        buildString {
            append("ไฟล์เล็ก ${found.size}")
            if (truncated || found.size > MAX_READS) append(" (ถึงขีดจำกัด อาจยังตรวจไม่ครบ)")
            append(" · อ่านได้เป็นข้อความ ${textFiles.size} · binary/เข้ารหัส $binary · คีย์ FPS ${hits.size}")
        }
    }

    /** เปิดดูเนื้อหาไฟล์ เพื่อให้ผู้ใช้หาค่าตั้งค่าเองได้ถ้า Scan ไม่เจอคีย์ */
    fun preview(path: String) = withAccess("ดูไฟล์") { fa ->
        if (path.isBlank()) throw IllegalArgumentException("ยังไม่ได้เลือกไฟล์")
        val size = fa.size(path)
        if (size > MAX_READ_SIZE) throw IllegalStateException("ไฟล์ใหญ่เกินไป ($size bytes)")
        val content = fa.readText(path)
        val text = if (ConfigEditor.isProbablyBinary(content)) {
            "[binary หรือเข้ารหัส · $size bytes]\n" +
                content.take(600).map { c -> if (c.code in 32..126) c else '.' }.joinToString("")
        } else {
            content.take(6000)
        }
        _ui.update { it.copy(preview = path to text) }
        "เปิดดู $path ($size bytes)"
    }

    fun useTextFile(entry: FileEntry) {
        onPathChange(entry.path)
        preview(entry.path)
    }

    fun closePreview() = _ui.update { it.copy(preview = null) }

    fun apply() = withAccess("Apply") { fa ->
        val s = _ui.value
        val value = s.fps.value ?: s.unlockedValue.ifBlank { throw IllegalArgumentException("กรอกค่าสำหรับ Unlocked ก่อน") }
        val path = s.configPath
        val backup = "$path.bak"

        val content = fa.readText(path)
        if (ConfigEditor.isProbablyBinary(content)) {
            throw IllegalStateException("ไฟล์นี้เป็น binary หรือถูกเข้ารหัส แก้ด้วยแอปนี้ไม่ได้")
        }
        val format = ConfigEditor.detectFormat(path, content)
        val (newContent, count) = ConfigEditor.applyValue(content, format, s.fpsKey, value)

        // สำรองเฉพาะครั้งแรก เพื่อให้ .bak เป็นไฟล์ต้นฉบับจริงเสมอ ไม่ถูกทับด้วยไฟล์ที่แก้แล้ว
        val backupNote = if (!fa.exists(backup)) {
            fa.copy(path, backup)
            "สร้าง $backup แล้ว"
        } else {
            "มี .bak เดิมอยู่แล้ว (เก็บต้นฉบับไว้ ไม่ทับ)"
        }

        fa.writeText(path, newContent)

        val check = ConfigEditor.readValue(fa.readText(path), format, s.fpsKey)
        if (check != value) throw IllegalStateException("เขียนแล้วอ่านกลับได้ $check ไม่ตรงกับ $value")

        "ตั้ง ${s.fpsKey} = $value แล้ว ($count ตำแหน่ง, $format) · $backupNote"
    }

    fun restore() = withAccess("Restore") { fa ->
        val path = _ui.value.configPath
        val backup = "$path.bak"
        if (!fa.exists(backup)) throw IllegalStateException("ไม่พบไฟล์ $backup")
        fa.copy(backup, path)
        "คืนค่าไฟล์จาก $backup แล้ว"
    }

    private fun refreshFileInfo() {
        val s = _ui.value
        val fa = fileAccess()
        if (fa == null || s.configPath.isBlank()) {
            _ui.update { it.copy(currentValue = null, backupExists = false) }
            return
        }
        viewModelScope.launch {
            val (current, hasBackup) = withContext(Dispatchers.IO) {
                runCatching {
                    val content = fa.readText(s.configPath)
                    val value = if (s.fpsKey.isBlank() || ConfigEditor.isProbablyBinary(content)) null
                    else ConfigEditor.readValue(content, ConfigEditor.detectFormat(s.configPath, content), s.fpsKey)
                    value to fa.exists("${s.configPath}.bak")
                }.getOrDefault(null to false)
            }
            _ui.update { it.copy(currentValue = current, backupExists = hasBackup) }
        }
    }

    // ---------- helpers ----------

    private fun withAccess(name: String, block: suspend (FileAccess) -> String) {
        val fa = fileAccess()
        if (fa == null) {
            log("✗ $name: ยังเข้าถึงโฟลเดอร์เกมไม่ได้ (เชื่อม Shizuku หรือให้สิทธิ์ SAF และกรอกชื่อแพ็กเกจ)")
            return
        }
        launchIo(name) { block(fa) + " [${fa.label}]" }
    }

    private fun launchIo(name: String, block: suspend () -> String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val message = withContext(Dispatchers.IO) { block() }
                log("✓ $message")
            } catch (e: Exception) {
                log("✗ $name: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _ui.update { it.copy(busy = false) }
                refreshFileInfo()
            }
        }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _ui.update { it.copy(log = (listOf("[$time] $message") + it.log).take(40)) }
    }

    private companion object {
        const val KEY_PKG = "package"
        const val KEY_PATH = "config_path"
        const val KEY_FPS_KEY = "fps_key"
        const val KEY_UNLOCKED = "unlocked_value"
        const val KEY_TREE = "tree_uri"
        val GAME_HINTS = listOf("netease", "yysls", "wherewinds", "wwm")

        // ลำดับโฟลเดอร์ที่ไล่ก่อน (โฟลเดอร์ใหญ่อย่าง files/Package ไว้ท้าย ผ่านรอบ "" ซึ่งคือทั้งโฟลเดอร์)
        val SCAN_DIRS = listOf("files/LocalData", "files/Engine", "files/orbit", "files/ccmini", "files", "")
        const val LIST_LIMIT = 2500          // ต่อหนึ่งโฟลเดอร์ (กันเกินขนาด binder)
        const val MAX_READ_SIZE = 256_000L   // ไฟล์ config จริงมักเล็กกว่านี้มาก
        const val MAX_READS = 3000
    }
}
